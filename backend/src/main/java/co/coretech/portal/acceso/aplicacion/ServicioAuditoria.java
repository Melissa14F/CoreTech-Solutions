package co.coretech.portal.acceso.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.coretech.portal.acceso.datos.ConsultaAuditoriaJdbc;
import co.coretech.portal.acceso.datos.UsuarioRepositorio;
import co.coretech.portal.compartido.PropiedadesCoretech;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.ValidacionException;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;

/**
 * Consulta y exportación de la auditoría (HU-22, RN-20, RNF-02). Es de solo lectura: no hay forma de
 * editar ni borrar un registro (y la base lo impide con tg_auditoria_inmutable). Las fechas del filtro son
 * días de Colombia: "hasta" incluye todo ese día.
 */
@Service
public class ServicioAuditoria {

    static final int TAMANO_POR_DEFECTO = 20;
    static final int TAMANO_MAXIMO = 100;
    static final int MAXIMO_EXPORTAR = 10_000;
    private static final DateTimeFormatter FECHA_CSV = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ConsultaAuditoriaJdbc consulta;
    private final UsuarioRepositorio usuarios;
    private final RegistroAuditoria auditoria;
    private final ObjectMapper json;
    private final ZoneId zona;
    private final Clock reloj;

    public ServicioAuditoria(ConsultaAuditoriaJdbc consulta, UsuarioRepositorio usuarios, RegistroAuditoria auditoria,
            ObjectMapper json, PropiedadesCoretech propiedades, Clock reloj) {
        this.consulta = consulta;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.json = json;
        this.zona = propiedades.zonaNegocio();
        this.reloj = reloj;
    }

    /** Filtros tal como llegan en la URL (texto): el servicio los valida y responde 422 por campo. */
    public record Parametros(String categoria, String usuarioId, String desde, String hasta, String q) {
    }

    public record Pagina(List<Registro> registros, int pagina, int tamano, long total, int totalPaginas,
            Map<String, Long> porCategoria) {
    }

    /**
     * @param usuario   quién lo hizo; null si no había sesión (por ejemplo, un intento fallido) o lo hizo el sistema
     * @param elemento  lo que se ve bajo la acción: el nombre de la cuenta o el código del registro
     * @param alerta    intento fallido o bloqueo: la pantalla lo resalta
     */
    public record Registro(long id, java.time.Instant fecha, Persona usuario, String categoria, String accion,
            String entidad, String entidadId, String elemento, List<Cambio> cambios, String ip, String agente,
            boolean alerta) {
    }

    public record Persona(UUID usuarioId, String nombre, String rol) {
    }

    /** Valor anterior y nuevo tal como se guardaron (texto, número, sí/no o null). */
    public record Cambio(String campo, Object anterior, Object nuevo) {
    }

    public record Resumen(long eventosHoy, long alertasSemana, long cambiosPermisosMes,
            List<ConsultaAuditoriaJdbc.Persona> usuarios, List<CategoriaVisible> categorias) {
    }

    public record CategoriaVisible(String valor, String nombre) {
    }

    @Transactional(readOnly = true)
    public Pagina consultar(Parametros parametros, Integer pagina, Integer tamano) {
        var errores = new ValidacionException.Acumulador();
        int numero = pagina == null ? 0 : pagina;
        int porPagina = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        if (numero < 0) {
            errores.agregar("pagina", "La página no existe.");
        }
        if (porPagina < 1 || porPagina > TAMANO_MAXIMO) {
            errores.agregar("tamano", "Elige entre 1 y " + TAMANO_MAXIMO + " registros por página.");
        }
        ConsultaAuditoriaJdbc.Filtro filtro = filtro(parametros, errores);
        errores.lanzarSiHay();

        long total = consulta.contar(filtro);
        List<Registro> registros = consulta.buscar(filtro, porPagina, (long) numero * porPagina).stream()
                .map(this::aRegistro).toList();
        int totalPaginas = (int) Math.max(1, (total + porPagina - 1) / porPagina);
        return new Pagina(registros, numero, porPagina, total, totalPaginas, consulta.contarPorCategoria(filtro));
    }

    /** Las cifras de arriba de la pantalla y las opciones de los filtros. */
    @Transactional(readOnly = true)
    public Resumen resumen() {
        var ahora = java.time.Instant.now(reloj);
        LocalDate hoy = LocalDate.now(reloj.withZone(zona));
        return new Resumen(
                consulta.contarDesde(hoy.atStartOfDay(zona).toInstant()),
                consulta.contarAlertasDesde(ahora.minus(Duration.ofDays(7))),
                consulta.contarCategoriaDesde(CategoriaAuditoria.PERMISOS.valor(), hoy.withDayOfMonth(1).atStartOfDay(zona).toInstant()),
                consulta.personas(),
                java.util.Arrays.stream(CategoriaAuditoria.values()).map(c -> new CategoriaVisible(c.valor(), c.nombre())).toList());
    }

    /**
     * HU-22: el resultado de los filtros en CSV (UTF-8 con BOM, para que Excel lea las tildes), del más
     * reciente al más antiguo. Exportar también queda en la auditoría.
     */
    @Transactional
    public String exportar(Parametros parametros) {
        var errores = new ValidacionException.Acumulador();
        ConsultaAuditoriaJdbc.Filtro filtro = filtro(parametros, errores);
        errores.lanzarSiHay();
        long total = consulta.contar(filtro);
        if (total > MAXIMO_EXPORTAR) {
            errores.agregar("hasta", "Hay más de 10.000 registros con estos filtros; acorta el rango de fechas.");
            errores.lanzarSiHay();
        }
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(",", List.of("Fecha", "Usuario", "Acción", "Elemento", "Módulo", "Cambios", "Origen")
                .stream().map(ServicioAuditoria::celda).toList())).append("\r\n");
        for (ConsultaAuditoriaJdbc.Fila fila : consulta.buscar(filtro, MAXIMO_EXPORTAR, 0)) {
            Registro r = aRegistro(fila);
            String usuario = r.usuario() == null ? "Sin identificar (Sin sesión)"
                    : r.usuario().nombre() + " (" + nombreRol(r.usuario().rol()) + ")";
            String cambios = String.join(" | ", r.cambios().stream()
                    .map(c -> c.campo() + ": " + texto(c.anterior()) + " → " + texto(c.nuevo())).toList());
            String origen = String.join(" · ", java.util.stream.Stream.of(r.ip(), r.agente())
                    .filter(v -> v != null && !v.isBlank()).toList());
            csv.append(String.join(",", java.util.stream.Stream.of(
                    FECHA_CSV.format(r.fecha().atZone(zona)), usuario, r.accion(), r.elemento() == null ? "—" : r.elemento(),
                    CategoriaAuditoria.deValor(r.categoria()).map(CategoriaAuditoria::nombre).orElse(r.categoria()),
                    cambios, origen.isEmpty() ? "—" : origen).map(ServicioAuditoria::celda).toList())).append("\r\n");
        }
        Map<String, CambioDeValor> detalle = new LinkedHashMap<>();
        detalle.put("registros", CambioDeValor.nuevo(total));
        auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Exportó la auditoría", "auditoria", null, detalle);
        return csv.toString();
    }

    // ------------------------------------------------------------------ Filtros

    private ConsultaAuditoriaJdbc.Filtro filtro(Parametros p, ValidacionException.Acumulador errores) {
        String categoria = vacioANull(p.categoria());
        if (categoria != null && CategoriaAuditoria.deValor(categoria).isEmpty()) {
            errores.agregar("categoria", "Elige una categoría de la lista.");
        }
        UUID usuarioId = null;
        String textoUsuario = vacioANull(p.usuarioId());
        if (textoUsuario != null) {
            try {
                usuarioId = UUID.fromString(textoUsuario);
            } catch (IllegalArgumentException e) {
                usuarioId = null;
            }
            if (usuarioId == null || !usuarios.existsById(usuarioId)) {
                errores.agregar("usuarioId", "Elige un usuario de la lista.");
            }
        }
        LocalDate desde = fecha(p.desde(), "desde", errores);
        LocalDate hasta = fecha(p.hasta(), "hasta", errores);
        if (desde != null && hasta != null) {
            if (desde.isAfter(hasta)) {
                errores.agregar("hasta", "La fecha final debe ser igual o posterior a la inicial.");
            } else if (hasta.isAfter(desde.plusYears(1))) {
                errores.agregar("hasta", "Elige un rango de máximo un año.");
            }
        }
        String texto = vacioANull(p.q());
        if (texto != null && texto.length() > 80) {
            errores.agregar("q", "Escribe máximo 80 caracteres.");
        }
        return new ConsultaAuditoriaJdbc.Filtro(categoria, usuarioId,
                desde == null ? null : desde.atStartOfDay(zona).toInstant(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(zona).toInstant(),
                texto);
    }

    private static LocalDate fecha(String valor, String campo, ValidacionException.Acumulador errores) {
        String texto = vacioANull(valor);
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            errores.agregar(campo, "Escribe una fecha válida.");
            return null;
        }
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // ------------------------------------------------------------------ Conversión

    private Registro aRegistro(ConsultaAuditoriaJdbc.Fila f) {
        Persona persona = f.usuarioId() == null ? null : new Persona(f.usuarioId(), f.usuarioNombre(), f.usuarioRol());
        String elemento = f.entidadNombre() != null ? f.entidadNombre() : f.entidadId();
        return new Registro(f.id(), f.fecha(), persona, f.categoria(), f.accion(), f.entidad(), f.entidadId(), elemento,
                cambios(f.cambiosJson()), f.ip(), f.agente(), ConsultaAuditoriaJdbc.ACCIONES_ALERTA.contains(f.accion()));
    }

    private List<Cambio> cambios(String texto) {
        if (texto == null) {
            return List.of();
        }
        try {
            JsonNode raiz = json.readTree(texto);
            List<Cambio> lista = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = raiz.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> campo = it.next();
                lista.add(new Cambio(campo.getKey(), valor(campo.getValue().get("anterior")), valor(campo.getValue().get("nuevo"))));
            }
            return lista;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Object valor(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        return nodo.isValueNode() ? json.convertValue(nodo, Object.class) : nodo.toString();
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return "—";
        }
        if (valor instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        return valor.toString();
    }

    private static String nombreRol(String rol) {
        if (rol == null) {
            return "Sin rol";
        }
        return switch (rol) {
            case "ADMINISTRADOR" -> "Administrador";
            case "PRODUCT_OWNER" -> "Desarrollador (Product Owner)";
            default -> "Cliente";
        };
    }

    /** Celda CSV entre comillas; una fórmula (=, +, -, @) se antepone con ' para que Excel no la ejecute. */
    static String celda(String valor) {
        String v = valor == null ? "" : valor;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
