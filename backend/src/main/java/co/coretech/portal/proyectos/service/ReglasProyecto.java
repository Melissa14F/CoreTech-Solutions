package co.coretech.portal.proyectos.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Set;

import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.ValidacionException.Acumulador;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Reglas de validación de los proyectos (HU-02, HU-17; RN-04, RN-05, RN-28): nombre, alcance, motivo, fechas, plantilla de
 * fases, los filtros de la lista y el estado que impide cambiar un proyecto. Son funciones puras sobre los datos que llegan (sin
 * base de datos ni sesión): las de validación juntan sus errores en un {@link Acumulador} para responder uno por campo. Las usa
 * {@link ServicioProyectos}, que conserva el flujo (crear, listar, editar, cambiar de estado), los permisos y el cupo del equipo.
 */
final class ReglasProyecto {

    static final Set<String> FILTROS = Set.of("todas", "activo", "riesgo", "plan", "pausado", "garantia", "finalizado",
            "cancelado");

    private ReglasProyecto() {
    }

    /** Los filtros de la ficha HU-17; "en riesgo" ya viene calculado por la consulta. */
    static boolean cumple(String filtro, ProyectoFila f) {
        return switch (filtro) {
            case "activo" -> f.estado().equals("ACTIVO");
            case "riesgo" -> f.riesgo() != null;
            case "plan" -> f.estado().equals("ACTIVO") && f.estadoPlan().equals("ENVIADO");
            case "pausado" -> f.estado().equals("PAUSADO");
            case "garantia" -> f.estado().equals("EN_GARANTIA");
            case "finalizado" -> f.estado().equals("FINALIZADO");
            case "cancelado" -> f.estado().equals("CANCELADO");
            default -> true;
        };
    }

    /** RN-28: de 2 a 8 fases, al menos una que apruebe el cliente y al menos un paso en cada una. */
    static boolean plantillaValida(Collection<FasePlantilla> fases) {
        return fases.size() >= 2 && fases.size() <= 8 && fases.stream().anyMatch(FasePlantilla::apruebaCliente)
                && fases.stream().allMatch(f -> !f.pasos().isEmpty());
    }

    static void validarNombre(String nombre, Acumulador errores) {
        if (nombre.isEmpty()) {
            errores.agregar("nombre", "Escribe el nombre que verá el cliente.");
        } else if (nombre.length() < 5) {
            errores.agregar("nombre", "Escribe al menos 5 caracteres.");
        } else if (nombre.length() > 60) {
            errores.agregar("nombre", "Escribe máximo 60 caracteres.");
        }
    }

    static void validarAlcance(String alcance, Acumulador errores) {
        if (alcance.isEmpty()) {
            errores.agregar("alcance", "Describe lo que se acordó en la cita.");
        } else if (alcance.length() < 20) {
            errores.agregar("alcance", "Escribe al menos 20 caracteres.");
        } else if (alcance.length() > 600) {
            errores.agregar("alcance", "Escribe máximo 600 caracteres.");
        }
    }

    static void validarMotivo(String motivo, Acumulador errores) {
        if (motivo.isEmpty()) {
            errores.agregar("motivo", "Escribe el motivo: queda en la auditoría.");
        } else if (motivo.length() < 10) {
            errores.agregar("motivo", "Escribe al menos 10 caracteres.");
        } else if (motivo.length() > 300) {
            errores.agregar("motivo", "Escribe máximo 300 caracteres.");
        }
    }

    static LocalDate fecha(String texto, String campo, String vacio, Acumulador errores) {
        if (texto == null || texto.isBlank()) {
            errores.agregar(campo, vacio);
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            errores.agregar(campo, "Escribe la fecha como AAAA-MM-DD.");
            return null;
        }
    }

    static void exigirAbierto(ProyectoFila f) {
        switch (f.estado()) {
            case "FINALIZADO" -> throw new ConflictoException("Este proyecto ya finalizó: solo se puede consultar");
            case "CANCELADO" -> throw new ConflictoException("Este proyecto está cancelado: solo se puede consultar");
            case "EN_GARANTIA" -> throw new ConflictoException("Este proyecto está en garantía: solo admite correcciones");
            default -> {
            }
        }
    }

    /**
     * HU-17: la entrega que llega al editar. Sin fecha en la petición se conserva la actual; si cambia, no puede ser pasada ni
     * quedar a menos de 2 semanas del inicio.
     */
    static LocalDate validarEntregaEditada(String texto, ProyectoFila actual, LocalDate hoy, Acumulador errores) {
        if (texto == null) {
            return actual.fechaEntrega();
        }
        LocalDate entrega = fecha(texto, "fechaEstimadaEntrega", "Elige la fecha de entrega.", errores);
        if (entrega != null && !entrega.equals(actual.fechaEntrega())) {
            if (entrega.isBefore(hoy)) {
                errores.agregar("fechaEstimadaEntrega", "La entrega no puede quedar en una fecha pasada.");
            } else if (entrega.isBefore(actual.fechaInicio().plusDays(14))) {
                errores.agregar("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio");
            }
        }
        return entrega;
    }
}
