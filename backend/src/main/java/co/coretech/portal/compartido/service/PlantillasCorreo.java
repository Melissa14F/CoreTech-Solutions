package co.coretech.portal.compartido.service;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import co.coretech.portal.compartido.PropiedadesCoretech;

/**
 * Arma los correos con las plantillas HTML de {@code resources/templates/correos/} (Thymeleaf), en español
 * y con la versión en texto plano. Los enlaces de los avisos se guardan como rutas de la SPA
 * ({@code /cliente/citas}); aquí se completan con {@code coretech.url-portal}.
 */
@Component
public class PlantillasCorreo {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    private final ITemplateEngine motor;
    private final String urlPortal;

    PlantillasCorreo(ITemplateEngine motor, PropiedadesCoretech propiedades) {
        this.motor = motor;
        this.urlPortal = propiedades.urlPortal().replaceAll("/+$", "");
    }

    /**
     * Correo de un aviso (HU-24): saludo, título, texto y, si hay enlace, el botón para abrirlo en el portal.
     *
     * @param enlace ruta de la SPA que empieza con "/", o {@code null}
     */
    public Correo aviso(String para, String nombre, String titulo, String texto, String enlace) {
        String enlaceCompleto = enlace == null ? null : urlPortal + enlace;
        Context contexto = new Context(ESPANOL);
        contexto.setVariable("nombre", nombre);
        contexto.setVariable("titulo", titulo);
        contexto.setVariable("texto", texto);
        contexto.setVariable("enlace", enlaceCompleto);
        contexto.setVariable("urlPortal", urlPortal);
        String html = motor.process("correos/aviso", contexto);

        StringBuilder plano = new StringBuilder()
                .append("Hola, ").append(nombre).append(":\n\n")
                .append(titulo).append("\n\n")
                .append(texto).append("\n\n");
        if (enlaceCompleto != null) {
            plano.append("Míralo en el portal: ").append(enlaceCompleto).append("\n\n");
        }
        plano.append("CoreTech Solutions\n")
                .append("Recibes este correo porque tienes una cuenta en el portal. No respondas a este mensaje.\n");
        return new Correo(para, titulo, plano.toString(), html);
    }
}
