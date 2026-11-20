package co.coretech.portal.acceso;

import java.util.Map;
import java.util.UUID;

/**
 * Citas de cada cliente, para la columna "Actividad" de Usuarios (HU-20). La define acceso y la
 * implementa agenda: así acceso no depende de agenda (agenda sí usará acceso).
 */
public interface CitasDeUsuarios {

    record Conteo(int pendientes, int confirmadas, int atendidas, int canceladas) {
    }

    /** Cliente → sus citas por estado (solo los clientes que tienen alguna). */
    Map<UUID, Conteo> porCliente();
}
