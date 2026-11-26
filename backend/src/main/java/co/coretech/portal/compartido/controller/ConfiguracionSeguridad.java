package co.coretech.portal.compartido.controller;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.service.CargadorUsuarioSesion;

/**
 * Seguridad de la API (RNF-01, RNF-10): sin sesiones de servidor; la sesión es el JWT de la cookie.
 * <ul>
 *   <li>Públicos: registro, inicio y cierre de sesión, recuperación de contraseña y Swagger.</li>
 *   <li>Todo lo demás de /api/** pide sesión (401 sin ella). Cada endpoint pide además su permiso
 *       con {@code @PreAuthorize("@permisos.tiene('…')")} (403 sin él).</li>
 *   <li>CSRF: SameSite=Strict + {@link FiltroOrigen}; por eso el CSRF de Spring va desactivado.</li>
 * </ul>
 * Cuando una pantalla pública necesite datos (servicios, contenido del sitio), agrega su ruta GET a
 * {@link #PUBLICAS_GET}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ConfiguracionSeguridad {

    static final String[] PUBLICAS_POST = {
        "/api/v1/auth/registro", "/api/v1/auth/login", "/api/v1/auth/logout",
        "/api/v1/auth/recuperacion", "/api/v1/auth/restablecer",
        // Registro con Google o Microsoft (RF-50): "Completa tu registro" todavía no tiene sesión
        "/api/v1/auth/externo/completar",
    };
    /** Página principal (HU-07): servicios visibles, sus imágenes subidas y los textos del sitio. */
    static final String[] PUBLICAS_GET = { "/api/v1/servicios", "/api/v1/servicios/imagenes/*", "/api/v1/contenido",
            // Regreso de Google al conectar el correo del sistema (D-51): llega sin cookie; lo valida el "estado".
            "/api/v1/correo-sistema/gmail/callback",
            // Ingreso con Google o Microsoft (RF-50): botones, ida y regreso del proveedor, datos para completar el registro
            "/api/v1/auth/proveedores", "/api/v1/auth/externo/*", "/api/v1/auth/externo/callback/*" };
    static final String[] DOCUMENTACION = { "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**" };

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, ServicioSesion sesiones, CargadorUsuarioSesion usuarios,
            EscritorErrores errores, PropiedadesCoretech propiedades, Clock reloj) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.POST, PUBLICAS_POST).permitAll();
                    // Ojo: requestMatchers(GET) sin rutas abriría TODOS los GET; solo se agrega si hay alguna.
                    if (PUBLICAS_GET.length > 0) {
                        a.requestMatchers(HttpMethod.GET, PUBLICAS_GET).permitAll();
                    }
                    a.requestMatchers(DOCUMENTACION).permitAll()
                            .requestMatchers("/error").permitAll()
                            .anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((peticion, respuesta, ex) ->
                                errores.escribir(respuesta, HttpStatus.UNAUTHORIZED, "Tu sesión venció, ingresa de nuevo"))
                        .accessDeniedHandler((peticion, respuesta, ex) ->
                                errores.escribir(respuesta, HttpStatus.FORBIDDEN, "No tienes permiso para hacer esta acción")))
                .addFilterBefore(new FiltroOrigen(propiedades.origenesPermitidos(), errores), CsrfFilter.class)
                .addFilterBefore(new FiltroLimitePeticiones(propiedades.limites(), new LimitadorPeticiones(reloj), errores), CsrfFilter.class)
                .addFilterBefore(new FiltroSesion(sesiones, usuarios, errores), AnonymousAuthenticationFilter.class)
                .build();
    }

    /** BCrypt con costo 10 (RNF-10). */
    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder(10);
    }
}
