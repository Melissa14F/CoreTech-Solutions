package co.coretech.portal.agenda.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.agenda.repository.CitaRepositorio;
import co.coretech.portal.agenda.repository.CuestionarioCitaRepositorio;
import co.coretech.portal.agenda.domain.CuestionarioCita;
import co.coretech.portal.agenda.domain.Cita;
import co.coretech.portal.agenda.domain.EstadoCita;
import co.coretech.portal.agenda.domain.Modalidad;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.Equipo;
import static co.coretech.portal.compartido.service.Textos.limpio;
import static co.coretech.portal.compartido.service.Textos.sinTildes;

/**
 * Administración de las citas (HU-09: el administrador lista, asigna el equipo y cancela) y trabajo del desarrollador
 * (Product Owner) con las de su equipo (HU-10: confirma y llena el cuestionario). Todas las reglas se validan aquí:
 * RN-11 (quién cancela), RN-12 (confirmar, enlace si es virtual), RN-23 (dos horas) y RN-24 (el cuestionario cierra la
 * cita y no se edita). Cada cambio de estado avisa al cliente. La HU-09 no prevé reprogramar: el cliente que necesita
 * otra hora cancela y agenda de nuevo.
 */
@Service
@Transactional
public class ServicioGestionCitas {

    static final int TAMANO_PAGINA = 20;
    static final int TAMANO_MAXIMO = 100;
    static final String MENSAJE_NO_SE_PUEDE_MODIFICAR = "Esta cita ya no se puede modificar";
    static final String MENSAJE_ENLACE_OBLIGATORIO = "Las citas virtuales necesitan el enlace de la reunión";
    static final String MENSAJE_NO_ES_DE_TU_EQUIPO = "Esta cita no es de tu equipo";
    static final String MENSAJE_AUN_NO_OCURRIO = "La cita aún no ha ocurrido";
    static final String MENSAJE_YA_TIENE_CUESTIONARIO = "Esta cita ya tiene su cuestionario";

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA_AVISO = DateTimeFormatter.ofPattern("d 'de' MMMM, h:mm a",
            Locale.forLanguageTag("es-CO"));
    private static final Set<String> PRESUPUESTOS = Set.of("MENOS_5M", "DE_5_A_15M", "DE_15_A_30M", "MAS_30M", "SIN_DEFINIR");
    private static final Set<String> IDIOMAS = Set.of("ESPANOL", "INGLES", "PORTUGUES", "OTRO");
    private static final Set<String> URGENCIAS = Set.of("BAJA", "MEDIA", "ALTA");

    /** Una cita con todo lo que muestran las pantallas de administración y del equipo. */
    public record VistaCita(Cita cita, String servicio, String clienteNombre, String clienteEmail, Equipo equipo,
            String emailProductOwner,
            boolean tieneCuestionario) {
    }

    /** Filtros de la lista del administrador; todos opcionales (texto tal como llega por la API). */
    public record Filtros(String estado, Boolean sinEquipo, String desde, String hasta, UUID equipoId, String q, Integer pagina,
            Integer tamano) {
    }

    /** Lista con los números de los filtros: {@code porEstado} cuenta con los demás filtros (menos estado y sin equipo). */
    public record Listado(List<VistaCita> citas, Map<EstadoCita, Integer> porEstado, int sinEquipo, int total, int pagina,
            int tamano, int totalFiltradas, int totalPaginas) {
    }

    /** Un equipo para elegir al asignar, con su carga. */
    public record EquipoConCarga(Equipo equipo, int citasProximas) {
    }

    /** Lo que llena el desarrollador (Product Owner) después de la reunión (flujo 3 de la API). */
    public record DatosCuestionario(String resumen, String descripcionCliente, String presupuesto, String fechaDeseada,
            String idioma, String urgencia) {
    }

    private final CitaRepositorio citas;
    private final CuestionarioCitaRepositorio cuestionarios;
    private final DirectorioServicios catalogo;
    private final DirectorioUsuarios usuarios;
    private final EquipoConsultas equipos;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioGestionCitas(CitaRepositorio citas, CuestionarioCitaRepositorio cuestionarios, DirectorioServicios catalogo,
            DirectorioUsuarios usuarios, EquipoConsultas equipos, RegistroAuditoria auditoria,
            ApplicationEventPublisher eventos, Clock reloj) {
        this.citas = citas;
        this.cuestionarios = cuestionarios;
        this.catalogo = catalogo;
        this.usuarios = usuarios;
        this.equipos = equipos;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ Administrador (HU-09)

    /** Todas las citas, de la más reciente a la más antigua, con los filtros de estado, sin equipo, equipo y fecha. */
    @Transactional(readOnly = true)
    public Listado listar(Filtros filtros) {
        Acumulador errores = new Acumulador();
        EstadoCita estado = null;
        if (filtros.estado() != null && !filtros.estado().isBlank()) {
            try {
                estado = EstadoCita.valueOf(filtros.estado().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errores.agregar("estado", "Elige un estado de la lista.");
            }
        }
        LocalDate desde = fecha(filtros.desde(), "desde", errores);
        LocalDate hasta = fecha(filtros.hasta(), "hasta", errores);
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            errores.agregar("hasta", "La fecha final debe ser igual o posterior a la inicial.");
        }
        errores.lanzarSiHay();

        Instant inicio = desde == null ? Instant.EPOCH : desde.atStartOfDay(COLOMBIA).toInstant();
        Instant fin = hasta == null ? Instant.parse("2999-01-01T00:00:00Z") : hasta.plusDays(1).atStartOfDay(COLOMBIA).toInstant();
        List<Cita> base = citas.entre(inicio, fin).stream()
                .filter(c -> filtros.equipoId() == null || filtros.equipoId().equals(c.getEquipoId())).toList();

        Map<EstadoCita, Integer> porEstado = new EnumMap<>(EstadoCita.class);
        for (EstadoCita e : EstadoCita.values()) {
            porEstado.put(e, 0);
        }
        int sinEquipo = 0;
        for (Cita c : base) {
            porEstado.merge(c.getEstado(), 1, Integer::sum);
            if (esSinEquipo(c)) {
                sinEquipo++;
            }
        }
        EstadoCita estadoPedido = estado;
        List<Cita> filtradas = base.stream()
                .filter(c -> estadoPedido == null || c.getEstado() == estadoPedido)
                .filter(c -> !Boolean.TRUE.equals(filtros.sinEquipo()) || esSinEquipo(c))
                .toList();
        String texto = sinTildes(limpio(filtros.q()));
        List<VistaCita> vistas = vistas(filtradas).stream()
                .filter(v -> texto.isEmpty() || sinTildes(v.cita().getCodigo() + " " + v.clienteNombre() + " " + v.clienteEmail()).contains(texto))
                .toList();
        int tamano = filtros.tamano() == null ? TAMANO_PAGINA : Math.max(1, Math.min(filtros.tamano(), TAMANO_MAXIMO));
        int totalPaginas = Math.max(1, (vistas.size() + tamano - 1) / tamano);
        int pagina = Math.max(0, Math.min(filtros.pagina() == null ? 0 : filtros.pagina(), totalPaginas - 1));
        List<VistaCita> pedida = vistas.subList(Math.min(pagina * tamano, vistas.size()), Math.min((pagina + 1) * tamano, vistas.size()));
        return new Listado(pedida, porEstado, sinEquipo, base.size(), pagina, tamano, vistas.size(), totalPaginas);
    }

    /** Equipos activos para elegir al asignar, de menor a mayor carga (proyectos activos y luego citas próximas). */
    @Transactional(readOnly = true)
    public List<EquipoConCarga> equiposParaAsignar() {
        Map<UUID, Integer> proximas = proximasPorEquipo();
        List<EquipoConCarga> lista = new ArrayList<>(equipos.activos().stream()
                .map(e -> new EquipoConCarga(e, proximas.getOrDefault(e.equipoId(), 0))).toList());
        lista.sort(Comparator.comparingInt((EquipoConCarga e) -> e.equipo().proyectosActivos())
                .thenComparingInt(EquipoConCarga::citasProximas).thenComparing(e -> e.equipo().productOwner()));
        return lista;
    }

    /** HU-09: asigna o reasigna el equipo de una cita PENDIENTE o CONFIRMADA. El nuevo Product Owner la recibe en su bandeja. */
    public VistaCita asignar(UUID citaId, UUID equipoId, String nota) {
        Acumulador errores = new Acumulador();
        if (equipoId == null) {
            errores.agregar("equipoId", "Elige quién atenderá la cita.");
        }
        String notaLimpia = nota == null || nota.isBlank() ? null : nota.trim();
        if (notaLimpia != null && notaLimpia.length() > 300) {
            errores.agregar("nota", "Usa máximo 300 caracteres");
        }
        errores.lanzarSiHay();

        Cita cita = citas.findById(citaId).orElseThrow(() -> new NoEncontradoException("No encontramos esa cita"));
        exigirModificable(cita);
        Equipo nuevo = equipos.activo(equipoId)
                .orElseThrow(() -> new ValidacionException(List.of(new ValidacionException.Falla("equipoId",
                        "Elige un equipo de la lista"))));
        UUID anterior = cita.getEquipoId();
        boolean reabre = cita.getEstado() == EstadoCita.CONFIRMADA && anterior != null && !equipoId.equals(anterior);
        Instant ahora = Instant.now(reloj);
        cita.asignar(equipoId, notaLimpia, ahora);
        if (reabre) {
            // El enlace y la confirmación eran del Product Owner anterior: el nuevo confirma con los suyos.
            cita.reabrir(ahora);
        }
        citas.saveAndFlush(cita);

        String servicio = catalogo.nombres(List.of(cita.getServicioId())).getOrDefault(cita.getServicioId(), "");
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        if (reabre) {
            cambios.put("estado", CambioDeValor.de(EstadoCita.CONFIRMADA.name(), EstadoCita.PENDIENTE.name()));
        }
        cambios.put("equipo", CambioDeValor.de(anterior == null ? null : nombreEquipo(anterior), "Equipo de " + nuevo.productOwner()));
        if (notaLimpia != null) {
            cambios.put("notaAsignacion", CambioDeValor.nuevo(notaLimpia));
        }
        auditoria.registrar(CategoriaAuditoria.CITAS,
                anterior == null ? "Asignó el equipo de una cita" : "Reasignó el equipo de una cita", "cita", cita.getCodigo(), cambios);

        if (reabre) {
            eventos.publishEvent(AvisoSolicitado.para(cita.getClienteId(), "CITA_REASIGNADA", "Cambió el equipo de tu cita " + cita.getCodigo(),
                    "Ahora te atiende el equipo de " + nuevo.productOwner() + ". Te avisaremos cuando confirme la cita y te envíe el enlace.", "/cliente/citas"));
        }
        if (!equipoId.equals(anterior)) {
            eventos.publishEvent(AvisoSolicitado.para(nuevo.productOwnerId(), "CITA_ASIGNADA",
                    "Te asignaron la cita " + cita.getCodigo(),
                    recortar(servicio + ": " + FECHA_AVISO.format(cita.getFechaHora().atZone(COLOMBIA))
                            + " (hora de Colombia). Confírmala con el cliente."),
                    "/equipo/citas"));
            if (anterior != null) {
                equipos.porIds(List.of(anterior)).values().stream().findFirst().ifPresent(e -> eventos.publishEvent(
                        AvisoSolicitado.para(e.productOwnerId(), "CITA_REASIGNADA", "La cita " + cita.getCodigo() + " pasó a otro equipo",
                                "La administración la asignó al equipo de " + nuevo.productOwner() + ".", "/equipo/citas")));
            }
        }
        return vistas(List.of(cita)).get(0);
    }

    /**
     * HU-09, RN-11: el administrador cancela una cita PENDIENTE o CONFIRMADA con un motivo de 15 a 300 caracteres. Avisa al
     * cliente y, si ya tenía equipo, a su Product Owner.
     */
    public VistaCita cancelar(UUID administradorId, UUID citaId, String motivo) {
        String limpio = limpio(motivo);
        if (limpio.isEmpty()) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("motivo", "Escribe el motivo: quedará en la auditoría.")));
        }
        if (limpio.length() < 15 || limpio.length() > 300) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("motivo",
                    "El motivo debe tener entre 15 y 300 caracteres")));
        }
        Cita cita = citas.findById(citaId).orElseThrow(() -> new NoEncontradoException("No encontramos esa cita"));
        exigirModificable(cita);
        EstadoCita anterior = cita.getEstado();
        cita.cancelar(administradorId, limpio, Instant.now(reloj));
        citas.saveAndFlush(cita);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de(anterior.name(), EstadoCita.CANCELADA.name()));
        cambios.put("motivoCancelacion", CambioDeValor.nuevo(limpio));
        auditoria.registrar(CategoriaAuditoria.CITAS, "Canceló una cita", "cita", cita.getCodigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(cita.getClienteId(), "CITA_CANCELADA", "Cancelamos tu cita " + cita.getCodigo(),
                recortar("Motivo: " + limpio + " La hora quedó libre: puedes agendar otra cita."), "/cliente/citas"));
        if (cita.getEquipoId() != null) {
            equipos.porIds(List.of(cita.getEquipoId())).values().stream().findFirst().ifPresent(e -> eventos.publishEvent(
                    AvisoSolicitado.para(e.productOwnerId(), "CITA_CANCELADA", "Se canceló la cita " + cita.getCodigo(),
                            recortar("Motivo: " + limpio), "/equipo/citas")));
        }
        return vistas(List.of(cita)).get(0);
    }

    // ------------------------------------------------------------------ Desarrollador (Product Owner) (HU-10)

    /** Las citas del equipo que lidera el Product Owner, de la más reciente a la más antigua (RN-23: ambas horas). */
    @Transactional(readOnly = true)
    public List<VistaCita> deMiEquipo(UUID productOwnerId) {
        return equipos.equipoDelProductOwner(productOwnerId).map(equipo -> vistas(citas.deEquipo(equipo))).orElse(List.of());
    }

    /** HU-10, RN-12: confirma una cita PENDIENTE de su equipo. Si es virtual, el enlace es obligatorio. Avisa al cliente. */
    public VistaCita confirmar(UUID productOwnerId, UUID citaId, String enlaceReunion, String nota) {
        Cita cita = citaDelEquipo(productOwnerId, citaId);
        switch (cita.getEstado()) {
            case CONFIRMADA -> throw new ConflictoException("Esta cita ya fue confirmada");
            case ATENDIDA, CANCELADA -> throw new ConflictoException(MENSAJE_NO_SE_PUEDE_MODIFICAR);
            case PENDIENTE -> { }
        }
        Acumulador errores = new Acumulador();
        String enlace = limpio(enlaceReunion).isEmpty() ? null : limpio(enlaceReunion);
        if (cita.getModalidad() == Modalidad.VIRTUAL) {
            if (enlace == null) {
                errores.agregar("enlaceReunion", MENSAJE_ENLACE_OBLIGATORIO);
            } else if (enlace.length() > 255) {
                errores.agregar("enlaceReunion", "Usa máximo 255 caracteres");
            } else if (!enlace.matches("^https?://.+\\..+")) {
                errores.agregar("enlaceReunion", "El enlace debe empezar por https://");
            }
        } else {
            enlace = null;
        }
        String notaLimpia = limpio(nota).isEmpty() ? null : limpio(nota);
        if (notaLimpia != null && notaLimpia.length() > 300) {
            errores.agregar("nota", "Usa máximo 300 caracteres");
        }
        errores.lanzarSiHay();

        cita.confirmar(enlace, notaLimpia, Instant.now(reloj));
        citas.saveAndFlush(cita);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de(EstadoCita.PENDIENTE.name(), EstadoCita.CONFIRMADA.name()));
        if (enlace != null) {
            cambios.put("enlaceReunion", CambioDeValor.nuevo(enlace));
        }
        auditoria.registrar(CategoriaAuditoria.CITAS, "Confirmó una cita", "cita", cita.getCodigo(), cambios);

        String servicio = catalogo.nombres(List.of(cita.getServicioId())).getOrDefault(cita.getServicioId(), "");
        String hora = FECHA_AVISO.format(cita.getFechaHora().atZone(ZoneId.of(cita.getZonaHoraria())));
        String texto = servicio + ": " + hora + " (hora de tu zona)."
                + (enlace != null ? " Enlace: " + enlace : "")
                + (notaLimpia != null ? " " + notaLimpia : "");
        eventos.publishEvent(AvisoSolicitado.para(cita.getClienteId(), "CITA_CONFIRMADA", "Confirmamos tu cita " + cita.getCodigo(),
                recortar(texto), "/cliente/citas"));
        return vistas(List.of(cita)).get(0);
    }

    /**
     * HU-10, RN-24: el cuestionario de la reunión deja la cita ATENDIDA. Solo la de su equipo, ya confirmada, después de la
     * hora de la cita y una sola vez (no se edita: RNF-02).
     */
    public UUID llenarCuestionario(UUID productOwnerId, UUID citaId, DatosCuestionario datos) {
        Cita cita = citaDelEquipo(productOwnerId, citaId);
        if (cuestionarios.existe(cita.getId())) {
            throw new ConflictoException(MENSAJE_YA_TIENE_CUESTIONARIO);
        }
        switch (cita.getEstado()) {
            case PENDIENTE -> throw new ConflictoException("Confirma la cita antes de llenar el cuestionario");
            case CANCELADA -> throw new ConflictoException(MENSAJE_NO_SE_PUEDE_MODIFICAR);
            case ATENDIDA -> throw new ConflictoException(MENSAJE_YA_TIENE_CUESTIONARIO);
            case CONFIRMADA -> { }
        }
        Instant ahora = Instant.now(reloj);
        if (ahora.isBefore(cita.getFechaHora())) {
            throw new ConflictoException(MENSAJE_AUN_NO_OCURRIO);
        }

        Acumulador errores = new Acumulador();
        String resumen = limpio(datos.resumen());
        if (resumen.isEmpty()) {
            errores.agregar("resumen", "Escribe el resumen de la reunión.");
        } else if (resumen.length() < 30) {
            errores.agregar("resumen", "Escribe al menos 30 caracteres.");
        } else if (resumen.length() > 1000) {
            errores.agregar("resumen", "Usa máximo 1000 caracteres");
        }
        String descripcion = limpio(datos.descripcionCliente());
        if (descripcion.isEmpty()) {
            errores.agregar("descripcionCliente", "Describe al cliente.");
        } else if (descripcion.length() < 30) {
            errores.agregar("descripcionCliente", "Escribe al menos 30 caracteres.");
        } else if (descripcion.length() > 600) {
            errores.agregar("descripcionCliente", "Usa máximo 600 caracteres");
        }
        String presupuesto = enumeracion(datos.presupuesto());
        if (!PRESUPUESTOS.contains(presupuesto)) {
            errores.agregar("presupuesto", "Elige el presupuesto aproximado.");
        }
        String idioma = enumeracion(datos.idioma());
        if (!IDIOMAS.contains(idioma)) {
            errores.agregar("idioma", "Elige el idioma.");
        }
        String urgencia = enumeracion(datos.urgencia());
        if (!URGENCIAS.contains(urgencia)) {
            errores.agregar("urgencia", "Elige el nivel de urgencia.");
        }
        String fechaDeseada = limpio(datos.fechaDeseada());
        if (fechaDeseada.length() > 60) {
            errores.agregar("fechaDeseada", "Usa máximo 60 caracteres");
        }
        errores.lanzarSiHay();

        UUID id;
        try {
            id = cuestionarios.insertar(cita.getId(), resumen, descripcion, presupuesto,
                    fechaDeseada.isEmpty() ? null : fechaDeseada, idioma, urgencia, productOwnerId, ahora);
        } catch (DataIntegrityViolationException e) {
            // Dos envíos a la vez: el índice único de cita_id deja pasar solo uno.
            throw new ConflictoException(MENSAJE_YA_TIENE_CUESTIONARIO);
        }
        cita.atender(ahora);
        citas.saveAndFlush(cita);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de(EstadoCita.CONFIRMADA.name(), EstadoCita.ATENDIDA.name()));
        cambios.put("urgencia", CambioDeValor.nuevo(urgencia));
        cambios.put("presupuesto", CambioDeValor.nuevo(presupuesto));
        auditoria.registrar(CategoriaAuditoria.CITAS, "Llenó el cuestionario de una cita", "cita", cita.getCodigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(cita.getClienteId(), "CITA_ATENDIDA", "Atendimos tu cita " + cita.getCodigo(),
                "Gracias por reunirte con nosotros. La administración te contactará para armar tu proyecto.", "/cliente/citas"));
        List<UUID> administradores = usuarios.administradoresActivos();
        if (!administradores.isEmpty()) {
            eventos.publishEvent(new AvisoSolicitado(administradores, "CITA_ATENDIDA", "La cita " + cita.getCodigo() + " ya fue atendida",
                    "El cuestionario está listo: puedes crear el proyecto.", "/admin/citas"));
        }
        return id;
    }

    /** Cuestionario de una cita: lo ven el administrador (todas) y el Product Owner de su equipo. */
    @Transactional(readOnly = true)
    public CuestionarioCita cuestionarioDe(UsuarioSesion quien, UUID citaId) {
        Cita cita = quien.tienePermiso("citas.ver_todas")
                ? citas.findById(citaId).orElseThrow(() -> new NoEncontradoException("No encontramos esa cita"))
                : citaDelEquipo(quien.id(), citaId);
        return cuestionarios.deCita(cita.getId())
                .orElseThrow(() -> new NoEncontradoException("Esta cita todavía no tiene cuestionario"));
    }

    // ------------------------------------------------------------------ apoyo

    /** La cita si es del equipo que lidera ese Product Owner; si no existe, 404; si es de otro equipo o sin equipo, 403. */
    private Cita citaDelEquipo(UUID productOwnerId, UUID citaId) {
        Cita cita = citas.findById(citaId).orElseThrow(() -> new NoEncontradoException("No encontramos esa cita"));
        UUID suEquipo = equipos.equipoDelProductOwner(productOwnerId).orElse(null);
        if (suEquipo == null || !suEquipo.equals(cita.getEquipoId())) {
            throw new ProhibidoException(MENSAJE_NO_ES_DE_TU_EQUIPO);
        }
        return cita;
    }

    private static void exigirModificable(Cita cita) {
        if (cita.getEstado() == EstadoCita.ATENDIDA || cita.getEstado() == EstadoCita.CANCELADA) {
            throw new ConflictoException(MENSAJE_NO_SE_PUEDE_MODIFICAR);
        }
    }

    private static boolean esSinEquipo(Cita c) {
        return c.getEquipoId() == null && CitaRepositorio.OCUPAN_FRANJA.contains(c.getEstado());
    }

    private Map<UUID, Integer> proximasPorEquipo() {
        Map<UUID, Integer> mapa = new HashMap<>();
        for (Object[] fila : citas.proximasPorEquipo(CitaRepositorio.OCUPAN_FRANJA, Instant.now(reloj))) {
            mapa.put((UUID) fila[0], ((Number) fila[1]).intValue());
        }
        return mapa;
    }

    private String nombreEquipo(UUID equipoId) {
        return equipos.porIds(List.of(equipoId)).values().stream().findFirst()
                .map(e -> "Equipo de " + e.productOwner()).orElse("Equipo");
    }

    private List<VistaCita> vistas(List<Cita> lista) {
        Map<UUID, String> servicios = catalogo.nombres(lista.stream().map(Cita::getServicioId).distinct().toList());
        Map<UUID, ContactoCorreo> clientes = usuarios.perfiles(lista.stream().map(Cita::getClienteId).distinct().toList());
        Map<UUID, Equipo> porEquipo = equipos.porIds(lista.stream().map(Cita::getEquipoId).filter(Objects::nonNull).distinct().toList());
        Set<UUID> conCuestionario = cuestionarios.conCuestionario(lista.stream().map(Cita::getId).toList());
        Map<UUID, ContactoCorreo> productOwners = usuarios.perfiles(porEquipo.values().stream().map(Equipo::productOwnerId).distinct().toList());
        return lista.stream().map(c -> {
            ContactoCorreo cliente = clientes.get(c.getClienteId());
            Equipo equipo = c.getEquipoId() == null ? null : porEquipo.get(c.getEquipoId());
            ContactoCorreo po = equipo == null ? null : productOwners.get(equipo.productOwnerId());
            return new VistaCita(c, servicios.getOrDefault(c.getServicioId(), ""), cliente == null ? "" : cliente.nombre(),
                    cliente == null ? "" : cliente.email(), equipo, po == null ? null : po.email(),
                    conCuestionario.contains(c.getId()));
        }).toList();
    }

    private static LocalDate fecha(String texto, String campo, Acumulador errores) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            errores.agregar(campo, "Indica la fecha como AAAA-MM-DD");
            return null;
        }
    }

    private static String enumeracion(String texto) {
        return limpio(texto).toUpperCase(Locale.ROOT);
    }

    /** Los avisos tienen máximo 255 caracteres. */
    private static String recortar(String texto) {
        return texto.length() <= 255 ? texto : texto.substring(0, 252) + "...";
    }
}
