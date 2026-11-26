package co.coretech.portal.catalogo.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.catalogo.CitasDeServicios;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.catalogo.PlanesDeServicios;
import co.coretech.portal.catalogo.repository.ServicioRepositorio;
import co.coretech.portal.catalogo.domain.Servicio;
import co.coretech.portal.compartido.service.AlmacenArchivos;
import co.coretech.portal.compartido.service.AlmacenArchivos.TipoImagen;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import static co.coretech.portal.compartido.service.Textos.limpio;

/**
 * Servicios del catálogo (HU-07, HU-18, RN-21, RNF-06): el sitio muestra los visibles en su orden; el
 * administrador los crea, edita con vista previa, ordena, oculta y elimina (solo sin citas ni proyectos).
 */
@Service
public class ServicioCatalogo implements DirectorioServicios {

    static final String NO_ENCONTRADO = "Este servicio ya no está disponible";
    static final String NOMBRE_REPETIDO = "Ya existe un servicio con este nombre.";
    static final String EN_USO = "Este servicio tiene citas o proyectos; puedes ocultarlo";
    static final String IMAGEN_PESADA = "La imagen pesa más de 2 MB. Comprímela o usa formato WebP.";
    static final String IMAGEN_FORMATO = "Sube una imagen en formato WebP, PNG o JPG.";
    private static final Duration SEIS_MESES = Duration.ofDays(183);

    /** Datos del formulario "Nuevo servicio" / "Editar servicio". */
    public record DatosServicio(String nombre, String descripcionCorta, String imagenUrl, String imagenAlt, Boolean visible) {
    }

    /** Un servicio como lo ve el administrador: con sus citas de los últimos 6 meses y las fases de su plantilla. */
    public record VistaServicio(Servicio servicio, long citasUltimos6Meses, int fasesPlantilla) {
    }

    private final ServicioRepositorio servicios;
    private final CitasDeServicios citas;
    private final PlanesDeServicios planes;
    private final AlmacenArchivos almacen;
    private final RegistroAuditoria auditoria;
    private final Clock reloj;

    public ServicioCatalogo(ServicioRepositorio servicios, CitasDeServicios citas, PlanesDeServicios planes,
            AlmacenArchivos almacen, RegistroAuditoria auditoria, Clock reloj) {
        this.servicios = servicios;
        this.citas = citas;
        this.planes = planes;
        this.almacen = almacen;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** HU-07: servicios visibles, en el orden del carrusel. */
    @Transactional(readOnly = true)
    public List<Servicio> visibles() {
        return servicios.findByVisibleTrueOrderByOrdenAsc();
    }

    /** Nombre del servicio si existe y está visible; vacío si no existe o está oculto (HU-01: "ya no está disponible"). */
    @Transactional(readOnly = true)
    @Override
    public Optional<String> nombreSiVisible(UUID id) {
        return servicios.findById(id).filter(Servicio::isVisible).map(Servicio::getNombre);
    }

    /** Nombres de los servicios pedidos (también ocultos: una cita vieja los conserva), por id. */
    @Transactional(readOnly = true)
    @Override
    public Map<UUID, String> nombres(Collection<UUID> ids) {
        Map<UUID, String> nombres = new LinkedHashMap<>();
        servicios.findAllById(ids).forEach(s -> nombres.put(s.getId(), s.getNombre()));
        return nombres;
    }

    /** HU-18: todos los servicios (también los ocultos), con su uso. */
    @Transactional(readOnly = true)
    public List<VistaServicio> todos() {
        Map<UUID, Long> citasRecientes = citas.contarDesde(Instant.now(reloj).minus(SEIS_MESES));
        Map<UUID, Integer> fases = planes.fasesDePlantilla();
        return servicios.findAllByOrderByOrdenAsc().stream()
                .map(s -> new VistaServicio(s, citasRecientes.getOrDefault(s.getId(), 0L), fases.getOrDefault(s.getId(), 0)))
                .toList();
    }

    @Transactional
    public VistaServicio crear(DatosServicio datos) {
        DatosServicio d = validar(datos, null);
        Instant ahora = Instant.now(reloj);
        boolean visible = d.visible() == null || d.visible();
        Servicio servicio = Servicio.nuevo(d.nombre(), slugUnico(d.nombre()), d.descripcionCorta(), d.imagenUrl(), d.imagenAlt(),
                (short) (servicios.ultimaPosicion() + 1), visible, ahora);
        servicios.saveAndFlush(servicio);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("nombre", CambioDeValor.nuevo(servicio.getNombre()));
        cambios.put("visible", CambioDeValor.nuevo(visible));
        auditoria.registrar(CategoriaAuditoria.SITIO, "Creó un servicio del catálogo", "servicio", servicio.getSlug(), cambios);
        return vista(servicio);
    }

    @Transactional
    public VistaServicio editar(UUID id, DatosServicio datos) {
        Servicio servicio = buscar(id);
        DatosServicio d = validar(datos, id);
        boolean visible = d.visible() == null ? servicio.isVisible() : d.visible();

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        anotar(cambios, "nombre", servicio.getNombre(), d.nombre());
        anotar(cambios, "descripcionCorta", servicio.getDescripcionCorta(), d.descripcionCorta());
        anotar(cambios, "imagenUrl", servicio.getImagenUrl(), d.imagenUrl());
        anotar(cambios, "imagenAlt", servicio.getImagenAlt(), d.imagenAlt());
        anotar(cambios, "visible", servicio.isVisible(), visible);
        servicio.editar(d.nombre(), d.descripcionCorta(), d.imagenUrl(), d.imagenAlt(), visible, Instant.now(reloj));
        servicios.flush();
        if (!cambios.isEmpty()) {
            auditoria.registrar(CategoriaAuditoria.SITIO, "Editó un servicio del catálogo", "servicio", servicio.getSlug(), cambios);
        }
        return vista(servicio);
    }

    /** Ocultar o mostrar en el catálogo (RN-21: un servicio oculto no aparece en el sitio ni al agendar). */
    @Transactional
    public VistaServicio cambiarVisibilidad(UUID id, boolean visible) {
        Servicio servicio = buscar(id);
        if (servicio.isVisible() != visible) {
            boolean antes = servicio.isVisible();
            servicio.cambiarVisibilidad(visible, Instant.now(reloj));
            auditoria.registrar(CategoriaAuditoria.SITIO, visible ? "Mostró un servicio en el catálogo" : "Ocultó un servicio del catálogo",
                    "servicio", servicio.getSlug(), Map.of("visible", CambioDeValor.de(antes, visible)));
        }
        return vista(servicio);
    }

    /** Sube (-1) o baja (+1) un lugar en el carrusel. Devuelve la lista completa en su nuevo orden. */
    @Transactional
    public List<VistaServicio> mover(UUID id, int direccion) {
        if (direccion != -1 && direccion != 1) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("direccion", "Indica -1 para subir o 1 para bajar.")));
        }
        Servicio servicio = buscar(id);
        List<Servicio> lista = servicios.findAllByOrderByOrdenAsc();
        int posicion = lista.indexOf(servicio);
        int destino = posicion + direccion;
        if (destino < 0 || destino >= lista.size()) {
            throw new ConflictoException(direccion < 0 ? "Este servicio ya es el primero del carrusel" : "Este servicio ya es el último del carrusel");
        }
        Servicio vecino = lista.get(destino);
        short antes = servicio.getOrden();
        Instant ahora = Instant.now(reloj);
        servicio.ponerEnPosicion(vecino.getOrden(), ahora);
        vecino.ponerEnPosicion(antes, ahora);
        servicios.flush();
        auditoria.registrar(CategoriaAuditoria.SITIO, "Cambió el orden de los servicios", "servicio", servicio.getSlug(),
                Map.of("orden", CambioDeValor.de(antes, servicio.getOrden())));
        return todos();
    }

    /** RN-21: solo sin citas ni proyectos; se borra también su plantilla de fases. */
    @Transactional
    public void eliminar(UUID id) {
        Servicio servicio = buscar(id);
        if (citas.tieneCitas(id) || planes.tieneProyectos(id)) {
            throw new ConflictoException(EN_USO);
        }
        planes.eliminarPlantilla(id);
        short orden = servicio.getOrden();
        servicios.delete(servicio);
        servicios.flush();
        Instant ahora = Instant.now(reloj);
        servicios.findAllByOrderByOrdenAsc().stream()
                .filter(s -> s.getOrden() > orden)
                .forEach(s -> s.ponerEnPosicion((short) (s.getOrden() - 1), ahora));
        auditoria.registrar(CategoriaAuditoria.SITIO, "Eliminó un servicio del catálogo", "servicio", servicio.getSlug(),
                Map.of("nombre", new CambioDeValor(servicio.getNombre(), null)));
    }

    /**
     * Imagen propia de un servicio (WebP, PNG o JPG de máximo 2 MB, reconocida por su contenido).
     * @return la URL con la que se guarda en el servicio
     */
    @Transactional
    public String subirImagen(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("imagen", IMAGEN_FORMATO)));
        }
        if (contenido.length > ImagenesServicio.MAX_BYTES) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("imagen", IMAGEN_PESADA)));
        }
        TipoImagen tipo = TipoImagen.reconocer(contenido)
                .orElseThrow(() -> new ValidacionException(List.of(new ValidacionException.Falla("imagen", IMAGEN_FORMATO))));
        String nombre = almacen.guardar(ImagenesServicio.CARPETA, contenido, tipo.extension());
        // entidad_id admite 40 caracteres: se guarda el UUID del archivo, sin la extensión
        auditoria.registrar(CategoriaAuditoria.SITIO, "Subió una imagen para un servicio", "imagen_servicio",
                nombre.substring(0, nombre.indexOf('.')));
        return ImagenesServicio.PREFIJO_SUBIDA + nombre;
    }

    /** Una imagen subida, para servirla en el sitio. */
    /**
     * Conservación de datos (RNF-09): borra las imágenes subidas que ningún servicio usa. Solo las de más de un
     * día, porque el editor sube la imagen antes de guardar el servicio. Devuelve cuántas borró.
     */
    @Transactional(readOnly = true)
    public int limpiarImagenesSinUso() {
        Set<String> enUso = servicios.findAll().stream()
                .map(Servicio::getImagenUrl)
                .filter(url -> url != null && url.startsWith(ImagenesServicio.PREFIJO_SUBIDA))
                .map(url -> url.substring(ImagenesServicio.PREFIJO_SUBIDA.length()))
                .collect(Collectors.toSet());
        Instant limite = Instant.now(reloj).minus(Duration.ofDays(1));
        int borradas = 0;
        for (AlmacenArchivos.Guardado archivo : almacen.listar(ImagenesServicio.CARPETA)) {
            if (!enUso.contains(archivo.nombre()) && archivo.modificado().isBefore(limite)
                    && almacen.borrar(ImagenesServicio.CARPETA, archivo.nombre())) {
                borradas++;
            }
        }
        return borradas;
    }

    public Optional<AlmacenArchivos.Archivo> imagen(String nombre) {
        return TipoImagen.deNombre(nombre).flatMap(tipo -> almacen.leer(ImagenesServicio.CARPETA, nombre, tipo.tipoMime()));
    }

    // ---------------------------------------------------------------- reglas

    /** Valida y limpia los datos (mismos límites y mensajes del prototipo, admin/servicios.html). */
    private DatosServicio validar(DatosServicio datos, UUID editando) {
        String nombre = limpio(datos.nombre());
        String descripcion = limpio(datos.descripcionCorta());
        String imagen = limpio(datos.imagenUrl());
        String alt = limpio(datos.imagenAlt());

        var errores = new ValidacionException.Acumulador();
        longitud(errores, "nombre", nombre, 4, 40, "Escribe el nombre del servicio.");
        longitud(errores, "descripcionCorta", descripcion, 20, 90, "Escribe la descripción.");
        if (imagen.isEmpty() || !imagenValida(imagen)) {
            errores.agregar("imagenUrl", "Elige o sube una imagen.");
        }
        longitud(errores, "imagenAlt", alt, 10, 120, "Describe la imagen: lo leen las personas con lector de pantalla.");
        errores.lanzarSiHay();

        boolean repetido = editando == null ? servicios.existsByNombreIgnoreCase(nombre)
                : servicios.existsByNombreIgnoreCaseAndIdNot(nombre, editando);
        if (repetido) {
            throw new ConflictoException("nombre", NOMBRE_REPETIDO);
        }
        return new DatosServicio(nombre, descripcion, imagen, alt, datos.visible());
    }

    private static void longitud(ValidacionException.Acumulador errores, String campo, String valor, int min, int max, String vacio) {
        if (valor.isEmpty()) {
            errores.agregar(campo, vacio);
        } else if (valor.length() < min) {
            errores.agregar(campo, "Escribe al menos " + min + " caracteres.");
        } else if (valor.length() > max) {
            errores.agregar(campo, "Escribe máximo " + max + " caracteres.");
        }
    }

    private boolean imagenValida(String url) {
        if (ImagenesServicio.GALERIA.contains(url)) {
            return true;
        }
        return url.startsWith(ImagenesServicio.PREFIJO_SUBIDA)
                && almacen.existe(ImagenesServicio.CARPETA, url.substring(ImagenesServicio.PREFIJO_SUBIDA.length()));
    }

    /**
     * "Desarrollo de tienda en línea" → "desarrollo-de-tienda-en-linea", único entre los servicios. Máximo 40
     * caracteres (la columna admite 50, pero el slug es el identificador del servicio en la auditoría).
     */
    String slugUnico(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        base = base.isEmpty() ? "servicio" : base.substring(0, Math.min(base.length(), 36)).replaceAll("-$", "");
        String slug = base;
        for (int n = 2; servicios.existsBySlug(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private Servicio buscar(UUID id) {
        return servicios.findById(id).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private VistaServicio vista(Servicio s) {
        long recientes = citas.contarDesde(Instant.now(reloj).minus(SEIS_MESES)).getOrDefault(s.getId(), 0L);
        return new VistaServicio(s, recientes, planes.fasesDePlantilla().getOrDefault(s.getId(), 0));
    }

    private static void anotar(Map<String, CambioDeValor> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, CambioDeValor.de(antes, despues));
        }
    }
}
