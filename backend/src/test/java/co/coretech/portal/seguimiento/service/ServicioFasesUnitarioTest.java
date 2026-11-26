package co.coretech.portal.seguimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
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
import co.coretech.portal.especialidad.EquipoConsultas.ProductOwner;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento.Proyecto;
import co.coretech.portal.seguimiento.service.ServicioFases.CambioPedido;
import co.coretech.portal.seguimiento.service.ServicioFases.Entregada;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseDeProyecto;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseEnRevision;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseFila;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.PasoContexto;

/**
 * Pruebas UNITARIAS de {@link ServicioFases} (HU-11, HU-12, HU-13; RN-08, RN-13, RN-16, RN-17, RN-31): el ciclo de una fase.
 * La persistencia, el proyecto, el equipo, el calendario, la auditoría y los eventos van simulados con Mockito; no hay base
 * de datos. El reloj es fijo: martes 2031-03-04, 10:00 hora de Colombia. Patrón AAA.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioFasesUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = Instant.parse("2031-03-04T15:00:00Z");
    static final LocalDate HOY = LocalDate.of(2031, 3, 4);

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID FASE = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    static final UUID SIGUIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000f2");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    @Mock
    SeguimientoJdbc datos;
    @Mock
    ProyectosParaSeguimiento proyectos;
    @Mock
    EquipoConsultas equipos;
    @Mock
    DirectorioUsuarios usuarios;
    @Mock
    CalendarioHabil calendario;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;

    ServicioFases servicio;
    Proyecto proyecto;

    @BeforeEach
    void preparaLoComun() {
        proyecto = proyecto("ACTIVO", "APROBADO", null);
        when(proyectos.porId(PROYECTO)).thenAnswer(i -> Optional.of(proyecto));
        when(equipos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
        when(equipos.equipoDelProductOwner(OTRO_PO)).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipos.productOwner(EQUIPO)).thenReturn(Optional.of(new ProductOwner(PO, "Andrés Pérez", "andres@coretechsolutions.co")));
        when(usuarios.perfiles(List.of(CLIENTE))).thenReturn(java.util.Map.of(CLIENTE, new ContactoCorreo("Laura Gómez", "laura@correo.com")));
        when(calendario.sumarDiasHabiles(HOY, 3)).thenReturn(HOY.plusDays(3));
        when(datos.pendientes(FASE)).thenReturn(new int[] { 0, 0 });
        when(datos.entregarParaRevision(eq(FASE), any(), any())).thenReturn(true);
        when(datos.aprobar(eq(FASE), any(), any(), anyBoolean())).thenReturn(true);
        when(datos.devolverPorAjuste(FASE)).thenReturn(true);
        when(datos.siguientePendiente(PROYECTO)).thenReturn(Optional.empty());
        when(datos.fasesSinAprobar(PROYECTO)).thenReturn(0);
        when(datos.insertarPaso(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        when(datos.insertarSolicitud(any(), any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        conFase(fase("EN_CURSO", true, 0));
        comoProductOwner();
        servicio = new ServicioFases(datos, proyectos, equipos, usuarios, calendario, auditoria, sesion, eventos, Clock.fixed(AHORA, COLOMBIA));
    }

    // ---- ayudas ----

    static Proyecto proyecto(String estado, String estadoPlan, LocalDate garantiaHasta) {
        return new Proyecto(PROYECTO, "P-0031", "Inventario del salón", CLIENTE, EQUIPO, "Sistema de inventario", "C-0045", estado, estadoPlan,
                new BigDecimal("40.00"), HOY.minusDays(30), HOY.plusDays(30), garantiaHasta);
    }

    static FaseFila fase(String estado, boolean apruebaCliente, int rondasUsadas) {
        return new FaseFila(FASE, 2, "Construcción", "Se construye el sistema", apruebaCliente, estado, AHORA.minusSeconds(86400), null, null,
                rondasUsadas, false, null);
    }

    void conFase(FaseFila fase) {
        when(datos.faseConProyecto(FASE)).thenReturn(Optional.of(new FaseDeProyecto(fase, PROYECTO)));
    }

    void comoProductOwner() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(PO, "Andrés", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of(), false, 0));
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura", "laura@correo.com", "CLIENTE", Set.of(), false, 0));
    }

    List<AvisoSolicitado> avisosPublicados() {
        ArgumentCaptor<AvisoSolicitado> capturados = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(capturados.capture());
        return capturados.getAllValues();
    }

    // ------------------------------------------------------------------------------------------------------ entregar

    @Nested
    @DisplayName("Entregar o cerrar una fase (HU-13)")
    class Entregar {

        @Test
        @DisplayName("La fase que aprueba el cliente queda EN_REVISION con plazo de 3 días hábiles y se le avisa al cliente")
        void entregaParaRevision() {
            Entregada r = servicio.entregar(FASE);

            assertThat(r.estado()).isEqualTo("EN_REVISION");
            assertThat(r.plazoAprobacion()).isEqualTo(HOY.plusDays(3));
            verify(datos).entregarParaRevision(FASE, AHORA, HOY.plusDays(3));
            verify(datos, never()).aprobar(any(), any(), any(), anyBoolean());
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), eq("Entregó una fase al cliente"), eq("proyecto"), eq("P-0031"), anyMap());
            AvisoSolicitado aviso = avisosPublicados().get(0);
            assertThat(aviso.destinatarios()).containsExactly(CLIENTE);
            assertThat(aviso.tipo()).isEqualTo("FASE_ENTREGADA");
            assertThat(aviso.enlace()).isEqualTo("/cliente/proyectos/P-0031");
        }

        @Test
        @DisplayName("La fase que cierra el equipo queda APROBADA y empieza la siguiente (RN-08)")
        void cierraYEmpiezaLaSiguiente() {
            conFase(new FaseFila(FASE, 2, "Construcción", null, false, "EN_CURSO", AHORA, null, null, 0, false, null));
            when(datos.siguientePendiente(PROYECTO)).thenReturn(Optional.of(new FaseFila(SIGUIENTE, 3, "Entrega", null, true, "PENDIENTE", null, null, null, 0, false, null)));

            Entregada r = servicio.entregar(FASE);

            assertThat(r.estado()).isEqualTo("APROBADA");
            assertThat(r.plazoAprobacion()).isNull();
            verify(datos).aprobar(FASE, AHORA, PO, false);
            verify(datos).iniciar(SIGUIENTE, AHORA);
            verify(proyectos, never()).iniciarGarantia(any(), any());
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), eq("Cerró una fase"), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("Cerrar la última fase deja el proyecto EN_GARANTIA 30 días (RN-13, RN-31) y avisa a las dos partes")
        void cerrarLaUltimaInicia30DiasDeGarantia() {
            conFase(new FaseFila(FASE, 5, "Entrega", null, false, "EN_CURSO", AHORA, null, null, 0, false, null));
            when(datos.siguientePendiente(PROYECTO)).thenReturn(Optional.empty());
            when(datos.fasesSinAprobar(PROYECTO)).thenReturn(0);

            servicio.entregar(FASE);

            verify(proyectos).iniciarGarantia(PROYECTO, HOY.plusDays(30));
            assertThat(avisosPublicados()).extracting(AvisoSolicitado::tipo).contains("PROYECTO_EN_GARANTIA", "FASE_CERRADA");
        }

        @Test
        @DisplayName("Si quedan fases sin aprobar (por ejemplo, una en corrección) no empieza la garantía")
        void conFasesSinAprobarNoHayGarantia() {
            conFase(new FaseFila(FASE, 5, "Entrega", null, false, "EN_CURSO", AHORA, null, null, 0, false, null));
            when(datos.fasesSinAprobar(PROYECTO)).thenReturn(1);

            servicio.entregar(FASE);

            verify(proyectos, never()).iniciarGarantia(any(), any());
        }

        @Test
        @DisplayName("Con correcciones abiertas en la fase: 409 «Termina las correcciones antes de entregar» (RN-16)")
        void conCorreccionesAbiertas() {
            when(datos.pendientes(FASE)).thenReturn(new int[] { 1, 0 });

            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ConflictoException.class).hasMessage(ServicioFases.CORRECCIONES_ABIERTAS);
            verify(datos, never()).entregarParaRevision(any(), any(), any());
        }

        @Test
        @DisplayName("Con pasos pendientes: «Falta 1 paso» en singular y «Faltan 3 pasos» en plural")
        void conPasosPendientes() {
            when(datos.pendientes(FASE)).thenReturn(new int[] { 0, 1 });
            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ConflictoException.class).hasMessage("Falta 1 paso por terminar");

            when(datos.pendientes(FASE)).thenReturn(new int[] { 0, 3 });
            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ConflictoException.class).hasMessage("Faltan 3 pasos por terminar");
        }

        @Test
        @DisplayName("Una fase que no está EN_CURSO (pendiente, en revisión o aprobada) da 409")
        void faseQueNoEstaEnCurso() {
            for (String estado : List.of("PENDIENTE", "EN_REVISION", "APROBADA", "EN_CORRECCION")) {
                conFase(fase(estado, true, 0));
                assertThatThrownBy(() -> servicio.entregar(FASE)).as(estado).isInstanceOf(ConflictoException.class).hasMessage("Esta fase no está en curso");
            }
        }

        @Test
        @DisplayName("Un proyecto pausado, en garantía o con el plan sin aprobar no admite entregar (409)")
        void proyectoQueNoAdmite() {
            for (Proyecto p : List.of(proyecto("PAUSADO", "APROBADO", null), proyecto("EN_GARANTIA", "APROBADO", HOY), proyecto("ACTIVO", "ENVIADO", null))) {
                proyecto = p;
                assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ConflictoException.class).hasMessage(ServicioSeguimiento.ESTADO_NO_ADMITE);
            }
        }

        @Test
        @DisplayName("El Product Owner de otro equipo recibe 403 y queda en la auditoría (RN-07)")
        void otroEquipo() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of(), false, 0));

            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ProhibidoException.class).hasMessage(ServicioSeguimiento.SOLO_EL_PRODUCT_OWNER);
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(datos, never()).entregarParaRevision(any(), any(), any());
        }

        @Test
        @DisplayName("Quien no lidera ningún equipo (un cliente o el administrador) recibe 403")
        void sinEquipo() {
            when(equipos.equipoDelProductOwner(CLIENTE)).thenReturn(Optional.empty());
            comoCliente(CLIENTE);

            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ProhibidoException.class);
        }

        @Test
        @DisplayName("Una fase que no existe es 404")
        void faseInexistente() {
            when(datos.faseConProyecto(FASE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioFases.FASE_NO_ENCONTRADA);
        }

        @Test
        @DisplayName("Si otro cambió la fase entre la lectura y el cambio (el UPDATE no toca filas): 409 «El proyecto cambió»")
        void datosDesactualizados() {
            when(datos.entregarParaRevision(eq(FASE), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> servicio.entregar(FASE)).isInstanceOf(ConflictoException.class).hasMessage(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Bloquea el proyecto antes de decidir, para que dos acciones a la vez no se pisen")
        void bloqueaElProyecto() {
            servicio.entregar(FASE);

            verify(proyectos).bloquear(PROYECTO);
        }
    }

    // ------------------------------------------------------------------------------------------------------ aprobar

    @Nested
    @DisplayName("Aprobar una fase (HU-11)")
    class Aprobar {

        @BeforeEach
        void enRevision() {
            conFase(fase("EN_REVISION", true, 0));
            comoCliente(CLIENTE);
        }

        @Test
        @DisplayName("El cliente dueño aprueba: queda APROBADA, empieza la siguiente y se avisa al equipo")
        void aprueba() {
            when(datos.siguientePendiente(PROYECTO)).thenReturn(Optional.of(new FaseFila(SIGUIENTE, 3, "Entrega", null, true, "PENDIENTE", null, null, null, 0, false, null)));

            var r = servicio.aprobar(FASE);

            assertThat(r.estado()).isEqualTo("APROBADA");
            verify(datos).aprobar(FASE, AHORA, CLIENTE, false);
            verify(datos).iniciar(SIGUIENTE, AHORA);
            AvisoSolicitado aviso = avisosPublicados().get(0);
            assertThat(aviso.destinatarios()).containsExactly(PO);
            assertThat(aviso.tipo()).isEqualTo("FASE_APROBADA");
            assertThat(aviso.enlace()).isEqualTo("/equipo/proyectos/P-0031/registrar");
            assertThat(aviso.texto()).as("usa el primer nombre del cliente").startsWith("Laura aprobó");
        }

        @Test
        @DisplayName("Aprobar la última fase lleva el proyecto a garantía de 30 días")
        void aprobarLaUltima() {
            servicio.aprobar(FASE);

            verify(proyectos).iniciarGarantia(PROYECTO, HOY.plusDays(30));
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404 (no se revela el proyecto) y queda en la auditoría (RN-10)")
        void clienteAjeno() {
            comoCliente(OTRO_CLIENTE);

            assertThatThrownBy(() -> servicio.aprobar(FASE)).isInstanceOf(NoEncontradoException.class);
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(datos, never()).aprobar(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Una fase que no espera su aprobación (en curso, o que cierra el equipo) da 409")
        void noEspera() {
            conFase(fase("EN_CURSO", true, 0));
            assertThatThrownBy(() -> servicio.aprobar(FASE)).isInstanceOf(ConflictoException.class).hasMessage(ServicioFases.NO_ESPERA_APROBACION);

            conFase(fase("EN_REVISION", false, 0));
            assertThatThrownBy(() -> servicio.aprobar(FASE)).isInstanceOf(ConflictoException.class).hasMessage(ServicioFases.NO_ESPERA_APROBACION);
        }

        @Test
        @DisplayName("Con el proyecto pausado no se aprueba (409)")
        void proyectoPausado() {
            proyecto = proyecto("PAUSADO", "APROBADO", null);

            assertThatThrownBy(() -> servicio.aprobar(FASE)).isInstanceOf(ConflictoException.class);
        }

        @Test
        @DisplayName("Dos aprobaciones a la vez: la segunda ve que el UPDATE no cambió nada y da 409")
        void aprobacionDoble() {
            when(datos.aprobar(eq(FASE), any(), any(), anyBoolean())).thenReturn(false);

            assertThatThrownBy(() -> servicio.aprobar(FASE)).isInstanceOf(ConflictoException.class).hasMessage(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
        }
    }

    // ------------------------------------------------------------------------------------------------------ pedir cambios

    @Nested
    @DisplayName("Pedir cambios en una fase (HU-11; RN-17)")
    class PedirCambio {

        static final String TEXTO = "Quiero que el reporte de ventas muestre también el total por semana.";

        @BeforeEach
        void enRevision() {
            conFase(fase("EN_REVISION", true, 0));
            comoCliente(CLIENTE);
        }

        @Test
        @DisplayName("Un AJUSTE usa la ronda 1 de 2: la fase vuelve a EN_CURSO con un paso de tipo AJUSTE")
        void primerAjuste() {
            CambioPedido r = servicio.pedirCambio(FASE, "AJUSTE", TEXTO);

            assertThat(r.rondasUsadas()).isEqualTo(1);
            assertThat(r.estadoFase()).isEqualTo("EN_CURSO");
            verify(datos).devolverPorAjuste(FASE);
            verify(datos).insertarPaso(eq(FASE), eq("AJUSTE"), eq("Ajuste 1 de 2"), eq(TEXTO), eq("Lo pidió el cliente"));
            verify(datos).insertarSolicitud(eq(FASE), eq(CLIENTE), eq("AJUSTE"), eq(TEXTO), eq("ABIERTO"), any());
            assertThat(avisosPublicados().get(0).tipo()).isEqualTo("FASE_AJUSTE");
        }

        @Test
        @DisplayName("Valor límite: la 2.ª ronda todavía se puede usar; la 3.ª da 409 «Ya usaste las 2 rondas»")
        void tercerAjusteNo() {
            conFase(fase("EN_REVISION", true, 1));
            assertThat(servicio.pedirCambio(FASE, "AJUSTE", TEXTO).rondasUsadas()).isEqualTo(2);

            conFase(fase("EN_REVISION", true, 2));
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", TEXTO)).isInstanceOf(ConflictoException.class).hasMessage(ServicioFases.SIN_RONDAS);
        }

        @Test
        @DisplayName("Algo nuevo (ALCANCE) queda POR_COTIZAR, la fase sigue en revisión y no gasta una ronda; aunque ya se usaron las 2")
        void alcance() {
            conFase(fase("EN_REVISION", true, 2));

            CambioPedido r = servicio.pedirCambio(FASE, "ALCANCE", TEXTO);

            assertThat(r.estado()).isEqualTo("POR_COTIZAR");
            assertThat(r.estadoFase()).isEqualTo("EN_REVISION");
            assertThat(r.rondasUsadas()).isEqualTo(2);
            verify(datos, never()).devolverPorAjuste(any());
            verify(datos, never()).insertarPaso(any(), any(), any(), any(), any());
            assertThat(avisosPublicados().get(0).tipo()).isEqualTo("CAMBIO_ALCANCE");
        }

        @Test
        @DisplayName("Valores límite de la descripción: 19 caracteres no, 20 sí, 800 sí, 801 no")
        void largoDeLaDescripcion() {
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", "x".repeat(19)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).campo()).isEqualTo("descripcion"));
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", "x".repeat(801)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 800 caracteres."));

            assertThat(servicio.pedirCambio(FASE, "ALCANCE", "x".repeat(20)).tipo()).isEqualTo("ALCANCE");
            assertThat(servicio.pedirCambio(FASE, "ALCANCE", "x".repeat(800)).tipo()).isEqualTo("ALCANCE");
        }

        @Test
        @DisplayName("El mensaje de descripción corta nombra al Product Owner")
        void mensajeConElNombreDelProductOwner() {
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", "corto"))
                    .hasMessageContaining("Andrés Pérez te entienda");
        }

        @Test
        @DisplayName("Sin tipo o con un tipo desconocido: 422 en el campo tipo; sin descripción: 422 en descripcion; ambos a la vez")
        void tipoYDescripcionInvalidos() {
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "OTRO", "   "))
                    .isInstanceOfSatisfying(ValidacionException.class,
                            e -> assertThat(e.fallas()).extracting(ValidacionException.Falla::campo).containsExactly("tipo", "descripcion"));
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, null, TEXTO)).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("No guarda nada si la validación falla")
        void validacionNoGuarda() {
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", "corto")).isInstanceOf(ValidacionException.class);

            verify(datos, never()).insertarSolicitud(any(), any(), any(), any(), any(), any());
            verify(datos, never()).devolverPorAjuste(any());
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404 y una fase que no espera respuesta, 409")
        void permisosYEstado() {
            comoCliente(OTRO_CLIENTE);
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", TEXTO)).isInstanceOf(NoEncontradoException.class);

            comoCliente(CLIENTE);
            conFase(fase("EN_CURSO", true, 0));
            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", TEXTO)).isInstanceOf(ConflictoException.class);
        }

        @Test
        @DisplayName("Si la fase cambió mientras tanto (el UPDATE no toca filas), 409 y no se crea el paso")
        void datosDesactualizados() {
            when(datos.devolverPorAjuste(FASE)).thenReturn(false);

            assertThatThrownBy(() -> servicio.pedirCambio(FASE, "AJUSTE", TEXTO)).isInstanceOf(ConflictoException.class);
            verify(datos, never()).insertarPaso(any(), any(), any(), any(), any());
        }
    }

    // ------------------------------------------------------------------------------------------------------ correcciones

    @Nested
    @DisplayName("Registrar una corrección (HU-12; RN-16)")
    class Correcciones {

        static final String ORIGEN = "La encontramos en pruebas";
        static final String EXPLICACION = "El reporte mostraba mal el total cuando había devoluciones.";

        @Test
        @DisplayName("En una fase en curso crea el paso CORRECCION y la fase sigue en curso")
        void enFaseEnCurso() {
            var r = servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION);

            assertThat(r.estadoFase()).isEqualTo("EN_CURSO");
            verify(datos).insertarPaso(FASE, "CORRECCION", "Total del reporte", EXPLICACION, ORIGEN);
            verify(datos, never()).ponerEnCorreccion(any());
        }

        @Test
        @DisplayName("En una fase APROBADA con el proyecto ACTIVO la pasa a EN_CORRECCION: deja de sumar su porcentaje (RN-16)")
        void enFaseAprobada() {
            conFase(fase("APROBADA", true, 0));

            var r = servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION);

            assertThat(r.estadoFase()).isEqualTo("EN_CORRECCION");
            verify(datos).ponerEnCorreccion(FASE);
            assertThat(avisosPublicados().get(0).texto()).contains("la fase no suma su porcentaje");
        }

        @Test
        @DisplayName("En garantía la fase sigue APROBADA y el avance sigue en 100 % (RN-16)")
        void enGarantia() {
            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY.plusDays(10));
            conFase(fase("APROBADA", true, 0));

            var r = servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION);

            assertThat(r.estadoFase()).isEqualTo("APROBADA");
            verify(datos, never()).ponerEnCorreccion(any());
        }

        @Test
        @DisplayName("Valor límite de la garantía: el último día todavía se puede; el día del vencimiento ya no (409)")
        void limiteDeLaGarantia() {
            conFase(fase("APROBADA", true, 0));
            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY.plusDays(1));
            assertThat(servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION).estadoFase()).isEqualTo("APROBADA");

            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY);
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION))
                    .isInstanceOf(ConflictoException.class).hasMessage(ServicioFases.GARANTIA_TERMINADA);
        }

        @Test
        @DisplayName("Un proyecto finalizado, pausado o con el plan sin aprobar no admite correcciones (409)")
        void proyectoQueNoAdmite() {
            for (Proyecto p : List.of(proyecto("FINALIZADO", "APROBADO", HOY.minusDays(5)), proyecto("PAUSADO", "APROBADO", null), proyecto("ACTIVO", "BORRADOR", null))) {
                proyecto = p;
                assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION))
                        .isInstanceOf(ConflictoException.class).hasMessage(ServicioSeguimiento.ESTADO_NO_ADMITE);
            }
        }

        @Test
        @DisplayName("Una fase en revisión (espera al cliente) o pendiente no admite correcciones (409)")
        void faseQueNoAdmite() {
            conFase(fase("EN_REVISION", true, 0));
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION)).isInstanceOf(ConflictoException.class);

            conFase(fase("PENDIENTE", true, 0));
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION))
                    .isInstanceOf(ConflictoException.class).hasMessage(ServicioFases.SOLO_FASES_EN_CURSO_O_APROBADAS);
        }

        @Test
        @DisplayName("Valores límite: título de 3 a 80; explicación de 20 a 400")
        void limitesDeTexto() {
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "ab", EXPLICACION)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "x".repeat(81), EXPLICACION)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "abc", "x".repeat(19))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "abc", "x".repeat(401))).isInstanceOf(ValidacionException.class);

            assertThat(servicio.registrarCorreccion(FASE, ORIGEN, "abc", "x".repeat(20)).estadoFase()).isEqualTo("EN_CURSO");
            assertThat(servicio.registrarCorreccion(FASE, ORIGEN, "x".repeat(80), "x".repeat(400)).estadoFase()).isEqualTo("EN_CURSO");
        }

        @Test
        @DisplayName("El origen debe ser uno de los tres de la lista; si no, 422 en origen, y no se guarda nada")
        void origenDesconocido() {
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, "Porque sí", "Total del reporte", EXPLICACION))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).campo()).isEqualTo("origen"));
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, null, "Total del reporte", EXPLICACION)).isInstanceOf(ValidacionException.class);

            verify(datos, never()).insertarPaso(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Los textos nulos no lanzan NullPointerException: dan 422 con un error por campo")
        void nulos() {
            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, null, null, null))
                    .isInstanceOfSatisfying(ValidacionException.class,
                            e -> assertThat(e.fallas()).extracting(ValidacionException.Falla::campo).containsExactly("origen", "titulo", "explicacion"));
        }

        @Test
        @DisplayName("Solo el Product Owner del equipo: el de otro equipo recibe 403")
        void otroEquipo() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of(), false, 0));

            assertThatThrownBy(() -> servicio.registrarCorreccion(FASE, ORIGEN, "Total del reporte", EXPLICACION)).isInstanceOf(ProhibidoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------------ al terminar un paso

    @Nested
    @DisplayName("Al terminar un paso")
    class AlTerminarPaso {

        PasoContexto paso(String tipo, String faseEstado) {
            return new PasoContexto(UUID.randomUUID(), tipo, "Un paso", "HECHO", FASE, 2, "Construcción", faseEstado, PROYECTO);
        }

        @Test
        @DisplayName("Un ajuste o un alcance resuelto cierra su solicitud")
        void resuelveLaSolicitud() {
            PasoContexto ajuste = paso("AJUSTE", "EN_CURSO");
            servicio.alTerminarPaso(proyecto, ajuste);
            PasoContexto alcance = paso("ALCANCE", "EN_CURSO");
            servicio.alTerminarPaso(proyecto, alcance);

            verify(datos).resolverSolicitudDelPaso(ajuste.pasoId());
            verify(datos).resolverSolicitudDelPaso(alcance.pasoId());
        }

        @Test
        @DisplayName("Un paso normal no cambia nada")
        void pasoNormal() {
            servicio.alTerminarPaso(proyecto, paso("PASO", "EN_CURSO"));

            verify(datos, never()).resolverSolicitudDelPaso(any());
            verify(datos, never()).volverAAprobada(any());
        }

        @Test
        @DisplayName("La última corrección de una fase en corrección la devuelve a APROBADA (vuelve a sumar, RN-16)")
        void ultimaCorreccion() {
            when(datos.pendientes(FASE)).thenReturn(new int[] { 0, 0 });

            servicio.alTerminarPaso(proyecto, paso("CORRECCION", "EN_CORRECCION"));

            verify(datos).volverAAprobada(FASE);
        }

        @Test
        @DisplayName("Si todavía quedan correcciones abiertas, la fase sigue en corrección")
        void quedanCorrecciones() {
            when(datos.pendientes(FASE)).thenReturn(new int[] { 1, 0 });

            servicio.alTerminarPaso(proyecto, paso("CORRECCION", "EN_CORRECCION"));

            verify(datos, never()).volverAAprobada(any());
        }

        @Test
        @DisplayName("En garantía cumplida, la última corrección finaliza el proyecto (RN-13)")
        void finalizaLaGarantia() {
            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY);
            when(datos.correccionesAbiertasDelProyecto(PROYECTO)).thenReturn(0);

            servicio.alTerminarPaso(proyecto, paso("CORRECCION", "APROBADA"));

            verify(proyectos).finalizar(PROYECTO, AHORA);
        }

        @Test
        @DisplayName("En garantía vigente (aún no se cumplen los 30 días) no finaliza")
        void garantiaVigente() {
            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY.plusDays(5));

            servicio.alTerminarPaso(proyecto, paso("CORRECCION", "APROBADA"));

            verify(proyectos, never()).finalizar(any(), any());
        }
    }

    // ------------------------------------------------------------------------------------------------------ tareas programadas

    @Nested
    @DisplayName("Tareas programadas (RN-17, RN-13)")
    class Tareas {

        FaseEnRevision enRevision(LocalDate plazo, boolean recordada, Instant entregadaAt) {
            return new FaseEnRevision(FASE, PROYECTO, 2, "Construcción", entregadaAt, plazo, recordada);
        }

        @Test
        @DisplayName("A los 2 días hábiles de la entrega recuerda al cliente, una sola vez")
        void recuerda() {
            when(calendario.diasHabilesEntre(HOY.minusDays(2), HOY)).thenReturn(2);
            FaseEnRevision fase = enRevision(HOY.plusDays(1), false, AHORA.minusSeconds(2 * 86400));

            assertThat(servicio.recordarRevision(fase)).isTrue();

            verify(datos).marcarRecordada(FASE);
            assertThat(avisosPublicados().get(0).tipo()).isEqualTo("FASE_RECORDATORIO");
        }

        @Test
        @DisplayName("Con 1 día hábil o si ya se recordó, no recuerda")
        void noRecuerda() {
            when(calendario.diasHabilesEntre(any(), any())).thenReturn(1);
            assertThat(servicio.recordarRevision(enRevision(HOY.plusDays(2), false, AHORA.minusSeconds(86400)))).isFalse();

            when(calendario.diasHabilesEntre(any(), any())).thenReturn(5);
            assertThat(servicio.recordarRevision(enRevision(HOY.plusDays(2), true, AHORA.minusSeconds(86400)))).isFalse();
            verify(datos, never()).marcarRecordada(any());
        }

        @Test
        @DisplayName("Vencido el plazo, la fase se aprueba sola y queda marcada como automática (sin aprobador)")
        void apruebaPorVencimiento() {
            FaseEnRevision fase = enRevision(HOY.minusDays(1), true, AHORA.minusSeconds(5 * 86400));
            when(datos.siguientePendiente(PROYECTO)).thenReturn(Optional.of(new FaseFila(SIGUIENTE, 3, "Entrega", null, true, "PENDIENTE", null, null, null, 0, false, null)));

            assertThat(servicio.aprobarPorVencimiento(fase)).isTrue();

            verify(datos).aprobar(eq(FASE), eq(AHORA), isNull(), eq(true));
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), eq("La fase se aprobó sola por falta de respuesta"), eq("proyecto"), eq("P-0031"), anyMap());
            assertThat(avisosPublicados()).extracting(AvisoSolicitado::tipo).containsOnly("FASE_APROBADA_AUTO");
            assertThat(avisosPublicados()).hasSize(2);
        }

        @Test
        @DisplayName("Valor límite: el último día del plazo todavía no se aprueba sola; al día siguiente sí")
        void ultimoDiaDelPlazo() {
            assertThat(servicio.aprobarPorVencimiento(enRevision(HOY, true, AHORA.minusSeconds(3 * 86400)))).isFalse();
            verify(datos, never()).aprobar(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Un proyecto que ya no está ACTIVO (pausado) no aprueba solo, y si otro la aprobó antes tampoco")
        void noApruebaSiNoCorresponde() {
            proyecto = proyecto("PAUSADO", "APROBADO", null);
            assertThat(servicio.aprobarPorVencimiento(enRevision(HOY.minusDays(1), true, AHORA))).isFalse();

            proyecto = proyecto("ACTIVO", "APROBADO", null);
            when(datos.aprobar(eq(FASE), any(), any(), anyBoolean())).thenReturn(false);
            assertThat(servicio.aprobarPorVencimiento(enRevision(HOY.minusDays(1), true, AHORA))).isFalse();
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Cerrar la garantía: cumplida y sin correcciones abiertas finaliza; con una abierta, no")
        void cerrarGarantia() {
            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY.minusDays(1));
            when(datos.correccionesAbiertasDelProyecto(PROYECTO)).thenReturn(1);
            assertThat(servicio.cerrarGarantia(PROYECTO)).isFalse();
            verify(proyectos, never()).finalizar(any(), any());

            when(datos.correccionesAbiertasDelProyecto(PROYECTO)).thenReturn(0);
            assertThat(servicio.cerrarGarantia(PROYECTO)).isTrue();
            verify(proyectos, times(1)).finalizar(PROYECTO, AHORA);
        }

        @Test
        @DisplayName("Cerrar la garantía antes de la fecha no hace nada")
        void garantiaNoCumplida() {
            proyecto = proyecto("EN_GARANTIA", "APROBADO", HOY.plusDays(1));

            assertThat(servicio.cerrarGarantia(PROYECTO)).isFalse();
        }
    }
}
