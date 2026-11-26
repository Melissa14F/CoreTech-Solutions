package co.coretech.portal.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;

/**
 * RNF-02 (integridad): "0 operaciones UPDATE o DELETE aceptadas sobre pasos publicados, fases aprobadas, respuestas al
 * plan, cuestionarios de cita y auditoría; 0 ediciones de un mensaje después de 5 minutos". Cada caso intenta el
 * cambio con SQL directo, es decir, saltándose toda la aplicación: lo que lo rechaza son los disparadores de la base.
 * Después de cada intento se comprueba que el dato sigue igual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class InmutabilidadTest extends BaseSeguimientoPrueba {

    @Test
    void unPasoPublicadoNoSeEditaNiSeBorra() throws Exception {
        Aprobado a = aprobado();
        hacerPasos(a, 1);
        UUID paso = pasos(a.proyecto().codigo()).get(0);
        String titulo = jdbc.queryForObject("SELECT titulo FROM pasos WHERE id = ?", String.class, paso);

        assertThatThrownBy(() -> jdbc.update("UPDATE pasos SET titulo = 'Otro título' WHERE id = ?", paso))
                .hasMessageContaining("ya se publicó al cliente");
        assertThatThrownBy(() -> jdbc.update("UPDATE pasos SET nota = 'Otra nota que no debería quedar' WHERE id = ?", paso))
                .hasMessageContaining("ya se publicó al cliente");
        assertThatThrownBy(() -> jdbc.update("UPDATE pasos SET estado = 'PENDIENTE' WHERE id = ?", paso))
                .hasMessageContaining("ya se publicó al cliente");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM pasos WHERE id = ?", paso))
                .hasMessageContaining("ya se publicó al cliente");

        assertThat(jdbc.queryForObject("SELECT titulo FROM pasos WHERE id = ?", String.class, paso)).isEqualTo(titulo);
        assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("HECHO");
    }

    @Test
    void unaFaseAprobadaNoSeEditaNiSeBorra() throws Exception {
        Aprobado a = aprobado();
        aprobarFase(a, 1);
        String codigo = a.proyecto().codigo();
        UUID fase = faseId(codigo, 1);
        assertThat(estadoFase(codigo, 1)).isEqualTo("APROBADA");

        assertThatThrownBy(() -> jdbc.update("UPDATE proyecto_fases SET nombre = 'Fase renombrada' WHERE id = ?", fase))
                .hasMessageContaining("ya fue aprobada");
        assertThatThrownBy(() -> jdbc.update("UPDATE proyecto_fases SET aprueba_cliente = false WHERE id = ?", fase))
                .hasMessageContaining("ya fue aprobada");
        assertThatThrownBy(() -> jdbc.update("UPDATE proyecto_fases SET orden = 9 WHERE id = ?", fase))
                .hasMessageContaining("ya fue aprobada");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM proyecto_fases WHERE id = ?", fase))
                .hasMessageContaining("no se borra");

        assertThat(jdbc.queryForObject("SELECT nombre FROM proyecto_fases WHERE id = ?", String.class, fase)).isEqualTo("Fase 1");
        assertThat(estadoFase(codigo, 1)).isEqualTo("APROBADA");
    }

    @Test
    void lasRespuestasAlPlanNoSeEditanNiSeBorran() throws Exception {
        Aprobado a = aprobado();
        UUID proyecto = jdbc.queryForObject("SELECT id FROM proyectos WHERE codigo = ?", UUID.class, a.proyecto().codigo());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM respuestas_plan WHERE proyecto_id = ?", Integer.class, proyecto))
                .as("el cliente aprobó el plan").isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("UPDATE respuestas_plan SET respuesta = 'RECHAZADO' WHERE proyecto_id = ?", proyecto))
                .hasMessageContaining("registro inmutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM respuestas_plan WHERE proyecto_id = ?", proyecto))
                .hasMessageContaining("registro inmutable");

        assertThat(jdbc.queryForObject("SELECT respuesta::text FROM respuestas_plan WHERE proyecto_id = ?", String.class, proyecto))
                .isEqualTo("APROBADO");
    }

    @Test
    void elCuestionarioDeLaCitaNoSeEditaNiSeBorra() throws Exception {
        Aprobado a = aprobado();
        UUID cita = jdbc.queryForObject("SELECT cita_id FROM proyectos WHERE codigo = ?", UUID.class, a.proyecto().codigo());
        jdbc.update("""
                INSERT INTO cuestionarios_cita (cita_id, resumen, descripcion_cliente, presupuesto, idioma, urgencia, registrado_por)
                VALUES (?, 'Resumen de la reunión con el cliente: quiere un catálogo y un carrito de compras.',
                        'Necesita vender sus productos por internet con pagos y envíos.',
                        (enum_range(NULL::rango_presupuesto))[1], (enum_range(NULL::idioma))[1], (enum_range(NULL::urgencia))[1], ?)
                """, cita, a.proyecto().equipo().usuario());

        assertThatThrownBy(() -> jdbc.update("UPDATE cuestionarios_cita SET resumen = 'cambiado' WHERE cita_id = ?", cita))
                .hasMessageContaining("registro inmutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cuestionarios_cita WHERE cita_id = ?", cita))
                .hasMessageContaining("registro inmutable");

        assertThat(jdbc.queryForObject("SELECT resumen FROM cuestionarios_cita WHERE cita_id = ?", String.class, cita))
                .startsWith("Resumen de la reunión");
    }

    @Test
    void laAuditoriaNoSeEditaNiSeBorra() throws Exception {
        Aprobado a = aprobado();
        Long id = jdbc.queryForObject("SELECT max(id) FROM auditoria WHERE entidad_id = ?", Long.class, a.proyecto().codigo());
        assertThat(id).as("la creación del proyecto quedó en la auditoría").isNotNull();
        String accion = jdbc.queryForObject("SELECT accion FROM auditoria WHERE id = ?", String.class, id);

        assertThatThrownBy(() -> jdbc.update("UPDATE auditoria SET accion = 'Otra acción' WHERE id = ?", id))
                .hasMessageContaining("registro inmutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE auditoria SET usuario_id = NULL WHERE id = ?", id))
                .hasMessageContaining("registro inmutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria WHERE id = ?", id))
                .hasMessageContaining("registro inmutable");
        // Ni el borrado masivo: ninguna fila del proyecto se pierde
        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria WHERE entidad_id = ?", a.proyecto().codigo()))
                .hasMessageContaining("registro inmutable");

        assertThat(jdbc.queryForObject("SELECT accion FROM auditoria WHERE id = ?", String.class, id)).isEqualTo(accion);
    }

    @Test
    void unMensajeNoSeEditaDespuesDeCincoMinutosNiSeBorra() throws Exception {
        Aprobado a = aprobado();
        UUID proyecto = jdbc.queryForObject("SELECT id FROM proyectos WHERE codigo = ?", UUID.class, a.proyecto().codigo());
        UUID viejo = UUID.randomUUID();
        UUID reciente = UUID.randomUUID();
        String texto = "¿Cómo va el catálogo de productos? Quisiera verlo esta semana.";
        jdbc.update("INSERT INTO mensajes (id, proyecto_id, autor_id, tema, contenido, created_at) VALUES (?, ?, ?, 'Avance', ?, now() - interval '6 minutes')",
                viejo, proyecto, a.proyecto().clienteId(), texto);
        jdbc.update("INSERT INTO mensajes (id, proyecto_id, autor_id, tema, contenido) VALUES (?, ?, ?, 'Avance', ?)",
                reciente, proyecto, a.proyecto().clienteId(), texto);

        assertThatThrownBy(() -> jdbc.update("UPDATE mensajes SET contenido = 'Texto cambiado después de seis minutos.' WHERE id = ?", viejo))
                .hasMessageContaining("5 minutos");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM mensajes WHERE id = ?", viejo))
                .hasMessageContaining("no se borran");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM mensajes WHERE id = ?", reciente))
                .hasMessageContaining("no se borran");
        // Dentro de los 5 minutos sí se edita el texto, pero no el autor ni la fecha de envío
        assertThat(jdbc.update("UPDATE mensajes SET contenido = 'Texto corregido a tiempo, dentro de los cinco minutos.' WHERE id = ?", reciente))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE mensajes SET autor_id = ? WHERE id = ?", a.proyecto().equipo().usuario(), reciente))
                .hasMessageContaining("solo se editan el tema y el contenido");

        assertThat(jdbc.queryForObject("SELECT contenido FROM mensajes WHERE id = ?", String.class, viejo)).isEqualTo(texto);
    }
}
