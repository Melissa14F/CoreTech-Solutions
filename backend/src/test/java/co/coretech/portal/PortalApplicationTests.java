package co.coretech.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.compartido.PropiedadesCoretech;

/** Levanta el contexto completo contra PostgreSQL 15 y revisa que Flyway creó el esquema. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class PortalApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PropiedadesCoretech propiedades;

    @Test
    void flywayAplicaLasMigraciones() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
    }

    @Test
    void existenLas32Tablas() {
        Integer tablas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);
        // 27 de V1 + rol_permisos_vetados (V6) + sesiones_cerradas (V7) + correo_sistema (V8) + cuentas_externas (V9)
        // + resumenes_reunion (V14)
        assertThat(tablas).isEqualTo(32);
    }

    @Test
    void generaCodigosVisiblesConCuatroDigitos() {
        String primeraCita = jdbc.queryForObject("SELECT fn_siguiente_codigo_cita()", String.class);
        String segundaCita = jdbc.queryForObject("SELECT fn_siguiente_codigo_cita()", String.class);
        String proyecto = jdbc.queryForObject("SELECT fn_siguiente_codigo_proyecto()", String.class);

        // Otras pruebas (agenda) también piden códigos en esta misma base: se revisa el formato y que sean consecutivos.
        assertThat(primeraCita).matches("C-\\d{4,}");
        assertThat(Long.parseLong(segundaCita.substring(2))).isEqualTo(Long.parseLong(primeraCita.substring(2)) + 1);
        assertThat(proyecto).matches("P-\\d{4,}");
        assertThat(jdbc.queryForObject("SELECT fn_formatear_codigo('C', 12345)", String.class)).isEqualTo("C-12345");
    }

    @Test
    void zonaDelNegocioEsBogota() {
        assertThat(propiedades.zonaNegocio().getId()).isEqualTo("America/Bogota");
    }
}
