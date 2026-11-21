package co.coretech.portal.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/**
 * Plan de trabajo y cotización contra PostgreSQL 15 (HU-28, HU-29, RN-27 a RN-29): el ciclo completo (enviar, pedir ajustes,
 * reenviar, aprobar), el rechazo y la reanudación, y cada excepción de las dos HU con su código HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class PlanTrabajoTest extends BaseProyectosPrueba {

    static final String CONDICIONES = "Incluye 2 rondas de ajustes en cada fase y 30 días de garantía; no incluye hosting.";

    /** Plan de tres fases (la primera la aprueba el cliente) con la cotización dada; {@code null} omite el campo. */
    static String plan(String valor, LocalDate entrega, String condiciones) {
        StringBuilder s = new StringBuilder("{\"fases\":[");
        for (int f = 1; f <= 3; f++) {
            s.append(f > 1 ? "," : "").append("{\"nombre\":\"Fase ").append(f).append("\",\"queEs\":\"Qué es\",\"apruebaCliente\":")
                    .append(f == 1).append(",\"pasos\":[{\"titulo\":\"Paso 1 de la fase ").append(f)
                    .append("\",\"explicacion\":\"Explicación\"},{\"titulo\":\"Paso 2 de la fase ").append(f).append("\"}]}");
        }
        s.append("]");
        if (valor != null) {
            s.append(",\"valorCotizado\":").append(valor);
        }
        if (entrega != null) {
            s.append(",\"fechaEstimadaEntrega\":\"").append(entrega).append("\"");
        }
        if (condiciones != null) {
            s.append(",\"condicionesCotizacion\":\"").append(condiciones).append("\"");
        }
        return s.append("}").toString();
    }

    static String planCompleto() {
        return plan("7800000", hoy().plusDays(60), CONDICIONES);
    }

    private String estadoPlan(String codigo) {
        return jdbc.queryForObject("SELECT estado::text || '/' || estado_plan::text FROM proyectos WHERE codigo = ?", String.class, codigo);
    }

    private void enviado(Proyecto p) throws Exception {
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(sesionDe(p.equipo().email())), planCompleto()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------ Ciclo completo

    @Test
    void elProductOwnerEnviaElClientePideAjustesLoReenviaYElClienteAprueba() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);
        Cookie po = sesionDe(p.equipo().email());
        Cookie cliente = sesionDe(p.clienteEmail());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";

        // El borrador lo ve el equipo con las fases copiadas de la plantilla; el cliente todavía no
        mvc.perform(get(ruta).cookie(po))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("BORRADOR"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.fases", hasSize(3)));
        mvc.perform(get(ruta).cookie(cliente)).andExpect(status().isConflict());

        mvc.perform(json(post(ruta + "/enviar").cookie(po), planCompleto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("ENVIADO"))
                .andExpect(jsonPath("$.porcentajePorFase").value(33.3))
                .andExpect(jsonPath("$.plazoRespuesta").exists())
                .andExpect(jsonPath("$.venceEl").exists());
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/ENVIADO");
        assertThat(jdbc.queryForObject("SELECT plan_vence_el > plan_plazo_respuesta AND plan_enviado_at IS NOT NULL FROM proyectos WHERE codigo = ?",
                Boolean.class, p.codigo())).isTrue();
        assertThat(avisos(p.clienteId(), "PLAN_ENVIADO")).isEqualTo(1);

        // Con el plan enviado, el cliente lo ve y el equipo ya no lo edita
        mvc.perform(get(ruta).cookie(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.valorCotizado").value(7800000));
        mvc.perform(json(put(ruta).cookie(po), planCompleto())).andExpect(status().isConflict());

        // Pide ajustes: comentario corto → 422; completo → el plan vuelve al equipo
        mvc.perform(json(post(ruta + "/ajustes").cookie(cliente), "{\"comentario\":\"Muy corto\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("comentario"));
        mvc.perform(json(post(ruta + "/ajustes").cookie(cliente), "{\"comentario\":\"Falta una fase de capacitación del personal.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoPlan").value("AJUSTES_PEDIDOS"))
                .andExpect(jsonPath("$.respuestaId").exists());
        assertThat(avisos(p.equipo().usuario(), "PLAN_AJUSTES_PEDIDOS")).isEqualTo(1);
        mvc.perform(get(ruta).cookie(po))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.ajustesPedidos.texto").value("Falta una fase de capacitación del personal."));

        // El equipo ajusta (agrega una fase) y reenvía
        String conCuartaFase = planCompleto().replace("]," + "\"valorCotizado\"",
                ",{\"nombre\":\"Capacitación\",\"apruebaCliente\":false,\"pasos\":[{\"titulo\":\"Capacitar al personal\"}]}],\"valorCotizado\"");
        mvc.perform(json(post(ruta + "/enviar").cookie(po), conCuartaFase))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentajePorFase").value(25.0));

        // Aprueba: sin la casilla → 422; con ella, el plan queda fijo y empieza la fase 1
        mvc.perform(json(post(ruta + "/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Confirma que leíste el plan y la cotización."));
        mvc.perform(json(post(ruta + "/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("APROBADO"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.planAprobadoAt").exists());
        assertThat(jdbc.queryForList("SELECT f.estado::text FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id WHERE p.codigo = ? ORDER BY f.orden",
                String.class, p.codigo())).containsExactly("EN_CURSO", "PENDIENTE", "PENDIENTE", "PENDIENTE");
        assertThat(jdbc.queryForList("SELECT respuesta::text FROM respuestas_plan r JOIN proyectos p ON p.id = r.proyecto_id WHERE p.codigo = ? ORDER BY r.created_at",
                String.class, p.codigo())).containsExactly("AJUSTES_PEDIDOS", "APROBADO");
        assertThat(avisos(p.equipo().usuario(), "PLAN_APROBADO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND categoria = 'proyectos'", Integer.class, p.codigo()))
                .isGreaterThanOrEqualTo(4);

        // Un plan aprobado no se aprueba dos veces ni se edita
        mvc.perform(json(post(ruta + "/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este plan no está esperando tu respuesta"));
        mvc.perform(json(put(ruta).cookie(po), planCompleto())).andExpect(status().isConflict());
    }

    @Test
    void elAdministradorTambienEditaYEnviaElPlan() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);

        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(sesionDe(admin)), planCompleto()))
                .andExpect(status().isOk());
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/ENVIADO");
    }

    @Test
    void guardarElBorradorConservaLasFasesYLaCotizacionAMedias() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie po = sesionDe(p.equipo().email());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";

        mvc.perform(json(put(ruta).cookie(po), plan("5000000", null, null).replace("Fase 3", "Entrega final")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlan").value("BORRADOR"))
                .andExpect(jsonPath("$.valorCotizado").value(5000000))
                .andExpect(jsonPath("$.fases[2].nombre").value("Entrega final"));
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/BORRADOR");

        // Sin ninguna fase que apruebe el cliente: 422 con el mensaje de RN-28
        mvc.perform(json(put(ruta).cookie(po), plan(null, null, null).replace("\"apruebaCliente\":true", "\"apruebaCliente\":false")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Marca al menos una fase que apruebe el cliente"));
    }

    // ------------------------------------------------------------------------------------------ Cotización (HU-28)

    @Test
    void elValorFueraDeRangoSeRechazaConUnaSolaMensajeYElPlanNoSeEnvia() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie po = sesionDe(p.equipo().email());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan/enviar";

        for (String valor : new String[] { "999999", "200000001", "5000000.5" }) {
            mvc.perform(json(post(ruta).cookie(po), plan(valor, hoy().plusDays(60), CONDICIONES)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("valorCotizado"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("El valor debe estar entre $1.000.000 y $200.000.000."));
        }
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/BORRADOR");

        // Los dos extremos del rango sí valen
        mvc.perform(json(post(ruta).cookie(po), plan("200000000", hoy().plusDays(60), CONDICIONES))).andExpect(status().isOk());
        Proyecto otro = proyecto(crearUsuario("ADMINISTRADOR"));
        mvc.perform(json(post("/api/v1/proyectos/" + otro.codigo() + "/plan/enviar").cookie(sesionDe(otro.equipo().email())),
                plan("1000000", hoy().plusDays(60), CONDICIONES))).andExpect(status().isOk());
    }

    @Test
    void sinCotizacionNoSeEnviaYLaEntregaDebeSerDosSemanasDespuesDelInicio() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie po = sesionDe(p.equipo().email());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan/enviar";

        mvc.perform(json(post(ruta).cookie(po), plan(null, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[?(@.campo=='valorCotizado')].mensaje").value("Escribe el valor del proyecto."))
                .andExpect(jsonPath("$.errores[?(@.campo=='condicionesCotizacion')].mensaje").value("Escribe qué incluye el valor."))
                .andExpect(jsonPath("$.errores[?(@.campo=='fechaEstimadaEntrega')].mensaje").value("Elige la fecha estimada de entrega."))
                .andExpect(jsonPath("$.errores[?(@.campo=='cotizacion')].mensaje")
                        .value("Completa el valor y lo que incluye antes de enviar el plan"));

        mvc.perform(json(post(ruta).cookie(po), plan("7800000", hoy().plusDays(13), CONDICIONES)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaEstimadaEntrega"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.startsWith("La entrega debe ser al menos 2 semanas después del inicio")));

        mvc.perform(json(post(ruta).cookie(po), plan("7800000", hoy().plusDays(60), "Muy corto")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe al menos 20 caracteres."));
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/BORRADOR");
    }

    // ------------------------------------------------------------------------------------------ Rechazo y reanudación

    @Test
    void elRechazoPausaElProyectoLiberaElCupoYAlReanudarElEquipoLoReenvia() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);
        enviado(p);
        Cookie cliente = sesionDe(p.clienteEmail());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";
        int activosAntes = jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE equipo_id = ? AND estado = 'ACTIVO'", Integer.class, p.equipo().id());

        mvc.perform(json(post(ruta + "/rechazar").cookie(cliente), "{\"motivo\":\"Muy corto\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Cuéntanos por qué la rechazas (mínimo 20 caracteres)"));
        mvc.perform(json(post(ruta + "/rechazar").cookie(cliente), "{\"motivo\":\"El valor supera nuestro presupuesto de este año.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoPlan").value("RECHAZADO"))
                .andExpect(jsonPath("$.estado").value("PAUSADO"));

        assertThat(estadoPlan(p.codigo())).isEqualTo("PAUSADO/RECHAZADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM proyectos WHERE equipo_id = ? AND estado = 'ACTIVO'", Integer.class, p.equipo().id()))
                .isEqualTo(activosAntes - 1);
        assertThat(avisos(p.clienteId(), "PLAN_RECHAZADO_CLIENTE")).isEqualTo(1);
        assertThat(avisos(p.equipo().usuario(), "PLAN_RECHAZADO")).isEqualTo(1);
        assertThat(avisos(jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", java.util.UUID.class, admin), "PLAN_RECHAZADO")).isEqualTo(1);
        mvc.perform(json(post(ruta + "/rechazar").cookie(cliente), "{\"motivo\":\"El valor supera nuestro presupuesto de este año.\"}"))
                .andExpect(status().isConflict());

        // El administrador reanuda con el equipo: el plan vuelve a borrador y el Product Owner lo reenvía
        mvc.perform(json(patch("/api/v1/proyectos/" + p.codigo() + "/estado").cookie(sesionDe(admin)),
                        "{\"estado\":\"ACTIVO\",\"motivo\":\"El cliente retomó la conversación\",\"equipoId\":\"" + p.equipo().id() + "\"}"))
                .andExpect(status().isOk());
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/BORRADOR");
        enviado(p);
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/ENVIADO");
    }

    // ------------------------------------------------------------------------------------------ Recordar al cliente

    @Test
    void alReenviarElPlanSeBorranLasMarcasDeLosAvisosAutomaticos() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        enviado(p);
        jdbc.update("UPDATE proyectos SET plan_recordado_at = now() WHERE codigo = ?", p.codigo());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";
        mvc.perform(json(post(ruta + "/ajustes").cookie(sesionDe(p.clienteEmail())), "{\"comentario\":\"Falta una fase de capacitación del personal.\"}"))
                .andExpect(status().isCreated());

        enviado(p);

        assertThat(jdbc.queryForObject("SELECT plan_recordado_at IS NULL AND plan_cancelacion_avisada_at IS NULL FROM proyectos WHERE codigo = ?",
                Boolean.class, p.codigo())).isTrue();
    }

    // ------------------------------------------------------------------------------------------ Mis proyectos

    @Test
    void misProyectosMuestraACadaUnoSoloLoSuyoYLoQueLeToca() throws Exception {
        String admin = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(admin);
        Proyecto ajeno = proyecto(admin);
        Cookie po = sesionDe(p.equipo().email());
        Cookie cliente = sesionDe(p.clienteEmail());

        // Plan en borrador: al Product Owner le toca prepararlo; el cliente solo lo ve en preparación
        mvc.perform(get("/api/v1/proyectos/propios").cookie(po))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value(p.codigo()))
                .andExpect(jsonPath("$[0].porHacer").value("PREPARAR_PLAN"));
        mvc.perform(get("/api/v1/proyectos/propios").cookie(cliente))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value(p.codigo()))
                .andExpect(jsonPath("$[0].cliente").value("Usuario de Prueba"))
                .andExpect(jsonPath("$[0].porHacer").doesNotExist());

        // Plan enviado: ahora le toca al cliente
        enviado(p);
        mvc.perform(get("/api/v1/proyectos/propios").cookie(cliente)).andExpect(jsonPath("$[0].porHacer").value("REVISAR_PROPUESTA"));
        mvc.perform(get("/api/v1/proyectos/propios").cookie(po)).andExpect(jsonPath("$[0].porHacer").doesNotExist());

        // Con ajustes pedidos vuelve al Product Owner
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/ajustes").cookie(cliente),
                "{\"comentario\":\"Falta una fase de capacitación del personal.\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/proyectos/propios").cookie(po)).andExpect(jsonPath("$[0].porHacer").value("AJUSTAR_PLAN"));

        // Nadie ve el proyecto de otro; el administrador usa su propia lista; sin sesión, 401
        mvc.perform(get("/api/v1/proyectos/propios").cookie(sesionDe(ajeno.clienteEmail())))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].codigo").value(ajeno.codigo()));
        mvc.perform(get("/api/v1/proyectos/propios").cookie(sesionDe(admin))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/proyectos/propios")).andExpect(status().isUnauthorized());
    }

    @Test
    void recordarAlClienteSoloConElPlanEnviado() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie po = sesionDe(p.equipo().email());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan/recordar";

        mvc.perform(post(ruta).cookie(po)).andExpect(status().isConflict());
        enviado(p);
        mvc.perform(post(ruta).cookie(po)).andExpect(status().isNoContent());
        assertThat(avisos(p.clienteId(), "PLAN_RECORDATORIO")).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------ Permisos y recursos ajenos

    @Test
    void elClienteNoEditaNiEnviaElPlan() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie cliente = sesionDe(p.clienteEmail());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";

        mvc.perform(json(put(ruta).cookie(cliente), planCompleto())).andExpect(status().isForbidden());
        mvc.perform(json(post(ruta + "/enviar").cookie(cliente), planCompleto())).andExpect(status().isForbidden());
        mvc.perform(post(ruta + "/recordar").cookie(cliente)).andExpect(status().isForbidden());
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    }

    @Test
    void elProductOwnerDeOtroEquipoRecibe403YQuedaEnLaAuditoria() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie otroPo = sesionDe(equipo().email());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";

        mvc.perform(get(ruta).cookie(otroPo)).andExpect(status().isForbidden());
        mvc.perform(json(put(ruta).cookie(otroPo), planCompleto())).andExpect(status().isForbidden());
        mvc.perform(json(post(ruta + "/enviar").cookie(otroPo), planCompleto()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este proyecto no es de tu equipo"));
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/BORRADOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion LIKE 'Intentó %'", Integer.class, p.codigo()))
                .isEqualTo(3);
    }

    @Test
    void unClienteAjenoNoVeNiResponderElPlanYRecibe404() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        enviado(p);
        Cookie ajeno = sesionDe(crearUsuario("CLIENTE"));
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";

        mvc.perform(get(ruta).cookie(ajeno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Proyecto no encontrado"));
        mvc.perform(json(post(ruta + "/aprobar").cookie(ajeno), "{\"aceptaPlanYCotizacion\":true}")).andExpect(status().isNotFound());
        mvc.perform(json(post(ruta + "/ajustes").cookie(ajeno), "{\"comentario\":\"Quiero cambiar muchas cosas del plan.\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(post(ruta + "/rechazar").cookie(ajeno), "{\"motivo\":\"No me interesa este proyecto ahora.\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/proyectos/P-9999999/plan").cookie(ajeno)).andExpect(status().isNotFound());
        assertThat(estadoPlan(p.codigo())).isEqualTo("ACTIVO/ENVIADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad_id = ? AND accion LIKE 'Intentó %'", Integer.class, p.codigo()))
                .isEqualTo(4);
    }

    @Test
    void responderUnPlanQueNoEstaEnviadoDa409() throws Exception {
        Proyecto p = proyecto(crearUsuario("ADMINISTRADOR"));
        Cookie cliente = sesionDe(p.clienteEmail());
        String ruta = "/api/v1/proyectos/" + p.codigo() + "/plan";

        mvc.perform(json(post(ruta + "/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Este plan no está esperando tu respuesta"));
        mvc.perform(json(post(ruta + "/ajustes").cookie(cliente), "{\"comentario\":\"Quiero cambiar muchas cosas del plan.\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(post(ruta + "/rechazar").cookie(cliente), "{\"motivo\":\"No me interesa este proyecto ahora.\"}"))
                .andExpect(status().isConflict());
    }
}
