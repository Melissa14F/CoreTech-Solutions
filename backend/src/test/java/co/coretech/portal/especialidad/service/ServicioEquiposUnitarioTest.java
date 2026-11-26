package co.coretech.portal.especialidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.coretech.portal.acceso.CuentasDeProductOwner;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.service.ServicioEquipos.DatosEquipo;
import co.coretech.portal.especialidad.service.ServicioEquipos.DatosIntegrante;
import co.coretech.portal.especialidad.service.Vistas.EquipoVista;
import co.coretech.portal.especialidad.repository.RepositorioEspecialidad;
import co.coretech.portal.especialidad.domain.Desarrollador;
import co.coretech.portal.especialidad.domain.Equipo;
import co.coretech.portal.especialidad.domain.Especialidad;
import co.coretech.portal.especialidad.domain.Integrante;
import co.coretech.portal.especialidad.domain.RolEquipo;

/**
 * Pruebas UNITARIAS de {@link ServicioEquipos} (HU-26; RN-05, RN-18, RN-25, RN-26): armar equipos con un único Product Owner con
 * cuenta, integrantes con el rol que permite su especialidad y cupo de 1 a 3 proyectos. El repositorio, las cuentas y la
 * auditoría van simulados; no hay base de datos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioEquiposUnitarioTest {

    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID OTRO_EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    static final UUID CUENTA_NUEVA = UUID.fromString("00000000-0000-4000-8000-0000000000c9");

    @Mock
    RepositorioEspecialidad repositorio;
    @Mock
    CuentasDeProductOwner cuentas;
    @Mock
    RegistroAuditoria auditoria;

    ServicioEquipos servicio;

    Desarrollador andres = dev("Andrés Pérez", Especialidad.FULL_STACK, true, UUID.randomUUID());
    Desarrollador camila = dev("Camila Ruiz", Especialidad.UX_UI, true, null);
    Desarrollador mateo = dev("Mateo Gil", Especialidad.QA, true, null);
    Desarrollador sebastian = dev("Sebastián Mora", Especialidad.BACKEND, true, UUID.randomUUID());
    Desarrollador diana = dev("Diana Soto", Especialidad.FULL_STACK, true, null);
    Desarrollador inactivo = dev("Iván Inactivo", Especialidad.QA, false, null);
    List<Integrante> integrantes = new ArrayList<>();
    List<Equipo> equipos = new ArrayList<>();

    static Desarrollador dev(String nombre, Especialidad especialidad, boolean activo, UUID usuario) {
        return new Desarrollador(UUID.randomUUID(), nombre, nombre.split(" ")[0].toLowerCase() + "@coretechsolutions.co", "3001234567", especialidad, usuario, activo, LocalDate.of(2026, 1, 15));
    }

    @BeforeEach
    void preparaLoComun() {
        List<Desarrollador> todos = List.of(andres, camila, mateo, sebastian, diana, inactivo);
        when(repositorio.desarrolladores()).thenReturn(todos);
        when(repositorio.desarrollador(any())).thenAnswer(i -> todos.stream().filter(d -> d.id().equals(i.getArgument(0))).findFirst());
        when(repositorio.integrantes()).thenAnswer(i -> integrantes);
        when(repositorio.equipos()).thenAnswer(i -> equipos);
        when(repositorio.integranteDe(any())).thenAnswer(i -> integrantes.stream().filter(x -> x.desarrolladorId().equals(i.getArgument(0))).findFirst());
        when(repositorio.proyectosActivosPorEquipo()).thenReturn(Map.of());
        // El equipo y sus integrantes aparecen cuando se insertan, como en la base: así obtener(...) los lee de vuelta
        when(repositorio.insertarEquipo(anyInt())).thenAnswer(i -> {
            equipos.add(new Equipo(EQUIPO, i.getArgument(0), true));
            return EQUIPO;
        });
        org.mockito.Mockito.doAnswer(i -> integrantes.add(new Integrante(i.getArgument(0), i.getArgument(1), i.getArgument(2)))).when(repositorio).insertarIntegrante(any(), any(), any());
        when(repositorio.equipoBloqueado(EQUIPO)).thenAnswer(i -> equipos.stream().filter(e -> e.id().equals(EQUIPO)).findFirst());
        when(cuentas.habilitar(any(), any(), any(), any())).thenReturn(CUENTA_NUEVA);
        servicio = new ServicioEquipos(repositorio, cuentas, auditoria);
    }

    /** El equipo EQUIPO ya armado: Andrés lidera, Camila (UX/UI) y Mateo (QA) son integrantes. */
    void equipoArmado(int cupo) {
        equipos.add(new Equipo(EQUIPO, cupo, true));
        integrantes.add(new Integrante(EQUIPO, andres.id(), RolEquipo.PRODUCT_OWNER));
        integrantes.add(new Integrante(EQUIPO, camila.id(), RolEquipo.UX_UI));
        integrantes.add(new Integrante(EQUIPO, mateo.id(), RolEquipo.QA));
    }

    static DatosIntegrante integrante(Desarrollador d, RolEquipo rol) {
        return new DatosIntegrante(d.id(), rol.name());
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    // ------------------------------------------------------------------------------------------------ crear

    @Nested
    @DisplayName("Crear un equipo (HU-26)")
    class Crear {

        @Test
        @DisplayName("Crea el equipo con cupo 3 por omisión, habilita la cuenta del Product Owner, lo asigna, guarda a todos con su rol y lo audita")
        void crea() {

            EquipoVista v = servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(mateo, RolEquipo.QA)), null));

            verify(repositorio).insertarEquipo(3);
            verify(cuentas).habilitar(andres.usuarioId(), andres.nombre(), andres.correo(), andres.celular());
            verify(repositorio).asignarCuenta(andres.id(), CUENTA_NUEVA);
            verify(repositorio).insertarIntegrante(EQUIPO, andres.id(), RolEquipo.PRODUCT_OWNER);
            verify(repositorio).insertarIntegrante(EQUIPO, camila.id(), RolEquipo.UX_UI);
            verify(repositorio).insertarIntegrante(EQUIPO, mateo.id(), RolEquipo.QA);
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Creó un equipo"), eq("equipo"), eq(EQUIPO.toString()), anyMap());
            assertThat(v.productOwner().nombre()).isEqualTo("Andrés Pérez");
            assertThat(v.integrantes()).extracting(Vistas.MiembroVista::nombre).containsExactly("Camila Ruiz", "Mateo Gil");
            assertThat(v.maxProyectos()).isEqualTo(3);
        }

        @Test
        @DisplayName("Valores límite del cupo (RN-05): 0 y 4 se rechazan; 1, 2 y 3 pasan")
        void limitesDelCupo() {
            for (int malo : new int[] { 0, 4, -1 }) {
                assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI)), malo))).as("cupo " + malo)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("maxProyectos"));
            }
            for (int bueno : new int[] { 1, 2, 3 }) {
                equipos.clear();
                integrantes.clear();
                assertThat(servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI)), bueno))).isNotNull();
                verify(repositorio).insertarEquipo(bueno);
            }
        }

        @Test
        @DisplayName("Sin Product Owner, con uno que no existe o con uno inactivo: 422 en productOwnerId")
        void productOwnerInvalido() {
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(null, List.of(integrante(camila, RolEquipo.UX_UI)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige el desarrollador (Product Owner)."));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(UUID.randomUUID(), List.of(integrante(camila, RolEquipo.UX_UI)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige un desarrollador de la lista."));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(inactivo.id(), List.of(integrante(camila, RolEquipo.UX_UI)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Este desarrollador está inactivo"));
            verify(repositorio, never()).insertarEquipo(anyInt());
        }

        @Test
        @DisplayName("Un equipo necesita al menos un integrante además del Product Owner (RN-25): lista vacía o nula, 422")
        void sinIntegrantes() {
            for (List<DatosIntegrante> lista : java.util.Arrays.asList(null, List.<DatosIntegrante>of())) {
                assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), lista, 3)))
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo(ServicioEquipos.SIN_INTEGRANTES));
            }
        }

        @Test
        @DisplayName("El rol debe corresponder a la especialidad (RN-26): un QA no es Frontend; un Full stack puede ser Frontend, Backend o Full stack; nadie entra como Product Owner")
        void rolSegunLaEspecialidad() {
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(mateo, RolEquipo.FRONTEND)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo(ServicioEquipos.ROL_NO_CORRESPONDE));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(sebastian.id(), List.of(integrante(camila, RolEquipo.PRODUCT_OWNER)), 3))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(new DatosIntegrante(camila.id(), "INVENTADO")), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige el rol de cada integrante."));

            for (RolEquipo rol : List.of(RolEquipo.FRONTEND, RolEquipo.BACKEND, RolEquipo.FULL_STACK)) {
                equipos.clear();
                integrantes.clear();
                assertThat(servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(diana, rol)), 3))).as(rol.name()).isNotNull();
            }
        }

        @Test
        @DisplayName("Un desarrollador va una sola vez, el Product Owner no se repite como integrante, uno inactivo no entra y una fila vacía se señala")
        void repetidosInactivosYFilasVacias() {
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(camila, RolEquipo.UX_UI)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Cada desarrollador va una sola vez."));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(andres, RolEquipo.FRONTEND)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("no se repite como integrante"));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(inactivo, RolEquipo.QA)), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Iván Inactivo está inactivo"));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), java.util.Arrays.asList(integrante(camila, RolEquipo.UX_UI), new DatosIntegrante(null, "QA")), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige el desarrollador de cada fila"));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(new DatosIntegrante(UUID.randomUUID(), "QA")), 3)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("integrantes"));
        }

        @Test
        @DisplayName("Un desarrollador está en un solo equipo (RN-26): si el Product Owner o un integrante ya está en otro, 409")
        void yaEstaEnOtroEquipo() {
            integrantes.add(new Integrante(OTRO_EQUIPO, camila.id(), RolEquipo.UX_UI));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI)), 3)))
                    .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioEquipos.OTRO_EQUIPO));

            integrantes.add(new Integrante(OTRO_EQUIPO, sebastian.id(), RolEquipo.PRODUCT_OWNER));
            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(sebastian.id(), List.of(integrante(mateo, RolEquipo.QA)), 3))).isInstanceOf(ConflictoException.class);
            verify(repositorio, never()).insertarEquipo(anyInt());
        }

        @Test
        @DisplayName("Los errores de datos (422) se revisan antes que los choques con otros equipos (409), y todos juntos")
        void primeroLosDatos() {
            integrantes.add(new Integrante(OTRO_EQUIPO, camila.id(), RolEquipo.UX_UI));

            assertThatThrownBy(() -> servicio.crear(new DatosEquipo(null, List.of(integrante(camila, RolEquipo.UX_UI)), 9)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("productOwnerId", "maxProyectos"));
        }
    }

    // ------------------------------------------------------------------------------------------------ editar

    @Nested
    @DisplayName("Editar un equipo (HU-26)")
    class Editar {

        @BeforeEach
        void equipoExistente() {
            equipoArmado(3);
        }

        @Test
        @DisplayName("Cambiar el cupo lo guarda y lo audita; si no se envía, se conserva el actual")
        void cambiaElCupo() {
            servicio.editar(EQUIPO, new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(mateo, RolEquipo.QA)), 2));

            verify(repositorio).cambiarCupo(EQUIPO, 2);
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Editó un equipo"), eq("equipo"), eq(EQUIPO.toString()), anyMap());

            servicio.editar(EQUIPO, new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(mateo, RolEquipo.QA)), null));
            verify(repositorio, org.mockito.Mockito.times(1)).cambiarCupo(any(), anyInt());
        }

        @Test
        @DisplayName("El cupo no puede ser menor que los proyectos activos del equipo (409, RN-05)")
        void cupoMenorQueLosActivos() {
            when(repositorio.proyectosActivosPorEquipo()).thenReturn(Map.of(EQUIPO, 3));

            assertThatThrownBy(() -> servicio.editar(EQUIPO, new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI)), 2)))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining("3 proyectos activos");
            verify(repositorio, never()).cambiarCupo(any(), anyInt());
        }

        @Test
        @DisplayName("Quitar a un integrante de la lista lo saca del equipo y agregar uno nuevo lo inserta con su rol")
        void quitaYAgrega() {
            servicio.editar(EQUIPO, new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(sebastian, RolEquipo.BACKEND)), null));

            verify(repositorio).quitarIntegrante(mateo.id());
            verify(repositorio).insertarIntegrante(EQUIPO, sebastian.id(), RolEquipo.BACKEND);
            verify(repositorio, never()).quitarIntegrante(camila.id());
        }

        @Test
        @DisplayName("Cambiar el rol de un integrante lo actualiza sin sacarlo del equipo")
        void cambiaElRol() {
            integrantes.add(new Integrante(EQUIPO, diana.id(), RolEquipo.FRONTEND));

            servicio.editar(EQUIPO, new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(mateo, RolEquipo.QA), integrante(diana, RolEquipo.BACKEND)), null));

            verify(repositorio).cambiarRol(diana.id(), RolEquipo.BACKEND);
            verify(repositorio, never()).quitarIntegrante(any());
        }

        @Test
        @DisplayName("Cambiar el Product Owner: el nuevo recibe su cuenta, el anterior baja a integrante y pierde el acceso (se bloquea su cuenta)")
        void cambiaElProductOwner() {
            servicio.editar(EQUIPO, new DatosEquipo(sebastian.id(), List.of(integrante(camila, RolEquipo.UX_UI), integrante(mateo, RolEquipo.QA), integrante(andres, RolEquipo.FULL_STACK)), null));

            verify(cuentas).habilitar(sebastian.usuarioId(), sebastian.nombre(), sebastian.correo(), sebastian.celular());
            verify(repositorio).asignarCuenta(sebastian.id(), CUENTA_NUEVA);
            verify(repositorio).cambiarRol(andres.id(), RolEquipo.FULL_STACK);
            verify(repositorio).insertarIntegrante(EQUIPO, sebastian.id(), RolEquipo.PRODUCT_OWNER);
            verify(cuentas).bloquear(andres.usuarioId());
        }

        @Test
        @DisplayName("Un equipo que no existe es 404; los integrantes del propio equipo no cuentan como «en otro equipo»")
        void inexistenteYPropioEquipo() {
            assertThatThrownBy(() -> servicio.editar(UUID.randomUUID(), new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI)), 3)))
                    .isInstanceOf(NoEncontradoException.class);

            assertThat(servicio.editar(EQUIPO, new DatosEquipo(andres.id(), List.of(integrante(camila, RolEquipo.UX_UI)), 3))).isNotNull();
        }

        @Test
        @DisplayName("agregarIntegrante suma uno a los actuales y respeta las reglas del rol")
        void agregaUno() {
            servicio.agregarIntegrante(EQUIPO, integrante(sebastian, RolEquipo.BACKEND));
            verify(repositorio).insertarIntegrante(EQUIPO, sebastian.id(), RolEquipo.BACKEND);

            assertThatThrownBy(() -> servicio.agregarIntegrante(EQUIPO, integrante(diana, RolEquipo.QA))).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("quitarIntegrante: se quita con su auditoría; el Product Owner no (409); uno que no está en el equipo, 404")
        void quitaUno() {
            servicio.quitarIntegrante(EQUIPO, mateo.id());
            verify(repositorio).quitarIntegrante(mateo.id());
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Quitó un integrante de un equipo"), eq("equipo"), eq(EQUIPO.toString()), anyMap());

            assertThatThrownBy(() -> servicio.quitarIntegrante(EQUIPO, andres.id())).isInstanceOf(ConflictoException.class).hasMessageContaining("elige otro al editar el equipo");
            assertThatThrownBy(() -> servicio.quitarIntegrante(EQUIPO, diana.id())).isInstanceOf(NoEncontradoException.class);
        }

        @Test
        @DisplayName("No se quita al único integrante que queda además del Product Owner (409, RN-25, D-43)")
        void noSeQuitaAlUltimo() {
            integrantes.removeIf(i -> i.desarrolladorId().equals(mateo.id()));

            assertThatThrownBy(() -> servicio.quitarIntegrante(EQUIPO, camila.id())).isInstanceOf(ConflictoException.class).hasMessageContaining("quedaría sin integrantes");
            verify(repositorio, never()).quitarIntegrante(any());
        }
    }

    // ------------------------------------------------------------------------------------------------ consultar

    @Nested
    @DisplayName("Consultar los equipos")
    class Consultar {

        @Test
        @DisplayName("listar trae cada equipo activo con su Product Owner, los demás integrantes y sus proyectos activos; obtener de uno que no existe es 404")
        void lista() {
            equipoArmado(2);
            when(repositorio.proyectosActivosPorEquipo()).thenReturn(Map.of(EQUIPO, 1));

            List<EquipoVista> vistas = servicio.listar();

            assertThat(vistas).hasSize(1);
            assertThat(vistas.get(0).productOwner().rol()).isEqualTo(RolEquipo.PRODUCT_OWNER);
            assertThat(vistas.get(0).integrantes()).extracting(Vistas.MiembroVista::rol).containsExactlyInAnyOrder(RolEquipo.UX_UI, RolEquipo.QA);
            assertThat(vistas.get(0).proyectosActivos()).isEqualTo(1);
            assertThat(vistas.get(0).maxProyectos()).isEqualTo(2);
            assertThat(servicio.obtener(EQUIPO).equipoId()).isEqualTo(EQUIPO);
            assertThatThrownBy(() -> servicio.obtener(UUID.randomUUID())).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioEquipos.NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Un equipo inactivo, o sin Product Owner, no aparece en la lista")
        void soloActivosConProductOwner() {
            equipos.add(new Equipo(EQUIPO, 3, false));
            integrantes.add(new Integrante(EQUIPO, andres.id(), RolEquipo.PRODUCT_OWNER));
            equipos.add(new Equipo(OTRO_EQUIPO, 3, true));
            integrantes.add(new Integrante(OTRO_EQUIPO, camila.id(), RolEquipo.UX_UI));

            assertThat(servicio.listar()).isEmpty();
        }
    }
}
