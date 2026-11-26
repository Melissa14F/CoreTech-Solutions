package co.coretech.portal.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.coretech.portal.catalogo.CitasDeServicios;
import co.coretech.portal.catalogo.PlanesDeServicios;
import co.coretech.portal.catalogo.service.ServicioCatalogo.DatosServicio;
import co.coretech.portal.catalogo.repository.ServicioRepositorio;
import co.coretech.portal.catalogo.domain.Servicio;
import co.coretech.portal.compartido.service.AlmacenArchivos;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * Pruebas UNITARIAS de {@link ServicioCatalogo} (HU-07, HU-18; RN-21): el catálogo de servicios. Un servicio con citas o
 * proyectos no se elimina, solo se oculta; uno oculto no aparece en el sitio ni al agendar. El repositorio, las citas, los planes,
 * el almacén de imágenes y la auditoría van simulados. Reloj fijo: 2031-03-04.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioCatalogoUnitarioTest {

    static final Instant AHORA = Instant.parse("2031-03-04T15:00:00Z");
    static final String GALERIA = ImagenesServicio.GALERIA.get(0);
    static final String DESCRIPCION = "Catálogo, carrito de compras y pagos en línea.";
    static final String ALT = "Ilustración de una tienda en línea con productos";

    @Mock
    ServicioRepositorio servicios;
    @Mock
    CitasDeServicios citas;
    @Mock
    PlanesDeServicios planes;
    @Mock
    AlmacenArchivos almacen;
    @Mock
    RegistroAuditoria auditoria;

    ServicioCatalogo servicio;
    final List<Servicio> lista = new ArrayList<>();
    Servicio tienda;
    Servicio web;
    Servicio movil;

    static Servicio servicioDe(String nombre, String slug, int orden, boolean visible) {
        return Servicio.nuevo(nombre, slug, DESCRIPCION, GALERIA, ALT, (short) orden, visible, AHORA.minusSeconds(100000));
    }

    @BeforeEach
    void preparaLoComun() {
        tienda = servicioDe("Desarrollo de tienda en línea", "desarrollo-de-tienda-en-linea", 1, true);
        web = servicioDe("Web corporativa", "web-corporativa", 2, true);
        movil = servicioDe("App móvil", "app-movil", 3, false);
        lista.addAll(List.of(tienda, web, movil));
        when(servicios.findAllByOrderByOrdenAsc()).thenAnswer(i -> lista.stream().sorted(java.util.Comparator.comparingInt(Servicio::getOrden)).toList());
        when(servicios.findByVisibleTrueOrderByOrdenAsc()).thenAnswer(i -> lista.stream().filter(Servicio::isVisible).toList());
        when(servicios.findAll()).thenAnswer(i -> lista);
        when(servicios.findById(any())).thenAnswer(i -> lista.stream().filter(s -> s.getId().equals(i.getArgument(0))).findFirst());
        when(servicios.findAllById(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return lista.stream().filter(s -> ids.contains(s.getId())).toList();
        });
        when(servicios.ultimaPosicion()).thenReturn(3);
        when(servicios.saveAndFlush(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));
        when(citas.contarDesde(any())).thenReturn(Map.of(tienda.getId(), 26L));
        when(planes.fasesDePlantilla()).thenReturn(Map.of(tienda.getId(), 5, web.getId(), 4));
        when(almacen.existe(eq("servicios"), anyString())).thenReturn(false);
        servicio = new ServicioCatalogo(servicios, citas, planes, almacen, auditoria, Clock.fixed(AHORA, ZoneId.of("America/Bogota")));
    }

    static DatosServicio datos(String nombre, String descripcion, String imagen, String alt, Boolean visible) {
        return new DatosServicio(nombre, descripcion, imagen, alt, visible);
    }

    static DatosServicio validos(String nombre) {
        return datos(nombre, DESCRIPCION, GALERIA, ALT, true);
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    // ------------------------------------------------------------------------------------------------ consultar

    @Nested
    @DisplayName("Consultar el catálogo (HU-07, RN-21)")
    class Consultar {

        @Test
        @DisplayName("El sitio solo muestra los servicios visibles, en el orden del carrusel; uno oculto no aparece ni se puede agendar")
        void visibles() {
            assertThat(servicio.visibles()).extracting(Servicio::getNombre).containsExactly("Desarrollo de tienda en línea", "Web corporativa");
            assertThat(servicio.nombreSiVisible(tienda.getId())).contains("Desarrollo de tienda en línea");
            assertThat(servicio.nombreSiVisible(movil.getId())).as("oculto").isEmpty();
            assertThat(servicio.nombreSiVisible(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("nombres trae también los ocultos (una cita vieja conserva su servicio) y no inventa los que no existen")
        void nombres() {
            assertThat(servicio.nombres(List.of(tienda.getId(), movil.getId(), UUID.randomUUID())))
                    .containsOnlyKeys(tienda.getId(), movil.getId()).containsEntry(movil.getId(), "App móvil");
        }

        @Test
        @DisplayName("El administrador ve todos (también los ocultos) con sus citas de los últimos 6 meses y las fases de su plantilla; 0 si no hay")
        void todos() {
            var todos = servicio.todos();

            assertThat(todos).hasSize(3);
            assertThat(todos.get(0).citasUltimos6Meses()).isEqualTo(26L);
            assertThat(todos.get(0).fasesPlantilla()).isEqualTo(5);
            assertThat(todos.get(2).citasUltimos6Meses()).isZero();
            assertThat(todos.get(2).fasesPlantilla()).isZero();
            ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
            verify(citas).contarDesde(desde.capture());
            assertThat(desde.getValue()).isEqualTo(AHORA.minus(java.time.Duration.ofDays(183)));
        }
    }

    // ------------------------------------------------------------------------------------------------ crear

    @Nested
    @DisplayName("Crear un servicio (HU-18)")
    class Crear {

        @Test
        @DisplayName("Se crea al final del carrusel, visible por omisión, con un identificador sin tildes ni espacios, y se audita")
        void crea() {
            var v = servicio.crear(datos("  Consultoría de Datos y Análisis ", DESCRIPCION, GALERIA, ALT, null));

            ArgumentCaptor<Servicio> guardado = ArgumentCaptor.forClass(Servicio.class);
            verify(servicios).saveAndFlush(guardado.capture());
            Servicio s = guardado.getValue();
            assertThat(s.getNombre()).isEqualTo("Consultoría de Datos y Análisis");
            assertThat(s.getSlug()).isEqualTo("consultoria-de-datos-y-analisis");
            assertThat(s.getOrden()).isEqualTo((short) 4);
            assertThat(s.isVisible()).isTrue();
            assertThat(v.servicio()).isSameAs(s);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SITIO), eq("Creó un servicio del catálogo"), eq("servicio"), eq("consultoria-de-datos-y-analisis"), anyMap());
        }

        @Test
        @DisplayName("Puede crearse oculto")
        void creaOculto() {
            servicio.crear(datos("Servicio nuevo", DESCRIPCION, GALERIA, ALT, false));

            ArgumentCaptor<Servicio> guardado = ArgumentCaptor.forClass(Servicio.class);
            verify(servicios).saveAndFlush(guardado.capture());
            assertThat(guardado.getValue().isVisible()).isFalse();
        }

        @Test
        @DisplayName("slugUnico: sin tildes ni símbolos, en minúscula, de máximo 36 caracteres, y con -2, -3… si el identificador ya existe")
        void slugs() {
            assertThat(servicio.slugUnico("Integración de Pagos")).isEqualTo("integracion-de-pagos");
            assertThat(servicio.slugUnico("¡¡¡ Ñandú & Co. !!!")).isEqualTo("nandu-co");
            assertThat(servicio.slugUnico("%%%%")).as("sin letras ni números queda «servicio»").isEqualTo("servicio");
            assertThat(servicio.slugUnico("a".repeat(100))).hasSize(36);

            when(servicios.existsBySlug("integracion-de-pagos")).thenReturn(true);
            when(servicios.existsBySlug("integracion-de-pagos-2")).thenReturn(true);
            assertThat(servicio.slugUnico("Integración de Pagos")).isEqualTo("integracion-de-pagos-3");
        }

        @Test
        @DisplayName("Valores límite del nombre: 3 caracteres no, 4 sí, 40 sí, 41 no")
        void limitesDelNombre() {
            assertThatThrownBy(() -> servicio.crear(validos("abc")))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe al menos 4 caracteres."));
            assertThatThrownBy(() -> servicio.crear(validos("a".repeat(41))))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 40 caracteres."));
            servicio.crear(validos("abcd"));
            servicio.crear(validos("b".repeat(40)));
            verify(servicios, org.mockito.Mockito.times(2)).saveAndFlush(any(Servicio.class));
        }

        @Test
        @DisplayName("Valores límite de la descripción (20 a 90) y del texto alternativo de la imagen (10 a 120)")
        void limitesDeLosTextos() {
            assertThatThrownBy(() -> servicio.crear(datos("Servicio nuevo", "x".repeat(19), GALERIA, ALT, true))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.crear(datos("Servicio nuevo", "x".repeat(91), GALERIA, ALT, true))).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.crear(datos("Servicio nuevo", DESCRIPCION, GALERIA, "x".repeat(9), true)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("imagenAlt"));
            assertThatThrownBy(() -> servicio.crear(datos("Servicio nuevo", DESCRIPCION, GALERIA, "x".repeat(121), true))).isInstanceOf(ValidacionException.class);

            servicio.crear(datos("Servicio uno", "x".repeat(20), GALERIA, "x".repeat(10), true));
            servicio.crear(datos("Servicio dos", "x".repeat(90), GALERIA, "x".repeat(120), true));
            verify(servicios, org.mockito.Mockito.times(2)).saveAndFlush(any(Servicio.class));
        }

        @Test
        @DisplayName("La imagen debe ser de la galería o una subida que exista en el almacén; si no, 422; todos los errores salen juntos")
        void laImagen() {
            assertThatThrownBy(() -> servicio.crear(datos("Servicio nuevo", DESCRIPCION, "https://otro-sitio.com/foto.png", ALT, true)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("imagenUrl"));
            assertThatThrownBy(() -> servicio.crear(datos("Servicio nuevo", DESCRIPCION, ImagenesServicio.PREFIJO_SUBIDA + "no-existe.png", ALT, true)))
                    .isInstanceOf(ValidacionException.class);

            when(almacen.existe("servicios", "abc123.png")).thenReturn(true);
            servicio.crear(datos("Servicio nuevo", DESCRIPCION, ImagenesServicio.PREFIJO_SUBIDA + "abc123.png", ALT, true));

            assertThatThrownBy(() -> servicio.crear(datos(null, null, null, null, null)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("nombre", "descripcionCorta", "imagenUrl", "imagenAlt"));
        }

        @Test
        @DisplayName("Un nombre repetido (sin distinguir mayúsculas) da 409 en el campo nombre y no se guarda nada")
        void nombreRepetido() {
            when(servicios.existsByNombreIgnoreCase("web corporativa")).thenReturn(true);

            assertThatThrownBy(() -> servicio.crear(validos("web corporativa")))
                    .isInstanceOfSatisfying(ConflictoException.class, e -> assertThat(e.getMessage()).isEqualTo(ServicioCatalogo.NOMBRE_REPETIDO));
            verify(servicios, never()).saveAndFlush(any(Servicio.class));
        }
    }

    // ------------------------------------------------------------------------------------------------ editar y visibilidad

    @Nested
    @DisplayName("Editar, ocultar y mover")
    class EditarYMover {

        @Test
        @DisplayName("Editar guarda lo nuevo y la auditoría anota solo lo que cambió; sin cambios no audita; sin «visible» conserva el actual")
        void edita() {
            servicio.editar(web.getId(), datos("Web corporativa pro", DESCRIPCION, GALERIA, ALT, null));

            assertThat(web.getNombre()).isEqualTo("Web corporativa pro");
            assertThat(web.isVisible()).isTrue();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, co.coretech.portal.compartido.domain.CambioDeValor>> cambios = ArgumentCaptor.forClass(Map.class);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SITIO), eq("Editó un servicio del catálogo"), eq("servicio"), eq("web-corporativa"), cambios.capture());
            assertThat(cambios.getValue()).containsOnlyKeys("nombre");

            org.mockito.Mockito.clearInvocations(auditoria);
            servicio.editar(web.getId(), datos("Web corporativa pro", DESCRIPCION, GALERIA, ALT, true));
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Al editar, el propio nombre no cuenta como repetido; el de otro servicio sí (409); uno inexistente es 404")
        void nombreAlEditar() {
            when(servicios.existsByNombreIgnoreCaseAndIdNot("Web corporativa", web.getId())).thenReturn(false);
            servicio.editar(web.getId(), validos("Web corporativa"));

            when(servicios.existsByNombreIgnoreCaseAndIdNot("App móvil", web.getId())).thenReturn(true);
            assertThatThrownBy(() -> servicio.editar(web.getId(), validos("App móvil"))).isInstanceOf(ConflictoException.class);
            assertThatThrownBy(() -> servicio.editar(UUID.randomUUID(), validos("Cualquiera"))).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioCatalogo.NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Ocultar o mostrar cambia la visibilidad y lo audita; pedir el estado que ya tiene no hace nada (RN-21)")
        void visibilidad() {
            servicio.cambiarVisibilidad(web.getId(), false);
            assertThat(web.isVisible()).isFalse();
            verify(auditoria).registrar(eq(CategoriaAuditoria.SITIO), eq("Ocultó un servicio del catálogo"), eq("servicio"), eq("web-corporativa"), anyMap());

            servicio.cambiarVisibilidad(movil.getId(), true);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SITIO), eq("Mostró un servicio en el catálogo"), eq("servicio"), eq("app-movil"), anyMap());

            org.mockito.Mockito.clearInvocations(auditoria);
            servicio.cambiarVisibilidad(tienda.getId(), true);
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Subir un lugar intercambia el orden con el vecino y lo audita; la dirección solo puede ser -1 o 1 (422)")
        void mueve() {
            servicio.mover(web.getId(), -1);

            assertThat(web.getOrden()).isEqualTo((short) 1);
            assertThat(tienda.getOrden()).isEqualTo((short) 2);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SITIO), eq("Cambió el orden de los servicios"), eq("servicio"), eq("web-corporativa"), anyMap());

            for (int mala : new int[] { 0, 2, -2 }) {
                assertThatThrownBy(() -> servicio.mover(web.getId(), mala)).as("dirección " + mala)
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(campos(e)).containsExactly("direccion"));
            }
        }

        @Test
        @DisplayName("El primero no sube y el último no baja: 409 con su mensaje; uno inexistente es 404")
        void extremos() {
            assertThatThrownBy(() -> servicio.mover(tienda.getId(), -1)).isInstanceOf(ConflictoException.class).hasMessage("Este servicio ya es el primero del carrusel");
            assertThatThrownBy(() -> servicio.mover(movil.getId(), 1)).isInstanceOf(ConflictoException.class).hasMessage("Este servicio ya es el último del carrusel");
            assertThatThrownBy(() -> servicio.mover(UUID.randomUUID(), 1)).isInstanceOf(NoEncontradoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ eliminar

    @Nested
    @DisplayName("Eliminar un servicio (RN-21)")
    class Eliminar {

        @Test
        @DisplayName("Un servicio sin citas ni proyectos se elimina con su plantilla de fases, los de abajo suben un lugar y queda en la auditoría")
        void elimina() {
            servicio.eliminar(web.getId());

            verify(planes).eliminarPlantilla(web.getId());
            verify(servicios).delete(web);
            assertThat(movil.getOrden()).as("el de abajo sube de 3 a 2").isEqualTo((short) 2);
            assertThat(tienda.getOrden()).isEqualTo((short) 1);
            verify(auditoria).registrar(eq(CategoriaAuditoria.SITIO), eq("Eliminó un servicio del catálogo"), eq("servicio"), eq("web-corporativa"), anyMap());
        }

        @Test
        @DisplayName("Un servicio con citas o con proyectos no se elimina (409): solo se oculta")
        void enUso() {
            when(citas.tieneCitas(web.getId())).thenReturn(true);
            assertThatThrownBy(() -> servicio.eliminar(web.getId())).isInstanceOf(ConflictoException.class).hasMessage(ServicioCatalogo.EN_USO);

            when(citas.tieneCitas(web.getId())).thenReturn(false);
            when(planes.tieneProyectos(web.getId())).thenReturn(true);
            assertThatThrownBy(() -> servicio.eliminar(web.getId())).isInstanceOf(ConflictoException.class);

            verify(servicios, never()).delete(any(Servicio.class));
            verify(planes, never()).eliminarPlantilla(any());
        }

        @Test
        @DisplayName("Un servicio que no existe es 404")
        void inexistente() {
            assertThatThrownBy(() -> servicio.eliminar(UUID.randomUUID())).isInstanceOf(NoEncontradoException.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ imágenes

    @Nested
    @DisplayName("Imágenes de los servicios")
    class Imagenes {

        static byte[] png() {
            return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2 };
        }

        static byte[] webp() {
            return new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1 };
        }

        @Test
        @DisplayName("Una imagen WebP, PNG o JPG se guarda con la extensión que da su contenido y devuelve la URL con la que se muestra")
        void sube() {
            when(almacen.guardar("servicios", png(), "png")).thenReturn("3f2a-c1.png");

            String url = servicio.subirImagen(png());

            assertThat(url).isEqualTo(ImagenesServicio.PREFIJO_SUBIDA + "3f2a-c1.png");
            verify(auditoria).registrar(CategoriaAuditoria.SITIO, "Subió una imagen para un servicio", "imagen_servicio", "3f2a-c1");
            when(almacen.guardar(eq("servicios"), any(), eq("webp"))).thenReturn("9b9b.webp");
            assertThat(servicio.subirImagen(webp())).endsWith("9b9b.webp");
        }

        @Test
        @DisplayName("Vacía, nula o con un contenido que no es WebP, PNG ni JPG: 422 «Sube una imagen en formato…»; un .exe renombrado no pasa")
        void formatoInvalido() {
            for (byte[] mala : new byte[][] { null, new byte[0], { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0 }, "<svg></svg>".getBytes() }) {
                assertThatThrownBy(() -> servicio.subirImagen(mala))
                        .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo(ServicioCatalogo.IMAGEN_FORMATO));
            }
            verify(almacen, never()).guardar(any(), any(), any());
        }

        @Test
        @DisplayName("Valor límite del peso: 2 MB exactos pasan; 2 MB + 1 byte da «La imagen pesa más de 2 MB»")
        void limiteDelPeso() {
            byte[] justa = new byte[ImagenesServicio.MAX_BYTES];
            System.arraycopy(png(), 0, justa, 0, 8);
            byte[] pasada = new byte[ImagenesServicio.MAX_BYTES + 1];
            System.arraycopy(png(), 0, pasada, 0, 8);
            when(almacen.guardar(any(), any(), any())).thenReturn("a1.png");

            assertThat(servicio.subirImagen(justa)).isNotBlank();
            assertThatThrownBy(() -> servicio.subirImagen(pasada))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo(ServicioCatalogo.IMAGEN_PESADA));
        }

        @Test
        @DisplayName("Conservación de datos (RNF-09): borra las imágenes sin uso con más de un día y deja las que algún servicio usa y las recientes")
        void limpiaLasHuerfanas() {
            Servicio conImagenPropia = servicioDe("Con imagen propia", "con-imagen-propia", 4, true);
            conImagenPropia.editar("Con imagen propia", DESCRIPCION, ImagenesServicio.PREFIJO_SUBIDA + "enuso.png", ALT, true, AHORA);
            lista.add(conImagenPropia);
            when(almacen.listar("servicios")).thenReturn(List.of(
                    new AlmacenArchivos.Guardado("enuso.png", AHORA.minusSeconds(86400 * 30L)),
                    new AlmacenArchivos.Guardado("huerfana-vieja.png", AHORA.minusSeconds(86400 * 3L)),
                    new AlmacenArchivos.Guardado("huerfana-reciente.png", AHORA.minusSeconds(3600)),
                    new AlmacenArchivos.Guardado("huerfana-justo.png", AHORA.minusSeconds(86400))));
            when(almacen.borrar("servicios", "huerfana-vieja.png")).thenReturn(true);
            when(almacen.borrar("servicios", "huerfana-justo.png")).thenReturn(true);

            int borradas = servicio.limpiarImagenesSinUso();

            assertThat(borradas).isEqualTo(1);
            verify(almacen).borrar("servicios", "huerfana-vieja.png");
            verify(almacen, never()).borrar("servicios", "enuso.png");
            verify(almacen, never()).borrar("servicios", "huerfana-reciente.png");
            verify(almacen, never()).borrar("servicios", "huerfana-justo.png");
        }

        @Test
        @DisplayName("imagen(nombre): la lee del almacén según su extensión; una extensión desconocida da vacío")
        void leeLaImagen() {
            when(almacen.leer("servicios", "a1.webp", "image/webp")).thenReturn(Optional.of(new AlmacenArchivos.Archivo(webp(), "image/webp")));

            assertThat(servicio.imagen("a1.webp")).isPresent();
            assertThat(servicio.imagen("a1.gif")).isEmpty();
            assertThat(servicio.imagen("a1.png")).isEmpty();
        }
    }
}
