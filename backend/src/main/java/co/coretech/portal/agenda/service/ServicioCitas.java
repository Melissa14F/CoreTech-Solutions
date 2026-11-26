package co.coretech.portal.agenda.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.agenda.repository.CitaRepositorio;
import co.coretech.portal.agenda.domain.Cita;
import co.coretech.portal.agenda.domain.EstadoCita;
import co.coretech.portal.agenda.domain.Modalidad;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Citas del cliente: agendar (HU-01) y consultar o cancelar las suyas (HU-08). Todo se valida aquí, otra vez,
 * aunque el calendario ya lo haya mostrado: la interfaz nunca es la última barrera. La franja única (RN-02) y
 * "no dos citas a la misma hora" (RN-03) las garantizan además los índices {@code ux_cita_franja} y
 * {@code ux_cita_cliente_hora}: si dos clientes toman la misma franja a la vez, uno falla con 409.
 */
@Service
@Transactional
public class ServicioCitas {

    static final String MENSAJE_FRANJA_OCUPADA = "La hora que elegiste ya no está disponible, elige otra";
    static final String MENSAJE_YA_TIENES_CITA = "Ya tienes una cita";
    static final String MENSAJE_FUERA_DE_HORARIO =
            "Las citas se agendan de lunes a viernes, con mínimo 24 horas y máximo 30 días de anticipación";

    private static final DateTimeFormatter FECHA_AVISO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu, h:mm a",
            Locale.forLanguageTag("es-CO"));

    /** Lo que envía el cliente al confirmar (flujo 1 de la API). */
    public record DatosCita(UUID servicioId, String fechaHora, String modalidad, String descripcionNecesidad, String pais,
            String zonaHoraria) {
    }

    /** Una cita con el nombre de su servicio, para mostrarla. */
    public record CitaConServicio(Cita cita, String servicio) {
    }

    private final CitaRepositorio citas;
    private final DirectorioServicios catalogo;
    private final ServicioDisponibilidad disponibilidad;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioCitas(CitaRepositorio citas, DirectorioServicios catalogo, ServicioDisponibilidad disponibilidad,
            RegistroAuditoria auditoria, ApplicationEventPublisher eventos, Clock reloj) {
        this.citas = citas;
        this.catalogo = catalogo;
        this.disponibilidad = disponibilidad;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** HU-01, paso 2: las franjas del mes en la zona del cliente. El servicio debe estar visible. */
    @Transactional(readOnly = true)
    public List<ServicioDisponibilidad.Franja> disponibilidad(UUID clienteId, UUID servicioId, String mes, String zona) {
        Acumulador errores = new Acumulador();
        YearMonth mesPedido = null;
        ZoneId zonaPedida = null;
        if (servicioId == null) {
            errores.agregar("servicioId", "Elige un servicio.");
        }
        try {
            mesPedido = YearMonth.parse(mes == null ? "" : mes.trim());
        } catch (DateTimeParseException e) {
            errores.agregar("mes", "Indica el mes como AAAA-MM, por ejemplo 2026-11");
        }
        try {
            zonaPedida = ZoneId.of(zona == null ? "" : zona.trim());
        } catch (DateTimeException e) {
            errores.agregar("zona", "Indica una zona horaria válida, por ejemplo Europe/Madrid");
        }
        errores.lanzarSiHay();
        if (catalogo.nombreSiVisible(servicioId).isEmpty()) {
            throw new NoEncontradoException("Este servicio ya no está disponible");
        }
        return disponibilidad.deMes(clienteId, mesPedido, zonaPedida);
    }

    /** HU-01: crea la cita PENDIENTE, bloquea la franja y avisa al cliente (campana y correo, RF-39). */
    public CitaConServicio agendar(UUID clienteId, DatosCita datos) {
        Acumulador errores = new Acumulador();

        if (datos.servicioId() == null) {
            errores.agregar("servicioId", "Elige un servicio.");
        }
        Modalidad modalidad = modalidad(datos.modalidad());
        if (modalidad == null) {
            errores.agregar("modalidad", "Elige si la reunión es presencial o virtual.");
        }
        String descripcion = datos.descripcionNecesidad() == null ? "" : datos.descripcionNecesidad().trim();
        if (descripcion.length() < 20) {
            errores.agregar("descripcionNecesidad", "Cuéntanos un poco más (mínimo 20 caracteres)");
        } else if (descripcion.length() > 500) {
            errores.agregar("descripcionNecesidad", "Usa máximo 500 caracteres");
        }
        String zona = zona(datos, errores);
        Instant inicio = instante(datos.fechaHora(), errores);
        errores.lanzarSiHay();

        String servicio = catalogo.nombreSiVisible(datos.servicioId())
                .orElseThrow(() -> new NoEncontradoException("Este servicio ya no está disponible"));

        if (!disponibilidad.esFranjaAgendable(inicio)) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("fechaHora", MENSAJE_FUERA_DE_HORARIO)));
        }
        // Lo normal (no la carrera): se avisa antes de intentar guardar. La carrera la resuelve el índice único.
        if (citas.clienteTieneCita(clienteId, inicio)) {
            throw new ConflictoException("fechaHora", MENSAJE_YA_TIENES_CITA);
        }
        if (citas.franjaOcupada(inicio)) {
            throw new ConflictoException("fechaHora", MENSAJE_FRANJA_OCUPADA);
        }

        Instant ahora = Instant.now(reloj);
        Cita cita = Cita.nueva(citas.siguienteCodigo(), clienteId, datos.servicioId(), inicio, modalidad, descripcion,
                datos.pais(), zona, ahora);
        // saveAndFlush: si otro cliente tomó la franja a la vez, el error del índice sale aquí y se traduce a 409.
        citas.saveAndFlush(cita);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("servicio", CambioDeValor.nuevo(servicio));
        cambios.put("fechaHora", CambioDeValor.nuevo(inicio.toString()));
        cambios.put("modalidad", CambioDeValor.nuevo(modalidad.name()));
        cambios.put("estado", CambioDeValor.nuevo(EstadoCita.PENDIENTE.name()));
        auditoria.registrar(CategoriaAuditoria.CITAS, "Agendó una cita", "cita", cita.getCodigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(clienteId, "CITA_REGISTRADA", "Registramos tu cita " + cita.getCodigo(),
                servicio + ": " + FECHA_AVISO.format(inicio.atZone(ZoneId.of(zona))) + " (hora de tu zona). "
                        + "Te avisaremos cuando el equipo la confirme.",
                "/cliente/citas"));
        return new CitaConServicio(cita, servicio);
    }

    /** HU-08: las citas del cliente, de la más lejana a la más reciente en el tiempo (la pantalla las agrupa y filtra). */
    @Transactional(readOnly = true)
    public List<CitaConServicio> deCliente(UUID clienteId) {
        List<Cita> lista = citas.findByClienteIdOrderByFechaHoraDesc(clienteId);
        Map<UUID, String> nombres = catalogo.nombres(lista.stream().map(Cita::getServicioId).distinct().toList());
        return lista.stream().map(c -> new CitaConServicio(c, nombres.getOrDefault(c.getServicioId(), ""))).toList();
    }

    /**
     * HU-08, RN-11: el cliente cancela su cita mientras esté PENDIENTE. Una cita ajena o inexistente es 404 (no se
     * revela que existe). El motivo es opcional; si lo escribe, de 15 a 300 caracteres.
     */
    public Cita cancelar(UUID clienteId, UUID citaId, String motivo) {
        String motivoLimpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (motivoLimpio != null && (motivoLimpio.length() < 15 || motivoLimpio.length() > 300)) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("motivo",
                    "El motivo debe tener entre 15 y 300 caracteres")));
        }
        Cita cita = citas.findByIdAndClienteId(citaId, clienteId)
                .orElseThrow(() -> new NoEncontradoException("No encontramos esa cita"));
        switch (cita.getEstado()) {
            case CONFIRMADA -> throw new ConflictoException("Esta cita ya fue confirmada, escríbenos para reprogramarla");
            case ATENDIDA -> throw new ConflictoException("Esta cita ya fue atendida y no se puede cancelar");
            case CANCELADA -> throw new ConflictoException("Esta cita ya fue cancelada");
            case PENDIENTE -> { }
        }

        cita.cancelar(clienteId, motivoLimpio, Instant.now(reloj));
        citas.saveAndFlush(cita);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de(EstadoCita.PENDIENTE.name(), EstadoCita.CANCELADA.name()));
        if (motivoLimpio != null) {
            cambios.put("motivoCancelacion", CambioDeValor.nuevo(motivoLimpio));
        }
        auditoria.registrar(CategoriaAuditoria.CITAS, "Canceló su cita", "cita", cita.getCodigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(clienteId, "CITA_CANCELADA", "Cancelaste tu cita " + cita.getCodigo(),
                "La hora quedó libre. Si aún necesitas reunirte con nosotros, puedes agendar otra cita.", "/cliente/citas"));
        return cita;
    }

    // ------------------------------------------------------------------ validación de los datos

    private static Modalidad modalidad(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return Modalidad.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** País de la lista y su zona (obligatoria solo si el país tiene varias); devuelve la zona IANA a guardar. */
    private static String zona(DatosCita datos, Acumulador errores) {
        if (!PaisesYZonas.existe(datos.pais())) {
            errores.agregar("pais", "Elige el país donde vives");
            return null;
        }
        List<String> zonas = PaisesYZonas.zonasDe(datos.pais());
        if (datos.zonaHoraria() == null || datos.zonaHoraria().isBlank()) {
            if (zonas.size() == 1) {
                return zonas.get(0);
            }
            errores.agregar("zonaHoraria", "Elige tu zona horaria");
            return null;
        }
        if (!zonas.contains(datos.zonaHoraria())) {
            errores.agregar("zonaHoraria", "Elige una zona horaria de la lista");
            return null;
        }
        return datos.zonaHoraria();
    }

    private static Instant instante(String texto, Acumulador errores) {
        if (texto == null || texto.isBlank()) {
            errores.agregar("fechaHora", "Elige el día y la hora de la cita");
            return null;
        }
        try {
            return OffsetDateTime.parse(texto.trim()).toInstant();
        } catch (DateTimeException e) {
            errores.agregar("fechaHora", "Elige el día y la hora de la cita");
            return null;
        }
    }
}
