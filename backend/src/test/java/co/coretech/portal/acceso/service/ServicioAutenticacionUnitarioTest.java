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
import java.util.Optional;
import java.util.Set;

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

import co.coretech.portal.acceso.ClienteRegistrado;
import co.coretech.portal.acceso.repository.RolRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.CuentaBloqueadaException;
import co.coretech.portal.compartido.exception.NoAutenticadoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Pruebas UNITARIAS de {@link ServicioAutenticacion} (HU-05 y HU-06; RN-18, RN-22; RNF-10) con los repositorios, la auditoría
 * y los eventos simulados con Mockito. El codificador es el BCrypt real con costo 4 (rápido) para que «coincide» y «no
 * coincide» sean de verdad. El reloj es fijo. Patrón AAA.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioAutenticacionUnitarioTest {

    static final Instant AHORA = Instant.parse("2031-03-04T13:00:00Z");
    static final String CLAVE = "Abcdef1!";

    @Mock
    UsuarioRepositorio usuarios;
    @Mock
    RolRepositorio roles;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;

    final PasswordEncoder codificador = new BCryptPasswordEncoder(4);
    final PoliticaContrasena politica = new PoliticaContrasena();
    Rol rolCliente;
    ServicioAutenticacion servicio;

    @BeforeEach
    void preparaLoComun() {
        rolCliente = mock(Rol.class);
        when(rolCliente.getCodigo()).thenReturn(Rol.CLIENTE);
        when(roles.findByCodigo(Rol.CLIENTE)).thenReturn(Optional.of(rolCliente));
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new ServicioAutenticacion(usuarios, roles, codificador, politica, auditoria, sesion,
                Clock.fixed(AHORA, ZoneId.of("America/Bogota")), eventos);
    }

    private Usuario cuenta(String email, String clave) {
        return Usuario.nuevoCliente(rolCliente, "Laura Gómez", email, "3001234567", null, codificador.encode(clave), AHORA);
    }

    private static NuevoCliente datos(String email, String clave, String confirmacion, boolean acepta) {
        return new NuevoCliente("  Laura Gómez ", email, " 3001234567 ", "  ", clave, confirmacion, acepta);
    }

    @Nested
    @DisplayName("Registro (HU-05)")
    class Registro {

        @Test
        @DisplayName("Crea la cuenta de cliente con el correo en minúscula y sin espacios, y publica la bienvenida")
        void creaLaCuenta() {
            when(usuarios.existeEmail("laura@correo.com")).thenReturn(false);

            Ingreso ingreso = servicio.registrar(datos("  Laura@Correo.COM ", CLAVE, CLAVE, true));

            ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarios).saveAndFlush(guardado.capture());
            assertThat(guardado.getValue().getEmail()).isEqualTo("laura@correo.com");
            assertThat(guardado.getValue().getNombre()).isEqualTo("Laura Gómez");
            assertThat(guardado.getValue().getEmpresa()).as("una empresa en blanco se guarda como nula").isNull();
            assertThat(guardado.getValue().getPasswordHash()).isNotEqualTo(CLAVE);
            assertThat(codificador.matches(CLAVE, guardado.getValue().getPasswordHash())).isTrue();
            assertThat(ingreso.rol()).isEqualTo(Rol.CLIENTE);
            assertThat(ingreso.debeCambiarPassword()).isFalse();
            verify(auditoria).registrarComo(eq(ingreso.usuarioId()), eq(CategoriaAuditoria.USUARIOS), any(), eq("usuario"), any(), anyMap());
            verify(eventos).publishEvent(any(ClienteRegistrado.class));
        }

        @Test
        @DisplayName("Sin aceptar la política de datos: 422 en el campo aceptaDatos y no guarda nada")
        void sinAceptarLaPolitica() {
            assertThatThrownBy(() -> servicio.registrar(datos("a@b.com", CLAVE, CLAVE, false)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.campo()).contains("aceptaDatos"));
            verify(usuarios, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Contraseña que no cumple RN-22: 422 y no guarda nada")
        void contrasenaDebil() {
            assertThatThrownBy(() -> servicio.registrar(datos("a@b.com", "abc", "abc", true)))
                    .isInstanceOf(ReglaNegocioException.class);
            verify(usuarios, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Correo repetido (sin distinguir mayúsculas): 409 en el campo email")
        void correoRepetido() {
            when(usuarios.existeEmail("laura@correo.com")).thenReturn(true);

            assertThatThrownBy(() -> servicio.registrar(datos("LAURA@correo.com", CLAVE, CLAVE, true)))
                    .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioAutenticacion.CORREO_REPETIDO));
            verify(eventos, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("Inicio de sesión (HU-06)")
    class InicioDeSesion {

        @Test
        @DisplayName("Con las credenciales correctas entra, reinicia los intentos y audita la sesión")
        void entra() {
            Usuario u = cuenta("laura@correo.com", CLAVE);
            u.registrarIntentoFallido(AHORA);
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));

            Ingreso ingreso = servicio.iniciarSesion(" Laura@correo.com ", CLAVE);

            assertThat(ingreso.usuarioId()).isEqualTo(u.getId());
            assertThat(u.getIntentosFallidos()).isZero();
            assertThat(u.getUltimoAccesoAt()).isEqualTo(AHORA);
            verify(auditoria).registrarComo(eq(u.getId()), eq(CategoriaAuditoria.SESIONES), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Un correo que no existe da el mismo 401 que una contraseña errada")
        void correoInexistente() {
            when(usuarios.buscarPorEmail("nadie@correo.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.iniciarSesion("nadie@correo.com", CLAVE))
                    .isInstanceOfSatisfying(NoAutenticadoException.class,
                            e -> assertThat(e.getMessage()).isEqualTo(ServicioAutenticacion.CREDENCIALES_INCORRECTAS));
            verify(auditoria).registrar(eq(CategoriaAuditoria.SEGURIDAD), any(), eq("usuario"), eq(null), anyMap());
        }

        @Test
        @DisplayName("Contraseña errada: 401, suma un intento y lo audita")
        void contrasenaErrada() {
            Usuario u = cuenta("laura@correo.com", CLAVE);
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> servicio.iniciarSesion("laura@correo.com", "Otra1234!"))
                    .isInstanceOf(NoAutenticadoException.class);

            assertThat(u.getIntentosFallidos()).isEqualTo(1);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SEGURIDAD), any(), eq("usuario"), any(), anyMap());
        }

        @Test
        @DisplayName("Valor límite: el 4.º intento fallido sigue dando 401; el 5.º bloquea 15 minutos (423)")
        void elQuintoIntentoBloquea() {
            Usuario u = cuenta("laura@correo.com", CLAVE);
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));

            for (int i = 1; i <= 4; i++) {
                assertThatThrownBy(() -> servicio.iniciarSesion("laura@correo.com", "Mala1234!")).isInstanceOf(NoAutenticadoException.class);
            }
            assertThat(u.getBloqueadoHasta()).isNull();

            assertThatThrownBy(() -> servicio.iniciarSesion("laura@correo.com", "Mala1234!")).isInstanceOf(CuentaBloqueadaException.class);
            assertThat(u.getBloqueadoHasta()).isEqualTo(AHORA.plus(Usuario.BLOQUEO));
        }

        @Test
        @DisplayName("Bloqueada, rechaza hasta la contraseña correcta (423) sin sumar intentos")
        void bloqueadaRechazaTodo() {
            Usuario u = cuenta("laura@correo.com", CLAVE);
            for (int i = 0; i < 5; i++) {
                u.registrarIntentoFallido(AHORA);
            }
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> servicio.iniciarSesion("laura@correo.com", CLAVE)).isInstanceOf(CuentaBloqueadaException.class);
            assertThat(u.getIntentosFallidos()).isEqualTo(5);
        }

        @Test
        @DisplayName("Pasados los 15 minutos el bloqueo se levanta y puede entrar")
        void pasadoElBloqueoEntra() {
            Usuario u = cuenta("laura@correo.com", CLAVE);
            for (int i = 0; i < 5; i++) {
                u.registrarIntentoFallido(AHORA.minus(Usuario.BLOQUEO).minusSeconds(1));
            }
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));

            Ingreso ingreso = servicio.iniciarSesion("laura@correo.com", CLAVE);

            assertThat(ingreso.usuarioId()).isEqualTo(u.getId());
            assertThat(u.getIntentosFallidos()).isZero();
        }

        @Test
        @DisplayName("Cuenta desactivada con la contraseña correcta: 403")
        void cuentaDesactivada() {
            Usuario u = cuenta("laura@correo.com", CLAVE);
            u.cambiarActivo(false, AHORA);
            when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> servicio.iniciarSesion("laura@correo.com", CLAVE))
                    .isInstanceOfSatisfying(ProhibidoException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioAutenticacion.CUENTA_DESACTIVADA));
        }
    }

    @Nested
    @DisplayName("Cambiar la contraseña (HU-06)")
    class CambiarContrasena {

        Usuario u;

        @BeforeEach
        void conSesion() {
            u = cuenta("laura@correo.com", CLAVE);
            when(sesion.usuario()).thenReturn(new UsuarioSesion(u.getId(), "Laura", "laura@correo.com", Rol.CLIENTE, Set.of(), false, 0));
            when(usuarios.buscarConRol(u.getId())).thenReturn(Optional.of(u));
        }

        @Test
        @DisplayName("Con la actual correcta y una nueva válida: cambia el hash, sube la versión de sesión y audita")
        void cambia() {
            Ingreso ingreso = servicio.cambiarPassword(CLAVE, "Nueva123#", "Nueva123#");

            assertThat(codificador.matches("Nueva123#", u.getPasswordHash())).isTrue();
            assertThat(u.getVersionSesion()).isEqualTo(1);
            assertThat(ingreso.versionSesion()).isEqualTo(1);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SEGURIDAD), any(), eq("usuario"), any(), anyMap());
        }

        @Test
        @DisplayName("La contraseña actual incorrecta da 422 en passwordActual")
        void actualIncorrecta() {
            assertThatThrownBy(() -> servicio.cambiarPassword("Equivocada1!", "Nueva123#", "Nueva123#"))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.campo()).contains("passwordActual"));
        }

        @Test
        @DisplayName("La nueva igual a la actual da 422 en passwordNueva")
        void igualALaActual() {
            assertThatThrownBy(() -> servicio.cambiarPassword(CLAVE, CLAVE, CLAVE))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.campo()).contains("passwordNueva");
                        assertThat(e.getMessage()).contains("distinta de la actual");
                    });
        }

        @Test
        @DisplayName("Con una temporal, el mensaje habla de la temporal y la cuenta queda libre al cambiarla")
        void conTemporal() {
            Usuario personal = Usuario.nuevoPersonal(rolCliente, "Andrés", "andres@coretechsolutions.co", "3001234567", codificador.encode("Temp123#"), AHORA);
            when(sesion.usuario()).thenReturn(new UsuarioSesion(personal.getId(), "Andrés", personal.getEmail(), Rol.CLIENTE, Set.of(), true, 0));
            when(usuarios.buscarConRol(personal.getId())).thenReturn(Optional.of(personal));

            assertThatThrownBy(() -> servicio.cambiarPassword("Temp123#", "Temp123#", "Temp123#"))
                    .hasMessageContaining("distinta de la temporal");

            Ingreso ingreso = servicio.cambiarPassword("Temp123#", "Nueva123#", "Nueva123#");
            assertThat(personal.isDebeCambiarPassword()).isFalse();
            assertThat(ingreso.debeCambiarPassword()).isFalse();
        }

        @Test
        @DisplayName("La nueva que no cumple RN-22 o no coincide con su confirmación da 422")
        void nuevaInvalida() {
            assertThatThrownBy(() -> servicio.cambiarPassword(CLAVE, "corta", "corta")).isInstanceOf(ReglaNegocioException.class);
            assertThatThrownBy(() -> servicio.cambiarPassword(CLAVE, "Nueva123#", "Nueva123?")).isInstanceOf(ReglaNegocioException.class);
            assertThat(u.getVersionSesion()).isZero();
        }

        @Test
        @DisplayName("Si la cuenta ya no existe, la sesión venció (401)")
        void sesionVencida() {
            when(usuarios.buscarConRol(u.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.cambiarPassword(CLAVE, "Nueva123#", "Nueva123#")).isInstanceOf(NoAutenticadoException.class);
        }
    }

    @Test
    @DisplayName("normalizarEmail: recorta, pasa a minúscula y trata el nulo como vacío")
    void normalizaElCorreo() {
        assertThat(ServicioAutenticacion.normalizarEmail("  ANA@Correo.Com ")).isEqualTo("ana@correo.com");
        assertThat(ServicioAutenticacion.normalizarEmail(null)).isEmpty();
    }
}
