package co.coretech.portal.compartido.aplicacion;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import co.coretech.portal.compartido.aplicacion.ConexionGmail.Estado;
import co.coretech.portal.compartido.aplicacion.ConexionGmail.Resultado;
import co.coretech.portal.compartido.aplicacion.ServidorCorreo.CorreoNoEnviadoException;

/** Pantalla "Correo del sistema" del administrador (D-51): conectar Gmail, ver el estado y enviar un correo de prueba. */
@Service
public class ServicioCorreoSistema {

    static final String CORREO_INVALIDO = "Escribe un correo válido, por ejemplo nombre@empresa.com.";
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ConexionGmail gmail;
    private final ServidorCorreoEnrutado servidor;
    private final PlantillasCorreo plantillas;
    private final SesionActual sesion;

    ServicioCorreoSistema(ConexionGmail gmail, ServidorCorreoEnrutado servidor, PlantillasCorreo plantillas, SesionActual sesion) {
        this.gmail = gmail;
        this.servidor = servidor;
        this.plantillas = plantillas;
        this.sesion = sesion;
    }

    public Estado estado() {
        return gmail.estado();
    }

    public String iniciarConexion() {
        return gmail.iniciarConexion();
    }

    public Resultado completarConexion(String estado, String codigo, String error) {
        return gmail.completarConexion(estado, codigo, error);
    }

    public void desconectar() {
        gmail.desconectar();
    }

    /**
     * "Enviar correo de prueba": sale de inmediato (no por la cola) para mostrar en pantalla si funcionó.
     *
     * @return por dónde salió: "GMAIL" (correo real) o "SMTP" (Mailpit en desarrollo)
     */
    public String enviarPrueba(String para) {
        String destino = para == null ? "" : para.trim();
        if (!CORREO.matcher(destino).matches() || destino.length() > 120) {
            throw new ReglaNegocioException("para", CORREO_INVALIDO);
        }
        String canal = servidor.canal(destino);
        try {
            servidor.enviar(plantillas.aviso(destino, sesion.usuario().nombre(), "Correo de prueba de CoreTech",
                    "Si recibes este mensaje, el correo del sistema funciona: los avisos del portal llegarán desde esta cuenta.",
                    null));
        } catch (CorreoNoEnviadoException e) {
            throw new ConflictoException("No se pudo enviar el correo: " + e.getMessage());
        }
        return canal;
    }
}
