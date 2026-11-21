package co.coretech.portal.proyectos.aplicacion;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.ReglaNegocioException;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.compartido.aplicacion.ValidacionException;
import co.coretech.portal.compartido.aplicacion.ValidacionException.Acumulador;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.Equipo;
import co.coretech.portal.proyectos.CitasParaProyectos;
import co.coretech.portal.proyectos.CitasParaProyectos.CitaOrigen;
import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.FasePlantilla;
import co.coretech.portal.proyectos.datos.PlantillasJdbc;
import co.coretech.portal.proyectos.datos.ProyectosJdbc;
import co.coretech.portal.proyectos.datos.ProyectosJdbc.Fila;

/**
 * Crear proyectos desde una cita atendida y gestionarlos (HU-02, HU-17, RN-04, RN-05, RN-06, RN-27, RN-29).
 * <p>
 * Cupo del equipo (RN-05): lo que ocupa un cupo es un proyecto ACTIVO (con el plan en borrador, enviado o aprobado);
 * los pausados no cuentan. Antes de leer el cupo se bloquea la fila del equipo, así dos asignaciones simultáneas no
 * pasan del máximo. La fecha de entrega depende del estado del plan (D-33): en borrador o con ajustes pedidos cambia de
 * una vez; con el plan enviado no se cambia; con el plan aprobado nace un cambio CAMBIAR_FECHA ya AUTORIZADO que acepta
 * el cliente (P21).
 */
@Service
public class ServicioProyectos {

    static final String NO_ENCONTRADO = "Proyecto no encontrado";
    static final int TAMANO_PAGINA = 20;
    static final int TAMANO_MAXIMO = 100;
    private static final Set<String> FILTROS = Set.of("todas", "activo", "riesgo", "plan", "pausado", "garantia", "finalizado",
            "cancelado");

    /** Lo que se necesita para crear un proyecto; las fechas llegan como texto AAAA-MM-DD. */
    public record Creacion(UUID citaId, String nombre, String alcance, String fechaInicio, String fechaEstimadaEntrega,
            UUID equipoId) {
    }

    public record Creado(UUID proyectoId, String codigo, String estadoPlan, int fases) {
    }

    /** Cita atendida que puede ser (o ya fue) el origen de un proyecto. {@code proyecto} es el código si ya tiene uno. */
    public record CitaAtendida(UUID citaId, String codigo, String cliente, String correoCliente, UUID servicioId,
            String servicio, Instant fechaHora, UUID equipoId, String productOwner, String proyecto) {
    }

    public record VistaProyecto(Fila fila, String cliente, String correoCliente, String servicio, UUID productOwnerId,
            String productOwner, int proyectosActivosDelEquipo, int maxProyectosDelEquipo) {
    }

    public record Filtros(String estado, UUID equipoId, String q, Integer pagina, Integer tamano) {
    }

    public record Listado(List<VistaProyecto> proyectos, Map<String, Integer> porFiltro, int pagina, int tamano,
            int totalFiltrados, int totalPaginas) {
    }

    /** Campos opcionales: solo se cambia lo que se envía. {@code motivo} es obligatorio siempre (queda en la auditoría). */
    public record Edicion(String nombre, String alcance, String fechaEstimadaEntrega, UUID equipoId, String motivo) {
    }

    /** {@code cambioFecha} solo existe cuando el plan está aprobado: la fecha no cambia hasta que el cliente acepte. */
    public record ResultadoEdicion(VistaProyecto proyecto, CambioFecha cambioFecha) {
    }

    public record CambioFecha(UUID cambioId, String estado, LocalDate nuevaFecha) {
    }

    private final ProyectosJdbc proyectos;
    private final PlantillasJdbc plantillas;
    private final CitasParaProyectos citas;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final DirectorioServicios servicios;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioProyectos(ProyectosJdbc proyectos, PlantillasJdbc plantillas, CitasParaProyectos citas,
            EquipoConsultas equipos, DirectorioUsuarios usuarios, DirectorioServicios servicios, RegistroAuditoria auditoria,
            SesionActual sesion, ApplicationEventPublisher eventos, Clock reloj) {
        this.proyectos = proyectos;
        this.plantillas = plantillas;
        this.citas = citas;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.servicios = servicios;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------------------------------- Crear (HU-02)

    /** Citas atendidas, las recientes primero, con el código del proyecto si ya generaron uno (RN-04). */
    @Transactional(readOnly = true)
    public List<CitaAtendida> citasAtendidas() {
        List<CitaOrigen> origenes = citas.atendidas();
        Map<UUID, String> conProyecto = proyectos.codigosPorCita();
        Map<UUID, ContactoCorreo> clientes = usuarios.perfiles(origenes.stream().map(CitaOrigen::clienteId).toList());
        Map<UUID, String> nombresServicio = servicios.nombres(origenes.stream().map(CitaOrigen::servicioId).toList());
        Map<UUID, Equipo> equiposDeCitas = equipos.porIds(origenes.stream().map(CitaOrigen::equipoId).filter(java.util.Objects::nonNull).toList());
        List<CitaAtendida> lista = new ArrayList<>();
        for (CitaOrigen c : origenes) {
            ContactoCorreo cliente = clientes.get(c.clienteId());
            Equipo equipo = c.equipoId() == null ? null : equiposDeCitas.get(c.equipoId());
            lista.add(new CitaAtendida(c.citaId(), c.codigo(), cliente == null ? "" : cliente.nombre(),
                    cliente == null ? "" : cliente.email(), c.servicioId(), nombresServicio.getOrDefault(c.servicioId(), ""),
                    c.fechaHora(), c.equipoId(), equipo == null ? null : equipo.productOwner(), conProyecto.get(c.citaId())));
        }
        return lista;
    }

    /** Equipos activos para elegir, de menor a mayor carga. */
    @Transactional(readOnly = true)
    public List<Equipo> equiposParaElegir() {
        return equipos.activos();
    }

    @Transactional
    public Creado crear(Creacion pedida) {
        LocalDate hoy = LocalDate.now(reloj);
        Acumulador errores = new Acumulador();
        String nombre = limpio(pedida.nombre());
        String alcance = limpio(pedida.alcance());
        if (pedida.citaId() == null) {
            errores.agregar("citaId", "Elige la cita de la que nace el proyecto.");
        }
        validarNombre(nombre, errores);
        validarAlcance(alcance, errores);
        LocalDate inicio = fecha(pedida.fechaInicio(), "fechaInicio", "Elige la fecha de inicio.", errores);
        if (inicio != null && inicio.isBefore(hoy)) {
            errores.agregar("fechaInicio", "El inicio no puede ser una fecha pasada.");
        }
        LocalDate entrega = fecha(pedida.fechaEstimadaEntrega(), "fechaEstimadaEntrega", "Elige la fecha estimada de entrega.", errores);
        if (inicio != null && entrega != null && entrega.isBefore(inicio.plusDays(14))) {
            errores.agregar("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio");
        }
        if (pedida.equipoId() == null) {
            errores.agregar("equipoId", "Elige el equipo responsable.");
        }
        errores.lanzarSiHay();

        CitaOrigen cita = citas.buscar(pedida.citaId()).orElseThrow(() -> new NoEncontradoException("No encontramos esa cita"));
        if (!cita.atendida()) {
            throw new ConflictoException("citaId", "Solo una cita atendida puede convertirse en proyecto");
        }
        Optional<String> existente = proyectos.codigoDeCita(cita.citaId());
        if (existente.isPresent()) {
            throw new ConflictoException("citaId", "Esta cita ya se convirtió en el proyecto " + existente.get());
        }
        if (!plantillaValida(plantillas.leer(cita.servicioId()))) {
            throw new ReglaNegocioException("citaId", "Completa la plantilla de fases de este servicio antes de crear el proyecto");
        }

        Equipo equipo = equipoConCupo(pedida.equipoId());
        UUID administrador = sesion.usuario().id();
        String codigo = proyectos.siguienteCodigo();
        UUID id = proyectos.insertar(codigo, nombre, alcance, cita.citaId(), cita.clienteId(), cita.servicioId(), equipo.equipoId(),
                administrador, inicio, entrega);
        int fases = proyectos.copiarPlantilla(id, cita.servicioId());

        String servicio = servicios.nombres(List.of(cita.servicioId())).getOrDefault(cita.servicioId(), "");
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("nombre", CambioDeValor.nuevo(nombre));
        cambios.put("cita", CambioDeValor.nuevo(cita.codigo()));
        cambios.put("servicio", CambioDeValor.nuevo(servicio));
        cambios.put("equipo", CambioDeValor.nuevo("Equipo de " + equipo.productOwner()));
        cambios.put("fechaInicio", CambioDeValor.nuevo(inicio.toString()));
        cambios.put("fechaEstimadaEntrega", CambioDeValor.nuevo(entrega.toString()));
        cambios.put("fasesDelPlan", CambioDeValor.nuevo(fases));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Creó un proyecto desde una cita atendida", "proyecto", codigo, cambios);

        eventos.publishEvent(AvisoSolicitado.para(cita.clienteId(), "PROYECTO_CREADO", "Creamos tu proyecto " + codigo,
                recortar(nombre + ": el equipo de " + equipo.productOwner() + " prepara tu plan de trabajo y tu cotización."),
                "/cliente/proyectos/" + codigo));
        eventos.publishEvent(AvisoSolicitado.para(equipo.productOwnerId(), "PROYECTO_ASIGNADO", "Te asignaron el proyecto " + codigo,
                recortar(nombre + ": ajusta el plan de trabajo y envíalo al cliente con la cotización."),
                "/equipo/proyectos/" + codigo + "/plan"));
        return new Creado(id, codigo, "BORRADOR", fases);
    }

    // ------------------------------------------------------------------------------------------- Lista (HU-17)

    @Transactional(readOnly = true)
    public Listado listar(Filtros filtros) {
        String filtro = filtros.estado() == null || filtros.estado().isBlank() ? "todas" : filtros.estado().trim().toLowerCase(Locale.ROOT);
        if (!FILTROS.contains(filtro)) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("estado", "Elige un estado de la lista.")));
        }
        List<Fila> todos = proyectos.todos(LocalDate.now(reloj));
        Map<UUID, ContactoCorreo> clientes = usuarios.perfiles(todos.stream().map(Fila::clienteId).collect(java.util.stream.Collectors.toSet()));
        Map<UUID, String> nombresServicio = servicios.nombres(todos.stream().map(Fila::servicioId).collect(java.util.stream.Collectors.toSet()));
        Map<UUID, Equipo> deEquipos = equipos.porIds(todos.stream().map(Fila::equipoId).collect(java.util.stream.Collectors.toSet()));
        Map<UUID, UUID> poDeEquipos = new HashMap<>();
        deEquipos.values().forEach(e -> poDeEquipos.put(e.equipoId(), e.productOwnerId()));

        String texto = sinTildes(limpio(filtros.q()));
        List<VistaProyecto> base = new ArrayList<>();
        for (Fila f : todos) {
            if (filtros.equipoId() != null && !filtros.equipoId().equals(f.equipoId())) {
                continue;
            }
            VistaProyecto v = vista(f, clientes, nombresServicio, deEquipos);
            if (texto.isEmpty() || sinTildes(f.codigo() + " " + f.nombre() + " " + v.cliente() + " " + v.correoCliente()).contains(texto)) {
                base.add(v);
            }
        }
        Map<String, Integer> porFiltro = new LinkedHashMap<>();
        for (String nombre : List.of("todas", "activo", "riesgo", "plan", "pausado", "garantia", "finalizado", "cancelado")) {
            porFiltro.put(nombre, (int) base.stream().filter(v -> cumple(nombre, v.fila())).count());
        }
        List<VistaProyecto> filtrados = base.stream().filter(v -> cumple(filtro, v.fila())).toList();
        int tamano = filtros.tamano() == null ? TAMANO_PAGINA : Math.max(1, Math.min(filtros.tamano(), TAMANO_MAXIMO));
        int totalPaginas = Math.max(1, (filtrados.size() + tamano - 1) / tamano);
        int pagina = Math.max(0, Math.min(filtros.pagina() == null ? 0 : filtros.pagina(), totalPaginas - 1));
        List<VistaProyecto> pedida = filtrados.subList(Math.min(pagina * tamano, filtrados.size()),
                Math.min((pagina + 1) * tamano, filtrados.size()));
        return new Listado(pedida, porFiltro, pagina, tamano, filtrados.size(), totalPaginas);
    }

    @Transactional(readOnly = true)
    public VistaProyecto obtener(String codigo) {
        return vistaDe(buscar(codigo));
    }

    /** Los filtros de la ficha HU-17; "en riesgo" ya viene calculado por la consulta. */
    static boolean cumple(String filtro, Fila f) {
        return switch (filtro) {
            case "activo" -> f.estado().equals("ACTIVO");
            case "riesgo" -> f.riesgo() != null;
            case "plan" -> f.estado().equals("ACTIVO") && f.estadoPlan().equals("ENVIADO");
            case "pausado" -> f.estado().equals("PAUSADO");
            case "garantia" -> f.estado().equals("EN_GARANTIA");
            case "finalizado" -> f.estado().equals("FINALIZADO");
            case "cancelado" -> f.estado().equals("CANCELADO");
            default -> true;
        };
    }

    // ------------------------------------------------------------------------------------------- Editar (HU-17)

    @Transactional
    public ResultadoEdicion editar(String codigo, Edicion pedida) {
        Fila actual = bloqueado(codigo);
        exigirAbierto(actual);
        LocalDate hoy = LocalDate.now(reloj);

        Acumulador errores = new Acumulador();
        String nombre = pedida.nombre() == null ? actual.nombre() : limpio(pedida.nombre());
        String alcance = pedida.alcance() == null ? actual.alcance() : limpio(pedida.alcance());
        validarNombre(nombre, errores);
        validarAlcance(alcance, errores);
        LocalDate entrega = actual.fechaEntrega();
        if (pedida.fechaEstimadaEntrega() != null) {
            entrega = fecha(pedida.fechaEstimadaEntrega(), "fechaEstimadaEntrega", "Elige la fecha de entrega.", errores);
            if (entrega != null && !entrega.equals(actual.fechaEntrega())) {
                if (entrega.isBefore(hoy)) {
                    errores.agregar("fechaEstimadaEntrega", "La entrega no puede quedar en una fecha pasada.");
                } else if (entrega.isBefore(actual.fechaInicio().plusDays(14))) {
                    errores.agregar("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio");
                }
            }
        }
        String motivo = limpio(pedida.motivo());
        validarMotivo(motivo, errores);
        errores.lanzarSiHay();

        boolean cambiaDatos = !nombre.equals(actual.nombre()) || !alcance.equals(actual.alcance());
        boolean cambiaFecha = entrega != null && !entrega.equals(actual.fechaEntrega());
        boolean cambiaEquipo = pedida.equipoId() != null && !pedida.equipoId().equals(actual.equipoId());
        if (!cambiaDatos && !cambiaFecha && !cambiaEquipo) {
            throw new ReglaNegocioException("No hay cambios para guardar");
        }

        // La fecha de entrega según el estado del plan (D-33)
        boolean fechaDirecta = false;
        boolean fechaConCambio = false;
        if (cambiaFecha) {
            switch (actual.estadoPlan()) {
                case "ENVIADO" -> throw new ConflictoException("fechaEstimadaEntrega",
                        "El plan está enviado al cliente; espera su respuesta para cambiar la entrega");
                case "APROBADO" -> {
                    if (!actual.estado().equals("ACTIVO")) {
                        throw new ConflictoException("fechaEstimadaEntrega", "Reanuda el proyecto para cambiar la fecha de entrega");
                    }
                    if (motivo.length() < 20 || motivo.length() > 500) {
                        throw new ReglaNegocioException("motivo", "El motivo del cambio de fecha debe tener entre 20 y 500 caracteres");
                    }
                    if (proyectos.hayCambioDePlanAbierto(actual.id())) {
                        throw new ConflictoException("fechaEstimadaEntrega",
                                "Este proyecto ya tiene un cambio de plan abierto: espera su respuesta");
                    }
                    fechaConCambio = true;
                }
                default -> fechaDirecta = true;
            }
        }

        UUID equipoNuevo = null;
        Equipo equipo = null;
        if (cambiaEquipo) {
            equipo = actual.estado().equals("ACTIVO") ? equipoConCupo(pedida.equipoId()) : equipoActivo(pedida.equipoId());
            equipoNuevo = equipo.equipoId();
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        if (!nombre.equals(actual.nombre())) {
            cambios.put("nombre", CambioDeValor.de(actual.nombre(), nombre));
        }
        if (!alcance.equals(actual.alcance())) {
            cambios.put("alcance", CambioDeValor.de(actual.alcance(), alcance));
        }
        proyectos.actualizarDatos(actual.id(), nombre, alcance, fechaDirecta ? entrega : actual.fechaEntrega());
        if (fechaDirecta) {
            cambios.put("fechaEstimadaEntrega", CambioDeValor.de(actual.fechaEntrega().toString(), entrega.toString()));
        }
        CambioFecha cambioFecha = null;
        if (fechaConCambio) {
            UUID cambioId = proyectos.insertarCambioDeFecha(actual.id(), entrega, motivo, sesion.usuario().id());
            cambioFecha = new CambioFecha(cambioId, "AUTORIZADO", entrega);
            cambios.put("cambioDeFechaAutorizado", CambioDeValor.nuevo(entrega.toString()));
        }
        Equipo anterior = null;
        if (cambiaEquipo) {
            anterior = equipos.porIds(List.of(actual.equipoId())).get(actual.equipoId());
            proyectos.cambiarEquipo(actual.id(), equipoNuevo);
            cambios.put("equipo", CambioDeValor.de(anterior == null ? null : "Equipo de " + anterior.productOwner(),
                    "Equipo de " + equipo.productOwner()));
        }
        cambios.put("motivo", CambioDeValor.nuevo(motivo));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, cambiaEquipo && !cambiaDatos && !cambiaFecha
                ? "Reasignó el equipo de un proyecto" : "Editó un proyecto", "proyecto", codigo, cambios);

        avisarEdicion(actual, nombre, cambiaDatos || fechaDirecta, fechaConCambio, entrega, cambiaEquipo, equipo, anterior);
        return new ResultadoEdicion(vistaDe(buscar(codigo)), cambioFecha);
    }

    private void avisarEdicion(Fila actual, String nombre, boolean datos, boolean fechaConCambio, LocalDate entrega,
            boolean cambiaEquipo, Equipo nuevo, Equipo anterior) {
        String enlaceCliente = "/cliente/proyectos/" + actual.codigo();
        UUID poActual = cambiaEquipo ? nuevo.productOwnerId() : propietarioDe(actual.equipoId());
        if (datos) {
            eventos.publishEvent(AvisoSolicitado.para(actual.clienteId(), "PROYECTO_EDITADO", "Actualizamos tu proyecto " + actual.codigo(),
                    recortar("Se actualizaron los datos de «" + nombre + "». Míralos en tu panel."), enlaceCliente));
            if (poActual != null && !cambiaEquipo) {
                eventos.publishEvent(AvisoSolicitado.para(poActual, "PROYECTO_EDITADO", "Actualizaron el proyecto " + actual.codigo(),
                        recortar("La administración cambió los datos de «" + nombre + "»."), "/equipo/proyectos/" + actual.codigo() + "/plan"));
            }
        }
        if (fechaConCambio) {
            eventos.publishEvent(AvisoSolicitado.para(actual.clienteId(), "CAMBIO_FECHA_AUTORIZADO",
                    "Propuesta para cambiar la entrega de " + actual.codigo(),
                    recortar("La administración propone entregar el " + entrega + ". Acéptala o recházala en tu proyecto."), enlaceCliente));
        }
        if (cambiaEquipo) {
            eventos.publishEvent(AvisoSolicitado.para(nuevo.productOwnerId(), "PROYECTO_ASIGNADO", "Te asignaron el proyecto " + actual.codigo(),
                    recortar("Recibes «" + nombre + "» con su historial."), "/equipo/proyectos/" + actual.codigo() + "/plan"));
            if (anterior != null && anterior.productOwnerId() != null) {
                eventos.publishEvent(AvisoSolicitado.para(anterior.productOwnerId(), "PROYECTO_REASIGNADO",
                        "El proyecto " + actual.codigo() + " pasó a otro equipo",
                        recortar("Ahora lo atiende el equipo de " + nuevo.productOwner() + "."), null));
            }
            eventos.publishEvent(AvisoSolicitado.para(actual.clienteId(), "PROYECTO_REASIGNADO", "Cambió el equipo de " + actual.codigo(),
                    recortar("Ahora te atiende el equipo de " + nuevo.productOwner() + "."), enlaceCliente));
        }
    }

    // ------------------------------------------------------------------------------------------- Estado (HU-17)

    /**
     * Pausa, reanuda o cancela. {@code estadoPedido}: PAUSADO, ACTIVO (reanudar) o CANCELADO. Al reanudar un proyecto cuyo
     * plan venció o fue rechazado el administrador elige el equipo y el plan vuelve a BORRADOR en la misma operación (RN-29).
     * Un proyecto nunca se borra: se cancela.
     */
    @Transactional
    public VistaProyecto cambiarEstado(String codigo, String estadoPedido, String motivoPedido, UUID equipoId) {
        String pedido = estadoPedido == null ? "" : estadoPedido.trim().toUpperCase(Locale.ROOT);
        Acumulador errores = new Acumulador();
        if (!Set.of("PAUSADO", "ACTIVO", "CANCELADO").contains(pedido)) {
            errores.agregar("estado", "Elige si quieres pausar, reanudar o cancelar el proyecto.");
        }
        String motivo = limpio(motivoPedido);
        validarMotivo(motivo, errores);
        errores.lanzarSiHay();

        Fila actual = bloqueado(codigo);
        exigirAbierto(actual);
        if (actual.estado().equals(pedido)) {
            throw new ConflictoException(pedido.equals("ACTIVO") ? "El proyecto ya está activo" : "El proyecto ya está pausado");
        }
        if (pedido.equals("PAUSADO") && !actual.estado().equals("ACTIVO")) {
            throw new ConflictoException("Solo se pausa un proyecto activo");
        }
        if (pedido.equals("ACTIVO") && !actual.estado().equals("PAUSADO")) {
            throw new ConflictoException("Solo se reanuda un proyecto pausado");
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de(actual.estado(), pedido));
        String accion;
        UUID equipoFinal = actual.equipoId();
        if (pedido.equals("ACTIVO")) {
            boolean planVuelve = actual.estadoPlan().equals("VENCIDO") || actual.estadoPlan().equals("RECHAZADO");
            if (planVuelve && equipoId == null) {
                throw new ValidacionException(List.of(new ValidacionException.Falla("equipoId",
                        "Elige el equipo que retomará el proyecto (puede ser el mismo)")));
            }
            equipoFinal = equipoId == null ? actual.equipoId() : equipoId;
            Equipo equipo = equipoConCupo(equipoFinal);
            proyectos.reanudar(actual.id(), equipo.equipoId(), planVuelve);
            if (planVuelve) {
                cambios.put("estadoPlan", CambioDeValor.de(actual.estadoPlan(), "BORRADOR"));
            }
            if (!equipo.equipoId().equals(actual.equipoId())) {
                Equipo anterior = equipos.porIds(List.of(actual.equipoId())).get(actual.equipoId());
                cambios.put("equipo", CambioDeValor.de(anterior == null ? null : "Equipo de " + anterior.productOwner(),
                        "Equipo de " + equipo.productOwner()));
            }
            accion = "Reanudó un proyecto";
        } else {
            proyectos.cambiarEstado(actual.id(), pedido, motivo);
            accion = pedido.equals("PAUSADO") ? "Pausó un proyecto" : "Canceló un proyecto";
        }
        cambios.put("motivo", CambioDeValor.nuevo(motivo));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, accion, "proyecto", codigo, cambios);

        avisarEstado(actual, pedido, equipoFinal);
        return vistaDe(buscar(codigo));
    }

    private void avisarEstado(Fila actual, String pedido, UUID equipoFinal) {
        String enlaceCliente = "/cliente/proyectos/" + actual.codigo();
        UUID po = propietarioDe(equipoFinal);
        String titulo;
        String texto;
        String tipo;
        switch (pedido) {
            case "PAUSADO" -> {
                tipo = "PROYECTO_PAUSADO";
                titulo = "Pausamos tu proyecto " + actual.codigo();
                texto = "«" + actual.nombre() + "» queda en pausa; no se registran avances hasta reanudarlo.";
            }
            case "ACTIVO" -> {
                tipo = "PROYECTO_REANUDADO";
                titulo = "Reanudamos tu proyecto " + actual.codigo();
                texto = actual.estadoPlan().equals("VENCIDO") || actual.estadoPlan().equals("RECHAZADO")
                        ? "«" + actual.nombre() + "» vuelve a empezar: el equipo prepara un nuevo plan y cotización."
                        : "«" + actual.nombre() + "» sigue su curso.";
            }
            default -> {
                tipo = "PROYECTO_CANCELADO";
                titulo = "Cancelamos tu proyecto " + actual.codigo();
                texto = "«" + actual.nombre() + "» se cerró sin terminar. Su historial se conserva.";
            }
        }
        eventos.publishEvent(AvisoSolicitado.para(actual.clienteId(), tipo, titulo, recortar(texto), enlaceCliente));
        UUID poAnterior = propietarioDe(actual.equipoId());
        Set<UUID> equipo = new HashSet<>();
        if (po != null) {
            equipo.add(po);
        }
        if (poAnterior != null) {
            equipo.add(poAnterior);
        }
        if (!equipo.isEmpty()) {
            eventos.publishEvent(new AvisoSolicitado(List.copyOf(equipo), tipo, titulo.replace("tu proyecto", "el proyecto"),
                    recortar(texto.replace("tu ", "su ")), pedido.equals("CANCELADO") ? null : "/equipo/proyectos/" + actual.codigo() + "/plan"));
        }
    }

    // ------------------------------------------------------------------------------------------- Ayudas

    private Fila bloqueado(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        proyectos.bloquear(codigo);
        return buscar(codigo);
    }

    private Fila buscar(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        return proyectos.porCodigo(codigo, LocalDate.now(reloj)).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private static void exigirAbierto(Fila f) {
        switch (f.estado()) {
            case "FINALIZADO" -> throw new ConflictoException("Este proyecto ya finalizó: solo se puede consultar");
            case "CANCELADO" -> throw new ConflictoException("Este proyecto está cancelado: solo se puede consultar");
            case "EN_GARANTIA" -> throw new ConflictoException("Este proyecto está en garantía: solo admite correcciones");
            default -> {
            }
        }
    }

    /** El equipo si está activo; bloquea su fila antes de leer el cupo. */
    private Equipo equipoActivo(UUID equipoId) {
        equipos.bloquear(equipoId);
        return equipos.activo(equipoId).orElseThrow(() -> new ValidacionException(List.of(
                new ValidacionException.Falla("equipoId", "Elige un equipo de la lista"))));
    }

    /** RN-05: el equipo activo con cupo; si ya llegó a su máximo de proyectos activos, 409. */
    private Equipo equipoConCupo(UUID equipoId) {
        Equipo equipo = equipoActivo(equipoId);
        if (equipo.proyectosActivos() >= equipo.maxProyectos()) {
            throw new ConflictoException("equipoId", "El equipo ya tiene " + equipo.maxProyectos() + " proyectos activos, elige otro");
        }
        return equipo;
    }

    private UUID propietarioDe(UUID equipoId) {
        Equipo e = equipos.porIds(List.of(equipoId)).get(equipoId);
        return e == null ? null : e.productOwnerId();
    }

    private VistaProyecto vistaDe(Fila f) {
        return vista(f, usuarios.perfiles(List.of(f.clienteId())), servicios.nombres(List.of(f.servicioId())),
                equipos.porIds(List.of(f.equipoId())));
    }

    private static VistaProyecto vista(Fila f, Map<UUID, ContactoCorreo> clientes, Map<UUID, String> nombresServicio,
            Map<UUID, Equipo> deEquipos) {
        ContactoCorreo cliente = clientes.get(f.clienteId());
        Equipo equipo = deEquipos.get(f.equipoId());
        return new VistaProyecto(f, cliente == null ? "" : cliente.nombre(), cliente == null ? "" : cliente.email(),
                nombresServicio.getOrDefault(f.servicioId(), ""), equipo == null ? null : equipo.productOwnerId(),
                equipo == null ? "" : equipo.productOwner(), equipo == null ? 0 : equipo.proyectosActivos(),
                equipo == null ? 0 : equipo.maxProyectos());
    }

    /** RN-28: de 2 a 8 fases, al menos una que apruebe el cliente y al menos un paso en cada una. */
    static boolean plantillaValida(Collection<FasePlantilla> fases) {
        return fases.size() >= 2 && fases.size() <= 8 && fases.stream().anyMatch(FasePlantilla::apruebaCliente)
                && fases.stream().allMatch(f -> !f.pasos().isEmpty());
    }

    private static void validarNombre(String nombre, Acumulador errores) {
        if (nombre.isEmpty()) {
            errores.agregar("nombre", "Escribe el nombre que verá el cliente.");
        } else if (nombre.length() < 5) {
            errores.agregar("nombre", "Escribe al menos 5 caracteres.");
        } else if (nombre.length() > 60) {
            errores.agregar("nombre", "Escribe máximo 60 caracteres.");
        }
    }

    private static void validarAlcance(String alcance, Acumulador errores) {
        if (alcance.isEmpty()) {
            errores.agregar("alcance", "Describe lo que se acordó en la cita.");
        } else if (alcance.length() < 20) {
            errores.agregar("alcance", "Escribe al menos 20 caracteres.");
        } else if (alcance.length() > 600) {
            errores.agregar("alcance", "Escribe máximo 600 caracteres.");
        }
    }

    private static void validarMotivo(String motivo, Acumulador errores) {
        if (motivo.isEmpty()) {
            errores.agregar("motivo", "Escribe el motivo: queda en la auditoría.");
        } else if (motivo.length() < 10) {
            errores.agregar("motivo", "Escribe al menos 10 caracteres.");
        } else if (motivo.length() > 300) {
            errores.agregar("motivo", "Escribe máximo 300 caracteres.");
        }
    }

    private static LocalDate fecha(String texto, String campo, String vacio, Acumulador errores) {
        if (texto == null || texto.isBlank()) {
            errores.agregar(campo, vacio);
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            errores.agregar(campo, "Escribe la fecha como AAAA-MM-DD.");
            return null;
        }
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static String recortar(String texto) {
        return texto.length() <= 255 ? texto : texto.substring(0, 254) + "…";
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

}
