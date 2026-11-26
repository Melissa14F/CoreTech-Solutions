package co.coretech.portal.proyectos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;
import co.coretech.portal.proyectos.service.ServicioPlan.Solicitud;

/**
 * Reglas de validación del plan de trabajo y de la cotización (HU-28, HU-29; RN-27 a RN-29): de 2 a 8 fases, valor entre
 * $1.000.000 y $200.000.000, entrega al menos 14 días después del inicio, qué incluye (20 a 600 caracteres) y los comentarios del
 * cliente. Son funciones puras sobre los datos que llegan (sin base de datos ni sesión): juntan sus errores en un {@link Acumulador}
 * para responder uno por campo. Las usa {@link ServicioPlan}, que conserva el flujo (guardar, enviar, aprobar, pedir ajustes,
 * rechazar), los permisos y los avisos.
 */
final class ReglasPlan {

    static final long VALOR_MINIMO = 1_000_000L;
    static final long VALOR_MAXIMO = 200_000_000L;
    /** La entrega más lejana que se acepta cuenta desde hoy (5 años): un tope fijo dejaría de servir cuando pasara su año. */
    static final int ANIOS_MAXIMOS_DE_ENTREGA = 5;

    /** Los datos de la cotización ya validados: valor, qué incluye y fecha de entrega (los que no vengan, nulos). */
    record Datos(BigDecimal valor, String condiciones, LocalDate entrega) {
    }

    private ReglasPlan() {
    }

    /** Última fecha de entrega que se acepta hoy; la usan también los cambios de plan. */
    static LocalDate ultimaFechaDeEntrega(LocalDate hoy) {
        return hoy.plusYears(ANIOS_MAXIMOS_DE_ENTREGA);
    }

    static void validarFases(List<FasePlantilla> fases, Acumulador errores) {
        try {
            ServicioPlantillas.validar(fases, "el plan");
        } catch (ValidacionException e) {
            e.fallas().forEach(falla -> errores.agregar(falla.campo(), falla.mensaje()));
        }
    }

    /**
     * RN-29: valor entre $1.000.000 y $200.000.000, entrega al menos 14 días después del inicio y qué incluye (20 a 600).
     * Con {@code obligatoria} falso (borrador) solo se revisa lo que venga escrito.
     */
    static Datos validarCotizacion(Solicitud pedida, ProyectoFila f, boolean obligatoria, LocalDate hoy, Acumulador errores) {
        BigDecimal valor = pedida.valorCotizado();
        String condiciones = pedida.condicionesCotizacion() == null ? "" : pedida.condicionesCotizacion().trim();
        String textoFecha = pedida.fechaEstimadaEntrega() == null ? "" : pedida.fechaEstimadaEntrega().trim();

        if (valor == null) {
            if (obligatoria) {
                errores.agregar("valorCotizado", "Escribe el valor del proyecto.");
            }
        } else if (valor.signum() < 0 || valor.stripTrailingZeros().scale() > 0 || valor.compareTo(BigDecimal.valueOf(VALOR_MINIMO)) < 0
                || valor.compareTo(BigDecimal.valueOf(VALOR_MAXIMO)) > 0) {
            errores.agregar("valorCotizado", "El valor debe estar entre $1.000.000 y $200.000.000.");
        }

        if (condiciones.isEmpty()) {
            if (obligatoria) {
                errores.agregar("condicionesCotizacion", "Escribe qué incluye el valor.");
            }
        } else if (condiciones.length() < 20) {
            errores.agregar("condicionesCotizacion", "Escribe al menos 20 caracteres.");
        } else if (condiciones.length() > 600) {
            errores.agregar("condicionesCotizacion", "Escribe máximo 600 caracteres.");
        }
        if (obligatoria && (errores.tiene("valorCotizado") || errores.tiene("condicionesCotizacion"))
                && (valor == null || condiciones.isEmpty())) {
            errores.agregar("cotizacion", "Completa el valor y lo que incluye antes de enviar el plan");
        }

        LocalDate entrega = null;
        if (textoFecha.isEmpty()) {
            if (obligatoria) {
                errores.agregar("fechaEstimadaEntrega", "Elige la fecha estimada de entrega.");
            }
        } else {
            try {
                entrega = LocalDate.parse(textoFecha);
                if (entrega.isBefore(f.fechaInicio().plusDays(14))) {
                    errores.agregar("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio ("
                            + Fechas.corta(f.fechaInicio()) + ").");
                } else if (entrega.isAfter(ultimaFechaDeEntrega(hoy))) {
                    errores.agregar("fechaEstimadaEntrega", "Revisa el año de la fecha.");
                }
            } catch (DateTimeParseException e) {
                errores.agregar("fechaEstimadaEntrega", "Escribe la fecha como AAAA-MM-DD.");
            }
        }
        return new Datos(valor == null ? null : valor.setScale(0, java.math.RoundingMode.DOWN), condiciones.isEmpty() ? null : condiciones, entrega);
    }

    static void validarRespuesta(String texto, String campo, String vacio, String corto) {
        var errores = new Acumulador();
        if (texto.isEmpty()) {
            errores.agregar(campo, vacio);
        } else if (texto.length() < 20) {
            errores.agregar(campo, corto);
        } else if (texto.length() > 600) {
            errores.agregar(campo, "Escribe máximo 600 caracteres.");
        }
        errores.lanzarSiHay();
    }

    static BigDecimal porcentajePorFase(int fases) {
        return fases == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(fases), 1, java.math.RoundingMode.HALF_UP);
    }
}
