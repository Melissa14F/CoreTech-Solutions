package co.coretech.portal.acceso.aplicacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.datos.PermisosJdbc;
import co.coretech.portal.acceso.datos.RolRepositorio;
import co.coretech.portal.acceso.dominio.Rol;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.ReglaNegocioException;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;

/**
 * Roles y permisos (HU-21, RN-19, D-35): la matriz de prototipo/admin/roles-permisos.html y sus cambios.
 * <ul>
 *   <li>Una casilla fija (candado) no cambia: quitar un permiso fijo → 422 "Este permiso lo exige una regla
 *       del negocio"; dar uno vetado → 422.</li>
 *   <li>El cambio aplica de inmediato: la sesión lee los permisos de la base en cada petición.</li>
 *   <li>Cada rol cambiado deja un registro en la auditoría (categoría permisos) con lo que cambió.</li>
 * </ul>
 */
@Service
public class ServicioPermisos {

    static final String PERMISO_FIJO = "Este permiso lo exige una regla del negocio";
    static final String PERMISO_VETADO = "Una regla del negocio no permite dar este permiso a este rol";
    static final String SIN_CAMBIOS = "No hay cambios por guardar.";
    static final String COMBINACION_INEXISTENTE = "Ese rol o ese permiso no existe";
    static final String ADMIN_SIEMPRE = "El administrador siempre lo tiene";

    /** Orden de las columnas de la matriz. */
    private static final List<String> ORDEN_ROLES = List.of(Rol.CLIENTE, Rol.PRODUCT_OWNER, Rol.ADMINISTRADOR);

    private final PermisosJdbc permisos;
    private final RolRepositorio roles;
    private final RegistroAuditoria auditoria;

    public ServicioPermisos(PermisosJdbc permisos, RolRepositorio roles, RegistroAuditoria auditoria) {
        this.permisos = permisos;
        this.roles = roles;
        this.auditoria = auditoria;
    }

    public record Matriz(List<RolResumen> roles, List<FilaPermiso> permisos) {
    }

    /** @param permisos cuántos tiene el rol; @param usuarios cuentas activas con ese rol */
    public record RolResumen(String codigo, String nombre, int usuarios, int permisos) {
    }

    /** @param grupo "Citas", "Proyectos", "Reuniones de seguimiento", "Sitio web" o "Gestión y seguridad" */
    public record FilaPermiso(String codigo, String nombre, String ayuda, String regla, String grupo, List<Celda> celdas) {
    }

    /** @param motivo por qué es fija ("Fijo por RN-12", "No aplica a este rol"…); null si se puede cambiar */
    public record Celda(String rol, boolean permitido, boolean fijo, String motivo) {
    }

    /** Lo que el administrador movió en un interruptor. */
    public record Cambio(String rol, String permiso, boolean permitido) {
    }

    @Transactional(readOnly = true)
    public Matriz matriz() {
        return armar();
    }

    /** Guarda todos los cambios de la matriz juntos: si uno no se puede, no se guarda ninguno. */
    @Transactional
    public Matriz cambiar(List<Cambio> cambios) {
        permisos.bloquearMatriz();
        Matriz actual = armar();
        Map<String, Rol> rolesPorCodigo = rolesOrdenados().stream().collect(Collectors.toMap(Rol::getCodigo, Function.identity()));
        Map<String, PermisosJdbc.Permiso> permisosPorCodigo = permisos.permisos().stream()
                .collect(Collectors.toMap(PermisosJdbc.Permiso::codigo, Function.identity()));

        // Por rol, en el orden de la matriz: permiso → antes y después
        Map<String, Map<String, CambioDeValor>> porRol = new LinkedHashMap<>();
        List<Runnable> escrituras = new ArrayList<>();
        // Si la misma casilla llega dos veces, vale la última
        Map<String, Cambio> unicos = new LinkedHashMap<>();
        (cambios == null ? List.<Cambio>of() : cambios).forEach(c -> unicos.put(c.rol() + "|" + c.permiso(), c));
        for (Cambio cambio : unicos.values()) {
            Rol rol = rolesPorCodigo.get(cambio.rol());
            PermisosJdbc.Permiso permiso = permisosPorCodigo.get(cambio.permiso());
            if (rol == null || permiso == null) {
                throw new ReglaNegocioException("permisos", COMBINACION_INEXISTENTE);
            }
            Celda celda = celda(actual, permiso.codigo(), rol.getCodigo());
            if (celda.permitido() == cambio.permitido()) {
                continue;
            }
            if (celda.fijo()) {
                throw new ReglaNegocioException("permisos", celda.permitido() ? PERMISO_FIJO : PERMISO_VETADO);
            }
            porRol.computeIfAbsent(rol.getCodigo(), r -> new LinkedHashMap<>())
                    .put(permiso.nombre(), CambioDeValor.de(celda.permitido(), cambio.permitido()));
            escrituras.add(cambio.permitido()
                    ? () -> permisos.dar(rol.getId(), permiso.id())
                    : () -> permisos.quitar(rol.getId(), permiso.id()));
        }
        if (escrituras.isEmpty()) {
            throw new ReglaNegocioException(SIN_CAMBIOS);
        }
        escrituras.forEach(Runnable::run);
        ORDEN_ROLES.stream().filter(porRol::containsKey).forEach(codigo ->
                auditoria.registrar(CategoriaAuditoria.PERMISOS, "Cambió los permisos de un rol", "rol", codigo,
                        porRol.get(codigo)));
        return armar();
    }

    private Matriz armar() {
        List<Rol> listaRoles = rolesOrdenados();
        Map<Short, Map<Short, Boolean>> asignados = new HashMap<>(); // rol → permiso → fijo
        permisos.asignaciones().forEach(a -> asignados.computeIfAbsent(a.rolId(), r -> new HashMap<>()).put(a.permisoId(), a.fijo()));
        Map<Short, Map<Short, String>> vetados = new HashMap<>();
        permisos.vetos().forEach(v -> vetados.computeIfAbsent(v.rolId(), r -> new HashMap<>()).put(v.permisoId(), v.motivo()));
        Map<Short, Integer> usuarios = permisos.usuariosActivosPorRol();

        List<FilaPermiso> filas = permisos.permisos().stream().map(p -> new FilaPermiso(p.codigo(), p.nombre(), p.ayuda(),
                p.regla(), grupo(p.modulo()), listaRoles.stream().map(r -> {
                    Boolean fijo = asignados.getOrDefault(r.getId(), Map.of()).get(p.id());
                    String veto = vetados.getOrDefault(r.getId(), Map.of()).get(p.id());
                    if (fijo != null) {
                        return new Celda(r.getCodigo(), true, fijo, fijo ? motivoFijo(r, p) : null);
                    }
                    return new Celda(r.getCodigo(), false, veto != null, veto);
                }).toList())).toList();

        List<RolResumen> resumen = listaRoles.stream().map(r -> new RolResumen(r.getCodigo(), r.getNombre(),
                usuarios.getOrDefault(r.getId(), 0), asignados.getOrDefault(r.getId(), Map.of()).size())).toList();
        return new Matriz(resumen, filas);
    }

    private List<Rol> rolesOrdenados() {
        return roles.findAll().stream()
                .filter(r -> ORDEN_ROLES.contains(r.getCodigo()))
                .sorted(Comparator.comparingInt(r -> ORDEN_ROLES.indexOf(r.getCodigo())))
                .toList();
    }

    private static Celda celda(Matriz matriz, String permiso, String rol) {
        Optional<Celda> celda = matriz.permisos().stream().filter(f -> f.codigo().equals(permiso)).findFirst()
                .flatMap(f -> f.celdas().stream().filter(c -> c.rol().equals(rol)).findFirst());
        return celda.orElseThrow(() -> new ReglaNegocioException("permisos", COMBINACION_INEXISTENTE));
    }

    private static String motivoFijo(Rol rol, PermisosJdbc.Permiso permiso) {
        if (Rol.ADMINISTRADOR.equals(rol.getCodigo())) {
            return ADMIN_SIEMPRE;
        }
        return permiso.regla() == null ? "Fijo por una regla del negocio" : "Fijo por " + permiso.regla();
    }

    private static String grupo(String modulo) {
        return switch (modulo) {
            case "citas" -> "Citas";
            case "proyectos" -> "Proyectos";
            case "reuniones" -> "Reuniones de seguimiento";
            case "sitio" -> "Sitio web";
            default -> "Gestión y seguridad";
        };
    }
}
