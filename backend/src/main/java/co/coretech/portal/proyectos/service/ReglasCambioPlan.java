package co.coretech.portal.proyectos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.proyectos.repository.CambiosPlanJdbc.FaseDelPlan;
import co.coretech.portal.proyectos.domain.PasoPlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Reglas de validación de los cambios de un plan aprobado (HU-30, RN-28, RN-30): motivo, nueva entrega, valor adicional, fase nueva
 * y que el plan siga teniendo de 2 a 8 fases con una que apruebe el cliente. Son funciones puras sobre los datos que llegan (sin
 * base de datos ni sesión): cada una junta sus errores en un {@link Acumulador} para responder uno por campo. Las usa
 * {@link ServicioCambiosPlan}, que conserva el flujo (proponer, autorizar, rechazar, aceptar) y los permisos.
 */
final class ReglasCambioPlan {

    static final long VALOR_MAXIMO = 200_000_000L;

    private ReglasCambioPlan() {
    }

    static void validarMotivo(String motivo, Acumulador errores) {
        if (motivo.isEmpty()) {
            errores.agregar("motivo", "Cuéntale al administrador por qué hace falta este cambio.");
        } else if (motivo.length() < 20) {
            errores.agregar("motivo", "Escribe al menos 20 caracteres.");
        } else if (motivo.length() > 500) {
            errores.agregar("motivo", "Escribe máximo 500 caracteres.");
        }
    }

    static LocalDate validarEntrega(String texto, ProyectoFila f, LocalDate hoy, Acumulador errores) {
        if (texto == null || texto.isBlank()) {
            errores.agregar("nuevaEntrega", "Elige la nueva fecha de entrega.");
            return null;
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            errores.agregar("nuevaEntrega", "Elige una fecha válida.");
            return null;
        }
        if (f.fechaInicio() != null && fecha.isBefore(f.fechaInicio().plusDays(14))) {
            errores.agregar("nuevaEntrega", "La entrega debe ser al menos 14 días después del inicio del proyecto.");
            return null;
        }
        LocalDate ultima = ReglasPlan.ultimaFechaDeEntrega(hoy);
        if (fecha.isAfter(ultima)) {
            errores.agregar("nuevaEntrega", "Elige una fecha de entrega hasta el "
                    + ultima.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new java.util.Locale("es", "CO"))) + ".");
            return null;
        }
        return fecha;
    }

    static BigDecimal validarValor(BigDecimal valor, Acumulador errores) {
        if (valor == null) {
            errores.agregar("valorAdicional", "Escribe el valor adicional (puede ser $0).");
            return BigDecimal.ZERO;
        }
        if (valor.stripTrailingZeros().scale() > 0 || valor.signum() < 0 || valor.compareTo(BigDecimal.valueOf(VALOR_MAXIMO)) > 0) {
            errores.agregar("valorAdicional", "El valor adicional debe ser un número entero entre $0 y $200.000.000");
            return BigDecimal.ZERO;
        }
        return valor.setScale(0, RoundingMode.UNNECESSARY);
    }

    /** La fase nueva va después de la fase actual (las ya empezadas no se mueven); devuelve su posición si es válida. */
    static Integer validarFaseNueva(String nombre, Integer posicion, List<PasoPlantilla> pasos, List<FaseDelPlan> fases, Acumulador errores) {
        if (nombre.length() < 3) {
            errores.agregar("nombreFase", "Escribe el nombre de la fase (al menos 3 caracteres).");
        } else if (nombre.length() > 60) {
            errores.agregar("nombreFase", "El nombre de la fase puede tener máximo 60 caracteres.");
        } else if (fases.stream().anyMatch(x -> x.nombre().trim().equalsIgnoreCase(nombre))) {
            errores.agregar("nombreFase", "Ya hay una fase con ese nombre en el plan");
        }
        if (pasos.isEmpty()) {
            errores.agregar("pasos", "Agrega al menos un paso.");
        } else if (pasos.size() > 12) {
            errores.agregar("pasos", "Cada fase puede tener máximo 12 pasos.");
        }
        for (PasoPlantilla paso : pasos) {
            if (paso.titulo().length() < 3 || paso.titulo().length() > 80) {
                errores.agregar("pasos", "El título de cada paso debe tener entre 3 y 80 caracteres.");
                break;
            }
            if (paso.explicacion() != null && paso.explicacion().length() > 160) {
                errores.agregar("pasos", "La explicación de un paso puede tener máximo 160 caracteres.");
                break;
            }
        }
        if (fases.size() >= 8) {
            errores.agregar("fases", "El plan puede tener máximo 8 fases");
        }
        int minimo = posicionMinima(fases);
        if (posicion == null || posicion < minimo || posicion > fases.size() + 1) {
            errores.agregar("posicion", "La fase nueva va después de la fase actual.");
            return null;
        }
        return posicion;
    }

    /** La primera posición libre: después de la última fase que ya empezó o terminó (RN-30). */
    static int posicionMinima(List<FaseDelPlan> fases) {
        return fases.stream().filter(x -> !x.estado().equals("PENDIENTE")).mapToInt(FaseDelPlan::orden).max().orElse(0) + 1;
    }

    /** RN-28: de 2 a 8 fases y al menos una que apruebe el cliente. */
    static void validarPlan(List<FaseDelPlan> fases) {
        Acumulador errores = new Acumulador();
        if (fases.size() < 2) {
            errores.agregar("fases", "El plan necesita al menos 2 fases");
        } else if (fases.size() > 8) {
            errores.agregar("fases", "El plan puede tener máximo 8 fases");
        }
        if (fases.stream().noneMatch(FaseDelPlan::apruebaCliente)) {
            errores.agregar("fases", "Marca al menos una fase que apruebe el cliente");
        }
        errores.lanzarSiHay();
    }
}
