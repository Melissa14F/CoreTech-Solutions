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
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Fase;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Persona;
import co.coretech.portal.comunicacion.ProyectosParaComunicacion.Proyecto;
import co.coretech.portal.comunicacion.repository.SolicitudesReunionJdbc;
import co.coretech.portal.comunicacion.repository.SolicitudesReunionJdbc.ResumenFila;
import co.coretech.portal.comunicacion.repository.SolicitudesReunionJdbc.SolicitudFila;
import co.coretech.portal.compartido.service.CalendarioHabil;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Pruebas UNITARIAS de {@link ServicioReuniones} (HU-15, HU-16; RN-01, RN-14, RN-15; D-42): solicitar una reunión de seguimiento
 * con una o dos horas en hora de Colombia, programarla, rechazarla, cancelarla y cerrarla con su resumen. La persistencia, el
 * calendario, el proyecto y los avisos van simulados. Reloj fijo: martes 2027-03-02, 10:00 hora de Colombia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioReunionesUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final UUID FASE = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    static final UUID SOLICITUD = UUID.fromString("00000000-0000-4000-8000-0000000000a5");
    static final String RAZON = "Quiero revisar el avance del módulo de inventario.";
    static final String MOTIVO = "Revisar el avance";
    static final String ENLACE = "https://meet.google.com/abc-defg-hij";
    static final Instant OPCION1 = bogota("2027-03-03T08:00:00");
    static final Instant OPCION2 = bogota("2027-03-04T14:00:00");

    @Mock
    SolicitudesReunionJdbc datos;
    @Mock
    ProyectosParaComunicacion proyectos;
    @Mock
    CalendarioHabil calendario;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;

    ServicioReuniones servicio;
    Proyecto proyecto;

    static Instant bogota(String local) {
        return OffsetDateTime.parse(local + "-05:00").toInstant();
    }

    @BeforeEach
    void preparaLoComun() {
        proyecto = proyecto("ACTIVO", "APROBADO");
        when(proyectos.porCodigo("P-0031")).thenAnswer(i -> Optional.of(proyecto));
        when(proyectos.porId(PROYECTO)).thenAnswer(i -> Optional.of(proyecto));
        when(proyectos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
        when(proyectos.equipoDelProductOwner(OTRO_PO)).thenReturn(Optional.of(UUID.randomUUID()));
        when(proyectos.productOwnerDe(EQUIPO)).thenReturn(Optional.of(new Persona(PO, "Andrés Pérez", "andres@coretechsolutions.co")));
        when(proyectos.faseEnCurso(PROYECTO)).thenReturn(Optional.of(new Fase(FASE, 2, "Construcción")));
        when(proyectos.horarioOcupado(any(), any(), any())).thenReturn(false);
        when(calendario.esDiaHabil(any())).thenReturn(true);
        when(datos.deProyecto(PROYECTO)).thenReturn(List.of());
        when(datos.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SOLICITUD);
        when(datos.porId(SOLICITUD)).thenAnswer(i -> Optional.of(solicitud("PENDIENTE", "VIRTUAL", null, null)));
        when(datos.programar(any(), any(), any(), any())).thenReturn(true);
        when(datos.rechazar(any(), any(), any())).thenReturn(true);
        when(datos.cancelar(any(), any(), any())).thenReturn(true);
        when(datos.marcarAtendida(any())).thenReturn(true);
        comoCliente(CLIENTE);
        servicio = new ServicioReuniones(datos, proyectos, calendario, auditoria, sesion, eventos, Clock.fixed(AHORA, COLOMBIA));
    }

    // ---- ayudas ----

    static Proyecto proyecto(String estado, String estadoPlan) {
        return new Proyecto(PROYECTO, "P-0031", "Inventario del salón", CLIENTE, EQUIPO, estado, estadoPlan);
    }

    static SolicitudFila solicitud(String estado, String modalidad, Instant fechaProgramada, ResumenFila resumen) {
        return new SolicitudFila(SOLICITUD, "S-0007", PROYECTO, "P-0031", "Inventario del salón", CLIENTE, "Laura Gómez", EQUIPO, "Andrés Pérez", FASE, "Construcción",
                MOTIVO, RAZON, modalidad, OPCION1, OPCION2, estado, fechaProgramada, null, null, AHORA.minusSeconds(3600), resumen);
    }

    void con(SolicitudFila fila) {
        when(datos.porId(SOLICITUD)).thenReturn(Optional.of(fila));
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura Gómez", "laura@correo.com", "CLIENTE", Set.of("proyectos.ver_propios"), false, 0));
    }

    void comoProductOwner(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Andrés Pérez", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance", "reuniones.programar"), false, 0));
    }

    void comoAdministrador() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of("proyectos.ver_todos"), false, 0));
    }

    List<AvisoSolicitado> avisos() {
        ArgumentCaptor<AvisoSolicitado> c = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(c.capture());
        return c.getAllValues();
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    // ------------------------------------------------------------------------------------------------ solicitar

    @Nested
    @DisplayName("Solicitar una reunión (HU-15)")
    class Solicitar {

        @Test
        @DisplayName("El cliente dueño pide la reunión con dos horas: queda PENDIENTE en la fase en curso, se audita y se avisa al Product Owner")
        void solicita() {
            var v = servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, OPCION2);

            assertThat(v.estado()).isEqualTo("PENDIENTE");
            verify(proyectos).bloquear(PROYECTO);
            verify(datos).insertar(eq(PROYECTO), eq(CLIENTE), eq(FASE), eq(MOTIVO), eq(RAZON), eq("VIRTUAL"), eq(OPCION1), eq(OPCION2), eq(AHORA));
            verify(auditoria).registrar(eq(CategoriaAuditoria.SOLICITUDES), eq("Solicitó una reunión"), eq("solicitud"), eq("S-0007"), anyMap());
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(PO);
            assertThat(aviso.tipo()).isEqualTo("REUNION_SOLICITADA");
        }

        @Test
        @DisplayName("La segunda hora es opcional")
        void unaSolaHora() {
            servicio.solicitar("P-0031", MOTIVO, RAZON, "PRESENCIAL", OPCION1, null);

            verify(datos).insertar(any(), any(), any(), any(), any(), any(), eq(OPCION1), eq(null), any());
        }

        @Test
        @DisplayName("Solo el cliente dueño: uno ajeno recibe 404 (RN-10) y queda en la auditoría")
        void clienteAjeno() {
            comoCliente(OTRO_CLIENTE);

            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, null)).isInstanceOf(NoEncontradoException.class);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SOLICITUDES), any(), eq("solicitud"), eq("P-0031"), anyMap());
            verify(datos, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Solo con el proyecto activo (409) y el plan aprobado (409 «Podrás pedir reuniones cuando apruebes el plan») (RN-14)")
        void estadoYPlan() {
            for (String estado : List.of("PAUSADO", "EN_GARANTIA", "FINALIZADO", "CANCELADO")) {
                proyecto = proyecto(estado, "APROBADO");
                assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, null)).as(estado).hasMessage(ServicioReuniones.ESTADO_NO_ADMITE);
            }
            proyecto = proyecto("ACTIVO", "ENVIADO");
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, null)).hasMessage(ServicioReuniones.PLAN_SIN_APROBAR);
        }

        @Test
        @DisplayName("Una sola solicitud pendiente por proyecto (RN-15): con una en espera, 409")
        void unaPendiente() {
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PENDIENTE", "VIRTUAL", null, null)));

            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, null)).hasMessage(ServicioReuniones.YA_HAY_PENDIENTE);
            verify(datos, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Una reunión programada, rechazada o atendida no impide pedir otra")
        void soloLaPendienteBloquea() {
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PROGRAMADA", "VIRTUAL", OPCION1, null), solicitud("RECHAZADA", "VIRTUAL", null, null)));

            assertThat(servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, null)).isNotNull();
        }

        @Test
        @DisplayName("Sin datos: 422 con un error por campo (motivo, razón, modalidad y primera opción)")
        void sinDatos() {
            assertThatThrownBy(() -> servicio.solicitar("P-0031", null, null, null, null, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("motivo", "razon", "modalidad", "opcion1"));
        }

        @Test
        @DisplayName("Valores límite de la razón: 19 caracteres no, 20 sí, 500 sí, 501 no; un motivo fuera de la lista no")
        void limitesDeLaRazon() {
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, "x".repeat(19), "VIRTUAL", OPCION1, null)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, "x".repeat(501), "VIRTUAL", OPCION1, null)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.solicitar("P-0031", "Motivo inventado", RAZON, "VIRTUAL", OPCION1, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("motivo"));

            servicio.solicitar("P-0031", MOTIVO, "x".repeat(20), "VIRTUAL", OPCION1, null);
            servicio.solicitar("P-0031", MOTIVO, "x".repeat(500), "VIRTUAL", OPCION1, null);
            verify(datos, times(2)).insertar(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("RN-01: la hora debe empezar en punto entre las 8 y las 11 o entre las 14 y las 17 (hora de Colombia): 7:00, 12:00, 13:00, 18:00 y 8:30 no; 8:00, 11:00, 14:00 y 17:00 sí")
        void franjasDelHorario() {
            for (String mala : List.of("2027-03-03T07:00:00", "2027-03-03T12:00:00", "2027-03-03T13:00:00", "2027-03-03T18:00:00", "2027-03-03T08:30:00", "2027-03-03T08:00:30")) {
                assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", bogota(mala), null)).as(mala)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("opcion1"));
            }
            for (String buena : List.of("2027-03-03T08:00:00", "2027-03-03T11:00:00", "2027-03-03T14:00:00", "2027-03-03T17:00:00")) {
                assertThat(servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", bogota(buena), null)).as(buena).isNotNull();
            }
        }

        @Test
        @DisplayName("La hora se mide en Colombia, no en UTC: 13:00 UTC es las 8:00 a. m. en Bogotá y sirve; 08:00 UTC son las 3:00 a. m. y no")
        void usaLaZonaDeColombia() {
            assertThat(servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", Instant.parse("2027-03-03T13:00:00Z"), null)).isNotNull();
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", Instant.parse("2027-03-03T08:00:00Z"), null)).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("No se puede pedir una hora pasada (ni la de este mismo instante) ni un día que no es hábil (fin de semana o festivo)")
        void pasadoYNoHabil() {
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", bogota("2027-03-02T08:00:00"), null)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", AHORA, null)).isInstanceOf(ValidacionException.class);

            when(calendario.esDiaHabil(LocalDate.of(2027, 3, 6))).thenReturn(false);
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", bogota("2027-03-06T09:00:00"), null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).startsWith("Elige un día hábil"));
        }

        @Test
        @DisplayName("La segunda opción debe ser válida y distinta de la primera")
        void segundaOpcion() {
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, OPCION1))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Las dos opciones deben ser distintas"));
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, bogota("2027-03-04T12:00:00")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("opcion2"));
        }

        @Test
        @DisplayName("La modalidad es PRESENCIAL o VIRTUAL; sin una fase en curso no hay a qué asociarla (409)")
        void modalidadYFase() {
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "TELEFONICA", OPCION1, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("modalidad"));

            when(proyectos.faseEnCurso(PROYECTO)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.solicitar("P-0031", MOTIVO, RAZON, "VIRTUAL", OPCION1, null)).isInstanceOf(ConflictoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ programar

    @Nested
    @DisplayName("Programar una reunión (HU-16)")
    class Programar {

        @BeforeEach
        void comoElProductOwner() {
            comoProductOwner(PO);
            con(solicitud("PENDIENTE", "VIRTUAL", null, null));
        }

        @Test
        @DisplayName("El Product Owner elige la primera hora y pone el enlace: queda PROGRAMADA y el cliente recibe el aviso con «por videollamada»")
        void programaVirtual() {
            servicio.programar(SOLICITUD, 1, "  " + ENLACE + "  ");

            verify(datos).programar(SOLICITUD, OPCION1, ENLACE, PO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SOLICITUDES), eq("Programó una reunión"), eq("solicitud"), eq("S-0007"), anyMap());
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(CLIENTE);
            assertThat(aviso.tipo()).isEqualTo("REUNION_PROGRAMADA");
            assertThat(aviso.texto()).contains("por videollamada");
            assertThat(avisos()).as("el Product Owner lo hizo él mismo: no se le avisa a sí mismo").hasSize(1);
        }

        @Test
        @DisplayName("Una reunión presencial no lleva enlace (se guarda nulo aunque llegue uno) y el aviso dice «en la oficina»")
        void programaPresencial() {
            con(solicitud("PENDIENTE", "PRESENCIAL", null, null));

            servicio.programar(SOLICITUD, 2, ENLACE);

            verify(datos).programar(SOLICITUD, OPCION2, null, PO);
            assertThat(avisos().get(0).texto()).contains("en la oficina de CoreTech");
        }

        @Test
        @DisplayName("Si lo programa el administrador, el Product Owner también se entera")
        void administradorProgramaYAvisaAlEquipo() {
            comoAdministrador();

            servicio.programar(SOLICITUD, 1, ENLACE);

            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsExactly("REUNION_PROGRAMADA", "REUNION_PROGRAMADA");
            assertThat(avisos().get(1).destinatarios()).containsExactly(PO);
        }

        @Test
        @DisplayName("La opción debe ser 1, o 2 solo si el cliente propuso una segunda hora; si no, 422 en el campo horario")
        void opcionInvalida() {
            con(solicitudSinOpcion2());
            for (Integer mala : new Integer[] { null, 0, 3, 2 }) {
                assertThatThrownBy(() -> servicio.programar(SOLICITUD, mala, ENLACE)).as("opción " + mala)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("horario"));
            }
        }

        SolicitudFila solicitudSinOpcion2() {
            SolicitudFila base = solicitud("PENDIENTE", "VIRTUAL", null, null);
            return new SolicitudFila(base.id(), base.codigo(), base.proyectoId(), base.proyectoCodigo(), base.proyectoNombre(), base.clienteId(), base.cliente(), base.equipoId(),
                    base.responsable(), base.faseId(), base.fase(), base.motivo(), base.razon(), base.modalidad(), OPCION1, null, "PENDIENTE", null, null, null, base.enviadaAt(), null);
        }

        @Test
        @DisplayName("La reunión virtual exige un enlace que empiece por https:// o http:// (máximo 255 caracteres)")
        void enlaceDeLaReunionVirtual() {
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Pega el enlace: la reunión es virtual."));
            for (String malo : new String[] { "meet.google.com/abc", "ftp://sitio.com/sala", "https://sinpunto", "https://" + "a".repeat(250) + ".com" }) {
                assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, malo)).as(malo)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("El enlace debe empezar por https://"));
            }
            verify(datos, never()).programar(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Una hora que ya pasó, o que el equipo ya tiene ocupada, da 409 «Ese horario ya no está disponible»")
        void horarioNoDisponible() {
            when(proyectos.horarioOcupado(EQUIPO, OPCION1, SOLICITUD)).thenReturn(true);
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).hasMessage(ServicioReuniones.HORARIO_NO_DISPONIBLE);

            SolicitudFila pasada = new SolicitudFila(SOLICITUD, "S-0007", PROYECTO, "P-0031", "x", CLIENTE, "Laura", EQUIPO, "Andrés", FASE, "F", MOTIVO, RAZON, "VIRTUAL",
                    AHORA.minusSeconds(60), null, "PENDIENTE", null, null, null, AHORA, null);
            con(pasada);
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).hasMessage(ServicioReuniones.HORARIO_NO_DISPONIBLE);
            verify(datos, never()).programar(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Una solicitud ya respondida da 409; si otra pestaña se adelantó (el UPDATE no toca filas), 409 «El proyecto cambió»")
        void estadoYCarrera() {
            con(solicitud("PROGRAMADA", "VIRTUAL", OPCION1, null));
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).hasMessage(ServicioReuniones.YA_RESPONDIDA);

            con(solicitud("PENDIENTE", "VIRTUAL", null, null));
            when(datos.programar(any(), any(), any(), any())).thenReturn(false);
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).hasMessage(ServicioReuniones.DATOS_DESACTUALIZADOS);
        }

        @Test
        @DisplayName("El Product Owner de otro equipo y el cliente reciben 403 y quedan en la auditoría; una solicitud inexistente es 404")
        void permisos() {
            comoProductOwner(OTRO_PO);
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).isInstanceOf(ProhibidoException.class).hasMessage(ServicioReuniones.SIN_PERMISO);
            comoCliente(CLIENTE);
            when(proyectos.equipoDelProductOwner(CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).isInstanceOf(ProhibidoException.class);
            verify(auditoria, times(2)).registrar(eq(CategoriaAuditoria.SOLICITUDES), any(), eq("solicitud"), eq("S-0007"), anyMap());

            when(datos.porId(SOLICITUD)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.programar(SOLICITUD, 1, ENLACE)).isInstanceOf(NoEncontradoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ rechazar y cancelar

    @Nested
    @DisplayName("Rechazar y cancelar")
    class RechazarYCancelar {

        @Test
        @DisplayName("Rechazar una pendiente con su motivo: queda RECHAZADA, el cliente ve el motivo y puede pedir otra (RN-15)")
        void rechaza() {
            comoProductOwner(PO);
            con(solicitud("PENDIENTE", "VIRTUAL", null, null));

            servicio.rechazar(SOLICITUD, "  Esa semana el equipo está en entregas.  ");

            verify(datos).rechazar(SOLICITUD, "Esa semana el equipo está en entregas.", PO);
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(CLIENTE);
            assertThat(aviso.texto()).isEqualTo("Esa semana el equipo está en entregas.");
        }

        @Test
        @DisplayName("Valores límite del motivo: 14 caracteres no, 15 sí, 300 sí, 301 no; vacío no")
        void limitesDelMotivo() {
            comoProductOwner(PO);
            con(solicitud("PENDIENTE", "VIRTUAL", null, null));

            assertThatThrownBy(() -> servicio.rechazar(SOLICITUD, "x".repeat(14))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.rechazar(SOLICITUD, "x".repeat(301))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.rechazar(SOLICITUD, null)).isInstanceOf(ValidacionException.class);
            servicio.rechazar(SOLICITUD, "x".repeat(15));
            servicio.rechazar(SOLICITUD, "x".repeat(300));
        }

        @Test
        @DisplayName("Solo se rechaza una pendiente (409) y solo el equipo o el administrador (403)")
        void estadoYPermisos() {
            comoProductOwner(PO);
            con(solicitud("PROGRAMADA", "VIRTUAL", OPCION1, null));
            assertThatThrownBy(() -> servicio.rechazar(SOLICITUD, "x".repeat(20))).hasMessage(ServicioReuniones.YA_RESPONDIDA);

            con(solicitud("PENDIENTE", "VIRTUAL", null, null));
            comoProductOwner(OTRO_PO);
            assertThatThrownBy(() -> servicio.rechazar(SOLICITUD, "x".repeat(20))).isInstanceOf(ProhibidoException.class);
        }

        @Test
        @DisplayName("Solo el administrador cancela, y solo una reunión PROGRAMADA; el Product Owner recibe 403 y queda en la auditoría")
        void cancela() {
            con(solicitud("PROGRAMADA", "VIRTUAL", OPCION1, null));
            comoProductOwner(PO);
            assertThatThrownBy(() -> servicio.cancelar(SOLICITUD, "x".repeat(20))).isInstanceOf(ProhibidoException.class).hasMessage(ServicioReuniones.SIN_PERMISO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SOLICITUDES), any(), eq("solicitud"), eq("S-0007"), anyMap());

            comoAdministrador();
            servicio.cancelar(SOLICITUD, "Se cruzó con otra reunión importante.");
            verify(datos).cancelar(SOLICITUD, "Se cruzó con otra reunión importante.", ADMIN);
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).contains("REUNION_CANCELADA");

            con(solicitud("PENDIENTE", "VIRTUAL", null, null));
            assertThatThrownBy(() -> servicio.cancelar(SOLICITUD, "x".repeat(20))).hasMessage(ServicioReuniones.NO_SE_PUEDE_CANCELAR);
        }
    }

    // ------------------------------------------------------------------------------------------------ cerrar

    @Nested
    @DisplayName("Cerrar con el resumen (D-42)")
    class Cerrar {

        static final String RESUMEN = "Revisamos el avance del inventario y acordamos los ajustes pendientes.";
        static final String PIDIO = "Quiere un reporte semanal de ventas.";

        @BeforeEach
        void reunionOcurrida() {
            comoProductOwner(PO);
            con(solicitud("PROGRAMADA", "VIRTUAL", AHORA.minusSeconds(3600), null));
        }

        @Test
        @DisplayName("Después de la hora, el Product Owner llena el resumen: queda ATENDIDA, se guarda el resumen y se avisa al cliente")
        void cierra() {
            servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "DENTRO_DEL_PLAN");

            verify(datos).marcarAtendida(SOLICITUD);
            verify(datos).insertarResumen(SOLICITUD, RESUMEN, PIDIO, "DENTRO_DEL_PLAN", PO, AHORA);
            assertThat(avisos().get(0).tipo()).isEqualTo("REUNION_ATENDIDA");
        }

        @Test
        @DisplayName("Valor límite: en el instante exacto de la reunión ya se puede cerrar; un segundo antes, 409 «La reunión aún no ha ocurrido»")
        void limiteDeLaHora() {
            con(solicitud("PROGRAMADA", "VIRTUAL", AHORA, null));
            servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO");

            con(solicitud("PROGRAMADA", "VIRTUAL", AHORA.plusSeconds(1), null));
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO")).hasMessage(ServicioReuniones.AUN_NO_OCURRIO);
        }

        @Test
        @DisplayName("El resumen no se edita: una reunión que ya tiene resumen, o que no está PROGRAMADA, da 409")
        void yaNoSeCierra() {
            con(solicitud("PROGRAMADA", "VIRTUAL", AHORA.minusSeconds(3600), new ResumenFila("Ya registrado antes.", "Algo.", "NINGUNO", AHORA)));
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO")).hasMessage(ServicioReuniones.YA_NO_SE_CIERRA);

            con(solicitud("PENDIENTE", "VIRTUAL", null, null));
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO")).hasMessage(ServicioReuniones.YA_NO_SE_CIERRA);

            con(solicitud("PROGRAMADA", "VIRTUAL", AHORA.minusSeconds(3600), null));
            when(datos.marcarAtendida(any())).thenReturn(false);
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO")).hasMessage(ServicioReuniones.YA_NO_SE_CIERRA);
            verify(datos, never()).insertarResumen(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Valores límite: resumen 29 no, 30 sí, 1000 sí, 1001 no; «qué pidió» 19 no, 20 sí, 600 sí, 601 no; el siguiente paso debe ser uno de la lista")
        void limites() {
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, "x".repeat(29), PIDIO, "NINGUNO"))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("resumen"));
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, "x".repeat(1001), PIDIO, "NINGUNO")).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, "x".repeat(19), "NINGUNO"))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("quePidio"));
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, "x".repeat(601), "NINGUNO")).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "OTRO"))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("siguientePaso"));
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, null, null, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("resumen", "quePidio", "siguientePaso"));

            servicio.cerrar(SOLICITUD, "x".repeat(30), "x".repeat(20), "CAMBIO_DE_PLAN");
            servicio.cerrar(SOLICITUD, "x".repeat(1000), "x".repeat(600), "NINGUNO");
        }

        @Test
        @DisplayName("Solo el Product Owner del equipo llena el resumen: el administrador recibe 403; el de otro equipo, 403")
        void permisos() {
            comoAdministrador();
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO")).isInstanceOf(ProhibidoException.class).hasMessageContaining("Product Owner");

            comoProductOwner(OTRO_PO);
            assertThatThrownBy(() -> servicio.cerrar(SOLICITUD, RESUMEN, PIDIO, "NINGUNO")).isInstanceOf(ProhibidoException.class);

            verify(datos, never()).marcarAtendida(any());
        }
    }

    // ------------------------------------------------------------------------------------------------ consultar

    @Nested
    @DisplayName("Consultar las solicitudes")
    class Consultar {

        @Test
        @DisplayName("El cliente puede pedir una reunión nueva solo con el proyecto activo, el plan aprobado y ninguna solicitud pendiente")
        void puedeSolicitar() {
            assertThat(servicio.deProyecto("P-0031").puedeSolicitar()).isTrue();
            assertThat(servicio.deProyecto("P-0031").motivos()).isEqualTo(ServicioReuniones.MOTIVOS);

            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PENDIENTE", "VIRTUAL", null, null)));
            assertThat(servicio.deProyecto("P-0031").puedeSolicitar()).isFalse();

            when(datos.deProyecto(PROYECTO)).thenReturn(List.of());
            proyecto = proyecto("ACTIVO", "ENVIADO");
            assertThat(servicio.deProyecto("P-0031").puedeSolicitar()).isFalse();

            proyecto = proyecto("ACTIVO", "APROBADO");
            comoProductOwner(PO);
            assertThat(servicio.deProyecto("P-0031").puedeSolicitar()).as("el Product Owner no pide reuniones").isFalse();
        }

        @Test
        @DisplayName("Un Product Owner de otro equipo recibe 403 y un cliente ajeno 404")
        void ajenos() {
            comoProductOwner(OTRO_PO);
            assertThatThrownBy(() -> servicio.deProyecto("P-0031")).isInstanceOf(ProhibidoException.class);

            comoCliente(OTRO_CLIENTE);
            when(proyectos.equipoDelProductOwner(OTRO_CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.deProyecto("P-0031")).isInstanceOf(NoEncontradoException.class);
        }

        @Test
        @DisplayName("puedeCerrar: solo el Product Owner, con la reunión PROGRAMADA y la hora ya cumplida; nunca el administrador ni el cliente")
        void puedeCerrar() {
            comoProductOwner(PO);
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PROGRAMADA", "VIRTUAL", AHORA.minusSeconds(60), null)));
            assertThat(servicio.deProyecto("P-0031").solicitudes().get(0).puedeCerrar()).isTrue();

            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PROGRAMADA", "VIRTUAL", AHORA.plusSeconds(3600), null)));
            assertThat(servicio.deProyecto("P-0031").solicitudes().get(0).puedeCerrar()).isFalse();

            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PROGRAMADA", "VIRTUAL", AHORA.minusSeconds(60), null)));
            comoAdministrador();
            assertThat(servicio.deProyecto("P-0031").solicitudes().get(0).puedeCerrar()).isFalse();
            comoCliente(CLIENTE);
            assertThat(servicio.deProyecto("P-0031").solicitudes().get(0).puedeCerrar()).isFalse();
        }

        @Test
        @DisplayName("listar: el administrador ve todas, el Product Owner las de su equipo y quien no lidera un equipo, una lista vacía")
        void listar() {
            when(datos.listar(null)).thenReturn(List.of(solicitud("PENDIENTE", "VIRTUAL", null, null)));
            when(datos.listar(EQUIPO)).thenReturn(List.of());
            comoAdministrador();
            assertThat(servicio.listar()).hasSize(1);
            verify(datos).listar(null);

            comoProductOwner(PO);
            assertThat(servicio.listar()).isEmpty();
            verify(datos).listar(EQUIPO);

            when(proyectos.equipoDelProductOwner(CLIENTE)).thenReturn(Optional.empty());
            comoCliente(CLIENTE);
            assertThat(servicio.listar()).isEmpty();
        }

        @Test
        @DisplayName("Las opciones que propuso el cliente salen en orden; si no hay segunda, solo una; sin responsable, vacío")
        void opciones() {
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(solicitud("PENDIENTE", "VIRTUAL", null, null)));
            assertThat(servicio.deProyecto("P-0031").solicitudes().get(0).opciones()).containsExactly(OPCION1, OPCION2);
        }
    }
}
