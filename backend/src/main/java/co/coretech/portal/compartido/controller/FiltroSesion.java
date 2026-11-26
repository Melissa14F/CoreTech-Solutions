package co.coretech.portal.compartido.controller;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import co.coretech.portal.compartido.service.CargadorUsuarioSesion;
import co.coretech.portal.compartido.service.UsuarioSesion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee la cookie de sesión, valida el JWT y carga el usuario con su rol y sus permisos.
 * Sin cookie o con una inválida la petición sigue como anónima (la configuración decide si eso es 401).
 * <p>
 * Con la contraseña temporal sin cambiar (RF-05, HU-06), solo deja ver la sesión, cambiar la
 * contraseña y cerrar sesión; lo demás responde 403.
 * <p>
 * No es un bean: lo crea {@link ConfiguracionSeguridad}, para que no se registre dos veces como filtro.
 */
class FiltroSesion extends OncePerRequestFilter {

    static final String MENSAJE_CAMBIAR_PASSWORD = "Cambia tu contraseña temporal para continuar";
    private static final Set<String> PERMITIDAS_CON_PASSWORD_TEMPORAL = Set.of(
            "/api/v1/auth/yo", "/api/v1/auth/cambiar-password", "/api/v1/auth/logout");
    private static final Set<String> RUTAS_PUBLICAS = Set.of(ConfiguracionSeguridad.PUBLICAS_POST);

    private final ServicioSesion sesiones;
    private final CargadorUsuarioSesion usuarios;
    private final EscritorErrores errores;

    FiltroSesion(ServicioSesion sesiones, CargadorUsuarioSesion usuarios, EscritorErrores errores) {
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.errores = errores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        // La versión del token debe ser la del usuario: si cambió o restableció la contraseña, las sesiones anteriores no valen
        Optional<UsuarioSesion> usuario = token(peticion).flatMap(sesiones::leer)
                .flatMap(t -> usuarios.cargar(t.usuarioId()).filter(u -> u.versionSesion() == t.version()));
        if (usuario.isPresent()) {
            UsuarioSesion u = usuario.get();
            String ruta = peticion.getRequestURI();
            // Las rutas públicas (login, registro, recuperación…) no piden sesión: no se bloquean aunque llegue
            // la cookie de alguien con contraseña temporal (por ejemplo, para pedir el enlace si la olvidó).
            if (u.debeCambiarPassword() && !PERMITIDAS_CON_PASSWORD_TEMPORAL.contains(ruta) && !RUTAS_PUBLICAS.contains(ruta)) {
                errores.escribir(respuesta, HttpStatus.FORBIDDEN, MENSAJE_CAMBIAR_PASSWORD);
                return;
            }
            var autoridades = u.permisos().stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u, null, autoridades));
            SecurityContextHolder.setContext(contexto);
        }
        cadena.doFilter(peticion, respuesta);
    }

    private static Optional<String> token(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (ServicioSesion.COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
