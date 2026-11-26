package co.coretech.portal.proyectos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.coretech.portal.proyectos.service.ServicioProyectos.CambioFecha;
import co.coretech.portal.proyectos.service.ServicioProyectos.CitaAtendida;
import co.coretech.portal.proyectos.service.ServicioProyectos.Creado;
import co.coretech.portal.proyectos.service.ServicioProyectos.Listado;
import co.coretech.portal.proyectos.service.ServicioProyectos.ResultadoEdicion;
import co.coretech.portal.proyectos.service.ServicioProyectos.VistaProyecto;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/** Cuerpos de las peticiones y respuestas de {@link ControladorProyectos}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoProyectos {

    private DtoProyectos() {
    }

    public record SolicitudCrear(UUID citaId, String nombre, String alcance, String fechaInicio, String fechaEstimadaEntrega,
            UUID equipoId) {
    }

    public record SolicitudEditar(String nombre, String alcance, String fechaEstimadaEntrega, UUID equipoId, String motivo) {
    }

    /** {@code estado}: PAUSADO, ACTIVO (reanudar) o CANCELADO; {@code equipoId} solo al reanudar. */
    public record SolicitudEstado(String estado, String motivo, UUID equipoId) {
    }

    public record CreadoDto(UUID proyectoId, String codigo, String estadoPlan, int fases) {

        public static CreadoDto de(Creado c) {
            return new CreadoDto(c.proyectoId(), c.codigo(), c.estadoPlan(), c.fases());
        }
    }

    public record CitaAtendidaDto(UUID citaId, String codigo, String cliente, String correoCliente, UUID servicioId, String servicio,
            Instant fechaHora, UUID equipoId, String productOwner, String proyecto) {

        public static CitaAtendidaDto de(CitaAtendida c) {
            return new CitaAtendidaDto(c.citaId(), c.codigo(), c.cliente(), c.correoCliente(), c.servicioId(), c.servicio(),
                    c.fechaHora(), c.equipoId(), c.productOwner(), c.proyecto());
        }
    }

    public record EquipoDto(UUID equipoId, String productOwner, int integrantes, int proyectosActivos, int maxProyectos,
            int cupoLibre) {
    }

    public record ClienteDto(UUID clienteId, String nombre, String correo) {
    }

    public record EquipoProyectoDto(UUID equipoId, String productOwner, int proyectosActivos, int maxProyectos) {
    }

    public record FaseActualDto(int orden, String nombre) {
    }

    /** Un proyecto para el administrador. {@code riesgo} dice por qué está "en riesgo" (HU-17) o es nulo. */
    public record ProyectoDto(UUID proyectoId, String codigo, String nombre, String alcance, UUID citaId, ClienteDto cliente,
            UUID servicioId, String servicio, EquipoProyectoDto equipo, LocalDate fechaInicio, LocalDate fechaEstimadaEntrega,
            String estado, String estadoPlan, BigDecimal valorCotizado, LocalDate planPlazoRespuesta, LocalDate planVenceEl,
            BigDecimal avancePorcentaje, int fases, FaseActualDto faseActual, String motivoEstado, LocalDate garantiaHasta,
            Instant finalizadoAt, Instant creadoAt, Instant actualizadoAt, LocalDate ultimoAvance, String riesgo,
            boolean enRiesgo) {

        public static ProyectoDto de(VistaProyecto v) {
            ProyectoFila f = v.fila();
            return new ProyectoDto(f.id(), f.codigo(), f.nombre(), f.alcance(), f.citaId(),
                    new ClienteDto(f.clienteId(), v.cliente(), v.correoCliente()), f.servicioId(), v.servicio(),
                    new EquipoProyectoDto(f.equipoId(), v.productOwner(), v.proyectosActivosDelEquipo(), v.maxProyectosDelEquipo()),
                    f.fechaInicio(), f.fechaEntrega(), f.estado(), f.estadoPlan(), f.valorCotizado(), f.planPlazoRespuesta(),
                    f.planVenceEl(), f.avance(), f.fases(),
                    f.faseActualOrden() == null ? null : new FaseActualDto(f.faseActualOrden(), f.faseActualNombre()),
                    f.motivoEstado(), f.garantiaHasta(), f.finalizadoAt(), f.creadoAt(), f.actualizadoAt(), f.ultimoAvance(),
                    f.riesgo(), f.riesgo() != null);
        }
    }

    public record ListaProyectos(List<ProyectoDto> proyectos, Map<String, Integer> porFiltro, int pagina, int tamano,
            int totalFiltrados, int totalPaginas) {

        public static ListaProyectos de(Listado l) {
            return new ListaProyectos(l.proyectos().stream().map(ProyectoDto::de).toList(), l.porFiltro(), l.pagina(), l.tamano(),
                    l.totalFiltrados(), l.totalPaginas());
        }
    }

    public record CambioFechaDto(UUID cambioId, String estado, LocalDate nuevaFecha) {

        public static CambioFechaDto de(CambioFecha c) {
            return c == null ? null : new CambioFechaDto(c.cambioId(), c.estado(), c.nuevaFecha());
        }
    }

    /** El proyecto tras editarlo; {@code cambioFecha} solo viene con el plan aprobado (la fecha aún no cambia, D-33). */
    public record ProyectoEditado(ProyectoDto proyecto, CambioFechaDto cambioFecha) {

        public static ProyectoEditado de(ResultadoEdicion r) {
            return new ProyectoEditado(ProyectoDto.de(r.proyecto()), CambioFechaDto.de(r.cambioFecha()));
        }
    }
}
