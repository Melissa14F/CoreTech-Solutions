package co.coretech.portal.acceso.aplicacion;

import java.time.Duration;
import java.util.Map;

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

import co.coretech.portal.acceso.dominio.ProveedorExterno;

/**
 * {@link ClienteIdentidadExterna} por HTTP. Google usa el mismo cliente OAuth del correo del sistema
 * (CORETECH_GOOGLE_ID y _SECRETO); Microsoft, el de su registro de aplicación (CORETECH_MICROSOFT_ID y _SECRETO).
 * <p>
 * Pide el token y luego los datos de la persona al proveedor (userinfo), siempre por HTTPS y con la clave del cliente:
 * así los datos vienen del proveedor y no del navegador. Los tokens no se guardan.
 */
@Component
class ClienteIdentidadExternaHttp implements ClienteIdentidadExterna {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final Map<ProveedorExterno, Credencial> credenciales;
    private final RestClient http;

    private record Credencial(String id, String secreto) {

        boolean completa() {
            return id != null && !id.isBlank() && secreto != null && !secreto.isBlank();
        }
    }

    ClienteIdentidadExternaHttp(@Value("${coretech.google.cliente-id:}") String googleId,
            @Value("${coretech.google.cliente-secreto:}") String googleSecreto,
            @Value("${coretech.microsoft.cliente-id:}") String microsoftId,
            @Value("${coretech.microsoft.cliente-secreto:}") String microsoftSecreto) {
        this.credenciales = Map.of(
                ProveedorExterno.GOOGLE, new Credencial(googleId, googleSecreto),
                ProveedorExterno.MICROSOFT, new Credencial(microsoftId, microsoftSecreto));
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public boolean configurado(ProveedorExterno proveedor) {
        return credenciales.get(proveedor).completa();
    }

    @Override
    public String urlAutorizacion(ProveedorExterno proveedor, String redirect, String estado, String retoPkce) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(proveedor.urlAutorizacion())
                .queryParam("client_id", credenciales.get(proveedor).id())
                .queryParam("redirect_uri", redirect)
                .queryParam("response_type", "code")
                .queryParam("scope", ProveedorExterno.ALCANCES)
                .queryParam("state", estado)
                .queryParam("code_challenge", retoPkce)
                .queryParam("code_challenge_method", "S256");
        // Que la persona elija la cuenta (útil si tiene varias abiertas en el navegador)
        url.queryParam("prompt", "select_account");
        return url.encode().build().toUriString();
    }

    @Override
    public Identidad identidad(ProveedorExterno proveedor, String codigo, String verificadorPkce, String redirect) {
        Credencial credencial = credenciales.get(proveedor);
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", credencial.id());
        formulario.add("client_secret", credencial.secreto());
        formulario.add("grant_type", "authorization_code");
        formulario.add("code", codigo);
        formulario.add("code_verifier", verificadorPkce);
        formulario.add("redirect_uri", redirect);
        try {
            Map<String, Object> tokens = http.post().uri(proveedor.urlToken())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario).retrieve().body(JSON);
            String acceso = texto(tokens, "access_token");
            Map<String, Object> persona = http.get().uri(proveedor.urlUsuario())
                    .headers(h -> h.setBearerAuth(acceso)).retrieve().body(JSON);
            String email = texto(persona, "email");
            boolean verificado = switch (proveedor) {
                // Google lo dice explícitamente
                case GOOGLE -> Boolean.TRUE.equals(persona.get("email_verified")) || "true".equals(texto(persona, "email_verified"));
                // Inquilino "consumers": cuentas personales de Microsoft, con el correo verificado por Microsoft
                case MICROSOFT -> email != null;
            };
            return new Identidad(texto(persona, "sub"), email, verificado, texto(persona, "name"));
        } catch (RestClientException e) {
            throw new ErrorProveedorException(proveedor.nombre() + " no respondió bien: " + detalle(e), e);
        }
    }

    private static String texto(Map<String, Object> r, String campo) {
        Object valor = r == null ? null : r.get(campo);
        return valor == null ? null : valor.toString();
    }

    private static String detalle(RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            String cuerpo = r.getResponseBodyAsString();
            return r.getStatusCode().value() + " " + (cuerpo.length() > 180 ? cuerpo.substring(0, 180) : cuerpo).replaceAll("\\s+", " ");
        }
        return e.getMessage();
    }
}
