package co.coretech.portal.seguimiento.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.comunicacion.AvisoSolicitado;
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
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.ProductOwner;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento.Proyecto;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.FaseDeProyecto;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.FaseEnRevision;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.FaseFila;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.PasoContexto;

/**
 * El ciclo de una fase (HU-11, HU-12, HU-13; RN-08, RN-13, RN-16, RN-17, RN-31): entregar o cerrar, aprobar, pedir cambios,
 * registrar correcciones y la garantía.
 * <p>
 * Una fase que aprueba el cliente se entrega (EN_REVISION, 3 días hábiles) y la aprueba él; una que no, la cierra el equipo.
 * Al aprobar una fase empieza la siguiente; al aprobar la última (con todas las demás aprobadas) el proyecto llega al 100 % y
 * queda EN_GARANTIA 30 días. El porcentaje lo calcula la base ({@code tg_recalcular_avance}): aquí solo se cambia el estado de
 * la fase y se relee el proyecto. Cada acción bloquea el proyecto, así dos acciones a la vez no se pisan.
 * <p>
 * Los accesos no permitidos (otro equipo, proyecto ajeno) quedan en la auditoría aunque respondan 403 o 404, por eso los
 * métodos no revierten en esos dos casos.
 */
@Service
public class ServicioFases {

    static final String FASE_NO_ENCONTRADA = "Fase no encontrada";
    static final String NO_ESPERA_APROBACION = "Esta fase no está esperando tu aprobación";
    static final String CORRECCIONES_ABIERTAS = "Termina las correcciones antes de entregar";
    static final String SIN_RONDAS = "Ya usaste las 2 rondas; un nuevo cambio se cotiza";
    static final String GARANTIA_TERMINADA = "La garantía ya terminó; solo se terminan las correcciones abiertas";
    static final String SOLO_FASES_EN_CURSO_O_APROBADAS = "Solo se corrigen fases en curso o aprobadas";
    static final int DIAS_DE_PLAZO = 3;
    static final int DIAS_PARA_RECORDAR = 2;
    static final int DIAS_DE_GARANTIA = 30;
    static final int RONDAS_DE_AJUSTE = 2;
    static final List<String> ORIGENES = List.of("Lo pidió el cliente", "La encontramos al construir", "La encontramos en pruebas");

    /** Resultado de entregar o cerrar una fase (HU-13). {@code plazoAprobacion} solo viene con EN_REVISION. */
    public record Entregada(String estado, LocalDate plazoAprobacion, BigDecimal avancePorcentaje, String estadoProyecto,
            LocalDate garantiaHasta) {
    }

    /** Resultado de aprobar una fase (HU-11). */
    public record Aprobada(String estado, BigDecimal avancePorcentaje, String estadoProyecto, LocalDate garantiaHasta) {
    }

    /** Resultado de pedir cambios (HU-11): {@code rondasUsadas} de 2 y el estado en que queda la fase. */
    public record CambioPedido(UUID solicitudId, String tipo, String estado, int rondasUsadas, String estadoFase, UUID pasoId) {
    }

    /** Resultado de registrar una corrección (HU-12). */
    public record CorreccionRegistrada(UUID pasoId, String estadoFase, BigDecimal avancePorcentaje) {
    }

    private final SeguimientoJdbc datos;
    private final ProyectosParaSeguimiento proyectos;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final CalendarioHabil calendario;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioFases(SeguimientoJdbc datos, ProyectosParaSeguimiento proyectos, EquipoConsultas equipos,
            DirectorioUsuarios usuarios, CalendarioHabil calendario, RegistroAuditoria auditoria, SesionActual sesion,
            ApplicationEventPublisher eventos, Clock reloj) {
        this.datos = datos;
        this.proyectos = proyectos;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.calendario = calendario;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------------------------------- Entregar o cerrar (HU-13)

    /**
     * El desarrollador (Product Owner) entrega la fase que aprueba el cliente (queda EN_REVISION, 3 días hábiles) o cierra la
     * que cierra el equipo (queda APROBADA y empieza la siguiente). Exige todos los pasos hechos y ninguna corrección abierta
     * en esa misma fase.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Entregada entregar(UUID faseId) {
        UsuarioSesion usuario = sesion.usuario();
        Cargada c = cargar(faseId);
        exigirProductOwner(c.proyecto, usuario, "entregar una fase de");
        exigirActivo(c.proyecto);
        FaseFila fase = c.fase;
        if (!fase.estado().equals("EN_CURSO")) {
            throw new ConflictoException("Esta fase no está en curso");
        }
        int[] pendientes = datos.pendientes(fase.id());
        if (pendientes[0] > 0) {
            throw new ConflictoException(CORRECCIONES_ABIERTAS);
        }
        if (pendientes[1] > 0) {
            throw new ConflictoException(pendientes[1] == 1 ? "Falta 1 paso por terminar" : "Faltan " + pendientes[1] + " pasos por terminar");
        }

        Instant ahora = Instant.now(reloj);
        LocalDate hoy = LocalDate.now(reloj);
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("fase", CambioDeValor.nuevo(fase.nombre()));
        if (fase.apruebaCliente()) {
            LocalDate plazo = calendario.sumarDiasHabiles(hoy, DIAS_DE_PLAZO);
            if (!datos.entregarParaRevision(fase.id(), ahora, plazo)) {
                throw new ConflictoException(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
            }
            cambios.put("estado", CambioDeValor.de("EN_CURSO", "EN_REVISION"));
            auditoria.registrar(CategoriaAuditoria.AVANCES, "Entregó una fase al cliente", "proyecto", c.proyecto.codigo(), cambios);
            avisarAlCliente(c.proyecto, "FASE_ENTREGADA", "Tienes una fase por revisar",
                    c.proyecto.nombre() + ": revisa «" + fase.nombre() + "». Tienes hasta el " + Fechas.conDia(plazo) + " para aprobarla o pedir cambios.");
            Proyecto p = releer(c.proyecto);
            return new Entregada("EN_REVISION", plazo, p.avance(), p.estado(), p.garantiaHasta());
        }

        if (!datos.aprobar(fase.id(), ahora, usuario.id(), false)) {
            throw new ConflictoException(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
        }
        cambios.put("estado", CambioDeValor.de("EN_CURSO", "APROBADA"));
        auditoria.registrar(CategoriaAuditoria.AVANCES, "Cerró una fase", "proyecto", c.proyecto.codigo(), cambios);
        continuarDespuesDeAprobar(c.proyecto, ahora, hoy);
        Proyecto p = releer(c.proyecto);
        avisarAlCliente(c.proyecto, "FASE_CERRADA", "El equipo cerró una fase",
                c.proyecto.nombre() + ": el equipo cerró «" + fase.nombre() + "»." + textoDeCierre(p));
        return new Entregada("APROBADA", null, p.avance(), p.estado(), p.garantiaHasta());
    }

    // ------------------------------------------------------------------------------------------- Aprobar o pedir cambios (HU-11)

    /** El cliente dueño aprueba la fase entregada: suma su porcentaje y empieza la siguiente. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Aprobada aprobar(UUID faseId) {
        UsuarioSesion usuario = sesion.usuario();
        Cargada c = cargar(faseId);
        exigirCliente(c.proyecto, usuario, "aprobar una fase de");
        exigirEnRevision(c);

        Instant ahora = Instant.now(reloj);
        if (!datos.aprobar(c.fase.id(), ahora, usuario.id(), false)) {
            throw new ConflictoException(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
        }
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("fase", CambioDeValor.nuevo(c.fase.nombre()));
        cambios.put("estado", CambioDeValor.de("EN_REVISION", "APROBADA"));
        auditoria.registrar(CategoriaAuditoria.AVANCES, "Aprobó una fase", "proyecto", c.proyecto.codigo(), cambios);
        continuarDespuesDeAprobar(c.proyecto, ahora, LocalDate.now(reloj));
        Proyecto p = releer(c.proyecto);
        avisarAlEquipo(c.proyecto, "FASE_APROBADA", c.proyecto.nombre() + ": el cliente aprobó una fase",
                nombreCliente(c.proyecto) + " aprobó «" + c.fase.nombre() + "»." + textoDeCierre(p));
        return new Aprobada("APROBADA", p.avance(), p.estado(), p.garantiaHasta());
    }

    /**
     * El cliente pide cambios en la fase entregada. Un AJUSTE usa una de las 2 rondas: la fase vuelve a EN_CURSO con un paso de
     * tipo AJUSTE. Algo nuevo (ALCANCE) queda POR_COTIZAR y la fase sigue esperando su respuesta; se cotiza con un cambio de
     * plan (P21). El origen del paso lo pone el servicio: «Lo pidió el cliente» (D-44).
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public CambioPedido pedirCambio(UUID faseId, String tipo, String descripcion) {
        UsuarioSesion usuario = sesion.usuario();
        Cargada c = cargar(faseId);
        exigirCliente(c.proyecto, usuario, "pedir cambios en una fase de");
        exigirEnRevision(c);

        String texto = descripcion == null ? "" : descripcion.trim();
        String po = productOwnerDe(c.proyecto);
        Acumulador errores = new Acumulador();
        if (tipo == null || !(tipo.equals("AJUSTE") || tipo.equals("ALCANCE"))) {
            errores.agregar("tipo", "Elige si es un ajuste o algo nuevo.");
        }
        if (texto.isEmpty()) {
            errores.agregar("descripcion", "Cuéntanos qué quieres cambiar.");
        } else if (texto.length() < 20) {
            errores.agregar("descripcion", "Escribe al menos 20 caracteres para que " + (po.isEmpty() ? "el equipo" : po) + " te entienda.");
        } else if (texto.length() > 800) {
            errores.agregar("descripcion", "Escribe máximo 800 caracteres.");
        }
        errores.lanzarSiHay();

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("fase", CambioDeValor.nuevo(c.fase.nombre()));
        cambios.put("tipo", CambioDeValor.nuevo(tipo));
        cambios.put("descripcion", CambioDeValor.nuevo(texto));
        if (tipo.equals("ALCANCE")) {
            UUID solicitud = datos.insertarSolicitud(c.fase.id(), usuario.id(), "ALCANCE", texto, "POR_COTIZAR", null);
            auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Pidió algo nuevo en una fase", "proyecto", c.proyecto.codigo(), cambios);
            avisarAlEquipo(c.proyecto, "CAMBIO_ALCANCE", c.proyecto.nombre() + ": el cliente pidió algo nuevo",
                    nombreCliente(c.proyecto) + " pidió algo que no estaba en el plan: «" + texto + "». Hay que cotizarlo.");
            return new CambioPedido(solicitud, "ALCANCE", "POR_COTIZAR", c.fase.rondasAjusteUsadas(), "EN_REVISION", null);
        }

        if (c.fase.rondasAjusteUsadas() >= RONDAS_DE_AJUSTE) {
            throw new ConflictoException("tipo", SIN_RONDAS);
        }
        if (!datos.devolverPorAjuste(c.fase.id())) {
            throw new ConflictoException(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
        }
        int ronda = c.fase.rondasAjusteUsadas() + 1;
        UUID paso = datos.insertarPaso(c.fase.id(), "AJUSTE", "Ajuste " + ronda + " de " + RONDAS_DE_AJUSTE, recortar(texto, 400),
                "Lo pidió el cliente");
        UUID solicitud = datos.insertarSolicitud(c.fase.id(), usuario.id(), "AJUSTE", texto, "ABIERTO", paso);
        cambios.put("ronda", CambioDeValor.nuevo(ronda + " de " + RONDAS_DE_AJUSTE));
        auditoria.registrar(CategoriaAuditoria.SOLICITUDES, "Pidió un ajuste en una fase", "proyecto", c.proyecto.codigo(), cambios);
        avisarAlEquipo(c.proyecto, "FASE_AJUSTE", c.proyecto.nombre() + ": el cliente pidió un ajuste",
                nombreCliente(c.proyecto) + " pidió el ajuste " + ronda + " de " + RONDAS_DE_AJUSTE + " en «" + c.fase.nombre() + "»: «" + texto + "».");
        return new CambioPedido(solicitud, "AJUSTE", "ABIERTO", ronda, "EN_CURSO", paso);
    }

    // ------------------------------------------------------------------------------------------- Correcciones (HU-12)

    /**
     * El desarrollador (Product Owner) registra una corrección en una fase en curso o aprobada. Si la fase ya estaba aprobada,
     * pasa a EN_CORRECCION y deja de sumar su porcentaje (RN-16); con el proyecto EN_GARANTIA sigue aprobada y el avance sigue en
     * 100 %. Dentro de la garantía solo mientras no se cumplan los 30 días.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public CorreccionRegistrada registrarCorreccion(UUID faseId, String origen, String titulo, String explicacion) {
        UsuarioSesion usuario = sesion.usuario();
        Cargada c = cargar(faseId);
        exigirProductOwner(c.proyecto, usuario, "corregir una fase de");
        LocalDate hoy = LocalDate.now(reloj);
        Proyecto proyecto = c.proyecto;
        boolean enGarantia = proyecto.estado().equals("EN_GARANTIA");
        if (!proyecto.estadoPlan().equals("APROBADO") || !(proyecto.estado().equals("ACTIVO") || enGarantia)) {
            throw new ConflictoException(ServicioSeguimiento.ESTADO_NO_ADMITE);
        }
        if (enGarantia && proyecto.garantiaHasta() != null && !hoy.isBefore(proyecto.garantiaHasta())) {
            throw new ConflictoException(GARANTIA_TERMINADA);
        }
        FaseFila fase = c.fase;
        if (fase.estado().equals("EN_REVISION")) {
            throw new ConflictoException("Esta fase espera la respuesta del cliente: no se le agregan correcciones");
        }
        if (fase.estado().equals("PENDIENTE")) {
            throw new ConflictoException(SOLO_FASES_EN_CURSO_O_APROBADAS);
        }

        String nombre = titulo == null ? "" : titulo.trim();
        String detalle = explicacion == null ? "" : explicacion.trim();
        String desde = origen == null ? "" : origen.trim();
        Acumulador errores = new Acumulador();
        if (!ORIGENES.contains(desde)) {
            errores.agregar("origen", "Elige de dónde sale la corrección.");
        }
        if (nombre.isEmpty()) {
            errores.agregar("titulo", "Escribe un nombre corto.");
        } else if (nombre.length() < 3) {
            errores.agregar("titulo", "Escribe al menos 3 caracteres.");
        } else if (nombre.length() > 80) {
            errores.agregar("titulo", "Escribe máximo 80 caracteres.");
        }
        if (detalle.isEmpty()) {
            errores.agregar("explicacion", "Explícale a " + nombreCliente(proyecto) + " qué se va a corregir y por qué.");
        } else if (detalle.length() < 20) {
            errores.agregar("explicacion", "Escribe al menos 20 caracteres.");
        } else if (detalle.length() > 400) {
            errores.agregar("explicacion", "Escribe máximo 400 caracteres.");
        }
        errores.lanzarSiHay();

        UUID paso = datos.insertarPaso(fase.id(), "CORRECCION", nombre, detalle, desde);
        String estadoFase = fase.estado();
        if (fase.estado().equals("APROBADA") && !enGarantia) {
            datos.ponerEnCorreccion(fase.id());
            estadoFase = "EN_CORRECCION";
        }
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("fase", CambioDeValor.nuevo(fase.nombre()));
        cambios.put("correccion", CambioDeValor.nuevo(nombre));
        cambios.put("origen", CambioDeValor.nuevo(desde));
        if (!estadoFase.equals(fase.estado())) {
            cambios.put("estadoFase", CambioDeValor.de(fase.estado(), estadoFase));
        }
        auditoria.registrar(CategoriaAuditoria.AVANCES, "Registró una corrección", "proyecto", proyecto.codigo(), cambios);
        Proyecto p = releer(proyecto);
        avisarAlCliente(proyecto, "CORRECCION_ABIERTA", "Estamos corrigiendo algo en " + proyecto.nombre(),
                "El equipo registró una corrección en «" + fase.nombre() + "»: " + nombre + "."
                        + (estadoFase.equals("EN_CORRECCION") ? " Mientras tanto la fase no suma su porcentaje." : ""));
        return new CorreccionRegistrada(paso, estadoFase, p.avance());
    }

    /**
     * Lo que pasa cuando se marca hecho un paso (lo llama {@link ServicioSeguimiento#marcarHecho}): un ajuste resuelto cierra su
     * solicitud; la última corrección de una fase en corrección la devuelve a APROBADA (vuelve a sumar) y puede completar el
     * proyecto; en garantía cumplida, la última corrección cierra el proyecto.
     */
    public void alTerminarPaso(Proyecto proyecto, PasoContexto paso) {
        Instant ahora = Instant.now(reloj);
        LocalDate hoy = LocalDate.now(reloj);
        if (paso.tipo().equals("AJUSTE") || paso.tipo().equals("ALCANCE")) {
            datos.resolverSolicitudDelPaso(paso.pasoId());
            return;
        }
        if (!paso.tipo().equals("CORRECCION")) {
            return;
        }
        if (paso.faseEstado().equals("EN_CORRECCION") && datos.pendientes(paso.faseId())[0] == 0) {
            datos.volverAAprobada(paso.faseId());
            completarSiCorresponde(proyecto, hoy);
        }
        if (proyecto.estado().equals("EN_GARANTIA")) {
            finalizarSiCumplida(proyecto.id(), hoy, ahora);
        }
    }

    // ------------------------------------------------------------------------------------------- Tareas programadas

    /** Pasados 2 días hábiles desde la entrega, un recordatorio al cliente, una sola vez (RN-17). */
    @Transactional
    public boolean recordarRevision(FaseEnRevision fase) {
        Proyecto p = proyectos.porId(fase.proyectoId()).orElse(null);
        if (p == null || fase.recordada() || fase.entregadaAt() == null) {
            return false;
        }
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate entregada = fase.entregadaAt().atZone(reloj.getZone()).toLocalDate();
        if (calendario.diasHabilesEntre(entregada, hoy) < DIAS_PARA_RECORDAR) {
            return false;
        }
        datos.marcarRecordada(fase.faseId());
        avisarAlCliente(p, "FASE_RECORDATORIO", "Tu fase espera tu respuesta",
                p.nombre() + ": aprueba «" + fase.nombre() + "» o pide cambios antes del " + Fechas.conDia(fase.plazoAprobacion())
                        + "; si no respondes, se aprueba sola.");
        return true;
    }

    /** Vencido el plazo de 3 días hábiles sin respuesta, la fase se aprueba sola y se registra como automática (RN-17). */
    @Transactional
    public boolean aprobarPorVencimiento(FaseEnRevision fase) {
        LocalDate hoy = LocalDate.now(reloj);
        if (!hoy.isAfter(fase.plazoAprobacion())) {
            return false;
        }
        proyectos.bloquear(fase.proyectoId());
        Proyecto p = proyectos.porId(fase.proyectoId()).orElse(null);
        Instant ahora = Instant.now(reloj);
        if (p == null || !p.estado().equals("ACTIVO") || !datos.aprobar(fase.faseId(), ahora, null, true)) {
            return false;
        }
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("fase", CambioDeValor.nuevo(fase.nombre()));
        cambios.put("estado", CambioDeValor.de("EN_REVISION", "APROBADA"));
        cambios.put("aprobacionAutomatica", CambioDeValor.nuevo(true));
        auditoria.registrar(CategoriaAuditoria.AVANCES, "La fase se aprobó sola por falta de respuesta", "proyecto", p.codigo(), cambios);
        continuarDespuesDeAprobar(p, ahora, hoy);
        Proyecto nuevo = releer(p);
        avisarAlCliente(p, "FASE_APROBADA_AUTO", "Se aprobó una fase de tu proyecto",
                p.nombre() + ": «" + fase.nombre() + "» se aprobó sola porque no respondiste en " + DIAS_DE_PLAZO + " días hábiles."
                        + textoDeCierre(nuevo));
        avisarAlEquipo(p, "FASE_APROBADA_AUTO", p.nombre() + ": se aprobó una fase",
                "«" + fase.nombre() + "» se aprobó sola: el cliente no respondió en " + DIAS_DE_PLAZO + " días hábiles." + textoDeCierre(nuevo));
        return true;
    }

    /** Cumplidos los 30 días de garantía y sin correcciones abiertas, el proyecto queda FINALIZADO (RN-13, RN-31). */
    @Transactional
    public boolean cerrarGarantia(UUID proyectoId) {
        proyectos.bloquear(proyectoId);
        return finalizarSiCumplida(proyectoId, LocalDate.now(reloj), Instant.now(reloj));
    }

    // ------------------------------------------------------------------------------------------- Pasos internos

    private record Cargada(Proyecto proyecto, FaseFila fase) {
    }

    /** Lee la fase, bloquea el proyecto y la vuelve a leer: lo que se decide es sobre el estado que quedó tras el bloqueo. */
    private Cargada cargar(UUID faseId) {
        FaseDeProyecto antes = datos.faseConProyecto(faseId).orElseThrow(() -> new NoEncontradoException(FASE_NO_ENCONTRADA));
        proyectos.bloquear(antes.proyectoId());
        FaseDeProyecto fase = datos.faseConProyecto(faseId).orElseThrow(() -> new NoEncontradoException(FASE_NO_ENCONTRADA));
        Proyecto p = proyectos.porId(fase.proyectoId()).orElseThrow(() -> new NoEncontradoException(ServicioSeguimiento.NO_ENCONTRADO));
        return new Cargada(p, fase.fase());
    }

    private Proyecto releer(Proyecto p) {
        return proyectos.porId(p.id()).orElse(p);
    }

    /** La siguiente fase empieza (RN-08); si ya no queda ninguna por empezar, se mira si el proyecto quedó completo. */
    private void continuarDespuesDeAprobar(Proyecto p, Instant ahora, LocalDate hoy) {
        Optional<FaseFila> siguiente = datos.siguientePendiente(p.id());
        if (siguiente.isPresent()) {
            datos.iniciar(siguiente.get().id(), ahora);
        } else {
            completarSiCorresponde(p, hoy);
        }
    }

    /** Todas las fases aprobadas (ninguna en corrección): 100 % y garantía de 30 días (RN-13, RN-31). */
    private void completarSiCorresponde(Proyecto p, LocalDate hoy) {
        Proyecto actual = releer(p);
        if (!actual.estado().equals("ACTIVO") || datos.fasesSinAprobar(p.id()) > 0) {
            return;
        }
        LocalDate hasta = hoy.plusDays(DIAS_DE_GARANTIA);
        proyectos.iniciarGarantia(p.id(), hasta);
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("ACTIVO", "EN_GARANTIA"));
        cambios.put("garantiaHasta", CambioDeValor.nuevo(hasta.toString()));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "El proyecto completó todas sus fases", "proyecto", p.codigo(), cambios);
        avisarAlCliente(p, "PROYECTO_EN_GARANTIA", "¡Tu proyecto está terminado!",
                p.nombre() + " llegó al 100 %. Tiene 30 días de garantía, hasta el " + Fechas.conDia(hasta) + ": si algo falla, lo corregimos sin costo.");
        avisarAlEquipo(p, "PROYECTO_EN_GARANTIA", p.nombre() + " llegó al 100 %",
                "Todas las fases quedaron aprobadas. El proyecto está en garantía hasta el " + Fechas.conDia(hasta) + ": solo se registran correcciones.");
    }

    private boolean finalizarSiCumplida(UUID proyectoId, LocalDate hoy, Instant ahora) {
        Proyecto p = proyectos.porId(proyectoId).orElse(null);
        if (p == null || !p.estado().equals("EN_GARANTIA") || p.garantiaHasta() == null || hoy.isBefore(p.garantiaHasta())) {
            return false;
        }
        if (datos.correccionesAbiertasDelProyecto(proyectoId) > 0) {
            return false;
        }
        proyectos.finalizar(proyectoId, ahora);
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("EN_GARANTIA", "FINALIZADO"));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "El proyecto terminó su garantía", "proyecto", p.codigo(), cambios);
        avisarAlCliente(p, "PROYECTO_FINALIZADO", "Tu proyecto terminó su garantía",
                p.nombre() + " quedó finalizado. ¡Gracias por confiar en nosotros!");
        avisarAlEquipo(p, "PROYECTO_FINALIZADO", p.nombre() + " quedó finalizado", "Terminó la garantía y no quedan correcciones abiertas.");
        return true;
    }

    private static String textoDeCierre(Proyecto p) {
        return p.estado().equals("EN_GARANTIA") && p.garantiaHasta() != null
                ? " El proyecto llegó al 100 % y queda en garantía hasta el " + Fechas.conDia(p.garantiaHasta()) + "."
                : "";
    }

    // ------------------------------------------------------------------------------------------- Permisos y estado

    private void exigirProductOwner(Proyecto p, UsuarioSesion usuario, String accion) {
        Optional<UUID> equipo = equipos.equipoDelProductOwner(usuario.id());
        if (equipo.isEmpty() || !equipo.get().equals(p.equipoId())) {
            denegar("Intentó " + accion + " un proyecto de otro equipo", p);
            throw new ProhibidoException(ServicioSeguimiento.SOLO_EL_PRODUCT_OWNER);
        }
    }

    /** Un proyecto de otro cliente no se revela: 404 y queda en la auditoría (RN-10). */
    private void exigirCliente(Proyecto p, UsuarioSesion usuario, String accion) {
        if (!p.clienteId().equals(usuario.id())) {
            denegar("Intentó " + accion + " un proyecto ajeno", p);
            throw new NoEncontradoException(ServicioSeguimiento.NO_ENCONTRADO);
        }
    }

    private void denegar(String accion, Proyecto p) {
        auditoria.registrar(CategoriaAuditoria.AVANCES, accion, "proyecto", p.codigo(), new LinkedHashMap<>());
    }

    private static void exigirActivo(Proyecto p) {
        if (!p.estado().equals("ACTIVO") || !p.estadoPlan().equals("APROBADO")) {
            throw new ConflictoException(ServicioSeguimiento.ESTADO_NO_ADMITE);
        }
    }

    private static void exigirEnRevision(Cargada c) {
        if (!c.proyecto.estado().equals("ACTIVO") || !c.fase.estado().equals("EN_REVISION") || !c.fase.apruebaCliente()) {
            throw new ConflictoException(NO_ESPERA_APROBACION);
        }
    }

    // ------------------------------------------------------------------------------------------- Avisos

    private String nombreCliente(Proyecto p) {
        ContactoCorreo c = usuarios.perfiles(List.of(p.clienteId())).get(p.clienteId());
        return c == null || c.nombre().isBlank() ? "el cliente" : c.nombre().trim().split("\\s+")[0];
    }

    private String productOwnerDe(Proyecto p) {
        return p.equipoId() == null ? "" : equipos.productOwner(p.equipoId()).map(ProductOwner::nombre).orElse("");
    }

    private void avisarAlCliente(Proyecto p, String tipo, String titulo, String texto) {
        eventos.publishEvent(AvisoSolicitado.para(p.clienteId(), tipo, recortar(titulo, 80), recortar(texto, 255), "/cliente/proyectos/" + p.codigo()));
    }

    private void avisarAlEquipo(Proyecto p, String tipo, String titulo, String texto) {
        if (p.equipoId() == null) {
            return;
        }
        equipos.productOwner(p.equipoId()).ifPresent(po -> eventos.publishEvent(
                AvisoSolicitado.para(po.usuarioId(), tipo, recortar(titulo, 80), recortar(texto, 255), "/equipo/proyectos/" + p.codigo() + "/registrar")));
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
