package co.coretech.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de V3__explicacion_pasos_y_plan_en_revision.sql (D-23 y D-26).
 * <p>
 * Cada prueba corre en una transacción que se revierte. Las llaves foráneas se difieren para no
 * tener que crear cliente, cita y equipo: como la transacción nunca se confirma, no se revisan.
 * Los CHECK y los disparadores sí se revisan al momento. Después de un error, PostgreSQL aborta la
 * transacción, así que cada prueba termina en el error que comprueba.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@Transactional
class MigracionV3Test {

    @Autowired
    JdbcTemplate jdbc;

    UUID proyecto;
    UUID fase1;
    UUID paso1;

    @BeforeEach
    void creaUnProyectoConPlanEnBorrador() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        proyecto = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO proyectos (id, codigo, nombre, alcance, cita_id, cliente_id, servicio_id, equipo_id,
                                       creado_por, fecha_inicio, fecha_estimada_entrega)
                VALUES (?, 'P-9999', 'Proyecto de prueba', 'Alcance de prueba con más de veinte caracteres',
                        gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        gen_random_uuid(), current_date, current_date + 30)
                """, proyecto);
        fase1 = agregaFase(1, "Diseño", true);
        UUID fase2 = agregaFase(2, "Construcción", false);
        paso1 = agregaPaso(fase1, 1, "PASO", "Bocetos de las pantallas", null);
        agregaPaso(fase2, 1, "PASO", "Programar el catálogo", "Armamos la lista de productos que verán tus clientes.");
    }

    // D-23 · Explicación de un paso

    @Test
    void unPasoDelPlanPuedeQuedarSinExplicacion() {
        String explicacion = jdbc.queryForObject("SELECT explicacion FROM pasos WHERE id = ?", String.class, paso1);
        assertThat(explicacion).isNull();
    }

    @Test
    void laExplicacionDeUnPasoDelPlanTieneMaximo160() {
        assertThatThrownBy(() -> agregaPaso(fase1, 2, "PASO", "Paso largo", "x".repeat(161)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_pasos_explicacion");
    }

    @Test
    void unaCorreccionNecesitaExplicacionDe20a400() {
        agregaPaso(fase1, 2, "CORRECCION", "Corregir el logo", "El logo se veía borroso en celulares.");
        assertThatThrownBy(() -> agregaPaso(fase1, 3, "CORRECCION", "Corregir el menú", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_pasos_explicacion");
    }

    // D-26 · Un plan enviado no se edita

    @Test
    void enBorradorSeEditanFasesYPasos() {
        assertThat(jdbc.update("UPDATE pasos SET titulo = 'Bocetos de todas las pantallas' WHERE id = ?", paso1))
                .isEqualTo(1);
        assertThat(jdbc.update("UPDATE proyecto_fases SET nombre = 'Diseño visual' WHERE id = ?", fase1))
                .isEqualTo(1);
    }

    @Test
    void conElPlanEnviadoNoSeAgreganFases() {
        enviaElPlan();
        assertThatThrownBy(() -> agregaFase(3, "Pruebas", false))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("no se cambian sus fases ni sus pasos");
    }

    @Test
    void conElPlanEnviadoNoSeRenombranPasos() {
        enviaElPlan();
        assertThatThrownBy(() -> jdbc.update("UPDATE pasos SET titulo = 'Otro título' WHERE id = ?", paso1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("no se cambian sus fases ni sus pasos");
    }

    @Test
    void conElPlanEnviadoNoSeBorranFases() {
        enviaElPlan();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM proyecto_fases WHERE id = ?", fase1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("no se cambian sus fases ni sus pasos");
    }

    @Test
    void conElPlanRechazadoNoSeAgreganPasos() {
        enviaElPlan();
        jdbc.update("UPDATE proyectos SET estado_plan = 'RECHAZADO' WHERE id = ?", proyecto);
        assertThatThrownBy(() -> agregaPaso(fase1, 2, "PASO", "Paso nuevo", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("no se cambian sus fases ni sus pasos");
    }

    private UUID agregaFase(int orden, String nombre, boolean apruebaCliente) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO proyecto_fases (id, proyecto_id, orden, nombre, aprueba_cliente) VALUES (?, ?, ?, ?, ?)",
                id, proyecto, orden, nombre, apruebaCliente);
        return id;
    }

    private UUID agregaPaso(UUID fase, int orden, String tipo, String titulo, String explicacion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pasos (id, proyecto_fase_id, orden, tipo, titulo, explicacion, origen)
                VALUES (?, ?, ?, ?::tipo_paso, ?, ?, CASE WHEN ? = 'PASO' THEN NULL ELSE 'La encontramos en pruebas' END)
                """, id, fase, orden, tipo, titulo, explicacion, tipo);
        return id;
    }

    private void enviaElPlan() {
        jdbc.update("""
                UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 5000000,
                       condiciones_cotizacion = 'Incluye diseño y construcción; no incluye hosting.',
                       plan_enviado_at = now(), plan_plazo_respuesta = current_date + 3,
                       plan_vence_el = current_date + 14
                 WHERE id = ?
                """, proyecto);
    }
}
