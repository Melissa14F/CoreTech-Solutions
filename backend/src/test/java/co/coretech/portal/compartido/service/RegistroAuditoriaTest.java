package co.coretech.portal.compartido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/** Registro de auditoría contra PostgreSQL 15 (RN-20). Cada prueba se revierte al terminar. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@Transactional
class RegistroAuditoriaTest {

    @Autowired
    RegistroAuditoria auditoria;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void guardaUsuarioIpNavegadorYCambios() {
        // El usuario no existe en esta prueba: se difiere la llave foránea y la transacción se revierte.
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        UUID usuario = UUID.randomUUID();
        conSesion(usuario);
        conPeticion("203.0.113.7", "Mozilla/5.0 (Windows NT 10.0) Edge/131");
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("PENDIENTE", "CANCELADA"));
        cambios.put("motivoCancelacion", CambioDeValor.nuevo("El cliente ya no puede asistir"));

        auditoria.registrar(CategoriaAuditoria.CITAS, "Canceló la cita", "cita", "C-0064", cambios);

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT usuario_id, categoria, accion, entidad, entidad_id, ip, agente,
                       cambios -> 'estado' ->> 'anterior' AS anterior, cambios -> 'estado' ->> 'nuevo' AS nuevo
                  FROM auditoria WHERE entidad_id = 'C-0064'
                """);
        assertThat(fila.get("usuario_id")).isEqualTo(usuario);
        assertThat(fila.get("categoria")).isEqualTo("citas");
        assertThat(fila.get("accion")).isEqualTo("Canceló la cita");
        assertThat(fila.get("entidad")).isEqualTo("cita");
        assertThat(fila.get("ip")).isEqualTo("203.0.113.7");
        assertThat(fila.get("agente")).isEqualTo("Mozilla/5.0 (Windows NT 10.0) Edge/131");
        assertThat(fila.get("anterior")).isEqualTo("PENDIENTE");
        assertThat(fila.get("nuevo")).isEqualTo("CANCELADA");
    }

    @Test
    void sinSesionNiPeticionQuedaSinUsuarioIpNiNavegador() {
        // Por ejemplo, una tarea programada que vence una propuesta (RN-29). Spring Test deja una petición
        // simulada en cada prueba; se quita para que sea como en una tarea programada.
        RequestContextHolder.resetRequestAttributes();
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Venció la propuesta", "proyecto", "P-0024");

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT usuario_id, ip, agente, cambios FROM auditoria WHERE entidad_id = 'P-0024'");
        assertThat(fila.get("usuario_id")).isNull();
        assertThat(fila.get("ip")).isNull();
        assertThat(fila.get("agente")).isNull();
        assertThat(fila.get("cambios")).isNull();
    }

    @Test
    void recortaUnNavegadorMuyLargoEnLugarDePerderLaAccion() {
        conPeticion("198.51.100.4", "x".repeat(300));

        auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Inició sesión", "usuario", "U-1");

        String agente = jdbc.queryForObject("SELECT agente FROM auditoria WHERE entidad_id = 'U-1'", String.class);
        assertThat(agente).hasSize(120);
    }

    @Test
    void unaAccionDemasiadoLargaEsUnErrorDeProgramacion() {
        assertThatThrownBy(() -> auditoria.registrar(CategoriaAuditoria.SITIO, "a".repeat(61), "servicio", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fueraDeUnaTransaccionNoRegistra() {
        assertThatThrownBy(() -> auditoria.registrar(CategoriaAuditoria.SITIO, "Publicó los textos", "contenido", null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void conSesion(UUID usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
    }

    private static void conPeticion(String ip, String navegador) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr(ip);
        peticion.addHeader("User-Agent", navegador);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
    }
}
