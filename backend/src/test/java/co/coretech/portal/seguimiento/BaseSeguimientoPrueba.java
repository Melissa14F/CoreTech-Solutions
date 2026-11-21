package co.coretech.portal.seguimiento;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.coretech.portal.proyectos.BaseProyectosPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Datos y ayudas comunes de las pruebas de seguimiento (P18, P19): un proyecto con el plan aprobado de tres fases de dos pasos
 * (solo la primera la aprueba el cliente) y atajos para marcar pasos, entregar fases y leer sus estados en la base.
 */
abstract class BaseSeguimientoPrueba extends BaseProyectosPrueba {

    static final String NOTA = "Quedó listo y ya puedes verlo funcionando en tu cuenta de pruebas.";
    static final String PEDIDO = "Quiero que el botón de pagar sea más grande y de otro color para verlo mejor.";

    /** Un proyecto con el plan aprobado: la fase 1 en curso (la aprueba el cliente) y sus dos pasos pendientes. */
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

    /** Los pasos del proyecto en orden: fase 1 paso 1, fase 1 paso 2, fase 2 paso 1… */
    List<UUID> pasos(String codigo) {
        return jdbc.queryForList("""
                SELECT s.id FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id JOIN proyectos p ON p.id = f.proyecto_id
                 WHERE p.codigo = ? ORDER BY f.orden, s.orden
                """, UUID.class, codigo);
    }

    static String hecho(String nota) {
        return "{\"estado\":\"HECHO\",\"nota\":\"" + nota + "\"}";
    }

    int auditoriaDe(String codigo, String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion = ?", Integer.class, codigo, accion);
    }

    UUID faseId(String codigo, int orden) {
        return jdbc.queryForObject("""
                SELECT f.id FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id WHERE p.codigo = ? AND f.orden = ?
                """, UUID.class, codigo, orden);
    }

    String estadoFase(String codigo, int orden) {
        return jdbc.queryForObject("""
                SELECT f.estado::text FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id WHERE p.codigo = ? AND f.orden = ?
                """, String.class, codigo, orden);
    }

    BigDecimal avance(String codigo) {
        return jdbc.queryForObject("SELECT avance_porcentaje FROM proyectos WHERE codigo = ?", BigDecimal.class, codigo);
    }

    String estadoProyecto(String codigo) {
        return jdbc.queryForObject("SELECT estado::text FROM proyectos WHERE codigo = ?", String.class, codigo);
    }

    /** Marca como hechos todos los pasos pendientes de la fase {@code orden} (incluidos los de ajustes y correcciones). */
    void hacerPasos(Aprobado a, int orden) throws Exception {
        List<UUID> pendientes = jdbc.queryForList("""
                SELECT s.id FROM pasos s WHERE s.proyecto_fase_id = ? AND s.estado IN ('PENDIENTE', 'EN_CURSO') ORDER BY s.orden
                """, UUID.class, faseId(a.proyecto().codigo(), orden));
        for (UUID paso : pendientes) {
            mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho(NOTA))).andExpect(status().isOk());
        }
    }

    /** Hace los pasos de la fase y la entrega (o la cierra, si la cierra el equipo). */
    void entregarFase(Aprobado a, int orden) throws Exception {
        hacerPasos(a, orden);
        mvc.perform(post("/api/v1/fases/" + faseId(a.proyecto().codigo(), orden) + "/entregar").cookie(a.po())).andExpect(status().isOk());
    }

    /** Lleva la fase 1 hasta aprobada por el cliente. */
    void aprobarFase(Aprobado a, int orden) throws Exception {
        entregarFase(a, orden);
        mvc.perform(post("/api/v1/fases/" + faseId(a.proyecto().codigo(), orden) + "/aprobar").cookie(a.cliente())).andExpect(status().isOk());
    }
}
