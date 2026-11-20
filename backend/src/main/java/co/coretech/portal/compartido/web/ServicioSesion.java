package co.coretech.portal.compartido.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.datos.SesionesCerradasJdbc;

/**
 * Sesión en una cookie (decisión de CLAUDE.md, RNF-10): JWT firmado con HS256, en la cookie
 * {@value #COOKIE} HttpOnly y SameSite=Strict; Secure en producción. El JavaScript nunca la lee.
 * <p>
 * El token lleva el id del usuario ("sub"), su versión de sesión ("ver"), si eligió "Recordarme" ("rec")
 * y un id propio ("jti"). Rol y permisos se leen de la base en cada petición. Una sesión deja de valer
 * si vence, si se cerró con "Cerrar sesión" (su jti queda en sesiones_cerradas) o si la versión del
 * usuario cambió (cambió o restableció la contraseña).
 */
@Component
public class ServicioSesion {

    public static final String COOKIE = "sesion";
    private static final String EMISOR = "coretech-portal";
    private static final String VERSION = "ver";
    private static final String RECORDARME = "rec";

    private final NimbusJwtEncoder codificador;
    private final NimbusJwtDecoder decodificador;
    private final PropiedadesCoretech.Sesion propiedades;
    private final SesionesCerradasJdbc cerradas;
    private final Clock reloj;

    /** Lo que dice un token válido. */
    public record Token(UUID usuarioId, int version, boolean recordarme, UUID jti, Instant expira) {
    }

    ServicioSesion(PropiedadesCoretech propiedades, SesionesCerradasJdbc cerradas, Clock reloj) {
        this.propiedades = propiedades.sesion();
        this.cerradas = cerradas;
        this.reloj = reloj;
        SecretKey clave = new SecretKeySpec(this.propiedades.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.codificador = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        this.decodificador = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator vigencia = new JwtTimestampValidator(Duration.ZERO);
        vigencia.setClock(reloj);
        this.decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(vigencia, new JwtIssuerValidator(EMISOR)));
    }

    /** Cookie de una sesión nueva: 8 horas, o 30 días con "Recordarme". */
    public ResponseCookie abrir(UUID usuarioId, int version, boolean recordarme) {
        Duration duracion = recordarme ? propiedades.duracionRecordarme() : propiedades.duracion();
        Instant ahora = Instant.now(reloj);
        JwtClaimsSet reclamos = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .id(UUID.randomUUID().toString())
                .subject(usuarioId.toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .claim(VERSION, version)
                .claim(RECORDARME, recordarme)
                .build();
        String token = codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), reclamos))
                .getTokenValue();
        return cookie(token, duracion);
    }

    /** "Cerrar sesión": el token deja de valer aunque alguien lo haya copiado, y la cookie se borra. */
    public ResponseCookie cerrar(String token) {
        if (token != null && !token.isBlank()) {
            leer(token).ifPresent(t -> cerradas.cerrar(t.jti(), t.expira(), Instant.now(reloj)));
        }
        return cookie("", Duration.ZERO);
    }

    /** El token si es válido, está firmado por nosotros, no venció y no se cerró. */
    public Optional<Token> leer(String token) {
        try {
            Jwt jwt = decodificador.decode(token);
            Number version = jwt.getClaim(VERSION);
            Boolean recordarme = jwt.getClaim(RECORDARME);
            UUID jti = jwt.getId() == null ? null : UUID.fromString(jwt.getId());
            if (jti == null || cerradas.estaCerrada(jti)) {
                return Optional.empty();
            }
            return Optional.of(new Token(UUID.fromString(jwt.getSubject()), version == null ? 0 : version.intValue(),
                    Boolean.TRUE.equals(recordarme), jti, jwt.getExpiresAt()));
        } catch (JwtException | IllegalArgumentException | NullPointerException | ClassCastException tokenInvalido) {
            return Optional.empty();
        }
    }

    private ResponseCookie cookie(String valor, Duration duracion) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(propiedades.cookieSegura())
                .sameSite("Strict")
                .path("/")
                .maxAge(duracion)
                .build();
    }
}
