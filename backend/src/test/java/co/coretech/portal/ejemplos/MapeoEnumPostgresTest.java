package co.coretech.portal.ejemplos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.ejemplos.CitaEjemploEnum.EstadoCita;
import co.coretech.portal.ejemplos.CitaEjemploEnum.Modalidad;
import jakarta.persistence.EntityManager;

/**
 * Comprueba la receta para los enums de PostgreSQL ({@code @Enumerated(EnumType.STRING)} +
 * {@code @JdbcType(PostgreSQLEnumJdbcType.class)}, ver {@link CitaEjemploEnum}): guardar, leer y filtrar por el enum
 * (sin el @JdbcType, el filtro falla con "operator does not exist: estado_cita = character varying").
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@Transactional
class MapeoEnumPostgresTest {

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void guardaLeeYFiltraPorUnEnumDePostgres() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED"); // cliente y servicio de prueba no existen; se revierte
        UUID id = UUID.randomUUID();
        em.persist(new CitaEjemploEnum(id, "C-9100", UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-12-07T15:00:00Z"), "Necesitamos una tienda en línea para vender ropa",
                Modalidad.VIRTUAL, EstadoCita.PENDIENTE));
        em.flush();
        em.clear();

        assertThat(jdbc.queryForObject("SELECT modalidad::text FROM citas WHERE id = ?", String.class, id))
                .isEqualTo("VIRTUAL");
        assertThat(em.find(CitaEjemploEnum.class, id).getEstado()).isEqualTo(EstadoCita.PENDIENTE);

        List<CitaEjemploEnum> pendientes = em.createQuery(
                        "SELECT c FROM CitaEjemploEnum c WHERE c.estado = :estado", CitaEjemploEnum.class)
                .setParameter("estado", EstadoCita.PENDIENTE)
                .getResultList();
        assertThat(pendientes).extracting(CitaEjemploEnum::getId).contains(id);
    }
}
