package co.coretech.portal.compartido.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.coretech.portal.compartido.datos.AuditoriaRepositorio;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;

/**
 * Deja en {@code auditoria} cada acción que cambia datos (RN-20): quién, cuándo, qué cambió (valor
 * anterior y nuevo), IP y navegador.
 * <p>
 * Los servicios lo llaman <b>dentro de su propia transacción</b> ({@link Propagation#MANDATORY}): si la
 * acción se revierte, su registro también, y nunca queda una acción sin registro.
 * <pre>{@code
 * auditoria.registrar(CategoriaAuditoria.CITAS, "Canceló la cita", "cita", cita.codigo(),
 *         Map.of("estado", CambioDeValor.de("PENDIENTE", "CANCELADA")));
 * }</pre>
 */
@Service
public class RegistroAuditoria {

    private static final int MAX_ACCION = 60;
    private static final int MAX_ENTIDAD = 40;
    private static final int MAX_IP = 45;
    private static final int MAX_AGENTE = 120;

    private final AuditoriaRepositorio repositorio;
    private final ContextoSolicitud contexto;
    private final ObjectMapper json;
    private final Clock reloj;

    public RegistroAuditoria(AuditoriaRepositorio repositorio, ContextoSolicitud contexto, ObjectMapper json, Clock reloj) {
        this.repositorio = repositorio;
        this.contexto = contexto;
        this.json = json;
        this.reloj = reloj;
    }

    /**
     * @param accion    lo que hizo, en palabras del administrador: "Marcó un paso como hecho" (máx. 60)
     * @param entidad   tipo de registro afectado: "cita", "proyecto", "paso"… (máx. 40)
     * @param entidadId código visible o id del registro (máx. 40); puede ser {@code null}
     * @param cambios   campo → valor anterior y nuevo; vacío si la acción no cambia campos
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(CategoriaAuditoria categoria, String accion, String entidad, String entidadId,
            Map<String, CambioDeValor> cambios) {
        insertar(contexto.usuarioId().orElse(null), categoria, accion, entidad, entidadId, cambios);
    }

    /**
     * Acción de un usuario que todavía no tiene sesión en la petición (por ejemplo, "Inició sesión": la
     * cookie se crea al responder). En lo demás es igual a {@link #registrar(CategoriaAuditoria, String, String, String, Map)}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarComo(UUID usuarioId, CategoriaAuditoria categoria, String accion, String entidad, String entidadId,
            Map<String, CambioDeValor> cambios) {
        insertar(Objects.requireNonNull(usuarioId, "usuarioId"), categoria, accion, entidad, entidadId, cambios);
    }

    private void insertar(UUID usuarioId, CategoriaAuditoria categoria, String accion, String entidad, String entidadId,
            Map<String, CambioDeValor> cambios) {
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(cambios, "cambios");
        repositorio.insertar(new AuditoriaRepositorio.NuevoRegistro(
                usuarioId,
                categoria.valor(),
                obligatorio(accion, "accion", MAX_ACCION),
                obligatorio(entidad, "entidad", MAX_ENTIDAD),
                entidadId == null ? null : obligatorio(entidadId, "entidadId", MAX_ENTIDAD),
                cambios.isEmpty() ? null : aJson(cambios),
                contexto.ip().map(ip -> recortar(ip, MAX_IP)).orElse(null),
                contexto.agente().map(a -> recortar(a, MAX_AGENTE)).orElse(null),
                Instant.now(reloj)));
    }

    /** Acción sin campos cambiados (por ejemplo, "Exportó la auditoría"). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(CategoriaAuditoria categoria, String accion, String entidad, String entidadId) {
        registrar(categoria, accion, entidad, entidadId, Map.of());
    }

    private String aJson(Map<String, CambioDeValor> cambios) {
        // Orden estable: el de inserción si el mapa lo tiene; si no, alfabético.
        Map<String, CambioDeValor> ordenados = cambios instanceof LinkedHashMap ? cambios : new TreeMap<>(cambios);
        try {
            return json.writeValueAsString(ordenados);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Los cambios de la auditoría no se pueden convertir a JSON", e);
        }
    }

    /** Un texto demasiado largo es un error de programación: se avisa en vez de cortarlo en silencio. */
    private static String obligatorio(String valor, String nombre, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + nombre + " en el registro de auditoría");
        }
        if (valor.length() > maximo) {
            throw new IllegalArgumentException(nombre + " supera " + maximo + " caracteres: " + valor);
        }
        return valor;
    }

    /** La IP y el navegador vienen del cliente: se recortan para no perder la acción por un encabezado largo. */
    private static String recortar(String valor, int maximo) {
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
