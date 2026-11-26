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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import co.coretech.portal.acceso.repository.TokenRecuperacionRepositorio;
import co.coretech.portal.acceso.repository.UsuarioRepositorio;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.TokenRecuperacion;
import co.coretech.portal.acceso.domain.Usuario;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;

/**
 * Pruebas UNITARIAS de {@link ServicioRecuperacion} (RF-04, RNF-10): el enlace es de un solo uso, vale 30 minutos, se guarda
 * solo su huella SHA-256 (nunca el token) y la respuesta es la misma exista o no el correo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioRecuperacionUnitarioTest {

    static final Instant AHORA = Instant.parse("2031-03-04T13:00:00Z");

    @Mock
    UsuarioRepositorio usuarios;
    @Mock
    TokenRecuperacionRepositorio tokens;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    ApplicationEventPublisher eventos;
    @Mock
    PropiedadesCoretech propiedades;

    final PasswordEncoder codificador = new BCryptPasswordEncoder(4);
    Usuario laura;
    ServicioRecuperacion servicio;

    @BeforeEach
    void preparaLoComun() {
        Rol cliente = mock(Rol.class);
        when(propiedades.urlPortal()).thenReturn("https://portal.coretech.test");
        laura = Usuario.nuevoCliente(cliente, "Laura Gómez", "laura@correo.com", "3001234567", null, codificador.encode("Vieja123#"), AHORA);
        when(usuarios.buscarPorEmail("laura@correo.com")).thenReturn(Optional.of(laura));
        when(usuarios.findById(laura.getId())).thenReturn(Optional.of(laura));
        servicio = new ServicioRecuperacion(usuarios, tokens, codificador, new PoliticaContrasena(), auditoria, eventos, propiedades,
                Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    @Test
    @DisplayName("Pide el enlace con una cuenta activa: borra los pendientes, guarda solo la huella y publica el correo con el enlace")
    void emiteElEnlace() {
        servicio.solicitarPorCorreo(" LAURA@correo.com ");

        ArgumentCaptor<TokenRecuperacion> guardado = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokens).borrarPendientes(laura.getId());
        verify(tokens).save(guardado.capture());
        ArgumentCaptor<EnlaceRecuperacionEmitido> evento = ArgumentCaptor.forClass(EnlaceRecuperacionEmitido.class);
        verify(eventos).publishEvent(evento.capture());
        String enlace = evento.getValue().enlace();
        assertThat(enlace).startsWith("https://portal.coretech.test/restablecer?token=");
        String token = enlace.substring(enlace.indexOf("token=") + 6);
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(guardado.getValue()).as("se guarda la huella, no el token").extracting("tokenHash").isEqualTo(ServicioRecuperacion.hash(token)).isNotEqualTo(token);
        verify(auditoria).registrarComo(eq(laura.getId()), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("Dos solicitudes producen enlaces distintos")
    void losEnlacesSonAleatorios() {
        servicio.solicitarPorCorreo("laura@correo.com");
        servicio.solicitarPorCorreo("laura@correo.com");

        ArgumentCaptor<EnlaceRecuperacionEmitido> evento = ArgumentCaptor.forClass(EnlaceRecuperacionEmitido.class);
        verify(eventos, org.mockito.Mockito.times(2)).publishEvent(evento.capture());
        assertThat(evento.getAllValues().get(0).enlace()).isNotEqualTo(evento.getAllValues().get(1).enlace());
    }

    @Test
    @DisplayName("Un correo que no existe responde igual (sin error) y no envía nada")
    void correoInexistenteNoDiceNada() {
        when(usuarios.buscarPorEmail("nadie@correo.com")).thenReturn(Optional.empty());

        servicio.solicitarPorCorreo("nadie@correo.com");

        verify(tokens, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Una cuenta desactivada tampoco recibe el enlace")
    void cuentaDesactivadaNoRecibe() {
        laura.cambiarActivo(false, AHORA);

        servicio.solicitarPorCorreo("laura@correo.com");

        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("El administrador envía el enlace y recibe el correo de la cuenta; la auditoría lleva al administrador como autor")
    void administradorEnvia() {
        String correo = servicio.solicitarPorAdministrador(laura.getId());

        assertThat(correo).isEqualTo("laura@correo.com");
        verify(auditoria).registrar(any(), any(), eq("usuario"), eq(laura.getId().toString()));
        verify(eventos).publishEvent(any(EnlaceRecuperacionEmitido.class));
    }

    @Test
    @DisplayName("El administrador: cuenta que no existe es 404 y cuenta desactivada es 409")
    void administradorConCuentaInvalida() {
        UUID otro = UUID.randomUUID();
        when(usuarios.findById(otro)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.solicitarPorAdministrador(otro)).isInstanceOf(NoEncontradoException.class);

        laura.cambiarActivo(false, AHORA);
        assertThatThrownBy(() -> servicio.solicitarPorAdministrador(laura.getId())).isInstanceOf(ConflictoException.class);
    }

    private TokenRecuperacion tokenValido(String token, Instant creadoEn) {
        TokenRecuperacion t = TokenRecuperacion.nuevo(laura.getId(), ServicioRecuperacion.hash(token), creadoEn);
        when(tokens.findByTokenHash(ServicioRecuperacion.hash(token))).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("Restablecer con un enlace válido: cambia la contraseña, sube la versión de sesión y deja el enlace usado")
    void restablece() {
        TokenRecuperacion t = tokenValido("abc", AHORA.minusSeconds(60));

        servicio.restablecer("abc", "Nueva123#", "Nueva123#");

        assertThat(codificador.matches("Nueva123#", laura.getPasswordHash())).isTrue();
        assertThat(laura.getVersionSesion()).isEqualTo(1);
        assertThat(t.esValido(AHORA)).as("un enlace sirve una sola vez").isFalse();
        verify(auditoria).registrarComo(eq(laura.getId()), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("Valor límite: a los 29 min 59 s sirve; a los 30 min ya no")
    void vigenciaDe30Minutos() {
        tokenValido("justo", AHORA.minus(TokenRecuperacion.VIGENCIA).plusSeconds(1));
        servicio.restablecer("justo", "Nueva123#", "Nueva123#");

        tokenValido("vencido", AHORA.minus(TokenRecuperacion.VIGENCIA));
        assertThatThrownBy(() -> servicio.restablecer("vencido", "Otra1234#", "Otra1234#"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioRecuperacion.ENLACE_INVALIDO));
    }

    @Test
    @DisplayName("Un enlace ya usado, o que no existe, da 422 en el campo token")
    void enlaceUsadoOInexistente() {
        TokenRecuperacion usado = tokenValido("usado", AHORA.minusSeconds(60));
        usado.usar(AHORA.minusSeconds(30));

        assertThatThrownBy(() -> servicio.restablecer("usado", "Nueva123#", "Nueva123#"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.campo()).contains("token"));
        when(tokens.findByTokenHash(ServicioRecuperacion.hash("falso"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.restablecer("falso", "Nueva123#", "Nueva123#")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Si la cuenta se desactivó después de pedir el enlace, el enlace ya no sirve")
    void cuentaDesactivadaDespues() {
        tokenValido("abc", AHORA.minusSeconds(60));
        laura.cambiarActivo(false, AHORA);

        assertThatThrownBy(() -> servicio.restablecer("abc", "Nueva123#", "Nueva123#")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Una contraseña nueva que no cumple RN-22 se rechaza y el enlace NO se gasta")
    void contrasenaDebilNoGastaElEnlace() {
        TokenRecuperacion t = tokenValido("abc", AHORA.minusSeconds(60));

        assertThatThrownBy(() -> servicio.restablecer("abc", "corta", "corta")).isInstanceOf(ReglaNegocioException.class);

        assertThat(t.esValido(AHORA)).isTrue();
        assertThat(laura.getVersionSesion()).isZero();
    }

    @Test
    @DisplayName("hash: SHA-256 en hexadecimal (64 caracteres), siempre igual para el mismo texto")
    void hashDeterminista() {
        assertThat(ServicioRecuperacion.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ServicioRecuperacion.hash("abc")).hasSize(64);
    }
}
