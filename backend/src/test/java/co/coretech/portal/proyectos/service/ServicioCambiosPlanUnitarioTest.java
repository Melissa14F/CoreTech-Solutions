package co.coretech.portal.proyectos.service;

import co.coretech.portal.proyectos.domain.EstadoCambioPlan;

import co.coretech.portal.proyectos.domain.TipoCambioPlan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
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
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.PasoNuevo;
import co.coretech.portal.proyectos.service.ServicioCambiosPlan.Propuesta;
import co.coretech.portal.proyectos.repository.CambioPlanRepositorio;
import co.coretech.portal.proyectos.repository.CambiosPlanJdbc;
import co.coretech.portal.proyectos.repository.ProyectoRepositorio;
import co.coretech.portal.proyectos.repository.CambiosPlanJdbc.AlcancePorCotizar;
import co.coretech.portal.proyectos.repository.CambiosPlanJdbc.CambioFila;
import co.coretech.portal.proyectos.repository.CambiosPlanJdbc.FaseDelPlan;
import co.coretech.portal.proyectos.repository.CambiosPlanJdbc.PasoDelPlan;
import co.coretech.portal.proyectos.repository.ProyectosJdbc;
import co.coretech.portal.proyectos.domain.PasoPlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Pruebas UNITARIAS de {@link ServicioCambiosPlan} (HU-30; RN-28, RN-30, D-32, D-33): proponer, autorizar, rechazar y aceptar
 * los cinco tipos de cambio de un plan aprobado (agregar o quitar fase, cambiar la fecha, quitar un paso y cotizar un alcance
 * nuevo), con quién puede hacer qué. El acceso a datos y los demás módulos van simulados; no hay base de datos. Reloj fijo:
 * 2027-03-02.
 * <p>
 * El plan de prueba tiene 4 fases: 1 aprobada, 2 en curso (la aprueba el cliente), 3 pendiente (la cierra el equipo) y 4
 * pendiente (la aprueba el cliente). Así la fase nueva solo puede ir en las posiciones 3 a 5 (después de la fase actual).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioCambiosPlanUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");
    static final LocalDate HOY = LocalDate.of(2027, 3, 2);
    static final LocalDate INICIO = HOY.minusDays(30);
    static final LocalDate ENTREGA = HOY.plusDays(40);

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final UUID F1 = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    static final UUID F2 = UUID.fromString("00000000-0000-4000-8000-0000000000f2");
    static final UUID F3 = UUID.fromString("00000000-0000-4000-8000-0000000000f3");
    static final UUID F4 = UUID.fromString("00000000-0000-4000-8000-0000000000f4");
    static final UUID PASO = UUID.fromString("00000000-0000-4000-8000-000000000051");
    static final UUID SOLICITUD = UUID.fromString("00000000-0000-4000-8000-000000000061");
    static final UUID CAMBIO = UUID.fromString("00000000-0000-4000-8000-000000000071");
    static final String MOTIVO = "El cliente necesita este cambio por una nueva necesidad del negocio.";

    @Mock
    CambiosPlanJdbc datos;
    @Mock
    CambioPlanRepositorio cambiosJpa;
    @Mock
    ProyectoRepositorio proyectosJpa;
    @Mock
    ProyectosJdbc proyectos;
    @Mock
    EquipoConsultas equipos;
    @Mock
    DirectorioUsuarios usuarios;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;

    ServicioCambiosPlan servicio;
    ProyectoFila proyecto;
    List<FaseDelPlan> fases;

    @BeforeEach
    void preparaLoComun() {
        proyecto = proyecto("ACTIVO", "APROBADO");
        fases = List.of(new FaseDelPlan(F1, 1, "Diseño", "APROBADA", false), new FaseDelPlan(F2, 2, "Construcción", "EN_CURSO", true),
                new FaseDelPlan(F3, 3, "Pruebas", "PENDIENTE", false), new FaseDelPlan(F4, 4, "Entrega", "PENDIENTE", true));
        when(proyectos.porCodigo(eq("P-0031"), eq(HOY))).thenAnswer(i -> Optional.of(proyecto));
        when(cambiosJpa.hayCambioDePlanAbierto(PROYECTO)).thenReturn(false);
        when(datos.fases(PROYECTO)).thenAnswer(i -> fases);
        when(cambiosJpa.insertar(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(CAMBIO);
        when(cambiosJpa.autorizar(any(), any(), any(), any())).thenReturn(true);
        when(cambiosJpa.rechazarComoAdministrador(any(), any())).thenReturn(true);
        when(cambiosJpa.rechazarComoCliente(any(), any(), any())).thenReturn(true);
        when(cambiosJpa.aceptar(any(), any())).thenReturn(true);
        when(datos.valorCotizado(PROYECTO)).thenReturn(new BigDecimal("5000000"));
        when(datos.valorAdicionalAceptado(PROYECTO)).thenReturn(new BigDecimal("500000"));
        when(datos.avance(PROYECTO)).thenReturn(new BigDecimal("25.00"));
        when(datos.alcancesPorCotizar(PROYECTO)).thenReturn(List.of(new AlcancePorCotizar(SOLICITUD, "Construcción", "Quiero un reporte semanal.", AHORA)));
        when(datos.paso(PASO)).thenReturn(Optional.of(new PasoDelPlan(PASO, PROYECTO, F3, "Pruebas", "PENDIENTE", false, "PASO", "PENDIENTE", "Probar el reporte", 2)));
        when(datos.faseActual(PROYECTO)).thenReturn(Optional.of(fases.get(1)));
        when(equipos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
        when(equipos.equipoDelProductOwner(OTRO_PO)).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipos.productOwner(EQUIPO)).thenReturn(Optional.of(new ProductOwner(PO, "Andrés Pérez", "andres@coretechsolutions.co")));
        when(usuarios.administradoresActivos()).thenReturn(List.of(ADMIN));
        comoProductOwner();
        servicio = new ServicioCambiosPlan(datos, cambiosJpa, proyectosJpa, proyectos, equipos, usuarios, auditoria, sesion, eventos, new ObjectMapper(), Clock.fixed(AHORA, COLOMBIA));
    }

    // ---- ayudas ----

    static ProyectoFila proyecto(String estado, String estadoPlan) {
        return new ProyectoFila(PROYECTO, "P-0031", "Inventario del salón", "Un sistema para llevar el inventario.", UUID.randomUUID(), CLIENTE, UUID.randomUUID(),
                EQUIPO, INICIO, ENTREGA, estado, estadoPlan, new BigDecimal("5000000"), null, null, new BigDecimal("25.00"), null, null, null, AHORA, AHORA, 4, 2,
                "Construcción", null, null);
    }

    void comoProductOwner() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(PO, "Andrés", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar", "proyectos.registrar_avance"), false, 0));
    }

    void comoAdministrador() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of("proyectos.gestionar", "proyectos.planificar"), false, 0));
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura", "laura@correo.com", "CLIENTE", Set.of("proyectos.ver_propios"), false, 0));
    }

    static Propuesta fecha(String nuevaEntrega, BigDecimal valor, String motivo) {
        return new Propuesta("CAMBIAR_FECHA", null, null, null, null, null, null, null, null, null, motivo, nuevaEntrega, valor);
    }

    static Propuesta fase(String nombre, Integer posicion, List<PasoNuevo> pasos) {
        return new Propuesta("AGREGAR_FASE", nombre, posicion, true, pasos, null, null, null, null, null, MOTIVO, ENTREGA.plusDays(10).toString(), new BigDecimal("800000"));
    }

    static List<PasoNuevo> unPaso() {
        return List.of(new PasoNuevo("Probar con el cliente", null));
    }

    static CambioFila fila(String tipo, String estado) {
        return fila(tipo, estado, false, null, null, null, null, ENTREGA.plusDays(10), new BigDecimal("800000"));
    }

    static CambioFila fila(String tipo, String estado, boolean pasoFaseAprueba, Integer posicion, UUID faseId, UUID pasoId, UUID solicitudId, LocalDate nuevaEntrega, BigDecimal valor) {
        return new CambioFila(CAMBIO, PROYECTO, "P-0031", "Inventario del salón", CLIENTE, "Laura Gómez", EQUIPO, ENTREGA, tipo, tipo.equals("AGREGAR_FASE") ? "Pruebas finales" : null,
                posicion, true, tipo.equals("AGREGAR_FASE") ? "[{\"titulo\":\"Probar con el cliente\",\"explicacion\":null}]" : null, faseId, faseId == null ? null : "Entrega", MOTIVO,
                pasoId, pasoId == null ? null : "Probar el reporte", pasoId == null ? null : "Pruebas", pasoFaseAprueba, nuevaEntrega, valor, estado, PO, "Andrés Pérez", AHORA,
                null, null, null, solicitudId, solicitudId == null ? null : "Reporte semanal", solicitudId == null ? null : "Un reporte semanal con el total de ventas del salón.", null);
    }

    List<AvisoSolicitado> avisos() {
        ArgumentCaptor<AvisoSolicitado> c = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(c.capture());
        return c.getAllValues();
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    // ------------------------------------------------------------------------------------------------ proponer

    @Nested
    @DisplayName("Proponer un cambio (HU-30)")
    class Proponer {

        @Test
        @DisplayName("Cambiar la fecha: queda PROPUESTO, se audita y se avisa a los administradores; el plan no cambia")
        void cambiaLaFecha() {
            var r = servicio.proponer("P-0031", fecha(ENTREGA.plusDays(10).toString(), new BigDecimal("800000"), MOTIVO));

            assertThat(r.estado()).isEqualTo("PROPUESTO");
            assertThat(r.fasesAntes()).isEqualTo(4);
            assertThat(r.fasesDespues()).isEqualTo(4);
            verify(cambiosJpa).insertar(eq(PROYECTO), eq(TipoCambioPlan.CAMBIAR_FECHA), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), eq(MOTIVO), eq(ENTREGA.plusDays(10)),
                    eq(new BigDecimal("800000")), eq(PO), eq(AHORA));
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Propuso un cambio de plan"), eq("proyecto"), eq("P-0031"), anyMap());
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(ADMIN);
            assertThat(aviso.tipo()).isEqualTo("CAMBIO_PLAN_PROPUESTO");
        }

        @Test
        @DisplayName("Un proyecto solo tiene un cambio abierto a la vez (409), y solo se cambia el plan de un proyecto activo con el plan aprobado (409)")
        void estadoYAbierto() {
            when(cambiosJpa.hayCambioDePlanAbierto(PROYECTO)).thenReturn(true);
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ENTREGA.plusDays(10).toString(), BigDecimal.ZERO, MOTIVO))).hasMessage(ServicioCambiosPlan.YA_HAY_ABIERTO);

            when(cambiosJpa.hayCambioDePlanAbierto(PROYECTO)).thenReturn(false);
            for (ProyectoFila p : List.of(proyecto("PAUSADO", "APROBADO"), proyecto("ACTIVO", "ENVIADO"), proyecto("EN_GARANTIA", "APROBADO"))) {
                proyecto = p;
                assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ENTREGA.plusDays(10).toString(), BigDecimal.ZERO, MOTIVO)))
                        .isInstanceOf(ConflictoException.class).hasMessage(ServicioCambiosPlan.ESTADO_NO_ADMITE);
            }
            verify(cambiosJpa, never()).insertar(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Solo el Product Owner del equipo propone: el de otro equipo recibe 403, el administrador 403 y un cliente 404; todos quedan en la auditoría (RN-10)")
        void permisos() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar"), false, 0));
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ENTREGA.plusDays(10).toString(), BigDecimal.ZERO, MOTIVO))).isInstanceOf(ProhibidoException.class).hasMessage(ServicioCambiosPlan.NO_ES_DE_TU_EQUIPO);

            comoAdministrador();
            when(equipos.equipoDelProductOwner(ADMIN)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ENTREGA.plusDays(10).toString(), BigDecimal.ZERO, MOTIVO))).isInstanceOf(ProhibidoException.class).hasMessage(ServicioCambiosPlan.SOLO_PRODUCT_OWNER);

            comoCliente(CLIENTE);
            when(equipos.equipoDelProductOwner(CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ENTREGA.plusDays(10).toString(), BigDecimal.ZERO, MOTIVO))).isInstanceOf(NoEncontradoException.class);

            verify(auditoria, org.mockito.Mockito.times(3)).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("Un tipo que no existe da 422 solo en el campo tipo")
        void tipoDesconocido() {
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("INVENTADO", null, null, null, null, null, null, null, null, null, MOTIVO, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("tipo"));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta(null, null, null, null, null, null, null, null, null, null, MOTIVO, null, null))).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("Valores límite del motivo: 19 caracteres no, 20 sí, 500 sí, 501 no")
        void limitesDelMotivo() {
            String entrega = ENTREGA.plusDays(10).toString();
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(entrega, BigDecimal.ZERO, "x".repeat(19))))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("motivo"));
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(entrega, BigDecimal.ZERO, "x".repeat(501)))).isInstanceOf(ValidacionException.class);
            assertThat(servicio.proponer("P-0031", fecha(entrega, BigDecimal.ZERO, "x".repeat(20))).estado()).isEqualTo("PROPUESTO");
            assertThat(servicio.proponer("P-0031", fecha(entrega, BigDecimal.ZERO, "x".repeat(500))).estado()).isEqualTo("PROPUESTO");
        }

        @Test
        @DisplayName("Valores límite del valor adicional: $0 y $200.000.000 pasan; negativo, con decimales o $200.000.001 no; sin valor no")
        void limitesDelValor() {
            String entrega = ENTREGA.plusDays(10).toString();
            servicio.proponer("P-0031", fecha(entrega, BigDecimal.ZERO, MOTIVO));
            servicio.proponer("P-0031", fecha(entrega, new BigDecimal("200000000"), MOTIVO));
            for (String malo : List.of("-1", "1000.5", "200000001")) {
                assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(entrega, new BigDecimal(malo), MOTIVO)))
                        .as("valor " + malo).isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("valorAdicional"));
            }
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(entrega, null, MOTIVO))).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("La nueva entrega: no puede ser igual a la actual, ni a menos de 14 días del inicio, ni pasar de 5 años desde hoy, ni estar mal escrita")
        void validaLaEntrega() {
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ENTREGA.toString(), BigDecimal.ZERO, MOTIVO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo(ServicioCambiosPlan.FECHA_DISTINTA));
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(INICIO.plusDays(13).toString(), BigDecimal.ZERO, MOTIVO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("14 días después del inicio"));
            // El tope cuenta desde hoy (5 años): con el reloj en 2027-03-02 el último día es el 2 de marzo de 2032
            LocalDate ultimo = LocalDate.of(2032, 3, 2);
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(ultimo.plusDays(1).toString(), BigDecimal.ZERO, MOTIVO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige una fecha de entrega hasta el 2 de marzo de 2032."));
            assertThat(servicio.proponer("P-0031", fecha(ultimo.toString(), BigDecimal.ZERO, MOTIVO)).estado()).as("el último día permitido").isEqualTo("PROPUESTO");
            assertThat(servicio.proponer("P-0031", fecha("2029-06-01", BigDecimal.ZERO, MOTIVO)).estado()).as("una fecha de 2029 ya es válida").isEqualTo("PROPUESTO");
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha("pronto", BigDecimal.ZERO, MOTIVO))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.proponer("P-0031", fecha(null, BigDecimal.ZERO, MOTIVO))).isInstanceOf(ValidacionException.class);
            assertThat(servicio.proponer("P-0031", fecha(INICIO.plusDays(14).toString(), BigDecimal.ZERO, MOTIVO)).estado()).as("justo 14 días").isEqualTo("PROPUESTO");
        }

        @Test
        @DisplayName("Agregar una fase en la posición 3 (después de la fase actual): el plan pasa de 4 a 5 fases y el avance se recalcula")
        void agregaUnaFase() {
            var r = servicio.proponer("P-0031", fase("Pruebas finales", 3, unPaso()));

            assertThat(r.fasesAntes()).isEqualTo(4);
            assertThat(r.fasesDespues()).isEqualTo(5);
            assertThat(r.avanceDespues()).as("1 aprobada de 5 fases").isEqualByComparingTo("20.00");
            ArgumentCaptor<String> pasosJson = ArgumentCaptor.forClass(String.class);
            verify(cambiosJpa).insertar(eq(PROYECTO), eq(TipoCambioPlan.AGREGAR_FASE), eq("Pruebas finales"), eq(3), eq(true), pasosJson.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertThat(pasosJson.getValue()).contains("Probar con el cliente");
        }

        @Test
        @DisplayName("La fase nueva va después de la fase actual: las posiciones 1 y 2 (ya empezadas) y la 6 (más allá del final) se rechazan; la 5 (al final) sí")
        void posicionDeLaFase() {
            for (int mala : new int[] { 1, 2, 6 }) {
                assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Pruebas finales", mala, unPaso())))
                        .as("posición " + mala).isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("posicion"));
            }
            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Pruebas finales", null, unPaso()))).isInstanceOf(ValidacionException.class);
            assertThat(servicio.proponer("P-0031", fase("Pruebas finales", 5, unPaso())).fasesDespues()).isEqualTo(5);
        }

        @Test
        @DisplayName("Una fase nueva sin pasos, con el nombre repetido (sin distinguir mayúsculas) o con nombre de 2 caracteres, da 422 por campo")
        void validaLaFaseNueva() {
            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Pruebas finales", 3, List.of())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("pasos"));
            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("construcción", 3, unPaso())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Ya hay una fase con ese nombre en el plan"));
            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Ab", 3, unPaso()))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Pruebas finales", 3, List.of(new PasoNuevo("ab", null))))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Pruebas finales", 3, List.of(new PasoNuevo("Probar", "x".repeat(161)))))).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("Con 8 fases ya no caben más: «El plan puede tener máximo 8 fases»")
        void maximoDeFases() {
            fases = List.of(new FaseDelPlan(F1, 1, "F1", "APROBADA", false), new FaseDelPlan(F2, 2, "F2", "EN_CURSO", true), new FaseDelPlan(UUID.randomUUID(), 3, "F3", "PENDIENTE", false),
                    new FaseDelPlan(UUID.randomUUID(), 4, "F4", "PENDIENTE", false), new FaseDelPlan(UUID.randomUUID(), 5, "F5", "PENDIENTE", false), new FaseDelPlan(UUID.randomUUID(), 6, "F6", "PENDIENTE", false),
                    new FaseDelPlan(UUID.randomUUID(), 7, "F7", "PENDIENTE", false), new FaseDelPlan(UUID.randomUUID(), 8, "F8", "PENDIENTE", true));

            assertThatThrownBy(() -> servicio.proponer("P-0031", fase("Pruebas finales", 3, unPaso())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas()).anySatisfy(f -> assertThat(f.mensaje()).isEqualTo("El plan puede tener máximo 8 fases")));
        }

        @Test
        @DisplayName("Quitar una fase pendiente: el plan pasa de 4 a 3 fases")
        void quitaUnaFase() {
            var r = servicio.proponer("P-0031", new Propuesta("QUITAR_FASE", null, null, null, null, F3, null, null, null, null, MOTIVO, ENTREGA.minusDays(5).toString(), BigDecimal.ZERO));

            assertThat(r.fasesDespues()).isEqualTo(3);
            verify(cambiosJpa).insertar(eq(PROYECTO), eq(TipoCambioPlan.QUITAR_FASE), any(), any(), anyBoolean(), any(), eq(F3), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Una fase aprobada o en curso no se quita (409); una que no existe da 422 en faseId")
        void soloFasesPendientes() {
            for (UUID id : List.of(F1, F2)) {
                assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_FASE", null, null, null, null, id, null, null, null, null, MOTIVO, ENTREGA.toString().replace("-0", "-0"), BigDecimal.ZERO)))
                        .isInstanceOf(ConflictoException.class).hasMessage(ServicioCambiosPlan.SOLO_FASE_PENDIENTE);
            }
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_FASE", null, null, null, null, UUID.randomUUID(), null, null, null, null, MOTIVO, ENTREGA.minusDays(5).toString(), BigDecimal.ZERO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).contains("faseId"));
        }

        @Test
        @DisplayName("Quitar una fase no puede dejar el plan sin fase que apruebe el cliente ni con menos de 2 fases (RN-28)")
        void noRompeElPlan() {
            fases = List.of(new FaseDelPlan(F1, 1, "Diseño", "APROBADA", false), new FaseDelPlan(F2, 2, "Construcción", "EN_CURSO", false),
                    new FaseDelPlan(F3, 3, "Entrega", "PENDIENTE", true), new FaseDelPlan(F4, 4, "Cierre", "PENDIENTE", false));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_FASE", null, null, null, null, F3, null, null, null, null, MOTIVO, ENTREGA.minusDays(5).toString(), BigDecimal.ZERO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Marca al menos una fase que apruebe el cliente"));

            fases = List.of(new FaseDelPlan(F1, 1, "Diseño", "APROBADA", true), new FaseDelPlan(F2, 2, "Entrega", "PENDIENTE", true));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_FASE", null, null, null, null, F2, null, null, null, null, MOTIVO, ENTREGA.minusDays(5).toString(), BigDecimal.ZERO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("El plan necesita al menos 2 fases"));
        }

        @Test
        @DisplayName("Quitar un paso pendiente: no lleva fecha ni valor y se guarda con valor 0")
        void quitaUnPaso() {
            var r = servicio.proponer("P-0031", new Propuesta("QUITAR_PASO", null, null, null, null, null, PASO, null, null, null, MOTIVO, null, null));

            assertThat(r.estado()).isEqualTo("PROPUESTO");
            verify(cambiosJpa).insertar(eq(PROYECTO), eq(TipoCambioPlan.QUITAR_PASO), any(), any(), anyBoolean(), any(), any(), eq(PASO), any(), any(), any(), any(), eq(null), eq(BigDecimal.ZERO), any(), any());
        }

        @Test
        @DisplayName("Un paso que ya empezó, una corrección o uno de una fase aprobada no se quita (409); el último paso de la fase tampoco; uno de otro proyecto o inexistente, 422")
        void quitarPasoInvalido() {
            when(datos.paso(PASO)).thenReturn(Optional.of(new PasoDelPlan(PASO, PROYECTO, F3, "Pruebas", "PENDIENTE", false, "PASO", "EN_CURSO", "Probar", 2)));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_PASO", null, null, null, null, null, PASO, null, null, null, MOTIVO, null, null))).hasMessage(ServicioCambiosPlan.SOLO_PASO_SIN_EMPEZAR);

            when(datos.paso(PASO)).thenReturn(Optional.of(new PasoDelPlan(PASO, PROYECTO, F1, "Diseño", "APROBADA", false, "PASO", "PENDIENTE", "Probar", 2)));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_PASO", null, null, null, null, null, PASO, null, null, null, MOTIVO, null, null))).isInstanceOf(ConflictoException.class);

            when(datos.paso(PASO)).thenReturn(Optional.of(new PasoDelPlan(PASO, PROYECTO, F3, "Pruebas", "PENDIENTE", false, "CORRECCION", "PENDIENTE", "Probar", 2)));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_PASO", null, null, null, null, null, PASO, null, null, null, MOTIVO, null, null))).isInstanceOf(ConflictoException.class);

            when(datos.paso(PASO)).thenReturn(Optional.of(new PasoDelPlan(PASO, PROYECTO, F3, "Pruebas", "PENDIENTE", false, "PASO", "PENDIENTE", "Probar", 0)));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_PASO", null, null, null, null, null, PASO, null, null, null, MOTIVO, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo(ServicioCambiosPlan.FASE_CONSERVA_UN_PASO));

            when(datos.paso(PASO)).thenReturn(Optional.of(new PasoDelPlan(PASO, UUID.randomUUID(), F3, "Pruebas", "PENDIENTE", false, "PASO", "PENDIENTE", "Probar", 2)));
            assertThatThrownBy(() -> servicio.proponer("P-0031", new Propuesta("QUITAR_PASO", null, null, null, null, null, PASO, null, null, null, MOTIVO, null, null))).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("Cotizar algo nuevo (AGREGAR_ALCANCE): enlaza la solicitud del cliente y valida el título (3 a 80) y la explicación del paso (20 a 400)")
        void cotizaAlcance() {
            Propuesta valida = new Propuesta("AGREGAR_ALCANCE", null, null, null, null, null, null, SOLICITUD, "Reporte semanal", "Un reporte semanal con el total de ventas del salón.", MOTIVO,
                    ENTREGA.plusDays(7).toString(), new BigDecimal("600000"));

            assertThat(servicio.proponer("P-0031", valida).estado()).isEqualTo("PROPUESTO");
            verify(cambiosJpa).insertar(eq(PROYECTO), eq(TipoCambioPlan.AGREGAR_ALCANCE), any(), any(), anyBoolean(), any(), any(), any(), eq(SOLICITUD), eq("Reporte semanal"), any(), any(), any(), any(), any(), any());

            Propuesta tituloCorto = new Propuesta("AGREGAR_ALCANCE", null, null, null, null, null, null, SOLICITUD, "ab", "Un reporte semanal con el total de ventas del salón.", MOTIVO, ENTREGA.plusDays(7).toString(), BigDecimal.ZERO);
            assertThatThrownBy(() -> servicio.proponer("P-0031", tituloCorto)).isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("tituloPaso"));
            Propuesta explicacionCorta = new Propuesta("AGREGAR_ALCANCE", null, null, null, null, null, null, SOLICITUD, "Reporte", "x".repeat(19), MOTIVO, ENTREGA.plusDays(7).toString(), BigDecimal.ZERO);
            assertThatThrownBy(() -> servicio.proponer("P-0031", explicacionCorta)).isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("explicacionPaso"));
            Propuesta ajena = new Propuesta("AGREGAR_ALCANCE", null, null, null, null, null, null, UUID.randomUUID(), "Reporte", "x".repeat(20), MOTIVO, ENTREGA.plusDays(7).toString(), BigDecimal.ZERO);
            assertThatThrownBy(() -> servicio.proponer("P-0031", ajena)).isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("solicitudCambioId"));
        }
    }

    // ------------------------------------------------------------------------------------------------ autorizar

    @Nested
    @DisplayName("Autorizar (el administrador)")
    class Autorizar {

        @BeforeEach
        void comoAdmin() {
            comoAdministrador();
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "PROPUESTO")));
        }

        @Test
        @DisplayName("Un cambio propuesto queda AUTORIZADO y se avisa al cliente (que debe aceptarlo) y al equipo; todavía no se aplica")
        void autoriza() {
            var r = servicio.autorizar(CAMBIO);

            assertThat(r.estado()).isEqualTo("AUTORIZADO");
            verify(cambiosJpa).autorizar(CAMBIO, EstadoCambioPlan.AUTORIZADO, ADMIN, AHORA);
            verify(proyectosJpa, never()).actualizarEntrega(any(), any());
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsExactly("CAMBIO_PLAN_AUTORIZADO", "CAMBIO_PLAN_AUTORIZADO");
            assertThat(avisos().get(0).destinatarios()).containsExactly(CLIENTE);
            assertThat(avisos().get(1).destinatarios()).containsExactly(PO);
        }

        @Test
        @DisplayName("Quitar un paso de una fase que cierra el equipo se aplica al autorizarlo (queda ACEPTADO) y solo se avisa al cliente")
        void quitarPasoSeAplicaAlAutorizar() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("QUITAR_PASO", "PROPUESTO", false, null, null, PASO, null, null, BigDecimal.ZERO)));

            var r = servicio.autorizar(CAMBIO);

            assertThat(r.estado()).isEqualTo("ACEPTADO");
            verify(cambiosJpa).autorizar(CAMBIO, EstadoCambioPlan.ACEPTADO, ADMIN, AHORA);
            verify(datos).quitarPaso(PASO);
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsOnly("CAMBIO_PLAN_APLICADO");
        }

        @Test
        @DisplayName("Quitar un paso de una fase que aprueba el cliente NO se aplica al autorizar: espera su aceptación")
        void quitarPasoDeFaseDelClienteEspera() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("QUITAR_PASO", "PROPUESTO", true, null, null, PASO, null, null, BigDecimal.ZERO)));

            assertThat(servicio.autorizar(CAMBIO).estado()).isEqualTo("AUTORIZADO");
            verify(datos, never()).quitarPaso(any());
        }

        @Test
        @DisplayName("Al autorizar un alcance nuevo la solicitud queda cotizada con el valor y los días adicionales")
        void alcanceQuedaCotizado() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("AGREGAR_ALCANCE", "PROPUESTO", false, null, null, null, SOLICITUD, ENTREGA.plusDays(10), new BigDecimal("600000"))));

            servicio.autorizar(CAMBIO);

            verify(datos).cotizarSolicitud(SOLICITUD, new BigDecimal("600000"), 10);
        }

        @Test
        @DisplayName("Solo la administración autoriza: el Product Owner y el cliente reciben 403 y queda en la auditoría")
        void soloElAdministrador() {
            comoProductOwner();
            assertThatThrownBy(() -> servicio.autorizar(CAMBIO)).isInstanceOf(ProhibidoException.class).hasMessageContaining("administración");
            comoCliente(CLIENTE);
            assertThatThrownBy(() -> servicio.autorizar(CAMBIO)).isInstanceOf(ProhibidoException.class);

            verify(auditoria, org.mockito.Mockito.times(2)).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(cambiosJpa, never()).autorizar(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Un cambio ya respondido da 409; con el plan que ya no está aprobado, 409; uno que no existe, 404")
        void estados() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "AUTORIZADO")));
            assertThatThrownBy(() -> servicio.autorizar(CAMBIO)).hasMessage(ServicioCambiosPlan.YA_RESPONDIDO);

            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "PROPUESTO")));
            proyecto = proyecto("PAUSADO", "APROBADO");
            assertThatThrownBy(() -> servicio.autorizar(CAMBIO)).hasMessage(ServicioCambiosPlan.ESTADO_NO_ADMITE);

            when(datos.porId(CAMBIO)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.autorizar(CAMBIO)).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioCambiosPlan.CAMBIO_NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Si dos acciones llegan a la vez (el UPDATE no toca filas): 409 «El proyecto cambió»")
        void doblePeticion() {
            when(cambiosJpa.autorizar(any(), any(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> servicio.autorizar(CAMBIO)).hasMessage(ServicioCambiosPlan.DATOS_DESACTUALIZADOS);
        }
    }

    // ------------------------------------------------------------------------------------------------ rechazar

    @Nested
    @DisplayName("Rechazar un cambio")
    class Rechazar {

        @Test
        @DisplayName("El administrador rechaza uno PROPUESTO con su motivo: queda RECHAZADO_ADMIN y se avisa al equipo")
        void comoAdmin() {
            comoAdministrador();
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "PROPUESTO")));

            var r = servicio.rechazar(CAMBIO, "No hay presupuesto para este cambio ahora.");

            assertThat(r.estado()).isEqualTo("RECHAZADO_ADMIN");
            verify(cambiosJpa).rechazarComoAdministrador(CAMBIO, "No hay presupuesto para este cambio ahora.");
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsExactly("CAMBIO_PLAN_RECHAZADO");
        }

        @Test
        @DisplayName("El cliente rechaza uno AUTORIZADO: RECHAZADO_CLIENTE, avisa al equipo y a la administración; si era un alcance, la solicitud se rechaza")
        void rechazaElCliente() {
            comoCliente(CLIENTE);
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("AGREGAR_ALCANCE", "AUTORIZADO", false, null, null, null, SOLICITUD, ENTREGA.plusDays(10), new BigDecimal("600000"))));

            var r = servicio.rechazar(CAMBIO, "Es más caro de lo que esperaba pagar.");

            assertThat(r.estado()).isEqualTo("RECHAZADO_CLIENTE");
            verify(cambiosJpa).rechazarComoCliente(eq(CAMBIO), eq("Es más caro de lo que esperaba pagar."), eq(AHORA));
            verify(datos).rechazarSolicitud(SOLICITUD);
            assertThat(avisos()).hasSize(2);
        }

        @Test
        @DisplayName("Valores límite del motivo: 9 caracteres no, 10 sí, 400 sí, 401 no")
        void limitesDelMotivo() {
            comoAdministrador();
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "PROPUESTO")));

            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, "x".repeat(9))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, "x".repeat(401))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, "  ")).isInstanceOf(ValidacionException.class);
            assertThat(servicio.rechazar(CAMBIO, "x".repeat(10)).estado()).isEqualTo("RECHAZADO_ADMIN");
            assertThat(servicio.rechazar(CAMBIO, "x".repeat(400)).estado()).isEqualTo("RECHAZADO_ADMIN");
        }

        @Test
        @DisplayName("Cada quien rechaza en su momento: el administrador solo si está PROPUESTO; el cliente solo si está AUTORIZADO (409)")
        void momentoEquivocado() {
            comoAdministrador();
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "AUTORIZADO")));
            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, MOTIVO)).hasMessage(ServicioCambiosPlan.YA_RESPONDIDO);

            comoCliente(CLIENTE);
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "PROPUESTO")));
            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, MOTIVO)).hasMessage(ServicioCambiosPlan.NO_ESPERA_TU_RESPUESTA);
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404 (RN-10) y el equipo 403; ambos quedan en la auditoría")
        void ajenos() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "AUTORIZADO")));
            comoCliente(OTRO_CLIENTE);
            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, MOTIVO)).isInstanceOf(NoEncontradoException.class);
            comoProductOwner();
            assertThatThrownBy(() -> servicio.rechazar(CAMBIO, MOTIVO)).isInstanceOf(ProhibidoException.class);

            verify(auditoria, org.mockito.Mockito.times(2)).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("proyecto"), eq("P-0031"), anyMap());
        }
    }

    // ------------------------------------------------------------------------------------------------ aceptar

    @Nested
    @DisplayName("Aceptar (el cliente): el cambio se aplica al plan en la misma transacción")
    class Aceptar {

        @BeforeEach
        void comoElCliente() {
            comoCliente(CLIENTE);
            when(datos.fases(PROYECTO)).thenAnswer(i -> fases);
        }

        @Test
        @DisplayName("Cambiar la fecha: se actualiza la entrega, se suma el valor adicional al total y se avisa al equipo y a la administración")
        void aceptaLaFecha() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "AUTORIZADO")));

            var r = servicio.aceptar(CAMBIO);

            assertThat(r.estado()).isEqualTo("ACEPTADO");
            assertThat(r.fechaEstimadaEntrega()).isEqualTo(ENTREGA.plusDays(10));
            assertThat(r.valorTotal()).as("cotizado + adicional aceptado").isEqualByComparingTo("5500000");
            verify(cambiosJpa).aceptar(CAMBIO, AHORA);
            verify(proyectosJpa).actualizarEntrega(PROYECTO, ENTREGA.plusDays(10));
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsExactly("CAMBIO_PLAN_ACEPTADO", "CAMBIO_PLAN_ACEPTADO");
        }

        @Test
        @DisplayName("Agregar una fase: la inserta en su posición con sus pasos, leídos del JSON que se guardó al proponer")
        void agregaLaFase() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("AGREGAR_FASE", "AUTORIZADO", false, 3, null, null, null, ENTREGA.plusDays(10), new BigDecimal("800000"))));

            servicio.aceptar(CAMBIO);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PasoPlantilla>> pasos = ArgumentCaptor.forClass(List.class);
            verify(datos).agregarFase(eq(PROYECTO), eq(CAMBIO), eq(3), eq("Pruebas finales"), eq(true), pasos.capture());
            assertThat(pasos.getValue()).extracting(PasoPlantilla::titulo).containsExactly("Probar con el cliente");
            assertThat(pasos.getValue().get(0).orden()).isEqualTo(1);
        }

        @Test
        @DisplayName("Quitar una fase pendiente: la marca QUITADA; quitar un paso: lo marca QUITADO")
        void quitaFaseYPaso() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("QUITAR_FASE", "AUTORIZADO", false, null, F3, null, null, ENTREGA.minusDays(5), BigDecimal.ZERO)));
            servicio.aceptar(CAMBIO);
            verify(datos).quitarFase(PROYECTO, F3);

            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("QUITAR_PASO", "AUTORIZADO", true, null, null, PASO, null, null, BigDecimal.ZERO)));
            servicio.aceptar(CAMBIO);
            verify(datos).quitarPaso(PASO);
        }

        @Test
        @DisplayName("Agregar un alcance: crea el paso en la fase en curso y deja la solicitud ACEPTADA con ese paso")
        void agregaElAlcance() {
            UUID nuevoPaso = UUID.randomUUID();
            when(datos.agregarPasoDeAlcance(F2, "Reporte semanal", "Un reporte semanal con el total de ventas del salón.")).thenReturn(nuevoPaso);
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("AGREGAR_ALCANCE", "AUTORIZADO", false, null, null, null, SOLICITUD, ENTREGA.plusDays(7), new BigDecimal("600000"))));

            servicio.aceptar(CAMBIO);

            verify(datos).aceptarSolicitud(SOLICITUD, nuevoPaso);
        }

        @Test
        @DisplayName("Si el plan cambió desde la propuesta no se aplica nada y da 409: la fase a quitar ya empezó, quedarían menos de 2 fases o sin fase del cliente")
        void elPlanCambio() {
            fases = List.of(new FaseDelPlan(F1, 1, "Diseño", "APROBADA", false), new FaseDelPlan(F2, 2, "Construcción", "EN_CURSO", true),
                    new FaseDelPlan(F3, 3, "Pruebas", "EN_CURSO", false), new FaseDelPlan(F4, 4, "Entrega", "PENDIENTE", true));
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("QUITAR_FASE", "AUTORIZADO", false, null, F3, null, null, ENTREGA.minusDays(5), BigDecimal.ZERO)));
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).isInstanceOf(ConflictoException.class).hasMessage(ServicioCambiosPlan.PLAN_CAMBIO);

            fases = List.of(new FaseDelPlan(F1, 1, "Diseño", "APROBADA", true), new FaseDelPlan(F3, 2, "Pruebas", "PENDIENTE", false));
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).hasMessage(ServicioCambiosPlan.PLAN_CAMBIO);

            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("AGREGAR_FASE", "AUTORIZADO", false, 9, null, null, null, ENTREGA.plusDays(10), BigDecimal.ZERO)));
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).hasMessage(ServicioCambiosPlan.PLAN_CAMBIO);

            verify(datos, never()).quitarFase(any(), any());
            verify(datos, never()).agregarFase(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("Un cambio que no está AUTORIZADO no se acepta (409); un cliente ajeno recibe 404; el equipo, 403")
        void estadoYPermisos() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "PROPUESTO")));
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).hasMessage(ServicioCambiosPlan.NO_ESPERA_TU_RESPUESTA);

            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "AUTORIZADO")));
            comoCliente(OTRO_CLIENTE);
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).isInstanceOf(NoEncontradoException.class);
            comoProductOwner();
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).isInstanceOf(ProhibidoException.class);
            verify(cambiosJpa, never()).aceptar(any(), any());
        }

        @Test
        @DisplayName("Con el plan que ya no está aprobado, 409; y si el UPDATE no toca filas (otra pestaña se adelantó), 409 sin aplicar")
        void planYCarrera() {
            when(datos.porId(CAMBIO)).thenReturn(Optional.of(fila("CAMBIAR_FECHA", "AUTORIZADO")));
            proyecto = proyecto("ACTIVO", "ENVIADO");
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).hasMessage(ServicioCambiosPlan.ESTADO_NO_ADMITE);

            proyecto = proyecto("ACTIVO", "APROBADO");
            when(cambiosJpa.aceptar(any(), any())).thenReturn(false);
            assertThatThrownBy(() -> servicio.aceptar(CAMBIO)).hasMessage(ServicioCambiosPlan.DATOS_DESACTUALIZADOS);
            verify(proyectosJpa, never()).actualizarEntrega(any(), any());
        }
    }

    // ------------------------------------------------------------------------------------------------ consultar

    @Nested
    @DisplayName("Consultar los cambios")
    class Consultar {

        @Test
        @DisplayName("El Product Owner ve todos, puede proponer si no hay un cambio abierto y ve los alcances por cotizar; con uno abierto ya no puede proponer")
        void productOwner() {
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(fila("CAMBIAR_FECHA", "RECHAZADO_ADMIN")));
            var sinAbierto = servicio.deProyecto("P-0031");
            assertThat(sinAbierto.puedeProponer()).isTrue();
            assertThat(sinAbierto.alcancesPorCotizar()).hasSize(1);
            assertThat(sinAbierto.valorTotal()).isEqualByComparingTo("5500000");

            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(fila("CAMBIAR_FECHA", "PROPUESTO")));
            var conAbierto = servicio.deProyecto("P-0031");
            assertThat(conAbierto.puedeProponer()).isFalse();
            assertThat(conAbierto.hayCambioAbierto()).isTrue();
        }

        @Test
        @DisplayName("El cliente solo ve los que el administrador ya autorizó (nunca los propuestos), no puede proponer y no ve alcances por cotizar")
        void cliente() {
            comoCliente(CLIENTE);
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(fila("CAMBIAR_FECHA", "PROPUESTO"), fila("CAMBIAR_FECHA", "AUTORIZADO"), fila("CAMBIAR_FECHA", "RECHAZADO_ADMIN")));

            var vista = servicio.deProyecto("P-0031");

            assertThat(vista.cambios()).extracting(ServicioCambiosPlan.CambioVista::estado).containsExactly("AUTORIZADO");
            assertThat(vista.cambios().get(0).puedeAceptar()).isTrue();
            assertThat(vista.puedeProponer()).isFalse();
            assertThat(vista.alcancesPorCotizar()).isEmpty();
        }

        @Test
        @DisplayName("El administrador ve todos y puede autorizar los propuestos; un cliente ajeno recibe 404 y el Product Owner de otro equipo 403")
        void administradorYAjenos() {
            comoAdministrador();
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(fila("CAMBIAR_FECHA", "PROPUESTO")));
            assertThat(servicio.deProyecto("P-0031").cambios().get(0).puedeAutorizar()).isTrue();

            comoCliente(OTRO_CLIENTE);
            when(equipos.equipoDelProductOwner(OTRO_CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.deProyecto("P-0031")).isInstanceOf(NoEncontradoException.class);

            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar"), false, 0));
            assertThatThrownBy(() -> servicio.deProyecto("P-0031")).isInstanceOf(ProhibidoException.class);
        }

        @Test
        @DisplayName("El antes y el después de un cambio abierto: agregar una fase reparte el 100 % entre 5 fases (20 % cada una) y suma el valor adicional")
        void antesYDespues() {
            comoAdministrador();
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(fila("AGREGAR_FASE", "PROPUESTO", false, 3, null, null, null, ENTREGA.plusDays(10), new BigDecimal("800000"))));

            var cambio = servicio.deProyecto("P-0031").cambios().get(0);

            assertThat(cambio.antes().fases()).hasSize(4);
            assertThat(cambio.antes().fases().get(0).porcentaje()).isEqualByComparingTo("25.0");
            assertThat(cambio.despues().fases()).hasSize(5);
            assertThat(cambio.despues().fases().get(0).porcentaje()).isEqualByComparingTo("20.0");
            assertThat(cambio.despues().avance()).as("1 aprobada de 5").isEqualByComparingTo("20.00");
            assertThat(cambio.despues().valorTotal()).as("5.500.000 + 800.000").isEqualByComparingTo("6300000");
            assertThat(cambio.despues().entrega()).isEqualTo(ENTREGA.plusDays(10));
            assertThat(cambio.pasosNuevos()).extracting(PasoNuevo::titulo).containsExactly("Probar con el cliente");
        }

        @Test
        @DisplayName("Quitar una fase: el «después» tiene una fase menos; un cambio ya respondido no trae antes ni después")
        void quitarYRespondido() {
            comoAdministrador();
            when(datos.deProyecto(PROYECTO)).thenReturn(List.of(fila("QUITAR_FASE", "PROPUESTO", false, null, F3, null, null, ENTREGA.minusDays(5), BigDecimal.ZERO),
                    fila("CAMBIAR_FECHA", "ACEPTADO")));

            var cambios = servicio.deProyecto("P-0031").cambios();

            assertThat(cambios.get(0).despues().fases()).hasSize(3);
            assertThat(cambios.get(1).antes()).isNull();
            assertThat(cambios.get(1).despues()).isNull();
        }

        @Test
        @DisplayName("porAutorizar: solo los PROPUESTOS; un código mal formado o inexistente al consultar es 404")
        void porAutorizar() {
            comoAdministrador();
            when(datos.porEstado("PROPUESTO")).thenReturn(List.of(fila("CAMBIAR_FECHA", "PROPUESTO")));

            assertThat(servicio.porAutorizar()).hasSize(1);
            assertThatThrownBy(() -> servicio.deProyecto("31")).isInstanceOf(NoEncontradoException.class);
            when(proyectos.porCodigo("P-9999", HOY)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.deProyecto("P-9999")).isInstanceOf(NoEncontradoException.class);
        }
    }
}
