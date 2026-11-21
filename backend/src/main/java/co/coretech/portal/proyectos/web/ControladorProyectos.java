package co.coretech.portal.proyectos.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.proyectos.aplicacion.ServicioProyectos;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.CambioFecha;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.CitaAtendida;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.Creacion;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.Creado;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.Edicion;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.Filtros;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.Listado;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.ProyectoPropio;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.ResultadoEdicion;
import co.coretech.portal.proyectos.aplicacion.ServicioProyectos.VistaProyecto;
import co.coretech.portal.proyectos.datos.ProyectosJdbc.Fila;

/**
 * Crear proyectos desde una cita atendida (HU-02) y gestionarlos: lista con filtros, editar, reasignar, pausar,
 * reanudar y cancelar (HU-17). Cada endpoint pide su permiso: crear → {@code proyectos.crear}, el resto →
 * {@code proyectos.gestionar}.
 */
@RestController
@RequestMapping("/api/v1/proyectos")
class ControladorProyectos {

    private final ServicioProyectos servicio;

    ControladorProyectos(ServicioProyectos servicio) {
        this.servicio = servicio;
    }

    // --------------------------------------------------------------------------------------------- DTO

    record SolicitudCrear(UUID citaId, String nombre, String alcance, String fechaInicio, String fechaEstimadaEntrega,
            UUID equipoId) {
    }

    record SolicitudEditar(String nombre, String alcance, String fechaEstimadaEntrega, UUID equipoId, String motivo) {
    }

    /** {@code estado}: PAUSADO, ACTIVO (reanudar) o CANCELADO; {@code equipoId} solo al reanudar. */
    record SolicitudEstado(String estado, String motivo, UUID equipoId) {
    }

    record CreadoDto(UUID proyectoId, String codigo, String estadoPlan, int fases) {

        static CreadoDto de(Creado c) {
            return new CreadoDto(c.proyectoId(), c.codigo(), c.estadoPlan(), c.fases());
        }
    }

    record CitaAtendidaDto(UUID citaId, String codigo, String cliente, String correoCliente, UUID servicioId, String servicio,
            Instant fechaHora, UUID equipoId, String productOwner, String proyecto) {

        static CitaAtendidaDto de(CitaAtendida c) {
            return new CitaAtendidaDto(c.citaId(), c.codigo(), c.cliente(), c.correoCliente(), c.servicioId(), c.servicio(),
                    c.fechaHora(), c.equipoId(), c.productOwner(), c.proyecto());
        }
    }

    record EquipoDto(UUID equipoId, String productOwner, int integrantes, int proyectosActivos, int maxProyectos,
            int cupoLibre) {
    }

    record ClienteDto(UUID clienteId, String nombre, String correo) {
    }

    record EquipoProyectoDto(UUID equipoId, String productOwner, int proyectosActivos, int maxProyectos) {
    }

    record FaseActualDto(int orden, String nombre) {
    }

    /** Un proyecto para el administrador. {@code riesgo} dice por qué está "en riesgo" (HU-17) o es nulo. */
    record ProyectoDto(UUID proyectoId, String codigo, String nombre, String alcance, UUID citaId, ClienteDto cliente,
            UUID servicioId, String servicio, EquipoProyectoDto equipo, LocalDate fechaInicio, LocalDate fechaEstimadaEntrega,
            String estado, String estadoPlan, BigDecimal valorCotizado, LocalDate planPlazoRespuesta, LocalDate planVenceEl,
            BigDecimal avancePorcentaje, int fases, FaseActualDto faseActual, String motivoEstado, LocalDate garantiaHasta,
            Instant finalizadoAt, Instant creadoAt, Instant actualizadoAt, LocalDate ultimoAvance, String riesgo,
            boolean enRiesgo) {

        static ProyectoDto de(VistaProyecto v) {
            Fila f = v.fila();
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

    record ListaProyectos(List<ProyectoDto> proyectos, Map<String, Integer> porFiltro, int pagina, int tamano,
            int totalFiltrados, int totalPaginas) {

        static ListaProyectos de(Listado l) {
            return new ListaProyectos(l.proyectos().stream().map(ProyectoDto::de).toList(), l.porFiltro(), l.pagina(), l.tamano(),
                    l.totalFiltrados(), l.totalPaginas());
        }
    }

    record CambioFechaDto(UUID cambioId, String estado, LocalDate nuevaFecha) {

        static CambioFechaDto de(CambioFecha c) {
            return c == null ? null : new CambioFechaDto(c.cambioId(), c.estado(), c.nuevaFecha());
        }
    }

    /** El proyecto tras editarlo; {@code cambioFecha} solo viene con el plan aprobado (la fecha aún no cambia, D-33). */
    record ProyectoEditado(ProyectoDto proyecto, CambioFechaDto cambioFecha) {

        static ProyectoEditado de(ResultadoEdicion r) {
            return new ProyectoEditado(ProyectoDto.de(r.proyecto()), CambioFechaDto.de(r.cambioFecha()));
        }
    }

    // --------------------------------------------------------------------------------------------- Endpoints

    /** Citas atendidas para elegir el origen; las que ya son proyecto traen su código y no se pueden elegir (RN-04). */
    @GetMapping("/citas-atendidas")
    @PreAuthorize("@permisos.tiene('proyectos.crear')")
    List<CitaAtendidaDto> citasAtendidas() {
        return servicio.citasAtendidas().stream().map(CitaAtendidaDto::de).toList();
    }

    /** Equipos activos con su carga: el que ya tiene su cupo lleno se muestra deshabilitado (RN-05). */
    @GetMapping("/equipos")
    @PreAuthorize("@permisos.tiene('proyectos.crear') or @permisos.tiene('proyectos.gestionar')")
    List<EquipoDto> equipos() {
        return servicio.equiposParaElegir().stream().map(e -> new EquipoDto(e.equipoId(), e.productOwner(), e.integrantes(),
                e.proyectosActivos(), e.maxProyectos(), Math.max(0, e.maxProyectos() - e.proyectosActivos()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permisos.tiene('proyectos.crear')")
    CreadoDto crear(@RequestBody SolicitudCrear cuerpo) {
        return CreadoDto.de(servicio.crear(new Creacion(cuerpo.citaId(), cuerpo.nombre(), cuerpo.alcance(), cuerpo.fechaInicio(),
                cuerpo.fechaEstimadaEntrega(), cuerpo.equipoId())));
    }

    /** «Mis proyectos» del cliente y del Product Owner: cada uno ve solo los suyos (RN-10). El administrador usa la lista de arriba. */
    @GetMapping("/propios")
    @PreAuthorize("@permisos.tiene('proyectos.ver_propios')")
    List<ProyectoPropio> propios() {
        return servicio.propios();
    }

    /**
     * Lista del administrador. Filtros: {@code estado} (todas, activo, riesgo, plan, pausado, garantia, finalizado,
     * cancelado), {@code equipoId}, {@code q} (código, nombre o cliente, sin tildes), {@code pagina} (desde 0) y {@code tamano}.
     */
    @GetMapping
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    ListaProyectos listar(@RequestParam(required = false) String estado, @RequestParam(required = false) UUID equipoId,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return ListaProyectos.de(servicio.listar(new Filtros(estado, equipoId, q, pagina, tamano)));
    }

    @PatchMapping("/{codigo}")
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    ProyectoEditado editar(@PathVariable String codigo, @RequestBody SolicitudEditar cuerpo) {
        return ProyectoEditado.de(servicio.editar(codigo, new Edicion(cuerpo.nombre(), cuerpo.alcance(), cuerpo.fechaEstimadaEntrega(),
                cuerpo.equipoId(), cuerpo.motivo())));
    }

    @PatchMapping("/{codigo}/estado")
    @PreAuthorize("@permisos.tiene('proyectos.gestionar')")
    ProyectoDto cambiarEstado(@PathVariable String codigo, @RequestBody SolicitudEstado cuerpo) {
        return ProyectoDto.de(servicio.cambiarEstado(codigo, cuerpo.estado(), cuerpo.motivo(), cuerpo.equipoId()));
    }
}
