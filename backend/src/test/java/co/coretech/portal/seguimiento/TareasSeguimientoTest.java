package co.coretech.portal.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.compartido.aplicacion.AlmacenArchivos;
import co.coretech.portal.compartido.aplicacion.CalendarioHabil;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.seguimiento.aplicacion.ServicioFases;
import co.coretech.portal.seguimiento.aplicacion.TareasSeguimiento;
import co.coretech.portal.seguimiento.datos.SeguimientoJdbc;

/**
 * La tarea diaria del seguimiento (HU-11, HU-13; RN-13, RN-17, RN-31, RNF-09) con un reloj fijo: recordatorio a los 2 días hábiles,
 * aprobación automática vencido el plazo de 3, cierre de la garantía a los 30 días (salvo correcciones abiertas) y borrado de los
 * archivos adjuntos de proyectos cerrados hace más de 12 meses. Cada prueba mira solo su propio proyecto: la base la comparten
 * todas las pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class TareasSeguimientoTest extends BaseSeguimientoPrueba {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

    @Autowired
    SeguimientoJdbc datos;
    @Autowired
    co.coretech.portal.seguimiento.ProyectosParaSeguimiento proyectos;
    @Autowired
    EquipoConsultas equipos;
    @Autowired
    DirectorioUsuarios usuarios;
    @Autowired
    CalendarioHabil calendario;
    @Autowired
    RegistroAuditoria auditoria;
    @Autowired
    SesionActual sesion;
    @Autowired
    ApplicationEventPublisher eventos;
    @Autowired
    AlmacenArchivos almacen;
    @Autowired
    TransactionTemplate transaccion;

    /** La tarea con el reloj fijo en {@code dia}, a las 9:00 de Colombia. */
    private TareasSeguimiento tareas(LocalDate dia) {
        Clock reloj = Clock.fixed(dia.atTime(9, 0).atZone(COLOMBIA).toInstant(), COLOMBIA);
        ServicioFases fases = new ServicioFases(datos, proyectos, equipos, usuarios, calendario, auditoria, sesion, eventos, reloj);
        return new TareasSeguimiento(datos, fases, proyectos, almacen, reloj);
    }

    private int correr(TareasSeguimiento tarea) {
        return transaccion.execute(estado -> tarea.revisar());
    }

    // ------------------------------------------------------------------------------------------ RN-17: fase en revisión

    @Test
    void laFaseEntregadaSeRecuerdaALos2DiasHabilesYSeApruebaSolaVencidoElPlazo() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        entregarFase(a, 1);
        LocalDate plazo = jdbc.queryForObject("SELECT plazo_aprobacion FROM proyecto_fases WHERE id = ?", LocalDate.class, fase1);
        assertThat(plazo).isEqualTo(calendario.sumarDiasHabiles(hoy(), 3));

        // Al primer día hábil todavía no hay nada que hacer
        correr(tareas(calendario.sumarDiasHabiles(hoy(), 1)));
        assertThat(avisos(a.proyecto().clienteId(), "FASE_RECORDATORIO")).isZero();

        // A los 2 días hábiles: un recordatorio, una sola vez
        LocalDate segundoDia = calendario.sumarDiasHabiles(hoy(), 2);
        correr(tareas(segundoDia));
        assertThat(avisos(a.proyecto().clienteId(), "FASE_RECORDATORIO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT plazo_recordado_at IS NOT NULL FROM proyecto_fases WHERE id = ?", Boolean.class, fase1)).isTrue();
        correr(tareas(segundoDia));
        assertThat(avisos(a.proyecto().clienteId(), "FASE_RECORDATORIO")).isEqualTo(1);
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");

        // El último día del plazo todavía puede responder
        correr(tareas(plazo));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_REVISION");

        // Vencido el plazo: se aprueba sola, queda registrada como automática y empieza la siguiente fase
        correr(tareas(plazo.plusDays(1)));
        assertThat(estadoFase(codigo, 1)).isEqualTo("APROBADA");
        assertThat(jdbc.queryForObject("SELECT aprobacion_automatica AND aprobada_por IS NULL AND aprobada_at IS NOT NULL FROM proyecto_fases WHERE id = ?",
                Boolean.class, fase1)).isTrue();
        assertThat(estadoFase(codigo, 2)).isEqualTo("EN_CURSO");
        assertThat(avance(codigo)).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(avisos(a.proyecto().clienteId(), "FASE_APROBADA_AUTO")).isEqualTo(1);
        assertThat(avisos(a.proyecto().equipo().usuario(), "FASE_APROBADA_AUTO")).isEqualTo(1);
        assertThat(auditoriaDe(codigo, "La fase se aprobó sola por falta de respuesta")).isEqualTo(1);
    }

    @Test
    void unAjusteDetieneElReloj_ElPlazoVuelveAEmpezarAlReentregar() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        UUID fase1 = faseId(codigo, 1);
        entregarFase(a, 1);
        mvc.perform(json(post("/api/v1/fases/" + fase1 + "/solicitudes-cambio").cookie(a.cliente()),
                "{\"tipo\":\"AJUSTE\",\"descripcion\":\"" + PEDIDO + "\"}")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT plazo_aprobacion IS NULL FROM proyecto_fases WHERE id = ?", Boolean.class, fase1)).isTrue();

        // Pasado el plazo original la fase no se aprueba sola: está en curso, trabajando el ajuste
        correr(tareas(calendario.sumarDiasHabiles(hoy(), 10)));
        assertThat(estadoFase(codigo, 1)).isEqualTo("EN_CURSO");
        assertThat(avisos(a.proyecto().clienteId(), "FASE_APROBADA_AUTO")).isZero();
    }

    // ------------------------------------------------------------------------------------------ RN-13 y RN-31: garantía

    @Test
    void cumplidosLos30DiasElProyectoSinCorreccionesAbiertasQuedaFinalizado() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        aprobarFase(a, 1);
        entregarFase(a, 2);
        entregarFase(a, 3);
        LocalDate fin = jdbc.queryForObject("SELECT garantia_hasta FROM proyectos WHERE codigo = ?", LocalDate.class, codigo);
        assertThat(fin).isEqualTo(hoy().plusDays(30));

        correr(tareas(fin.minusDays(1)));
        assertThat(estadoProyecto(codigo)).isEqualTo("EN_GARANTIA");
        correr(tareas(fin));
        assertThat(estadoProyecto(codigo)).isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT finalizado_at::date = garantia_hasta FROM proyectos WHERE codigo = ?", Boolean.class, codigo)).isTrue();
        assertThat(avisos(a.proyecto().clienteId(), "PROYECTO_FINALIZADO")).isEqualTo(1);
        assertThat(auditoriaDe(codigo, "El proyecto terminó su garantía")).isEqualTo(1);
    }

    @Test
    void conUnaCorreccionAbiertaLaGarantiaSigueAunqueSeCumplanLos30Dias() throws Exception {
        Aprobado a = aprobado();
        String codigo = a.proyecto().codigo();
        aprobarFase(a, 1);
        entregarFase(a, 2);
        entregarFase(a, 3);
        mvc.perform(json(post("/api/v1/fases/" + faseId(codigo, 3) + "/pasos").cookie(a.po()),
                "{\"origen\":\"Lo pidió el cliente\",\"titulo\":\"Corregir el total\",\"explicacion\":\"El total del carrito no suma el envío cuando hay descuentos.\"}"))
                .andExpect(status().isCreated());
        LocalDate fin = jdbc.queryForObject("SELECT garantia_hasta FROM proyectos WHERE codigo = ?", LocalDate.class, codigo);

        correr(tareas(fin.plusDays(5)));
        assertThat(estadoProyecto(codigo)).isEqualTo("EN_GARANTIA");
        assertThat(avance(codigo)).isEqualByComparingTo(new BigDecimal("100"));
    }

    // ------------------------------------------------------------------------------------------ RNF-09: adjuntos viejos

    @Test
    void losArchivosDeProyectosCerradosHaceMasDe12MesesSeBorranYElRegistroQueda() throws Exception {
        Aprobado viejo = aprobado();
        Aprobado reciente = aprobado();
        UUID pasoViejo = pasos(viejo.proyecto().codigo()).get(0);
        UUID pasoReciente = pasos(reciente.proyecto().codigo()).get(0);
        String archivoViejo = almacen.guardar("adjuntos", PNG, "png");
        String archivoReciente = almacen.guardar("adjuntos", PNG, "png");
        UUID adjuntoViejo = guardarAdjunto(pasoViejo, archivoViejo);
        UUID adjuntoReciente = guardarAdjunto(pasoReciente, archivoReciente);
        // El viejo se canceló hace 13 meses; el otro sigue activo
        jdbc.update("UPDATE proyectos SET estado = 'CANCELADO', motivo_estado = 'Cancelado en la prueba', updated_at = now() - interval '13 months' WHERE codigo = ?",
                viejo.proyecto().codigo());

        correr(tareas(hoy()));

        assertThat(almacen.existe("adjuntos", archivoViejo)).isFalse();
        assertThat(jdbc.queryForObject("SELECT archivo_eliminado_at IS NOT NULL FROM adjuntos_paso WHERE id = ?", Boolean.class, adjuntoViejo)).isTrue();
        assertThat(almacen.existe("adjuntos", archivoReciente)).isTrue();
        assertThat(jdbc.queryForObject("SELECT archivo_eliminado_at IS NULL FROM adjuntos_paso WHERE id = ?", Boolean.class, adjuntoReciente)).isTrue();
        // El registro se conserva
        assertThat(jdbc.queryForObject("SELECT count(*) FROM adjuntos_paso WHERE id = ?", Integer.class, adjuntoViejo)).isEqualTo(1);
    }

    private UUID guardarAdjunto(UUID paso, String archivo) {
        return jdbc.queryForObject("""
                INSERT INTO adjuntos_paso (paso_id, titulo, url, tipo_mime, tamano_bytes) VALUES (?, 'Boceto', ?, 'image/png', 12) RETURNING id
                """, UUID.class, paso, archivo);
    }
}
