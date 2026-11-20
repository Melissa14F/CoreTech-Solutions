package co.coretech.portal.acceso.datos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consulta de la auditoría para el administrador (HU-22). Solo lee: la tabla es inmutable
 * (tg_auditoria_inmutable rechaza UPDATE y DELETE) y las inserciones las hace compartido.RegistroAuditoria.
 */
@Repository
public class ConsultaAuditoriaJdbc {

    /** Acciones que la pantalla marca como alerta de seguridad (intentos fallidos y bloqueos). */
    public static final List<String> ACCIONES_ALERTA = List.of(
            "Intento fallido de inicio de sesión", "Bloqueó la cuenta por intentos fallidos");

    private static final String DESDE = """
              FROM auditoria a
              LEFT JOIN usuarios u ON u.id = a.usuario_id
              LEFT JOIN roles r ON r.id = u.rol_id
              LEFT JOIN usuarios ue ON a.entidad = 'usuario' AND ue.id::text = a.entidad_id
            """;

    private final JdbcTemplate jdbc;

    ConsultaAuditoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param categoria valor de la columna (null = todas)
     * @param desde     incluido (null = sin límite)
     * @param hasta     excluido (null = sin límite)
     * @param texto     busca en la acción, el elemento y el nombre de las personas (null = sin búsqueda)
     */
    public record Filtro(String categoria, UUID usuarioId, Instant desde, Instant hasta, String texto) {
    }

    /** Fila de la auditoría con el nombre del usuario y, si el elemento es una cuenta, su nombre. */
    public record Fila(long id, Instant fecha, UUID usuarioId, String usuarioNombre, String usuarioRol, String categoria,
            String accion, String entidad, String entidadId, String entidadNombre, String cambiosJson, String ip,
            String agente) {
    }

    public List<Fila> buscar(Filtro filtro, int limite, long desplazamiento) {
        List<Object> parametros = new ArrayList<>();
        String donde = donde(filtro, true, parametros);
        parametros.add(limite);
        parametros.add(desplazamiento);
        return jdbc.query("""
                SELECT a.id, a.created_at, a.usuario_id, u.nombre, r.codigo, a.categoria, a.accion, a.entidad,
                       a.entidad_id, ue.nombre, a.cambios::text, a.ip, a.agente
                """ + DESDE + donde + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
                ConsultaAuditoriaJdbc::fila, parametros.toArray());
    }

    public long contar(Filtro filtro) {
        List<Object> parametros = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT count(*) " + DESDE + donde(filtro, true, parametros), Long.class,
                parametros.toArray());
        return total == null ? 0 : total;
    }

    /** Cuántos registros hay de cada categoría con los demás filtros (los números de los filtros de la pantalla). */
    public Map<String, Long> contarPorCategoria(Filtro filtro) {
        List<Object> parametros = new ArrayList<>();
        Map<String, Long> conteo = new LinkedHashMap<>();
        jdbc.query("SELECT a.categoria, count(*) " + DESDE + donde(filtro, false, parametros) + " GROUP BY a.categoria",
                f -> {
                    conteo.put(f.getString(1), f.getLong(2));
                }, parametros.toArray());
        return conteo;
    }

    public long contarDesde(Instant desde) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE created_at >= ?", Long.class, Timestamp.from(desde));
        return n == null ? 0 : n;
    }

    public long contarAlertasDesde(Instant desde) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE created_at >= ? AND accion IN (?, ?)", Long.class,
                Timestamp.from(desde), ACCIONES_ALERTA.get(0), ACCIONES_ALERTA.get(1));
        return n == null ? 0 : n;
    }

    public long contarCategoriaDesde(String categoria, Instant desde) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE created_at >= ? AND categoria = ?", Long.class,
                Timestamp.from(desde), categoria);
        return n == null ? 0 : n;
    }

    public record Persona(UUID usuarioId, String nombre) {
    }

    /** Las personas que aparecen en la auditoría, para el filtro "Usuario". */
    public List<Persona> personas() {
        return jdbc.query("""
                SELECT u.id, u.nombre FROM usuarios u
                 WHERE EXISTS (SELECT 1 FROM auditoria a WHERE a.usuario_id = u.id)
                 ORDER BY u.nombre
                """, (f, n) -> new Persona(f.getObject(1, UUID.class), f.getString(2)));
    }

    private static String donde(Filtro filtro, boolean conCategoria, List<Object> parametros) {
        List<String> condiciones = new ArrayList<>();
        if (conCategoria && filtro.categoria() != null) {
            condiciones.add("a.categoria = ?");
            parametros.add(filtro.categoria());
        }
        if (filtro.usuarioId() != null) {
            condiciones.add("a.usuario_id = ?");
            parametros.add(filtro.usuarioId());
        }
        if (filtro.desde() != null) {
            condiciones.add("a.created_at >= ?");
            parametros.add(Timestamp.from(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            condiciones.add("a.created_at < ?");
            parametros.add(Timestamp.from(filtro.hasta()));
        }
        if (filtro.texto() != null) {
            String patron = "%" + filtro.texto().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            condiciones.add("(a.accion ILIKE ? OR a.entidad_id ILIKE ? OR u.nombre ILIKE ? OR ue.nombre ILIKE ?)");
            for (int i = 0; i < 4; i++) {
                parametros.add(patron);
            }
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private static Fila fila(ResultSet f, int n) throws SQLException {
        return new Fila(f.getLong(1), f.getTimestamp(2).toInstant(), f.getObject(3, UUID.class), f.getString(4),
                f.getString(5), f.getString(6), f.getString(7), f.getString(8), f.getString(9), f.getString(10),
                f.getString(11), f.getString(12), f.getString(13));
    }
}
