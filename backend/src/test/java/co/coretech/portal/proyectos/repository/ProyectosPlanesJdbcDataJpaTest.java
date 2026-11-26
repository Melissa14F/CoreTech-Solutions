package co.coretech.portal.proyectos.repository;

import co.coretech.portal.proyectos.domain.EstadoPlan;

import co.coretech.portal.proyectos.domain.EstadoProyecto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import co.coretech.portal.proyectos.repository.PlanesJdbc.PlanPendiente;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;
import co.coretech.portal.proyectos.domain.ProyectoFila;

/**
 * Pruebas de PERSISTENCIA y CONSULTAS de {@link ProyectosJdbc} y {@link PlanesJdbc} (HU-02, HU-17, HU-28, HU-29; RN-04, RN-05,
 * RN-06, RN-08, RN-27 a RN-29) contra PostgreSQL 15 real (Testcontainers + Flyway V1…V16): el SQL de cada operación, la consulta
 * «en riesgo» con sus cuatro casos, el avance que calcula la base y las restricciones y disparadores que guardan el recorrido del
 * plan (BORRADOR → ENVIADO → APROBADO, AJUSTES_PEDIDOS, RECHAZADO, VENCIDO). No es H2: usa enums, índices parciales y
 * disparadores de PostgreSQL. Cada prueba corre en una transacción que se revierte; las llaves foráneas se difieren porque la
 * cita, el cliente y el equipo no son lo que se prueba.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class ProyectosPlanesJdbcDataJpaTest {

    static final String ALCANCE = "Un sistema para llevar el inventario del salón de belleza.";
    static final String CONDICIONES = "Incluye diseño, desarrollo y capacitación; no incluye hosting.";
    static final LocalDate HOY = LocalDate.now();

    @Autowired
    JdbcTemplate jdbc;

    ProyectosJdbc proyectos;
    @Autowired
    ProyectoRepositorio repo;
    @Autowired
    CambioPlanRepositorio cambiosRepo;
    @Autowired
    jakarta.persistence.EntityManager entidades;
    PlanesJdbc planes;
    UUID servicio;
    final UUID cliente = UUID.randomUUID();
    final UUID equipo = UUID.randomUUID();
    final UUID admin = UUID.randomUUID();

    @BeforeEach
    void preparaLoComun() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        proyectos = new ProyectosJdbc(jdbc);
        planes = new PlanesJdbc(jdbc);
        servicio = jdbc.queryForObject("SELECT id FROM servicios ORDER BY orden LIMIT 1", UUID.class);
    }

    // ---- ayudas ----

    UUID crear(LocalDate inicio, LocalDate entrega) {
        return repo.insertar(repo.siguienteCodigo(), "Inventario del salón", ALCANCE, UUID.randomUUID(), cliente, servicio, equipo, admin, inicio, entrega);
    }

    UUID crear() {
        return crear(HOY.minusDays(5), HOY.plusDays(60));
    }

    String codigo(UUID id) {
        return jdbc.queryForObject("SELECT codigo FROM proyectos WHERE id = ?", String.class, id);
    }

    ProyectoFila leer(UUID id) {
        return proyectos.porCodigo(codigo(id), HOY).orElseThrow();
    }

    static FasePlantilla fase(String nombre, boolean aprueba, String... pasos) {
        return new FasePlantilla(0, nombre, null, aprueba, java.util.Arrays.stream(pasos).map(p -> new PasoPlantilla(0, p, null)).toList());
    }

    static List<FasePlantilla> planValido() {
        return List.of(fase("Diseño", false, "Reunir requisitos", "Dibujar pantallas"), fase("Entrega", true, "Capacitar al equipo"));
    }

    /** Un proyecto con su plan de 2 fases ya guardado, listo para enviar. */
    UUID conPlan() {
        UUID id = crear();
        planes.reemplazarFases(id, planValido());
        return id;
    }

    void enviar(UUID id) {
        repo.enviarPlan(id, new BigDecimal("5000000"), CONDICIONES, HOY.plusDays(60), HOY.plusDays(3), HOY.plusDays(14));
    }

    /** PostgreSQL aborta la transacción al primer error: un punto de guardado deja comprobar el rechazo y seguir. */
    void rechaza(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String fragmento) {
        jdbc.execute("SAVEPOINT antes_del_rechazo");
        try {
            assertThatThrownBy(accion).isInstanceOf(DataAccessException.class).hasMessageContaining(fragmento);
        } finally {
            jdbc.execute("ROLLBACK TO SAVEPOINT antes_del_rechazo");
            // Hibernate conserva la entidad cuyo INSERT falló y la reintentaría en el siguiente flush: se descarta
            entidades.clear();
        }
    }

    String estadoFase(UUID proyecto, int orden) {
        return jdbc.queryForObject("SELECT estado::text FROM proyecto_fases WHERE proyecto_id = ? AND orden = ?", String.class, proyecto, orden);
    }

    // ------------------------------------------------------------------------------------------------ proyectos

    @Nested
    @DisplayName("ProyectosJdbc: crear y leer (HU-02)")
    class CrearYLeer {

        @Test
        @DisplayName("Lo que se inserta es lo que se lee: nace ACTIVO, con el plan en BORRADOR, 0 % de avance y sin fases (RN-06)")
        void nace() {
            UUID id = crear(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 4, 20));

            ProyectoFila p = leer(id);

            assertThat(p.id()).isEqualTo(id);
            assertThat(p.nombre()).isEqualTo("Inventario del salón");
            assertThat(p.alcance()).isEqualTo(ALCANCE);
            assertThat(p.clienteId()).isEqualTo(cliente);
            assertThat(p.equipoId()).isEqualTo(equipo);
            assertThat(p.fechaInicio()).isEqualTo(LocalDate.of(2031, 3, 1));
            assertThat(p.fechaEntrega()).isEqualTo(LocalDate.of(2031, 4, 20));
            assertThat(p.estado()).isEqualTo("ACTIVO");
            assertThat(p.estadoPlan()).isEqualTo("BORRADOR");
            assertThat(p.avance()).isEqualByComparingTo("0");
            assertThat(p.fases()).isZero();
            assertThat(p.valorCotizado()).isNull();
        }

        @Test
        @DisplayName("siguienteCodigo: «P-» con cuatro dígitos o más, y cada llamada da uno nuevo")
        void codigos() {
            String primero = repo.siguienteCodigo();
            String segundo = repo.siguienteCodigo();

            assertThat(primero).matches("P-\\d{4,}");
            assertThat(segundo).matches("P-\\d{4,}").isNotEqualTo(primero);
        }

        @Test
        @DisplayName("Cada cita genera como máximo un proyecto: la segunda con la misma cita la rechaza la base (RN-04)")
        void unaCitaUnProyecto() {
            UUID cita = UUID.randomUUID();
            repo.insertar(repo.siguienteCodigo(), "Inventario del salón", ALCANCE, cita, cliente, servicio, equipo, admin, HOY, HOY.plusDays(30));

            assertThatThrownBy(() -> repo.insertar(repo.siguienteCodigo(), "Otro proyecto", ALCANCE, cita, cliente, servicio, equipo, admin, HOY, HOY.plusDays(30)))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("cita_id");
        }

        @Test
        @DisplayName("Valor límite de la entrega: 13 días después del inicio la rechaza la base (ck_proyectos_fechas); 14 pasa")
        void entregaDosSemanasDespues() {
            LocalDate inicio = LocalDate.of(2031, 3, 1);
            crear(inicio, inicio.plusDays(14));

            rechaza(() -> crear(inicio, inicio.plusDays(13)), "ck_proyectos_fechas");
        }

        @Test
        @DisplayName("Nombre de 4 caracteres y alcance de 19: los rechaza la base aunque el servicio falle")
        void textosCortos() {
            rechaza(() -> repo.insertar(repo.siguienteCodigo(), "abcd", ALCANCE, UUID.randomUUID(), cliente, servicio, equipo, admin, HOY, HOY.plusDays(30)), "ck_proyectos_nombre");
            rechaza(() -> repo.insertar(repo.siguienteCodigo(), "Inventario", "x".repeat(19), UUID.randomUUID(), cliente, servicio, equipo, admin, HOY, HOY.plusDays(30)), "ck_proyectos_alcance");
        }

        @Test
        @DisplayName("copiarPlantilla: copia las fases y los pasos de la plantilla del servicio, todos PENDIENTES (RN-06, RN-27)")
        void copiaLaPlantilla() {
            UUID id = crear();
            int esperadas = jdbc.queryForObject("SELECT count(*) FROM plantilla_fases WHERE servicio_id = ?", Integer.class, servicio);
            int pasosEsperados = jdbc.queryForObject("SELECT count(*) FROM plantilla_pasos pp JOIN plantilla_fases pf ON pf.id = pp.plantilla_fase_id WHERE pf.servicio_id = ?", Integer.class, servicio);

            int fases = repo.copiarPlantilla(id, servicio);

            assertThat(fases).isEqualTo(esperadas).isGreaterThanOrEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM proyecto_fases WHERE proyecto_id = ? AND estado = 'PENDIENTE'", Integer.class, id)).isEqualTo(fases);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pasos s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id WHERE f.proyecto_id = ? AND s.estado = 'PENDIENTE'
                    """, Integer.class, id)).isEqualTo(pasosEsperados);
        }

        @Test
        @DisplayName("Un cambio posterior en la plantilla no afecta a un proyecto ya creado (RN-27)")
        void laPlantillaNoSeComparte() {
            UUID id = crear();
            repo.copiarPlantilla(id, servicio);
            int antes = leer(id).fases();

            new PlantillasJdbc(jdbc).reemplazar(servicio, admin, List.of(fase("Única fase nueva", true, "Un paso")));

            assertThat(leer(id).fases()).isEqualTo(antes);
        }

        @Test
        @DisplayName("bloquear: un código que no existe no falla (no bloquea nada)")
        void bloquearInexistente() {
            proyectos.bloquear("P-9999999");
        }
    }

    @Nested
    @DisplayName("ProyectosJdbc: avance, fase actual y «en riesgo» (HU-17; RN-08)")
    class ListaYRiesgo {

        @Test
        @DisplayName("El avance lo calcula la base: 100 × fases aprobadas / fases del plan, sin contar las quitadas (RN-08)")
        void avanceCalculadoPorLaBase() {
            UUID id = crear();
            for (int orden = 1; orden <= 5; orden++) {
                jdbc.update("INSERT INTO proyecto_fases (proyecto_id, orden, nombre, aprueba_cliente, estado, aprobada_at) VALUES (?, ?, ?, true, ?::estado_fase, CASE WHEN ?::text = 'APROBADA' THEN now() END)",
                        id, orden, "Fase " + orden, orden <= 2 ? "APROBADA" : orden == 5 ? "QUITADA" : "PENDIENTE", orden <= 2 ? "APROBADA" : "");
            }

            ProyectoFila p = leer(id);

            assertThat(p.avance()).as("2 de 4 fases (la quitada no cuenta)").isEqualByComparingTo("50.00");
            assertThat(p.fases()).isEqualTo(4);
        }

        @Test
        @DisplayName("La fase actual es la primera que no está aprobada ni quitada")
        void faseActual() {
            UUID id = crear();
            jdbc.update("INSERT INTO proyecto_fases (proyecto_id, orden, nombre, estado, aprobada_at) VALUES (?, 1, 'Diseño', 'APROBADA', now())", id);
            jdbc.update("INSERT INTO proyecto_fases (proyecto_id, orden, nombre, estado) VALUES (?, 2, 'Construcción', 'EN_CURSO')", id);
            jdbc.update("INSERT INTO proyecto_fases (proyecto_id, orden, nombre, estado) VALUES (?, 3, 'Entrega', 'PENDIENTE')", id);

            ProyectoFila p = leer(id);

            assertThat(p.faseActualOrden()).isEqualTo(2);
            assertThat(p.faseActualNombre()).isEqualTo("Construcción");
        }

        @Test
        @DisplayName("Riesgo ENTREGA_VENCIDA: un proyecto activo cuya entrega ya pasó")
        void entregaVencida() {
            UUID id = crear(HOY.minusDays(60), HOY.minusDays(1));

            assertThat(leer(id).riesgo()).isEqualTo("ENTREGA_VENCIDA");
        }

        @Test
        @DisplayName("Riesgo POCO_AVANCE: faltan 5 días hábiles o menos para la entrega y el avance es menor al 80 %")
        void pocoAvance() {
            UUID id = crear(HOY.minusDays(60), HOY.plusDays(3));

            assertThat(leer(id).riesgo()).isEqualTo("POCO_AVANCE");
        }

        @Test
        @DisplayName("Un proyecto al día (entrega lejana, plan en borrador) no está en riesgo")
        void alDia() {
            assertThat(leer(crear()).riesgo()).isNull();
        }

        @Test
        @DisplayName("Un proyecto PAUSADO no está en riesgo aunque su entrega ya pasó: el riesgo solo se calcula a los activos")
        void pausadoNoEstaEnRiesgo() {
            UUID id = crear(HOY.minusDays(60), HOY.minusDays(1));
            repo.cambiarEstado(id, EstadoProyecto.PAUSADO, "Pausa por falta de pago del cliente.");

            assertThat(leer(id).riesgo()).isNull();
        }

        @Test
        @DisplayName("Riesgo PLAN_SIN_RESPUESTA: plan enviado cuyo plazo de respuesta ya pasó")
        void planSinRespuesta() {
            UUID id = conPlan();
            repo.enviarPlan(id, new BigDecimal("5000000"), CONDICIONES, HOY.plusDays(60), HOY.minusDays(1), HOY.plusDays(9));

            assertThat(leer(id).riesgo()).isEqualTo("PLAN_SIN_RESPUESTA");
        }

        @Test
        @DisplayName("Riesgo SIN_AVANCES: plan aprobado y 7 días hábiles o más sin pasos hechos (se mide desde la aprobación)")
        void sinAvances() {
            UUID id = conPlan();
            enviar(id);
            repo.aprobarPlan(id);

            ProyectoFila hoyMismo = proyectos.porCodigo(codigo(id), HOY).orElseThrow();
            ProyectoFila enUnMes = proyectos.porCodigo(codigo(id), HOY.plusDays(30)).orElseThrow();

            assertThat(hoyMismo.riesgo()).as("recién aprobado").isNull();
            assertThat(enUnMes.riesgo()).as("un mes después sin avances").isEqualTo("SIN_AVANCES");
        }

        @Test
        @DisplayName("todos: los activos primero, luego los pausados; dentro de cada grupo por fecha de entrega")
        void orden() {
            UUID pausado = crear(HOY.minusDays(60), HOY.plusDays(10));
            repo.cambiarEstado(pausado, EstadoProyecto.PAUSADO, "Pausa por falta de pago del cliente.");
            UUID tarde = crear(HOY.minusDays(60), HOY.plusDays(90));
            UUID pronto = crear(HOY.minusDays(60), HOY.plusDays(20));

            List<UUID> ids = proyectos.todos(HOY).stream().map(ProyectoFila::id).toList();

            assertThat(ids.indexOf(pronto)).isLessThan(ids.indexOf(tarde));
            assertThat(ids.indexOf(tarde)).isLessThan(ids.indexOf(pausado));
        }
    }

    @Nested
    @DisplayName("ProyectosJdbc: editar, estado y cambios de plan (HU-17; D-33)")
    class Gestion {

        @Test
        @DisplayName("actualizarDatos y cambiarEquipo guardan lo nuevo y suben la versión (bloqueo optimista)")
        void actualizaYSubeLaVersion() {
            UUID id = crear();
            int antes = jdbc.queryForObject("SELECT version FROM proyectos WHERE id = ?", Integer.class, id);
            UUID otroEquipo = UUID.randomUUID();

            repo.actualizarDatos(id, "Nombre nuevo del proyecto", ALCANCE, HOY.plusDays(70));
            repo.cambiarEquipo(id, otroEquipo);

            ProyectoFila p = leer(id);
            assertThat(p.nombre()).isEqualTo("Nombre nuevo del proyecto");
            assertThat(p.fechaEntrega()).isEqualTo(HOY.plusDays(70));
            assertThat(p.equipoId()).isEqualTo(otroEquipo);
            assertThat(jdbc.queryForObject("SELECT version FROM proyectos WHERE id = ?", Integer.class, id)).isEqualTo(antes + 2);
        }

        @Test
        @DisplayName("Pausar guarda el motivo; reanudar vuelve a ACTIVO y borra el motivo")
        void pausarYReanudar() {
            UUID id = crear();

            repo.cambiarEstado(id, EstadoProyecto.PAUSADO, "Pausa por falta de pago del cliente.");
            assertThat(leer(id).estado()).isEqualTo("PAUSADO");
            assertThat(leer(id).motivoEstado()).isEqualTo("Pausa por falta de pago del cliente.");

            repo.reanudar(id, equipo, false);
            assertThat(leer(id).estado()).isEqualTo("ACTIVO");
            assertThat(leer(id).motivoEstado()).isNull();
        }

        @Test
        @DisplayName("Un motivo de menos de 10 caracteres lo rechaza la base (ck_proyectos_motivo)")
        void motivoCorto() {
            UUID id = crear();

            rechaza(() -> repo.cambiarEstado(id, EstadoProyecto.PAUSADO, "corto"), "ck_proyectos_motivo");
        }

        @Test
        @DisplayName("Cambio de fecha: nace AUTORIZADO, hayCambioDePlanAbierto lo ve y no caben dos cambios abiertos a la vez (RN-30)")
        void cambioDeFecha() {
            // Solo se cambia el plan de un proyecto activo con el plan aprobado (RN-30): la base lo exige
            UUID id = conPlan();
            rechaza(() -> cambiosRepo.insertarCambioDeFecha(id, HOY.plusDays(90), "El cliente necesita la entrega una semana después.", admin, java.time.Instant.now()), "plan aprobado");
            enviar(id);
            repo.aprobarPlan(id);
            assertThat(cambiosRepo.hayCambioDePlanAbierto(id)).isFalse();

            UUID cambio = cambiosRepo.insertarCambioDeFecha(id, HOY.plusDays(90), "El cliente necesita la entrega una semana después.", admin, java.time.Instant.now());

            assertThat(cambiosRepo.hayCambioDePlanAbierto(id)).isTrue();
            assertThat(jdbc.queryForObject("SELECT estado::text FROM cambios_plan WHERE id = ?", String.class, cambio)).isEqualTo("AUTORIZADO");
            rechaza(() -> cambiosRepo.insertarCambioDeFecha(id, HOY.plusDays(95), "Otro cambio de fecha mientras hay uno abierto.", admin, java.time.Instant.now()), "cambios_plan");
        }
    }

    // ------------------------------------------------------------------------------------------------ planes

    @Nested
    @DisplayName("PlanesJdbc: editar el borrador (HU-28; RN-27)")
    class Borrador {

        @Test
        @DisplayName("reemplazarFases y fases: lo guardado se lee numerado en el orden de la lista, solo con los pasos del plan")
        void guardaYLee() {
            UUID id = crear();

            planes.reemplazarFases(id, planValido());
            List<FasePlantilla> fases = planes.fases(id);

            assertThat(fases).extracting(FasePlantilla::nombre).containsExactly("Diseño", "Entrega");
            assertThat(fases).extracting(FasePlantilla::orden).containsExactly(1, 2);
            assertThat(fases.get(0).pasos()).extracting(PasoPlantilla::titulo).containsExactly("Reunir requisitos", "Dibujar pantallas");
            assertThat(fases.get(1).apruebaCliente()).isTrue();
        }

        @Test
        @DisplayName("Volver a guardar reemplaza todo y no deja restos; las correcciones y los pasos quitados no aparecen como pasos del plan")
        void reemplaza() {
            UUID id = conPlan();
            UUID fase = jdbc.queryForObject("SELECT id FROM proyecto_fases WHERE proyecto_id = ? AND orden = 1", UUID.class, id);
            jdbc.update("INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion, origen) VALUES (?, 9, 'CORRECCION', 'Una corrección', 'El reporte mostraba mal el total con devoluciones.', 'La encontramos en pruebas')", fase);
            assertThat(planes.fases(id).get(0).pasos()).as("la corrección no es un paso del plan").hasSize(2);

            planes.reemplazarFases(id, List.of(fase("Solo diseño", false, "Un paso"), fase("Solo entrega", true, "Otro paso")));

            assertThat(planes.fases(id)).extracting(FasePlantilla::nombre).containsExactly("Solo diseño", "Solo entrega");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM proyecto_fases WHERE proyecto_id = ?", Integer.class, id)).isEqualTo(2);
        }

        @Test
        @DisplayName("Dos fases con el mismo nombre (sin distinguir mayúsculas) las rechaza el índice ux_pf_nombre")
        void nombreRepetido() {
            UUID id = crear();

            rechaza(() -> planes.reemplazarFases(id, List.of(fase("Diseño", false, "Un paso"), fase("DISEÑO", true, "Otro paso"))), "ux_pf_nombre");
        }

        @Test
        @DisplayName("guardarCotizacion: guarda el valor y las condiciones; la fecha vacía no se toca (es obligatoria en la tabla)")
        void guardaLaCotizacion() {
            UUID id = crear();
            LocalDate entregaOriginal = leer(id).fechaEntrega();

            repo.guardarCotizacion(id, new BigDecimal("5000000"), CONDICIONES, null);

            PlanesJdbc.Cotizacion c = planes.cotizacion(id);
            assertThat(c.valor()).isEqualByComparingTo("5000000");
            assertThat(c.condiciones()).isEqualTo(CONDICIONES);
            assertThat(c.entrega()).isEqualTo(entregaOriginal);
            assertThat(c.enviadoAt()).isNull();
            assertThat(c.aprobadoAt()).isNull();
        }

        @Test
        @DisplayName("Valores límite del valor (RN-29): $999.999 y $200.000.001 los rechaza la base; $1.000.000 y $200.000.000 pasan")
        void limitesDelValor() {
            UUID id = crear();

            repo.guardarCotizacion(id, new BigDecimal("1000000"), CONDICIONES, null);
            repo.guardarCotizacion(id, new BigDecimal("200000000"), CONDICIONES, null);
            rechaza(() -> repo.guardarCotizacion(id, new BigDecimal("999999"), CONDICIONES, null), "ck_proyectos_valor");
            rechaza(() -> repo.guardarCotizacion(id, new BigDecimal("200000001"), CONDICIONES, null), "ck_proyectos_valor");
        }
    }

    @Nested
    @DisplayName("PlanesJdbc: el recorrido del plan (HU-28, HU-29; RN-29)")
    class Recorrido {

        @Test
        @DisplayName("enviar: el estado y las cuatro fechas cambian en una sola sentencia, y el plan queda cerrado a cambios")
        void envia() {
            UUID id = conPlan();

            enviar(id);

            ProyectoFila p = leer(id);
            assertThat(p.estadoPlan()).isEqualTo("ENVIADO");
            assertThat(p.valorCotizado()).isEqualByComparingTo("5000000");
            assertThat(p.planPlazoRespuesta()).isEqualTo(HOY.plusDays(3));
            assertThat(p.planVenceEl()).isEqualTo(HOY.plusDays(14));
            assertThat(planes.cotizacion(id).enviadoAt()).isNotNull();
            assertThat(jdbc.queryForObject("SELECT plan_recordado_at IS NULL AND plan_cancelacion_avisada_at IS NULL FROM proyectos WHERE id = ?", Boolean.class, id)).isTrue();
        }

        @Test
        @DisplayName("No se envía un plan que rompe RN-28: con una sola fase lo rechaza la base al cambiar el estado")
        void noEnviaUnPlanInvalido() {
            UUID id = crear();
            planes.reemplazarFases(id, List.of(fase("Entrega", true, "Capacitar")));

            rechaza(() -> enviar(id), "de 2 a 8 fases");
        }

        @Test
        @DisplayName("No se envía sin cotización completa: sin valor lo rechaza ck_proyectos_enviado")
        void noEnviaSinCotizacion() {
            UUID id = conPlan();

            rechaza(() -> repo.enviarPlan(id, null, CONDICIONES, HOY.plusDays(60), HOY.plusDays(3), HOY.plusDays(14)), "ck_proyectos_enviado");
        }

        @Test
        @DisplayName("El vencimiento debe ser posterior al plazo de respuesta (ck_proyectos_vence)")
        void venceDespuesDelPlazo() {
            UUID id = conPlan();

            rechaza(() -> repo.enviarPlan(id, new BigDecimal("5000000"), CONDICIONES, HOY.plusDays(60), HOY.plusDays(10), HOY.plusDays(10)), "ck_proyectos_vence");
        }

        @Test
        @DisplayName("Con el plan ENVIADO sus fases ya no se tocan: la base rechaza cambiarlas (D-26)")
        void planEnviadoNoSeEdita() {
            UUID id = conPlan();
            enviar(id);

            rechaza(() -> planes.reemplazarFases(id, List.of(fase("Nueva", false, "Paso"), fase("Otra", true, "Paso"))), "plan");
        }

        @Test
        @DisplayName("Un plan en borrador no se aprueba: «Solo un plan enviado se aprueba» (RN-29)")
        void noSeApruebaUnBorrador() {
            UUID id = conPlan();

            rechaza(() -> repo.aprobarPlan(id), "Solo un plan enviado");
        }

        @Test
        @DisplayName("aprobar: el plan queda APROBADO con su fecha y iniciarPrimeraFase empieza solo la fase 1, una sola vez (RN-06)")
        void apruebaYEmpiezaLaFase1() {
            UUID id = conPlan();
            enviar(id);

            repo.aprobarPlan(id);
            planes.iniciarPrimeraFase(id);
            planes.iniciarPrimeraFase(id);

            assertThat(leer(id).estadoPlan()).isEqualTo("APROBADO");
            assertThat(planes.cotizacion(id).aprobadoAt()).isNotNull();
            assertThat(estadoFase(id, 1)).isEqualTo("EN_CURSO");
            assertThat(estadoFase(id, 2)).isEqualTo("PENDIENTE");
        }

        @Test
        @DisplayName("Pedir ajustes deja el plan en AJUSTES_PEDIDOS y el proyecto ACTIVO; el equipo puede editarlo de nuevo")
        void ajustes() {
            UUID id = conPlan();
            enviar(id);

            repo.cambiarEstadoPlan(id, EstadoPlan.AJUSTES_PEDIDOS);

            ProyectoFila p = leer(id);
            assertThat(p.estadoPlan()).isEqualTo("AJUSTES_PEDIDOS");
            assertThat(p.estado()).isEqualTo("ACTIVO");
            planes.reemplazarFases(id, planValido());
        }

        @Test
        @DisplayName("Rechazar o vencer pausa el proyecto en la misma operación (libera el cupo) y deja un motivo automático (RN-29)")
        void rechazarOVencerPausa() {
            UUID rechazado = conPlan();
            enviar(rechazado);
            UUID vencido = conPlan();
            enviar(vencido);

            repo.cambiarEstadoPlan(rechazado, EstadoPlan.RECHAZADO);
            repo.cambiarEstadoPlan(vencido, EstadoPlan.VENCIDO);

            assertThat(leer(rechazado).estado()).isEqualTo("PAUSADO");
            assertThat(leer(rechazado).motivoEstado()).contains("rechazó");
            assertThat(leer(vencido).estado()).isEqualTo("PAUSADO");
            assertThat(leer(vencido).motivoEstado()).contains("venció");
        }

        @Test
        @DisplayName("Reanudar una propuesta rechazada la devuelve a BORRADOR con el proyecto ACTIVO y borra sus fechas de envío (RN-29)")
        void reanudaUnaPropuestaRechazada() {
            UUID id = conPlan();
            enviar(id);
            repo.cambiarEstadoPlan(id, EstadoPlan.RECHAZADO);

            repo.reanudar(id, equipo, true);

            ProyectoFila p = leer(id);
            assertThat(p.estado()).isEqualTo("ACTIVO");
            assertThat(p.estadoPlan()).isEqualTo("BORRADOR");
            assertThat(p.planPlazoRespuesta()).isNull();
            assertThat(p.planVenceEl()).isNull();
            assertThat(planes.cotizacion(id).enviadoAt()).isNull();
        }

        @Test
        @DisplayName("Un plan enviado no vuelve a borrador por su cuenta: lo rechaza la base")
        void enviadoNoVuelveABorrador() {
            UUID id = conPlan();
            enviar(id);

            rechaza(() -> repo.cambiarEstadoPlan(id, EstadoPlan.BORRADOR), "no vuelve a borrador");
        }
    }

    @Nested
    @DisplayName("PlanesJdbc: respuestas del cliente y tarea diaria (HU-29)")
    class Respuestas {

        @Test
        @DisplayName("insertarRespuesta y ultimaRespuesta: guardan y leen el comentario por tipo; sin respuesta de ese tipo da vacío")
        void guardaYLee() {
            UUID id = conPlan();
            enviar(id);

            UUID respuesta = planes.insertarRespuesta(id, cliente, "AJUSTES_PEDIDOS", "Quiero el logo en el diseño del sistema.");

            var leida = planes.ultimaRespuesta(id, "AJUSTES_PEDIDOS");
            assertThat(leida).isPresent();
            assertThat(leida.get().id()).isEqualTo(respuesta);
            assertThat(leida.get().comentario()).isEqualTo("Quiero el logo en el diseño del sistema.");
            assertThat(planes.ultimaRespuesta(id, "RECHAZADO")).isEmpty();
        }

        @Test
        @DisplayName("Aprobar no lleva comentario; pedir ajustes o rechazar exigen de 20 a 600 caracteres (ck_rp_comentario)")
        void comentarioObligatorio() {
            UUID id = conPlan();
            enviar(id);

            planes.insertarRespuesta(id, cliente, "APROBADO", null);
            planes.insertarRespuesta(id, cliente, "RECHAZADO", "x".repeat(20));
            rechaza(() -> planes.insertarRespuesta(id, cliente, "RECHAZADO", "x".repeat(19)), "ck_rp_comentario");
            rechaza(() -> planes.insertarRespuesta(id, cliente, "AJUSTES_PEDIDOS", null), "ck_rp_comentario");
            // 601 caracteres ni siquiera caben en la columna varchar(600): la rechaza antes que el CHECK
            rechaza(() -> planes.insertarRespuesta(id, cliente, "AJUSTES_PEDIDOS", "x".repeat(601)), "character varying(600)");
        }

        @Test
        @DisplayName("Las respuestas son historial: la base no deja editarlas ni borrarlas (RNF-02)")
        void historialInmutable() {
            UUID id = conPlan();
            enviar(id);
            UUID respuesta = planes.insertarRespuesta(id, cliente, "AJUSTES_PEDIDOS", "Quiero el logo en el diseño del sistema.");

            rechaza(() -> jdbc.update("UPDATE respuestas_plan SET comentario = 'cambiado a escondidas por alguien' WHERE id = ?", respuesta), "respuestas");
            rechaza(() -> jdbc.update("DELETE FROM respuestas_plan WHERE id = ?", respuesta), "respuestas");
        }

        @Test
        @DisplayName("pendientes: planes ENVIADOS de proyectos activos y VENCIDOS o RECHAZADOS de proyectos pausados, con sus marcas")
        void pendientes() {
            UUID enviado = conPlan();
            enviar(enviado);
            UUID rechazado = conPlan();
            enviar(rechazado);
            repo.cambiarEstadoPlan(rechazado, EstadoPlan.RECHAZADO);
            planes.insertarRespuesta(rechazado, cliente, "RECHAZADO", "Es más caro de lo que podemos pagar.");
            UUID borrador = conPlan();

            repo.marcarRecordado(enviado);
            repo.marcarCancelacionAvisada(rechazado);
            List<PlanPendiente> lista = planes.pendientes();

            PlanPendiente a = lista.stream().filter(x -> x.id().equals(enviado)).findFirst().orElseThrow();
            PlanPendiente b = lista.stream().filter(x -> x.id().equals(rechazado)).findFirst().orElseThrow();
            assertThat(a.estadoPlan()).isEqualTo("ENVIADO");
            assertThat(a.recordado()).isTrue();
            assertThat(a.cancelacionAvisada()).isFalse();
            assertThat(a.plazoRespuesta()).isEqualTo(HOY.plusDays(3));
            assertThat(b.estadoPlan()).isEqualTo("RECHAZADO");
            assertThat(b.rechazadoEn()).as("la fecha del rechazo sale de la respuesta del cliente").isNotNull();
            assertThat(b.cancelacionAvisada()).isTrue();
            assertThat(lista).extracting(PlanPendiente::id).doesNotContain(borrador);
        }

        @Test
        @DisplayName("Al reenviar el plan las marcas de aviso se borran para que los avisos vuelvan a salir")
        void alReenviarSeBorranLasMarcas() {
            UUID id = conPlan();
            enviar(id);
            repo.marcarRecordado(id);
            repo.cambiarEstadoPlan(id, EstadoPlan.AJUSTES_PEDIDOS);

            enviar(id);

            assertThat(jdbc.queryForObject("SELECT plan_recordado_at IS NULL FROM proyectos WHERE id = ?", Boolean.class, id)).isTrue();
        }
    }
}
