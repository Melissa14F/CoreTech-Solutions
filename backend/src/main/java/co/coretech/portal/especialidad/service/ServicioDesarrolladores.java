package co.coretech.portal.especialidad.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.CuentasDeProductOwner;
import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.service.Vistas.DesarrolladorVista;
import co.coretech.portal.especialidad.service.Vistas.EquipoResumen;
import co.coretech.portal.especialidad.repository.RepositorioEspecialidad;
import co.coretech.portal.especialidad.domain.Desarrollador;
import co.coretech.portal.especialidad.domain.Especialidad;
import co.coretech.portal.especialidad.domain.Integrante;
import co.coretech.portal.especialidad.domain.RolEquipo;

/**
 * Registro de desarrolladores con su especialidad (HU-25; RN-25, RN-26): registrar, editar, activar y desactivar.
 * Solo el Product Owner de un equipo tiene cuenta; su correo es su usuario, por eso sus datos se mantienen al día
 * en la cuenta. Todo cambio queda en la auditoría (RN-20).
 */
@Service
public class ServicioDesarrolladores {

    static final String DOMINIO = "@coretechsolutions.co";
    static final String CORREO_REPETIDO = "Ya hay un desarrollador registrado con este correo";
    static final String NO_ENCONTRADO = "Desarrollador no encontrado";
    /** RN-26, D-34: mismo texto que HU-25 y que Usuarios. */
    static final String PO_CON_PROYECTOS = "Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo";

    private static final Pattern SOLO_LETRAS = Pattern.compile("^[\\p{L} ]+$");
    private static final Pattern CELULAR = Pattern.compile("^3\\d{9}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final RepositorioEspecialidad repositorio;
    private final CuentasDeProductOwner cuentas;
    private final DirectorioUsuarios directorio;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioDesarrolladores(RepositorioEspecialidad repositorio, CuentasDeProductOwner cuentas,
            DirectorioUsuarios directorio, RegistroAuditoria auditoria, ApplicationEventPublisher eventos, Clock reloj) {
        this.repositorio = repositorio;
        this.cuentas = cuentas;
        this.directorio = directorio;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Lo que el administrador escribe en el formulario. */
    public record DatosDesarrollador(String nombre, String celular, String correo, String especialidad) {
    }

    // ------------------------------------------------------------------ Consultas

    @Transactional(readOnly = true)
    public List<DesarrolladorVista> listar() {
        Map<UUID, Integrante> integrantes = new HashMap<>();
        Map<UUID, String> productOwners = new HashMap<>();
        Map<UUID, Desarrollador> porId = new HashMap<>();
        List<Desarrollador> todos = repositorio.desarrolladores();
        todos.forEach(d -> porId.put(d.id(), d));
        for (Integrante i : repositorio.integrantes()) {
            integrantes.put(i.desarrolladorId(), i);
            if (i.rol() == RolEquipo.PRODUCT_OWNER) {
                productOwners.put(i.equipoId(), porId.get(i.desarrolladorId()).nombre());
            }
        }
        return todos.stream().map(d -> vista(d, integrantes.get(d.id()), productOwners)).toList();
    }

    @Transactional(readOnly = true)
    public DesarrolladorVista obtener(UUID id) {
        return listar().stream().filter(v -> v.desarrolladorId().equals(id)).findFirst()
                .orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    // ------------------------------------------------------------------ Cambios

    /** HU-25: queda en el registro, sin cuenta y disponible para sumarse a un equipo. */
    @Transactional
    public DesarrolladorVista registrar(DatosDesarrollador datos) {
        Limpios l = validar(datos, null);
        Desarrollador d = new Desarrollador(UUID.randomUUID(), l.nombre, l.correo, l.celular, l.especialidad, null, true,
                LocalDate.now(reloj));
        repositorio.insertar(d);
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("nombre", CambioDeValor.nuevo(d.nombre()));
        cambios.put("correo", CambioDeValor.nuevo(d.correo()));
        cambios.put("especialidad", CambioDeValor.nuevo(d.especialidad()));
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Registró un desarrollador", "desarrollador", d.id().toString(), cambios);
        return obtener(d.id());
    }

    /**
     * HU-25: corrige los datos. Si cambia la especialidad y su rol en el equipo ya no corresponde, el rol pasa al
     * primero que permite la nueva especialidad.
     */
    @Transactional
    public DesarrolladorVista editar(UUID id, DatosDesarrollador datos) {
        Desarrollador actual = buscar(id);
        Limpios l = validar(datos, actual.id());
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        anotar(cambios, "nombre", actual.nombre(), l.nombre);
        anotar(cambios, "celular", actual.celular(), l.celular);
        anotar(cambios, "correo", actual.correo(), l.correo);
        anotar(cambios, "especialidad", actual.especialidad(), l.especialidad);
        if (cambios.isEmpty()) {
            return obtener(id);
        }
        repositorio.actualizar(id, l.nombre, l.correo, l.celular, l.especialidad);
        if (actual.usuarioId() != null) {
            cuentas.actualizarDatos(actual.usuarioId(), l.nombre, l.correo, l.celular);
        }
        Integrante integrante = repositorio.integranteDe(id).orElse(null);
        if (integrante != null && integrante.rol() != RolEquipo.PRODUCT_OWNER && !l.especialidad.permite(integrante.rol())) {
            RolEquipo nuevo = l.especialidad.rolesPermitidos().get(0);
            repositorio.cambiarRol(id, nuevo);
            cambios.put("rolEnElEquipo", CambioDeValor.de(integrante.rol(), nuevo));
        }
        auditoria.registrar(CategoriaAuditoria.USUARIOS, "Editó un desarrollador", "desarrollador", id.toString(), cambios);
        return obtener(id);
    }

    /**
     * HU-25: activar o desactivar. Un Product Owner con proyectos activos no se desactiva (409, RN-26); sin
     * proyectos, su cuenta queda bloqueada hasta que el administrador la reactive. Un integrante sale de su equipo,
     * salvo que sea el único además del Product Owner (409, RN-25, D-43).
     */
    @Transactional
    public DesarrolladorVista cambiarActivo(UUID id, boolean activo) {
        Desarrollador d = buscar(id);
        if (d.activo() == activo) {
            return obtener(id);
        }
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("activo", CambioDeValor.de(!activo, activo));
        if (!activo) {
            Integrante integrante = repositorio.integranteDe(id).orElse(null);
            if (integrante != null && integrante.rol() == RolEquipo.PRODUCT_OWNER) {
                if (repositorio.proyectosActivosPorEquipo().getOrDefault(integrante.equipoId(), 0) > 0) {
                    throw new ConflictoException(PO_CON_PROYECTOS);
                }
                repositorio.cambiarActivo(id, false);
                if (d.usuarioId() != null) {
                    cuentas.bloquear(d.usuarioId());
                }
                avisarCitasSinProductOwner(d, integrante.equipoId());
            } else if (integrante != null) {
                long total = repositorio.integrantes().stream().filter(i -> i.equipoId().equals(integrante.equipoId())).count();
                if (total <= 2) {
                    throw new ConflictoException("El equipo de " + nombreDelProductOwner(integrante.equipoId())
                            + " quedaría sin integrantes; agrega otro antes de desactivarlo");
                }
                repositorio.quitarIntegrante(id);
                repositorio.cambiarActivo(id, false);
                cambios.put("equipo", CambioDeValor.de("Equipo de " + nombreDelProductOwner(integrante.equipoId()), null));
            } else {
                repositorio.cambiarActivo(id, false);
            }
        } else {
            repositorio.cambiarActivo(id, true);
        }
        auditoria.registrar(CategoriaAuditoria.USUARIOS, activo ? "Activó un desarrollador" : "Desactivó un desarrollador",
                "desarrollador", id.toString(), cambios);
        return obtener(id);
    }

    // ------------------------------------------------------------------ Apoyo

    private void avisarCitasSinProductOwner(Desarrollador po, UUID equipoId) {
        int citas = repositorio.citasPorAtender(equipoId);
        List<UUID> administradores = directorio.administradoresActivos();
        if (citas == 0 || administradores.isEmpty()) {
            return;
        }
        String texto = "El equipo de " + po.nombre() + " quedó sin desarrollador (Product Owner) activo y tiene "
                + (citas == 1 ? "1 cita por atender" : citas + " citas por atender") + ". Reasígnalas a otro equipo.";
        eventos.publishEvent(new AvisoSolicitado(administradores, "EQUIPO_SIN_PRODUCT_OWNER", "Un equipo quedó sin Product Owner",
                texto.length() > 255 ? texto.substring(0, 255) : texto, "/admin/citas?sinEquipo=true"));
    }

    private String nombreDelProductOwner(UUID equipoId) {
        return repositorio.integrantes().stream()
                .filter(i -> i.equipoId().equals(equipoId) && i.rol() == RolEquipo.PRODUCT_OWNER)
                .findFirst().flatMap(i -> repositorio.desarrollador(i.desarrolladorId())).map(Desarrollador::nombre)
                .orElse("este equipo");
    }

    private Desarrollador buscar(UUID id) {
        return repositorio.desarrollador(id).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private static DesarrolladorVista vista(Desarrollador d, Integrante integrante, Map<UUID, String> productOwners) {
        EquipoResumen equipo = integrante == null ? null
                : new EquipoResumen(integrante.equipoId(), productOwners.get(integrante.equipoId()));
        return new DesarrolladorVista(d.id(), d.nombre(), d.correo(), d.celular(), d.especialidad(), d.activo(),
                d.fechaIngreso(), d.usuarioId() != null, equipo, integrante == null ? null : integrante.rol());
    }

    private record Limpios(String nombre, String celular, String correo, Especialidad especialidad) {
    }

    /** Reglas de HU-25, todas a la vez (un error por campo); el correo es único (409). */
    private Limpios validar(DatosDesarrollador datos, UUID propio) {
        String nombre = datos.nombre() == null ? "" : datos.nombre().trim().replaceAll("\\s+", " ");
        String celular = datos.celular() == null ? "" : datos.celular().trim();
        String correo = datos.correo() == null ? "" : datos.correo().trim().toLowerCase();
        Especialidad especialidad = null;
        var errores = new ValidacionException.Acumulador();
        if (nombre.isEmpty()) {
            errores.agregar("nombre", "Escribe el nombre completo.");
        } else if (nombre.length() < 5) {
            errores.agregar("nombre", "Escribe nombre y apellido.");
        } else if (nombre.length() > 60) {
            errores.agregar("nombre", "Escribe máximo 60 caracteres.");
        } else if (!SOLO_LETRAS.matcher(nombre).matches()) {
            errores.agregar("nombre", "Escribe solo letras y espacios.");
        }
        if (celular.isEmpty()) {
            errores.agregar("celular", "Escribe el número de celular.");
        } else if (!CELULAR.matcher(celular).matches()) {
            errores.agregar("celular", "Escribe un celular de 10 dígitos que empiece por 3.");
        }
        if (correo.isEmpty()) {
            errores.agregar("correo", "Escribe el correo corporativo.");
        } else if (correo.length() > 120) {
            errores.agregar("correo", "Escribe máximo 120 caracteres.");
        } else if (!CORREO.matcher(correo).matches()) {
            errores.agregar("correo", "Escribe un correo válido, por ejemplo nombre@empresa.com.");
        } else if (!correo.endsWith(DOMINIO)) {
            errores.agregar("correo", "Usa el correo corporativo que termina en @coretechsolutions.co.");
        }
        if (datos.especialidad() == null || datos.especialidad().isBlank()) {
            errores.agregar("especialidad", "Elige una especialidad.");
        } else {
            try {
                especialidad = Especialidad.valueOf(datos.especialidad());
            } catch (IllegalArgumentException e) {
                errores.agregar("especialidad", "Elige una especialidad de la lista.");
            }
        }
        errores.lanzarSiHay();
        repositorio.desarrolladorPorCorreo(correo).filter(otro -> !Objects.equals(otro.id(), propio)).ifPresent(otro -> {
            throw new ConflictoException("correo", CORREO_REPETIDO);
        });
        return new Limpios(nombre, celular, correo, especialidad);
    }

    private static void anotar(Map<String, CambioDeValor> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, CambioDeValor.de(antes, despues));
        }
    }
}
