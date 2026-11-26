package co.coretech.portal.comunicacion.service;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.comunicacion.repository.NotificacionRepositorio;
import co.coretech.portal.comunicacion.domain.Notificacion;

/**
 * Notificaciones internas (HU-24, RF-39): las guarda cuando otro módulo publica {@link AvisoSolicitado} y
 * las muestra en la campana. El correo lo manda después {@link TareaEnvioCorreos}, nunca dentro de la petición.
 * <p>
 * Marcar como leída no queda en la auditoría: no cambia datos del negocio, solo el estado de la propia campana.
 */
@Service
public class ServicioNotificaciones {

    static final String NO_ENCONTRADA = "Notificación no encontrada";
    /** Cuántas muestra la campana: todas las no leídas primero, hasta completar este número. */
    static final int TAMANO_BANDEJA = 30;
    /** RNF-09: las leídas se conservan 6 meses. */
    static final Period CONSERVACION_LEIDAS = Period.ofMonths(6);

    private final NotificacionRepositorio notificaciones;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;

    ServicioNotificaciones(NotificacionRepositorio notificaciones, SesionActual sesion, Clock reloj,
            ApplicationEventPublisher eventos) {
        this.notificaciones = notificaciones;
        this.sesion = sesion;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    /**
     * Guarda una notificación por destinatario en la MISMA transacción que publicó el aviso, justo antes de confirmarla:
     * si esa transacción se revierte, el aviso también (no se avisa de algo que no pasó) y si se confirma, el aviso queda
     * guardado con ella. Si se publica fuera de una transacción (una tarea programada), abre la suya y se guarda de una vez.
     * <p>
     * No puede ser {@code AFTER_COMMIT} con una transacción nueva: en esa fase la petición todavía tiene su conexión a la
     * base, así que cada una necesitaría dos a la vez y, con el pool de 10, diez peticiones simultáneas se bloquearían unas
     * a otras hasta que vencieran los 30 s de espera (avisos perdidos y respuestas de 30 s; AvisosConcurrentesTest).
     * El correo sí sale después de confirmar, en otro hilo: {@link EnvioInmediato} escucha el {@link AvisosGuardados}.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    @Transactional
    public void registrar(AvisoSolicitado aviso) {
        Instant ahora = Instant.now(reloj);
        List<Notificacion> nuevas = aviso.destinatarios().stream()
                .map(usuarioId -> Notificacion.nueva(usuarioId, aviso.tipo(), aviso.titulo(), aviso.texto(), aviso.enlace(), ahora))
                .toList();
        notificaciones.saveAll(nuevas);
        // Cuando esta transacción se confirme, el correo sale de inmediato en segundo plano (EnvioInmediato)
        eventos.publishEvent(new AvisosGuardados(nuevas.size()));
    }

    /** La campana del usuario en sesión: cuántas no ha leído y la lista (no leídas primero). */
    @Transactional(readOnly = true)
    public Bandeja bandeja() {
        UUID usuarioId = sesion.usuario().id();
        List<NotificacionVista> lista = notificaciones.bandeja(usuarioId, Limit.of(TAMANO_BANDEJA)).stream()
                .map(NotificacionVista::de)
                .toList();
        return new Bandeja(notificaciones.countByUsuarioIdAndLeidaFalse(usuarioId), lista);
    }

    /** Marca una como leída. La de otro usuario responde 404, como si no existiera. */
    @Transactional
    public void marcarLeida(UUID notificacionId) {
        notificaciones.findByIdAndUsuarioId(notificacionId, sesion.usuario().id())
                .orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADA))
                .marcarLeida();
    }

    @Transactional
    public int marcarTodasLeidas() {
        return notificaciones.marcarTodasLeidas(sesion.usuario().id());
    }

    /** RNF-09: borra las notificaciones leídas con más de 6 meses. */
    public int borrarLeidasAntiguas() {
        Instant limite = ZonedDateTime.now(reloj).minus(CONSERVACION_LEIDAS).toInstant();
        return notificaciones.borrarLeidasAntesDe(limite);
    }

    public record Bandeja(long noLeidas, List<NotificacionVista> notificaciones) {
    }

    public record NotificacionVista(UUID notificacionId, String tipo, String titulo, String mensaje, String enlace,
            boolean leida, Instant fecha) {

        static NotificacionVista de(Notificacion n) {
            return new NotificacionVista(n.getId(), n.getTipo(), n.getTitulo(), n.getMensaje(), n.getEnlace(),
                    n.isLeida(), n.getCreadaEn());
        }
    }
}
