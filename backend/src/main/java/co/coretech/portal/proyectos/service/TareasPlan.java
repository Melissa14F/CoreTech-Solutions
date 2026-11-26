package co.coretech.portal.proyectos.service;

import co.coretech.portal.proyectos.domain.EstadoPlan;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.service.CalendarioHabil;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.proyectos.repository.PlanesJdbc;
import co.coretech.portal.proyectos.repository.ProyectoRepositorio;
import co.coretech.portal.proyectos.repository.PlanesJdbc.PlanPendiente;
import static co.coretech.portal.compartido.service.Textos.recortar;

/**
 * Tarea diaria de las propuestas sin respuesta (HU-29, RN-29). El plan <b>nunca se aprueba solo</b>:
 * <ul>
 *   <li>Pasado el plazo de 3 días hábiles: recordatorio al cliente. Sale una sola vez (queda la marca {@code plan_recordado_at})
 *       y, si el servidor estuvo apagado, sale el primer día en que la tarea vuelve a correr.</li>
 *   <li>Al llegar {@code plan_vence_el} (10 días hábiles): el plan pasa a VENCIDO y el proyecto a PAUSADO (el equipo libera su
 *       cupo); se avisa al cliente que el proyecto se cancelará si no responde en 30 días calendario.</li>
 *   <li>Cumplidos 30 días calendario de un plan VENCIDO (desde {@code plan_vence_el}) o RECHAZADO (desde la respuesta) que nadie
 *       reanudó: aviso a los administradores para que lo cancelen, una sola vez ({@code plan_cancelacion_avisada_at}).
 *       No se cancela solo.</li>
 * </ul>
 * Corre todos los días, también fines de semana: los avisos de 30 días calendario pueden caer en cualquier día.
 */
@Service
public class TareasPlan {

    private static final Logger LOG = LoggerFactory.getLogger(TareasPlan.class);
    static final int DIAS_PARA_CANCELAR = 30;

    private final PlanesJdbc planes;
    private final ProyectoRepositorio proyectosJpa;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final CalendarioHabil calendario;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public TareasPlan(PlanesJdbc planes, ProyectoRepositorio proyectosJpa, EquipoConsultas equipos, DirectorioUsuarios usuarios, CalendarioHabil calendario,
            RegistroAuditoria auditoria, ApplicationEventPublisher eventos, Clock reloj) {
        this.planes = planes;
        this.proyectosJpa = proyectosJpa;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.calendario = calendario;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Corre sola cada día. Lleva su propia transacción: llamar a {@link #revisar()} desde aquí no pasa por el proxy y la
     * auditoría (que exige transacción) fallaría. Si algo falla se revierte todo y el error queda en el registro.
     */
    @Scheduled(cron = "${coretech.planes.cron:0 0 8 * * *}", zone = "America/Bogota")
    @Transactional
    public void ejecutarCadaDia() {
        int avisos = revisar();
        if (avisos > 0) {
            LOG.info("Propuestas sin respuesta: {} aviso(s) o cambio(s) de estado", avisos);
        }
    }

    /** Devuelve cuántos recordatorios, vencimientos y avisos de cancelación se hicieron hoy. */
    @Transactional
    public int revisar() {
        LocalDate hoy = LocalDate.now(reloj);
        int hechos = 0;
        for (PlanPendiente p : planes.pendientes()) {
            switch (p.estadoPlan()) {
                case "ENVIADO" -> hechos += revisarEnviado(p, hoy);
                case "VENCIDO" -> hechos += avisarCancelacion(p, p.venceEl(), hoy, "venció sin respuesta");
                case "RECHAZADO" -> hechos += avisarCancelacion(p, p.rechazadoEn() == null ? null
                        : p.rechazadoEn().atZone(reloj.getZone()).toLocalDate(), hoy, "fue rechazada");
                default -> {
                }
            }
        }
        return hechos;
    }

    private int revisarEnviado(PlanPendiente p, LocalDate hoy) {
        if (!hoy.isBefore(p.venceEl())) {
            proyectosJpa.cambiarEstadoPlan(p.id(), EstadoPlan.VENCIDO);
            Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
            cambios.put("estadoPlan", CambioDeValor.de("ENVIADO", "VENCIDO"));
            cambios.put("estado", CambioDeValor.de("ACTIVO", "PAUSADO"));
            auditoria.registrar(CategoriaAuditoria.PROYECTOS, "La propuesta venció sin respuesta", "proyecto", p.codigo(), cambios);
            eventos.publishEvent(AvisoSolicitado.para(p.clienteId(), "PLAN_VENCIDO", "Tu propuesta de " + p.codigo() + " venció",
                    recortar(p.nombre() + " quedó en pausa porque no respondiste. Si no respondes en " + DIAS_PARA_CANCELAR
                            + " días, el proyecto se cancelará. Puedes pedir que te envíen la propuesta de nuevo."),
                    "/cliente/proyectos/" + p.codigo() + "/propuesta"));
            avisarAlProductOwner(p, "PLAN_VENCIDO", "La propuesta de " + p.codigo() + " venció",
                    p.nombre() + " quedó en pausa: el cliente no respondió a tiempo y tu equipo libera el cupo.");
            return 1;
        }
        if (p.plazoRespuesta() != null && !p.recordado() && calendario.diasHabilesEntre(p.plazoRespuesta(), hoy) >= 1) {
            proyectosJpa.marcarRecordado(p.id());
            eventos.publishEvent(AvisoSolicitado.para(p.clienteId(), "PLAN_RECORDATORIO", "Tu propuesta espera tu respuesta",
                    recortar(p.nombre() + ": aprueba el plan y la cotización o pide ajustes antes del " + Fechas.conDia(p.venceEl())
                            + "; después la propuesta vence y el proyecto queda en pausa."),
                    "/cliente/proyectos/" + p.codigo() + "/propuesta"));
            return 1;
        }
        return 0;
    }

    /** Desde los 30 días calendario de {@code desde}, y una sola vez: avisa a la administración; no cancela. */
    private int avisarCancelacion(PlanPendiente p, LocalDate desde, LocalDate hoy, String que) {
        if (desde == null || p.cancelacionAvisada() || hoy.isBefore(desde.plusDays(DIAS_PARA_CANCELAR))) {
            return 0;
        }
        List<UUID> administradores = usuarios.administradoresActivos();
        if (administradores.isEmpty()) {
            return 0;
        }
        eventos.publishEvent(new AvisoSolicitado(administradores, "PLAN_PARA_CANCELAR",
                "Sin respuesta hace 30 días: " + p.codigo(),
                recortar("La propuesta de " + p.nombre() + " " + que + " hace " + DIAS_PARA_CANCELAR
                        + " días y nadie la retomó. Si el cliente no responde, cancélalo."),
                "/admin/proyectos?buscar=" + p.codigo()));
        proyectosJpa.marcarCancelacionAvisada(p.id());
        return 1;
    }

    private void avisarAlProductOwner(PlanPendiente p, String tipo, String titulo, String texto) {
        if (p.equipoId() == null) {
            return;
        }
        EquipoConsultas.Equipo e = equipos.porIds(List.of(p.equipoId())).get(p.equipoId());
        if (e != null) {
            eventos.publishEvent(AvisoSolicitado.para(e.productOwnerId(), tipo, titulo, recortar(texto),
                    "/equipo/proyectos/" + p.codigo() + "/plan"));
        }
    }
}
