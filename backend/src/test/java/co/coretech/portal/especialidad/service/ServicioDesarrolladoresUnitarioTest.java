package co.coretech.portal.especialidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import co.coretech.portal.acceso.CuentasDeProductOwner;
import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.service.ServicioDesarrolladores.DatosDesarrollador;
import co.coretech.portal.especialidad.service.Vistas.DesarrolladorVista;
import co.coretech.portal.especialidad.repository.RepositorioEspecialidad;
import co.coretech.portal.especialidad.domain.Desarrollador;
import co.coretech.portal.especialidad.domain.Especialidad;
import co.coretech.portal.especialidad.domain.Integrante;
import co.coretech.portal.especialidad.domain.RolEquipo;

/**
 * Pruebas UNITARIAS de {@link ServicioDesarrolladores} (HU-25; RN-20, RN-25, RN-26): registrar, editar, activar y desactivar
 * desarrolladores con su especialidad. Solo el Product Owner de un equipo tiene cuenta. El repositorio, las cuentas, el
 * directorio y la auditoría van simulados. Reloj fijo: 2031-03-04.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioDesarrolladoresUnitarioTest {

    static final Instant AHORA = Instant.parse("2031-03-04T15:00:00Z");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");

    @Mock
    RepositorioEspecialidad repositorio;
    @Mock
    CuentasDeProductOwner cuentas;
    @Mock
    DirectorioUsuarios directorio;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    ApplicationEventPublisher eventos;

    ServicioDesarrolladores servicio;
    final List<Desarrollador> registro = new ArrayList<>();
    final List<Integrante> integrantes = new ArrayList<>();

    Desarrollador andres = new Desarrollador(UUID.randomUUID(), "Andrés Pérez", "andres@coretechsolutions.co", "3009876543", Especialidad.FULL_STACK, UUID.randomUUID(), true, LocalDate.of(2026, 1, 15));
    Desarrollador camila = new Desarrollador(UUID.randomUUID(), "Camila Ruiz", "camila@coretechsolutions.co", "3001112233", Especialidad.FULL_STACK, null, true, LocalDate.of(2026, 2, 1));
    Desarrollador mateo = new Desarrollador(UUID.randomUUID(), "Mateo Gil", "mateo@coretechsolutions.co", "3004445566", Especialidad.QA, null, true, LocalDate.of(2026, 3, 1));
    Desarrollador diana = new Desarrollador(UUID.randomUUID(), "Diana Soto", "diana@coretechsolutions.co", "3007778899", Especialidad.UX_UI, null, true, LocalDate.of(2026, 4, 1));

    @BeforeEach
    void preparaLoComun() {
        registro.addAll(List.of(andres, camila, mateo, diana));
        when(repositorio.desarrolladores()).thenAnswer(i -> registro);
        when(repositorio.desarrollador(any())).thenAnswer(i -> registro.stream().filter(d -> d.id().equals(i.getArgument(0))).findFirst());
        when(repositorio.desarrolladorPorCorreo(any())).thenAnswer(i -> registro.stream().filter(d -> d.correo().equals(i.getArgument(0))).findFirst());
        when(repositorio.integrantes()).thenAnswer(i -> integrantes);
        when(repositorio.integranteDe(any())).thenAnswer(i -> integrantes.stream().filter(x -> x.desarrolladorId().equals(i.getArgument(0))).findFirst());
        when(repositorio.proyectosActivosPorEquipo()).thenReturn(Map.of());
        when(repositorio.citasPorAtender(any())).thenReturn(0);
        when(directorio.administradoresActivos()).thenReturn(List.of(ADMIN));
        org.mockito.Mockito.doAnswer(i -> registro.add(i.getArgument(0))).when(repositorio).insertar(any());
        servicio = new ServicioDesarrolladores(repositorio, cuentas, directorio, auditoria, eventos, Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    static DatosDesarrollador datos(String nombre, String celular, String correo, String especialidad) {
        return new DatosDesarrollador(nombre, celular, correo, especialidad);
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    /** Equipo con Andrés como Product Owner y los integrantes indicados. */
    void equipoDeAndres(Desarrollador... otros) {
        integrantes.add(new Integrante(EQUIPO, andres.id(), RolEquipo.PRODUCT_OWNER));
        for (Desarrollador d : otros) {
            integrantes.add(new Integrante(EQUIPO, d.id(), d.especialidad() == Especialidad.QA ? RolEquipo.QA : d.especialidad() == Especialidad.UX_UI ? RolEquipo.UX_UI : RolEquipo.FULL_STACK));
        }
    }

    // ------------------------------------------------------------------------------------------------ registrar

    @Nested
    @DisplayName("Registrar un desarrollador (HU-25)")
    class Registrar {

        @Test
        @DisplayName("Queda activo, sin cuenta, con la fecha de ingreso de hoy, el correo en minúscula y el nombre sin espacios dobles; se audita")
        void registra() {
            DesarrolladorVista v = servicio.registrar(datos("  Laura   Beltrán ", " 3001234567 ", " Laura.Beltran@CoretechSolutions.co ", "BACKEND"));

            ArgumentCaptor<Desarrollador> guardado = ArgumentCaptor.forClass(Desarrollador.class);
            verify(repositorio).insertar(guardado.capture());
            Desarrollador d = guardado.getValue();
            assertThat(d.nombre()).isEqualTo("Laura Beltrán");
            assertThat(d.correo()).isEqualTo("laura.beltran@coretechsolutions.co");
            assertThat(d.celular()).isEqualTo("3001234567");
            assertThat(d.activo()).isTrue();
            assertThat(d.usuarioId()).as("solo el Product Owner de un equipo tiene cuenta").isNull();
            assertThat(d.fechaIngreso()).isEqualTo(LocalDate.of(2031, 3, 4));
            assertThat(v.tieneCuenta()).isFalse();
            assertThat(v.equipo()).isNull();
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Registró un desarrollador"), eq("desarrollador"), eq(d.id().toString()), anyMap());
        }

        @Test
        @DisplayName("Sin datos: 422 con un error por campo a la vez y no se guarda nada")
        void sinDatos() {
            assertThatThrownBy(() -> servicio.registrar(datos(null, null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("nombre", "celular", "correo", "especialidad"));
            verify(repositorio, never()).insertar(any());
        }

        @Test
        @DisplayName("Valores límite del nombre: 4 caracteres no, 5 sí, 60 sí, 61 no; con números no")
        void limitesDelNombre() {
            assertThatThrownBy(() -> servicio.registrar(datos("Ana ", "3001234567", "a2@coretechsolutions.co", "QA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe nombre y apellido."));
            assertThat(servicio.registrar(datos("Ana B", "3001234567", "a3@coretechsolutions.co", "QA")).nombre()).isEqualTo("Ana B");
            assertThat(servicio.registrar(datos("a".repeat(60), "3001234567", "a4@coretechsolutions.co", "QA")).nombre()).hasSize(60);
            assertThatThrownBy(() -> servicio.registrar(datos("a".repeat(61), "3001234567", "a5@coretechsolutions.co", "QA"))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.registrar(datos("Ana 123", "3001234567", "a6@coretechsolutions.co", "QA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe solo letras y espacios."));
        }

        @Test
        @DisplayName("El celular de 10 dígitos que empieza por 3; el correo debe ser el corporativo y no pasar de 120 caracteres")
        void celularYCorreo() {
            assertThatThrownBy(() -> servicio.registrar(datos("Laura Beltrán", "2001234567", "l@coretechsolutions.co", "QA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("celular"));
            assertThatThrownBy(() -> servicio.registrar(datos("Laura Beltrán", "3001234567", "laura@gmail.com", "QA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("@coretechsolutions.co"));
            assertThatThrownBy(() -> servicio.registrar(datos("Laura Beltrán", "3001234567", "sin-arroba", "QA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).startsWith("Escribe un correo válido"));
            assertThatThrownBy(() -> servicio.registrar(datos("Laura Beltrán", "3001234567", "a".repeat(100) + "@coretechsolutions.co", "QA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 120 caracteres."));
        }

        @Test
        @DisplayName("La especialidad debe ser una de la lista; una inventada o en blanco da 422")
        void especialidad() {
            assertThatThrownBy(() -> servicio.registrar(datos("Laura Beltrán", "3001234567", "l@coretechsolutions.co", "MAGIA")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige una especialidad de la lista."));
            assertThatThrownBy(() -> servicio.registrar(datos("Laura Beltrán", "3001234567", "l@coretechsolutions.co", "  ")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige una especialidad."));
            for (Especialidad e : Especialidad.values()) {
                assertThat(servicio.registrar(datos("Laura Beltrán", "3001234567", e.name().toLowerCase() + "@coretechsolutions.co", e.name())).especialidad()).isEqualTo(e);
            }
        }

        @Test
        @DisplayName("Un correo ya registrado da 409 en el campo correo (sin distinguir mayúsculas)")
        void correoRepetido() {
            assertThatThrownBy(() -> servicio.registrar(datos("Otra Persona", "3001234567", "ANDRES@coretechsolutions.co", "QA")))
                    .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioDesarrolladores.CORREO_REPETIDO));
        }
    }

    // ------------------------------------------------------------------------------------------------ editar

    @Nested
    @DisplayName("Editar un desarrollador (HU-25)")
    class Editar {

        @Test
        @DisplayName("Cambiar el nombre lo guarda y lo audita con el antes y el después; sin cambios no escribe ni audita")
        void edita() {
            servicio.editar(camila.id(), datos("Camila Ruiz Pardo", camila.celular(), camila.correo(), "FULL_STACK"));

            verify(repositorio).actualizar(camila.id(), "Camila Ruiz Pardo", camila.correo(), camila.celular(), Especialidad.FULL_STACK);
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Editó un desarrollador"), eq("desarrollador"), eq(camila.id().toString()), anyMap());
            verify(cuentas, never()).actualizarDatos(any(), any(), any(), any());

            org.mockito.Mockito.clearInvocations(repositorio, auditoria);
            servicio.editar(camila.id(), datos(camila.nombre(), camila.celular(), camila.correo(), "FULL_STACK"));
            verify(repositorio, never()).actualizar(any(), any(), any(), any(), any());
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Si tiene cuenta (es Product Owner), su correo, nombre y celular se actualizan también en la cuenta")
        void actualizaLaCuenta() {
            servicio.editar(andres.id(), datos("Andrés Felipe Pérez", andres.celular(), andres.correo(), "FULL_STACK"));

            verify(cuentas).actualizarDatos(andres.usuarioId(), "Andrés Felipe Pérez", andres.correo(), andres.celular());
        }

        @Test
        @DisplayName("Si cambia la especialidad y su rol en el equipo ya no corresponde, el rol pasa al primero que permite la nueva (RN-26); un Product Owner no cambia de rol")
        void cambiaElRolConLaEspecialidad() {
            equipoDeAndres(camila);

            servicio.editar(camila.id(), datos(camila.nombre(), camila.celular(), camila.correo(), "QA"));
            verify(repositorio).cambiarRol(camila.id(), RolEquipo.QA);

            servicio.editar(andres.id(), datos(andres.nombre(), andres.celular(), andres.correo(), "QA"));
            verify(repositorio, never()).cambiarRol(eq(andres.id()), any());
        }

        @Test
        @DisplayName("Si la nueva especialidad todavía permite su rol, el rol no se toca: un Full stack que era Frontend sigue siéndolo")
        void conservaElRolSiSigueSiendoValido() {
            integrantes.add(new Integrante(EQUIPO, camila.id(), RolEquipo.FRONTEND));

            servicio.editar(camila.id(), datos(camila.nombre(), camila.celular(), camila.correo(), "FRONTEND"));

            verify(repositorio, never()).cambiarRol(any(), any());
        }

        @Test
        @DisplayName("Conservar el propio correo no es un duplicado; el de otro desarrollador sí (409); uno inexistente es 404")
        void correos() {
            servicio.editar(camila.id(), datos("Camila Ruiz Pardo", camila.celular(), camila.correo(), "FULL_STACK"));

            assertThatThrownBy(() -> servicio.editar(camila.id(), datos(camila.nombre(), camila.celular(), andres.correo(), "FULL_STACK"))).isInstanceOf(ConflictoException.class);
            assertThatThrownBy(() -> servicio.editar(UUID.randomUUID(), datos("Camila Ruiz", "3001112233", "x@coretechsolutions.co", "QA")))
                    .isInstanceOf(NoEncontradoException.class).hasMessage(ServicioDesarrolladores.NO_ENCONTRADO);
        }
    }

    // ------------------------------------------------------------------------------------------------ activar o desactivar

    @Nested
    @DisplayName("Activar o desactivar (HU-25; RN-25, RN-26)")
    class Activar {

        @Test
        @DisplayName("Pedir el estado que ya tiene no hace nada")
        void mismoEstado() {
            servicio.cambiarActivo(camila.id(), true);

            verify(repositorio, never()).cambiarActivo(any(), org.mockito.ArgumentMatchers.anyBoolean());
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Activar a uno inactivo lo deja activo y lo audita")
        void activa() {
            Desarrollador inactivo = new Desarrollador(UUID.randomUUID(), "Iván Inactivo", "ivan@coretechsolutions.co", "3001119999", Especialidad.QA, null, false, LocalDate.of(2026, 1, 1));
            registro.add(inactivo);

            servicio.cambiarActivo(inactivo.id(), true);

            verify(repositorio).cambiarActivo(inactivo.id(), true);
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Activó un desarrollador"), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Desactivar a un desarrollador sin equipo solo lo desactiva")
        void sinEquipo() {
            servicio.cambiarActivo(diana.id(), false);

            verify(repositorio).cambiarActivo(diana.id(), false);
            verify(repositorio, never()).quitarIntegrante(any());
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Desactivó un desarrollador"), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Desactivar a un integrante lo saca de su equipo si el equipo conserva al menos uno más además del Product Owner")
        void integranteSaleDelEquipo() {
            equipoDeAndres(camila, mateo);

            servicio.cambiarActivo(mateo.id(), false);

            verify(repositorio).quitarIntegrante(mateo.id());
            verify(repositorio).cambiarActivo(mateo.id(), false);
        }

        @Test
        @DisplayName("No se desactiva al único integrante que queda además del Product Owner (409, RN-25, D-43)")
        void elUltimoIntegrante() {
            equipoDeAndres(camila);

            assertThatThrownBy(() -> servicio.cambiarActivo(camila.id(), false)).isInstanceOf(ConflictoException.class).hasMessageContaining("quedaría sin integrantes");
            verify(repositorio, never()).cambiarActivo(any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("Un Product Owner con proyectos activos no se desactiva sin reemplazarlo (409, RN-26, D-34)")
        void productOwnerConProyectos() {
            equipoDeAndres(camila);
            when(repositorio.proyectosActivosPorEquipo()).thenReturn(Map.of(EQUIPO, 1));

            assertThatThrownBy(() -> servicio.cambiarActivo(andres.id(), false)).isInstanceOf(ConflictoException.class).hasMessage(ServicioDesarrolladores.PO_CON_PROYECTOS);
            verify(cuentas, never()).bloquear(any());
        }

        @Test
        @DisplayName("Un Product Owner sin proyectos activos se desactiva y su cuenta queda bloqueada hasta que el administrador la reactive")
        void productOwnerSinProyectos() {
            equipoDeAndres(camila);

            servicio.cambiarActivo(andres.id(), false);

            verify(repositorio).cambiarActivo(andres.id(), false);
            verify(cuentas).bloquear(andres.usuarioId());
        }

        @Test
        @DisplayName("Si el equipo del Product Owner tenía citas por atender se avisa a los administradores, con «1 cita» en singular y «3 citas» en plural; sin citas no se avisa")
        void avisaDeLasCitas() {
            equipoDeAndres(camila);
            when(repositorio.citasPorAtender(EQUIPO)).thenReturn(1);
            servicio.cambiarActivo(andres.id(), false);

            ArgumentCaptor<AvisoSolicitado> aviso = ArgumentCaptor.forClass(AvisoSolicitado.class);
            verify(eventos).publishEvent(aviso.capture());
            assertThat(aviso.getValue().destinatarios()).containsExactly(ADMIN);
            assertThat(aviso.getValue().tipo()).isEqualTo("EQUIPO_SIN_PRODUCT_OWNER");
            assertThat(aviso.getValue().texto()).contains("1 cita por atender");
            assertThat(aviso.getValue().enlace()).isEqualTo("/admin/citas?sinEquipo=true");
        }

        @Test
        @DisplayName("Con 3 citas dice «3 citas por atender»; sin citas, o sin administradores activos, no se avisa")
        void pluralYSinAviso() {
            equipoDeAndres(camila);
            when(repositorio.citasPorAtender(EQUIPO)).thenReturn(3);
            servicio.cambiarActivo(andres.id(), false);
            ArgumentCaptor<AvisoSolicitado> aviso = ArgumentCaptor.forClass(AvisoSolicitado.class);
            verify(eventos).publishEvent(aviso.capture());
            assertThat(aviso.getValue().texto()).contains("3 citas por atender");

            org.mockito.Mockito.clearInvocations(eventos);
            Desarrollador otro = new Desarrollador(UUID.randomUUID(), "Otro Líder", "otro@coretechsolutions.co", "3001110000", Especialidad.BACKEND, UUID.randomUUID(), true, LocalDate.of(2026, 1, 1));
            registro.add(otro);
            UUID equipo2 = UUID.randomUUID();
            integrantes.add(new Integrante(equipo2, otro.id(), RolEquipo.PRODUCT_OWNER));
            when(repositorio.citasPorAtender(equipo2)).thenReturn(0);
            servicio.cambiarActivo(otro.id(), false);
            verify(eventos, never()).publishEvent(any());

            Desarrollador tercero = new Desarrollador(UUID.randomUUID(), "Tercer Líder", "tercero@coretechsolutions.co", "3001110001", Especialidad.BACKEND, null, true, LocalDate.of(2026, 1, 1));
            registro.add(tercero);
            UUID equipo3 = UUID.randomUUID();
            integrantes.add(new Integrante(equipo3, tercero.id(), RolEquipo.PRODUCT_OWNER));
            when(repositorio.citasPorAtender(equipo3)).thenReturn(2);
            when(directorio.administradoresActivos()).thenReturn(List.of());
            servicio.cambiarActivo(tercero.id(), false);
            verify(eventos, never()).publishEvent(any());
        }
    }

    // ------------------------------------------------------------------------------------------------ consultar

    @Nested
    @DisplayName("Consultar")
    class Consultar {

        @Test
        @DisplayName("listar muestra a cada desarrollador con su equipo (nombrado por su Product Owner), su rol y si tiene cuenta; obtener de uno inexistente es 404")
        void lista() {
            equipoDeAndres(camila);

            List<DesarrolladorVista> vistas = servicio.listar();

            assertThat(vistas).hasSize(4);
            DesarrolladorVista deAndres = vistas.stream().filter(v -> v.desarrolladorId().equals(andres.id())).findFirst().orElseThrow();
            assertThat(deAndres.tieneCuenta()).isTrue();
            assertThat(deAndres.rol()).isEqualTo(RolEquipo.PRODUCT_OWNER);
            assertThat(deAndres.equipo().productOwner()).isEqualTo("Andrés Pérez");
            DesarrolladorVista deCamila = vistas.stream().filter(v -> v.desarrolladorId().equals(camila.id())).findFirst().orElseThrow();
            assertThat(deCamila.equipo().productOwner()).isEqualTo("Andrés Pérez");
            DesarrolladorVista deDiana = vistas.stream().filter(v -> v.desarrolladorId().equals(diana.id())).findFirst().orElseThrow();
            assertThat(deDiana.equipo()).isNull();
            assertThat(deDiana.rol()).isNull();
            assertThatThrownBy(() -> servicio.obtener(UUID.randomUUID())).isInstanceOf(NoEncontradoException.class);
            assertThat(servicio.obtener(camila.id()).nombre()).isEqualTo("Camila Ruiz");
        }
    }
}
