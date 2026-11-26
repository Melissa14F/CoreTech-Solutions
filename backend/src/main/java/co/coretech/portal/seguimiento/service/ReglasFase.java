package co.coretech.portal.seguimiento.service;

import java.time.LocalDate;

import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento.Proyecto;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseFila;

/**
 * Reglas de las fases de un proyecto (HU-11, HU-12, HU-13; RN-16, RN-17): qué estado del proyecto y de la fase permite entregar,
 * aprobar, pedir cambios o registrar una corrección, y cómo se validan los textos de un cambio o de una corrección. Son
 * funciones puras sobre los datos que llegan (sin base de datos ni sesión): las de validación juntan sus errores en un
 * {@link Acumulador} para responder uno por campo y las de estado lanzan un 409. Las usa {@link ServicioFases}, que conserva el
 * flujo, los permisos, la auditoría y los avisos.
 */
final class ReglasFase {

    private ReglasFase() {
    }

    /** HU-11: el cliente pide un ajuste o algo nuevo y lo cuenta con sus palabras (20 a 800 caracteres). */
    static void validarCambioPedido(String tipo, String texto, String productOwner, Acumulador errores) {
        if (tipo == null || !(tipo.equals("AJUSTE") || tipo.equals("ALCANCE"))) {
            errores.agregar("tipo", "Elige si es un ajuste o algo nuevo.");
        }
        if (texto.isEmpty()) {
            errores.agregar("descripcion", "Cuéntanos qué quieres cambiar.");
        } else if (texto.length() < 20) {
            errores.agregar("descripcion", "Escribe al menos 20 caracteres para que "
                    + (productOwner.isEmpty() ? "el equipo" : productOwner) + " te entienda.");
        } else if (texto.length() > 800) {
            errores.agregar("descripcion", "Escribe máximo 800 caracteres.");
        }
    }

    /**
     * HU-12, RN-16: se puede corregir con el plan aprobado y el proyecto ACTIVO o EN_GARANTIA (esta última, hasta que se cumplan
     * los 30 días), y solo una fase en curso o aprobada; una que espera la respuesta del cliente no recibe correcciones.
     */
    static void exigirAdmiteCorreccion(Proyecto proyecto, FaseFila fase, LocalDate hoy) {
        boolean enGarantia = proyecto.estado().equals("EN_GARANTIA");
        if (!proyecto.estadoPlan().equals("APROBADO") || !(proyecto.estado().equals("ACTIVO") || enGarantia)) {
            throw new ConflictoException(ServicioSeguimiento.ESTADO_NO_ADMITE);
        }
        if (enGarantia && proyecto.garantiaHasta() != null && !hoy.isBefore(proyecto.garantiaHasta())) {
            throw new ConflictoException(ServicioFases.GARANTIA_TERMINADA);
        }
        if (fase.estado().equals("EN_REVISION")) {
            throw new ConflictoException("Esta fase espera la respuesta del cliente: no se le agregan correcciones");
        }
        if (fase.estado().equals("PENDIENTE")) {
            throw new ConflictoException(ServicioFases.SOLO_FASES_EN_CURSO_O_APROBADAS);
        }
    }

    /** HU-12: origen de la lista, nombre corto (3 a 80) y explicación para el cliente (20 a 400). */
    static void validarCorreccion(String origen, String titulo, String explicacion, String nombreCliente, Acumulador errores) {
        if (!ServicioFases.ORIGENES.contains(origen)) {
            errores.agregar("origen", "Elige de dónde sale la corrección.");
        }
        if (titulo.isEmpty()) {
            errores.agregar("titulo", "Escribe un nombre corto.");
        } else if (titulo.length() < 3) {
            errores.agregar("titulo", "Escribe al menos 3 caracteres.");
        } else if (titulo.length() > 80) {
            errores.agregar("titulo", "Escribe máximo 80 caracteres.");
        }
        if (explicacion.isEmpty()) {
            errores.agregar("explicacion", "Explícale a " + nombreCliente + " qué se va a corregir y por qué.");
        } else if (explicacion.length() < 20) {
            errores.agregar("explicacion", "Escribe al menos 20 caracteres.");
        } else if (explicacion.length() > 400) {
            errores.agregar("explicacion", "Escribe máximo 400 caracteres.");
        }
    }

    /** Entregar, cerrar o aprobar solo se puede con el proyecto ACTIVO y su plan APROBADO. */
    static void exigirActivo(Proyecto p) {
        if (!p.estado().equals("ACTIVO") || !p.estadoPlan().equals("APROBADO")) {
            throw new ConflictoException(ServicioSeguimiento.ESTADO_NO_ADMITE);
        }
    }

    /** El cliente solo responde una fase que él aprueba, está EN_REVISION y de un proyecto ACTIVO. */
    static void exigirEnRevision(Proyecto proyecto, FaseFila fase) {
        if (!proyecto.estado().equals("ACTIVO") || !fase.estado().equals("EN_REVISION") || !fase.apruebaCliente()) {
            throw new ConflictoException(ServicioFases.NO_ESPERA_APROBACION);
        }
    }

    /** El texto que se agrega al aviso cuando el proyecto llega al 100 % y queda en garantía. */
    static String textoDeCierre(Proyecto p) {
        return p.estado().equals("EN_GARANTIA") && p.garantiaHasta() != null
                ? " El proyecto llegó al 100 % y queda en garantía hasta el " + Fechas.conDia(p.garantiaHasta()) + "."
                : "";
    }
}
