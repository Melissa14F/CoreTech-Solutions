package co.coretech.portal.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.jayway.jsonpath.JsonPath;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.proyectos.BaseProyectosPrueba;
import jakarta.servlet.http.Cookie;

/**
 * HU-23 · Dashboard del administrador (RN-05, RN-17). La base de pruebas la comparten todas las clases, así que ninguna prueba
 * cuenta valores absolutos: toma las cifras antes, crea lo suyo y comprueba la diferencia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class DashboardTest extends BaseProyectosPrueba {

    private static final String RUTA = "/api/v1/dashboard";
    private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA = new java.util.concurrent.atomic.AtomicInteger();

    /** Un proyecto con el plan aprobado de tres fases (la 1 en curso), su administrador y sus sesiones. */
    record Aprobado(Proyecto proyecto, Cookie admin, Cookie po, Cookie cliente) {
    }

    private String leer(Cookie sesion, String ruta) throws Exception {
        return mvc.perform(get(ruta).cookie(sesion)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Cookie administrador() throws Exception {
        return sesionDe(crearUsuario("ADMINISTRADOR"));
    }

    private static int entero(String json, String ruta) {
        return JsonPath.read(json, ruta);
    }

    /** El único valor de un camino con filtro (JSONPath devuelve una lista aunque haya un solo elemento). */
    private static int unico(String json, String ruta) {
        java.util.List<Integer> valores = JsonPath.read(json, ruta);
        assertThat(valores).hasSize(1);
        return valores.get(0);
    }

    private Aprobado aprobado() throws Exception {
        String adminCorreo = crearUsuario("ADMINISTRADOR");
        Proyecto p = proyecto(adminCorreo);
        Cookie po = sesionDe(p.equipo().email());
        Cookie cliente = sesionDe(p.clienteEmail());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(po), planJson())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/aprobar").cookie(cliente), "{\"aceptaPlanYCotizacion\":true}"))
                .andExpect(status().isOk());
        return new Aprobado(p, sesionDe(adminCorreo), po, cliente);
    }

    private static String planJson() {
        StringBuilder s = new StringBuilder("{\"fases\":[");
        for (int f = 1; f <= 3; f++) {
            s.append(f > 1 ? "," : "").append("{\"nombre\":\"Fase ").append(f).append("\",\"queEs\":\"Qué es\",\"apruebaCliente\":").append(f == 1)
                    .append(",\"pasos\":[{\"titulo\":\"Paso 1 de la fase ").append(f).append("\",\"explicacion\":\"Explicación\"},{\"titulo\":\"Paso 2 de la fase ")
                    .append(f).append("\"}]}");
        }
        return s.append("],\"valorCotizado\":7800000,\"fechaEstimadaEntrega\":\"").append(hoy().plusDays(60))
                .append("\",\"condicionesCotizacion\":\"Incluye 2 rondas de ajustes en cada fase y 30 días de garantía.\"}").toString();
    }

    /** Una cita futura (dentro de 10 a 200 días) en la hora en punto; {@code equipo} puede ser nulo. */
    private String citaFutura(UUID cliente, UUID equipo, String estado, int diasDeCreada) {
        return citaFutura(cliente, equipo, estado, diasDeCreada, 10);
    }

    /** Con {@code enDias} pequeño la cita queda entre las más próximas: las alertas de citas muestran solo las 10 primeras por fecha. */
    private String citaFutura(UUID cliente, UUID equipo, String estado, int diasDeCreada, int enDias) {
        UUID id = UUID.randomUUID();
        // Una hora más cada vez: el índice de franja única (ux_cita_franja) no admite dos citas vivas a la misma hora
        Instant fecha = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(enDias, ChronoUnit.DAYS).plus(SECUENCIA.getAndIncrement(), ChronoUnit.HOURS);
        UUID servicio = servicioConPlantilla(2);
        jdbc.update("""
                INSERT INTO citas (id, codigo, cliente_id, servicio_id, equipo_id, fecha_hora, modalidad, descripcion_necesidad, estado, created_at, enlace_reunion, confirmada_at)
                VALUES (?, fn_siguiente_codigo_cita(), ?, ?, ?, ?, 'VIRTUAL', 'Necesito un sistema para mi empresa y su operación.', ?::estado_cita, ?, 'https://meet.google.com/abc-defg-hij', ?)
                """, id, cliente, servicio, equipo, Timestamp.from(fecha), estado, Timestamp.from(Instant.now().minus(diasDeCreada, ChronoUnit.DAYS)), estado.equals("PENDIENTE") ? null : Timestamp.from(Instant.now()));
        return jdbc.queryForObject("SELECT codigo FROM citas WHERE id = ?", String.class, id);
    }

    // =========================================================================================== Acceso y forma

    @Nested
    class Acceso {

        @Test
        void soloElAdministradorLoVe() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(get(RUTA).cookie(a.admin())).andExpect(status().isOk());
            mvc.perform(get(RUTA).cookie(a.cliente())).andExpect(status().isForbidden());
            mvc.perform(get(RUTA).cookie(a.po())).andExpect(status().isForbidden());
            mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        }

        @Test
        void unPeriodoInvalidoEsUnError422_yLosTresPeriodosResponden() throws Exception {
            Cookie admin = administrador();
            mvc.perform(get(RUTA + "?periodo=siglo").cookie(admin)).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("periodo"));
            for (String periodo : new String[] { "semana", "mes", "trimestre", "MES" }) {
                mvc.perform(get(RUTA + "?periodo=" + periodo).cookie(admin)).andExpect(status().isOk());
            }
            mvc.perform(get(RUTA).cookie(admin)).andExpect(jsonPath("$.periodo").value("mes"));
        }

        @Test
        void trae_todosLosBloquesDelPrototipo() throws Exception {
            Aprobado a = aprobado();
            String json = leer(a.admin(), RUTA);
            assertThat(JsonPath.<Object>read(json, "$.indicadores.citas.etiqueta")).isNotNull();
            assertThat(JsonPath.<java.util.List<?>>read(json, "$.citasPorMes")).hasSize(6);
            assertThat(JsonPath.<Boolean>read(json, "$.citasPorMes[5].parcial")).isTrue();
            assertThat(JsonPath.<Boolean>read(json, "$.citasPorMes[0].parcial")).isFalse();
            assertThat(JsonPath.<Object>read(json, "$.conteos.proyectosActivos")).isNotNull();
            assertThat(JsonPath.<Object>read(json, "$.citasPorEstado.total")).isNotNull();
            assertThat(JsonPath.<java.util.List<?>>read(json, "$.actividad").size()).isLessThanOrEqualTo(8);
            assertThat(JsonPath.<Boolean>read(json, "$.sinDatos")).isFalse();
            assertThat(JsonPath.<String>read(json, "$.corte")).isEqualTo(hoy().toString());
        }
    }

    // =========================================================================================== Cifras

    @Nested
    class Indicadores {

        @Test
        void unProyectoNuevoSumaUnActivo_ocupaUnCupoDeSuEquipo_yAparecePorFase() throws Exception {
            Cookie admin = administrador();
            String antes = leer(admin, RUTA);
            Aprobado a = aprobado();
            String despues = leer(a.admin(), RUTA);

            assertThat(entero(despues, "$.conteos.proyectosActivos")).isEqualTo(entero(antes, "$.conteos.proyectosActivos") + 1);
            assertThat(entero(despues, "$.indicadores.proyectos.activos")).isEqualTo(entero(antes, "$.indicadores.proyectos.activos") + 1);
            assertThat(entero(despues, "$.indicadores.proyectos.capacidad")).isEqualTo(entero(antes, "$.indicadores.proyectos.capacidad") + 3);
            // Su equipo aparece con 1 de 3 y 2 cupos libres (RN-05)
            String equipo = a.proyecto().equipo().id().toString();
            assertThat(unico(despues, "$.carga[?(@.id=='" + equipo + "')].activos")).isEqualTo(1);
            assertThat(unico(despues, "$.carga[?(@.id=='" + equipo + "')].maximo")).isEqualTo(3);
            assertThat(unico(despues, "$.carga[?(@.id=='" + equipo + "')].libres")).isEqualTo(2);
            // Con el plan aprobado va en su fase actual, no en «Plan por aprobar»
            int enFase1Antes = antes.contains("\"fase\":\"Fase 1\"") ? JsonPath.<java.util.List<Integer>>read(antes, "$.proyectosPorFase[?(@.fase=='Fase 1')].proyectos").stream().mapToInt(i -> i).sum() : 0;
            int enFase1Despues = JsonPath.<java.util.List<Integer>>read(despues, "$.proyectosPorFase[?(@.fase=='Fase 1')].proyectos").stream().mapToInt(i -> i).sum();
            assertThat(enFase1Despues).isEqualTo(enFase1Antes + 1);
        }

        @Test
        void unPlanEnviadoCuentaComoPorAprobarYAlAprobarloSeVaDeEsaLista() throws Exception {
            Cookie admin = administrador();
            String admCorreo = crearUsuario("ADMINISTRADOR");
            Proyecto p = proyecto(admCorreo);
            Cookie po = sesionDe(p.equipo().email());
            String base = leer(admin, RUTA);

            mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/enviar").cookie(po), planJson())).andExpect(status().isOk());
            String enviado = leer(admin, RUTA);
            assertThat(entero(enviado, "$.conteos.planesPorAprobar")).isEqualTo(entero(base, "$.conteos.planesPorAprobar") + 1);
            assertThat(entero(enviado, "$.indicadores.proyectos.planesPorAprobar")).isEqualTo(entero(base, "$.indicadores.proyectos.planesPorAprobar") + 1);

            mvc.perform(json(post("/api/v1/proyectos/" + p.codigo() + "/plan/aprobar").cookie(sesionDe(p.clienteEmail())), "{\"aceptaPlanYCotizacion\":true}"))
                    .andExpect(status().isOk());
            assertThat(entero(leer(admin, RUTA), "$.conteos.planesPorAprobar")).isEqualTo(entero(base, "$.conteos.planesPorAprobar"));
        }

        @Test
        void unaCitaNuevaSumaAlPeriodoYSeVaDelMesSiEsAntigua() throws Exception {
            Cookie admin = administrador();
            Aprobado a = aprobado();
            String antes = leer(admin, RUTA);
            for (String periodo : new String[] { "semana", "mes", "trimestre" }) {
                String b = leer(admin, RUTA + "?periodo=" + periodo);
                citaFutura(a.proyecto().clienteId(), null, "PENDIENTE", 0);
                String d = leer(admin, RUTA + "?periodo=" + periodo);
                assertThat(entero(d, "$.indicadores.citas.total")).as(periodo).isEqualTo(entero(b, "$.indicadores.citas.total") + 1);
                assertThat(entero(d, "$.citasPorEstado.pendientes")).as(periodo).isEqualTo(entero(b, "$.citasPorEstado.pendientes") + 1);
            }
            // Una cita agendada hace 100 días queda fuera del mes pero cuenta en las barras de los 6 meses
            String mesAntes = leer(admin, RUTA + "?periodo=mes");
            citaFutura(a.proyecto().clienteId(), null, "CONFIRMADA", 100);
            String mesDespues = leer(admin, RUTA + "?periodo=mes");
            assertThat(entero(mesDespues, "$.indicadores.citas.total")).isEqualTo(entero(mesAntes, "$.indicadores.citas.total"));
            int total6Antes = JsonPath.<java.util.List<Integer>>read(mesAntes, "$.citasPorMes[*].citas").stream().mapToInt(i -> i).sum();
            int total6Despues = JsonPath.<java.util.List<Integer>>read(mesDespues, "$.citasPorMes[*].citas").stream().mapToInt(i -> i).sum();
            assertThat(total6Despues).isEqualTo(total6Antes + 1);
            assertThat(entero(mesDespues, "$.indicadores.conversion.citas")).isEqualTo(entero(mesAntes, "$.indicadores.conversion.citas") + 1);
            assertThat(entero(antes, "$.indicadores.citas.total")).isGreaterThanOrEqualTo(0);
        }

        @Test
        void elServicioMasSolicitadoSaleEnElRanking() throws Exception {
            Cookie admin = administrador();
            Aprobado a = aprobado();
            for (int i = 0; i < 3; i++) {
                String codigo = citaFutura(a.proyecto().clienteId(), null, "PENDIENTE", 0);
                assertThat(codigo).startsWith("C-");
            }
            String json = leer(admin, RUTA);
            assertThat(JsonPath.<java.util.List<?>>read(json, "$.servicios").size()).isBetween(1, 5);
            assertThat(JsonPath.<Integer>read(json, "$.servicios[0].citas")).isGreaterThanOrEqualTo(1);
        }
    }

    // =========================================================================================== Alertas

    @Nested
    class Alertas {

        private String alerta(String json, String id, String campo) {
            java.util.List<String> valores = JsonPath.read(json, "$.alertas[?(@.id=='" + id + "')]." + campo);
            return valores.isEmpty() ? null : valores.get(0);
        }

        @Test
        void unProyectoFueraDePlazoEsCriticoYVaPrimero() throws Exception {
            Aprobado a = aprobado();
            jdbc.update("UPDATE proyectos SET fecha_inicio = ?, fecha_estimada_entrega = ? WHERE codigo = ?", hoy().minusDays(90), hoy().minusDays(5),
                    a.proyecto().codigo());
            String json = leer(a.admin(), RUTA);
            String id = "riesgo-" + a.proyecto().codigo();
            assertThat(alerta(json, id, "nivel")).isEqualTo("CRITICA");
            assertThat(alerta(json, id, "titulo")).isEqualTo(a.proyecto().codigo() + " está fuera de plazo");
            assertThat(alerta(json, id, "enlace")).isEqualTo("/admin/proyectos");
            assertThat(JsonPath.<String>read(json, "$.alertas[0].nivel")).isEqualTo("CRITICA");
            // Del más grave al menos grave: nunca una CRITICA después de una ADVERTENCIA o INFO
            java.util.List<String> niveles = JsonPath.read(json, "$.alertas[*].nivel");
            assertThat(niveles).isSortedAccordingTo(java.util.Comparator.comparingInt(n -> n.equals("CRITICA") ? 0 : n.equals("ADVERTENCIA") ? 1 : 2));
        }

        @Test
        void unCambioDePlanPorAutorizarApareceConSuAccionYSeVaAlAutorizarlo() throws Exception {
            Aprobado a = aprobado();
            String base = leer(a.admin(), RUTA);
            mvc.perform(json(post("/api/v1/proyectos/" + a.proyecto().codigo() + "/cambios-plan").cookie(a.po()), "{\"tipo\":\"CAMBIAR_FECHA\",\"motivo\":\""
                    + "El cliente necesita más tiempo para preparar su lanzamiento.\",\"nuevaEntrega\":\"" + hoy().plusDays(75) + "\",\"valorAdicional\":0}"))
                    .andExpect(status().isCreated());
            String json = leer(a.admin(), RUTA);
            String id = "cambio-" + a.proyecto().codigo();
            assertThat(alerta(json, id, "nivel")).isEqualTo("ADVERTENCIA");
            assertThat(alerta(json, id, "accion")).isEqualTo("Autorizar");
            assertThat(alerta(json, id, "titulo")).isEqualTo(a.proyecto().codigo() + " tiene un cambio de plan por autorizar");
            assertThat(entero(json, "$.conteos.cambiosPorAutorizar")).isEqualTo(entero(base, "$.conteos.cambiosPorAutorizar") + 1);

            String cambio = jdbc.queryForObject("SELECT id::text FROM cambios_plan WHERE estado = 'PROPUESTO' ORDER BY created_at DESC LIMIT 1", String.class);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/cambios-plan/" + cambio + "/autorizar").cookie(a.admin()))
                    .andExpect(status().isOk());
            assertThat(alerta(leer(a.admin(), RUTA), id, "titulo")).isNull();
        }

        @Test
        void unaFaseEnRevisionQueVencioSuPlazoSeAvisa() throws Exception {
            Aprobado a = aprobado();
            String base = leer(a.admin(), RUTA);
            jdbc.update("UPDATE proyecto_fases SET estado = 'EN_REVISION'::estado_fase, entregada_at = now() - interval '6 days', plazo_aprobacion = ? "
                    + "WHERE proyecto_id = (SELECT id FROM proyectos WHERE codigo = ?) AND orden = 1", hoy().minusDays(2), a.proyecto().codigo());
            String json = leer(a.admin(), RUTA);
            assertThat(entero(json, "$.conteos.fasesPorAprobar")).isEqualTo(entero(base, "$.conteos.fasesPorAprobar") + 1);
            assertThat(entero(json, "$.conteos.fasesVencidas")).isEqualTo(entero(base, "$.conteos.fasesVencidas") + 1);
            assertThat(alerta(json, "fase-" + a.proyecto().codigo() + "-Fase 1", "nivel")).isEqualTo("ADVERTENCIA");
        }

        @Test
        void unaCitaSinEquipoEsInfo_yUnaSinConfirmarHaceDiasEsAdvertencia() throws Exception {
            Aprobado a = aprobado();
            String base = leer(a.admin(), RUTA);
            String sinEquipo = citaFutura(a.proyecto().clienteId(), null, "PENDIENTE", 0, 1);
            String sinConfirmar = citaFutura(a.proyecto().clienteId(), a.proyecto().equipo().id(), "PENDIENTE", 10, 1);
            String json = leer(a.admin(), RUTA);

            assertThat(alerta(json, "sinequipo-" + sinEquipo, "nivel")).isEqualTo("INFO");
            assertThat(alerta(json, "sinequipo-" + sinEquipo, "accion")).isEqualTo("Asignar");
            assertThat(alerta(json, "sinequipo-" + sinEquipo, "titulo")).isEqualTo(sinEquipo + " no tiene equipo asignado");
            assertThat(alerta(json, "cita-" + sinConfirmar, "nivel")).isEqualTo("ADVERTENCIA");
            assertThat(alerta(json, "cita-" + sinConfirmar, "titulo")).startsWith(sinConfirmar + " sin confirmar hace ").endsWith("días hábiles");
            assertThat(entero(json, "$.conteos.citasSinAsignar")).isEqualTo(entero(base, "$.conteos.citasSinAsignar") + 1);
            // Una cita confirmada no es una alerta
            String confirmada = citaFutura(a.proyecto().clienteId(), a.proyecto().equipo().id(), "CONFIRMADA", 10);
            assertThat(alerta(leer(a.admin(), RUTA), "cita-" + confirmada, "titulo")).isNull();
        }
    }
}
