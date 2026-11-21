package co.coretech.portal.comunicacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import co.coretech.portal.proyectos.BaseProyectosPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Datos y ayudas comunes de las pruebas de comunicación (P20): un proyecto con el plan aprobado, con su cliente y su Product
 * Owner con sesión, y horas válidas para las reuniones (día hábil futuro, de 8 a 11 o de 14 a 17, hora de Colombia).
 */
abstract class BaseComunicacionPrueba extends BaseProyectosPrueba {

    static final String MENSAJE = "¿Cómo va el catálogo de productos? Quisiera verlo esta semana.";
    static final String RAZON = "Quiero revisar con el equipo cómo quedó el flujo de pago antes de seguir.";

    /** Un proyecto con el plan aprobado: la fase 1 en curso. */
    record Aprobado(String admin, Proyecto proyecto, Cookie po, Cookie cliente) {
    }

    Aprobado aprobado() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);
        Cookie po = sesionDe(p.equipo().email());
        Cookie cliente = sesionDe(p.clienteEmail());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(po), planJson())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isOk());
        return new Aprobado(admin, p, po, cliente);
    }

    /** Tres fases de dos pasos; solo la primera la aprueba el cliente. */
    static String planJson() {
        StringBuilder s = new StringBuilder("{\"fases\":[");
        for (int f = 1; f <= 3; f++) {
            s.append(f > 1 ? "," : "").append("{\"nombre\":\"Fase ").append(f).append("\",\"queEs\":\"Qué es\",\"apruebaCliente\":")
                    .append(f == 1).append(",\"pasos\":[{\"titulo\":\"Paso 1 de la fase ").append(f)
                    .append("\",\"explicacion\":\"Explicación\"},{\"titulo\":\"Paso 2 de la fase ").append(f).append("\"}]}");
        }
        LocalDate entrega = hoy().plusDays(60);
        return s.append("],\"valorCotizado\":7800000,\"fechaEstimadaEntrega\":\"").append(entrega)
                .append("\",\"condicionesCotizacion\":\"Incluye 2 rondas de ajustes en cada fase y 30 días de garantía.\"}").toString();
    }

    /** El primer día hábil (lunes a viernes y no festivo) que está a {@code desdeDias} días o más de hoy. */
    LocalDate diaHabil(int desdeDias) {
        LocalDate fecha = hoy().plusDays(desdeDias);
        while (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY || esFestivo(fecha)) {
            fecha = fecha.plusDays(1);
        }
        return fecha;
    }

    private boolean esFestivo(LocalDate fecha) {
        return jdbc.queryForObject("SELECT count(*) FROM dias_no_habiles WHERE fecha = ?", Integer.class, fecha) > 0;
    }

    /** Una hora de inicio con su desplazamiento, por ejemplo {@code 2026-11-24T10:00:00-05:00}. */
    static String hora(LocalDate fecha, int hora) {
        ZonedDateTime z = ZonedDateTime.of(fecha, LocalTime.of(hora, 0), BOGOTA);
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(z);
    }

    static String solicitudJson(String motivo, String razon, String modalidad, String opcion1, String opcion2) {
        return "{\"motivo\":\"" + motivo + "\",\"razon\":\"" + razon + "\",\"modalidad\":\"" + modalidad + "\",\"opcion1\":"
                + (opcion1 == null ? "null" : "\"" + opcion1 + "\"") + ",\"opcion2\":" + (opcion2 == null ? "null" : "\"" + opcion2 + "\"")
                + "}";
    }

    int auditoriaDe(String entidadId, String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = ?", Integer.class, entidadId, accion);
    }
}
