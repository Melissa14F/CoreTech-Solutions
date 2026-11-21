package co.coretech.portal.comunicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import jakarta.servlet.http.Cookie;

/** HU-14 · Enviar mensajes sobre un proyecto (RN-07, RN-14, RNF-02). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class MensajesTest extends BaseComunicacionPrueba {

    private static final String TEMA = "Pregunta sobre el avance";

    private static String cuerpo(String tema, String contenido) {
        return "{\"tema\":" + (tema == null ? "null" : "\"" + tema + "\"") + ",\"contenido\":\"" + contenido + "\"}";
    }

    private static String ruta(Aprobado a) {
        return "/api/v1/proyectos/" + a.proyecto().codigo() + "/mensajes";
    }

    @Nested
    class CaminoFeliz {

        @Test
        void elClienteEscribeConTemaYElProductOwnerResponde() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rol").value("CLIENTE")).andExpect(jsonPath("$.tema").value(TEMA))
                    .andExpect(jsonPath("$.esMio").value(true)).andExpect(jsonPath("$.puedeEditar").value(true));
            assertThat(avisos(a.proyecto().equipo().usuario(), "NUEVO_MENSAJE")).isEqualTo(1);

            // El Product Owner responde en el mismo hilo, sin tema (D-41): si manda uno, no se guarda
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpo(TEMA, "Ya está listo, mañana te lo muestro en la reunión.")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("PRODUCT_OWNER"))
                    .andExpect(jsonPath("$.tema").doesNotExist());
            assertThat(avisos(a.proyecto().clienteId(), "NUEVO_MENSAJE")).isEqualTo(1);

            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(status().isOk()).andExpect(jsonPath("$.puedeEscribir").value(true))
                    .andExpect(jsonPath("$.escribeConTema").value(true)).andExpect(jsonPath("$.temas", hasSize(5)))
                    .andExpect(jsonPath("$.mensajes", hasSize(2))).andExpect(jsonPath("$.mensajes[0].esMio").value(true))
                    .andExpect(jsonPath("$.mensajes[1].esMio").value(false));
            mvc.perform(get(ruta(a)).cookie(a.po())).andExpect(status().isOk()).andExpect(jsonPath("$.escribeConTema").value(false))
                    .andExpect(jsonPath("$.mensajes[1].esMio").value(true));
            assertThat(auditoriaDe(a.proyecto().codigo(), "Envió un mensaje")).isEqualTo(2);
        }
    }

    @Nested
    class Validaciones {

        @Test
        void elClienteDebeElegirUnTemaDeLaLista() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(null, MENSAJE))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("tema"));
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo("Un tema inventado", MENSAJE)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("tema"));
        }

        @Test
        void elMensajeVaDe10A1000Caracteres() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, "Hola"))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errores[0].campo").value("contenido"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe al menos 10 caracteres."));
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, "a".repeat(1001))))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores[0].campo").value("contenido"));
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, "a".repeat(1000)))).andExpect(status().isCreated());
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, "          "))).andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    class Excepciones {

        @Test
        void conElProyectoPausadoElHiloQuedaDeSoloLectura() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andExpect(status().isCreated());
            mvc.perform(json(patch("/api/v1/proyectos/" + a.proyecto().codigo() + "/estado").cookie(sesionDe(a.admin())),
                    "{\"estado\":\"PAUSADO\",\"motivo\":\"El cliente pidió una pausa mientras consigue el presupuesto.\"}"))
                    .andExpect(status().isOk());

            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Este proyecto no recibe mensajes en su estado actual"));
            mvc.perform(json(post(ruta(a)).cookie(a.po()), cuerpo(null, MENSAJE))).andExpect(status().isConflict());
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(status().isOk()).andExpect(jsonPath("$.puedeEscribir").value(false))
                    .andExpect(jsonPath("$.mensajes", hasSize(1))).andExpect(jsonPath("$.mensajes[0].puedeEditar").value(false));
        }

        @Test
        void elAutorEditaDurante5MinutosYElTextoAnteriorQuedaEnLaAuditoria() throws Exception {
            Aprobado a = aprobado();
            String id = mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andReturn().getResponse()
                    .getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

            mvc.perform(json(patch("/api/v1/mensajes/" + id).cookie(a.cliente()),
                    cuerpo("Otro", "Mejor te pregunto por el flujo de pago, que me preocupa más.")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.tema").value("Otro"))
                    .andExpect(jsonPath("$.editadoAt").isNotEmpty())
                    .andExpect(jsonPath("$.contenido").value("Mejor te pregunto por el flujo de pago, que me preocupa más."));
            assertThat(jdbc.queryForObject("SELECT editado_at IS NOT NULL FROM mensajes WHERE id = ?::uuid", Boolean.class, id)).isTrue();
            String cambios = jdbc.queryForObject("SELECT cambios::text FROM auditoria WHERE entidad_id = ? AND accion = 'Editó un mensaje'",
                    String.class, a.proyecto().codigo());
            assertThat(cambios).contains("¿Cómo va el catálogo").contains("Mejor te pregunto");
        }

        @Test
        void despuesDe5MinutosYaNoSeEdita() throws Exception {
            Aprobado a = aprobado();
            String id = mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andReturn().getResponse()
                    .getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            envejecer(id);

            mvc.perform(json(patch("/api/v1/mensajes/" + id).cookie(a.cliente()), cuerpo(TEMA, "Quiero cambiar lo que escribí antes.")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.errores[0].mensaje").value("Ya no puedes editar este mensaje"));
            mvc.perform(get(ruta(a)).cookie(a.cliente())).andExpect(jsonPath("$.mensajes[0].puedeEditar").value(false));
            // La base también lo impide, como última barrera (RNF-02)
            assertThatThrownBy(() -> jdbc.update("UPDATE mensajes SET contenido = 'Cambio directo en la base de datos' WHERE id = ?::uuid", id))
                    .hasMessageContaining("5 minutos");
        }

        @Test
        void nadieEditaElMensajeDeOtraPersona() throws Exception {
            Aprobado a = aprobado();
            String id = mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andReturn().getResponse()
                    .getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

            mvc.perform(json(patch("/api/v1/mensajes/" + id).cookie(a.po()), cuerpo(null, "El equipo cambia lo que dijo el cliente.")))
                    .andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT contenido FROM mensajes WHERE id = ?::uuid", String.class, id)).isEqualTo(MENSAJE);
        }

        @Test
        void losMensajesNoSeBorran() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andExpect(status().isCreated());
            assertThatThrownBy(() -> jdbc.update("DELETE FROM mensajes WHERE proyecto_id = (SELECT id FROM proyectos WHERE codigo = ?)",
                    a.proyecto().codigo())).hasMessageContaining("no se borran");
        }

        @Test
        void soloElClienteDuenoYElProductOwnerDelEquipoParticipan() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(post(ruta(a)).cookie(a.cliente()), cuerpo(TEMA, MENSAJE))).andExpect(status().isCreated());

            // Otro cliente: el proyecto no existe para él (404) y queda en la auditoría
            Cookie otroCliente = sesionDe(crearUsuario("CLIENTE"));
            mvc.perform(get(ruta(a)).cookie(otroCliente)).andExpect(status().isNotFound());
            mvc.perform(json(post(ruta(a)).cookie(otroCliente), cuerpo(TEMA, MENSAJE))).andExpect(status().isNotFound());
            assertThat(auditoriaDe(a.proyecto().codigo(), "Intentó leer los mensajes de un proyecto ajeno")).isEqualTo(1);
            assertThat(auditoriaDe(a.proyecto().codigo(), "Intentó escribir en un proyecto ajeno")).isEqualTo(1);

            // Un Product Owner de otro equipo y el administrador: 403
            Cookie otroPo = sesionDe(equipo().email());
            mvc.perform(get(ruta(a)).cookie(otroPo)).andExpect(status().isForbidden());
            mvc.perform(json(post(ruta(a)).cookie(otroPo), cuerpo(null, MENSAJE))).andExpect(status().isForbidden());
            mvc.perform(get(ruta(a)).cookie(sesionDe(a.admin()))).andExpect(status().isForbidden());

            mvc.perform(get("/api/v1/proyectos/P-9999/mensajes").cookie(otroCliente)).andExpect(status().isNotFound());
            mvc.perform(get(ruta(a))).andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mensajes", Integer.class)).isGreaterThanOrEqualTo(1);
        }

        @Test
        void unMensajeQueNoExisteResponde404() throws Exception {
            Aprobado a = aprobado();
            mvc.perform(json(patch("/api/v1/mensajes/" + UUID.randomUUID()).cookie(a.cliente()), cuerpo(TEMA, MENSAJE)))
                    .andExpect(status().isNotFound());
        }
    }

    /** Pone el mensaje 6 minutos en el pasado. El disparador protege {@code created_at}, así que se suspende solo para esto. */
    private void envejecer(String id) {
        jdbc.execute("ALTER TABLE mensajes DISABLE TRIGGER tg_proteger_mensajes");
        try {
            jdbc.update("UPDATE mensajes SET created_at = now() - interval '6 minutes' WHERE id = ?::uuid", id);
        } finally {
            jdbc.execute("ALTER TABLE mensajes ENABLE TRIGGER tg_proteger_mensajes");
        }
    }
}
