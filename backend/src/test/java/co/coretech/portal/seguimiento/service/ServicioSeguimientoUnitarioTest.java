package co.coretech.portal.seguimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.context.ApplicationEventPublisher;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.service.AlmacenArchivos;
import co.coretech.portal.compartido.exception.ConflictoException;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.exception.ProhibidoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.service.UsuarioSesion;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.Integrante;
import co.coretech.portal.especialidad.EquipoConsultas.ProductOwner;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento.Proyecto;
import co.coretech.portal.seguimiento.service.ServicioSeguimiento.ArchivoSubido;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.AdjuntoContexto;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.AdjuntoFila;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.CambioPedido;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseFila;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.PasoContexto;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.PasoFila;

/**
 * Pruebas UNITARIAS de {@link ServicioSeguimiento} (HU-03 y HU-04; RN-07, RN-09, RN-10, RN-16): consultar el proyecto, marcar
 * un paso como hecho con su nota, adjuntar archivos y descargarlos. El acceso a datos, el almacén de archivos, el equipo y la
 * auditoría van simulados; no hay base de datos. Reloj fijo: martes 2027-03-02.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioSeguimientoUnitarioTest {

    static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    static final Instant AHORA = Instant.parse("2027-03-02T15:00:00Z");
    static final LocalDate HOY = LocalDate.of(2027, 3, 2);

    static final UUID PROYECTO = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID OTRO_CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID EQUIPO = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    static final UUID PO = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID OTRO_PO = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    static final UUID FASE = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    static final UUID PASO = UUID.fromString("00000000-0000-4000-8000-000000000051");
    static final UUID ADJUNTO = UUID.fromString("00000000-0000-4000-8000-000000000091");
    static final String NOTA = "Se dejó listo el módulo de inventario y se probó con datos reales.";

    @Mock
    SeguimientoJdbc datos;
    @Mock
    ServicioFases fases;
    @Mock
    ProyectosParaSeguimiento proyectos;
    @Mock
    EquipoConsultas equipos;
    @Mock
    DirectorioUsuarios usuarios;
    @Mock
    AlmacenArchivos almacen;
    @Mock
    RegistroAuditoria auditoria;
    @Mock
    SesionActual sesion;
    @Mock
    ApplicationEventPublisher eventos;

    ServicioSeguimiento servicio;
    Proyecto proyecto;
    PasoContexto paso;

    @BeforeEach
    void preparaLoComun() {
        proyecto = proyecto("ACTIVO", "APROBADO");
        paso = paso("PASO", "PENDIENTE", "EN_CURSO");
        when(proyectos.porCodigo("P-0031")).thenAnswer(i -> Optional.of(proyecto));
        when(proyectos.porId(PROYECTO)).thenAnswer(i -> Optional.of(proyecto));
        when(datos.paso(PASO)).thenAnswer(i -> Optional.of(paso));
        when(datos.marcarHecho(any(), any(), any(), any())).thenReturn(true);
        when(datos.conteo(FASE)).thenReturn(new int[] { 4, 5 });
        when(datos.fases(PROYECTO)).thenReturn(List.of());
        when(datos.pasos(PROYECTO)).thenReturn(List.of());
        when(datos.adjuntos(PROYECTO)).thenReturn(List.of());
        when(datos.cambiosPedidos(PROYECTO)).thenReturn(List.of());
        when(equipos.equipoDelProductOwner(PO)).thenReturn(Optional.of(EQUIPO));
        when(equipos.equipoDelProductOwner(OTRO_PO)).thenReturn(Optional.of(UUID.randomUUID()));
        when(equipos.productOwner(EQUIPO)).thenReturn(Optional.of(new ProductOwner(PO, "Andrés Pérez", "andres@coretechsolutions.co")));
        when(equipos.integrantes(EQUIPO)).thenReturn(List.of(new Integrante("Camila Ruiz", "UX_UI"), new Integrante("Mateo Gil", "QA")));
        when(usuarios.perfiles(List.of(CLIENTE))).thenReturn(Map.of(CLIENTE, new ContactoCorreo("Laura Gómez", "laura@correo.com")));
        when(almacen.guardar(eq("adjuntos"), any(), any())).thenAnswer(i -> UUID.randomUUID() + "." + i.getArgument(2));
        when(datos.insertarAdjunto(any(), any(), any(), any(), anyInt())).thenAnswer(i -> UUID.randomUUID());
        comoProductOwner();
        servicio = new ServicioSeguimiento(datos, fases, proyectos, equipos, usuarios, almacen, auditoria, sesion, eventos, Clock.fixed(AHORA, COLOMBIA));
    }

    // ---- ayudas ----

    static Proyecto proyecto(String estado, String estadoPlan) {
        return new Proyecto(PROYECTO, "P-0031", "Inventario del salón", CLIENTE, EQUIPO, "Sistema de inventario", "C-0045", estado, estadoPlan,
                new BigDecimal("25.00"), HOY.minusDays(30), HOY.plusDays(40), null);
    }

    static PasoContexto paso(String tipo, String estado, String faseEstado) {
        return new PasoContexto(PASO, tipo, "Armar el inventario", estado, FASE, 2, "Construcción", faseEstado, PROYECTO);
    }

    void comoProductOwner() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(PO, "Andrés", "andres@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance"), false, 0));
    }

    void comoAdministrador() {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(ADMIN, "Patricia", "patricia@coretechsolutions.co", "ADMINISTRADOR", Set.of("proyectos.ver_todos"), false, 0));
    }

    void comoCliente(UUID id) {
        when(sesion.usuario()).thenReturn(new UsuarioSesion(id, "Laura", "laura@correo.com", "CLIENTE", Set.of("proyectos.ver_propios"), false, 0));
    }

    List<AvisoSolicitado> avisos() {
        ArgumentCaptor<AvisoSolicitado> c = ArgumentCaptor.forClass(AvisoSolicitado.class);
        verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(c.capture());
        return c.getAllValues();
    }

    static byte[] png() {
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
    }

    static byte[] pdf() {
        return "%PDF-1.7 contenido".getBytes();
    }

    // ------------------------------------------------------------------------------------------------ detalle

    @Nested
    @DisplayName("Consultar el proyecto (HU-04)")
    class Detalle {

        FaseFila fase(UUID id, int orden, String nombre, String estado) {
            return new FaseFila(id, orden, nombre, null, true, estado, AHORA.minusSeconds(86400), null, null, 1, false, HOY.plusDays(2));
        }

        @Test
        @DisplayName("El cliente dueño ve su proyecto con la fase actual, el porcentaje que vale cada fase, los pasos hechos y el equipo con palabras sencillas")
        void clienteDueno() {
            comoCliente(CLIENTE);
            UUID f2 = UUID.randomUUID();
            when(datos.fases(PROYECTO)).thenReturn(List.of(fase(FASE, 1, "Diseño", "APROBADA"), fase(f2, 2, "Construcción", "EN_CURSO"),
                    fase(UUID.randomUUID(), 3, "Entrega", "PENDIENTE"), fase(UUID.randomUUID(), 4, "Cierre", "PENDIENTE")));
            when(datos.pasos(PROYECTO)).thenReturn(List.of(
                    new PasoFila(UUID.randomUUID(), FASE, 1, "PASO", "Reunir requisitos", null, null, "HECHO", NOTA, AHORA.minusSeconds(100), AHORA),
                    new PasoFila(UUID.randomUUID(), f2, 1, "PASO", "Armar el inventario", null, null, "HECHO", NOTA, AHORA.minusSeconds(50), AHORA),
                    new PasoFila(UUID.randomUUID(), f2, 2, "PASO", "Probar", null, null, "PENDIENTE", null, null, AHORA)));

            var d = servicio.detalle("P-0031");

            assertThat(d.codigo()).isEqualTo("P-0031");
            assertThat(d.cliente()).isEqualTo("Laura Gómez");
            assertThat(d.productOwner()).isEqualTo("Andrés Pérez");
            assertThat(d.faseActual().nombre()).as("la fase en curso, no la aprobada").isEqualTo("Construcción");
            assertThat(d.fases()).hasSize(4);
            assertThat(d.fases().get(0).porcentaje()).as("100 / 4 fases").isEqualByComparingTo("25.0");
            assertThat(d.fases().get(1).pasosHechos()).isEqualTo(1);
            assertThat(d.fases().get(1).pasosTotal()).isEqualTo(2);
            assertThat(d.pasosHechos()).isEqualTo(2);
            assertThat(d.ultimoPaso().titulo()).as("el paso hecho más reciente").isEqualTo("Armar el inventario");
            assertThat(d.equipo()).extracting(ServicioSeguimiento.Persona::rol).containsExactly("Diseño de pantallas", "Pruebas");
            assertThat(d.puedeRegistrar()).isFalse();
        }

        @Test
        @DisplayName("Mientras el plan está en borrador el cliente todavía no ve fases ni pasos: el equipo lo está preparando")
        void borradorOcultoAlCliente() {
            comoCliente(CLIENTE);
            proyecto = proyecto("ACTIVO", "BORRADOR");

            var d = servicio.detalle("P-0031");

            assertThat(d.fases()).isEmpty();
            verify(datos, never()).fases(any());
        }

        @Test
        @DisplayName("Un cliente ajeno recibe 404 (RN-10) y el intento queda en la auditoría; no se revela que el proyecto existe")
        void clienteAjeno() {
            comoCliente(OTRO_CLIENTE);

            assertThatThrownBy(() -> servicio.detalle("P-0031")).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioSeguimiento.NO_ENCONTRADO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), any(), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("El Product Owner del equipo ve el proyecto y puede registrar solo con el proyecto activo o en garantía")
        void productOwnerDelEquipo() {
            assertThat(servicio.detalle("P-0031").puedeRegistrar()).isTrue();

            proyecto = proyecto("EN_GARANTIA", "APROBADO");
            assertThat(servicio.detalle("P-0031").puedeRegistrar()).isTrue();

            proyecto = proyecto("PAUSADO", "APROBADO");
            assertThat(servicio.detalle("P-0031").puedeRegistrar()).isFalse();
        }

        @Test
        @DisplayName("El Product Owner de otro equipo recibe 403 y queda en la auditoría")
        void otroEquipo() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance"), false, 0));

            assertThatThrownBy(() -> servicio.detalle("P-0031")).isInstanceOf(ProhibidoException.class).hasMessage(ServicioSeguimiento.NO_ES_DE_TU_EQUIPO);
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), any(), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("El administrador (ver_todos) ve cualquier proyecto, también un borrador, y no puede registrar")
        void administrador() {
            comoAdministrador();
            proyecto = proyecto("ACTIVO", "BORRADOR");

            var d = servicio.detalle("P-0031");

            assertThat(d.puedeRegistrar()).isFalse();
            verify(datos).fases(PROYECTO);
        }

        @Test
        @DisplayName("Un código mal formado, o que no existe, es 404")
        void codigoInvalido() {
            for (String malo : new String[] { null, "", "31", "P-12", "p-0031" }) {
                assertThatThrownBy(() -> servicio.detalle(malo)).as("«" + malo + "»").isInstanceOf(NoEncontradoException.class);
            }
            when(proyectos.porCodigo("P-9999")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.detalle("P-9999")).isInstanceOf(NoEncontradoException.class);
        }

        @Test
        @DisplayName("Los cambios que pidió el cliente cuelgan de su fase, y un adjunto eliminado no aparece")
        void cambiosYAdjuntos() {
            comoCliente(CLIENTE);
            UUID pasoHecho = UUID.randomUUID();
            when(datos.fases(PROYECTO)).thenReturn(List.of(fase(FASE, 1, "Diseño", "EN_REVISION")));
            when(datos.pasos(PROYECTO)).thenReturn(List.of(new PasoFila(pasoHecho, FASE, 1, "PASO", "Bocetos", null, null, "HECHO", NOTA, AHORA, AHORA)));
            when(datos.cambiosPedidos(PROYECTO)).thenReturn(List.of(new CambioPedido(FASE, "AJUSTE", "Quiero el logo más grande.", "ABIERTO", AHORA)));
            when(datos.adjuntos(PROYECTO)).thenReturn(List.of(new AdjuntoFila(ADJUNTO, pasoHecho, "Boceto", "a.png", "image/png", 100, false),
                    new AdjuntoFila(UUID.randomUUID(), pasoHecho, "Borrado", "b.png", "image/png", 100, true)));

            var f = servicio.detalle("P-0031").fases().get(0);

            assertThat(f.cambios()).extracting(ServicioSeguimiento.CambioVista::descripcion).containsExactly("Quiero el logo más grande.");
            assertThat(f.pasos().get(0).adjuntos()).extracting(ServicioSeguimiento.AdjuntoVista::titulo).containsExactly("Boceto");
            assertThat(f.pasos().get(0).adjuntos().get(0).url()).isEqualTo("/api/v1/adjuntos/" + ADJUNTO);
            assertThat(f.plazoAprobacion()).as("el plazo solo se muestra con la fase en revisión").isEqualTo(HOY.plusDays(2));
        }

        @Test
        @DisplayName("rolVisible: cada especialidad se dice con palabras sencillas; una desconocida es «Equipo»")
        void rolesVisibles() {
            assertThat(ServicioSeguimiento.rolVisible(new Integrante("A", "FRONTEND"))).isEqualTo("Desarrollo · lo que ves");
            assertThat(ServicioSeguimiento.rolVisible(new Integrante("A", "BACKEND"))).isEqualTo("Desarrollo · datos y reglas");
            assertThat(ServicioSeguimiento.rolVisible(new Integrante("A", "FULL_STACK"))).isEqualTo("Desarrollo completo");
            assertThat(ServicioSeguimiento.rolVisible(new Integrante("A", "MOVIL"))).isEqualTo("Desarrollo móvil");
            assertThat(ServicioSeguimiento.rolVisible(new Integrante("A", "OTRO"))).isEqualTo("Equipo");
        }
    }

    // ------------------------------------------------------------------------------------------------ marcar hecho

    @Nested
    @DisplayName("Marcar un paso como hecho (HU-03; RN-07, RN-09, RN-16)")
    class MarcarHecho {

        @Test
        @DisplayName("Marca el paso con su nota, autor y hora; audita, avisa al cliente con el nombre del Product Owner y devuelve «4 de 5»")
        void marca() {
            var r = servicio.marcarHecho(PASO, "HECHO", "  " + NOTA + "  ");

            assertThat(r.pasosHechos()).isEqualTo("4 de 5");
            assertThat(r.hechoAt()).isEqualTo(AHORA);
            verify(datos).marcarHecho(PASO, NOTA, PO, AHORA);
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), eq("Marcó un paso como hecho"), eq("proyecto"), eq("P-0031"), anyMap());
            verify(fases).alTerminarPaso(proyecto, paso);
            AvisoSolicitado aviso = avisos().get(0);
            assertThat(aviso.destinatarios()).containsExactly(CLIENTE);
            assertThat(aviso.tipo()).isEqualTo("PASO_HECHO");
            assertThat(aviso.texto()).startsWith("Andrés Pérez terminó «Armar el inventario»");
            assertThat(aviso.enlace()).isEqualTo("/cliente/proyectos/P-0031");
        }

        @Test
        @DisplayName("Valores límite de la nota de un paso del plan (RN-09): 19 caracteres no, 20 sí, 400 sí, 401 no")
        void limitesDeLaNotaDeUnPaso() {
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", "x".repeat(19)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe al menos 20 caracteres."));
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", "x".repeat(401)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 400 caracteres."));

            servicio.marcarHecho(PASO, "HECHO", "x".repeat(20));
            servicio.marcarHecho(PASO, "HECHO", "x".repeat(400));
            verify(datos, times(2)).marcarHecho(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Una corrección o un ajuste admite hasta 500 caracteres en su nota (RN-09)")
        void limiteDeUnaCorreccion() {
            paso = paso("CORRECCION", "PENDIENTE", "EN_CURSO");

            servicio.marcarHecho(PASO, "HECHO", "x".repeat(500));
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", "x".repeat(501)))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe máximo 500 caracteres."));
        }

        @Test
        @DisplayName("Sin nota o con una nota en blanco: 422 que nombra al cliente por su primer nombre; un estado distinto de HECHO también da 422")
        void notaVaciaYEstado() {
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", "   "))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Escribe la nota para que Laura sepa qué cambió."));
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", null)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "PENDIENTE", NOTA))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).campo()).isEqualTo("estado"));
            verify(datos, never()).marcarHecho(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Solo el Product Owner del equipo registra (RN-07): el de otro equipo recibe 403 y queda en la auditoría; un cliente también")
        void soloElProductOwnerDelEquipo() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance"), false, 0));
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).isInstanceOf(ProhibidoException.class).hasMessage(ServicioSeguimiento.SOLO_EL_PRODUCT_OWNER);

            comoCliente(CLIENTE);
            when(equipos.equipoDelProductOwner(CLIENTE)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).isInstanceOf(ProhibidoException.class);

            verify(auditoria, times(2)).registrar(eq(CategoriaAuditoria.AVANCES), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(datos, never()).marcarHecho(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Un paso que no existe es 404")
        void pasoInexistente() {
            when(datos.paso(PASO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).isInstanceOf(NoEncontradoException.class).hasMessage(ServicioSeguimiento.PASO_NO_ENCONTRADO);
        }

        @Test
        @DisplayName("Con el plan sin aprobar o el proyecto pausado, finalizado o cancelado no se registra (409)")
        void estadoDelProyecto() {
            for (Proyecto p : List.of(proyecto("ACTIVO", "ENVIADO"), proyecto("PAUSADO", "APROBADO"), proyecto("FINALIZADO", "APROBADO"), proyecto("CANCELADO", "APROBADO"))) {
                proyecto = p;
                assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).as(p.estado() + "/" + p.estadoPlan())
                        .isInstanceOf(ConflictoException.class).hasMessage(ServicioSeguimiento.ESTADO_NO_ADMITE);
            }
        }

        @Test
        @DisplayName("En garantía solo se registran correcciones (RN-16): un paso del plan da 409; una corrección sí pasa")
        void garantia() {
            proyecto = proyecto("EN_GARANTIA", "APROBADO");
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).hasMessage("En garantía solo se registran correcciones");

            paso = paso("CORRECCION", "PENDIENTE", "APROBADA");
            assertThat(servicio.marcarHecho(PASO, "HECHO", NOTA).pasoId()).isEqualTo(PASO);
        }

        @Test
        @DisplayName("Un paso ya hecho, o de una fase que no es la actual, da 409; una corrección sí se cierra en una fase aprobada o en corrección")
        void pasoDeLaFaseActual() {
            paso = paso("PASO", "HECHO", "EN_CURSO");
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).hasMessage(ServicioSeguimiento.PASO_YA_REGISTRADO);

            paso = paso("PASO", "PENDIENTE", "PENDIENTE");
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).hasMessage(ServicioSeguimiento.PASO_YA_REGISTRADO);

            paso = paso("PASO", "PENDIENTE", "APROBADA");
            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).hasMessage(ServicioSeguimiento.PASO_YA_REGISTRADO);

            for (String faseEstado : List.of("APROBADA", "EN_CORRECCION")) {
                paso = paso("CORRECCION", "PENDIENTE", faseEstado);
                assertThat(servicio.marcarHecho(PASO, "HECHO", NOTA).pasoId()).as(faseEstado).isEqualTo(PASO);
            }
        }

        @Test
        @DisplayName("Si otra pestaña se adelantó (el UPDATE no toca filas): 409 «El proyecto cambió» y no se audita ni se avisa")
        void otraPestaniaSeAdelanto() {
            when(datos.marcarHecho(any(), any(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> servicio.marcarHecho(PASO, "HECHO", NOTA)).hasMessage(ServicioSeguimiento.DATOS_DESACTUALIZADOS);
            verify(auditoria, never()).registrar(any(), any(), any(), any(), anyMap());
            verify(fases, never()).alTerminarPaso(any(), any());
        }
    }

    // ------------------------------------------------------------------------------------------------ adjuntar

    @Nested
    @DisplayName("Adjuntar archivos a un paso (HU-03; RNF-01)")
    class Adjuntar {

        @BeforeEach
        void pasoHecho() {
            paso = paso("PASO", "HECHO", "EN_CURSO");
            when(datos.contarAdjuntos(PASO)).thenReturn(0);
        }

        @Test
        @DisplayName("Un PNG y un PDF se guardan con el tipo que reconoce su contenido, el título sale del nombre y la respuesta lleva la URL de descarga")
        void adjunta() {
            var subidos = servicio.adjuntar(PASO, List.of(new ArchivoSubido("C:\\fotos\\boceto_final.png", png()), new ArchivoSubido("plan.pdf", pdf())));

            assertThat(subidos).extracting(ServicioSeguimiento.AdjuntoSubido::titulo).containsExactly("boceto final", "plan");
            assertThat(subidos).extracting(ServicioSeguimiento.AdjuntoSubido::tipoMime).containsExactly("image/png", "application/pdf");
            assertThat(subidos.get(0).url()).startsWith("/api/v1/adjuntos/");
            verify(almacen).guardar(eq("adjuntos"), any(), eq("png"));
            verify(almacen).guardar(eq("adjuntos"), any(), eq("pdf"));
            verify(auditoria).registrar(eq(CategoriaAuditoria.AVANCES), eq("Adjuntó archivos a un paso"), eq("proyecto"), eq("P-0031"), anyMap());
        }

        @Test
        @DisplayName("El tipo lo da el contenido, no el nombre: un ejecutable llamado boceto.png se rechaza y no se guarda nada")
        void unExeRenombrado() {
            byte[] exe = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0 };

            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("boceto.png", exe))))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("«boceto.png» no es un PNG, JPG, SVG o PDF válido."));
            verify(almacen, never()).guardar(any(), any(), any());
        }

        @Test
        @DisplayName("Se valida todo antes de guardar: si el segundo archivo falla, el primero tampoco se guarda")
        void todoONada() {
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("bueno.png", png()), new ArchivoSubido("malo.png", new byte[] { 'M', 'Z', 0, 0, 0, 0, 0, 0 }))))
                    .isInstanceOf(ValidacionException.class);

            verify(almacen, never()).guardar(any(), any(), any());
            verify(datos, never()).insertarAdjunto(any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Valores límite del tamaño: 5 MB exactos pasan; 5 MB + 1 byte da «pesa más de 5 MB»; un archivo vacío también se rechaza")
        void limiteDelTamano() {
            byte[] justo = new byte[ServicioSeguimiento.MAX_BYTES];
            System.arraycopy(png(), 0, justo, 0, 8);
            byte[] pasado = new byte[ServicioSeguimiento.MAX_BYTES + 1];
            System.arraycopy(png(), 0, pasado, 0, 8);

            assertThat(servicio.adjuntar(PASO, List.of(new ArchivoSubido("justo.png", justo)))).hasSize(1);
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("grande.png", pasado))))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("«grande.png» pesa más de 5 MB."));
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("vacio.png", new byte[0]))))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("«vacio.png» está vacío."));
        }

        @Test
        @DisplayName("Valores límite de la cantidad: con 3 ya subidos caben 2 más (5 en total) y 3 más no; sin ninguno, 6 no caben; una lista vacía o nula da 422")
        void limiteDeArchivos() {
            when(datos.contarAdjuntos(PASO)).thenReturn(3);
            assertThat(servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png()), new ArchivoSubido("b.png", png())))).hasSize(2);
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png()), new ArchivoSubido("b.png", png()), new ArchivoSubido("c.png", png()))))
                    .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Un paso admite máximo 5 archivos."));

            when(datos.contarAdjuntos(PASO)).thenReturn(0);
            List<ArchivoSubido> seis = java.util.stream.IntStream.range(0, 6).mapToObj(i -> new ArchivoSubido("f" + i + ".png", png())).toList();
            assertThatThrownBy(() -> servicio.adjuntar(PASO, seis)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of())).isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.fallas().get(0).mensaje()).isEqualTo("Elige al menos un archivo."));
            assertThatThrownBy(() -> servicio.adjuntar(PASO, null)).isInstanceOf(ValidacionException.class);
        }

        @Test
        @DisplayName("Solo a un paso que ya está hecho (409 «Primero marca el paso como hecho») y con el proyecto activo o en garantía")
        void estadoDelPasoYDelProyecto() {
            paso = paso("PASO", "PENDIENTE", "EN_CURSO");
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png())))).hasMessageContaining("Primero marca el paso como hecho");

            paso = paso("PASO", "HECHO", "EN_CURSO");
            proyecto = proyecto("PAUSADO", "APROBADO");
            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png())))).hasMessage(ServicioSeguimiento.ESTADO_NO_ADMITE);
            proyecto = proyecto("EN_GARANTIA", "APROBADO");
            assertThat(servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png())))).hasSize(1);
        }

        @Test
        @DisplayName("Solo el Product Owner del equipo adjunta: el de otro equipo recibe 403")
        void permisos() {
            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance"), false, 0));

            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png())))).isInstanceOf(ProhibidoException.class);
            verify(almacen, never()).guardar(any(), any(), any());
        }

        @Test
        @DisplayName("Si falla al guardar el segundo archivo, el primero se borra del almacén: no quedan archivos huérfanos")
        void sinHuerfanos() {
            when(almacen.guardar(eq("adjuntos"), any(), eq("png"))).thenReturn("uno.png").thenThrow(new IllegalStateException("disco lleno"));

            assertThatThrownBy(() -> servicio.adjuntar(PASO, List.of(new ArchivoSubido("a.png", png()), new ArchivoSubido("b.png", png()))))
                    .isInstanceOf(IllegalStateException.class);

            verify(almacen).borrar("adjuntos", "uno.png");
        }

        @Test
        @DisplayName("titulo: sin ruta ni extensión, los guiones bajos como espacios, «Archivo adjunto» si queda vacío y 80 caracteres como máximo")
        void titulos() {
            assertThat(ServicioSeguimiento.titulo("C:\\fotos\\boceto_final_v2.png")).isEqualTo("boceto final v2");
            assertThat(ServicioSeguimiento.titulo("/home/laura/logo.final.svg")).isEqualTo("logo.final");
            assertThat(ServicioSeguimiento.titulo(".png")).as("solo extensión").isEqualTo(".png");
            assertThat(ServicioSeguimiento.titulo(null)).isEqualTo("archivo");
            assertThat(ServicioSeguimiento.titulo("   ")).isEqualTo("archivo");
            assertThat(ServicioSeguimiento.titulo("___.png")).isEqualTo("Archivo adjunto");
            assertThat(ServicioSeguimiento.titulo("x".repeat(120) + ".png")).hasSize(80);
        }
    }

    // ------------------------------------------------------------------------------------------------ descargar

    @Nested
    @DisplayName("Descargar un adjunto (control de acceso igual al del detalle)")
    class Descargar {

        @BeforeEach
        void adjuntoGuardado() {
            when(datos.adjunto(ADJUNTO)).thenReturn(Optional.of(new AdjuntoContexto(new AdjuntoFila(ADJUNTO, PASO, "Boceto", "a.png", "image/png", 10, false), PROYECTO)));
            when(almacen.leer("adjuntos", "a.png", "image/png")).thenReturn(Optional.of(new AlmacenArchivos.Archivo(png(), "image/png")));
        }

        @Test
        @DisplayName("El cliente dueño, el Product Owner del equipo y el administrador descargan")
        void tienenAcceso() {
            comoCliente(CLIENTE);
            assertThat(servicio.descargar(ADJUNTO).titulo()).isEqualTo("Boceto");

            comoProductOwner();
            assertThat(servicio.descargar(ADJUNTO).tipoMime()).isEqualTo("image/png");

            comoAdministrador();
            assertThat(servicio.descargar(ADJUNTO).archivo().contenido()).isNotEmpty();
        }

        @Test
        @DisplayName("Un cliente ajeno y un Product Owner de otro equipo reciben 404 (no se revela que existe) y queda en la auditoría")
        void sinAcceso() {
            comoCliente(OTRO_CLIENTE);
            assertThatThrownBy(() -> servicio.descargar(ADJUNTO)).isInstanceOf(NoEncontradoException.class).hasMessage("Archivo no encontrado");

            when(sesion.usuario()).thenReturn(new UsuarioSesion(OTRO_PO, "Camila", "camila@coretechsolutions.co", "PRODUCT_OWNER", Set.of("proyectos.registrar_avance"), false, 0));
            assertThatThrownBy(() -> servicio.descargar(ADJUNTO)).isInstanceOf(NoEncontradoException.class);

            verify(auditoria, times(2)).registrar(eq(CategoriaAuditoria.AVANCES), any(), eq("proyecto"), eq("P-0031"), anyMap());
            verify(almacen, never()).leer(any(), any(), any());
        }

        @Test
        @DisplayName("Un adjunto que no existe, uno eliminado (RNF-09) o cuyo archivo ya no está en el almacén dan 404")
        void noDisponible() {
            when(datos.adjunto(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.descargar(UUID.randomUUID())).isInstanceOf(NoEncontradoException.class);

            when(datos.adjunto(ADJUNTO)).thenReturn(Optional.of(new AdjuntoContexto(new AdjuntoFila(ADJUNTO, PASO, "Boceto", "a.png", "image/png", 10, true), PROYECTO)));
            comoProductOwner();
            assertThatThrownBy(() -> servicio.descargar(ADJUNTO)).hasMessage("Este archivo ya no está disponible");

            when(datos.adjunto(ADJUNTO)).thenReturn(Optional.of(new AdjuntoContexto(new AdjuntoFila(ADJUNTO, PASO, "Boceto", "a.png", "image/png", 10, false), PROYECTO)));
            when(almacen.leer("adjuntos", "a.png", "image/png")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.descargar(ADJUNTO)).hasMessage("Este archivo ya no está disponible");
        }
    }
}
