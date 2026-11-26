package co.coretech.portal.proyectos.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;

/**
 * Pruebas de PERSISTENCIA y CONSULTAS de {@link PlantillasJdbc} (HU-27, RN-27, RN-28) contra PostgreSQL 15 real: leer la
 * plantilla de un servicio, reemplazarla completa y la revisión que hace la base al confirmar (disparadores
 * {@code tg_validar_plantilla_*}, {@code DEFERRABLE INITIALLY DEFERRED}): de 2 a 8 fases, una que apruebe el cliente y al menos
 * un paso por fase. Se usa un servicio de los que trae la migración V1 (el que más importa es que tenga plantilla de verdad).
 * Cada prueba corre en una transacción que se revierte.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class PlantillasJdbcDataJpaTest {

    @Autowired
    JdbcTemplate jdbc;

    PlantillasJdbc plantillas;
    UUID servicio;
    final UUID usuario = UUID.randomUUID();

    @BeforeEach
    void preparaLoComun() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        plantillas = new PlantillasJdbc(jdbc);
        servicio = jdbc.queryForObject("SELECT id FROM servicios ORDER BY orden LIMIT 1", UUID.class);
    }

    private static FasePlantilla fase(String nombre, boolean aprueba, String... pasos) {
        return new FasePlantilla(0, nombre, null, aprueba, IntStream.range(0, pasos.length).mapToObj(i -> new PasoPlantilla(0, pasos[i], null)).toList());
    }

    /** Revisa ya los disparadores de la plantilla, que en producción corren al confirmar la transacción. */
    private void revisarAhora() {
        jdbc.execute("SET CONSTRAINTS tg_validar_plantilla_fases, tg_validar_plantilla_pasos IMMEDIATE");
    }

    /** PostgreSQL aborta la transacción al primer error: un punto de guardado deja comprobar el rechazo y seguir. */
    private void rechaza(List<FasePlantilla> fases, String mensaje) {
        jdbc.execute("SAVEPOINT antes_del_rechazo");
        try {
            plantillas.reemplazar(servicio, usuario, fases);
            assertThatThrownBy(this::revisarAhora).isInstanceOf(DataAccessException.class).hasMessageContaining(mensaje);
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT antes_del_rechazo");
        }
    }

    @Test
    @DisplayName("leer: la plantilla de un servicio de la migración trae de 2 a 8 fases en orden, cada una con sus pasos")
    void leeLaPlantillaSembrada() {
        List<FasePlantilla> fases = plantillas.leer(servicio);

        assertThat(fases).hasSizeBetween(2, 8);
        assertThat(fases).extracting(FasePlantilla::orden).isSorted().startsWith(1);
        assertThat(fases).allSatisfy(f -> assertThat(f.pasos()).isNotEmpty().extracting(PasoPlantilla::orden).isSorted());
        assertThat(fases).anyMatch(FasePlantilla::apruebaCliente);
    }

    @Test
    @DisplayName("leer un servicio sin plantilla (o que no existe) devuelve una lista vacía, no nula")
    void servicioSinPlantilla() {
        assertThat(plantillas.leer(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("reemplazar: lo que se guarda es lo que se lee, numerado en el orden de la lista y sin restos de la plantilla anterior")
    void reemplazaTodo() {
        List<FasePlantilla> nueva = List.of(
                new FasePlantilla(99, "Diseño", "Se define qué se construye", false,
                        List.of(new PasoPlantilla(77, "Reunir requisitos", "Hablamos contigo"), new PasoPlantilla(5, "Dibujar pantallas", null))),
                fase("Entrega", true, "Capacitar al equipo"));

        plantillas.reemplazar(servicio, usuario, nueva);
        List<FasePlantilla> leida = plantillas.leer(servicio);

        assertThat(leida).hasSize(2);
        assertThat(leida).extracting(FasePlantilla::nombre).containsExactly("Diseño", "Entrega");
        assertThat(leida).extracting(FasePlantilla::orden).as("el orden lo da la lista, no el que mande el cliente").containsExactly(1, 2);
        assertThat(leida.get(0).queEs()).isEqualTo("Se define qué se construye");
        assertThat(leida.get(0).apruebaCliente()).isFalse();
        assertThat(leida.get(0).pasos()).extracting(PasoPlantilla::titulo).containsExactly("Reunir requisitos", "Dibujar pantallas");
        assertThat(leida.get(0).pasos()).extracting(PasoPlantilla::orden).containsExactly(1, 2);
        assertThat(leida.get(0).pasos().get(0).explicacion()).isEqualTo("Hablamos contigo");
        assertThat(leida.get(1).apruebaCliente()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_fases WHERE servicio_id = ?", Integer.class, servicio)).isEqualTo(2);
    }

    @Test
    @DisplayName("reemplazar no toca la plantilla de otro servicio")
    void noTocaOtrosServicios() {
        UUID otro = jdbc.queryForObject("SELECT id FROM servicios WHERE id <> ? ORDER BY orden LIMIT 1", UUID.class, servicio);
        int antes = plantillas.leer(otro).size();

        plantillas.reemplazar(servicio, usuario, List.of(fase("Diseño", false, "Reunir"), fase("Entrega", true, "Capacitar")));

        assertThat(plantillas.leer(otro)).hasSize(antes);
    }

    @Test
    @DisplayName("Una plantilla válida pasa la revisión de la base (2 fases, una la aprueba el cliente, cada una con un paso)")
    void validaPasaLaRevision() {
        plantillas.reemplazar(servicio, usuario, List.of(fase("Diseño", false, "Reunir"), fase("Entrega", true, "Capacitar")));

        revisarAhora();
    }

    @Test
    @DisplayName("Valores límite de la base: 8 fases pasan; 9 las rechaza (RN-28)")
    void limiteDeFases() {
        List<FasePlantilla> ocho = IntStream.rangeClosed(1, 8).mapToObj(i -> fase("Fase " + i, i == 8, "Hacer algo " + i)).toList();
        plantillas.reemplazar(servicio, usuario, ocho);
        revisarAhora();
        jdbc.execute("SET CONSTRAINTS tg_validar_plantilla_fases, tg_validar_plantilla_pasos DEFERRED");

        // La novena fase ni siquiera entra: el CHECK ck_plf_orden (orden de 1 a 8) la rechaza al insertarla, antes de que el
        // disparador revise el conjunto al confirmar. Son dos barreras distintas para la misma regla.
        List<FasePlantilla> nueve = IntStream.rangeClosed(1, 9).mapToObj(i -> fase("Fase " + i, i == 9, "Hacer algo " + i)).toList();
        jdbc.execute("SAVEPOINT antes_de_nueve");
        try {
            assertThatThrownBy(() -> plantillas.reemplazar(servicio, usuario, nueve))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_plf_orden");
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT antes_de_nueve");
        }
    }

    @Test
    @DisplayName("Una sola fase la rechaza la base: «debe tener de 2 a 8 fases» (RN-28)")
    void unaSolaFase() {
        rechaza(List.of(fase("Entrega", true, "Capacitar")), "de 2 a 8 fases");
    }

    @Test
    @DisplayName("Ninguna fase que apruebe el cliente la rechaza la base (RN-28)")
    void ningunaApruebaElCliente() {
        rechaza(List.of(fase("Diseño", false, "Reunir"), fase("Entrega", false, "Capacitar")), "al menos una fase que apruebe el cliente");
    }

    @Test
    @DisplayName("Una fase sin pasos la rechaza la base (RN-28)")
    void faseSinPasos() {
        rechaza(List.of(fase("Diseño", false), fase("Entrega", true, "Capacitar")), "necesita al menos un paso");
    }
}
