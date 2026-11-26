package co.coretech.portal.compartido.service;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link ClienteGoogle} por HTTP: OAuth 2.0 de Google (con PKCE) y la API de Gmail. El ID y la clave del cliente
 * OAuth salen de CORETECH_GOOGLE_ID y CORETECH_GOOGLE_SECRETO (Google Cloud Console → Credenciales).
 */
@Component
class ClienteGoogleHttp implements ClienteGoogle {

    static final String AUTORIZACION = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN = "https://oauth2.googleapis.com/token";
    static final String REVOCAR = "https://oauth2.googleapis.com/revoke";
    static final String USUARIO = "https://openidconnect.googleapis.com/v1/userinfo";
    static final String ENVIAR = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private static final Logger LOG = LoggerFactory.getLogger(ClienteGoogleHttp.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final String clienteId;
    private final String clienteSecreto;
    private final RestClient http;

    ClienteGoogleHttp(@Value("${coretech.google.cliente-id:}") String clienteId,
            @Value("${coretech.google.cliente-secreto:}") String clienteSecreto) {
        this.clienteId = clienteId;
        this.clienteSecreto = clienteSecreto;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public String urlAutorizacion(String redirect, String estado, String retoPkce) {
        return UriComponentsBuilder.fromUriString(AUTORIZACION)
                .queryParam("client_id", clienteId)
                .queryParam("redirect_uri", redirect)
                .queryParam("response_type", "code")
                .queryParam("scope", ALCANCES)
                // offline + consent: Google entrega el permiso duradero (refresh token) cada vez que se conecta
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", estado)
                .queryParam("code_challenge", retoPkce)
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }

    @Override
    public Tokens intercambiar(String codigo, String verificadorPkce, String redirect) {
        MultiValueMap<String, String> formulario = formulario();
        formulario.add("grant_type", "authorization_code");
        formulario.add("code", codigo);
        formulario.add("code_verifier", verificadorPkce);
        formulario.add("redirect_uri", redirect);
        Map<String, Object> r = postFormulario(TOKEN, formulario);
        return new Tokens(texto(r, "refresh_token"), texto(r, "access_token"), numero(r, "expires_in"), texto(r, "scope"));
    }

    @Override
    public String correoDe(String tokenAcceso) {
        try {
            Map<String, Object> r = http.get().uri(USUARIO).headers(h -> h.setBearerAuth(tokenAcceso)).retrieve().body(JSON);
            return texto(r, "email");
        } catch (RestClientException e) {
            throw new ErrorGoogleException("Google no devolvió el correo de la cuenta: " + detalle(e), e);
        }
    }

    @Override
    public TokenAcceso refrescar(String tokenRefresco) {
        MultiValueMap<String, String> formulario = formulario();
        formulario.add("grant_type", "refresh_token");
        formulario.add("refresh_token", tokenRefresco);
        try {
            Map<String, Object> r = http.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario).retrieve().body(JSON);
            return new TokenAcceso(texto(r, "access_token"), numero(r, "expires_in"));
        } catch (RestClientResponseException e) {
            if (e.getResponseBodyAsString().contains("invalid_grant")) {
                throw new PermisoRetiradoException("Google retiró el permiso para enviar correos: vuelve a conectar Gmail");
            }
            throw new ErrorGoogleException("Google no renovó el permiso: " + detalle(e), e);
        } catch (RestClientException e) {
            throw new ErrorGoogleException("Google no renovó el permiso: " + detalle(e), e);
        }
    }

    @Override
    public void enviar(String tokenAcceso, byte[] mime) {
        String crudo = Base64.getUrlEncoder().withoutPadding().encodeToString(mime);
        try {
            Map<String, Object> respuesta = http.post().uri(ENVIAR).headers(h -> h.setBearerAuth(tokenAcceso))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("raw", crudo)).retrieve().body(JSON);
            // Lo que Google confirma: el id del mensaje y sus etiquetas (SENT = quedó en Enviados). Sin datos del correo.
            LOG.info("Gmail aceptó un correo (id={}, etiquetas={})", texto(respuesta, "id"), respuesta == null ? null : respuesta.get("labelIds"));
        } catch (RestClientException e) {
            throw new ServidorCorreo.CorreoNoEnviadoException("Gmail no aceptó el correo: " + detalle(e), e);
        }
    }

    @Override
    public void revocar(String tokenRefresco) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("token", tokenRefresco);
        try {
            http.post().uri(REVOCAR).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            // El permiso ya no existía o Google no respondió: la conexión se borra igual en el portal.
        }
    }

    private MultiValueMap<String, String> formulario() {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", clienteId);
        formulario.add("client_secret", clienteSecreto);
        return formulario;
    }

    private Map<String, Object> postFormulario(String url, MultiValueMap<String, String> formulario) {
        try {
            return http.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario)
                    .retrieve().body(JSON);
        } catch (RestClientException e) {
            throw new ErrorGoogleException("Google rechazó la conexión: " + detalle(e), e);
        }
    }

    private static String texto(Map<String, Object> r, String campo) {
        Object valor = r == null ? null : r.get(campo);
        return valor == null ? null : valor.toString();
    }

    private static long numero(Map<String, Object> r, String campo) {
        Object valor = r == null ? null : r.get(campo);
        return valor instanceof Number n ? n.longValue() : 3600;
    }

    /** El error de Google sin datos sensibles (nunca incluye tokens). */
    private static String detalle(RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            String cuerpo = r.getResponseBodyAsString();
            return r.getStatusCode().value() + " " + (cuerpo.length() > 180 ? cuerpo.substring(0, 180) : cuerpo).replaceAll("\\s+", " ");
        }
        return e.getMessage();
    }
}
