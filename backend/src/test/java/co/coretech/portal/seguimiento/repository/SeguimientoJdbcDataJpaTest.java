package co.coretech.portal.seguimiento.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.CambioPedido;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseEnRevision;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseFila;

/**
 * Pruebas de PERSISTENCIA y CONSULTAS de {@link SeguimientoJdbc} contra PostgreSQL 15 real (Testcontainers + Flyway V1…V16):
 * el SQL de cada transición de estado de una fase (con su guarda {@code WHERE estado = …}, que es lo que evita que dos
 * acciones a la vez se pisen), los conteos de pasos y correcciones (HU-13, RN-16), las solicitudes de cambio y las consultas de
 * las tareas programadas (RN-17), y las restricciones y disparadores de la base que protegen los datos (RN-09).
 * <p>
 * No es H2: el esquema usa enums de PostgreSQL ({@code estado_fase}, {@code tipo_paso}), {@code count(*) FILTER} y disparadores.
 * Cada prueba corre en una transacción que se revierte; las llaves foráneas se difieren porque el proyecto, la fase y el
 * usuario no son lo que se prueba.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class SeguimientoJdbcDataJpaTest {

    static final Instant AHORA = Instant.parse("2031-03-04T15:00:00Z");
    static final String NOTA = "Se dejó listo el módulo y se probó con datos reales.";
    static final String EXPLICACION = "El reporte mostraba mal el total cuando había devoluciones.";

    @Autowired
    JdbcTemplate jdbc;

    SeguimientoJdbc datos;
    final UUID proyecto = UUID.randomUUID();
    final UUID autor = UUID.randomUUID();

    @BeforeEach
    void preparaLoComun() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        datos = new SeguimientoJdbc(jdbc);
    }

    // ---- ayudas ----

    UUID fase(int orden, String estado, boolean apruebaCliente) {
        return fase(proyecto, orden, estado, apruebaCliente);
    }

    UUID fase(UUID proyectoId, int orden, String estado, boolean apruebaCliente) {
        // aprobada_at: ck_pf_aprobada lo exige para APROBADA
        return jdbc.queryForObject("""
                INSERT INTO proyecto_fases (proyecto_id, orden, nombre, aprueba_cliente, estado, aprobada_at)
                VALUES (?, ?, ?, ?, ?::estado_fase, CASE WHEN ? = 'APROBADA' THEN now() END) RETURNING id
                """, UUID.class, proyectoId, orden, "Fase " + orden, apruebaCliente, estado, estado);
    }

    UUID paso(UUID faseId, int orden, String tipo, String estado) {
        boolean hecho = estado.equals("HECHO");
        return jdbc.queryForObject("""
                INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion, origen, estado, nota, hecho_at, registrado_por)
                VALUES (?, ?, ?::tipo_paso, ?, ?, ?, ?::estado_paso, ?, ?, ?) RETURNING id
                """, UUID.class, faseId, orden, tipo, "Paso " + orden, tipo.equals("PASO") ? null : EXPLICACION,
                tipo.equals("PASO") ? null : "La encontramos en pruebas", estado, hecho ? NOTA : null,
                hecho ? java.sql.Timestamp.from(AHORA) : null, hecho ? autor : null);
    }

    /**
     * PostgreSQL aborta la transacción entera al primer error: sin un punto de guardado, lo que se ejecute después del rechazo
     * esperado falla con «current transaction is aborted». Así cada rechazo se comprueba y la prueba puede seguir.
     */
    void rechaza(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String fragmentoDelMensaje) {
        jdbc.execute("SAVEPOINT antes_del_rechazo");
        try {
            assertThatThrownBy(accion).isInstanceOf(DataAccessException.class).hasMessageContaining(fragmentoDelMensaje);
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT antes_del_rechazo");
        }
    }

    String estadoDe(UUID faseId) {
        return jdbc.queryForObject("SELECT estado::text FROM proyecto_fases WHERE id = ?", String.class, faseId);
    }

    // ------------------------------------------------------------------------------------------------ lecturas

    @Nested
    @DisplayName("Lecturas")
    class Lecturas {

        @Test
        @DisplayName("fases: las del proyecto en orden y sin las QUITADA; no mezcla otros proyectos")
        void fasesEnOrdenSinQuitadas() {
            fase(3, "PENDIENTE", true);
            fase(1, "APROBADA", false);
            fase(2, "QUITADA", false);
            fase(UUID.randomUUID(), 1, "EN_CURSO", true);

            List<FaseFila> fases = datos.fases(proyecto);

            assertThat(fases).extracting(FaseFila::orden).containsExactly(1, 3);
            assertThat(fases).extracting(FaseFila::estado).containsExactly("APROBADA", "PENDIENTE");
        }

        @Test
        @DisplayName("faseConProyecto: devuelve la fase con su proyecto; un id que no existe da vacío")
        void faseConProyecto() {
            UUID id = fase(1, "EN_CURSO", true);

            var encontrada = datos.faseConProyecto(id);

            assertThat(encontrada).isPresent();
            assertThat(encontrada.get().proyectoId()).isEqualTo(proyecto);
            assertThat(encontrada.get().fase().apruebaCliente()).isTrue();
            assertThat(datos.faseConProyecto(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("siguientePendiente: la PENDIENTE de menor orden; vacío si no queda ninguna (RN-08)")
        void siguientePendiente() {
            fase(1, "APROBADA", false);
            fase(2, "EN_CURSO", true);
            UUID tercera = fase(3, "PENDIENTE", true);
            fase(4, "PENDIENTE", true);

            assertThat(datos.siguientePendiente(proyecto)).get().extracting(FaseFila::id).isEqualTo(tercera);
            assertThat(datos.siguientePendiente(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("fasesSinAprobar: cuenta todo lo que no esté APROBADA ni QUITADA (una fase en corrección sí cuenta)")
        void fasesSinAprobar() {
            fase(1, "APROBADA", false);
            fase(2, "EN_CORRECCION", true);
            fase(3, "EN_CURSO", true);
            fase(4, "QUITADA", true);

            assertThat(datos.fasesSinAprobar(proyecto)).isEqualTo(2);
        }

        @Test
        @DisplayName("pendientes: [correcciones abiertas, pasos por hacer]; los HECHO y los QUITADO no cuentan (RN-16)")
        void pendientes() {
            UUID f = fase(1, "EN_CURSO", true);
            paso(f, 1, "PASO", "HECHO");
            paso(f, 2, "PASO", "PENDIENTE");
            paso(f, 3, "PASO", "QUITADO");
            paso(f, 4, "CORRECCION", "PENDIENTE");
            paso(f, 5, "CORRECCION", "HECHO");

            assertThat(datos.pendientes(f)).containsExactly(1, 2);
        }

        @Test
        @DisplayName("pendientes de una fase sin pasos: [0, 0], no nulo")
        void pendientesSinPasos() {
            assertThat(datos.pendientes(fase(1, "EN_CURSO", true))).containsExactly(0, 0);
        }

        @Test
        @DisplayName("conteo: pasos hechos y pasos totales sin contar los quitados")
        void conteo() {
            UUID f = fase(1, "EN_CURSO", true);
            paso(f, 1, "PASO", "HECHO");
            paso(f, 2, "PASO", "PENDIENTE");
            paso(f, 3, "PASO", "QUITADO");

            assertThat(datos.conteo(f)).containsExactly(1, 2);
        }

        @Test
        @DisplayName("correccionesAbiertasDelProyecto: suma las de todas sus fases y no las de otro proyecto")
        void correccionesDelProyecto() {
            UUID a = fase(1, "EN_CURSO", true);
            UUID b = fase(2, "APROBADA", true);
            UUID ajena = fase(UUID.randomUUID(), 1, "EN_CURSO", true);
            paso(a, 1, "CORRECCION", "PENDIENTE");
            paso(b, 1, "CORRECCION", "PENDIENTE");
            paso(b, 2, "CORRECCION", "HECHO");
            paso(ajena, 1, "CORRECCION", "PENDIENTE");

            assertThat(datos.correccionesAbiertasDelProyecto(proyecto)).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------------------------------------ transiciones

    @Nested
    @DisplayName("Transiciones de estado de la fase (la guarda WHERE estado = … impide pisarse)")
    class Transiciones {

        @Test
        @DisplayName("entregarParaRevision: EN_CURSO → EN_REVISION con su plazo; la segunda vez devuelve false y no cambia nada")
        void entregar() {
            UUID f = fase(1, "EN_CURSO", true);

            assertThat(datos.entregarParaRevision(f, AHORA, LocalDate.of(2031, 3, 7))).isTrue();
            assertThat(estadoDe(f)).isEqualTo("EN_REVISION");
            assertThat(jdbc.queryForObject("SELECT plazo_aprobacion FROM proyecto_fases WHERE id = ?", LocalDate.class, f)).isEqualTo(LocalDate.of(2031, 3, 7));

            assertThat(datos.entregarParaRevision(f, AHORA, LocalDate.of(2031, 3, 9))).as("ya no está EN_CURSO").isFalse();
            assertThat(jdbc.queryForObject("SELECT plazo_aprobacion FROM proyecto_fases WHERE id = ?", LocalDate.class, f)).isEqualTo(LocalDate.of(2031, 3, 7));
        }

        @Test
        @DisplayName("entregarParaRevision no toca una fase PENDIENTE ni una APROBADA")
        void entregarSoloDesdeEnCurso() {
            UUID pendiente = fase(1, "PENDIENTE", true);
            UUID aprobada = fase(2, "APROBADA", true);

            assertThat(datos.entregarParaRevision(pendiente, AHORA, LocalDate.of(2031, 3, 7))).isFalse();
            assertThat(datos.entregarParaRevision(aprobada, AHORA, LocalDate.of(2031, 3, 7))).isFalse();
            assertThat(estadoDe(pendiente)).isEqualTo("PENDIENTE");
        }

        @Test
        @DisplayName("aprobar desde EN_REVISION: guarda quién y cuándo, limpia el plazo y marca la aprobación automática si lo fue")
        void aprobarDesdeRevision() {
            UUID f = fase(1, "EN_REVISION", true);
            jdbc.update("UPDATE proyecto_fases SET entregada_at = ?, plazo_aprobacion = ? WHERE id = ?", java.sql.Timestamp.from(AHORA.minusSeconds(86400)), LocalDate.of(2031, 3, 7), f);

            assertThat(datos.aprobar(f, AHORA, null, true)).isTrue();

            FaseFila fila = datos.faseConProyecto(f).orElseThrow().fase();
            assertThat(fila.estado()).isEqualTo("APROBADA");
            assertThat(fila.aprobadaAt()).isEqualTo(AHORA);
            assertThat(fila.aprobacionAutomatica()).isTrue();
            assertThat(fila.plazoAprobacion()).isNull();
            assertThat(fila.entregadaAt()).as("conserva la fecha de entrega que ya tenía").isEqualTo(AHORA.minusSeconds(86400));
        }

        @Test
        @DisplayName("aprobar desde EN_CURSO (la fase que cierra el equipo): usa la fecha como entrega y guarda al aprobador")
        void aprobarDesdeEnCurso() {
            UUID f = fase(1, "EN_CURSO", false);

            assertThat(datos.aprobar(f, AHORA, autor, false)).isTrue();

            FaseFila fila = datos.faseConProyecto(f).orElseThrow().fase();
            assertThat(fila.entregadaAt()).isEqualTo(AHORA);
            assertThat(fila.aprobacionAutomatica()).isFalse();
            assertThat(jdbc.queryForObject("SELECT aprobada_por FROM proyecto_fases WHERE id = ?", UUID.class, f)).isEqualTo(autor);
        }

        @Test
        @DisplayName("aprobar dos veces (dos pestañas): la segunda devuelve false; una PENDIENTE tampoco se aprueba")
        void aprobarDosVeces() {
            UUID f = fase(1, "EN_REVISION", true);
            UUID pendiente = fase(2, "PENDIENTE", true);

            assertThat(datos.aprobar(f, AHORA, autor, false)).isTrue();
            assertThat(datos.aprobar(f, AHORA.plusSeconds(5), autor, false)).isFalse();
            assertThat(datos.aprobar(pendiente, AHORA, autor, false)).isFalse();
            assertThat(estadoDe(pendiente)).isEqualTo("PENDIENTE");
        }

        @Test
        @DisplayName("iniciar: solo una fase PENDIENTE pasa a EN_CURSO con su fecha de inicio")
        void iniciar() {
            UUID pendiente = fase(1, "PENDIENTE", true);
            UUID aprobada = fase(2, "APROBADA", true);

            datos.iniciar(pendiente, AHORA);
            datos.iniciar(aprobada, AHORA);

            assertThat(estadoDe(pendiente)).isEqualTo("EN_CURSO");
            assertThat(datos.faseConProyecto(pendiente).orElseThrow().fase().iniciadaAt()).isEqualTo(AHORA);
            assertThat(estadoDe(aprobada)).isEqualTo("APROBADA");
        }

        @Test
        @DisplayName("devolverPorAjuste: EN_REVISION → EN_CURSO, suma una ronda y limpia el plazo; fuera de revisión devuelve false")
        void devolverPorAjuste() {
            UUID f = fase(1, "EN_REVISION", true);
            jdbc.update("UPDATE proyecto_fases SET plazo_aprobacion = ? WHERE id = ?", LocalDate.of(2031, 3, 7), f);

            assertThat(datos.devolverPorAjuste(f)).isTrue();

            FaseFila fila = datos.faseConProyecto(f).orElseThrow().fase();
            assertThat(fila.estado()).isEqualTo("EN_CURSO");
            assertThat(fila.rondasAjusteUsadas()).isEqualTo(1);
            assertThat(fila.plazoAprobacion()).isNull();
            assertThat(datos.devolverPorAjuste(f)).as("ya está EN_CURSO").isFalse();
        }

        @Test
        @DisplayName("Valor límite: la base no deja pasar de 2 rondas de ajuste (ck_pf_rondas)")
        void tercerAjusteLoRechazaLaBase() {
            UUID f = fase(1, "EN_REVISION", true);
            jdbc.update("UPDATE proyecto_fases SET rondas_ajuste_usadas = 2 WHERE id = ?", f);

            assertThatThrownBy(() -> datos.devolverPorAjuste(f)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("ponerEnCorreccion y volverAAprobada: solo entre APROBADA y EN_CORRECCION, nunca desde otro estado")
        void correccionDeUnaFaseAprobada() {
            UUID aprobada = fase(1, "APROBADA", true);
            UUID enCurso = fase(2, "EN_CURSO", true);

            datos.ponerEnCorreccion(aprobada);
            datos.ponerEnCorreccion(enCurso);
            assertThat(estadoDe(aprobada)).isEqualTo("EN_CORRECCION");
            assertThat(estadoDe(enCurso)).isEqualTo("EN_CURSO");

            datos.volverAAprobada(aprobada);
            datos.volverAAprobada(enCurso);
            assertThat(estadoDe(aprobada)).isEqualTo("APROBADA");
            assertThat(estadoDe(enCurso)).isEqualTo("EN_CURSO");
        }
    }

    // ------------------------------------------------------------------------------------------------ pasos y solicitudes

    @Nested
    @DisplayName("Pasos, correcciones y solicitudes de cambio")
    class PasosYSolicitudes {

        @Test
        @DisplayName("insertarPaso: va al final de la fase (orden = máximo + 1) y empieza en 1 si la fase no tiene pasos")
        void insertaAlFinal() {
            UUID f = fase(1, "EN_CURSO", true);

            UUID primero = datos.insertarPaso(f, "CORRECCION", "Total del reporte", EXPLICACION, "La encontramos en pruebas");
            UUID segundo = datos.insertarPaso(f, "AJUSTE", "Ajuste 1 de 2", EXPLICACION, "Lo pidió el cliente");

            assertThat(jdbc.queryForObject("SELECT orden FROM pasos WHERE id = ?", Integer.class, primero)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT orden FROM pasos WHERE id = ?", Integer.class, segundo)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT estado::text FROM pasos WHERE id = ?", String.class, primero)).isEqualTo("PENDIENTE");
        }

        @Test
        @DisplayName("Una corrección con explicación de menos de 20 caracteres la rechaza la base (ck_pasos_explicacion), no pasa como 500 sin causa")
        void explicacionCorta() {
            UUID f = fase(1, "EN_CURSO", true);

            assertThatThrownBy(() -> datos.insertarPaso(f, "CORRECCION", "Total del reporte", "muy corta", "La encontramos en pruebas"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_pasos_explicacion");
        }

        @Test
        @DisplayName("Una corrección sin origen la rechaza la base (ck_pasos_origen)")
        void sinOrigen() {
            UUID f = fase(1, "EN_CURSO", true);

            assertThatThrownBy(() -> datos.insertarPaso(f, "CORRECCION", "Total del reporte", EXPLICACION, null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_pasos_origen");
        }

        @Test
        @DisplayName("marcarHecho: pasa a HECHO con nota, fecha y autor; la segunda vez devuelve false (otra pestaña se adelantó)")
        void marcarHecho() {
            UUID f = fase(1, "EN_CURSO", true);
            UUID p = paso(f, 1, "PASO", "PENDIENTE");

            assertThat(datos.marcarHecho(p, NOTA, autor, AHORA)).isTrue();
            assertThat(datos.marcarHecho(p, NOTA, autor, AHORA)).isFalse();

            assertThat(jdbc.queryForObject("SELECT nota FROM pasos WHERE id = ?", String.class, p)).isEqualTo(NOTA);
            assertThat(datos.conteo(f)).containsExactly(1, 1);
        }

        @Test
        @DisplayName("Valores límite de la nota: 19 caracteres la rechaza la base; 20 pasa; 401 en un PASO la rechaza")
        void limitesDeLaNota() {
            UUID f = fase(1, "EN_CURSO", true);
            UUID corta = paso(f, 1, "PASO", "PENDIENTE");
            UUID justa = paso(f, 2, "PASO", "PENDIENTE");
            UUID larga = paso(f, 3, "PASO", "PENDIENTE");

            rechaza(() -> datos.marcarHecho(corta, "x".repeat(19), autor, AHORA), "ck_pasos_nota");
            assertThat(datos.marcarHecho(justa, "x".repeat(20), autor, AHORA)).isTrue();
            rechaza(() -> datos.marcarHecho(larga, "x".repeat(401), autor, AHORA), "ck_pasos_nota");
        }

        @Test
        @DisplayName("Un paso HECHO ya no se edita ni se borra: lo impide el disparador tg_proteger_pasos (RN-09)")
        void pasoPublicadoEsInmutable() {
            UUID f = fase(1, "EN_CURSO", true);
            UUID p = paso(f, 1, "PASO", "HECHO");

            rechaza(() -> jdbc.update("UPDATE pasos SET titulo = 'Cambiado a escondidas' WHERE id = ?", p), "RN-09");
            rechaza(() -> jdbc.update("DELETE FROM pasos WHERE id = ?", p), "RN-09");
            assertThat(jdbc.queryForObject("SELECT titulo FROM pasos WHERE id = ?", String.class, p)).isEqualTo("Paso 1");
        }

        @Test
        @DisplayName("Una solicitud de ajuste: se guarda con su paso, aparece en cambiosPedidos y al resolver su paso queda RESUELTO")
        void solicitudDeAjuste() {
            UUID f = fase(1, "EN_REVISION", true);
            UUID p = datos.insertarPaso(f, "AJUSTE", "Ajuste 1 de 2", EXPLICACION, "Lo pidió el cliente");
            UUID cliente = UUID.randomUUID();

            UUID solicitud = datos.insertarSolicitud(f, cliente, "AJUSTE", "Quiero el total por semana.", "ABIERTO", p);

            List<CambioPedido> pedidos = datos.cambiosPedidos(proyecto);
            assertThat(pedidos).hasSize(1);
            assertThat(pedidos.get(0).faseId()).isEqualTo(f);
            assertThat(pedidos.get(0).tipo()).isEqualTo("AJUSTE");
            assertThat(pedidos.get(0).estado()).isEqualTo("ABIERTO");

            datos.resolverSolicitudDelPaso(p);

            assertThat(jdbc.queryForObject("SELECT estado::text FROM solicitudes_cambio WHERE id = ?", String.class, solicitud)).isEqualTo("RESUELTO");
        }

        @Test
        @DisplayName("resolverSolicitudDelPaso no toca una solicitud que no esté ABIERTA o ACEPTADA (una POR_COTIZAR sigue igual)")
        void noResuelveLoQueNoCorresponde() {
            UUID f = fase(1, "EN_REVISION", true);
            UUID p = datos.insertarPaso(f, "ALCANCE", "Reporte semanal", EXPLICACION, "Lo pidió el cliente");
            UUID solicitud = datos.insertarSolicitud(f, UUID.randomUUID(), "ALCANCE", "Quiero algo nuevo en el sistema.", "POR_COTIZAR", p);

            datos.resolverSolicitudDelPaso(p);

            assertThat(jdbc.queryForObject("SELECT estado::text FROM solicitudes_cambio WHERE id = ?", String.class, solicitud)).isEqualTo("POR_COTIZAR");
        }

        @Test
        @DisplayName("cambiosPedidos: solo los de ese proyecto, en el orden en que se pidieron")
        void cambiosPedidosDeUnProyecto() {
            UUID f = fase(1, "EN_REVISION", true);
            UUID ajena = fase(UUID.randomUUID(), 1, "EN_REVISION", true);
            datos.insertarSolicitud(f, UUID.randomUUID(), "ALCANCE", "Primera petición del cliente.", "POR_COTIZAR", null);
            datos.insertarSolicitud(ajena, UUID.randomUUID(), "ALCANCE", "Petición de otro proyecto.", "POR_COTIZAR", null);

            assertThat(datos.cambiosPedidos(proyecto)).hasSize(1).first().extracting(CambioPedido::descripcion).isEqualTo("Primera petición del cliente.");
        }
    }

    // ------------------------------------------------------------------------------------------------ tareas programadas

    @Nested
    @DisplayName("Consultas de las tareas programadas (RN-17)")
    class Tareas {

        @Test
        @DisplayName("fasesEnRevision: solo las EN_REVISION con plazo, por fecha de entrega, y marca si ya se recordó")
        void fasesEnRevision() {
            UUID primera = fase(UUID.randomUUID(), 1, "EN_REVISION", true);
            UUID segunda = fase(UUID.randomUUID(), 1, "EN_REVISION", true);
            UUID sinPlazo = fase(UUID.randomUUID(), 1, "EN_REVISION", true);
            fase(UUID.randomUUID(), 1, "EN_CURSO", true);
            jdbc.update("UPDATE proyecto_fases SET entregada_at = ?, plazo_aprobacion = ? WHERE id = ?", java.sql.Timestamp.from(AHORA.minusSeconds(200)), LocalDate.of(2031, 3, 7), primera);
            jdbc.update("UPDATE proyecto_fases SET entregada_at = ?, plazo_aprobacion = ?, plazo_recordado_at = now() WHERE id = ?", java.sql.Timestamp.from(AHORA.minusSeconds(100)), LocalDate.of(2031, 3, 8), segunda);

            List<FaseEnRevision> lista = datos.fasesEnRevision().stream().filter(f -> f.faseId().equals(primera) || f.faseId().equals(segunda) || f.faseId().equals(sinPlazo)).toList();

            assertThat(lista).extracting(FaseEnRevision::faseId).containsExactly(primera, segunda);
            assertThat(lista).extracting(FaseEnRevision::recordada).containsExactly(false, true);
            assertThat(lista.get(0).plazoAprobacion()).isEqualTo(LocalDate.of(2031, 3, 7));
        }

        @Test
        @DisplayName("marcarRecordada: la fase queda marcada y deja de pedir recordatorio")
        void marcarRecordada() {
            UUID f = fase(UUID.randomUUID(), 1, "EN_REVISION", true);
            jdbc.update("UPDATE proyecto_fases SET entregada_at = now(), plazo_aprobacion = ? WHERE id = ?", LocalDate.of(2031, 3, 7), f);

            datos.marcarRecordada(f);

            assertThat(datos.fasesEnRevision()).filteredOn(x -> x.faseId().equals(f)).singleElement().extracting(FaseEnRevision::recordada).isEqualTo(true);
        }

        @Test
        @DisplayName("adjuntosPorBorrar: con la lista vacía no consulta y devuelve vacío; solo trae los que su archivo sigue en el almacén")
        void adjuntosPorBorrar() {
            assertThat(datos.adjuntosPorBorrar(List.of())).isEmpty();

            UUID f = fase(1, "EN_CURSO", true);
            UUID p = paso(f, 1, "PASO", "PENDIENTE");
            UUID vigente = datos.insertarAdjunto(p, "Boceto", "a1.png", "image/png", 1000);
            UUID borrado = datos.insertarAdjunto(p, "Otro boceto", "a2.png", "image/png", 2000);
            datos.marcarArchivoEliminado(borrado);

            assertThat(datos.adjuntosPorBorrar(List.of(proyecto))).extracting(SeguimientoJdbc.AdjuntoPorBorrar::id).containsExactly(vigente);
            assertThat(datos.adjuntosPorBorrar(List.of(UUID.randomUUID()))).isEmpty();
        }
    }
}
