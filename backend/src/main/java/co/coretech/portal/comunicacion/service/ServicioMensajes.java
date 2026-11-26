package co.coretech.portal.comunicacion.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Persona;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Proyecto;
import co.coretech.portal.comunicacion.repository.MensajesJdbc;
import co.coretech.portal.comunicacion.repository.MensajesJdbc.MensajeContexto;
import co.coretech.portal.comunicacion.repository.MensajesJdbc.MensajeFila;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import static co.coretech.portal.compartido.service.Textos.recortar;

/**
 * Mensajes entre el cliente dueño de un proyecto y el desarrollador (Product Owner) de su equipo (HU-14, RN-14).
 * <p>
 * Solo ellos dos participan: un cliente ajeno recibe 404 (como si el proyecto no existiera, RN-10) y quien no es de este
 * proyecto, 403; ambos intentos quedan en la auditoría aunque la petición falle, por eso los métodos no revierten en esos
 * casos. Solo se escribe con el proyecto activo o en garantía (pausado, finalizado o cancelado: solo lectura). El cliente
 * elige un tema de la lista; el desarrollador responde en el mismo hilo, sin tema (D-41). El autor edita su mensaje durante
 * 5 minutos y el texto anterior queda en la auditoría; los mensajes no se borran (RNF-02, {@code tg_proteger_mensajes}).
 */
@Service
public class ServicioMensajes {

    static final String NO_ENCONTRADO = "Proyecto no encontrado";
    static final String MENSAJE_NO_ENCONTRADO = "Mensaje no encontrado";
    static final String SOLO_PARTICIPANTES = "Solo el cliente y el desarrollador (Product Owner) del proyecto se escriben aquí";
    static final String ESTADO_NO_RECIBE = "Este proyecto no recibe mensajes en su estado actual";
    static final String YA_NO_EDITAS = "Ya no puedes editar este mensaje";
    static final String SOLO_EL_AUTOR = "Solo quien escribió el mensaje puede editarlo";
    static final Duration PLAZO_EDICION = Duration.ofMinutes(5);

    /** Los temas que el cliente elige (HU-14). */
    public static final List<String> TEMAS = List.of("Pregunta sobre el avance", "Idea o cambio para el proyecto",
            "Envío de información", "Reportar un problema", "Otro");

    /** Quién escribe en el hilo: {@code CLIENTE} o {@code PRODUCT_OWNER}. */
    public record MensajeVista(UUID id, String autor, String rol, boolean esMio, String tema, String contenido, Instant creadoAt,
            Instant editadoAt, boolean puedeEditar) {
    }

    /**
     * El hilo de un proyecto. {@code puedeEscribir} es falso con el proyecto pausado, finalizado o cancelado (el hilo queda en
     * solo lectura); {@code temas} es la lista que elige el cliente.
     */
    public record Hilo(String proyecto, boolean puedeEscribir, boolean escribeConTema, List<String> temas, List<MensajeVista> mensajes) {
    }

    private final MensajesJdbc datos;
    private final ProyectosParaComunicacion proyectos;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioMensajes(MensajesJdbc datos, ProyectosParaComunicacion proyectos, RegistroAuditoria auditoria, SesionActual sesion,
            ApplicationEventPublisher eventos, Clock reloj) {
        this.datos = datos;
        this.proyectos = proyectos;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Hilo hilo(String codigo) {
        UsuarioSesion usuario = sesion.usuario();
        Proyecto p = buscar(codigo);
        exigirParticipante(p, usuario, "leer los mensajes de");
        Instant ahora = Instant.now(reloj);
        List<MensajeVista> mensajes = datos.listar(p.id()).stream().map(m -> vista(m, usuario, p, ahora)).toList();
        return new Hilo(p.nombre(), recibe(p), esCliente(p, usuario), TEMAS, mensajes);
    }

    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public MensajeVista enviar(String codigo, String tema, String contenido) {
        UsuarioSesion usuario = sesion.usuario();
        Proyecto p = buscar(codigo);
        boolean cliente = exigirParticipante(p, usuario, "escribir en");
        if (!recibe(p)) {
            throw new ConflictoException(ESTADO_NO_RECIBE);
        }
        String temaFinal = cliente ? tema == null ? "" : tema.trim() : null;
        String texto = contenido == null ? "" : contenido.trim();
        Acumulador errores = new Acumulador();
        validarTema(cliente, temaFinal, errores);
        validarContenido(texto, errores);
        errores.lanzarSiHay();

        Instant ahora = Instant.now(reloj);
        UUID id = datos.insertar(p.id(), usuario.id(), temaFinal, texto, ahora);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        if (temaFinal != null) {
            cambios.put("tema", CambioDeValor.nuevo(temaFinal));
        }
        cambios.put("mensaje", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Envió un mensaje", "proyecto", p.codigo(), cambios);
        avisar(p, usuario, cliente, texto);

        return new MensajeVista(id, usuario.nombre(), cliente ? "CLIENTE" : "PRODUCT_OWNER", true, temaFinal, texto, ahora, null, true);
    }

    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public MensajeVista editar(UUID mensajeId, String tema, String contenido) {
        UsuarioSesion usuario = sesion.usuario();
        MensajeContexto m = datos.mensaje(mensajeId).orElseThrow(() -> new NoEncontradoException(MENSAJE_NO_ENCONTRADO));
        Proyecto p = proyectos.porId(m.proyectoId()).orElseThrow(() -> new NoEncontradoException(MENSAJE_NO_ENCONTRADO));
        boolean cliente = exigirParticipante(p, usuario, "editar un mensaje de");
        if (!m.autorId().equals(usuario.id())) {
            denegar("Intentó editar un mensaje de otra persona", p);
            throw new ProhibidoException(SOLO_EL_AUTOR);
        }
        Instant ahora = Instant.now(reloj);
        if (!recibe(p) || ahora.isAfter(m.creadoAt().plus(PLAZO_EDICION))) {
            throw new ConflictoException(YA_NO_EDITAS);
        }
        // Un mensaje del desarrollador no lleva tema: se conserva como estaba
        String temaFinal = m.tema() == null ? null : tema == null ? "" : tema.trim();
        String texto = contenido == null ? "" : contenido.trim();
        Acumulador errores = new Acumulador();
        validarTema(m.tema() != null, temaFinal, errores);
        validarContenido(texto, errores);
        errores.lanzarSiHay();

        datos.editar(m.id(), temaFinal, texto);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        if (temaFinal != null && !temaFinal.equals(m.tema())) {
            cambios.put("tema", CambioDeValor.de(m.tema(), temaFinal));
        }
        cambios.put("mensaje", CambioDeValor.de(m.contenido(), texto));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Editó un mensaje", "proyecto", p.codigo(), cambios);

        return datos.listar(p.id()).stream().filter(f -> f.id().equals(m.id())).findFirst()
                .map(f -> vista(f, usuario, p, ahora)).orElseThrow(() -> new NoEncontradoException(MENSAJE_NO_ENCONTRADO));
    }

    // ------------------------------------------------------------------------------------------- Reglas

    private static void validarTema(boolean cliente, String tema, Acumulador errores) {
        if (cliente && !TEMAS.contains(tema)) {
            errores.agregar("tema", "Elige de qué trata tu mensaje.");
        }
    }

    private static void validarContenido(String texto, Acumulador errores) {
        if (texto.isEmpty()) {
            errores.agregar("contenido", "Escribe tu mensaje.");
        } else if (texto.length() < 10) {
            errores.agregar("contenido", "Escribe al menos 10 caracteres.");
        } else if (texto.length() > 1000) {
            errores.agregar("contenido", "Escribe máximo 1000 caracteres.");
        }
    }

    /** Solo se escribe con el proyecto activo o en garantía (RN-14). */
    private static boolean recibe(Proyecto p) {
        return p.estado().equals("ACTIVO") || p.estado().equals("EN_GARANTIA");
    }

    private static boolean esCliente(Proyecto p, UsuarioSesion usuario) {
        return p.clienteId().equals(usuario.id());
    }

    private MensajeVista vista(MensajeFila m, UsuarioSesion usuario, Proyecto p, Instant ahora) {
        boolean mio = m.autorId().equals(usuario.id());
        boolean editable = mio && recibe(p) && !ahora.isAfter(m.creadoAt().plus(PLAZO_EDICION));
        return new MensajeVista(m.id(), m.autor(), m.rol().equals("CLIENTE") ? "CLIENTE" : "PRODUCT_OWNER", mio, m.tema(), m.contenido(),
                m.creadoAt(), m.editadoAt(), editable);
    }

    // ------------------------------------------------------------------------------------------- Permisos

    private Proyecto buscar(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        return proyectos.porCodigo(codigo).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    /**
     * Solo el cliente dueño o el Product Owner del equipo. Un cliente ajeno recibe 404 (RN-10); un Product Owner de otro
     * equipo o el administrador, 403. Devuelve verdadero si quien actúa es el cliente.
     */
    private boolean exigirParticipante(Proyecto p, UsuarioSesion usuario, String accion) {
        if (esCliente(p, usuario)) {
            return true;
        }
        Optional<UUID> equipo = proyectos.equipoDelProductOwner(usuario.id());
        if (equipo.isPresent() && equipo.get().equals(p.equipoId())) {
            return false;
        }
        denegar("Intentó " + accion + " un proyecto ajeno", p);
        if (usuario.tienePermiso("proyectos.registrar_avance") || usuario.tienePermiso("proyectos.ver_todos")) {
            throw new ProhibidoException(SOLO_PARTICIPANTES);
        }
        throw new NoEncontradoException(NO_ENCONTRADO);
    }

    private void denegar(String accion, Proyecto p) {
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, accion, "proyecto", p.codigo(), new LinkedHashMap<>());
    }

    // ------------------------------------------------------------------------------------------- Avisos

    /** Avisa a la otra persona del hilo: el cliente al Product Owner y el Product Owner al cliente (RF-39). */
    private void avisar(Proyecto p, UsuarioSesion autor, boolean escribeElCliente, String contenido) {
        String texto = recortar(autor.nombre() + ": «" + contenido + "»", 255);
        String titulo = recortar("Mensaje nuevo en " + p.nombre(), 80);
        if (escribeElCliente) {
            if (p.equipoId() != null) {
                proyectos.productOwnerDe(p.equipoId()).map(Persona::usuarioId).ifPresent(destino -> eventos
                        .publishEvent(AvisoSolicitado.para(destino, "NUEVO_MENSAJE", titulo, texto, "/equipo/proyectos/" + p.codigo() + "/registrar")));
            }
        } else {
            eventos.publishEvent(AvisoSolicitado.para(p.clienteId(), "NUEVO_MENSAJE", titulo, texto, "/cliente/proyectos/" + p.codigo()));
        }
    }
}
