package co.coretech.portal.especialidad.aplicacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.CuentasDeProductOwner;
import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.ValidacionException;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;
import co.coretech.portal.especialidad.aplicacion.Vistas.EquipoVista;
import co.coretech.portal.especialidad.aplicacion.Vistas.MiembroVista;
import co.coretech.portal.especialidad.datos.RepositorioEspecialidad;
import co.coretech.portal.especialidad.dominio.Desarrollador;
import co.coretech.portal.especialidad.dominio.Equipo;
import co.coretech.portal.especialidad.dominio.Integrante;
import co.coretech.portal.especialidad.dominio.RolEquipo;

/**
 * Equipos de trabajo (HU-26; RN-05, RN-18, RN-25, RN-26): un único Product Owner con cuenta, integrantes con el rol
 * que permite su especialidad y cupo de 1 a 3 proyectos. Al nombrar a un Product Owner nuevo se le crea la cuenta y
 * su contraseña temporal sale solo por correo (RF-05). Todo cambio queda en la auditoría (RN-20).
 */
@Service
public class ServicioEquipos {

    static final String NO_ENCONTRADO = "Equipo no encontrado";
    static final String OTRO_EQUIPO = "Este desarrollador ya está en otro equipo";
    static final String ROL_NO_CORRESPONDE = "El rol no corresponde a la especialidad del desarrollador";
    static final String SIN_INTEGRANTES = "Agrega al menos un integrante además del desarrollador (Product Owner)";

    private final RepositorioEspecialidad repositorio;
    private final CuentasDeProductOwner cuentas;
    private final RegistroAuditoria auditoria;

    public ServicioEquipos(RepositorioEspecialidad repositorio, CuentasDeProductOwner cuentas, RegistroAuditoria auditoria) {
        this.repositorio = repositorio;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
    }

    /** Un integrante que el administrador eligió (el rol es un nombre de {@link RolEquipo}). */
    public record DatosIntegrante(UUID desarrolladorId, String rol) {
    }

    /** @param maxProyectos cupo de 1 a 3; si no se envía, 3 al crear y el actual al editar */
    public record DatosEquipo(UUID productOwnerId, List<DatosIntegrante> integrantes, Integer maxProyectos) {
    }

    // ------------------------------------------------------------------ Consultas

    @Transactional(readOnly = true)
    public List<EquipoVista> listar() {
        Map<UUID, Desarrollador> devs = new HashMap<>();
        repositorio.desarrolladores().forEach(d -> devs.put(d.id(), d));
        Map<UUID, List<Integrante>> porEquipo = new HashMap<>();
        repositorio.integrantes().forEach(i -> porEquipo.computeIfAbsent(i.equipoId(), k -> new ArrayList<>()).add(i));
        Map<UUID, Integer> activos = repositorio.proyectosActivosPorEquipo();
        List<EquipoVista> vistas = new ArrayList<>();
        for (Equipo e : repositorio.equipos()) {
            List<Integrante> filas = porEquipo.getOrDefault(e.id(), List.of());
            MiembroVista po = null;
            List<MiembroVista> otros = new ArrayList<>();
            for (Integrante i : filas) {
                MiembroVista m = miembro(devs.get(i.desarrolladorId()), i.rol());
                if (i.rol() == RolEquipo.PRODUCT_OWNER) {
                    po = m;
                } else {
                    otros.add(m);
                }
            }
            if (po != null && e.activo()) {
                vistas.add(new EquipoVista(e.id(), po, otros, activos.getOrDefault(e.id(), 0), e.maxProyectos()));
            }
        }
        return vistas;
    }

    @Transactional(readOnly = true)
    public EquipoVista obtener(UUID equipoId) {
        return listar().stream().filter(e -> e.equipoId().equals(equipoId)).findFirst()
                .orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    // ------------------------------------------------------------------ Cambios

    /** HU-26: crea el equipo, la cuenta de su Product Owner y envía su contraseña temporal por correo. */
    @Transactional
    public EquipoVista crear(DatosEquipo datos) {
        Plan plan = planear(datos, null, 3);
        UUID equipoId = repositorio.insertarEquipo(plan.cupo);
        Desarrollador po = plan.productOwner;
        UUID usuarioId = cuentas.habilitar(po.usuarioId(), po.nombre(), po.correo(), po.celular());
        repositorio.asignarCuenta(po.id(), usuarioId);
        repositorio.insertarIntegrante(equipoId, po.id(), RolEquipo.PRODUCT_OWNER);
        plan.integrantes.forEach((dev, rol) -> repositorio.insertarIntegrante(equipoId, dev.id(), rol));

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("productOwner", CambioDeValor.nuevo(po.nombre()));
        cambios.put("integrantes", CambioDeValor.nuevo(descripcion(plan.integrantes)));
        cambios.put("maxProyectos", CambioDeValor.nuevo(plan.cupo));
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Creó un equipo", "equipo", equipoId.toString(), cambios);
        return obtener(equipoId);
    }

    /**
     * HU-26: edita Product Owner, integrantes y cupo. Si cambia el Product Owner, el nuevo recibe su cuenta y el
     * anterior pierde el acceso; queda como integrante si viene en la lista.
     */
    @Transactional
    public EquipoVista editar(UUID equipoId, DatosEquipo datos) {
        Equipo equipo = repositorio.equipoBloqueado(equipoId).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
        Plan plan = planear(datos, equipoId, equipo.maxProyectos());
        List<Integrante> actuales = repositorio.integrantes().stream().filter(i -> i.equipoId().equals(equipoId)).toList();
        Integrante poActual = actuales.stream().filter(i -> i.rol() == RolEquipo.PRODUCT_OWNER).findFirst().orElseThrow();
        Desarrollador poAnterior = repositorio.desarrollador(poActual.desarrolladorId()).orElseThrow();
        Desarrollador poNuevo = plan.productOwner;
        boolean cambiaPO = !poNuevo.id().equals(poAnterior.id());
        int activos = repositorio.proyectosActivosPorEquipo().getOrDefault(equipoId, 0);
        if (plan.cupo < activos) {
            throw new ConflictoException("maxProyectos", "El equipo tiene " + activos + " proyectos activos: el cupo no puede ser menor");
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        if (cambiaPO) {
            cambios.put("productOwner", CambioDeValor.de(poAnterior.nombre(), poNuevo.nombre()));
            UUID usuarioId = cuentas.habilitar(poNuevo.usuarioId(), poNuevo.nombre(), poNuevo.correo(), poNuevo.celular());
            repositorio.asignarCuenta(poNuevo.id(), usuarioId);
        }
        Set<UUID> finales = new HashSet<>();
        plan.integrantes.keySet().forEach(d -> finales.add(d.id()));
        finales.add(poNuevo.id());

        // Primero salen los que ya no están y el Product Owner anterior baja a integrante (hay un solo Product Owner por equipo)
        for (Integrante i : actuales) {
            if (!finales.contains(i.desarrolladorId())) {
                repositorio.quitarIntegrante(i.desarrolladorId());
            }
        }
        if (cambiaPO && finales.contains(poAnterior.id())) {
            repositorio.cambiarRol(poAnterior.id(), plan.integrantes.entrySet().stream()
                    .filter(e -> e.getKey().id().equals(poAnterior.id())).map(Map.Entry::getValue).findFirst().orElseThrow());
        }
        Map<UUID, RolEquipo> rolesActuales = new HashMap<>();
        actuales.forEach(i -> rolesActuales.put(i.desarrolladorId(), i.rol()));
        if (cambiaPO) {
            if (rolesActuales.containsKey(poNuevo.id())) {
                repositorio.cambiarRol(poNuevo.id(), RolEquipo.PRODUCT_OWNER);
            } else {
                repositorio.insertarIntegrante(equipoId, poNuevo.id(), RolEquipo.PRODUCT_OWNER);
            }
        }
        for (Map.Entry<Desarrollador, RolEquipo> e : plan.integrantes.entrySet()) {
            UUID dev = e.getKey().id();
            if (!rolesActuales.containsKey(dev)) {
                repositorio.insertarIntegrante(equipoId, dev, e.getValue());
            } else if (rolesActuales.get(dev) != e.getValue() && !(cambiaPO && dev.equals(poAnterior.id()))) {
                repositorio.cambiarRol(dev, e.getValue());
            }
        }
        if (cambiaPO && !poAnterior.id().equals(poNuevo.id()) && poAnterior.usuarioId() != null) {
            cuentas.bloquear(poAnterior.usuarioId());
        }
        if (plan.cupo != equipo.maxProyectos()) {
            repositorio.cambiarCupo(equipoId, plan.cupo);
            cambios.put("maxProyectos", CambioDeValor.de(equipo.maxProyectos(), plan.cupo));
        }
        cambios.put("integrantes", CambioDeValor.de(descripcionActual(actuales), descripcion(plan.integrantes)));
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Editó un equipo", "equipo", equipoId.toString(), cambios);
        return obtener(equipoId);
    }

    /** HU-26: suma un integrante al equipo. */
    @Transactional
    public EquipoVista agregarIntegrante(UUID equipoId, DatosIntegrante datos) {
        repositorio.equipoBloqueado(equipoId).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
        EquipoVista actual = obtener(equipoId);
        List<DatosIntegrante> todos = new ArrayList<>();
        actual.integrantes().forEach(m -> todos.add(new DatosIntegrante(m.desarrolladorId(), m.rol().name())));
        todos.add(datos);
        return editar(equipoId, new DatosEquipo(actual.productOwner().desarrolladorId(), todos, actual.maxProyectos()));
    }

    /** HU-26: quita un integrante; no el único que queda además del Product Owner (409, RN-25, D-43). */
    @Transactional
    public EquipoVista quitarIntegrante(UUID equipoId, UUID desarrolladorId) {
        repositorio.equipoBloqueado(equipoId).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
        EquipoVista actual = obtener(equipoId);
        if (actual.productOwner().desarrolladorId().equals(desarrolladorId)) {
            throw new ConflictoException("Para cambiar al desarrollador (Product Owner), elige otro al editar el equipo");
        }
        if (actual.integrantes().stream().noneMatch(m -> m.desarrolladorId().equals(desarrolladorId))) {
            throw new NoEncontradoException("Este desarrollador no está en el equipo");
        }
        if (actual.integrantes().size() <= 1) {
            throw new ConflictoException("El equipo de " + actual.productOwner().nombre()
                    + " quedaría sin integrantes; agrega otro antes de quitarlo");
        }
        repositorio.quitarIntegrante(desarrolladorId);
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("integrantes", CambioDeValor.de("Incluía al desarrollador " + desarrolladorId, "Ya no lo incluye"));
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Quitó un integrante de un equipo", "equipo", equipoId.toString(), cambios);
        return obtener(equipoId);
    }

    // ------------------------------------------------------------------ Reglas

    private record Plan(Desarrollador productOwner, Map<Desarrollador, RolEquipo> integrantes, int cupo) {
    }

    /**
     * Valida lo que el administrador envió: primero los datos (422, un error por campo) y luego los choques con
     * otros equipos (409). {@code equipoId} es el equipo que se edita (sus integrantes no cuentan como "en otro equipo").
     */
    private Plan planear(DatosEquipo datos, UUID equipoId, int cupoActual) {
        var errores = new ValidacionException.Acumulador();
        Desarrollador po = null;
        if (datos.productOwnerId() == null) {
            errores.agregar("productOwnerId", "Elige el desarrollador (Product Owner).");
        } else {
            po = repositorio.desarrollador(datos.productOwnerId()).orElse(null);
            if (po == null) {
                errores.agregar("productOwnerId", "Elige un desarrollador de la lista.");
            } else if (!po.activo()) {
                errores.agregar("productOwnerId", "Este desarrollador está inactivo");
            }
        }
        int cupo = datos.maxProyectos() == null ? cupoActual : datos.maxProyectos();
        if (cupo < 1 || cupo > 3) {
            errores.agregar("maxProyectos", "El cupo es de 1 a 3 proyectos.");
        }
        List<DatosIntegrante> filas = datos.integrantes() == null ? List.of() : datos.integrantes();
        Map<Desarrollador, RolEquipo> integrantes = new LinkedHashMap<>();
        Set<UUID> vistos = new HashSet<>();
        boolean filaVacia = false;
        for (DatosIntegrante fila : filas) {
            if (fila == null || fila.desarrolladorId() == null) {
                filaVacia = true;
                continue;
            }
            Desarrollador dev = repositorio.desarrollador(fila.desarrolladorId()).orElse(null);
            if (dev == null) {
                errores.agregar("integrantes", "Elige un desarrollador de la lista.");
            } else if (!vistos.add(dev.id())) {
                errores.agregar("integrantes", "Cada desarrollador va una sola vez.");
            } else if (po != null && dev.id().equals(po.id())) {
                errores.agregar("integrantes", "El desarrollador (Product Owner) no se repite como integrante.");
            } else if (!dev.activo()) {
                errores.agregar("integrantes", dev.nombre() + " está inactivo");
            } else {
                RolEquipo rol = rolDe(fila.rol());
                if (rol == null) {
                    errores.agregar("integrantes", "Elige el rol de cada integrante.");
                } else if (rol == RolEquipo.PRODUCT_OWNER || !dev.especialidad().permite(rol)) {
                    errores.agregar("integrantes", ROL_NO_CORRESPONDE);
                } else {
                    integrantes.put(dev, rol);
                }
            }
        }
        if (filaVacia) {
            errores.agregar("integrantes", "Elige el desarrollador de cada fila");
        }
        if (filas.isEmpty() || (integrantes.isEmpty() && !errores.tiene("integrantes"))) {
            errores.agregar("integrantes", SIN_INTEGRANTES);
        }
        errores.lanzarSiHay();

        List<Desarrollador> nombrados = new ArrayList<>(integrantes.keySet());
        nombrados.add(po);
        for (Desarrollador dev : nombrados) {
            Integrante en = repositorio.integranteDe(dev.id()).orElse(null);
            if (en != null && !en.equipoId().equals(equipoId)) {
                throw new ConflictoException(dev.equals(po) ? "productOwnerId" : "integrantes", OTRO_EQUIPO);
            }
        }
        return new Plan(po, integrantes, cupo);
    }

    private static RolEquipo rolDe(String texto) {
        try {
            return texto == null ? null : RolEquipo.valueOf(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static MiembroVista miembro(Desarrollador d, RolEquipo rol) {
        return new MiembroVista(d.id(), d.nombre(), d.correo(), d.especialidad(), d.activo(), rol);
    }

    private static String descripcion(Map<Desarrollador, RolEquipo> integrantes) {
        return integrantes.entrySet().stream().map(e -> e.getKey().nombre() + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("Sin integrantes");
    }

    private String descripcionActual(List<Integrante> actuales) {
        Map<UUID, Desarrollador> devs = new HashMap<>();
        repositorio.desarrolladores().forEach(d -> devs.put(d.id(), d));
        return actuales.stream().filter(i -> i.rol() != RolEquipo.PRODUCT_OWNER)
                .map(i -> devs.get(i.desarrolladorId()).nombre() + " (" + i.rol() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("Sin integrantes");
    }
}
