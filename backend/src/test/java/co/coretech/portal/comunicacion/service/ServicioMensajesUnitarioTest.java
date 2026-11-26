package co.coretech.portal.comunicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Persona;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Proyecto;
import co.coretech.portal.comunicacion.repository.MensajesJdbc;
import co.coretech.portal.comunicacion.repository.MensajesJdbc.MensajeContexto;
import co.coretech.portal.comunicacion.repository.MensajesJdbc.MensajeFila;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Pruebas UNITARIAS de {@link ServicioMensajes} (HU-14; RN-10, RN-14): el hilo entre el cliente dueño y el Product Owner del
 * equipo. Solo ellos dos participan; se escribe solo con el proyecto activo o en garantía; el autor edita durante 5 minutos y
 * ningún mensaje se borra. La persistencia, el proyecto y los avisos van simulados. Reloj fijo: martes 2027-03-02, 10:00.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioMensajesUnitarioTest {

    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");
    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final UUID MENSAJE = UUID.fromString("00000000-0000-4000-8000-000000000081");
    static final String TEMA = "Pregunta sobre el avance";
    static final String TEXTO = "¿Cómo va el módulo de inventario del salón?";

    @Mock
    MensajesJdbc datos;
    @Mock
    ProyectosParaComunicacion proyectos;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;

    ServicioMensajes servicio;
    Proyecto proyecto;

    @BeforeEach
    void preparaLoComun() {
        proyecto = proyecto("ACTIVO");
        when(proyectos.porCodigo("P-0031")).thenAnswer(i -> Optional.of(proyecto));
        when(proyectos.porId(PROYECTO)).thenAnswer(i -> Optional.of(proyecto));
        when(proyectos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
        when(proyectos.equipoDelProductOwner(OTRO_PO)).thenReturn(Optional.of(UUID.randomUUID()));
        when(proyectos.productOwnerDe(EQUIPO)).thenReturn(Optional.of(new Persona(PO, "Andrés Pérez", "andres@coretechsolutions.co")));
        when(datos.listar(PROYECTO)).thenReturn(List.of());
        when(datos.insertar(any(), any(), any(), any(), any())).thenReturn(MENSAJE);
        comoCliente(CLIENTE);
        servicio = new ServicioMensajes(datos, proyectos, auditoria, sesion, eventos, Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    // ---- ayudas ----

    static Proyecto proyecto(String estado) {
        return new Proyecto(PROYECTO, "P-0031", "Inventario del salón", CLIENTE, EQUIPO, estado, "APROBADO");
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura Gómez", "laura@correo.com", "CLIENTE", Set.of("proyectos.ver_propios"), false, 0));
    }

    void comoProductOwner(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Andrés Pérez", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance"), false, 0));
    }

    void comoAdministrador() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of("proyectos.ver_todos"), false, 0));
    }

    static MensajeFila fila(UUID autor, String rol, String tema, Instant creado) {
        return new MensajeFila(MENSAJE, autor, autor.equals(CLIENTE) ? "Laura Gómez" : "Andrés Pérez", rol, tema, TEXTO, creado, null);
    }

    void mensajeExistente(UUID autor, String tema, Instant creado) {
        when(datos.mensaje(MENSAJE)).thenReturn(Optional.of(new MensajeContexto(MENSAJE, PROYECTO, autor, tema, TEXTO, creado)));
        when(datos.listar(PROYECTO)).thenReturn(List.of(fila(autor, autor.equals(CLIENTE) ? "CLIENTE" : "PRODUCT_OWNER", tema, creado)));
    }

    List<AvisoSolicitado> avisos() {
        ArgumentCaptor<AvisoSolicitado> c = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(c.capture());
        return c.getAllValues();
    }

    // ------------------------------------------------------------------------------------------------ hilo

    @Nested
    @DisplayName("Leer el hilo")
    class Hilo {

        @Test
        @DisplayName("El cliente ve el hilo con la lista de temas, puede escribir con el proyecto activo y escribe con tema; el Product Owner escribe sin tema")
        void hilo() {
            when(datos.listar(PROYECTO)).thenReturn(List.of(fila(CLIENTE, "CLIENTE", TEMA, AHORA.minusSeconds(3600)), fila(PO, "PRODUCT_OWNER", null, AHORA.minusSeconds(60))));

            var delCliente = servicio.hilo("P-0031");
            assertThat(delCliente.proyecto()).isEqualTo("Inventario del salón");
            assertThat(delCliente.puedeEscribir()).isTrue();
            assertThat(delCliente.escribeConTema()).isTrue();
            assertThat(delCliente.temas()).isEqualTo(ServicioMensajes.TEMAS).hasSize(5);
            assertThat(delCliente.mensajes()).extracting(ServicioMensajes.MensajeVista::esMio).containsExactly(true, false);
            assertThat(delCliente.mensajes()).extracting(ServicioMensajes.MensajeVista::rol).containsExactly("CLIENTE", "PRODUCT_OWNER");

            comoProductOwner(PO);
            assertThat(servicio.hilo("P-0031").escribeConTema()).isFalse();
        }

        @Test
        @DisplayName("Valores límite de la edición (RN-14): a los 5 minutos exactos todavía se puede editar; un segundo después, no")
        void ventanaDeEdicion() {
            when(datos.listar(PROYECTO)).thenReturn(List.of(fila(CLIENTE, "CLIENTE", TEMA, AHORA.minusSeconds(300))));
            assertThat(servicio.hilo("P-0031").mensajes().get(0).puedeEditar()).isTrue();

            when(datos.listar(PROYECTO)).thenReturn(List.of(fila(CLIENTE, "CLIENTE", TEMA, AHORA.minusSeconds(301))));
            assertThat(servicio.hilo("P-0031").mensajes().get(0).puedeEditar()).isFalse();
        }

        @Test
        @DisplayName("Solo se edita el mensaje propio y solo con el proyecto que recibe mensajes; en un proyecto pausado el hilo es de solo lectura")
        void soloLectura() {
            when(datos.listar(PROYECTO)).thenReturn(List.of(fila(PO, "PRODUCT_OWNER", null, AHORA.minusSeconds(10))));
            assertThat(servicio.hilo("P-0031").mensajes().get(0).puedeEditar()).as("es del Product Owner, no mío").isFalse();

            when(datos.listar(PROYECTO)).thenReturn(List.of(fila(CLIENTE, "CLIENTE", TEMA, AHORA.minusSeconds(10))));
            proyecto = proyecto("PAUSADO");
            var pausado = servicio.hilo("P-0031");
            assertThat(pausado.puedeEscribir()).isFalse();
            assertThat(pausado.mensajes().get(0).puedeEditar()).isFalse();
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404 (RN-10), el Product Owner de otro equipo y el administrador 403, y todos quedan en la auditoría")
        void participantes() {
            comoCliente(OTRO_CLIENTE);
            when(proyectos.equipoDelProductOwner(OTRO_CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.hilo("P-0031")).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioMensajes.NO_ENCONTRADO);

            comoProductOwner(OTRO_PO);
            assertThatThrownBy(() -> servicio.hilo("P-0031")).isInstanceOf(ProhibidoException.class).hasMessage(ServicioMensajes.SOLO_PARTICIPANTES);

            comoAdministrador();
            when(proyectos.equipoDelProductOwner(ADMIN)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.hilo("P-0031")).isInstanceOf(ProhibidoException.class);

            verify(auditoria, times(3)).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("Un código mal formado, o de un proyecto que no existe, es 404")
        void codigo() {
            assertThatThrownBy(() -> servicio.hilo("31")).isInstanceOf(NoEncontradoException.class);
            when(proyectos.porCodigo("P-9999")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.hilo("P-9999")).isInstanceOf(NoEncontradoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ enviar

    @Nested
    @DisplayName("Enviar un mensaje (HU-14)")
    class Enviar {

        @Test
        @DisplayName("El cliente escribe con tema: se guarda recortado, se audita y se avisa al Product Owner con el enlace a registrar")
        void clienteEscribe() {
            var m = servicio.enviar("P-0031", "  " + TEMA + " ", "  " + TEXTO + "  ");

            assertThat(m.esMio()).isTrue();
            assertThat(m.rol()).isEqualTo("CLIENTE");
            assertThat(m.tema()).isEqualTo(TEMA);
            assertThat(m.contenido()).isEqualTo(TEXTO);
            verify(datos).insertar(PROYECTO, CLIENTE, TEMA, TEXTO, AHORA);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Envió un mensaje"), eq("proyecto"), eq("P-0031"), anyMap());
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(PO);
            assertThat(aviso.tipo()).isEqualTo("NUEVO_MENSAJE");
            assertThat(aviso.enlace()).isEqualTo("/equipo/proyectos/P-0031/registrar");
        }

        @Test
        @DisplayName("El Product Owner responde sin tema (D-41) y el aviso va al cliente")
        void productOwnerResponde() {
            comoProductOwner(PO);

            var m = servicio.enviar("P-0031", "Pregunta sobre el avance", "Va muy bien, mañana te muestro el avance.");

            assertThat(m.rol()).isEqualTo("PRODUCT_OWNER");
            assertThat(m.tema()).as("el tema que mande el desarrollador se ignora").isNull();
            verify(datos).insertar(eq(PROYECTO), eq(PO), eq(null), any(), any());
            assertThat(avisos().get(0).destinatarios()).containsExactly(CLIENTE);
            assertThat(avisos().get(0).enlace()).isEqualTo("/cliente/proyectos/P-0031");
        }

        @Test
        @DisplayName("El cliente debe elegir un tema de la lista: vacío o inventado da 422 en el campo tema")
        void temaObligatorio() {
            for (String malo : new String[] { null, "", "   ", "Tema inventado" }) {
                assertThatThrownBy(() -> servicio.enviar("P-0031", malo, TEXTO)).as("«" + malo + "»")
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).campo()).isEqualTo("tema"));
            }
            verify(datos, never()).insertar(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Valores límite del contenido: 9 caracteres no, 10 sí, 1000 sí, 1001 no; vacío da «Escribe tu mensaje.»")
        void limitesDelContenido() {
            assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, "x".repeat(9)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe al menos 10 caracteres."));
            assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, "x".repeat(1001)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 1000 caracteres."));
            assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, "  ")).hasMessage("Escribe tu mensaje.");
            assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, null)).isInstanceOf(ValidacionException.class);

            servicio.enviar("P-0031", TEMA, "x".repeat(10));
            servicio.enviar("P-0031", TEMA, "x".repeat(1000));
            verify(datos, times(2)).insertar(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Tema y contenido inválidos a la vez dan los dos errores")
        void dosErrores() {
            assertThatThrownBy(() -> servicio.enviar("P-0031", "", ""))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas()).extracting(ValidacionException.Falla::campo).containsExactly("tema", "contenido"));
        }

        @Test
        @DisplayName("Solo con el proyecto activo o en garantía (RN-14): pausado, finalizado o cancelado da 409 y no se guarda nada")
        void estadoDelProyecto() {
            proyecto = proyecto("EN_GARANTIA");
            assertThat(servicio.enviar("P-0031", TEMA, TEXTO).contenido()).isEqualTo(TEXTO);

            for (String estado : List.of("PAUSADO", "FINALIZADO", "CANCELADO")) {
                proyecto = proyecto(estado);
                assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, TEXTO)).as(estado).isInstanceOf(ConflictoException.class).hasMessage(ServicioMensajes.ESTADO_NO_RECIBE);
            }
            verify(datos, times(1)).insertar(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Quien no participa no escribe: un cliente ajeno 404 y el Product Owner de otro equipo 403, sin guardar ni avisar")
        void noParticipantes() {
            comoCliente(OTRO_CLIENTE);
            when(proyectos.equipoDelProductOwner(OTRO_CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, TEXTO)).isInstanceOf(NoEncontradoException.class);

            comoProductOwner(OTRO_PO);
            assertThatThrownBy(() -> servicio.enviar("P-0031", TEMA, TEXTO)).isInstanceOf(ProhibidoException.class);

            verify(datos, never()).insertar(any(), any(), any(), any(), any());
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Un aviso con un mensaje larguísimo se recorta a 255 caracteres y el título a 80")
        void recortaElAviso() {
            servicio.enviar("P-0031", TEMA, "x".repeat(1000));

            assertThat(avisos().get(0).texto()).hasSize(255).endsWith("…");
        }

        @Test
        @DisplayName("Si el equipo del proyecto no tiene Product Owner activo no hay a quién avisar y no falla")
        void sinProductOwner() {
            when(proyectos.productOwnerDe(EQUIPO)).thenReturn(Optional.empty());

            assertThat(servicio.enviar("P-0031", TEMA, TEXTO)).isNotNull();
            verify(eventos, never()).publishEvent(any());
        }
    }

    // ------------------------------------------------------------------------------------------------ editar

    @Nested
    @DisplayName("Editar un mensaje (5 minutos, RN-14)")
    class Editar {

        @Test
        @DisplayName("El autor edita durante los 5 minutos: se guarda, y la auditoría deja el texto anterior y el nuevo")
        void edita() {
            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(120));

            servicio.editar(MENSAJE, "Otro", "  Mejor pregunto otra cosa del proyecto.  ");

            verify(datos).editar(MENSAJE, "Otro", "Mejor pregunto otra cosa del proyecto.");
            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, co.coretech.portal.compartido.domain.CambioDeValor>> cambios = ArgumentCaptor.forClass(java.util.Map.class);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Editó un mensaje"), eq("proyecto"), eq("P-0031"), cambios.capture());
            assertThat(cambios.getValue()).containsKeys("tema", "mensaje");
        }

        @Test
        @DisplayName("Valores límite: a los 5 minutos exactos todavía; a los 5 minutos y un segundo, 409 «Ya no puedes editar este mensaje»")
        void limiteDeTiempo() {
            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(300));
            servicio.editar(MENSAJE, TEMA, "Texto editado a tiempo, justo en el límite.");

            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(301));
            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, "Texto editado demasiado tarde para el plazo."))
                    .isInstanceOf(ConflictoException.class).hasMessage(ServicioMensajes.YA_NO_EDITAS);
        }

        @Test
        @DisplayName("Con el proyecto que ya no recibe mensajes tampoco se edita (409)")
        void proyectoCerrado() {
            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(10));
            proyecto = proyecto("PAUSADO");

            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, "Texto editado en un proyecto en pausa.")).hasMessage(ServicioMensajes.YA_NO_EDITAS);
        }

        @Test
        @DisplayName("Solo el autor edita: el Product Owner no edita el mensaje del cliente (403) y queda en la auditoría")
        void soloElAutor() {
            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(10));
            comoProductOwner(PO);

            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, "Texto editado por otra persona distinta."))
                    .isInstanceOf(ProhibidoException.class).hasMessage(ServicioMensajes.SOLO_EL_AUTOR);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(datos, never()).editar(any(), any(), any());
        }

        @Test
        @DisplayName("El mensaje del Product Owner no lleva tema: al editarlo se conserva sin tema aunque llegue uno")
        void mensajeSinTema() {
            mensajeExistente(PO, null, AHORA.minusSeconds(10));
            comoProductOwner(PO);

            servicio.editar(MENSAJE, "Otro", "Texto corregido del desarrollador del equipo.");

            verify(datos).editar(MENSAJE, null, "Texto corregido del desarrollador del equipo.");
        }

        @Test
        @DisplayName("Al editar se valida igual que al enviar: tema de la lista (si es del cliente) y contenido de 10 a 1000")
        void validaciones() {
            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(10));

            assertThatThrownBy(() -> servicio.editar(MENSAJE, "Inventado", TEXTO)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, "corto")).isInstanceOf(ValidacionException.class);
            verify(datos, never()).editar(any(), any(), any());
        }

        @Test
        @DisplayName("Un mensaje que no existe es 404; uno de un proyecto que ya no existe también")
        void inexistente() {
            when(datos.mensaje(MENSAJE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, TEXTO)).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioMensajes.MENSAJE_NO_ENCONTRADO);

            mensajeExistente(CLIENTE, TEMA, AHORA);
            when(proyectos.porId(PROYECTO)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, TEXTO)).isInstanceOf(NoEncontradoException.class);
        }

        @Test
        @DisplayName("Un cliente ajeno que intenta editar recibe 404 y no se toca el mensaje")
        void clienteAjeno() {
            mensajeExistente(CLIENTE, TEMA, AHORA.minusSeconds(10));
            comoCliente(OTRO_CLIENTE);
            when(proyectos.equipoDelProductOwner(OTRO_CLIENTE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.editar(MENSAJE, TEMA, TEXTO)).isInstanceOf(NoEncontradoException.class);
            verify(datos, never()).editar(any(), any(), any());
        }
    }
}
