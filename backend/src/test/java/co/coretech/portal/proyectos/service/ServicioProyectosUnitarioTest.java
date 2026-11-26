package co.coretech.portal.proyectos.service;

import co.coretech.portal.proyectos.domain.EstadoProyecto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.Equipo;
import co.coretech.portal.proyectos.CitasParaProyectos;
import co.coretech.portal.proyectos.CitasParaProyectos.CitaOrigen;
import co.coretech.portal.proyectos.service.ServicioProyectos.Creacion;
import co.coretech.portal.proyectos.service.ServicioProyectos.Edicion;
import co.coretech.portal.proyectos.service.ServicioProyectos.Filtros;
import co.coretech.portal.proyectos.repository.PlantillasJdbc;
import co.coretech.portal.proyectos.repository.CambioPlanRepositorio;
import co.coretech.portal.proyectos.repository.ProyectoRepositorio;
import co.coretech.portal.proyectos.repository.ProyectosJdbc;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Pruebas UNITARIAS de {@link ServicioProyectos} (HU-02 y HU-17; RN-04, RN-05, RN-06, RN-27, RN-29): crear un proyecto desde una
 * cita atendida, el cupo del equipo, «Mis proyectos», la lista con filtros y paginación, editar (la fecha de entrega depende del
 * estado del plan, D-33) y pausar, reanudar o cancelar. Todo lo que toca la base o a otro módulo va simulado. Reloj fijo: martes
 * 2027-03-02.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioProyectosUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");
    static final LocalDate HOY = LocalDate.of(2027, 3, 2);

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CITA = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a9");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID OTRO_EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final String ALCANCE = "Un sistema para llevar el inventario del salón de belleza.";

    @Mock
    ProyectosJdbc proyectos;
    @Mock
    ProyectoRepositorio proyectosJpa;
    @Mock
    CambioPlanRepositorio cambiosJpa;
    @Mock
    PlantillasJdbc plantillas;
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

    ServicioProyectos servicio;
    ProyectoFila proyecto;

    @BeforeEach
    void preparaLoComun() {
        proyecto = fila("ACTIVO", "BORRADOR");
        when(proyectos.porCodigo(eq("P-0031"), eq(HOY))).thenAnswer(i -> Optional.of(proyecto));
        when(equipos.activo(EQUIPO)).thenReturn(Optional.of(equipo(EQUIPO, PO, "Andrés Pérez", 1, 3)));
        when(equipos.activo(OTRO_EQUIPO)).thenReturn(Optional.of(equipo(OTRO_EQUIPO, OTRO_PO, "Camila Ruiz", 0, 3)));
        when(equipos.porIds(any())).thenAnswer(i -> {
            java.util.Collection<UUID> ids = i.getArgument(0);
            return java.util.stream.Stream.of(equipo(EQUIPO, PO, "Andrés Pérez", 1, 3), equipo(OTRO_EQUIPO, OTRO_PO, "Camila Ruiz", 0, 3))
                    .filter(e -> ids.contains(e.equipoId())).collect(java.util.stream.Collectors.toMap(Equipo::equipoId, e -> e));
        });
        when(usuarios.perfiles(any())).thenReturn(Map.of(CLIENTE, new ContactoCorreo("Laura Gómez", "laura@correo.com"),
                OTRO_CLIENTE, new ContactoCorreo("Tomás Ruiz", "tomas@correo.com")));
        when(servicios.nombres(any())).thenReturn(Map.of(SERVICIO, "Sistema de inventario"));
        when(citas.buscar(CITA)).thenReturn(Optional.of(new CitaOrigen(CITA, "C-0045", "ATENDIDA", CLIENTE, SERVICIO, AHORA, EQUIPO)));
        when(proyectosJpa.codigoDeCita(CITA)).thenReturn(Optional.empty());
        when(plantillas.leer(SERVICIO)).thenReturn(plantillaValida());
        when(proyectosJpa.siguienteCodigo()).thenReturn("P-0032");
        when(proyectosJpa.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(PROYECTO);
        when(proyectosJpa.copiarPlantilla(PROYECTO, SERVICIO)).thenReturn(5);
        when(cambiosJpa.hayCambioDePlanAbierto(PROYECTO)).thenReturn(false);
        when(cambiosJpa.insertarCambioDeFecha(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        comoAdministrador();
        servicio = new ServicioProyectos(proyectos, proyectosJpa, cambiosJpa, plantillas, citas, equipos, usuarios, servicios, auditoria, sesion, eventos, Clock.fixed(AHORA, COLOMBIA));
    }

    // ---- ayudas ----

    static Equipo equipo(UUID id, UUID po, String nombre, int activos, int max) {
        return new Equipo(id, po, nombre, 3, activos, max);
    }

    static ProyectoFila fila(String estado, String estadoPlan) {
        return fila(PROYECTO, "P-0031", CLIENTE, EQUIPO, estado, estadoPlan, null);
    }

    static ProyectoFila fila(UUID id, String codigo, UUID cliente, UUID equipo, String estado, String estadoPlan, String riesgo) {
        return new ProyectoFila(id, codigo, "Inventario del salón", ALCANCE, UUID.randomUUID(), cliente, SERVICIO, equipo, HOY.minusDays(5),
                HOY.plusDays(40), estado, estadoPlan, null, null, null, BigDecimal.ZERO, null, null, null, AHORA, AHORA, 5, 1, "Diseño", null, riesgo);
    }

    static List<FasePlantilla> plantillaValida() {
        return List.of(new FasePlantilla(1, "Diseño", null, false, List.of(new PasoPlantilla(1, "Reunir", null))),
                new FasePlantilla(2, "Entrega", null, true, List.of(new PasoPlantilla(1, "Capacitar", null))));
    }

    void comoAdministrador() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of("proyectos.gestionar"), false, 0));
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura", "laura@correo.com", "CLIENTE", Set.of("proyectos.ver_propios"), false, 0));
    }

    void comoProductOwner(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Andrés", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.planificar"), false, 0));
    }

    static Creacion creacion(String nombre, String alcance, String inicio, String entrega) {
        return new Creacion(CITA, nombre, alcance, inicio, entrega, EQUIPO);
    }

    static Creacion creacionValida() {
        return creacion("Inventario del salón", ALCANCE, HOY.plusDays(2).toString(), HOY.plusDays(40).toString());
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    List<AvisoSolicitado> avisos() {
        ArgumentCaptor<AvisoSolicitado> c = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(c.capture());
        return c.getAllValues();
    }

    // ------------------------------------------------------------------------------------------------ crear

    @Nested
    @DisplayName("Crear un proyecto desde una cita atendida (HU-02)")
    class Crear {

        @Test
        @DisplayName("Crea el proyecto con la plantilla copiada, audita y avisa al cliente y al Product Owner (RN-06)")
        void crea() {
            var r = servicio.crear(creacionValida());

            assertThat(r.codigo()).isEqualTo("P-0032");
            assertThat(r.estadoPlan()).as("nace con el plan en borrador").isEqualTo("BORRADOR");
            assertThat(r.fases()).isEqualTo(5);
            verify(proyectosJpa).insertar("P-0032", "Inventario del salón", ALCANCE, CITA, CLIENTE, SERVICIO, EQUIPO, ADMIN, HOY.plusDays(2), HOY.plusDays(40));
            verify(proyectosJpa).copiarPlantilla(PROYECTO, SERVICIO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Creó un proyecto desde una cita atendida"), eq("proyecto"), eq("P-0032"), anyMap());
            List<AvisoSolicitado> avisos = avisos();
            assertThat(avisos).extracting(AvisoSolicitado::tipo).containsExactly("PROYECTO_CREADO", "PROYECTO_ASIGNADO");
            assertThat(avisos.get(0).destinatarios()).containsExactly(CLIENTE);
            assertThat(avisos.get(1).destinatarios()).containsExactly(PO);
        }

        @Test
        @DisplayName("Sin datos: 422 con un error por cada campo obligatorio y no toca nada")
        void sinDatos() {
            assertThatThrownBy(() -> servicio.crear(new Creacion(null, null, null, null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class,
                            e -> assertThat(campos(e)).containsExactly("citaId", "nombre", "alcance", "fechaInicio", "fechaEstimadaEntrega", "equipoId"));
            verify(proyectosJpa, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Valores límite del nombre: 4 caracteres no, 5 sí, 60 sí, 61 no")
        void limitesDelNombre() {
            assertThatThrownBy(() -> servicio.crear(creacion("x".repeat(4), ALCANCE, HOY.plusDays(2).toString(), HOY.plusDays(40).toString())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("nombre"));
            assertThatThrownBy(() -> servicio.crear(creacion("x".repeat(61), ALCANCE, HOY.plusDays(2).toString(), HOY.plusDays(40).toString())))
                    .isInstanceOf(ValidacionException.class);

            assertThat(servicio.crear(creacion("x".repeat(5), ALCANCE, HOY.plusDays(2).toString(), HOY.plusDays(40).toString())).codigo()).isEqualTo("P-0032");
            assertThat(servicio.crear(creacion("x".repeat(60), ALCANCE, HOY.plusDays(2).toString(), HOY.plusDays(40).toString())).codigo()).isEqualTo("P-0032");
        }

        @Test
        @DisplayName("Valores límite del alcance: 19 caracteres no, 20 sí, 600 sí, 601 no")
        void limitesDelAlcance() {
            String inicio = HOY.plusDays(2).toString();
            String entrega = HOY.plusDays(40).toString();
            assertThatThrownBy(() -> servicio.crear(creacion("Inventario", "x".repeat(19), inicio, entrega))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.crear(creacion("Inventario", "x".repeat(601), inicio, entrega))).isInstanceOf(ValidacionException.class);

            assertThat(servicio.crear(creacion("Inventario", "x".repeat(20), inicio, entrega)).codigo()).isEqualTo("P-0032");
            assertThat(servicio.crear(creacion("Inventario", "x".repeat(600), inicio, entrega)).codigo()).isEqualTo("P-0032");
        }

        @Test
        @DisplayName("Fechas: el inicio de ayer no; el de hoy sí; la entrega a 13 días del inicio no, a 14 sí; formato mal escrito da 422")
        void fechas() {
            assertThatThrownBy(() -> servicio.crear(creacion("Inventario", ALCANCE, HOY.minusDays(1).toString(), HOY.plusDays(40).toString())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("El inicio no puede ser una fecha pasada."));
            assertThat(servicio.crear(creacion("Inventario", ALCANCE, HOY.toString(), HOY.plusDays(14).toString())).codigo()).isEqualTo("P-0032");
            assertThatThrownBy(() -> servicio.crear(creacion("Inventario", ALCANCE, HOY.toString(), HOY.plusDays(13).toString())))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("fechaEstimadaEntrega"));
            assertThatThrownBy(() -> servicio.crear(creacion("Inventario", ALCANCE, "02/03/2027", "2027-04-20")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe la fecha como AAAA-MM-DD."));
        }

        @Test
        @DisplayName("Una cita que no existe es 404; una que no está atendida es 409 (RN-04)")
        void citaInvalida() {
            when(citas.buscar(CITA)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(NoEncontradoException.class);

            when(citas.buscar(CITA)).thenReturn(Optional.of(new CitaOrigen(CITA, "C-0045", "CONFIRMADA", CLIENTE, SERVICIO, AHORA, EQUIPO)));
            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(ConflictoException.class).hasMessage("Solo una cita atendida puede convertirse en proyecto");
        }

        @Test
        @DisplayName("Cada cita genera como máximo un proyecto: la repetida da 409 con el código del existente (RN-04)")
        void citaYaConvertida() {
            when(proyectosJpa.codigoDeCita(CITA)).thenReturn(Optional.of("P-0030"));

            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(ConflictoException.class).hasMessageContaining("P-0030");
            verify(proyectosJpa, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Si la plantilla del servicio no cumple RN-28 (una sola fase, ninguna aprueba el cliente o una sin pasos), 422 antes de crear")
        void plantillaInvalida() {
            when(plantillas.leer(SERVICIO)).thenReturn(List.of(new FasePlantilla(1, "Única", null, true, List.of(new PasoPlantilla(1, "Hacer", null)))));
            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("plantilla de fases");

            when(plantillas.leer(SERVICIO)).thenReturn(List.of(new FasePlantilla(1, "A", null, false, List.of(new PasoPlantilla(1, "Hacer", null))),
                    new FasePlantilla(2, "B", null, false, List.of(new PasoPlantilla(1, "Hacer", null)))));
            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(ReglaNegocioException.class);

            when(plantillas.leer(SERVICIO)).thenReturn(List.of(new FasePlantilla(1, "A", null, false, List.of()), new FasePlantilla(2, "B", null, true, List.of(new PasoPlantilla(1, "Hacer", null)))));
            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(ReglaNegocioException.class);
            verify(proyectosJpa, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Valores límite del cupo (RN-05): con 2 de 3 proyectos activos todavía recibe; con 3 de 3 da 409")
        void cupoDelEquipo() {
            when(equipos.activo(EQUIPO)).thenReturn(Optional.of(equipo(EQUIPO, PO, "Andrés Pérez", 2, 3)));
            assertThat(servicio.crear(creacionValida()).codigo()).isEqualTo("P-0032");

            when(equipos.activo(EQUIPO)).thenReturn(Optional.of(equipo(EQUIPO, PO, "Andrés Pérez", 3, 3)));
            assertThatThrownBy(() -> servicio.crear(creacionValida())).isInstanceOf(ConflictoException.class).hasMessage("El equipo ya tiene 3 proyectos activos, elige otro");
        }

        @Test
        @DisplayName("Bloquea la fila del equipo ANTES de leer su cupo: dos asignaciones a la vez no pasan del máximo")
        void bloqueaAntesDeLeerElCupo() {
            servicio.crear(creacionValida());

            InOrder orden = inOrder(equipos);
            orden.verify(equipos).bloquear(EQUIPO);
            orden.verify(equipos).activo(EQUIPO);
        }

        @Test
        @DisplayName("Un equipo inactivo o inexistente: 422 «Elige un equipo de la lista»")
        void equipoInactivo() {
            when(equipos.activo(EQUIPO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(creacionValida()))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige un equipo de la lista"));
        }
    }

    // ------------------------------------------------------------------------------------------------ mis proyectos

    @Nested
    @DisplayName("Mis proyectos (HU-03, HU-04)")
    class Propios {

        ProyectoFila ajeno;

        @BeforeEach
        void proyectosDeVarios() {
            ajeno = fila(UUID.randomUUID(), "P-0040", OTRO_CLIENTE, OTRO_EQUIPO, "ACTIVO", "BORRADOR", null);
            when(proyectos.todos(HOY)).thenAnswer(i -> List.of(proyecto, ajeno));
        }

        @Test
        @DisplayName("El cliente ve solo los suyos (nunca los de otro cliente) y con el plan enviado le toca REVISAR_PROPUESTA")
        void cliente() {
            proyecto = fila("ACTIVO", "ENVIADO");
            comoCliente(CLIENTE);

            var lista = servicio.propios();

            assertThat(lista).extracting(ServicioProyectos.ProyectoPropio::codigo).containsExactly("P-0031");
            assertThat(lista.get(0).porHacer()).isEqualTo("REVISAR_PROPUESTA");
            assertThat(lista.get(0).servicio()).isEqualTo("Sistema de inventario");
        }

        @Test
        @DisplayName("Al cliente un borrador no le pide nada; al Product Owner sí: PREPARAR_PLAN, y con ajustes pedidos AJUSTAR_PLAN")
        void porHacerSegunQuienMira() {
            comoCliente(CLIENTE);
            assertThat(servicio.propios().get(0).porHacer()).isNull();

            comoProductOwner(PO);
            when(equipos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
            assertThat(servicio.propios().get(0).porHacer()).isEqualTo("PREPARAR_PLAN");

            proyecto = fila("ACTIVO", "AJUSTES_PEDIDOS");
            assertThat(servicio.propios().get(0).porHacer()).isEqualTo("AJUSTAR_PLAN");
        }

        @Test
        @DisplayName("El Product Owner ve solo los de su equipo; un proyecto pausado no tiene tarea pendiente")
        void productOwner() {
            comoProductOwner(PO);
            when(equipos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
            proyecto = fila("PAUSADO", "BORRADOR");

            var lista = servicio.propios();

            assertThat(lista).extracting(ServicioProyectos.ProyectoPropio::codigo).containsExactly("P-0031");
            assertThat(lista.get(0).porHacer()).isNull();
        }

        @Test
        @DisplayName("Quien no lidera ningún equipo (por ejemplo el administrador) recibe una lista vacía")
        void sinEquipo() {
            comoProductOwner(UUID.randomUUID());
            when(equipos.equipoDelProductOwner(any())).thenReturn(Optional.empty());

            assertThat(servicio.propios()).isEmpty();
        }
    }

    // ------------------------------------------------------------------------------------------------ lista

    @Nested
    @DisplayName("Lista del administrador (HU-17)")
    class Lista {

        @Test
        @DisplayName("cumple: cada filtro deja pasar lo suyo; «plan» es ACTIVO con el plan ENVIADO; «riesgo» es lo que trae riesgo calculado")
        void filtros() {
            ProyectoFila enRiesgo = fila(UUID.randomUUID(), "P-0001", CLIENTE, EQUIPO, "ACTIVO", "APROBADO", "ENTREGA_VENCIDA");

            assertThat(ReglasProyecto.cumple("riesgo", enRiesgo)).isTrue();
            assertThat(ReglasProyecto.cumple("riesgo", fila("ACTIVO", "APROBADO"))).isFalse();
            assertThat(ReglasProyecto.cumple("plan", fila("ACTIVO", "ENVIADO"))).isTrue();
            assertThat(ReglasProyecto.cumple("plan", fila("PAUSADO", "ENVIADO"))).isFalse();
            assertThat(ReglasProyecto.cumple("activo", fila("ACTIVO", "BORRADOR"))).isTrue();
            assertThat(ReglasProyecto.cumple("pausado", fila("PAUSADO", "VENCIDO"))).isTrue();
            assertThat(ReglasProyecto.cumple("garantia", fila("EN_GARANTIA", "APROBADO"))).isTrue();
            assertThat(ReglasProyecto.cumple("finalizado", fila("FINALIZADO", "APROBADO"))).isTrue();
            assertThat(ReglasProyecto.cumple("cancelado", fila("CANCELADO", "BORRADOR"))).isTrue();
            assertThat(ReglasProyecto.cumple("todas", fila("CANCELADO", "BORRADOR"))).isTrue();
        }

        @Test
        @DisplayName("Un filtro que no existe da 422 en el campo estado")
        void filtroDesconocido() {
            assertThatThrownBy(() -> servicio.listar(new Filtros("inventado", null, null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("estado"));
        }

        @Test
        @DisplayName("Sin filtro o en blanco equivale a «todas»; el filtro no distingue mayúsculas; los contadores de cada filtro vienen completos")
        void contadores() {
            when(proyectos.todos(HOY)).thenReturn(List.of(fila("ACTIVO", "ENVIADO"), fila(UUID.randomUUID(), "P-0002", CLIENTE, EQUIPO, "PAUSADO", "VENCIDO", null)));

            var sinFiltro = servicio.listar(new Filtros("  ", null, null, null, null));
            var soloPausados = servicio.listar(new Filtros("PAUSADO", null, null, null, null));

            assertThat(sinFiltro.totalFiltrados()).isEqualTo(2);
            assertThat(soloPausados.totalFiltrados()).isEqualTo(1);
            assertThat(sinFiltro.porFiltro()).containsEntry("todas", 2).containsEntry("activo", 1).containsEntry("pausado", 1).containsEntry("plan", 1);
            assertThat(sinFiltro.porFiltro()).containsKeys("riesgo", "garantia", "finalizado", "cancelado");
        }

        @Test
        @DisplayName("La búsqueda no distingue mayúsculas ni tildes y mira código, nombre y cliente; el equipo filtra por su id")
        void busquedaYEquipo() {
            ProyectoFila otro = new ProyectoFila(UUID.randomUUID(), "P-0002", "Tienda de zapatos", ALCANCE, UUID.randomUUID(), OTRO_CLIENTE, SERVICIO, OTRO_EQUIPO,
                    HOY, HOY.plusDays(30), "ACTIVO", "BORRADOR", null, null, null, BigDecimal.ZERO, null, null, null, AHORA, AHORA, 5, 1, "Diseño", null, null);
            when(proyectos.todos(HOY)).thenReturn(List.of(fila("ACTIVO", "BORRADOR"), otro));

            assertThat(servicio.listar(new Filtros(null, null, "SALON", null, null)).proyectos()).extracting(v -> v.fila().codigo()).containsExactly("P-0031");
            assertThat(servicio.listar(new Filtros(null, null, "tomas", null, null)).proyectos()).extracting(v -> v.fila().codigo()).containsExactly("P-0002");
            assertThat(servicio.listar(new Filtros(null, null, "p-0002", null, null)).totalFiltrados()).isEqualTo(1);
            assertThat(servicio.listar(new Filtros(null, OTRO_EQUIPO, null, null, null)).proyectos()).extracting(v -> v.fila().codigo()).containsExactly("P-0002");
        }

        @Test
        @DisplayName("Paginación: el tamaño se ajusta a 1..100 y la página pedida de más cae en la última, sin error")
        void paginacion() {
            List<ProyectoFila> veinticinco = IntStream.rangeClosed(1, 25)
                    .mapToObj(i -> fila(UUID.randomUUID(), "P-%04d".formatted(i), CLIENTE, EQUIPO, "ACTIVO", "BORRADOR", null)).toList();
            when(proyectos.todos(HOY)).thenReturn(veinticinco);

            var porDefecto = servicio.listar(new Filtros(null, null, null, null, null));
            assertThat(porDefecto.tamano()).isEqualTo(ServicioProyectos.TAMANO_PAGINA);
            assertThat(porDefecto.totalPaginas()).isEqualTo(2);
            assertThat(porDefecto.proyectos()).hasSize(20);

            var ultima = servicio.listar(new Filtros(null, null, null, 99, null));
            assertThat(ultima.pagina()).isEqualTo(1);
            assertThat(ultima.proyectos()).hasSize(5);

            assertThat(servicio.listar(new Filtros(null, null, null, null, 0)).tamano()).isEqualTo(1);
            assertThat(servicio.listar(new Filtros(null, null, null, null, 500)).tamano()).isEqualTo(ServicioProyectos.TAMANO_MAXIMO);
            assertThat(servicio.listar(new Filtros(null, null, null, -3, null)).pagina()).isZero();
        }

        @Test
        @DisplayName("Sin proyectos: una sola página vacía, no cero páginas")
        void vacia() {
            when(proyectos.todos(HOY)).thenReturn(List.of());

            var vacia = servicio.listar(new Filtros(null, null, null, null, null));

            assertThat(vacia.proyectos()).isEmpty();
            assertThat(vacia.totalPaginas()).isEqualTo(1);
            assertThat(vacia.pagina()).isZero();
        }

        @Test
        @DisplayName("obtener: un código mal formado o inexistente es 404")
        void obtener() {
            assertThat(servicio.obtener("P-0031").cliente()).isEqualTo("Laura Gómez");
            assertThatThrownBy(() -> servicio.obtener("31")).isInstanceOf(NoEncontradoException.class);
            when(proyectos.porCodigo("P-9999", HOY)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.obtener("P-9999")).isInstanceOf(NoEncontradoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ editar

    @Nested
    @DisplayName("Editar un proyecto (HU-17; D-33)")
    class Editar {

        static final String MOTIVO = "El cliente pidió ajustar el nombre del proyecto.";
        static final String MOTIVO_DE_FECHA = "El cliente necesita la entrega una semana después por temporada alta.";

        @Test
        @DisplayName("Cambiar el nombre: se guarda, queda en la auditoría con el antes y el después y se avisa a las dos partes")
        void cambiaElNombre() {
            servicio.editar("P-0031", new Edicion("Inventario del salón Laura", null, null, null, MOTIVO));

            verify(proyectosJpa).actualizarDatos(PROYECTO, "Inventario del salón Laura", ALCANCE, proyecto.fechaEntrega());
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Editó un proyecto"), eq("proyecto"), eq("P-0031"), anyMap());
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsExactly("PROYECTO_EDITADO", "PROYECTO_EDITADO");
        }

        @Test
        @DisplayName("Sin ningún cambio: 422 «No hay cambios para guardar»")
        void sinCambios() {
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, null, null, MOTIVO)))
                    .isInstanceOf(ReglaNegocioException.class).hasMessage("No hay cambios para guardar");
        }

        @Test
        @DisplayName("El motivo es obligatorio siempre: vacío, 9 caracteres y 301 dan 422; 10 y 300 pasan")
        void motivo() {
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion("Nuevo nombre largo", null, null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("motivo"));
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion("Nuevo nombre largo", null, null, null, "x".repeat(9)))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion("Nuevo nombre largo", null, null, null, "x".repeat(301)))).isInstanceOf(ValidacionException.class);

            servicio.editar("P-0031", new Edicion("Nuevo nombre largo", null, null, null, "x".repeat(10)));
            servicio.editar("P-0031", new Edicion("Nuevo nombre largo", null, null, null, "x".repeat(300)));
        }

        @Test
        @DisplayName("Con el plan en BORRADOR o con ajustes pedidos, la fecha de entrega cambia de una vez")
        void fechaDirecta() {
            LocalDate nueva = proyecto.fechaEntrega().plusDays(10);

            servicio.editar("P-0031", new Edicion(null, null, nueva.toString(), null, MOTIVO));

            verify(proyectosJpa).actualizarDatos(PROYECTO, proyecto.nombre(), ALCANCE, nueva);
            verify(cambiosJpa, never()).insertarCambioDeFecha(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Con el plan ENVIADO la fecha no se cambia: 409 «espera su respuesta»")
        void fechaConPlanEnviado() {
            proyecto = fila("ACTIVO", "ENVIADO");

            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, proyecto.fechaEntrega().plusDays(5).toString(), null, MOTIVO)))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining("espera su respuesta");
            verify(proyectosJpa, never()).actualizarDatos(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Con el plan APROBADO nace un cambio de fecha ya AUTORIZADO que acepta el cliente; la fecha del proyecto no cambia todavía")
        void fechaConPlanAprobado() {
            proyecto = fila("ACTIVO", "APROBADO");
            LocalDate nueva = proyecto.fechaEntrega().plusDays(7);

            var r = servicio.editar("P-0031", new Edicion(null, null, nueva.toString(), null, MOTIVO_DE_FECHA));

            assertThat(r.cambioFecha()).isNotNull();
            assertThat(r.cambioFecha().estado()).isEqualTo("AUTORIZADO");
            verify(cambiosJpa).insertarCambioDeFecha(eq(PROYECTO), eq(nueva), eq(MOTIVO_DE_FECHA), eq(ADMIN), any());
            verify(proyectosJpa).actualizarDatos(PROYECTO, proyecto.nombre(), ALCANCE, proyecto.fechaEntrega());
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).contains("CAMBIO_FECHA_AUTORIZADO");
        }

        @Test
        @DisplayName("Con el plan aprobado el motivo de la fecha pide 20 caracteres (19 no); y no hay dos cambios de plan abiertos a la vez")
        void fechaConPlanAprobadoValidaciones() {
            proyecto = fila("ACTIVO", "APROBADO");
            String nueva = proyecto.fechaEntrega().plusDays(7).toString();

            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, nueva, null, "x".repeat(19))))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.campo()).contains("motivo"));

            when(cambiosJpa.hayCambioDePlanAbierto(PROYECTO)).thenReturn(true);
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, nueva, null, MOTIVO_DE_FECHA)))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining("cambio de plan abierto");
        }

        @Test
        @DisplayName("La entrega no puede quedar en el pasado ni a menos de 14 días del inicio")
        void fechasInvalidas() {
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, HOY.minusDays(1).toString(), null, MOTIVO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("fecha pasada"));
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, proyecto.fechaInicio().plusDays(13).toString(), null, MOTIVO)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("2 semanas"));
        }

        @Test
        @DisplayName("Un proyecto finalizado, cancelado o en garantía no se edita (409)")
        void proyectosCerrados() {
            for (String estado : List.of("FINALIZADO", "CANCELADO", "EN_GARANTIA")) {
                proyecto = fila(estado, "APROBADO");
                assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion("Nuevo nombre largo", null, null, null, MOTIVO))).as(estado).isInstanceOf(ConflictoException.class);
            }
        }

        @Test
        @DisplayName("Reasignar el equipo: respeta el cupo del equipo nuevo y avisa a los dos Product Owner y al cliente (RN-05)")
        void reasigna() {
            servicio.editar("P-0031", new Edicion(null, null, null, OTRO_EQUIPO, MOTIVO));

            verify(proyectosJpa).cambiarEquipo(PROYECTO, OTRO_EQUIPO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Reasignó el equipo de un proyecto"), eq("proyecto"), eq("P-0031"), anyMap());
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsExactlyInAnyOrder("PROYECTO_ASIGNADO", "PROYECTO_REASIGNADO", "PROYECTO_REASIGNADO");
        }

        @Test
        @DisplayName("Reasignar a un equipo sin cupo da 409 y no cambia nada; a un proyecto pausado sí se le puede asignar sin mirar el cupo")
        void reasignaSinCupo() {
            when(equipos.activo(OTRO_EQUIPO)).thenReturn(Optional.of(equipo(OTRO_EQUIPO, OTRO_PO, "Camila Ruiz", 3, 3)));
            assertThatThrownBy(() -> servicio.editar("P-0031", new Edicion(null, null, null, OTRO_EQUIPO, MOTIVO))).isInstanceOf(ConflictoException.class);
            verify(proyectosJpa, never()).cambiarEquipo(any(), any());

            proyecto = fila("PAUSADO", "RECHAZADO");
            servicio.editar("P-0031", new Edicion(null, null, null, OTRO_EQUIPO, MOTIVO));
            verify(proyectosJpa).cambiarEquipo(PROYECTO, OTRO_EQUIPO);
        }
    }

    // ------------------------------------------------------------------------------------------------ estado

    @Nested
    @DisplayName("Pausar, reanudar o cancelar (HU-17; RN-29)")
    class Estado {

        static final String MOTIVO = "Decisión de la administración tras hablar con el cliente.";

        @Test
        @DisplayName("Pausar un proyecto activo: queda PAUSADO con su motivo, se audita y se avisa al cliente y al equipo")
        void pausa() {
            servicio.cambiarEstado("P-0031", "pausado", MOTIVO, null);

            verify(proyectosJpa).cambiarEstado(PROYECTO, EstadoProyecto.PAUSADO, MOTIVO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), eq("Pausó un proyecto"), eq("proyecto"), eq("P-0031"), anyMap());
            assertThat(avisos()).extracting(AvisoSolicitado::tipo).containsOnly("PROYECTO_PAUSADO").hasSize(2);
        }

        @Test
        @DisplayName("Cancelar: el proyecto no se borra, queda CANCELADO y el aviso al equipo no lleva enlace")
        void cancela() {
            servicio.cambiarEstado("P-0031", "CANCELADO", MOTIVO, null);

            verify(proyectosJpa).cambiarEstado(PROYECTO, EstadoProyecto.CANCELADO, MOTIVO);
            verify(proyectosJpa, never()).reanudar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
            assertThat(avisos().get(1).enlace()).isNull();
        }

        @Test
        @DisplayName("Reanudar un proyecto pausado con el plan aprobado: vuelve ACTIVO con el mismo equipo y el plan no cambia")
        void reanudaSinCambiarElPlan() {
            proyecto = fila("PAUSADO", "APROBADO");

            servicio.cambiarEstado("P-0031", "ACTIVO", MOTIVO, null);

            verify(proyectosJpa).reanudar(PROYECTO, EQUIPO, false);
        }

        @Test
        @DisplayName("Reanudar una propuesta vencida o rechazada exige elegir equipo y deja el plan en BORRADOR en una sola operación (RN-29)")
        void reanudaUnaPropuestaVencida() {
            for (String plan : List.of("VENCIDO", "RECHAZADO")) {
                proyecto = fila("PAUSADO", plan);
                assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "ACTIVO", MOTIVO, null))
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("equipoId"));
            }
            proyecto = fila("PAUSADO", "VENCIDO");

            servicio.cambiarEstado("P-0031", "ACTIVO", MOTIVO, OTRO_EQUIPO);

            verify(proyectosJpa).reanudar(PROYECTO, OTRO_EQUIPO, true);
        }

        @Test
        @DisplayName("Reanudar respeta el cupo del equipo (RN-05): con 3 de 3 da 409 y no reanuda")
        void reanudarConCupo() {
            proyecto = fila("PAUSADO", "APROBADO");
            when(equipos.activo(EQUIPO)).thenReturn(Optional.of(equipo(EQUIPO, PO, "Andrés Pérez", 3, 3)));

            assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "ACTIVO", MOTIVO, null)).isInstanceOf(ConflictoException.class);
            verify(proyectosJpa, never()).reanudar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("Transiciones imposibles dan 409: pausar uno ya pausado, reanudar uno activo, pausar uno que no está activo")
        void transicionesImposibles() {
            assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "ACTIVO", MOTIVO, null)).hasMessage("El proyecto ya está activo");

            proyecto = fila("PAUSADO", "APROBADO");
            assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "PAUSADO", MOTIVO, null)).hasMessage("El proyecto ya está pausado");
        }

        @Test
        @DisplayName("Un estado que no existe, o un motivo vacío, da 422 antes de leer el proyecto")
        void validaciones() {
            assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "BORRADO", MOTIVO, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("estado"));
            assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "PAUSADO", "  ", null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("motivo"));
            assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", null, null, null))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("estado", "motivo"));
            verify(proyectos, never()).bloquear(any());
        }

        @Test
        @DisplayName("Un proyecto finalizado, cancelado o en garantía no cambia de estado (409)")
        void proyectosCerrados() {
            for (String estado : List.of("FINALIZADO", "CANCELADO", "EN_GARANTIA")) {
                proyecto = fila(estado, "APROBADO");
                assertThatThrownBy(() -> servicio.cambiarEstado("P-0031", "PAUSADO", MOTIVO, null)).as(estado).isInstanceOf(ConflictoException.class);
            }
        }
    }
}
