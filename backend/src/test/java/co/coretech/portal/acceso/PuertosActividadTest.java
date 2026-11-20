package co.coretech.portal.acceso;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.ContenedoresPrueba;

/**
 * Las consultas que agenda, proyectos y especialidad dan a acceso para la lista de usuarios (HU-20), contra
 * PostgreSQL 15. La transacción se revierte al terminar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@Transactional
class PuertosActividadTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CitasDeUsuarios citas;

    @Autowired
    ProyectosDeUsuarios proyectos;

    @Autowired
    EquiposDeUsuarios equipos;

    @Test
    void lasConsultasCorrenSobreElEsquemaReal() {
        assertThat(citas.porCliente()).isNotNull();
        assertThat(proyectos.porCliente()).isNotNull();
        assertThat(proyectos.activosPorEquipo()).isNotNull();
        assertThat(equipos.porProductOwner()).isNotNull();
    }

    @Test
    void cuentaLasCitasDeCadaClientePorEstado() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        UUID cliente = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO citas (codigo, cliente_id, servicio_id, fecha_hora, modalidad, descripcion_necesidad, estado)
                VALUES ('C-9801', ?, gen_random_uuid(), '2026-12-07 10:00-05', 'VIRTUAL', 'Necesito una tienda en línea para mi negocio', 'PENDIENTE'),
                       ('C-9802', ?, gen_random_uuid(), '2026-12-07 11:00-05', 'VIRTUAL', 'Necesito una tienda en línea para mi negocio', 'PENDIENTE')
                """, cliente, cliente);

        assertThat(citas.porCliente().get(cliente)).isEqualTo(new CitasDeUsuarios.Conteo(2, 0, 0, 0));
    }
}
