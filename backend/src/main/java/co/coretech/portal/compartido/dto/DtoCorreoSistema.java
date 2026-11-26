package co.coretech.portal.compartido.dto;

import java.time.Instant;

import co.coretech.portal.compartido.service.ConexionGmail.Estado;

/** Cuerpos de las peticiones y respuestas de {@link ControladorCorreoSistema}. Los DTO son records: la API nunca devuelve entidades. */
public final class DtoCorreoSistema {

    private DtoCorreoSistema() {
    }

    public record EstadoCorreo(boolean configurado, boolean conectado, String cuenta, String estado, Instant conectadoEn,
            String ultimoError, Instant ultimoErrorEn) {

        public static EstadoCorreo de(Estado e) {
            return new EstadoCorreo(e.configurado(), e.conectado(), e.cuenta(), e.estado(), e.conectadoEn(), e.ultimoError(),
                    e.ultimoErrorEn());
        }
    }

    public record UrlConexion(String url) {
    }

    public record SolicitudPrueba(String para) {
    }

    public record ResultadoPrueba(String canal, String mensaje) {
    }
}
