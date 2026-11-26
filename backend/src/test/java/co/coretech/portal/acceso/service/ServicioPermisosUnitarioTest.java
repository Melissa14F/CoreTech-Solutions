package co.coretech.portal.acceso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.coretech.portal.acceso.service.ServicioPermisos.Cambio;
import co.coretech.portal.acceso.service.ServicioPermisos.Celda;
import co.coretech.portal.acceso.repository.PermisosJdbc;
import co.coretech.portal.acceso.repository.PermisosJdbc.Asignacion;
import co.coretech.portal.acceso.repository.PermisosJdbc.Permiso;
import co.coretech.portal.acceso.repository.PermisosJdbc.Veto;
import co.coretech.portal.acceso.repository.RolRepositorio;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ReglaNegocioException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Pruebas UNITARIAS de {@link ServicioPermisos} (HU-21; RN-19): la matriz de roles y permisos y guardar sus cambios todos
 * juntos. Los permisos que fija una regla del negocio no se pueden quitar, los vetados no se pueden dar y el administrador
 * conserva siempre todos. La base y la auditoría van simuladas.
 * <p>
 * Matriz de prueba: tres roles (cliente, Product Owner, administrador) y cuatro permisos. El cliente tiene «citas.agendar»
 * (cambiable) y tiene vetado «seguridad.permisos»; el Product Owner tiene «proyectos.registrar_avance» fijo por RN-07; el
 * administrador tiene los cuatro, fijos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioPermisosUnitarioTest {

    static final short CLIENTE = 1;
    static final short PRODUCT_OWNER = 2;
    static final short ADMINISTRADOR = 3;
    static final short AGENDAR = 1;
    static final short REGISTRAR_AVANCE = 2;
    static final short SEGURIDAD = 3;
    static final short EDITAR_SITIO = 4;

    @Mock
    PermisosJdbc permisos;
    @Mock
    RolRepositorio roles;
    @Mock
    RegistroAuditoria auditoria;

    ServicioPermisos servicio;

    static Rol rol(short id, String codigo, String nombre) {
        Rol r = mock(Rol.class);
        when(r.getId()).thenReturn(id);
        when(r.getCodigo()).thenReturn(codigo);
        when(r.getNombre()).thenReturn(nombre);
        return r;
    }

    @BeforeEach
    void preparaLoComun() {
        // findAll en un orden distinto al de las columnas: el servicio debe ordenar
        Rol administrador = rol(ADMINISTRADOR, Rol.ADMINISTRADOR, "Administrador");
        Rol cliente = rol(CLIENTE, Rol.CLIENTE, "Cliente");
        Rol productOwner = rol(PRODUCT_OWNER, Rol.PRODUCT_OWNER, "Product Owner");
        when(roles.findAll()).thenReturn(List.of(administrador, cliente, productOwner));
        when(permisos.permisos()).thenReturn(List.of(
                new Permiso(AGENDAR, "citas.agendar", "citas", "Agendar citas", "El cliente agenda su cita", null),
                new Permiso(REGISTRAR_AVANCE, "proyectos.registrar_avance", "proyectos", "Registrar avances", "El equipo marca pasos", "RN-07"),
                new Permiso(SEGURIDAD, "seguridad.permisos", "seguridad", "Cambiar permisos", "Edita la matriz", null),
                new Permiso(EDITAR_SITIO, "sitio.editar", "sitio", "Editar el sitio", "Cambia los textos", null)));
        when(permisos.asignaciones()).thenReturn(List.of(
                new Asignacion(CLIENTE, AGENDAR, false),
                new Asignacion(PRODUCT_OWNER, REGISTRAR_AVANCE, true),
                new Asignacion(ADMINISTRADOR, AGENDAR, true), new Asignacion(ADMINISTRADOR, REGISTRAR_AVANCE, true),
                new Asignacion(ADMINISTRADOR, SEGURIDAD, true), new Asignacion(ADMINISTRADOR, EDITAR_SITIO, true)));
        when(permisos.vetos()).thenReturn(List.of(new Veto(CLIENTE, SEGURIDAD, "No aplica a este rol")));
        when(permisos.usuariosActivosPorRol()).thenReturn(Map.of(CLIENTE, 12, PRODUCT_OWNER, 4, ADMINISTRADOR, 2));
        servicio = new ServicioPermisos(permisos, roles, auditoria);
    }

    static Celda celda(ServicioPermisos.Matriz m, String permiso, String rol) {
        return m.permisos().stream().filter(f -> f.codigo().equals(permiso)).findFirst().orElseThrow()
                .celdas().stream().filter(c -> c.rol().equals(rol)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ matriz

    @Test
    @DisplayName("La matriz trae los roles en el orden de las columnas (cliente, Product Owner, administrador) con sus cuentas activas y cuántos permisos tienen")
    void rolesEnOrden() {
        var m = servicio.matriz();

        assertThat(m.roles()).extracting(ServicioPermisos.RolResumen::codigo).containsExactly(Rol.CLIENTE, Rol.PRODUCT_OWNER, Rol.ADMINISTRADOR);
        assertThat(m.roles()).extracting(ServicioPermisos.RolResumen::usuarios).containsExactly(12, 4, 2);
        assertThat(m.roles()).extracting(ServicioPermisos.RolResumen::permisos).containsExactly(1, 1, 4);
    }

    @Test
    @DisplayName("Cada permiso trae sus tres celdas en el mismo orden y se agrupa por módulo con su nombre sencillo")
    void filasYGrupos() {
        var m = servicio.matriz();

        assertThat(m.permisos()).extracting(ServicioPermisos.FilaPermiso::grupo).containsExactly("Citas", "Proyectos", "Gestión y seguridad", "Sitio web");
        assertThat(m.permisos()).allSatisfy(f -> assertThat(f.celdas()).extracting(Celda::rol).containsExactly(Rol.CLIENTE, Rol.PRODUCT_OWNER, Rol.ADMINISTRADOR));
    }

    @Test
    @DisplayName("Las celdas: lo asignado cambiable, lo fijo con su motivo (el administrador «siempre lo tiene»; el resto «Fijo por RN-xx»), lo vetado y lo que no se tiene")
    void celdas() {
        var m = servicio.matriz();

        Celda cambiable = celda(m, "citas.agendar", Rol.CLIENTE);
        assertThat(cambiable.permitido()).isTrue();
        assertThat(cambiable.fijo()).isFalse();
        assertThat(cambiable.motivo()).isNull();

        Celda fijoPorRegla = celda(m, "proyectos.registrar_avance", Rol.PRODUCT_OWNER);
        assertThat(fijoPorRegla.permitido()).isTrue();
        assertThat(fijoPorRegla.fijo()).isTrue();
        assertThat(fijoPorRegla.motivo()).isEqualTo("Fijo por RN-07");

        assertThat(celda(m, "seguridad.permisos", Rol.ADMINISTRADOR).motivo()).isEqualTo(ServicioPermisos.ADMIN_SIEMPRE);

        Celda vetada = celda(m, "seguridad.permisos", Rol.CLIENTE);
        assertThat(vetada.permitido()).isFalse();
        assertThat(vetada.fijo()).isTrue();
        assertThat(vetada.motivo()).isEqualTo("No aplica a este rol");

        Celda libre = celda(m, "sitio.editar", Rol.PRODUCT_OWNER);
        assertThat(libre.permitido()).isFalse();
        assertThat(libre.fijo()).isFalse();
    }

    @Test
    @DisplayName("Un permiso fijo sin regla escrita dice «Fijo por una regla del negocio»")
    void fijoSinRegla() {
        when(permisos.permisos()).thenReturn(List.of(new Permiso(AGENDAR, "citas.agendar", "citas", "Agendar citas", "x", null)));
        when(permisos.asignaciones()).thenReturn(List.of(new Asignacion(CLIENTE, AGENDAR, true)));
        when(permisos.vetos()).thenReturn(List.of());

        assertThat(celda(servicio.matriz(), "citas.agendar", Rol.CLIENTE).motivo()).isEqualTo("Fijo por una regla del negocio");
    }

    // ------------------------------------------------------------------------------------------------ cambiar

    @Test
    @DisplayName("Dar un permiso libre y quitar uno cambiable escribe cada cambio y deja un registro por rol con el antes y el después (RN-20)")
    void cambiaYAudita() {
        servicio.cambiar(List.of(new Cambio(Rol.PRODUCT_OWNER, "sitio.editar", true), new Cambio(Rol.CLIENTE, "citas.agendar", false)));

        verify(permisos).bloquearMatriz();
        verify(permisos).dar(PRODUCT_OWNER, EDITAR_SITIO);
        verify(permisos).quitar(CLIENTE, AGENDAR);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, CambioDeValor>> cambios = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq(CategoriaAuditoria.PERMISOS), eq("Cambió los permisos de un rol"), eq("rol"), eq(Rol.PRODUCT_OWNER), cambios.capture());
        assertThat(cambios.getValue()).containsKey("Editar el sitio");
        InOrder orden = inOrder(auditoria);
        orden.verify(auditoria).registrar(eq(CategoriaAuditoria.PERMISOS), any(), eq("rol"), eq(Rol.CLIENTE), any());
        orden.verify(auditoria).registrar(eq(CategoriaAuditoria.PERMISOS), any(), eq("rol"), eq(Rol.PRODUCT_OWNER), any());
    }

    @Test
    @DisplayName("Si no hay nada distinto a lo que ya está, 422 «No hay cambios por guardar» y no se escribe ni se audita")
    void sinCambios() {
        for (List<Cambio> lista : java.util.Arrays.asList(null, List.<Cambio>of(), List.of(new Cambio(Rol.CLIENTE, "citas.agendar", true)))) {
            assertThatThrownBy(() -> servicio.cambiar(lista)).isInstanceOf(ReglaNegocioException.class).hasMessage(ServicioPermisos.SIN_CAMBIOS);
        }
        verify(permisos, never()).dar(anyShort(), anyShort());
        verify(permisos, never()).quitar(anyShort(), anyShort());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un permiso que fija una regla del negocio no se quita (422), tampoco al administrador, que conserva todos (RN-19)")
    void noSeQuitaLoFijo() {
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio(Rol.PRODUCT_OWNER, "proyectos.registrar_avance", false))))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ServicioPermisos.PERMISO_FIJO);
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio(Rol.ADMINISTRADOR, "seguridad.permisos", false))))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ServicioPermisos.PERMISO_FIJO);

        verify(permisos, never()).quitar(anyShort(), anyShort());
    }

    @Test
    @DisplayName("Un permiso vetado para un rol no se da (422)")
    void noSeDaLoVetado() {
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio(Rol.CLIENTE, "seguridad.permisos", true))))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(ServicioPermisos.PERMISO_VETADO);

        verify(permisos, never()).dar(anyShort(), anyShort());
    }

    @Test
    @DisplayName("Todo o nada: si un cambio válido viene junto a uno imposible, ninguno se guarda")
    void todoONada() {
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio(Rol.PRODUCT_OWNER, "sitio.editar", true), new Cambio(Rol.PRODUCT_OWNER, "proyectos.registrar_avance", false))))
                .isInstanceOf(ReglaNegocioException.class);

        verify(permisos, never()).dar(anyShort(), anyShort());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un rol o un permiso que no existe da 422 en el campo permisos")
    void combinacionInexistente() {
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio("INVENTADO", "citas.agendar", true))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(ServicioPermisos.COMBINACION_INEXISTENTE);
                    assertThat(e.campo()).contains("permisos");
                });
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio(Rol.CLIENTE, "no.existe", true)))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Si la misma casilla llega dos veces vale la última: dar y quitar el mismo permiso no cambia nada")
    void laUltimaVale() {
        assertThatThrownBy(() -> servicio.cambiar(List.of(new Cambio(Rol.PRODUCT_OWNER, "sitio.editar", true), new Cambio(Rol.PRODUCT_OWNER, "sitio.editar", false))))
                .hasMessage(ServicioPermisos.SIN_CAMBIOS);

        servicio.cambiar(List.of(new Cambio(Rol.PRODUCT_OWNER, "sitio.editar", false), new Cambio(Rol.PRODUCT_OWNER, "sitio.editar", true)));
        verify(permisos).dar(PRODUCT_OWNER, EDITAR_SITIO);
    }
}
