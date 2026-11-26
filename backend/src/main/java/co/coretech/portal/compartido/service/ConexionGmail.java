package co.coretech.portal.compartido.service;

import co.coretech.portal.compartido.exception.ConflictoException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.service.ClienteGoogle.ErrorGoogleException;
import co.coretech.portal.compartido.service.ClienteGoogle.PermisoRetiradoException;
import co.coretech.portal.compartido.service.ClienteGoogle.TokenAcceso;
import co.coretech.portal.compartido.service.ClienteGoogle.Tokens;
import co.coretech.portal.compartido.service.ServidorCorreo.CorreoNoEnviadoException;
import co.coretech.portal.compartido.service.ServidorCorreo.CorreoSinConexionException;
import co.coretech.portal.compartido.repository.CorreoSistemaJdbc;
import co.coretech.portal.compartido.repository.CorreoSistemaJdbc.Conexion;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Correo del sistema enlazado con Gmail (D-51). El administrador pulsa "Conectar con Gmail", inicia sesión en Google
 * y acepta el permiso "Enviar correo en tu nombre"; el portal guarda ese permiso (refresh token) cifrado y con él
 * envía por la API de Gmail. Nunca ve ni guarda la contraseña de la cuenta. Quitar el permiso en la cuenta de Google
 * (Seguridad → Apps con acceso) o pulsar "Desconectar" lo corta de inmediato.
 * <p>
 * El regreso de Google llega sin la cookie de sesión (SameSite=Strict no viaja desde otro sitio): por eso el
 * "estado" de cada conexión, de un solo uso y 10 minutos, recuerda qué administrador la pidió (en memoria: un solo
 * servidor, como el límite de intentos). Con PKCE, un código robado no sirve sin el verificador.
 */
@Service
public class ConexionGmail {

    private static final Logger LOG = LoggerFactory.getLogger(ConexionGmail.class);

    static final Duration VIGENCIA_ESTADO = Duration.ofMinutes(10);
    static final String RUTA_REGRESO = "/api/v1/correo-sistema/gmail/callback";
    static final String NOMBRE_REMITENTE = "CoreTech Solutions";
    static final String SIN_CONFIGURAR = "Falta configurar el cliente de Google (CORETECH_GOOGLE_ID y CORETECH_GOOGLE_SECRETO)";

    public enum Resultado { CONECTADO, CANCELADO, SIN_PERMISO_ENVIO, VENCIDO, ERROR }

    public record Estado(boolean configurado, boolean conectado, String cuenta, String estado, Instant conectadoEn,
            String ultimoError, Instant ultimoErrorEn) {
    }

    private record Pendiente(UUID usuarioId, String verificador, Instant expira) {
    }

    private final ClienteGoogle google;
    private final CorreoSistemaJdbc conexiones;
    private final CifradoSecretos cifrado;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;
    private final String redirect;
    private final boolean configurado;
    private final SecureRandom azar = new SecureRandom();
    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();

    /** Token de acceso vigente (dura ~1 hora); se renueva con el permiso guardado. */
    private TokenAcceso tokenVigente;
    private Instant tokenExpira = Instant.EPOCH;
    private String tokenDeCuenta;

    ConexionGmail(ClienteGoogle google, CorreoSistemaJdbc conexiones, CifradoSecretos cifrado, RegistroAuditoria auditoria,
            SesionActual sesion, Clock reloj, PropiedadesCoretech propiedades,
            @Value("${coretech.google.cliente-id:}") String clienteId,
            @Value("${coretech.google.cliente-secreto:}") String clienteSecreto, ApplicationEventPublisher eventos) {
        this.eventos = eventos;
        this.google = google;
        this.conexiones = conexiones;
        this.cifrado = cifrado;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.reloj = reloj;
        this.redirect = propiedades.urlPortal().replaceAll("/+$", "") + RUTA_REGRESO;
        this.configurado = !clienteId.isBlank() && !clienteSecreto.isBlank();
    }

    public boolean configurado() {
        return configurado;
    }

    public Estado estado() {
        return conexiones.obtener()
                .map(c -> new Estado(configurado, c.activa(), c.cuenta(), c.estado(), c.conectadoEn(), c.ultimoError(), c.ultimoErrorEn()))
                .orElse(new Estado(configurado, false, null, null, null, null, null));
    }

    /** Hay una cuenta conectada y su permiso sigue valiendo. */
    public boolean conectada() {
        return conexiones.obtener().map(Conexion::activa).orElse(false);
    }

    /** Paso 1: la URL de Google a la que el navegador del administrador debe ir. */
    public String iniciarConexion() {
        if (!configurado) {
            throw new ConflictoException(SIN_CONFIGURAR);
        }
        Instant ahora = Instant.now(reloj);
        pendientes.values().removeIf(p -> p.expira().isBefore(ahora));
        String estado = aleatorio(32);
        String verificador = aleatorio(48);
        pendientes.put(estado, new Pendiente(sesion.usuario().id(), verificador, ahora.plus(VIGENCIA_ESTADO)));
        return google.urlAutorizacion(redirect, estado, reto(verificador));
    }

    /** Paso 2: Google devuelve al navegador con el código (o con un error si la persona canceló). */
    @Transactional
    public Resultado completarConexion(String estado, String codigo, String error) {
        Pendiente pendiente = estado == null ? null : pendientes.remove(estado);
        if (pendiente == null || pendiente.expira().isBefore(Instant.now(reloj))) {
            return Resultado.VENCIDO;
        }
        if (error != null || codigo == null) {
            return Resultado.CANCELADO;
        }
        try {
            Tokens tokens = google.intercambiar(codigo, pendiente.verificador(), redirect);
            if (tokens.alcances() == null || !tokens.alcances().contains(ClienteGoogle.ALCANCE_ENVIAR)) {
                // En la pantalla de Google se puede desmarcar el permiso de enviar: sin él no sirve.
                if (tokens.tokenRefresco() != null) {
                    google.revocar(tokens.tokenRefresco());
                }
                return Resultado.SIN_PERMISO_ENVIO;
            }
            if (tokens.tokenRefresco() == null) {
                return Resultado.ERROR;
            }
            String cuenta = google.correoDe(tokens.tokenAcceso());
            String anterior = conexiones.obtener().map(Conexion::cuenta).orElse(null);
            Instant ahora = Instant.now(reloj);
            conexiones.guardar(cuenta, cifrado.cifrar(tokens.tokenRefresco()), pendiente.usuarioId(), ahora);
            recordarToken(new TokenAcceso(tokens.tokenAcceso(), tokens.expiraEnSegundos()), cuenta, ahora);
            Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
            cambios.put("cuentaCorreo", new CambioDeValor(anterior, cuenta));
            auditoria.registrarComo(pendiente.usuarioId(), CategoriaAuditoria.SEGURIDAD, "Conectó el correo del sistema con Gmail",
                    "correo_sistema", "1", cambios);
            return Resultado.CONECTADO;
        } catch (ErrorGoogleException e) {
            LOG.warn("No se pudo conectar Gmail: {}", e.getMessage());
            return Resultado.ERROR;
        }
    }

    @Transactional
    public void desconectar() {
        Conexion conexion = conexiones.obtener().orElseThrow(() -> new ConflictoException("El correo del sistema no está conectado"));
        try {
            google.revocar(cifrado.descifrar(conexion.tokenCifrado()));
        } catch (IllegalStateException e) {
            // No se pudo descifrar (cambió la clave): igual se borra.
        }
        conexiones.borrar();
        olvidarToken();
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("cuentaCorreo", new CambioDeValor(conexion.cuenta(), null));
        auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Desconectó el correo del sistema", "correo_sistema", "1", cambios);
    }

    /** Envía por la API de Gmail con la cuenta conectada; el remitente es esa misma cuenta (Gmail lo exige). */
    public void enviar(Correo correo) {
        Conexion conexion = conexiones.obtener().filter(Conexion::activa)
                .orElseThrow(() -> new CorreoSinConexionException("El correo del sistema no está conectado con Gmail"));
        String token;
        try {
            token = tokenDeAcceso(conexion);
        } catch (PermisoRetiradoException e) {
            conexiones.registrarError(e.getMessage(), true, Instant.now(reloj));
            olvidarToken();
            LOG.warn("Gmail retiró el permiso del correo del sistema: hay que volver a conectarlo");
            // Una sola vez: desde ahora conectada() es falsa y esta rama no se vuelve a ejecutar hasta reconectar
            eventos.publishEvent(new CorreoSistemaRevocado(conexion.cuenta()));
            throw new CorreoSinConexionException(e.getMessage());
        } catch (ErrorGoogleException e) {
            conexiones.registrarError(e.getMessage(), false, Instant.now(reloj));
            throw new CorreoNoEnviadoException(e.getMessage(), e);
        }
        try {
            google.enviar(token, ArmadoCorreo.bytes(correo, NOMBRE_REMITENTE + " <" + conexion.cuenta() + ">"));
        } catch (CorreoNoEnviadoException e) {
            conexiones.registrarError(e.getMessage(), false, Instant.now(reloj));
            throw e;
        }
        if (conexion.ultimoError() != null) {
            conexiones.limpiarError();
        }
    }

    private synchronized String tokenDeAcceso(Conexion conexion) {
        Instant ahora = Instant.now(reloj);
        if (tokenVigente != null && conexion.cuenta().equals(tokenDeCuenta) && ahora.isBefore(tokenExpira)) {
            return tokenVigente.valor();
        }
        TokenAcceso nuevo = google.refrescar(cifrado.descifrar(conexion.tokenCifrado()));
        recordarToken(nuevo, conexion.cuenta(), ahora);
        return nuevo.valor();
    }

    private synchronized void recordarToken(TokenAcceso token, String cuenta, Instant ahora) {
        tokenVigente = token;
        tokenDeCuenta = cuenta;
        // Un minuto antes de que venza, se pide otro.
        tokenExpira = ahora.plusSeconds(Math.max(0, token.expiraEnSegundos() - 60));
    }

    private synchronized void olvidarToken() {
        tokenVigente = null;
        tokenDeCuenta = null;
        tokenExpira = Instant.EPOCH;
    }

    private String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        azar.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String reto(String verificador) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
