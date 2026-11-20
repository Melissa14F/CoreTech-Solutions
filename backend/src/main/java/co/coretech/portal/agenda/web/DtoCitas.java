package co.coretech.portal.agenda.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import co.coretech.portal.agenda.aplicacion.ServicioCitas.CitaConServicio;
import co.coretech.portal.agenda.aplicacion.ServicioGestionCitas;
import co.coretech.portal.agenda.datos.CuestionarioCitaRepositorio;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.agenda.dominio.Cita;
import co.coretech.portal.agenda.dominio.EstadoCita;

/** Cuerpos de /api/v1/citas (flujo 1 de docs/04-api.md, HU-01 y HU-08). */
final class DtoCitas {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    private DtoCitas() {
    }

    /** Lo que envía el cliente al confirmar la cita. {@code fechaHora} es el instante que devolvió la disponibilidad. */
    record SolicitudCita(UUID servicioId, String fechaHora, String modalidad, String descripcionNecesidad, String pais,
            String zonaHoraria) {
    }

    /** Cuerpo opcional al cancelar. */
    record SolicitudCancelar(String motivo) {
    }

    /** 201 al agendar: la hora en la zona del cliente y en la de Colombia (RN-23). */
    record CitaCreada(UUID citaId, String codigo, EstadoCita estado, String fechaHoraLocal, String fechaHoraColombia) {

        static CitaCreada de(Cita c) {
            return new CitaCreada(c.getId(), c.getCodigo(), c.getEstado(), local(c), colombia(c.getFechaHora()));
        }
    }

    /** 200 al cancelar. */
    record CitaCancelada(UUID citaId, EstadoCita estado, Instant canceladaAt) {

        static CitaCancelada de(Cita c) {
            return new CitaCancelada(c.getId(), c.getEstado(), c.getCanceladaEn());
        }
    }

    /** Una fila de "Mis citas". {@code puedeCancelar}: solo PENDIENTE (RN-11). */
    record CitaPropia(UUID citaId, String codigo, UUID servicioId, String servicio, EstadoCita estado, String modalidad,
            String fechaHoraLocal, String fechaHoraColombia, String pais, String zonaHoraria, String descripcionNecesidad,
            String enlaceReunion, String motivoCancelacion, boolean puedeCancelar) {

        static CitaPropia de(CitaConServicio v) {
            Cita c = v.cita();
            return new CitaPropia(c.getId(), c.getCodigo(), c.getServicioId(), v.servicio(), c.getEstado(),
                    c.getModalidad().name(), local(c), colombia(c.getFechaHora()), c.getPais(), c.getZonaHoraria(),
                    c.getDescripcionNecesidad(), c.getEnlaceReunion(), c.getMotivoCancelacion(),
                    c.getEstado() == EstadoCita.PENDIENTE);
        }
    }

    // ------------------------------------------------------------------ administración y equipo (HU-09, HU-10)

    record Persona(String nombre, String email) {
    }

    record EquipoCita(UUID equipoId, String productOwner, String emailProductOwner, int integrantes, int proyectosActivos,
            int maxProyectos) {

        static EquipoCita de(EquipoConsultas.Equipo e, String email) {
            return e == null ? null
                    : new EquipoCita(e.equipoId(), e.productOwner(), email, e.integrantes(), e.proyectosActivos(), e.maxProyectos());
        }
    }

    /** Una cita para el administrador o para el Product Owner: ambas horas (RN-23), el cliente, el equipo y las notas. */
    record CitaGestion(UUID citaId, String codigo, UUID servicioId, String servicio, EstadoCita estado, String modalidad,
            String fechaHoraLocal, String fechaHoraColombia, String pais, String zonaHoraria, String descripcionNecesidad,
            String enlaceReunion, String notaCliente, String notaAsignacion, String motivoCancelacion, Persona cliente,
            EquipoCita equipo, boolean tieneCuestionario, Instant confirmadaAt, Instant atendidaAt, Instant canceladaAt,
            String canceladaPor) {

        static CitaGestion de(ServicioGestionCitas.VistaCita v) {
            Cita c = v.cita();
            return new CitaGestion(c.getId(), c.getCodigo(), c.getServicioId(), v.servicio(), c.getEstado(), c.getModalidad().name(),
                    local(c), colombia(c.getFechaHora()), c.getPais(), c.getZonaHoraria(), c.getDescripcionNecesidad(),
                    c.getEnlaceReunion(), c.getNotaCliente(), c.getNotaAsignacion(), c.getMotivoCancelacion(),
                    new Persona(v.clienteNombre(), v.clienteEmail()), EquipoCita.de(v.equipo(), v.emailProductOwner()),
                    v.tieneCuestionario(), c.getConfirmadaEn(), c.getAtendidaEn(), c.getCanceladaEn(), quienCancelo(c));
        }
    }

    /** Lista del administrador con los números de los filtros. */
    record ListaCitas(java.util.List<CitaGestion> citas, java.util.Map<EstadoCita, Integer> porEstado, int sinEquipo, int total,
            int pagina, int tamano, int totalFiltradas, int totalPaginas) {

        static ListaCitas de(ServicioGestionCitas.Listado l) {
            return new ListaCitas(l.citas().stream().map(CitaGestion::de).toList(), l.porEstado(), l.sinEquipo(), l.total(),
                    l.pagina(), l.tamano(), l.totalFiltradas(), l.totalPaginas());
        }
    }

    /** Un equipo para elegir al asignar. */
    record EquipoParaAsignar(UUID equipoId, String productOwner, int integrantes, int proyectosActivos, int maxProyectos,
            int citasProximas) {

        static EquipoParaAsignar de(ServicioGestionCitas.EquipoConCarga e) {
            return new EquipoParaAsignar(e.equipo().equipoId(), e.equipo().productOwner(), e.equipo().integrantes(),
                    e.equipo().proyectosActivos(), e.equipo().maxProyectos(), e.citasProximas());
        }
    }

    record SolicitudAsignar(UUID equipoId, String nota) {
    }

    record SolicitudCancelarAdmin(String motivo) {
    }

    record SolicitudConfirmar(String enlaceReunion, String nota) {
    }

    record SolicitudCuestionario(String resumen, String descripcionCliente, String presupuesto, String fechaDeseada,
            String idioma, String urgencia) {
    }

    record CuestionarioCreado(UUID cuestionarioId, EstadoCita citaEstado) {
    }

    record ConfirmacionCita(UUID citaId, EstadoCita estado, Instant confirmadaAt) {
    }

    record CuestionarioVista(UUID cuestionarioId, UUID citaId, String resumen, String descripcionCliente, String presupuesto,
            String fechaDeseada, String idioma, String urgencia, Instant registradoAt) {

        static CuestionarioVista de(CuestionarioCitaRepositorio.Cuestionario q) {
            return new CuestionarioVista(q.id(), q.citaId(), q.resumen(), q.descripcionCliente(), q.presupuesto(),
                    q.fechaDeseada(), q.idioma(), q.urgencia(), q.creadoEn());
        }
    }

    /** CLIENTE si la canceló el propio cliente, ADMINISTRACION si la canceló la administración; nulo si no está cancelada. */
    static String quienCancelo(Cita c) {
        if (c.getEstado() != EstadoCita.CANCELADA || c.getCanceladaPor() == null) {
            return null;
        }
        return c.getCanceladaPor().equals(c.getClienteId()) ? "CLIENTE" : "ADMINISTRACION";
    }

    private static String local(Cita c) {
        return c.getFechaHora().atZone(ZoneId.of(c.getZonaHoraria())).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String colombia(Instant instante) {
        return instante.atZone(COLOMBIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
