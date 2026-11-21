package co.coretech.portal.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc;

/**
 * La excepción «Datos desactualizados» de HU-03 y de las acciones de fase (HU-11, HU-13): si al leer el estado la acción era
 * posible, pero otra pestaña se adelantó y el UPDATE condicionado ya no cambia ninguna fila, la respuesta es 409 «El proyecto
 * cambió, recarga para ver lo último» y no queda nada a medias. Si el estado ya la impedía al leerlo, se conserva su mensaje
 * propio (lo cubren {@code SeguimientoTest} y {@code FasesTest}).
 * <p>
 * La carrera se simula con un espía de {@link SeguimientoJdbc}: la lectura es real y el UPDATE condicionado responde "ninguna fila".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class DatosDesactualizadosTest extends BaseSeguimientoPrueba {

    private static final String RECARGA = "El proyecto cambió, recarga para ver lo último";

    @MockitoSpyBean
    SeguimientoJdbc datos;

    @Test
    void siOtraPestanaSeAdelantaAlMarcarUnPasoSeDiceQueElProyectoCambio() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID paso = pasos(codigo).get(0);

        doReturn(false).when(datos).marcarHecho(any(), any(), any(), any());
        mvc.perform(json(patch("/api/v1/pasos/" + paso).cookie(a.po()), hecho(NOTA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(RECARGA));

        assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, paso)).isEqualTo("PENDIENTE");
        assertThat(auditoriaDe(codigo, "Marcó un paso como hecho")).isZero();
    }

    @Test
    void siLaFaseCambiaAlEntregarlaOCerrarlaSeDiceQueElProyectoCambio() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();

        // Fase 1 (la aprueba el cliente): entregar
        hacerPasos(a, 1);
        doReturn(false).when(datos).entregarParaRevision(any(), any(), any());
        mvc.perform(post("/api/v1/fases/" + faseId(codigo, 1) + "/entregar").cookie(a.po()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(RECARGA));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_CURSO");
        assertThat(auditoriaDe(codigo, "Entregó una fase al cliente")).isZero();

        // Fase 2 (la cierra el equipo): cerrar
        reset(datos);
        aprobarFase(a, 1);
        hacerPasos(a, 2);
        doReturn(false).when(datos).aprobar(any(), any(), any(), anyBoolean());
        mvc.perform(post("/api/v1/fases/" + faseId(codigo, 2) + "/entregar").cookie(a.po()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(RECARGA));
        assertThat(estadoFase(codigo, 2)).isEqualTo("EN_CURSO");
        assertThat(auditoriaDe(codigo, "Cerró una fase")).isZero();
    }

    @Test
    void siLaFaseCambiaAlAprobarlaOPedirCambiosSeDiceQueElProyectoCambio() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        entregarFase(a, 1);

        doReturn(false).when(datos).aprobar(any(), any(), any(), anyBoolean());
        mvc.perform(post("/api/v1/fases/" + fase1 + "/aprobar").cookie(a.cliente()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(RECARGA));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");
        assertThat(auditoriaDe(codigo, "Aprobó una fase")).isZero();

        doReturn(false).when(datos).devolverPorAjuste(any());
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()),
                "{\"tipo\":\"AJUSTE\",\"descripcion\":\"" + PEDIDO + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value(RECARGA));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes_cambio WHERE proyecto_fase_id = ?", Integer.class, fase1)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pasos WHERE proyecto_fase_id = ? AND tipo = 'AJUSTE'", Integer.class, fase1)).isZero();
    }
}
