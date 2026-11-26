package co.coretech.portal.compartido;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias del portal ({@code coretech.*} en application.yml).
 *
 * @param zonaNegocio        zona horaria del negocio (America/Bogota). La base guarda instantes en UTC;
 *                           esta zona se usa para el horario laboral, los días hábiles y las horas que se muestran.
 * @param sesion             cookie y JWT de la sesión (RNF-10)
 * @param origenesPermitidos orígenes (esquema://host:puerto) desde los que se aceptan peticiones que cambian datos
 * @param urlPortal          dirección de la SPA, para armar los enlaces de los correos (recuperar contraseña)
 * @param archivos           carpeta de los archivos que suben los usuarios (imágenes de servicios, adjuntos)
 * @param limites            intentos por IP en las rutas públicas de acceso (contra adivinar contraseñas)
 */
@ConfigurationProperties(prefix = "coretech")
public record PropiedadesCoretech(ZoneId zonaNegocio, Sesion sesion, List<String> origenesPermitidos, String urlPortal,
        Archivos archivos, Limites limites) {

    public PropiedadesCoretech {
        Objects.requireNonNull(zonaNegocio, "Falta coretech.zona-negocio en application.yml");
        Objects.requireNonNull(sesion, "Falta coretech.sesion en application.yml");
        Objects.requireNonNull(urlPortal, "Falta coretech.url-portal en application.yml");
        origenesPermitidos = origenesPermitidos == null ? List.of() : List.copyOf(origenesPermitidos);
        archivos = archivos == null ? new Archivos(null) : archivos;
        limites = limites == null ? new Limites(null, null, null, null) : limites;
    }

    /**
     * Intentos por IP antes de responder 429 (RNF-10). Por defecto: inicio de sesión 10 por minuto,
     * pedir el enlace de recuperación 5 cada 15 minutos, restablecer 10 cada 15 minutos y registrarse 10 por hora.
     */
    public record Limites(Integer loginPorMinuto, Integer recuperacionPor15Minutos, Integer restablecerPor15Minutos,
            Integer registroPorHora) {

        public Limites {
            loginPorMinuto = loginPorMinuto == null ? 10 : loginPorMinuto;
            recuperacionPor15Minutos = recuperacionPor15Minutos == null ? 5 : recuperacionPor15Minutos;
            restablecerPor15Minutos = restablecerPor15Minutos == null ? 10 : restablecerPor15Minutos;
            registroPorHora = registroPorHora == null ? 10 : registroPorHora;
        }
    }

    /**
     * @param directorio carpeta raíz de los archivos; en Docker es un volumen (P25). Por defecto, "datos/archivos"
     *                   junto a donde corre la API.
     */
    public record Archivos(String directorio) {

        public Archivos {
            directorio = directorio == null || directorio.isBlank() ? "datos/archivos" : directorio;
        }
    }

    /**
     * @param secreto             clave del HS256; viene de la variable de entorno CORETECH_SESION_SECRETO (mínimo 32 bytes)
     * @param cookieSegura        Secure en la cookie: true en producción (solo HTTPS), false en desarrollo
     * @param duracion            sesión normal (8 h)
     * @param duracionRecordarme  sesión con "Recordarme" (30 días)
     */
    public record Sesion(String secreto, Boolean cookieSegura, Duration duracion, Duration duracionRecordarme) {

        public Sesion {
            if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException(
                        "coretech.sesion.secreto debe tener al menos 32 bytes (variable de entorno CORETECH_SESION_SECRETO)");
            }
            cookieSegura = cookieSegura == null || cookieSegura;
            duracion = duracion == null ? Duration.ofHours(8) : duracion;
            duracionRecordarme = duracionRecordarme == null ? Duration.ofDays(30) : duracionRecordarme;
        }

        /** Sin la clave que firma las sesiones: quien la tenga puede fabricar una sesión de cualquiera. */
        @Override
        public String toString() {
            return "Sesion[cookieSegura=" + cookieSegura + ", duracion=" + duracion + "]";
        }
    }
}
