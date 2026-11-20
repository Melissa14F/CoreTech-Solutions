package co.coretech.portal.compartido.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.ContenedoresPrueba;

/**
 * El traductor con errores reales de PostgreSQL 15: comprueba que el nombre de la restricción y el
 * contexto del RAISE ("…function fn_…() line N at RAISE") llegan como se espera.
 * Las llaves foráneas se difieren y la transacción se revierte (ver MigracionV3Test).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@Transactional
class TraductorErroresBaseDatosTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TraductorErroresBaseDatos traductor;

    @BeforeEach
    void diferirLlavesForaneas() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
    }

    @Test
    void dosCitasEnLaMismaFranjaDan409ConElMensajeDeLaTabla() {
        OffsetDateTime lunesDiezAm = OffsetDateTime.of(2026, 12, 7, 10, 0, 0, 0,
                ZoneId.of("America/Bogota").getRules().getOffset(java.time.LocalDateTime.of(2026, 12, 7, 10, 0)));
        insertarCita("C-9001", lunesDiezAm);

        Throwable error = catchThrowable(() -> insertarCita("C-9002", lunesDiezAm));

        ErrorTraducido traducido = traductor.traducir(error).orElseThrow();
        assertThat(traducido.estado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(traducido.campo()).isEqualTo("fechaHora");
        assertThat(traducido.mensaje()).isEqualTo("La hora que elegiste ya no está disponible, elige otra");
    }

    @Test
    void quitarUnPermisoFijoDa422PorElDisparador() {
        // V6 · RN-19: "Ver sus proyectos y avances" es fijo para el cliente
        Throwable error = catchThrowable(() -> jdbc.update("""
                DELETE FROM rol_permisos WHERE rol_id = (SELECT id FROM roles WHERE codigo = 'CLIENTE')
                   AND permiso_id = (SELECT id FROM permisos WHERE codigo = 'proyectos.ver_propios')
                """));

        ErrorTraducido traducido = traductor.traducir(error).orElseThrow();
        assertThat(traducido.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(traducido.campo()).isEqualTo("permisos");
        assertThat(traducido.mensaje()).isEqualTo("Este permiso lo exige una regla del negocio");
    }

    @Test
    void darUnPermisoVetadoDa422PorElDisparador() {
        // V6 · RN-07: el administrador no registra avances
        Throwable error = catchThrowable(() -> jdbc.update("""
                INSERT INTO rol_permisos (rol_id, permiso_id)
                SELECT r.id, p.id FROM roles r, permisos p WHERE r.codigo = 'ADMINISTRADOR' AND p.codigo = 'proyectos.registrar_avance'
                """));

        ErrorTraducido traducido = traductor.traducir(error).orElseThrow();
        assertThat(traducido.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(traducido.mensaje()).isEqualTo("Una regla del negocio no permite dar este permiso a este rol");
    }

    @Test
    void borrarUnMensajeDa409PorElDisparador() {
        UUID mensaje = UUID.randomUUID();
        jdbc.update("INSERT INTO mensajes (id, proyecto_id, autor_id, contenido) VALUES (?, gen_random_uuid(), gen_random_uuid(), ?)",
                mensaje, "¿Cuándo empieza la fase 2?");

        Throwable error = catchThrowable(() -> jdbc.update("DELETE FROM mensajes WHERE id = ?", mensaje));

        ErrorTraducido traducido = traductor.traducir(error).orElseThrow();
        assertThat(traducido.estado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(traducido.mensaje()).isEqualTo("Los mensajes no se borran");
    }

    @Test
    void unCheckDeUnFormularioDa422ConSuCampo() {
        Throwable error = catchThrowable(() -> jdbc.update("""
                INSERT INTO citas (codigo, cliente_id, servicio_id, fecha_hora, modalidad, descripcion_necesidad)
                VALUES ('C-9003', gen_random_uuid(), gen_random_uuid(), '2026-12-07 10:00-05', 'VIRTUAL', 'Muy corta')
                """));

        ErrorTraducido traducido = traductor.traducir(error).orElseThrow();
        assertThat(traducido.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(traducido.campo()).isEqualTo("descripcionNecesidad");
    }

    private void insertarCita(String codigo, OffsetDateTime fechaHora) {
        jdbc.update("""
                INSERT INTO citas (codigo, cliente_id, servicio_id, fecha_hora, modalidad, descripcion_necesidad)
                VALUES (?, gen_random_uuid(), gen_random_uuid(), ?, 'VIRTUAL', 'Necesitamos una tienda en línea para vender ropa')
                """, codigo, fechaHora);
    }
}
