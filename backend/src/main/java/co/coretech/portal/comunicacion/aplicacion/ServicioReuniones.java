package co.coretech.portal.comunicacion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Fase;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Persona;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Proyecto;
import co.coretech.portal.comunicacion.datos.SolicitudesReunionJdbc;
import co.coretech.portal.comunicacion.datos.SolicitudesReunionJdbc.SolicitudFila;
import co.coretech.portal.compartido.aplicacion.CalendarioHabil;
import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.ProhibidoException;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.compartido.aplicacion.UsuarioSesion;
import co.coretech.portal.compartido.aplicacion.ValidacionException.Acumulador;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;

/**
 * Solicitudes de reunión de seguimiento (HU-15, HU-16, RN-14, RN-15) y el resumen de la reunión atendida (D-42).
 * <p>
 * El cliente dueño pide la reunión con el proyecto activo y el plan aprobado, proponiendo una o dos horas (RN-01); queda
 * PENDIENTE y asociada a la fase en curso. El desarrollador (Product Owner) del equipo o el administrador la programan
 * (eligen una de las horas y, si es virtual, el enlace), la rechazan con un motivo o, el administrador, la cancelan.
 * Después de la hora programada, el Product Owner llena el resumen y la solicitud queda ATENDIDA; el resumen no se edita.
 * Los accesos no permitidos quedan en la auditoría aunque la petición responda 403 o 404.
 */
@Service
public class ServicioReuniones {

    static final String NO_ENCONTRADO = "Proyecto no encontrado";
    static final String SOLICITUD_NO_ENCONTRADA = "Solicitud no encontrada";
    static final String SIN_PERMISO = "No puedes gestionar solicitudes de reunión de este proyecto";
    static final String PLAN_SIN_APROBAR = "Podrás pedir reuniones de seguimiento cuando apruebes el plan de trabajo";
    static final String ESTADO_NO_ADMITE = "El proyecto no admite solicitudes de reunión en su estado actual";
    static final String YA_HAY_PENDIENTE = "Ya tienes una solicitud de reunión en espera para este proyecto";
    static final String YA_RESPONDIDA = "Esta solicitud ya fue respondida";
    static final String HORARIO_NO_DISPONIBLE = "Ese horario ya no está disponible, propón otro o rechaza";
    static final String AUN_NO_OCURRIO = "La reunión aún no ha ocurrido";
    static final String YA_NO_SE_CIERRA = "Esta reunión ya no se puede cerrar";
    static final String NO_SE_PUEDE_CANCELAR = "Solo se cancelan las reuniones programadas";
    static final String DATOS_DESACTUALIZADOS = "El proyecto cambió, recarga para ver lo último";

    public static final List<String> MOTIVOS = List.of("Revisar el avance", "Cambio en los requerimientos", "Duda técnica",
            "Reportar un error", "Otro");
    public static final List<String> SIGUIENTES_PASOS = List.of("NINGUNO", "DENTRO_DEL_PLAN", "CAMBIO_DE_PLAN");

    private static final Pattern ENLACE = Pattern.compile("^https?://\\S+\\.\\S+$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", Locale.forLanguageTag("es-CO"));

    public record ResumenVista(String resumen, String quePidio, String siguientePaso, Instant registradoAt) {
    }

    /**
     * Una solicitud como la ven el cliente, el equipo y el administrador. {@code opciones} son los horarios que propuso el
     * cliente; {@code puedeCerrar} es verdadero para el Product Owner cuando la reunión ya ocurrió y falta el resumen.
     */
    public record SolicitudVista(UUID id, String codigo, String proyectoCodigo, String proyecto, String cliente, String responsable,
            String fase, String motivo, String razon, String modalidad, List<Instant> opciones, String estado,
            Instant fechaProgramada, String enlace, String motivoRechazo, Instant enviadaAt, ResumenVista resumen,
            boolean puedeCerrar) {
    }

    /** Las solicitudes de un proyecto y si el cliente puede pedir una nueva ahora. */
    public record SolicitudesDelProyecto(boolean puedeSolicitar, List<String> motivos, List<SolicitudVista> solicitudes) {
    }

    private final SolicitudesReunionJdbc datos;
    private final ProyectosParaComunicacion proyectos;
    private final CalendarioHabil calendario;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioReuniones(SolicitudesReunionJdbc datos, ProyectosParaComunicacion proyectos, CalendarioHabil calendario,
            RegistroAuditoria auditoria, SesionActual sesion, ApplicationEventPublisher eventos, Clock reloj) {
        this.datos = datos;
        this.proyectos = proyectos;
        this.calendario = calendario;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------------------------------- Consultar

    /** Las solicitudes de un proyecto: el cliente dueño, el Product Owner del equipo o el administrador. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public SolicitudesDelProyecto deProyecto(String codigo) {
        UsuarioSesion usuario = sesion.usuario();
        Proyecto p = buscar(codigo);
        boolean cliente = p.clienteId().equals(usuario.id());
        if (!cliente && !esAdministrador(usuario)) {
            exigirEquipo(p, usuario, "consultar las reuniones de");
        }
        List<SolicitudFila> filas = datos.deProyecto(p.id());
        boolean pendiente = filas.stream().anyMatch(s -> s.estado().equals("PENDIENTE"));
        boolean puede = cliente && p.estado().equals("ACTIVO") && p.estadoPlan().equals("APROBADO") && !pendiente;
        return new SolicitudesDelProyecto(puede, MOTIVOS, filas.stream().map(f -> vista(f, usuario)).toList());
    }

    /** Todas las solicitudes (administrador) o las de los proyectos del equipo (Product Owner). */
    @Transactional(readOnly = true)
    public List<SolicitudVista> listar() {
        UsuarioSesion usuario = sesion.usuario();
        UUID equipo = null;
        if (!esAdministrador(usuario)) {
            equipo = proyectos.equipoDelProductOwner(usuario.id()).orElse(null);
            if (equipo == null) {
                return List.of();
            }
        }
        return datos.listar(equipo).stream().map(f -> vista(f, usuario)).toList();
    }

    // ------------------------------------------------------------------------------------------- Solicitar (HU-15)

    /**
     * El cliente dueño pide una reunión con una o dos horas en hora de Colombia (RN-01). Una sola solicitud pendiente por
     * proyecto (RN-15); con el proyecto activo y el plan aprobado (RN-14). La fase la guarda el sistema: la que está en curso.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public SolicitudVista solicitar(String codigo, String motivo, String razon, String modalidad, Instant opcion1, Instant opcion2) {
        UsuarioSesion usuario = sesion.usuario();
        Proyecto p = buscar(codigo);
        if (!p.clienteId().equals(usuario.id())) {
            denegar("Intentó pedir una reunión en un proyecto ajeno", p.codigo());
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        proyectos.bloquear(p.id());
        if (!p.estado().equals("ACTIVO")) {
            throw new ConflictoException(ESTADO_NO_ADMITE);
        }
        if (!p.estadoPlan().equals("APROBADO")) {
            throw new ConflictoException(PLAN_SIN_APROBAR);
        }

        String razonFinal = razon == null ? "" : razon.trim();
        Instant ahora = Instant.now(reloj);
        Acumulador errores = new Acumulador();
        if (!MOTIVOS.contains(motivo)) {
            errores.agregar("motivo", "Elige el motivo de la reunión.");
        }
        if (razonFinal.isEmpty()) {
            errores.agregar("razon", "Cuéntanos por qué quieres la reunión.");
        } else if (razonFinal.length() < 20) {
            errores.agregar("razon", "Escribe al menos 20 caracteres.");
        } else if (razonFinal.length() > 500) {
            errores.agregar("razon", "Escribe máximo 500 caracteres.");
        }
        if (!"PRESENCIAL".equals(modalidad) && !"VIRTUAL".equals(modalidad)) {
            errores.agregar("modalidad", "Elige cómo quieres reunirte.");
        }
        if (opcion1 == null) {
            errores.agregar("opcion1", "Elige al menos una opción de día y hora.");
        } else if (!franjaValida(opcion1, ahora)) {
            errores.agregar("opcion1", mensajeFranja());
        }
        if (opcion2 != null) {
            if (!franjaValida(opcion2, ahora)) {
                errores.agregar("opcion2", mensajeFranja());
            } else if (opcion2.equals(opcion1)) {
                errores.agregar("opcion2", "Las dos opciones deben ser distintas");
            }
        }
        errores.lanzarSiHay();

        if (datos.deProyecto(p.id()).stream().anyMatch(s -> s.estado().equals("PENDIENTE"))) {
            throw new ConflictoException(YA_HAY_PENDIENTE);
        }
        Fase fase = proyectos.faseEnCurso(p.id()).orElseThrow(() -> new ConflictoException(ESTADO_NO_ADMITE));
        UUID id = datos.insertar(p.id(), usuario.id(), fase.id(), motivo, razonFinal, modalidad, opcion1, opcion2, ahora);
        SolicitudFila fila = datos.porId(id).orElseThrow();

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("proyecto", CambioDeValor.nuevo(p.codigo()));
        cambios.put("motivo", CambioDeValor.nuevo(motivo));
        cambios.put("modalidad", CambioDeValor.nuevo(modalidad));
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Solicitó una reunión", "solicitud", fila.codigo(), cambios);
        avisarAlEquipo(p, "REUNION_SOLICITADA", "Un cliente pidió una reunión",
                usuario.nombre() + " pidió una reunión sobre «" + motivo + "» en " + p.nombre() + ".");
        return vista(fila, usuario);
    }

    // ------------------------------------------------------------------------------------------- Responder (HU-16)

    /**
     * El Product Owner del equipo o el administrador eligen una de las horas propuestas y, si es virtual, ponen el enlace.
     * {@code opcion} es 1 o 2.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public SolicitudVista programar(UUID solicitudId, Integer opcion, String enlace) {
        UsuarioSesion usuario = sesion.usuario();
        SolicitudFila s = solicitud(solicitudId);
        Proyecto p = proyecto(s);
        exigirGestor(p, usuario, s.codigo());
        if (!s.estado().equals("PENDIENTE")) {
            throw new ConflictoException(YA_RESPONDIDA);
        }

        Acumulador errores = new Acumulador();
        Instant elegida = null;
        if (opcion == null || (opcion != 1 && !(opcion == 2 && s.opcion2() != null))) {
            errores.agregar("horario", "Elige uno de los horarios propuestos.");
        } else {
            elegida = opcion == 1 ? s.opcion1() : s.opcion2();
        }
        String enlaceFinal = enlace == null ? "" : enlace.trim();
        boolean virtual = s.modalidad().equals("VIRTUAL");
        if (virtual) {
            if (enlaceFinal.isEmpty()) {
                errores.agregar("enlace", "Pega el enlace: la reunión es virtual.");
            } else if (enlaceFinal.length() > 255 || !ENLACE.matcher(enlaceFinal).matches()) {
                errores.agregar("enlace", "El enlace debe empezar por https://");
            }
        }
        errores.lanzarSiHay();

        Instant ahora = Instant.now(reloj);
        if (!elegida.isAfter(ahora) || (p.equipoId() != null && proyectos.horarioOcupado(p.equipoId(), elegida, s.id()))) {
            throw new ConflictoException(HORARIO_NO_DISPONIBLE);
        }
        if (!datos.programar(s.id(), elegida, virtual ? enlaceFinal : null, usuario.id())) {
            throw new ConflictoException(DATOS_DESACTUALIZADOS);
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("PENDIENTE", "PROGRAMADA"));
        cambios.put("fechaProgramada", CambioDeValor.nuevo(elegida.toString()));
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Programó una reunión", "solicitud", s.codigo(), cambios);
        String cuando = FECHA.format(elegida.atZone(reloj.getZone()));
        avisarAlCliente(p, "REUNION_PROGRAMADA", "Tu reunión quedó programada",
                "Será el " + cuando + (virtual ? ", por videollamada." : ", en la oficina de CoreTech."));
        avisarAlEquipoSiLoHaceElAdministrador(p, usuario, "REUNION_PROGRAMADA", "Se programó una reunión",
                "La reunión con " + s.cliente() + " será el " + cuando + ".");
        return vista(datos.porId(s.id()).orElseThrow(), usuario);
    }

    /** Rechaza una solicitud pendiente con un motivo de 15 a 300 caracteres; el cliente lo ve y puede pedir otra (RN-15). */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public SolicitudVista rechazar(UUID solicitudId, String motivo) {
        UsuarioSesion usuario = sesion.usuario();
        SolicitudFila s = solicitud(solicitudId);
        Proyecto p = proyecto(s);
        exigirGestor(p, usuario, s.codigo());
        if (!s.estado().equals("PENDIENTE")) {
            throw new ConflictoException(YA_RESPONDIDA);
        }
        String texto = motivoValido(motivo, "Escribe el motivo: el cliente lo verá.");
        if (!datos.rechazar(s.id(), texto, usuario.id())) {
            throw new ConflictoException(DATOS_DESACTUALIZADOS);
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("PENDIENTE", "RECHAZADA"));
        cambios.put("motivo", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Rechazó una solicitud de reunión", "solicitud", s.codigo(), cambios);
        avisarAlCliente(p, "REUNION_RECHAZADA", "Tu solicitud de reunión no pudo programarse", recortar(texto, 255));
        avisarAlEquipoSiLoHaceElAdministrador(p, usuario, "REUNION_RECHAZADA", "Se rechazó una solicitud de reunión",
                "La solicitud de " + s.cliente() + " se rechazó.");
        return vista(datos.porId(s.id()).orElseThrow(), usuario);
    }

    /** El administrador cancela una reunión ya programada, con su motivo de 15 a 300 caracteres. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public SolicitudVista cancelar(UUID solicitudId, String motivo) {
        UsuarioSesion usuario = sesion.usuario();
        SolicitudFila s = solicitud(solicitudId);
        Proyecto p = proyecto(s);
        if (!esAdministrador(usuario)) {
            denegar("Intentó cancelar una reunión sin ser administrador", s.codigo());
            throw new ProhibidoException(SIN_PERMISO);
        }
        if (!s.estado().equals("PROGRAMADA")) {
            throw new ConflictoException(NO_SE_PUEDE_CANCELAR);
        }
        String texto = motivoValido(motivo, "Escribe el motivo: el cliente lo verá.");
        if (!datos.cancelar(s.id(), texto, usuario.id())) {
            throw new ConflictoException(DATOS_DESACTUALIZADOS);
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("PROGRAMADA", "CANCELADA"));
        cambios.put("motivo", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Canceló una reunión", "solicitud", s.codigo(), cambios);
        avisarAlCliente(p, "REUNION_CANCELADA", "Se canceló tu reunión", recortar(texto, 255));
        avisarAlEquipoSiLoHaceElAdministrador(p, usuario, "REUNION_CANCELADA", "Se canceló una reunión",
                "La reunión con " + s.cliente() + " se canceló.");
        return vista(datos.porId(s.id()).orElseThrow(), usuario);
    }

    // ------------------------------------------------------------------------------------------- Resumen (D-42)

    /**
     * Después de la hora programada, el Product Owner del equipo cuenta qué se habló, qué pidió el cliente y cuál es el
     * siguiente paso. La solicitud queda ATENDIDA y el resumen no se edita.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public SolicitudVista cerrar(UUID solicitudId, String resumen, String quePidio, String siguientePaso) {
        UsuarioSesion usuario = sesion.usuario();
        SolicitudFila s = solicitud(solicitudId);
        Proyecto p = proyecto(s);
        if (esAdministrador(usuario)) {
            denegar("Intentó cerrar una reunión sin ser Product Owner", s.codigo());
            throw new ProhibidoException("Solo el desarrollador (Product Owner) del equipo llena el resumen de la reunión");
        }
        exigirEquipo(p, usuario, "cerrar una reunión de");
        if (!s.estado().equals("PROGRAMADA") || s.resumen() != null) {
            throw new ConflictoException(YA_NO_SE_CIERRA);
        }
        Instant ahora = Instant.now(reloj);
        if (ahora.isBefore(s.fechaProgramada())) {
            throw new ConflictoException(AUN_NO_OCURRIO);
        }

        String resumenFinal = resumen == null ? "" : resumen.trim();
        String pidioFinal = quePidio == null ? "" : quePidio.trim();
        Acumulador errores = new Acumulador();
        if (resumenFinal.isEmpty()) {
            errores.agregar("resumen", "Cuenta qué se habló en la reunión.");
        } else if (resumenFinal.length() < 30) {
            errores.agregar("resumen", "Escribe al menos 30 caracteres.");
        } else if (resumenFinal.length() > 1000) {
            errores.agregar("resumen", "Escribe máximo 1000 caracteres.");
        }
        if (pidioFinal.isEmpty()) {
            errores.agregar("quePidio", "Escribe qué pidió el cliente.");
        } else if (pidioFinal.length() < 20) {
            errores.agregar("quePidio", "Escribe al menos 20 caracteres.");
        } else if (pidioFinal.length() > 600) {
            errores.agregar("quePidio", "Escribe máximo 600 caracteres.");
        }
        if (siguientePaso == null || !SIGUIENTES_PASOS.contains(siguientePaso)) {
            errores.agregar("siguientePaso", "Elige cuál es el siguiente paso.");
        }
        errores.lanzarSiHay();

        if (!datos.marcarAtendida(s.id())) {
            throw new ConflictoException(YA_NO_SE_CIERRA);
        }
        datos.insertarResumen(s.id(), resumenFinal, pidioFinal, siguientePaso, usuario.id(), ahora);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("PROGRAMADA", "ATENDIDA"));
        cambios.put("siguientePaso", CambioDeValor.nuevo(siguientePaso));
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Registró el resumen de una reunión", "solicitud", s.codigo(), cambios);
        avisarAlCliente(p, "REUNION_ATENDIDA", "Ya está el resumen de tu reunión", "Entra a tu proyecto para leer qué quedó acordado.");
        return vista(datos.porId(s.id()).orElseThrow(), usuario);
    }

    // ------------------------------------------------------------------------------------------- Reglas

    /** RN-01: día hábil futuro y hora de inicio de 8 a 11 o de 14 a 17, en hora de Colombia. */
    private boolean franjaValida(Instant instante, Instant ahora) {
        ZonedDateTime z = instante.atZone(reloj.getZone());
        int hora = z.getHour();
        boolean horaValida = z.getMinute() == 0 && z.getSecond() == 0 && ((hora >= 8 && hora <= 11) || (hora >= 14 && hora <= 17));
        return instante.isAfter(ahora) && horaValida && calendario.esDiaHabil(z.toLocalDate());
    }

    private static String mensajeFranja() {
        return "Elige un día hábil y una hora de inicio entre 8:00 a. m. y 11:00 a. m., o entre 2:00 p. m. y 5:00 p. m. (hora de Colombia).";
    }

    private static String motivoValido(String motivo, String vacio) {
        String texto = motivo == null ? "" : motivo.trim();
        Acumulador errores = new Acumulador();
        if (texto.isEmpty()) {
            errores.agregar("motivo", vacio);
        } else if (texto.length() < 15) {
            errores.agregar("motivo", "Escribe al menos 15 caracteres.");
        } else if (texto.length() > 300) {
            errores.agregar("motivo", "Escribe máximo 300 caracteres.");
        }
        errores.lanzarSiHay();
        return texto;
    }

    private SolicitudVista vista(SolicitudFila s, UsuarioSesion usuario) {
        List<Instant> opciones = new ArrayList<>();
        opciones.add(s.opcion1());
        if (s.opcion2() != null) {
            opciones.add(s.opcion2());
        }
        ResumenVista resumen = s.resumen() == null ? null
                : new ResumenVista(s.resumen().resumen(), s.resumen().quePidio(), s.resumen().siguientePaso(), s.resumen().registradoAt());
        boolean puedeCerrar = !esAdministrador(usuario) && !s.clienteId().equals(usuario.id()) && s.estado().equals("PROGRAMADA")
                && s.fechaProgramada() != null && !Instant.now(reloj).isBefore(s.fechaProgramada());
        return new SolicitudVista(s.id(), s.codigo(), s.proyectoCodigo(), s.proyectoNombre(), s.cliente(),
                s.responsable() == null ? "" : s.responsable(), s.fase(), s.motivo(), s.razon(), s.modalidad(), opciones, s.estado(),
                s.fechaProgramada(), s.enlace(), s.motivoRechazo(), s.enviadaAt(), resumen, puedeCerrar);
    }

    // ------------------------------------------------------------------------------------------- Permisos

    private static boolean esAdministrador(UsuarioSesion usuario) {
        return usuario.tienePermiso("proyectos.ver_todos");
    }

    private Proyecto buscar(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        return proyectos.porCodigo(codigo).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private SolicitudFila solicitud(UUID id) {
        return datos.porId(id).orElseThrow(() -> new NoEncontradoException(SOLICITUD_NO_ENCONTRADA));
    }

    private Proyecto proyecto(SolicitudFila s) {
        return proyectos.porId(s.proyectoId()).orElseThrow(() -> new NoEncontradoException(SOLICITUD_NO_ENCONTRADA));
    }

    /** Programar o rechazar: el Product Owner del equipo o el administrador. */
    private void exigirGestor(Proyecto p, UsuarioSesion usuario, String codigoSolicitud) {
        if (esAdministrador(usuario)) {
            return;
        }
        Optional<UUID> equipo = proyectos.equipoDelProductOwner(usuario.id());
        if (equipo.isEmpty() || !equipo.get().equals(p.equipoId())) {
            denegar("Intentó responder una solicitud de reunión de otro equipo", codigoSolicitud);
            throw new ProhibidoException(SIN_PERMISO);
        }
    }

    private void exigirEquipo(Proyecto p, UsuarioSesion usuario, String accion) {
        Optional<UUID> equipo = proyectos.equipoDelProductOwner(usuario.id());
        if (equipo.isEmpty() || !equipo.get().equals(p.equipoId())) {
            denegar("Intentó " + accion + " otro equipo", p.codigo());
            if (usuario.tienePermiso("proyectos.registrar_avance") || usuario.tienePermiso("reuniones.programar")) {
                throw new ProhibidoException(SIN_PERMISO);
            }
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
    }

    private void denegar(String accion, String entidadId) {
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, accion, "solicitud", entidadId, new LinkedHashMap<>());
    }

    // ------------------------------------------------------------------------------------------- Avisos

    private void avisarAlCliente(Proyecto p, String tipo, String titulo, String texto) {
        eventos.publishEvent(AvisoSolicitado.para(p.clienteId(), tipo, recortar(titulo, 80), recortar(texto, 255),
                "/cliente/proyectos/" + p.codigo()));
    }

    private void avisarAlEquipo(Proyecto p, String tipo, String titulo, String texto) {
        if (p.equipoId() != null) {
            proyectos.productOwnerDe(p.equipoId()).map(Persona::usuarioId).ifPresent(destino -> eventos
                    .publishEvent(AvisoSolicitado.para(destino, tipo, recortar(titulo, 80), recortar(texto, 255), "/equipo/citas")));
        }
    }

    /** Si lo hizo el administrador en nombre del responsable, el Product Owner también se entera. */
    private void avisarAlEquipoSiLoHaceElAdministrador(Proyecto p, UsuarioSesion usuario, String tipo, String titulo, String texto) {
        if (esAdministrador(usuario)) {
            avisarAlEquipo(p, tipo, titulo, texto);
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
