package co.coretech.portal.acceso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.coretech.portal.acceso.CitasDeUsuarios;
import co.coretech.portal.acceso.EquiposDeUsuarios;
import co.coretech.portal.acceso.ProyectosDeUsuarios;
import co.coretech.portal.acceso.service.ServicioUsuarios.DatosCuenta;
import co.coretech.portal.acceso.repository.CuentaExternaRepositorio;
import co.coretech.portal.acceso.repository.RolRepositorio;
import co.coretech.portal.acceso.repository.TokenRecuperacionRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Pruebas UNITARIAS de {@link ServicioUsuarios} (HU-20; RN-18, RN-22, RN-26; RF-05, RF-46): crear cuentas de administrador con
 * contraseña temporal, editar, activar o desactivar, desbloquear y anonimizar los datos de un cliente (Ley 1581). Los repositorios,
 * la auditoría y los eventos van simulados; las entidades {@link Usuario} son reales y el codificador es BCrypt de costo 4.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioUsuariosUnitarioTest {

    static final Instant AHORA = Instant.parse("2031-03-04T13:00:00Z");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    @Mock
    UsuarioRepositorio usuarios;
    @Mock
    RolRepositorio roles;
    @Mock
    TokenRecuperacionRepositorio tokens;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;
    @Mock
    PropiedadesCoretech propiedades;
    @Mock
    CitasDeUsuarios citas;
    @Mock
    ProyectosDeUsuarios proyectos;
    @Mock
    EquiposDeUsuarios equipos;
    @Mock
    CuentaExternaRepositorio cuentasExternas;

    final PasswordEncoder codificador = new BCryptPasswordEncoder(4);
    Rol rolAdmin;
    Rol rolPo;
    Rol rolCliente;
    ServicioUsuarios servicio;

    static Rol rol(String codigo) {
        Rol r = mock(Rol.class);
        when(r.getCodigo()).thenReturn(codigo);
        return r;
    }

    @BeforeEach
    void preparaLoComun() {
        rolAdmin = rol(Rol.ADMINISTRADOR);
        rolPo = rol(Rol.PRODUCT_OWNER);
        rolCliente = rol(Rol.CLIENTE);
        when(roles.findByCodigo(Rol.ADMINISTRADOR)).thenReturn(Optional.of(rolAdmin));
        when(roles.findByCodigo(Rol.PRODUCT_OWNER)).thenReturn(Optional.of(rolPo));
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarios.buscarPorEmail(any())).thenReturn(Optional.empty());
        when(propiedades.urlPortal()).thenReturn("https://portal.coretech.test");
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of("seguridad.cuentas"), false, 0));
        when(citas.porCliente()).thenReturn(Map.of());
        when(proyectos.porCliente()).thenReturn(Map.of());
        when(proyectos.activosPorEquipo()).thenReturn(Map.of());
        when(equipos.porProductOwner()).thenReturn(Map.of());
        servicio = new ServicioUsuarios(usuarios, roles, tokens, codificador, auditoria, sesion, eventos, propiedades, citas, proyectos, equipos,
                Clock.fixed(AHORA, ZoneId.of("America/Bogota")), cuentasExternas);
    }

    // ---- ayudas ----

    Usuario cliente(String nombre, String email) {
        Usuario u = Usuario.nuevoCliente(rolCliente, nombre, email, "3001234567", "Salón Laura", codificador.encode("Abcdef1!"), AHORA.minusSeconds(1000));
        when(usuarios.buscarConRol(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    Usuario personal(Rol rol, String nombre, String email) {
        Usuario u = Usuario.nuevoPersonal(rol, nombre, email, "3009876543", codificador.encode("Temp123#"), AHORA.minusSeconds(1000));
        when(usuarios.buscarConRol(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    static DatosCuenta datos(String nombre, String celular, String email) {
        return new DatosCuenta(nombre, celular, email);
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    // ------------------------------------------------------------------------------------------------ crear administrador

    @Nested
    @DisplayName("Crear una cuenta de administrador (RF-05)")
    class CrearAdministrador {

        @Test
        @DisplayName("Crea la cuenta con el rol de administrador, el correo en minúscula y una contraseña temporal que debe cambiar; se envía por correo, no se muestra")
        void crea() {
            var g = servicio.crearAdministrador(datos("  Patricia   Rojas ", "3001112233", " Patricia.Rojas@CoretechSolutions.co "));

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).saveAndFlush(guardado.capture());
            Usuario u = guardado.getValue();
            assertThat(u.getNombre()).as("espacios colapsados").isEqualTo("Patricia Rojas");
            assertThat(u.getEmail()).isEqualTo("patricia.rojas@coretechsolutions.co");
            assertThat(u.isDebeCambiarPassword()).isTrue();
            assertThat(g.rol()).isEqualTo(Rol.ADMINISTRADOR);

            ArgumentCaptor<CuentaPersonalCreada> evento = ArgumentCaptor.forClass(CuentaPersonalCreada.class);
            verify(eventos).publishEvent(evento.capture());
            assertThat(evento.getValue().urlIngreso()).isEqualTo("https://portal.coretech.test/ingresar");
            assertThat(codificador.matches(evento.getValue().claveTemporal(), u.getPasswordHash())).as("la clave del correo es la que quedó guardada").isTrue();
            assertThat(u.getPasswordHash()).isNotEqualTo(evento.getValue().claveTemporal());
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Creó una cuenta de administrador"), eq("usuario"), eq(u.getId().toString()), anyMap());
        }

        @Test
        @DisplayName("Los datos vacíos dan un error por cada campo a la vez y no se guarda nada")
        void vacios() {
            assertThatThrownBy(() -> servicio.crearAdministrador(datos(null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("nombre", "celular", "email"));
            verify(usuarios, never()).saveAndFlush(any());
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Valores límite del nombre del personal (D-40): 4 caracteres no, 5 sí, 60 sí, 61 no; con números o símbolos no")
        void limitesDelNombre() {
            assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana ", "3001112233", "ana@coretechsolutions.co")))
                    .as("«Ana » se recorta a 3 caracteres").isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe nombre y apellido."));
            assertThat(servicio.crearAdministrador(datos("Ana B", "3001112233", "ana@coretechsolutions.co")).nombre()).isEqualTo("Ana B");
            assertThat(servicio.crearAdministrador(datos("a".repeat(60), "3001112233", "b@coretechsolutions.co")).nombre()).hasSize(60);
            assertThatThrownBy(() -> servicio.crearAdministrador(datos("a".repeat(61), "3001112233", "c@coretechsolutions.co")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 60 caracteres."));
            assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana 123", "3001112233", "d@coretechsolutions.co")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe solo letras y espacios."));
        }

        @Test
        @DisplayName("El celular debe tener 10 dígitos y empezar por 3")
        void celular() {
            for (String malo : new String[] { "2001112233", "300111223", "30011122334", "30011122a3", "300 111 2233" }) {
                assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana Beltrán", malo, "ana@coretechsolutions.co"))).as(malo)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("celular"));
            }
        }

        @Test
        @DisplayName("El correo del personal debe ser el corporativo; uno sin formato, o de más de 120 caracteres, también se rechaza")
        void correo() {
            assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana Beltrán", "3001112233", "ana@gmail.com")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).contains("@coretechsolutions.co"));
            assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana Beltrán", "3001112233", "sin-arroba")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).startsWith("Escribe un correo válido"));
            assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana Beltrán", "3001112233", "a".repeat(100) + "@coretechsolutions.co")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 120 caracteres."));
        }

        @Test
        @DisplayName("Un correo ya registrado (sin distinguir mayúsculas) da 409 en el campo email (RN-18)")
        void correoRepetido() {
            Usuario existente = personal(rolAdmin, "Otro Admin", "ana@coretechsolutions.co");
            when(usuarios.buscarPorEmail("ana@coretechsolutions.co")).thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> servicio.crearAdministrador(datos("Ana Beltrán", "3001112233", "ANA@coretechsolutions.co")))
                    .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioAutenticacion.CORREO_REPETIDO));
            verify(usuarios, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("La contraseña temporal tiene la forma «Ct-XXXX-XXXX», cumple RN-22 y es distinta cada vez; sin letras que se confunden (I, O, l, 0, 1)")
        void claveTemporal() {
            Set<String> claves = new HashSet<>();
            for (int i = 0; i < 300; i++) {
                String clave = servicio.claveTemporal();
                assertThat(clave).matches("Ct-[A-HJ-NP-Za-km-z2-9]{4}-[A-HJ-NP-Za-km-z2-9]{4}");
                assertThat(PoliticaContrasena.cumple(clave)).as(clave).isTrue();
                claves.add(clave);
            }
            assertThat(claves).hasSizeGreaterThan(290);
        }
    }

    // ------------------------------------------------------------------------------------------------ editar

    @Nested
    @DisplayName("Editar una cuenta (HU-20)")
    class Editar {

        @Test
        @DisplayName("Cambiar el nombre de un cliente: se guarda y la auditoría anota solo lo que cambió, con el antes y el después")
        void cambiaElNombre() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");

            var g = servicio.editar(u.getId(), datos("Laura Gómez Pérez", "3001234567", "laura@correo.com"));

            assertThat(g.nombre()).isEqualTo("Laura Gómez Pérez");
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, co.coretech.portal.compartido.domain.CambioDeValor>> cambios = ArgumentCaptor.forClass(Map.class);
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Editó los datos de una cuenta"), eq("usuario"), eq(u.getId().toString()), cambios.capture());
            assertThat(cambios.getValue()).containsOnlyKeys("nombre");
        }

        @Test
        @DisplayName("Sin ningún cambio no se audita ni se escribe")
        void sinCambios() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");

            servicio.editar(u.getId(), datos("Laura Gómez", "3001234567", "laura@correo.com"));

            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
            verify(usuarios, never()).flush();
        }

        @Test
        @DisplayName("Un cliente admite nombres de 3 caracteres (D-40) y cualquier correo; el personal exige 5 y el corporativo")
        void reglasPorRol() {
            Usuario c = cliente("Laura Gómez", "laura@correo.com");
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");

            assertThat(servicio.editar(c.getId(), datos("Ana", "3001234567", "ana@otrocorreo.com")).nombre()).isEqualTo("Ana");
            assertThatThrownBy(() -> servicio.editar(po.getId(), datos("Ana", "3009876543", "andres@coretechsolutions.co"))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.editar(po.getId(), datos("Andrés Pérez", "3009876543", "andres@gmail.com"))).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("Conservar el propio correo no es un duplicado; el de otra cuenta sí (409)")
        void correoPropioYAjeno() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));
            servicio.editar(u.getId(), datos("Laura G", "3001234567", "laura@correo.com"));

            Usuario otro = cliente("Tomás Ruiz", "tomas@correo.com");
            when(usuarios.buscarPorEmail("tomas@correo.com")).thenReturn(Optional.of(otro));
            assertThatThrownBy(() -> servicio.editar(u.getId(), datos("Laura G", "3001234567", "tomas@correo.com"))).isInstanceOf(ConflictoException.class);
        }

        @Test
        @DisplayName("Una cuenta anonimizada ya no se edita (409) y una que no existe es 404")
        void anonimizadaOInexistente() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");
            u.anonimizar("Cliente anónimo", "anonimo-x@anonimo.invalid", "3000000000", "hash", AHORA);
            assertThatThrownBy(() -> servicio.editar(u.getId(), datos("Laura Gómez", "3001234567", "laura@correo.com"))).isInstanceOf(ConflictoException.class);

            UUID otro = UUID.randomUUID();
            when(usuarios.buscarConRol(otro)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.editar(otro, datos("Laura Gómez", "3001234567", "laura@correo.com")))
                    .isInstanceOf(NoEncontradoException.class).hasMessage(ServicioUsuarios.NO_ENCONTRADO);
        }
    }

    // ------------------------------------------------------------------------------------------------ activar o desactivar

    @Nested
    @DisplayName("Activar o desactivar una cuenta (HU-20; RN-26)")
    class Activar {

        @Test
        @DisplayName("Desactivar una cuenta la deja inactiva y lo audita con el antes y el después")
        void desactiva() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");

            var g = servicio.cambiarActivo(u.getId(), false);

            assertThat(g.activo()).isFalse();
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Desactivó la cuenta"), eq("usuario"), eq(u.getId().toString()), anyMap());
        }

        @Test
        @DisplayName("Pedir el estado que ya tiene no hace nada: ni cambia, ni audita")
        void mismoEstado() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");

            servicio.cambiarActivo(u.getId(), true);

            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Nadie desactiva su propia cuenta (422 en el campo activo)")
        void nadieSeDesactivaASiMismo() {
            Usuario yo = personal(rolAdmin, "Patricia Rojas", "patricia@coretechsolutions.co");
            when(sesion.usuario()).thenReturn(new UsuarioSesion(yo.getId(), "Patricia", yo.getEmail(), "ADMINISTRADOR", Set.of(), false, 0));

            assertThatThrownBy(() -> servicio.cambiarActivo(yo.getId(), false))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.getMessage()).isEqualTo(ServicioUsuarios.DESACTIVARSE_A_SI_MISMO);
                        assertThat(e.campo()).contains("activo");
                    });
            assertThat(yo.isActivo()).isTrue();
        }

        @Test
        @DisplayName("Un Product Owner con proyectos activos no se desactiva sin reemplazarlo (409, RN-26); sin proyectos activos sí")
        void productOwnerConProyectos() {
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");
            when(equipos.porProductOwner()).thenReturn(Map.of(po.getId(), new EquiposDeUsuarios.Equipo(EQUIPO, 3)));
            when(proyectos.activosPorEquipo()).thenReturn(Map.of(EQUIPO, 2));

            assertThatThrownBy(() -> servicio.cambiarActivo(po.getId(), false)).isInstanceOf(ConflictoException.class).hasMessage(ServicioUsuarios.PO_CON_PROYECTOS);
            assertThat(po.isActivo()).isTrue();

            when(proyectos.activosPorEquipo()).thenReturn(Map.of(EQUIPO, 0));
            assertThat(servicio.cambiarActivo(po.getId(), false).activo()).isFalse();
        }

        @Test
        @DisplayName("Una cuenta anonimizada no se puede volver a activar (409)")
        void anonimizadaNoSeActiva() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");
            u.anonimizar("Cliente anónimo", "anonimo-x@anonimo.invalid", "3000000000", "hash", AHORA);

            assertThatThrownBy(() -> servicio.cambiarActivo(u.getId(), true)).isInstanceOf(ConflictoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ cuenta del Product Owner

    @Nested
    @DisplayName("Cuenta del Product Owner (HU-25, HU-26)")
    class ProductOwner {

        @Test
        @DisplayName("habilitar sin cuenta previa crea la del Product Owner con contraseña temporal por correo")
        void creaLaCuenta() {
            UUID id = servicio.habilitar(null, "Andrés Pérez", "andres@coretechsolutions.co", "3009876543");

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).saveAndFlush(guardado.capture());
            assertThat(guardado.getValue().getId()).isEqualTo(id);
            assertThat(guardado.getValue().isDebeCambiarPassword()).isTrue();
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Creó la cuenta de un desarrollador (Product Owner)"), eq("usuario"), eq(id.toString()), anyMap());
            verify(eventos).publishEvent(any(CuentaPersonalCreada.class));
        }

        @Test
        @DisplayName("habilitar con una cuenta existente la reactiva con una contraseña temporal nueva")
        void reactivaLaCuenta() {
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");
            po.cambiarActivo(false, AHORA);
            String hashAnterior = po.getPasswordHash();

            servicio.habilitar(po.getId(), "Andrés Pérez", "andres@coretechsolutions.co", "3009876543");

            assertThat(po.isActivo()).isTrue();
            assertThat(po.isDebeCambiarPassword()).isTrue();
            assertThat(po.getPasswordHash()).isNotEqualTo(hashAnterior);
            verify(usuarios, never()).saveAndFlush(any());
            verify(eventos).publishEvent(any(CuentaPersonalCreada.class));
        }

        @Test
        @DisplayName("bloquear desactiva la cuenta y lo audita; si ya estaba inactiva no hace nada")
        void bloquear() {
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");

            servicio.bloquear(po.getId());
            assertThat(po.isActivo()).isFalse();
            servicio.bloquear(po.getId());

            verify(auditoria, org.mockito.Mockito.times(1)).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Bloqueó la cuenta de un desarrollador (Product Owner)"), any(), any(), anyMap());
        }

        @Test
        @DisplayName("actualizarDatos edita nombre, correo y celular con las mismas reglas del personal")
        void actualizaDatos() {
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");

            servicio.actualizarDatos(po.getId(), "Andrés Felipe Pérez", "andres@coretechsolutions.co", "3009876543");

            assertThat(po.getNombre()).isEqualTo("Andrés Felipe Pérez");
            assertThatThrownBy(() -> servicio.actualizarDatos(po.getId(), "Andrés Felipe Pérez", "andres@gmail.com", "3009876543")).isInstanceOf(ValidacionException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ desbloquear y anonimizar

    @Nested
    @DisplayName("Desbloquear y anonimizar")
    class DesbloquearYAnonimizar {

        @Test
        @DisplayName("Desbloquear una cuenta bloqueada por intentos fallidos la deja libre y lo audita en seguridad; una que no está bloqueada da 409")
        void desbloquea() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");
            assertThatThrownBy(() -> servicio.desbloquear(u.getId())).isInstanceOf(ConflictoException.class).hasMessage(ServicioUsuarios.NO_BLOQUEADA);

            for (int i = 0; i < Usuario.MAX_INTENTOS; i++) {
                u.registrarIntentoFallido(AHORA);
            }
            assertThat(u.estaBloqueada(AHORA)).isTrue();

            var g = servicio.desbloquear(u.getId());

            assertThat(g.bloqueadaHasta()).isNull();
            assertThat(u.getIntentosFallidos()).isZero();
            verify(auditoria).registrar(eq(CategoriaAuditoria.SEGURIDAD), eq("Desbloqueó la cuenta"), eq("usuario"), eq(u.getId().toString()), anyMap());
        }

        @Test
        @DisplayName("Anonimizar a un cliente: nombre, correo y celular pasan a valores anónimos, queda inactivo, se borran sus enlaces de recuperación y sus vínculos con Google o Microsoft")
        void anonimiza() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");

            var g = servicio.anonimizar(u.getId());

            assertThat(g.nombre()).isEqualTo(ServicioUsuarios.NOMBRE_ANONIMO);
            assertThat(g.email()).isEqualTo("anonimo-" + u.getId().toString().replace("-", "") + ServicioUsuarios.DOMINIO_ANONIMO);
            assertThat(g.celular()).isEqualTo(ServicioUsuarios.CELULAR_ANONIMO);
            assertThat(g.activo()).isFalse();
            assertThat(g.anonimizada()).isTrue();
            verify(tokens).borrarPendientes(u.getId());
            verify(cuentasExternas).borrarDeUsuario(u.getId());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, co.coretech.portal.compartido.domain.CambioDeValor>> cambios = ArgumentCaptor.forClass(Map.class);
            verify(auditoria).registrar(eq(CategoriaAuditoria.USUARIOS), eq("Anonimizó los datos de un cliente"), eq("usuario"), eq(u.getId().toString()), cambios.capture());
            assertThat(cambios.getValue().toString()).as("la auditoría registra el hecho, no los datos que se borran").doesNotContain("Laura").doesNotContain("laura@correo.com");
        }

        @Test
        @DisplayName("La contraseña de una cuenta anonimizada queda inservible: no coincide con ninguna conocida")
        void claveInservible() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");

            servicio.anonimizar(u.getId());

            assertThat(codificador.matches("Abcdef1!", u.getPasswordHash())).isFalse();
        }

        @Test
        @DisplayName("Solo se anonimiza a un cliente (422) y una cuenta ya anonimizada da 409")
        void soloClientes() {
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");
            assertThatThrownBy(() -> servicio.anonimizar(po.getId())).isInstanceOf(ReglaNegocioException.class).hasMessage(ServicioUsuarios.SOLO_CLIENTES);

            Usuario u = cliente("Laura Gómez", "laura@correo.com");
            servicio.anonimizar(u.getId());
            assertThatThrownBy(() -> servicio.anonimizar(u.getId())).isInstanceOf(ConflictoException.class).hasMessage(ServicioUsuarios.ANONIMIZADA);
            verify(tokens, org.mockito.Mockito.times(1)).borrarPendientes(any());
        }
    }

    // ------------------------------------------------------------------------------------------------ listar

    @Nested
    @DisplayName("Listar las cuentas")
    class Listar {

        @Test
        @DisplayName("Primero los administradores, luego los Product Owners y al final los clientes; cada grupo por nombre sin distinguir mayúsculas")
        void orden() {
            Usuario cliente2 = cliente("zoe Torres", "zoe@correo.com");
            Usuario cliente1 = cliente("Ana Ruiz", "ana@correo.com");
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");
            Usuario admin = personal(rolAdmin, "Patricia Rojas", "patricia@coretechsolutions.co");
            when(usuarios.listarConRol()).thenReturn(List.of(cliente2, po, cliente1, admin));

            assertThat(servicio.listar()).extracting(UsuarioGestion::nombre).containsExactly("Patricia Rojas", "Andrés Pérez", "Ana Ruiz", "zoe Torres");
        }

        @Test
        @DisplayName("La actividad de un cliente trae sus citas por estado y sus proyectos; la de un Product Owner, su equipo con «n de m» proyectos activos")
        void actividad() {
            Usuario c = cliente("Laura Gómez", "laura@correo.com");
            Usuario po = personal(rolPo, "Andrés Pérez", "andres@coretechsolutions.co");
            when(usuarios.listarConRol()).thenReturn(List.of(c, po));
            when(citas.porCliente()).thenReturn(Map.of(c.getId(), new CitasDeUsuarios.Conteo(1, 2, 3, 4)));
            when(proyectos.porCliente()).thenReturn(Map.of(c.getId(), new ProyectosDeUsuarios.Conteo(2, 5)));
            when(equipos.porProductOwner()).thenReturn(Map.of(po.getId(), new EquiposDeUsuarios.Equipo(EQUIPO, 3)));
            when(proyectos.activosPorEquipo()).thenReturn(Map.of(EQUIPO, 2));

            List<UsuarioGestion> lista = servicio.listar();

            UsuarioGestion deLaPo = lista.get(0);
            UsuarioGestion delCliente = lista.get(1);
            assertThat(deLaPo.actividad().equipo().proyectosActivos()).isEqualTo(2);
            assertThat(deLaPo.actividad().equipo().maxProyectos()).isEqualTo(3);
            assertThat(delCliente.actividad().citasPendientes()).isEqualTo(1);
            assertThat(delCliente.actividad().citasConfirmadas()).isEqualTo(2);
            assertThat(delCliente.actividad().citasAtendidas()).isEqualTo(3);
            assertThat(delCliente.actividad().citasCanceladas()).isEqualTo(4);
            assertThat(delCliente.actividad().proyectosEnCurso()).isEqualTo(2);
            assertThat(delCliente.actividad().proyectosFinalizados()).isEqualTo(5);
        }

        @Test
        @DisplayName("El bloqueo solo aparece mientras sigue vigente y obtener de una cuenta que no existe es 404")
        void bloqueoVigenteYInexistente() {
            Usuario u = cliente("Laura Gómez", "laura@correo.com");
            assertThat(servicio.obtener(u.getId()).bloqueadaHasta()).isNull();
            for (int i = 0; i < Usuario.MAX_INTENTOS; i++) {
                u.registrarIntentoFallido(AHORA);
            }
            assertThat(servicio.obtener(u.getId()).bloqueadaHasta()).isEqualTo(AHORA.plus(Usuario.BLOQUEO));

            UUID otro = UUID.randomUUID();
            when(usuarios.buscarConRol(otro)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.obtener(otro)).isInstanceOf(NoEncontradoException.class);
        }
    }
}
