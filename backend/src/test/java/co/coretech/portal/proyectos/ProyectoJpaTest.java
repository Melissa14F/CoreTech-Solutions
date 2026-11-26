package co.coretech.portal.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.proyectos.domain.EstadoFase;
import co.coretech.portal.proyectos.domain.EstadoPaso;
import co.coretech.portal.proyectos.domain.EstadoPlan;
import co.coretech.portal.proyectos.domain.EstadoProyecto;
import co.coretech.portal.proyectos.domain.Paso;
import co.coretech.portal.proyectos.domain.ProyectoFase;
import co.coretech.portal.proyectos.domain.TipoPaso;
import co.coretech.portal.proyectos.repository.ProyectoRepositorio;
import jakarta.persistence.EntityManagerFactory;

/**
 * Mapeo JPA de proyectos, fases y pasos contra PostgreSQL 15 (ADR-004): las entidades coinciden con el esquema, los
 * enums de la base se leen con {@code @Enumerated(STRING)} y las fases y los pasos son perezosos (LAZY).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class ProyectoJpaTest extends BaseProyectosPrueba {

    @Autowired
    ProyectoRepositorio proyectos;

    @Autowired
    EntityManagerFactory fabrica;

    private TransactionTemplate transaccion() {
        return new TransactionTemplate(transacciones);
    }

    @Test
    void leeElProyectoConSusEnumsDeLaBase() throws Exception {
        Proyecto creado = proyecto(crearUsuario("ADMINISTRADOR"));

        var leido = proyectos.findByCodigo(creado.codigo()).orElseThrow();

        assertThat(leido.getCodigo()).isEqualTo(creado.codigo());
        assertThat(leido.getClienteId()).isEqualTo(creado.clienteId());
        assertThat(leido.getEquipoId()).isEqualTo(creado.equipo().id());
        assertThat(leido.getEstado()).isEqualTo(EstadoProyecto.ACTIVO);
        assertThat(leido.getEstadoPlan()).isEqualTo(EstadoPlan.BORRADOR);
        assertThat(leido.getAvancePorcentaje()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(leido.getNombre()).isEqualTo("Inventario del salón");
    }

    @Test
    void lasFasesSonPerezosasYNoSeCarganSinPedirlas() throws Exception {
        Proyecto creado = proyecto(crearUsuario("ADMINISTRADOR"));

        var leido = proyectos.findByCodigo(creado.codigo()).orElseThrow();

        assertThat(fabrica.getPersistenceUnitUtil().isLoaded(leido, "fases")).isFalse();
        // Fuera de la transacción la colección sin cargar no se puede abrir: prueba de que no se trajo con el proyecto
        assertThatThrownBy(leido::getFases).isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void conElGrafoDeEntidadTraeLasFasesEnOrdenYSusPasosSiguenPerezosos() throws Exception {
        Proyecto creado = proyecto(crearUsuario("ADMINISTRADOR"));

        transaccion().executeWithoutResult(estado -> {
            var leido = proyectos.findConFasesByCodigo(creado.codigo()).orElseThrow();

            assertThat(fabrica.getPersistenceUnitUtil().isLoaded(leido, "fases")).isTrue();
            List<ProyectoFase> fases = leido.getFases();
            assertThat(fases).hasSize(3);
            assertThat(fases).extracting(ProyectoFase::getOrden).containsExactly((short) 1, (short) 2, (short) 3);
            assertThat(fases).extracting(ProyectoFase::getEstado).doesNotContainNull();
            assertThat(fases.get(0).isApruebaCliente()).isTrue();
            assertThat(fases.get(1).getEstado()).isEqualTo(EstadoFase.PENDIENTE);

            // Los pasos de cada fase no vinieron con la consulta: se piden aparte (LAZY)
            assertThat(fabrica.getPersistenceUnitUtil().isLoaded(fases.get(0), "pasos")).isFalse();
            List<Paso> pasos = fases.get(0).getPasos();
            assertThat(pasos).hasSize(2);
            assertThat(pasos).extracting(Paso::getOrden).containsExactly((short) 1, (short) 2);
            assertThat(pasos).extracting(Paso::getTipo).containsOnly(TipoPaso.PASO);
            assertThat(pasos).extracting(Paso::getEstado).containsOnly(EstadoPaso.PENDIENTE);
            assertThat(pasos.get(0).getFase().getId()).isEqualTo(fases.get(0).getId());
        });
    }

    @Test
    void lasEntidadesNoExponenSettersNiTocanLaBase() throws Exception {
        Proyecto creado = proyecto(crearUsuario("ADMINISTRADOR"));
        String nombreAntes = jdbc.queryForObject("SELECT nombre FROM proyectos WHERE codigo = ?", String.class, creado.codigo());

        transaccion().executeWithoutResult(estado -> {
            var leido = proyectos.findByCodigo(creado.codigo()).orElseThrow();
            // @Immutable: no hay setters y Hibernate ignora cualquier cambio de estado en la entidad
            assertThat(leido.getClass().getDeclaredMethods()).noneMatch(m -> m.getName().startsWith("set"));
        });

        assertThat(jdbc.queryForObject("SELECT nombre FROM proyectos WHERE codigo = ?", String.class, creado.codigo()))
                .isEqualTo(nombreAntes);
    }

    @Test
    void unProyectoNuncaSeBorraYElRepositorioLoBloquea() throws Exception {
        Proyecto creado = proyecto(crearUsuario("ADMINISTRADOR"));
        var leido = proyectos.findByCodigo(creado.codigo()).orElseThrow();
        long antes = proyectos.count();

        assertThatThrownBy(() -> proyectos.delete(leido)).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("se cancela");
        assertThatThrownBy(() -> proyectos.deleteById(leido.getId())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(proyectos::deleteAll).isInstanceOf(UnsupportedOperationException.class);

        assertThat(proyectos.count()).isEqualTo(antes);
        assertThat(proyectos.findByCodigo(creado.codigo())).isPresent();
    }

    @Test
    void codigoDeCitaYCodigosPorCitaEncuentranElProyectoDeUnaCita() throws Exception {
        Proyecto creado = proyecto(crearUsuario("ADMINISTRADOR"));
        UUID cita = jdbc.queryForObject("SELECT cita_id FROM proyectos WHERE codigo = ?", UUID.class, creado.codigo());

        assertThat(proyectos.codigoDeCita(cita)).contains(creado.codigo());
        assertThat(proyectos.codigoDeCita(UUID.randomUUID())).isEmpty();
        assertThat(proyectos.codigosPorCita()).containsEntry(cita, creado.codigo());
    }
}
