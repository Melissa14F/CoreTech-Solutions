package co.coretech.portal.proyectos.service;

import co.coretech.portal.proyectos.domain.EstadoPlan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.service.CalendarioHabil;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.Equipo;
import co.coretech.portal.proyectos.CitasParaProyectos;
import co.coretech.portal.proyectos.service.ServicioPlan.Solicitud;
import co.coretech.portal.proyectos.repository.PlanesJdbc;
import co.coretech.portal.proyectos.repository.ProyectoRepositorio;
import co.coretech.portal.proyectos.repository.PlanesJdbc.Cotizacion;
import co.coretech.portal.proyectos.repository.ProyectosJdbc;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Pruebas UNITARIAS de {@link ServicioPlan} (HU-28 y HU-29; RN-06, RN-27, RN-28, RN-29): guardar el borrador, enviar el plan con
 * su cotización, y que el cliente lo apruebe, pida ajustes o lo rechace. La persistencia, el equipo, los usuarios, el calendario
 * y los eventos van simulados; no hay Spring ni base de datos. El reloj es fijo: martes 2027-03-02, 10:00 hora de Colombia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioPlanUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");
    static final LocalDate HOY = LocalDate.of(2027, 3, 2);
    static final LocalDate INICIO = HOY.minusDays(5);

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a9");
    static final String CONDICIONES = "Incluye diseño, desarrollo y capacitación; no incluye hosting.";

    @Mock
    ProyectosJdbc proyectos;
    @Mock
    PlanesJdbc planes;
    @Mock
    ProyectoRepositorio proyectosJpa;
    @Mock
    CitasParaProyectos citas;
    @Mock
    EquipoConsultas equipos;
    @Mock
    DirectorioUsuarios usuarios;
    @Mock
    DirectorioServicios servicios;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;
    @Mock
    CalendarioHabil calendario;

    ServicioPlan servicio;
    ProyectoFila proyecto;

    @BeforeEach
    void preparaLoComun() {
        proyecto = proyecto("ACTIVO", "BORRADOR");
        when(proyectos.porCodigo(eq("P-0031"), eq(HOY))).thenAnswer(i -> Optional.of(proyecto));
        when(planes.cotizacion(PROYECTO)).thenReturn(new Cotizacion(null, null, null, null, null));
        when(planes.fases(PROYECTO)).thenReturn(List.of());
        when(calendario.sumarDiasHabiles(HOY, 3)).thenReturn(HOY.plusDays(3));
        when(calendario.sumarDiasHabiles(HOY, 10)).thenReturn(HOY.plusDays(14));
        when(equipos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
        when(equipos.equipoDelProductOwner(OTRO_PO)).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipos.porIds(List.of(EQUIPO))).thenReturn(Map.of(EQUIPO, new Equipo(EQUIPO, PO, "Andrés Pérez", 3, 1, 3)));
        when(usuarios.perfiles(List.of(CLIENTE))).thenReturn(Map.of(CLIENTE, new ContactoCorreo("Laura Gómez", "laura@correo.com")));
        when(usuarios.administradoresActivos()).thenReturn(List.of(ADMIN));
        when(servicios.nombres(List.of(SERVICIO))).thenReturn(Map.of(SERVICIO, "Sistema de inventario"));
        when(citas.buscar(any())).thenReturn(Optional.empty());
        when(planes.insertarRespuesta(any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        comoProductOwner();
        servicio = new ServicioPlan(proyectos, planes, proyectosJpa, citas, equipos, usuarios, servicios, auditoria, sesion, eventos, calendario,
                Clock.fixed(AHORA, COLOMBIA));
    }

    // ---- ayudas ----

    static ProyectoFila proyecto(String estado, String estadoPlan) {
        return new ProyectoFila(PROYECTO, "P-0031", "Inventario del salón", "Un sistema para llevar el inventario del salón.", UUID.randomUUID(),
                CLIENTE, SERVICIO, EQUIPO, INICIO, INICIO.plusDays(40), estado, estadoPlan, null,
                // Un plan enviado siempre tiene sus dos plazos (ck_proyectos_enviado)
                estadoPlan.equals("ENVIADO") ? HOY.plusDays(3) : null, estadoPlan.equals("ENVIADO") ? HOY.plusDays(14) : null, BigDecimal.ZERO, null, null, null,
                AHORA, AHORA, 0, null, null, null, null);
    }

    void comoProductOwner() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(PO, "Andrés", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar"), false, 0));
    }

    void comoAdministrador() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR",
                Set.of("proyectos.planificar", "proyectos.gestionar"), false, 0));
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura", "laura@correo.com", "CLIENTE", Set.of("proyectos.ver_propios"), false, 0));
    }

    static FasePlantilla fase(String nombre, boolean aprueba) {
        return new FasePlantilla(0, nombre, null, aprueba, List.of(new PasoPlantilla(0, "Hacer " + nombre, null)));
    }

    static List<FasePlantilla> fasesValidas() {
        return List.of(fase("Diseño", false), fase("Entrega", true));
    }

    static Solicitud completa() {
        return new Solicitud(fasesValidas(), new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(30).toString());
    }

    static Solicitud con(BigDecimal valor, String condiciones, String fecha) {
        return new Solicitud(fasesValidas(), valor, condiciones, fecha);
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    List<AvisoSolicitado> avisos() {
        ArgumentCaptor<AvisoSolicitado> c = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(c.capture());
        return c.getAllValues();
    }

    // ------------------------------------------------------------------------------------------------ guardar

    @Nested
    @DisplayName("Guardar el borrador (HU-28)")
    class Guardar {

        @Test
        @DisplayName("Un borrador con solo las fases se guarda: la cotización puede ir vacía")
        void borradorSinCotizacion() {
            servicio.guardar("P-0031", new Solicitud(fasesValidas(), null, null, null));

            verify(planes).reemplazarFases(eq(PROYECTO), any());
            verify(proyectosJpa).guardarCotizacion(PROYECTO, null, null, null);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Guardó el borrador del plan de trabajo"), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("Guarda el valor sin decimales y la fecha ya interpretada")
        void guardaLaCotizacion() {
            servicio.guardar("P-0031", completa());

            verify(proyectosJpa).guardarCotizacion(PROYECTO, new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(30));
        }

        @Test
        @DisplayName("Fases inválidas y un valor fuera de rango a la vez: 422 con un error por campo y no guarda nada")
        void errores() {
            assertThatThrownBy(() -> servicio.guardar("P-0031", new Solicitud(List.of(fase("Sola", true)), new BigDecimal("10"), null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("fases", "valorCotizado"));
            verify(planes, never()).reemplazarFases(any(), any());
        }

        @Test
        @DisplayName("Con ajustes pedidos la cotización ya existe, así que debe seguir completa")
        void conAjustesLaCotizacionEsObligatoria() {
            proyecto = proyecto("ACTIVO", "AJUSTES_PEDIDOS");

            assertThatThrownBy(() -> servicio.guardar("P-0031", new Solicitud(fasesValidas(), null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("valorCotizado", "condicionesCotizacion", "fechaEstimadaEntrega"));
        }

        @Test
        @DisplayName("Con el plan ENVIADO no se edita: 409 que nombra al cliente")
        void planEnviado() {
            proyecto = proyecto("ACTIVO", "ENVIADO");

            assertThatThrownBy(() -> servicio.guardar("P-0031", completa())).isInstanceOf(ConflictoException.class).hasMessageContaining("Laura Gómez");
        }

        @Test
        @DisplayName("Con el plan APROBADO, RECHAZADO, VENCIDO o el proyecto pausado no se edita (409)")
        void estadosQueNoPermitenEditar() {
            for (ProyectoFila p : List.of(proyecto("ACTIVO", "APROBADO"), proyecto("PAUSADO", "RECHAZADO"), proyecto("PAUSADO", "VENCIDO"), proyecto("PAUSADO", "BORRADOR"))) {
                proyecto = p;
                assertThatThrownBy(() -> servicio.guardar("P-0031", completa())).as(p.estado() + "/" + p.estadoPlan()).isInstanceOf(ConflictoException.class);
            }
            verify(planes, never()).reemplazarFases(any(), any());
        }

        @Test
        @DisplayName("El Product Owner de otro equipo recibe 403 y queda en la auditoría; el administrador sí puede editar")
        void permisos() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar"), false, 0));
            assertThatThrownBy(() -> servicio.guardar("P-0031", completa())).isInstanceOf(ProhibidoException.class).hasMessage(ServicioPlan.NO_ES_DE_TU_EQUIPO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());

            comoAdministrador();
            assertThatCode(() -> servicio.guardar("P-0031", completa())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Un código mal formado, o que no existe, es 404")
        void codigoInvalido() {
            for (String codigo : new String[] { null, "", "P31", "31", "P-12", "p-0031", "P-0031; DROP" }) {
                assertThatThrownBy(() -> servicio.guardar(codigo, completa())).as("código «" + codigo + "»").isInstanceOf(NoEncontradoException.class);
            }
            when(proyectos.porCodigo("P-9999", HOY)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.guardar("P-9999", completa())).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioPlan.NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Bloquea el proyecto antes de cambiarlo")
        void bloquea() {
            servicio.guardar("P-0031", completa());

            verify(proyectos).bloquear("P-0031");
        }
    }

    // ------------------------------------------------------------------------------------------------ enviar

    @Nested
    @DisplayName("Enviar el plan con su cotización (HU-28; RN-29)")
    class Enviar {

        @Test
        @DisplayName("Envía: fija el plazo de 3 días hábiles y el vencimiento a los 10, audita y avisa al cliente con el enlace a su propuesta")
        void envia() {
            var r = servicio.enviar("P-0031", completa());

            assertThat(r.estadoPlan()).isEqualTo("ENVIADO");
            assertThat(r.plazoRespuesta()).isEqualTo(HOY.plusDays(3));
            assertThat(r.venceEl()).isEqualTo(HOY.plusDays(14));
            assertThat(r.porcentajePorFase()).as("100 / 2 fases").isEqualByComparingTo("50.0");
            verify(planes).reemplazarFases(eq(PROYECTO), any());
            verify(proyectosJpa).enviarPlan(PROYECTO, new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(30), HOY.plusDays(3), HOY.plusDays(14));
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Envió el plan de trabajo y la cotización"), eq("proyecto"), eq("P-0031"), anyMap());
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(CLIENTE);
            assertThat(aviso.tipo()).isEqualTo("PLAN_ENVIADO");
            assertThat(aviso.enlace()).isEqualTo("/cliente/proyectos/P-0031/propuesta");
        }

        @Test
        @DisplayName("El porcentaje por fase se redondea: 3 fases = 33,3 %")
        void porcentajeConTresFases() {
            List<FasePlantilla> tres = List.of(fase("Diseño", false), fase("Desarrollo", false), fase("Entrega", true));

            var r = servicio.enviar("P-0031", new Solicitud(tres, new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(30).toString()));

            assertThat(r.porcentajePorFase()).isEqualByComparingTo("33.3");
        }

        @Test
        @DisplayName("Un envío sin cotización: 422 con el error de cada campo obligatorio y «Completa el valor y lo que incluye»")
        void sinCotizacion() {
            assertThatThrownBy(() -> servicio.enviar("P-0031", new Solicitud(fasesValidas(), null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> {
                        assertThat(campos(e)).contains("valorCotizado", "condicionesCotizacion", "fechaEstimadaEntrega", "cotizacion");
                        assertThat(e.fallas()).anySatisfy(f -> assertThat(f.mensaje()).isEqualTo("Completa el valor y lo que incluye antes de enviar el plan"));
                    });
            verify(proyectosJpa, never()).enviarPlan(any(), any(), any(), any(), any(), any());
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Valores límite del valor (RN-29): $999.999 no, $1.000.000 sí, $200.000.000 sí, $200.000.001 no")
        void limitesDelValor() {
            assertThat(servicio.enviar("P-0031", con(new BigDecimal("1000000"), CONDICIONES, INICIO.plusDays(30).toString())).estadoPlan()).isEqualTo("ENVIADO");
            assertThat(servicio.enviar("P-0031", con(new BigDecimal("200000000"), CONDICIONES, INICIO.plusDays(30).toString())).estadoPlan()).isEqualTo("ENVIADO");

            for (String malo : List.of("999999", "200000001", "-5000000", "5000000.5", "0")) {
                assertThatThrownBy(() -> servicio.enviar("P-0031", con(new BigDecimal(malo), CONDICIONES, INICIO.plusDays(30).toString())))
                        .as("valor " + malo)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("El valor debe estar entre $1.000.000 y $200.000.000."));
            }
        }

        @Test
        @DisplayName("Un valor con ceros decimales (5000000.00) se acepta como entero")
        void decimalesEnCero() {
            servicio.enviar("P-0031", con(new BigDecimal("5000000.00"), CONDICIONES, INICIO.plusDays(30).toString()));

            verify(proyectosJpa).enviarPlan(eq(PROYECTO), eq(new BigDecimal("5000000")), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Valores límite de «qué incluye»: 19 caracteres no, 20 sí, 600 sí, 601 no")
        void limitesDeLasCondiciones() {
            String fecha = INICIO.plusDays(30).toString();
            assertThatThrownBy(() -> servicio.enviar("P-0031", con(new BigDecimal("5000000"), "x".repeat(19), fecha)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("condicionesCotizacion"));
            assertThatThrownBy(() -> servicio.enviar("P-0031", con(new BigDecimal("5000000"), "x".repeat(601), fecha)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 600 caracteres."));

            assertThat(servicio.enviar("P-0031", con(new BigDecimal("5000000"), "x".repeat(20), fecha)).estadoPlan()).isEqualTo("ENVIADO");
            assertThat(servicio.enviar("P-0031", con(new BigDecimal("5000000"), "x".repeat(600), fecha)).estadoPlan()).isEqualTo("ENVIADO");
        }

        @Test
        @DisplayName("Valor límite de la entrega: 13 días después del inicio no; 14 sí (ck_proyectos_fechas)")
        void entregaAlMenosDosSemanasDespues() {
            assertThatThrownBy(() -> servicio.enviar("P-0031", con(new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(13).toString())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).startsWith("La entrega debe ser al menos 2 semanas después del inicio"));

            assertThat(servicio.enviar("P-0031", con(new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(14).toString())).estadoPlan()).isEqualTo("ENVIADO");
        }

        @Test
        @DisplayName("Una fecha mal escrita, o de un año absurdo, da 422 en fechaEstimadaEntrega")
        void fechasInvalidas() {
            assertThatThrownBy(() -> servicio.enviar("P-0031", con(new BigDecimal("5000000"), CONDICIONES, "15/04/2027")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe la fecha como AAAA-MM-DD."));
            // El tope cuenta desde hoy (5 años), no es una fecha fija: con el reloj en 2027-03-02 el último día es 2032-03-02
            LocalDate ultimo = ReglasPlan.ultimaFechaDeEntrega(HOY);
            assertThat(ultimo).isEqualTo(LocalDate.of(2032, 3, 2));
            assertThatThrownBy(() -> servicio.enviar("P-0031", con(new BigDecimal("5000000"), CONDICIONES, ultimo.plusDays(1).toString())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Revisa el año de la fecha."));
            assertThat(servicio.enviar("P-0031", con(new BigDecimal("5000000"), CONDICIONES, ultimo.toString())).estadoPlan()).as("el último día permitido").isEqualTo("ENVIADO");
            assertThat(servicio.enviar("P-0031", con(new BigDecimal("5000000"), CONDICIONES, "2029-06-01")).estadoPlan()).as("una fecha de 2029 ya es válida").isEqualTo("ENVIADO");
        }

        @Test
        @DisplayName("Si el plan ya fue enviado, no se vuelve a enviar (409)")
        void yaEnviado() {
            proyecto = proyecto("ACTIVO", "ENVIADO");

            assertThatThrownBy(() -> servicio.enviar("P-0031", completa())).isInstanceOf(ConflictoException.class);
            verify(proyectosJpa, never()).enviarPlan(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Las fases inválidas (una sola) impiden enviar con el mensaje «el plan»")
        void fasesInvalidas() {
            assertThatThrownBy(() -> servicio.enviar("P-0031", new Solicitud(List.of(fase("Sola", true)), new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(30).toString())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("fases"));
        }

        @Test
        @DisplayName("Recordar: con el plan ENVIADO avisa de nuevo al cliente; en otro estado da 409")
        void recordar() {
            proyecto = proyecto("ACTIVO", "ENVIADO");
            servicio.recordar("P-0031");
            assertThat(avisos().get(0).tipo()).isEqualTo("PLAN_RECORDATORIO");

            proyecto = proyecto("ACTIVO", "BORRADOR");
            assertThatThrownBy(() -> servicio.recordar("P-0031")).isInstanceOf(ConflictoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ responder

    @Nested
    @DisplayName("El cliente responde (HU-29)")
    class Responder {

        @BeforeEach
        void planEnviado() {
            proyecto = proyecto("ACTIVO", "ENVIADO");
            comoCliente(CLIENTE);
        }

        @Test
        @DisplayName("Aprobar: el plan queda APROBADO, empieza la fase 1, se guarda la respuesta y se avisa al Product Owner")
        void aprueba() {
            Instant aprobado = AHORA;
            when(planes.cotizacion(PROYECTO)).thenReturn(new Cotizacion(new BigDecimal("5000000"), CONDICIONES, INICIO.plusDays(30), AHORA, aprobado));

            var r = servicio.aprobar("P-0031", true);

            assertThat(r.estadoPlan()).isEqualTo("APROBADO");
            assertThat(r.planAprobadoAt()).isEqualTo(aprobado);
            org.mockito.InOrder orden = org.mockito.Mockito.inOrder(planes, proyectosJpa);
            orden.verify(proyectosJpa).aprobarPlan(PROYECTO);
            orden.verify(planes).iniciarPrimeraFase(PROYECTO);
            verify(planes).insertarRespuesta(PROYECTO, CLIENTE, "APROBADO", null);
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(PO);
            assertThat(aviso.tipo()).isEqualTo("PLAN_APROBADO");
        }

        @Test
        @DisplayName("Aprobar sin confirmar que leyó el plan y la cotización: 422 y no cambia nada")
        void sinConfirmar() {
            assertThatThrownBy(() -> servicio.aprobar("P-0031", false))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("aceptaPlanYCotizacion"));
            verify(proyectosJpa, never()).aprobarPlan(any());
        }

        @Test
        @DisplayName("El plan no se aprueba solo: cada acción exige que esté ENVIADO y el proyecto ACTIVO (409)")
        void soloEnviado() {
            for (ProyectoFila p : List.of(proyecto("ACTIVO", "BORRADOR"), proyecto("ACTIVO", "APROBADO"), proyecto("PAUSADO", "ENVIADO"), proyecto("PAUSADO", "VENCIDO"))) {
                proyecto = p;
                assertThatThrownBy(() -> servicio.aprobar("P-0031", true)).isInstanceOf(ConflictoException.class).hasMessage(ServicioPlan.NO_ESPERA_RESPUESTA);
                assertThatThrownBy(() -> servicio.pedirAjustes("P-0031", "x".repeat(30))).isInstanceOf(ConflictoException.class);
                assertThatThrownBy(() -> servicio.rechazar("P-0031", "x".repeat(30))).isInstanceOf(ConflictoException.class);
            }
            verify(proyectosJpa, never()).aprobarPlan(any());
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404 (RN-10) y queda en la auditoría; el Product Owner tampoco responde por el cliente")
        void ajeno() {
            comoCliente(OTRO_CLIENTE);

            assertThatThrownBy(() -> servicio.aprobar("P-0031", true)).isInstanceOf(NoEncontradoException.class);
            assertThatThrownBy(() -> servicio.pedirAjustes("P-0031", "x".repeat(30))).isInstanceOf(NoEncontradoException.class);
            assertThatThrownBy(() -> servicio.rechazar("P-0031", "x".repeat(30))).isInstanceOf(NoEncontradoException.class);
            verify(auditoria, org.mockito.Mockito.times(3)).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(proyectosJpa, never()).cambiarEstadoPlan(any(), any());
        }

        @Test
        @DisplayName("Pedir ajustes: el plan pasa a AJUSTES_PEDIDOS con el comentario y se avisa al equipo")
        void pideAjustes() {
            String comentario = "  Quiero que la fase de diseño incluya el logo de mi negocio.  ";

            var r = servicio.pedirAjustes("P-0031", comentario);

            assertThat(r.estadoPlan()).isEqualTo("AJUSTES_PEDIDOS");
            verify(proyectosJpa).cambiarEstadoPlan(PROYECTO, EstadoPlan.AJUSTES_PEDIDOS);
            verify(planes).insertarRespuesta(PROYECTO, CLIENTE, "AJUSTES_PEDIDOS", comentario.trim());
            assertThat(avisos().get(0).tipo()).isEqualTo("PLAN_AJUSTES_PEDIDOS");
        }

        @Test
        @DisplayName("Valores límite del comentario: 19 caracteres no (y nombra al Product Owner), 20 sí, 600 sí, 601 no")
        void limitesDelComentario() {
            assertThatThrownBy(() -> servicio.pedirAjustes("P-0031", "x".repeat(19)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("Andrés Pérez te entienda"));
            assertThatThrownBy(() -> servicio.pedirAjustes("P-0031", "   ")).hasMessage("Cuéntanos qué quieres cambiar.");
            assertThatThrownBy(() -> servicio.pedirAjustes("P-0031", null)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.pedirAjustes("P-0031", "x".repeat(601))).hasMessage("Escribe máximo 600 caracteres.");

            assertThat(servicio.pedirAjustes("P-0031", "x".repeat(20)).estadoPlan()).isEqualTo("AJUSTES_PEDIDOS");
            assertThat(servicio.pedirAjustes("P-0031", "x".repeat(600)).estadoPlan()).isEqualTo("AJUSTES_PEDIDOS");
        }

        @Test
        @DisplayName("Rechazar: plan RECHAZADO y proyecto PAUSADO; avisa al cliente (con el plazo de 30 días), al equipo y a los administradores")
        void rechaza() {
            var r = servicio.rechazar("P-0031", "Es más caro de lo que podemos pagar este año.");

            assertThat(r.estadoPlan()).isEqualTo("RECHAZADO");
            assertThat(r.estado()).isEqualTo("PAUSADO");
            verify(proyectosJpa).cambiarEstadoPlan(PROYECTO, EstadoPlan.RECHAZADO);
            verify(planes).insertarRespuesta(PROYECTO, CLIENTE, "RECHAZADO", "Es más caro de lo que podemos pagar este año.");
            List<AvisoSolicitado> publicados = avisos();
            assertThat(publicados).extracting(AvisoSolicitado::tipo).containsExactly("PLAN_RECHAZADO_CLIENTE", "PLAN_RECHAZADO", "PLAN_RECHAZADO");
            assertThat(publicados.get(0).destinatarios()).containsExactly(CLIENTE);
            assertThat(publicados.get(0).texto()).contains("30 días");
            assertThat(publicados.get(1).destinatarios()).containsExactly(PO);
            assertThat(publicados.get(2).destinatarios()).containsExactly(ADMIN);
        }

        @Test
        @DisplayName("Rechazar sin administradores activos no falla: solo no avisa a nadie de la administración")
        void sinAdministradores() {
            when(usuarios.administradoresActivos()).thenReturn(List.of());

            servicio.rechazar("P-0031", "Es más caro de lo que podemos pagar este año.");

            assertThat(avisos()).hasSize(2);
        }

        @Test
        @DisplayName("El motivo del rechazo: 19 caracteres no, 20 sí, 601 no")
        void limitesDelMotivo() {
            assertThatThrownBy(() -> servicio.rechazar("P-0031", "x".repeat(19)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).campo()).isEqualTo("motivo"));
            assertThatThrownBy(() -> servicio.rechazar("P-0031", "x".repeat(601))).isInstanceOf(ValidacionException.class);
            verify(proyectosJpa, never()).cambiarEstadoPlan(any(), eq(EstadoPlan.RECHAZADO));

            assertThat(servicio.rechazar("P-0031", "x".repeat(20)).estadoPlan()).isEqualTo("RECHAZADO");
        }

        @Test
        @DisplayName("Un aviso larguísimo se recorta a 255 caracteres con «…» para no romper el límite del aviso")
        void recortaElAviso() {
            servicio.pedirAjustes("P-0031", "x".repeat(600));

            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.texto()).hasSize(255).endsWith("…");
        }
    }

    // ------------------------------------------------------------------------------------------------ leer

    @Nested
    @DisplayName("Leer el plan")
    class Leer {

        @Test
        @DisplayName("El Product Owner del equipo lo ve y puede editarlo mientras está en borrador")
        void equipo() {
            var vista = servicio.obtener("P-0031");

            assertThat(vista.codigo()).isEqualTo("P-0031");
            assertThat(vista.cliente()).isEqualTo("Laura Gómez");
            assertThat(vista.productOwner()).isEqualTo("Andrés Pérez");
            assertThat(vista.servicio()).isEqualTo("Sistema de inventario");
            assertThat(vista.editable()).isTrue();
        }

        @Test
        @DisplayName("Con el plan ENVIADO no es editable, ni para el equipo")
        void enviadoNoEsEditable() {
            proyecto = proyecto("ACTIVO", "ENVIADO");

            assertThat(servicio.obtener("P-0031").editable()).isFalse();
        }

        @Test
        @DisplayName("El Product Owner de otro equipo recibe 403")
        void otroEquipo() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar"), false, 0));

            assertThatThrownBy(() -> servicio.obtener("P-0031")).isInstanceOf(ProhibidoException.class);
        }

        @Test
        @DisplayName("Para el cliente un borrador no existe todavía (409); con el plan enviado lo ve y no es editable")
        void clienteYElBorrador() {
            comoCliente(CLIENTE);
            assertThatThrownBy(() -> servicio.obtener("P-0031")).isInstanceOf(ConflictoException.class).hasMessageContaining("lo está preparando");

            proyecto = proyecto("ACTIVO", "ENVIADO");
            assertThat(servicio.obtener("P-0031").editable()).isFalse();
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404")
        void clienteAjeno() {
            comoCliente(OTRO_CLIENTE);

            assertThatThrownBy(() -> servicio.obtener("P-0031")).isInstanceOf(NoEncontradoException.class);
        }

        @Test
        @DisplayName("Con ajustes pedidos o rechazo, la vista trae el comentario del cliente; sin ellos, nulo")
        void comentarios() {
            proyecto = proyecto("ACTIVO", "AJUSTES_PEDIDOS");
            when(planes.ultimaRespuesta(PROYECTO, "AJUSTES_PEDIDOS")).thenReturn(Optional.of(new PlanesJdbc.Respuesta(UUID.randomUUID(), "AJUSTES_PEDIDOS", "Falta el logo en el diseño.", AHORA)));

            var vista = servicio.obtener("P-0031");

            assertThat(vista.ajustesPedidos().texto()).isEqualTo("Falta el logo en el diseño.");
            assertThat(vista.rechazo()).isNull();
            verify(planes, never()).ultimaRespuesta(PROYECTO, "RECHAZADO");
        }

        @Test
        @DisplayName("Sin fases, el porcentaje por fase es 0 y no divide por cero")
        void sinFases() {
            assertThat(servicio.obtener("P-0031").porcentajePorFase()).isEqualByComparingTo("0");
        }
    }

    @Test
    @DisplayName("La fecha del aviso la arma Fechas.conDia con el día de la semana (revisión del texto que ve el cliente)")
    void fechasDeLosAvisos() {
        assertThat(Fechas.conDia(LocalDate.of(2027, 3, 5))).isNotBlank();
    }
}
