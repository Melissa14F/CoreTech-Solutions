package co.coretech.portal.seguimiento.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.aplicacion.AlmacenArchivos;
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
import co.coretech.portal.especialidad.EquipoConsultas.Integrante;
import co.coretech.portal.especialidad.EquipoConsultas.ProductOwner;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento.Proyecto;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.AdjuntoFila;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.FaseFila;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.PasoContexto;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc.PasoFila;

/**
 * Seguimiento del proyecto: registrar el trabajo de la fase actual (HU-03) y consultar el estado (HU-04).
 * <p>
 * Solo el Product Owner del equipo asignado registra (RN-07); el cliente ve únicamente sus proyectos (RN-10: uno ajeno
 * responde 404 como si no existiera). Lo publicado (un paso HECHO) no se edita ni se borra (RN-09): lo garantiza también
 * la base con {@code tg_proteger_pasos}. Los accesos no permitidos quedan en la auditoría aunque la petición responda
 * 403 o 404, por eso los métodos no revierten en esos dos casos.
 */
@Service
public class ServicioSeguimiento {

    static final String NO_ENCONTRADO = "Proyecto no encontrado";
    static final String PASO_NO_ENCONTRADO = "Paso no encontrado";
    static final String SOLO_EL_PRODUCT_OWNER = "Solo el desarrollador (Product Owner) del equipo puede registrar trabajo en este proyecto";
    static final String NO_ES_DE_TU_EQUIPO = "Este proyecto no es de tu equipo";
    static final String PASO_YA_REGISTRADO = "Este paso ya está registrado o no es de la fase actual";
    static final String ESTADO_NO_ADMITE = "El proyecto no admite registros en su estado actual";
    static final String CARPETA = "adjuntos";
    static final int MAX_ADJUNTOS = 5;
    static final int MAX_BYTES = 5 * 1024 * 1024;

    /** Un archivo que llegó con la petición: su nombre original (solo para el título) y su contenido. */
    public record ArchivoSubido(String nombre, byte[] contenido) {
    }

    /** Resultado de marcar un paso como hecho (HU-03). {@code pasosHechos} es "4 de 5" en la fase del paso. */
    public record PasoRegistrado(UUID pasoId, Instant hechoAt, String pasosHechos) {
    }

    public record AdjuntoSubido(UUID id, String titulo, String tipoMime, int tamanoBytes, String url) {
    }

    public record AdjuntoVista(UUID id, String titulo, String tipoMime, int tamanoBytes, String url) {
    }

    public record PasoVista(UUID id, int orden, String tipo, String titulo, String explicacion, String origen, String estado,
            String nota, Instant hechoAt, List<AdjuntoVista> adjuntos) {
    }

    /** {@code porcentaje} es lo que vale la fase en el proyecto (RN-08: 100 dividido entre el número de fases). */
    public record FaseVista(UUID id, int orden, String nombre, String queEs, boolean apruebaCliente, String estado,
            BigDecimal porcentaje, Instant iniciadaAt, Instant entregadaAt, Instant aprobadaAt, int rondasAjusteUsadas,
            boolean aprobacionAutomatica, int pasosHechos, int pasosTotal, List<PasoVista> pasos) {
    }

    public record Persona(String nombre, String rol) {
    }

    public record UltimoPaso(Instant fecha, String titulo) {
    }

    public record FaseActual(int orden, String nombre) {
    }

    /**
     * El proyecto como lo ven el cliente, el equipo y el administrador. {@code puedeRegistrar} es verdadero solo para el
     * Product Owner del equipo con el proyecto activo.
     */
    public record DetalleProyecto(String codigo, String nombre, String servicio, String citaCodigo, String estado,
            String estadoPlan, BigDecimal avancePorcentaje, LocalDate fechaInicio, LocalDate fechaEstimadaEntrega,
            FaseActual faseActual, int pasosHechos, UltimoPaso ultimoPaso, String cliente, String clienteCorreo,
            String productOwner, String productOwnerCorreo, List<Persona> equipo, boolean puedeRegistrar, List<FaseVista> fases) {
    }

    /** Un adjunto listo para descargar. */
    public record Descarga(AlmacenArchivos.Archivo archivo, String titulo, String tipoMime) {
    }

    private final SeguimientoJdbc datos;
    private final ProyectosParaSeguimiento proyectos;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final AlmacenArchivos almacen;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioSeguimiento(SeguimientoJdbc datos, ProyectosParaSeguimiento proyectos, EquipoConsultas equipos,
            DirectorioUsuarios usuarios, AlmacenArchivos almacen, RegistroAuditoria auditoria, SesionActual sesion,
            ApplicationEventPublisher eventos, Clock reloj) {
        this.datos = datos;
        this.proyectos = proyectos;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.almacen = almacen;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------------------------------- Consultar (HU-04)

    /**
     * El detalle de un proyecto: el cliente dueño, el Product Owner del equipo o el administrador. Para el cliente, un
     * proyecto ajeno no existe (404, RN-10) y el intento queda en la auditoría. Mientras el plan está en borrador el cliente
     * todavía no ve fases: el equipo lo está preparando.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public DetalleProyecto detalle(String codigo) {
        UsuarioSesion usuario = sesion.usuario();
        Proyecto p = buscar(codigo);
        boolean equipo = usuario.tienePermiso("proyectos.registrar_avance");
        if (equipo) {
            exigirEquipo(p, usuario, "consultar");
        } else if (!usuario.tienePermiso("proyectos.ver_todos")) {
            exigirCliente(p, usuario, "consultar");
        }
        boolean sinPlanVisible = !equipo && !usuario.tienePermiso("proyectos.ver_todos") && p.estadoPlan().equals("BORRADOR");
        return vista(p, usuario, equipo && p.estado().equals("ACTIVO"), sinPlanVisible);
    }

    // ------------------------------------------------------------------------------------------- Registrar (HU-03)

    /**
     * Marca un paso como hecho con una nota para el cliente: de 20 a 400 caracteres en un paso del plan y de 20 a 500 en una
     * corrección o un ajuste (RN-09). El paso debe ser de la fase actual y no estar hecho; si no, 409.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public PasoRegistrado marcarHecho(UUID pasoId, String estado, String nota) {
        UsuarioSesion usuario = sesion.usuario();
        PasoContexto paso = datos.paso(pasoId).orElseThrow(() -> new NoEncontradoException(PASO_NO_ENCONTRADO));
        proyectos.bloquear(paso.proyectoId());
        Proyecto p = proyectos.porId(paso.proyectoId()).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
        exigirProductOwner(p, usuario, "registrar trabajo en");

        Acumulador errores = new Acumulador();
        if (!"HECHO".equals(estado)) {
            errores.agregar("estado", "Solo se puede marcar un paso como hecho. Para quitarlo, propón un cambio de plan.");
        }
        String texto = nota == null ? "" : nota.trim();
        validarNota(texto, paso.tipo(), nombreCliente(p), errores);
        errores.lanzarSiHay();

        exigirRegistrable(p, paso);
        Instant ahora = Instant.now(reloj);
        if (!datos.marcarHecho(paso.pasoId(), texto, usuario.id(), ahora)) {
            throw new ConflictoException(PASO_YA_REGISTRADO);
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("paso", CambioDeValor.nuevo(paso.titulo()));
        cambios.put("estado", CambioDeValor.de(paso.estado(), "HECHO"));
        cambios.put("nota", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.AVANCES, "Marcó un paso como hecho", "proyecto", p.codigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(p.clienteId(), "PASO_HECHO", recortar("Hay un avance en " + p.nombre(), 80),
                recortar(quienPublica(p) + " terminó «" + paso.titulo() + "». Entra a ver su nota.", 255),
                "/cliente/proyectos/" + p.codigo()));
        int[] conteo = datos.conteo(paso.faseId());
        return new PasoRegistrado(paso.pasoId(), ahora, conteo[0] + " de " + conteo[1]);
    }

    /**
     * Adjunta hasta 5 archivos en total (PNG, JPG, SVG o PDF de máximo 5 MB cada uno) a un paso que ya está hecho. El tipo se
     * reconoce por el contenido. Se valida todo antes de guardar nada: si un archivo falla, ninguno se guarda.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public List<AdjuntoSubido> adjuntar(UUID pasoId, List<ArchivoSubido> archivos) {
        UsuarioSesion usuario = sesion.usuario();
        PasoContexto paso = datos.paso(pasoId).orElseThrow(() -> new NoEncontradoException(PASO_NO_ENCONTRADO));
        proyectos.bloquear(paso.proyectoId());
        Proyecto p = proyectos.porId(paso.proyectoId()).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
        exigirProductOwner(p, usuario, "adjuntar archivos en");
        if (!List.of("ACTIVO", "EN_GARANTIA").contains(p.estado())) {
            throw new ConflictoException(ESTADO_NO_ADMITE);
        }
        if (!paso.estado().equals("HECHO")) {
            throw new ConflictoException("Primero marca el paso como hecho y luego adjunta sus archivos");
        }

        Acumulador errores = new Acumulador();
        if (archivos == null || archivos.isEmpty()) {
            errores.agregar("adjuntos", "Elige al menos un archivo.");
        } else if (datos.contarAdjuntos(paso.pasoId()) + archivos.size() > MAX_ADJUNTOS) {
            errores.agregar("adjuntos", "Un paso admite máximo " + MAX_ADJUNTOS + " archivos.");
        }
        List<TipoAdjunto> tipos = new ArrayList<>();
        if (!errores.tiene("adjuntos")) {
            for (ArchivoSubido a : archivos) {
                if (a.contenido().length == 0) {
                    errores.agregar("adjuntos", "«" + nombreVisible(a.nombre()) + "» está vacío.");
                } else if (a.contenido().length > MAX_BYTES) {
                    errores.agregar("adjuntos", "«" + nombreVisible(a.nombre()) + "» pesa más de 5 MB.");
                } else {
                    Optional<TipoAdjunto> tipo = TipoAdjunto.reconocer(a.contenido());
                    if (tipo.isEmpty()) {
                        errores.agregar("adjuntos", "«" + nombreVisible(a.nombre()) + "» no es un PNG, JPG, SVG o PDF válido.");
                    }
                    tipo.ifPresent(tipos::add);
                }
            }
        }
        errores.lanzarSiHay();

        List<AdjuntoSubido> subidos = new ArrayList<>();
        List<String> guardados = new ArrayList<>();
        try {
            for (int i = 0; i < archivos.size(); i++) {
                ArchivoSubido a = archivos.get(i);
                TipoAdjunto tipo = tipos.get(i);
                String guardado = almacen.guardar(CARPETA, a.contenido(), tipo.extension());
                guardados.add(guardado);
                String titulo = titulo(a.nombre());
                UUID id = datos.insertarAdjunto(paso.pasoId(), titulo, guardado, tipo.tipoMime(), a.contenido().length);
                subidos.add(new AdjuntoSubido(id, titulo, tipo.tipoMime(), a.contenido().length, urlDe(id)));
            }
        } catch (RuntimeException e) {
            guardados.forEach(g -> almacen.borrar(CARPETA, g));
            throw e;
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("paso", CambioDeValor.nuevo(paso.titulo()));
        cambios.put("adjuntos", CambioDeValor.nuevo(subidos.stream().map(AdjuntoSubido::titulo).collect(Collectors.joining(", "))));
        auditoria.registrar(CategoriaAuditoria.AVANCES, "Adjuntó archivos a un paso", "proyecto", p.codigo(), cambios);
        return subidos;
    }

    /**
     * Descarga un adjunto con el mismo control de acceso que el detalle: el cliente dueño, el Product Owner del equipo o el
     * administrador. Uno ajeno responde 404 (no se revela que existe) y queda en la auditoría.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Descarga descargar(UUID adjuntoId) {
        UsuarioSesion usuario = sesion.usuario();
        var contexto = datos.adjunto(adjuntoId).orElseThrow(() -> new NoEncontradoException("Archivo no encontrado"));
        Proyecto p = proyectos.porId(contexto.proyectoId()).orElseThrow(() -> new NoEncontradoException("Archivo no encontrado"));
        if (usuario.tienePermiso("proyectos.registrar_avance")) {
            if (!esDelEquipo(p, usuario)) {
                denegar("Intentó descargar un archivo de otro equipo", p);
                throw new NoEncontradoException("Archivo no encontrado");
            }
        } else if (!usuario.tienePermiso("proyectos.ver_todos") && !p.clienteId().equals(usuario.id())) {
            denegar("Intentó descargar un archivo de un proyecto ajeno", p);
            throw new NoEncontradoException("Archivo no encontrado");
        }
        AdjuntoFila a = contexto.adjunto();
        if (a.eliminado()) {
            throw new NoEncontradoException("Este archivo ya no está disponible");
        }
        AlmacenArchivos.Archivo archivo = almacen.leer(CARPETA, a.archivo(), a.tipoMime())
                .orElseThrow(() -> new NoEncontradoException("Este archivo ya no está disponible"));
        return new Descarga(archivo, a.titulo(), a.tipoMime());
    }

    // ------------------------------------------------------------------------------------------- Validaciones

    /** RN-09: 20 a 400 caracteres en un paso del plan; 20 a 500 en una corrección, un ajuste o un cambio de alcance. */
    private static void validarNota(String nota, String tipoPaso, String cliente, Acumulador errores) {
        int maximo = tipoPaso.equals("PASO") ? 400 : 500;
        if (nota.isEmpty()) {
            errores.agregar("nota", "Escribe la nota para que " + cliente + " sepa qué cambió.");
        } else if (nota.length() < 20) {
            errores.agregar("nota", "Escribe al menos 20 caracteres.");
        } else if (nota.length() > maximo) {
            errores.agregar("nota", "Escribe máximo " + maximo + " caracteres.");
        }
    }

    /**
     * El paso debe estar pendiente y ser de la fase actual. Una corrección o un ajuste también se cierra en una fase ya
     * aprobada o en corrección (RN-16). Un proyecto pausado, finalizado o cancelado no admite registros; en garantía solo
     * las correcciones.
     */
    private static void exigirRegistrable(Proyecto p, PasoContexto paso) {
        boolean esPaso = paso.tipo().equals("PASO");
        boolean estadoAdmite = p.estado().equals("ACTIVO") || (p.estado().equals("EN_GARANTIA") && !esPaso);
        if (!estadoAdmite || !p.estadoPlan().equals("APROBADO")) {
            throw new ConflictoException(ESTADO_NO_ADMITE);
        }
        boolean pendiente = paso.estado().equals("PENDIENTE") || paso.estado().equals("EN_CURSO");
        boolean faseValida = paso.faseEstado().equals("EN_CURSO")
                || (!esPaso && List.of("APROBADA", "EN_CORRECCION").contains(paso.faseEstado()));
        if (!pendiente || !faseValida) {
            throw new ConflictoException(PASO_YA_REGISTRADO);
        }
    }

    // ------------------------------------------------------------------------------------------- Permisos

    /** Solo el Product Owner del equipo asignado (RN-07); otro Product Owner recibe 403 y queda en la auditoría. */
    private void exigirProductOwner(Proyecto p, UsuarioSesion usuario, String accion) {
        if (!esDelEquipo(p, usuario)) {
            denegar("Intentó " + accion + " un proyecto de otro equipo", p);
            throw new ProhibidoException(SOLO_EL_PRODUCT_OWNER);
        }
    }

    /** Para consultar: el administrador pasa por {@code ver_todos}; el Product Owner debe ser del equipo (403). */
    private void exigirEquipo(Proyecto p, UsuarioSesion usuario, String accion) {
        if (!esDelEquipo(p, usuario)) {
            denegar("Intentó " + accion + " un proyecto de otro equipo", p);
            throw new ProhibidoException(NO_ES_DE_TU_EQUIPO);
        }
    }

    private boolean esDelEquipo(Proyecto p, UsuarioSesion usuario) {
        Optional<UUID> equipo = equipos.equipoDelProductOwner(usuario.id());
        return equipo.isPresent() && equipo.get().equals(p.equipoId());
    }

    /** Un proyecto de otro cliente no se revela: 404 y queda en la auditoría (RN-10). */
    private void exigirCliente(Proyecto p, UsuarioSesion usuario, String accion) {
        if (!p.clienteId().equals(usuario.id())) {
            denegar("Intentó " + accion + " un proyecto ajeno", p);
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
    }

    private void denegar(String accion, Proyecto p) {
        auditoria.registrar(CategoriaAuditoria.AVANCES, accion, "proyecto", p.codigo(), new LinkedHashMap<>());
    }

    // ------------------------------------------------------------------------------------------- Armado de la vista

    private Proyecto buscar(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        return proyectos.porCodigo(codigo).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private DetalleProyecto vista(Proyecto p, UsuarioSesion usuario, boolean puedeRegistrar, boolean ocultarPlan) {
        List<FaseFila> fases = ocultarPlan ? List.of() : datos.fases(p.id());
        List<PasoFila> pasos = ocultarPlan ? List.of() : datos.pasos(p.id());
        Map<UUID, List<AdjuntoVista>> adjuntos = ocultarPlan ? Map.of() : datos.adjuntos(p.id()).stream()
                .filter(a -> !a.eliminado())
                .collect(Collectors.groupingBy(AdjuntoFila::pasoId, LinkedHashMap::new,
                        Collectors.mapping(a -> new AdjuntoVista(a.id(), a.titulo(), a.tipoMime(), a.tamanoBytes(), urlDe(a.id())),
                                Collectors.toList())));
        Map<UUID, List<PasoFila>> pasosPorFase = pasos.stream().collect(Collectors.groupingBy(PasoFila::faseId));
        BigDecimal porFase = fases.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(fases.size()), 1, RoundingMode.HALF_UP);

        List<FaseVista> vistaFases = new ArrayList<>();
        FaseActual actual = null;
        for (FaseFila f : fases) {
            List<PasoFila> deLaFase = pasosPorFase.getOrDefault(f.id(), List.of());
            List<PasoVista> vistaPasos = deLaFase.stream().map(s -> new PasoVista(s.id(), s.orden(), s.tipo(), s.titulo(),
                    s.explicacion(), s.origen(), s.estado(), s.nota(), s.hechoAt(), adjuntos.getOrDefault(s.id(), List.of()))).toList();
            int hechos = (int) deLaFase.stream().filter(s -> s.estado().equals("HECHO")).count();
            vistaFases.add(new FaseVista(f.id(), f.orden(), f.nombre(), f.queEs(), f.apruebaCliente(), f.estado(), porFase,
                    f.iniciadaAt(), f.entregadaAt(), f.aprobadaAt(), f.rondasAjusteUsadas(), f.aprobacionAutomatica(), hechos,
                    deLaFase.size(), vistaPasos));
            if (actual == null && !f.estado().equals("APROBADA")) {
                actual = new FaseActual(f.orden(), f.nombre());
            }
        }
        PasoFila ultimo = pasos.stream().filter(s -> s.hechoAt() != null).max(java.util.Comparator.comparing(PasoFila::hechoAt))
                .orElse(null);
        int totalHechos = (int) pasos.stream().filter(s -> s.estado().equals("HECHO")).count();

        ContactoCorreo cliente = usuarios.perfiles(List.of(p.clienteId())).get(p.clienteId());
        Optional<ProductOwner> po = p.equipoId() == null ? Optional.empty() : equipos.productOwner(p.equipoId());
        List<Persona> equipo = p.equipoId() == null ? List.of() : equipos.integrantes(p.equipoId()).stream()
                .map(i -> new Persona(i.nombre(), rolVisible(i))).toList();
        return new DetalleProyecto(p.codigo(), p.nombre(), p.servicio(), p.citaCodigo(), p.estado(), p.estadoPlan(), p.avance(),
                p.fechaInicio(), p.fechaEntrega(), actual, totalHechos,
                ultimo == null ? null : new UltimoPaso(ultimo.hechoAt(), ultimo.titulo()),
                cliente == null ? "" : cliente.nombre(), cliente == null ? "" : cliente.email(),
                po.map(ProductOwner::nombre).orElse(""), po.map(ProductOwner::email).orElse(""), equipo, puedeRegistrar,
                vistaFases);
    }

    /** Lo que el cliente entiende: el enum de la base dicho con palabras sencillas. */
    static String rolVisible(Integrante i) {
        return switch (i.rol()) {
            case "UX_UI" -> "Diseño de pantallas";
            case "FRONTEND" -> "Desarrollo · lo que ves";
            case "BACKEND" -> "Desarrollo · datos y reglas";
            case "FULL_STACK" -> "Desarrollo completo";
            case "MOVIL" -> "Desarrollo móvil";
            case "QA" -> "Pruebas";
            default -> "Equipo";
        };
    }

    private String nombreCliente(Proyecto p) {
        ContactoCorreo c = usuarios.perfiles(List.of(p.clienteId())).get(p.clienteId());
        if (c == null || c.nombre().isBlank()) {
            return "el cliente";
        }
        return c.nombre().trim().split("\\s+")[0];
    }

    private String quienPublica(Proyecto p) {
        return p.equipoId() == null ? "El equipo" : equipos.productOwner(p.equipoId()).map(ProductOwner::nombre).orElse("El equipo");
    }

    static String urlDe(UUID adjuntoId) {
        return "/api/v1/adjuntos/" + adjuntoId;
    }

    /** El nombre sin ruta ni extensión, de 1 a 80 caracteres: es lo que ve el cliente bajo el boceto. */
    static String titulo(String nombreOriginal) {
        String nombre = nombreVisible(nombreOriginal);
        int punto = nombre.lastIndexOf('.');
        String sinExtension = punto > 0 ? nombre.substring(0, punto) : nombre;
        String limpio = sinExtension.replaceAll("[\\p{Cntrl}]", "").replaceAll("[_]+", " ").trim();
        if (limpio.isEmpty()) {
            limpio = "Archivo adjunto";
        }
        return limpio.length() <= 80 ? limpio : limpio.substring(0, 80).trim();
    }

    private static String nombreVisible(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "archivo";
        }
        String soloNombre = nombre.replace('\\', '/');
        soloNombre = soloNombre.substring(soloNombre.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return soloNombre.isEmpty() ? "archivo" : soloNombre;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
