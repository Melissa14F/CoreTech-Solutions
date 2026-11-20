package co.coretech.portal.catalogo.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.catalogo.datos.ContenidoRepositorio;
import co.coretech.portal.catalogo.dominio.ContenidoSitio;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.compartido.aplicacion.ValidacionException;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;

/**
 * Textos de la página principal (HU-07, HU-19, RNF-06): portada, encabezado de servicios, proceso,
 * contacto, llamado final y pie de página. Cada uno tiene su largo mínimo y máximo en contenido_sitio.
 */
@Service
public class ServicioContenido {

    static final String CLAVE_TELEFONO = "contacto-telefono";
    static final String CLAVE_CORREO = "contacto-correo";
    private static final Pattern TELEFONO = Pattern.compile("^[0-9 ]{7,14}$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    /** Quién publicó por última vez y cuándo ("Última publicación: 8 nov 2026 por Patricia León"). */
    public record UltimaPublicacion(Instant fecha, String nombre) {
    }

    /** Lo que ve el administrador: cada texto con su sección y su largo permitido. */
    public record Edicion(List<ContenidoSitio> textos, Optional<UltimaPublicacion> ultimaPublicacion) {
    }

    private final ContenidoRepositorio contenido;
    private final DirectorioUsuarios usuarios;
    private final SesionActual sesion;
    private final RegistroAuditoria auditoria;
    private final Clock reloj;

    public ServicioContenido(ContenidoRepositorio contenido, DirectorioUsuarios usuarios, SesionActual sesion,
            RegistroAuditoria auditoria, Clock reloj) {
        this.contenido = contenido;
        this.usuarios = usuarios;
        this.sesion = sesion;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** HU-07: clave → texto, para pintar la página principal. */
    @Transactional(readOnly = true)
    public Map<String, String> publico() {
        return contenido.findAll().stream()
                .collect(Collectors.toMap(ContenidoSitio::getClave, ContenidoSitio::getValor, (a, b) -> a, TreeMap::new));
    }

    @Transactional(readOnly = true)
    public Edicion edicion() {
        List<ContenidoSitio> textos = contenido.findAll();
        Optional<UltimaPublicacion> ultima = contenido.findTop1ByActualizadoPorIsNotNullOrderByActualizadoEnDesc().stream()
                .findFirst()
                .map(c -> new UltimaPublicacion(c.getActualizadoEn(), usuarios.nombre(c.getActualizadoPor()).orElse("un administrador")));
        return new Edicion(textos, ultima);
    }

    /**
     * HU-19: publica los textos que cambiaron (clave → texto nuevo). Si alguno no cumple, no se publica
     * ninguno y cada error va en su campo (la clave). Todo queda en la auditoría con el valor anterior.
     */
    @Transactional
    public Edicion publicar(Map<String, String> textos) {
        if (textos == null || textos.isEmpty()) {
            throw new ValidacionException(List.of(new ValidacionException.Falla(null, "No hay cambios por publicar.")));
        }
        Map<String, ContenidoSitio> actuales = contenido.findAllById(textos.keySet()).stream()
                .collect(Collectors.toMap(ContenidoSitio::getClave, Function.identity()));

        var errores = new ValidacionException.Acumulador();
        Map<String, String> limpios = new LinkedHashMap<>();
        textos.forEach((clave, valor) -> {
            ContenidoSitio actual = actuales.get(clave);
            if (actual == null) {
                errores.agregar(clave, "Este texto no existe en la página principal.");
                return;
            }
            String texto = valor == null ? "" : valor.trim();
            Optional<String> error = errorDe(actual, texto);
            error.ifPresentOrElse(mensaje -> errores.agregar(clave, mensaje), () -> limpios.put(clave, texto));
        });
        errores.lanzarSiHay();

        Instant ahora = Instant.now(reloj);
        var usuario = sesion.usuario().id();
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        limpios.forEach((clave, texto) -> {
            ContenidoSitio actual = actuales.get(clave);
            if (!actual.getValor().equals(texto)) {
                cambios.put(clave, CambioDeValor.de(actual.getValor(), texto));
                actual.publicar(texto, usuario, ahora);
            }
        });
        if (cambios.isEmpty()) {
            throw new ValidacionException(List.of(new ValidacionException.Falla(null, "No hay cambios por publicar.")));
        }
        contenido.flush();
        auditoria.registrar(CategoriaAuditoria.SITIO, "Publicó cambios en la página principal", "contenido_sitio", null, cambios);
        return edicion();
    }

    /** Mensajes del prototipo (admin/contenido.html). */
    private static Optional<String> errorDe(ContenidoSitio actual, String texto) {
        if (texto.isEmpty()) {
            return Optional.of("Este texto no puede quedar vacío.");
        }
        if (texto.length() < actual.getMinLongitud()) {
            return Optional.of("Escribe al menos " + actual.getMinLongitud() + " caracteres.");
        }
        if (texto.length() > actual.getMaxLongitud()) {
            return Optional.of("Escribe máximo " + actual.getMaxLongitud() + " caracteres.");
        }
        if (CLAVE_TELEFONO.equals(actual.getClave()) && !TELEFONO.matcher(texto).matches()) {
            return Optional.of("Escribe solo números y espacios, por ejemplo 300 123 4567.");
        }
        if (CLAVE_CORREO.equals(actual.getClave()) && !CORREO.matcher(texto).matches()) {
            return Optional.of("Escribe un correo válido, por ejemplo nombre@empresa.com.");
        }
        return Optional.empty();
    }
}
