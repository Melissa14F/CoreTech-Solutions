package co.coretech.portal.proyectos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.proyectos.repository.PlantillasJdbc;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;

/**
 * Pruebas UNITARIAS de {@link ServicioPlantillas} (HU-27; RN-27 y RN-28): de 2 a 8 fases con nombres distintos, de 1 a 12 pasos
 * por fase, al menos una fase que apruebe el cliente y los límites de texto, con un error por campo. La persistencia
 * ({@link PlantillasJdbc}) y el catálogo van simulados; no hay Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioPlantillasUnitarioTest {

    static final UUID SERVICIO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");

    @Mock
    PlantillasJdbc plantillas;
    @Mock
    DirectorioServicios servicios;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;

    @Captor
    ArgumentCaptor<Map<String, CambioDeValor>> cambios;

    ServicioPlantillas servicio;

    @BeforeEach
    void preparaLoComun() {
        when(servicios.nombres(List.of(SERVICIO))).thenReturn(Map.of(SERVICIO, "Sistema de inventario"));
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of(), false, 0));
        servicio = new ServicioPlantillas(plantillas, servicios, auditoria, sesion);
    }

    // ---- ayudas para armar plantillas ----

    static PasoPlantilla paso(String titulo) {
        return new PasoPlantilla(0, titulo, null);
    }

    static FasePlantilla fase(String nombre, boolean aprueba, String... pasos) {
        List<PasoPlantilla> lista = new ArrayList<>();
        for (String p : pasos) {
            lista.add(paso(p));
        }
        return new FasePlantilla(0, nombre, null, aprueba, lista);
    }

    /** Una plantilla válida de dos fases: la segunda la aprueba el cliente. */
    static List<FasePlantilla> valida() {
        return List.of(fase("Diseño", false, "Reunir requisitos"), fase("Entrega", true, "Capacitar al equipo"));
    }

    static ValidacionException falla(List<FasePlantilla> fases) {
        return (ValidacionException) org.junit.jupiter.api.Assertions.assertThrows(ValidacionException.class,
                () -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(fases), "este servicio"));
    }

    static List<String> campos(ValidacionException e) {
        return e.fallas().stream().map(ValidacionException.Falla::campo).toList();
    }

    @Nested
    @DisplayName("Número de fases (RN-28)")
    class NumeroDeFases {

        @Test
        @DisplayName("Valores límite: 2 y 8 fases pasan")
        void limitesValidos() {
            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(valida()), "este servicio")).doesNotThrowAnyException();
            List<FasePlantilla> ocho = IntStream.rangeClosed(1, 8).mapToObj(i -> fase("Fase " + i, i == 8, "Hacer algo " + i)).toList();
            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(ocho), "este servicio")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Una sola fase: «El plan necesita al menos 2 fases»")
        void unaSolaFase() {
            ValidacionException e = falla(List.of(fase("Entrega", true, "Capacitar")));
            assertThat(e.fallas()).extracting(ValidacionException.Falla::mensaje).contains("El plan necesita al menos 2 fases");
        }

        @Test
        @DisplayName("Nueve fases: «El plan puede tener máximo 8 fases»")
        void nueveFases() {
            List<FasePlantilla> nueve = IntStream.rangeClosed(1, 9).mapToObj(i -> fase("Fase " + i, true, "Hacer algo " + i)).toList();
            assertThat(falla(nueve).fallas()).extracting(ValidacionException.Falla::mensaje).contains("El plan puede tener máximo 8 fases");
        }

        @Test
        @DisplayName("Ninguna fase que apruebe el cliente: «Marca al menos una fase que apruebe el cliente»")
        void ningunaApruebaElCliente() {
            ValidacionException e = falla(List.of(fase("Diseño", false, "Reunir"), fase("Entrega", false, "Capacitar")));
            assertThat(e.fallas()).extracting(ValidacionException.Falla::mensaje).contains("Marca al menos una fase que apruebe el cliente");
        }

        @Test
        @DisplayName("Una lista nula se trata como vacía: pide al menos 2 fases y no lanza NullPointerException")
        void listaNula() {
            assertThat(falla(null).fallas()).extracting(ValidacionException.Falla::mensaje).contains("El plan necesita al menos 2 fases");
        }
    }

    @Nested
    @DisplayName("Nombres de las fases")
    class NombresDeFases {

        @Test
        @DisplayName("Valores límite: 3 caracteres pasa; 2 no; 60 pasa; 61 no")
        void largoDelNombre() {
            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(
                    List.of(fase("Uno", false, "Hacer algo"), fase("x".repeat(60), true, "Hacer algo"))), "este servicio")).doesNotThrowAnyException();

            assertThat(campos(falla(List.of(fase("Un", false, "Hacer algo"), fase("Entrega", true, "Hacer algo"))))).contains("fases[0].nombre");
            assertThat(campos(falla(List.of(fase("Diseño", false, "Hacer algo"), fase("x".repeat(61), true, "Hacer algo"))))).contains("fases[1].nombre");
        }

        @Test
        @DisplayName("Un nombre repetido (sin distinguir mayúsculas) marca la segunda fase y nombra el ámbito")
        void nombreRepetido() {
            ValidacionException e = falla(List.of(fase("Diseño", false, "Hacer algo"), fase("DISEÑO", true, "Hacer algo")));

            assertThat(e.fallas()).anySatisfy(f -> {
                assertThat(f.campo()).isEqualTo("fases[1].nombre");
                assertThat(f.mensaje()).isEqualTo("Ya hay una fase con ese nombre en este servicio");
            });
        }

        @Test
        @DisplayName("En un plan (no una plantilla) el mensaje de nombre repetido dice «el plan»")
        void nombreRepetidoEnUnPlan() {
            List<FasePlantilla> fases = ServicioPlantillas.normalizar(List.of(fase("Diseño", false, "Hacer algo"), fase("Diseño", true, "Hacer algo")));
            assertThatThrownBy(() -> ServicioPlantillas.validar(fases, "el plan")).hasMessageContaining("Ya hay una fase con ese nombre en el plan");
        }

        @Test
        @DisplayName("Los espacios sobrantes se recortan antes de contar: «  Di  » son 2 caracteres")
        void recortaAntesDeContar() {
            assertThat(campos(falla(List.of(fase("  Di  ", false, "Hacer algo"), fase("Entrega", true, "Hacer algo"))))).contains("fases[0].nombre");
        }

        @Test
        @DisplayName("«Qué es» de más de 140 caracteres se rechaza; 140 pasa")
        void queEs() {
            FasePlantilla larga = new FasePlantilla(0, "Diseño", "x".repeat(141), false, List.of(paso("Hacer algo")));
            FasePlantilla justa = new FasePlantilla(0, "Diseño", "x".repeat(140), false, List.of(paso("Hacer algo")));

            assertThat(campos(falla(List.of(larga, fase("Entrega", true, "Hacer algo"))))).contains("fases[0].queEs");
            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(List.of(justa, fase("Entrega", true, "Hacer algo"))), "este servicio"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Pasos (RN-28)")
    class Pasos {

        @Test
        @DisplayName("Una fase sin pasos: «Agrega al menos un paso.»")
        void sinPasos() {
            ValidacionException e = falla(List.of(fase("Diseño", false), fase("Entrega", true, "Hacer algo")));
            assertThat(e.fallas()).anySatisfy(f -> {
                assertThat(f.campo()).isEqualTo("fases[0].pasos");
                assertThat(f.mensaje()).isEqualTo("Agrega al menos un paso.");
            });
            assertThat(campos(e)).as("y marca el resumen de la fase incompleta").contains("fases");
        }

        @Test
        @DisplayName("Valores límite: 12 pasos pasan; 13 no")
        void maximoDe12() {
            String[] doce = IntStream.rangeClosed(1, 12).mapToObj(i -> "Hacer algo " + i).toArray(String[]::new);
            String[] trece = IntStream.rangeClosed(1, 13).mapToObj(i -> "Hacer algo " + i).toArray(String[]::new);

            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(List.of(fase("Diseño", false, doce), fase("Entrega", true, "Hacer algo"))), "este servicio"))
                    .doesNotThrowAnyException();
            assertThat(falla(List.of(fase("Diseño", false, trece), fase("Entrega", true, "Hacer algo"))).fallas())
                    .anySatisfy(f -> assertThat(f.mensaje()).isEqualTo("Cada fase puede tener máximo 12 pasos."));
        }

        @Test
        @DisplayName("Valores límite del título del paso: 3 pasa, 2 no, 80 pasa, 81 no")
        void tituloDelPaso() {
            assertThat(campos(falla(List.of(fase("Diseño", false, "Ab"), fase("Entrega", true, "Hacer algo"))))).contains("fases[0].pasos[0].titulo");
            assertThat(campos(falla(List.of(fase("Diseño", false, "x".repeat(81)), fase("Entrega", true, "Hacer algo"))))).contains("fases[0].pasos[0].titulo");
            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(
                    List.of(fase("Diseño", false, "Abc", "x".repeat(80)), fase("Entrega", true, "Hacer algo"))), "este servicio")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La explicación del paso admite hasta 160 caracteres; 161 se rechaza")
        void explicacionDelPaso() {
            FasePlantilla conLarga = new FasePlantilla(0, "Diseño", null, false, List.of(new PasoPlantilla(0, "Hacer algo", "x".repeat(161))));
            FasePlantilla conJusta = new FasePlantilla(0, "Diseño", null, false, List.of(new PasoPlantilla(0, "Hacer algo", "x".repeat(160))));

            assertThat(campos(falla(List.of(conLarga, fase("Entrega", true, "Hacer algo"))))).contains("fases[0].pasos[0].explicacion");
            assertThatCode(() -> ServicioPlantillas.validar(ServicioPlantillas.normalizar(List.of(conJusta, fase("Entrega", true, "Hacer algo"))), "este servicio"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Muestra solo el primer error de cada campo")
        void unErrorPorCampo() {
            ValidacionException e = falla(List.of(fase("A", false), fase("B", false)));
            assertThat(campos(e)).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("normalizar")
    class Normalizar {

        @Test
        @DisplayName("Recorta los textos, pasa los vacíos a nulo y reinicia el orden (lo pone el servidor)")
        void recortaYAnula() {
            FasePlantilla sucia = new FasePlantilla(7, "  Diseño  ", "   ", true, List.of(new PasoPlantilla(9, "  Reunir  ", "  ")));

            FasePlantilla limpia = ServicioPlantillas.normalizar(List.of(sucia)).get(0);

            assertThat(limpia.nombre()).isEqualTo("Diseño");
            assertThat(limpia.queEs()).isNull();
            assertThat(limpia.orden()).isZero();
            assertThat(limpia.apruebaCliente()).isTrue();
            assertThat(limpia.pasos().get(0).titulo()).isEqualTo("Reunir");
            assertThat(limpia.pasos().get(0).explicacion()).isNull();
            assertThat(limpia.pasos().get(0).orden()).isZero();
        }

        @Test
        @DisplayName("Tolera fases o pasos nulos sin lanzar NullPointerException")
        void toleraNulos() {
            List<FasePlantilla> pedidas = new ArrayList<>();
            pedidas.add(null);
            pedidas.add(new FasePlantilla(0, "Diseño", null, false, null));
            pedidas.add(new FasePlantilla(0, "Entrega", null, true, java.util.Arrays.asList(null, paso("Hacer algo"))));

            List<FasePlantilla> resultado = ServicioPlantillas.normalizar(pedidas);

            assertThat(resultado).hasSize(3);
            assertThat(resultado.get(0).nombre()).isEmpty();
            assertThat(resultado.get(1).pasos()).isEmpty();
            assertThat(resultado.get(2).pasos()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("guardar y obtener")
    class GuardarYObtener {

        @Test
        @DisplayName("Guarda con el id del administrador de la sesión y deja la auditoría con el antes y el después")
        void guardaYAudita() {
            when(plantillas.leer(SERVICIO)).thenReturn(List.of(fase("Vieja", true, "Un paso")));

            servicio.guardar(SERVICIO, valida());

            verify(plantillas).reemplazar(eq(SERVICIO), eq(ADMIN), any());
            verify(auditoria).registrar(eq(CategoriaAuditoria.PROYECTOS), any(), eq("servicio"), eq(SERVICIO.toString()), cambios.capture());
            assertThat(cambios.getValue()).containsKeys("servicio", "fases", "nombresFases", "pasos", "fasesQueApruebaElCliente");
        }

        @Test
        @DisplayName("Una plantilla inválida no toca la base ni la auditoría")
        void invalidaNoGuarda() {
            assertThatThrownBy(() -> servicio.guardar(SERVICIO, List.of(fase("Sola", true, "Hacer algo")))).isInstanceOf(ValidacionException.class);

            verify(plantillas, never()).reemplazar(any(), any(), any());
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Un servicio que no existe es 404 «Este servicio ya no está disponible», al obtener y al guardar")
        void servicioInexistente() {
            UUID otro = UUID.randomUUID();
            when(servicios.nombres(List.of(otro))).thenReturn(Map.of());

            assertThatThrownBy(() -> servicio.obtener(otro)).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioPlantillas.SERVICIO_NO_ENCONTRADO);
            assertThatThrownBy(() -> servicio.guardar(otro, valida())).isInstanceOf(NoEncontradoException.class);
            verify(plantillas, never()).reemplazar(any(), any(), any());
        }

        @Test
        @DisplayName("obtener devuelve lo que lee la base")
        void obtiene() {
            when(plantillas.leer(SERVICIO)).thenReturn(valida());

            assertThat(servicio.obtener(SERVICIO)).hasSize(2);
        }
    }
}
