package co.coretech.portal.acceso.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import co.coretech.portal.ContenedoresPrueba;
import co.coretech.portal.acceso.domain.Rol;
import co.coretech.portal.acceso.domain.TokenRecuperacion;
import co.coretech.portal.acceso.domain.Usuario;

/**
 * Pruebas de PERSISTENCIA y CONSULTAS de {@link UsuarioRepositorio}, {@link RolRepositorio} y
 * {@link TokenRecuperacionRepositorio} con {@code @DataJpaTest} sobre PostgreSQL 15 real (Testcontainers + Flyway): las
 * consultas JPQL y nativas, el mapeo de la entidad (que lo que se guarda es lo que se lee) y las restricciones de la base
 * (correo único sin distinguir mayúsculas, RN-18). Cada prueba corre en una transacción que se revierte.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
class UsuarioRepositorioDataJpaTest {

    static final Instant AHORA = Instant.parse("2031-03-04T13:00:00Z");

    @Autowired
    UsuarioRepositorio usuarios;
    @Autowired
    RolRepositorio roles;
    @Autowired
    TokenRecuperacionRepositorio tokens;
    @Autowired
    TestEntityManager em;

    Rol cliente;
    Rol administrador;

    @BeforeEach
    void rolesDeLaMigracion() {
        cliente = roles.findByCodigo(Rol.CLIENTE).orElseThrow();
        administrador = roles.findByCodigo(Rol.ADMINISTRADOR).orElseThrow();
    }

    /** Un correo único por prueba: la base de pruebas tiene las cuentas de la migración y se comparte entre clases. */
    private String correo(String prefijo) {
        return prefijo + UUID.randomUUID().toString().substring(0, 8) + "@prueba.com";
    }

    private Usuario guardarCliente(String email, Instant creado) {
        Usuario u = Usuario.nuevoCliente(cliente, "Laura Gómez", email, "3001234567", "Salón Laura", "$2a$04$hash", creado);
        usuarios.saveAndFlush(u);
        em.clear();
        return u;
    }

    @Nested
    @DisplayName("Roles")
    class PruebasDeRoles {

        @Test
        @DisplayName("findByCodigo: los tres roles de la migración existen; uno desconocido da vacío")
        void rolesDeLaMigracion() {
            assertThat(roles.findByCodigo("CLIENTE")).isPresent();
            assertThat(roles.findByCodigo("PRODUCT_OWNER")).isPresent();
            assertThat(roles.findByCodigo("ADMINISTRADOR")).isPresent();
            assertThat(roles.findByCodigo("INVENTADO")).isEmpty();
        }

        @Test
        @DisplayName("permisosDelRol: el administrador tiene sus permisos ordenados por código; el cliente no tiene los del administrador")
        void permisosDelRol() {
            List<String> deAdmin = usuarios.permisosDelRol(administrador.getId());
            List<String> deCliente = usuarios.permisosDelRol(cliente.getId());

            assertThat(deAdmin).isNotEmpty().isSorted().contains("seguridad.permisos", "proyectos.gestionar");
            assertThat(deCliente).contains("citas.agendar").doesNotContain("seguridad.permisos", "proyectos.gestionar");
        }
    }

    @Nested
    @DisplayName("Consultas de usuarios")
    class Consultas {

        @Test
        @DisplayName("Lo que se guarda es lo que se lee: todos los campos de la cuenta nueva")
        void guardaYLee() {
            String email = correo("laura");
            Usuario u = guardarCliente(email, AHORA);

            Usuario leido = usuarios.findById(u.getId()).orElseThrow();

            assertThat(leido.getEmail()).isEqualTo(email);
            assertThat(leido.getNombre()).isEqualTo("Laura Gómez");
            assertThat(leido.getCelular()).isEqualTo("3001234567");
            assertThat(leido.getEmpresa()).isEqualTo("Salón Laura");
            assertThat(leido.isActivo()).isTrue();
            assertThat(leido.isDebeCambiarPassword()).isFalse();
            assertThat(leido.getRol().getCodigo()).isEqualTo(Rol.CLIENTE);
            assertThat(leido.getCreadoEn()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("buscarPorEmail: no distingue mayúsculas y trae el rol ya cargado (sin LazyInitializationException)")
        void buscarPorEmailSinMayusculas() {
            String email = correo("laura");
            Usuario u = guardarCliente(email, AHORA);

            Usuario encontrado = usuarios.buscarPorEmail(email.toUpperCase()).orElseThrow();

            assertThat(encontrado.getId()).isEqualTo(u.getId());
            assertThat(Hibernate.isInitialized(encontrado.getRol())).isTrue();
            assertThat(usuarios.buscarPorEmail("nadie@prueba.com")).isEmpty();
        }

        @Test
        @DisplayName("existeEmail: verdadero con cualquier combinación de mayúsculas; falso si no existe")
        void existeEmail() {
            String email = correo("ana");
            guardarCliente(email, AHORA);

            assertThat(usuarios.existeEmail(email)).isTrue();
            assertThat(usuarios.existeEmail(email.toUpperCase())).isTrue();
            assertThat(usuarios.existeEmail(correo("otra"))).isFalse();
        }

        @Test
        @DisplayName("buscarConRol: por id, con el rol cargado; un id que no existe da vacío")
        void buscarConRol() {
            Usuario u = guardarCliente(correo("laura"), AHORA);

            Usuario encontrado = usuarios.buscarConRol(u.getId()).orElseThrow();

            assertThat(Hibernate.isInitialized(encontrado.getRol())).isTrue();
            assertThat(encontrado.getRol().getCodigo()).isEqualTo("CLIENTE");
            assertThat(usuarios.buscarConRol(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("idsActivosDelRol: solo las cuentas activas de ese rol, de la más antigua a la más nueva")
        void idsActivosDelRol() {
            Usuario vieja = guardarCliente(correo("vieja"), AHORA.minusSeconds(1000));
            Usuario nueva = guardarCliente(correo("nueva"), AHORA);
            Usuario inactiva = guardarCliente(correo("inactiva"), AHORA.minusSeconds(500));
            Usuario inactivaGuardada = usuarios.findById(inactiva.getId()).orElseThrow();
            inactivaGuardada.cambiarActivo(false, AHORA);
            usuarios.saveAndFlush(inactivaGuardada);

            List<UUID> ids = usuarios.idsActivosDelRol(Rol.CLIENTE);

            assertThat(ids).contains(vieja.getId(), nueva.getId()).doesNotContain(inactiva.getId());
            assertThat(ids.indexOf(vieja.getId())).isLessThan(ids.indexOf(nueva.getId()));
            assertThat(usuarios.idsActivosDelRol("ROL_QUE_NO_EXISTE")).isEmpty();
        }

        @Test
        @DisplayName("listarConRol: trae las cuentas con su rol ya cargado")
        void listarConRol() {
            Usuario u = guardarCliente(correo("laura"), AHORA);

            List<Usuario> todos = usuarios.listarConRol();

            assertThat(todos).extracting(Usuario::getId).contains(u.getId());
            assertThat(todos).allSatisfy(x -> assertThat(Hibernate.isInitialized(x.getRol())).isTrue());
        }

        @Test
        @DisplayName("Los intentos fallidos y el bloqueo de 15 minutos sobreviven a guardar y leer (RNF-10)")
        void persisteElBloqueo() {
            Usuario u = guardarCliente(correo("laura"), AHORA);
            Usuario cargado = usuarios.findById(u.getId()).orElseThrow();
            for (int i = 0; i < 5; i++) {
                cargado.registrarIntentoFallido(AHORA);
            }
            usuarios.saveAndFlush(cargado);
            em.clear();

            Usuario leido = usuarios.findById(u.getId()).orElseThrow();

            assertThat(leido.getIntentosFallidos()).isEqualTo(5);
            assertThat(leido.getBloqueadoHasta()).isEqualTo(AHORA.plus(Usuario.BLOQUEO));
            assertThat(leido.estaBloqueada(AHORA)).isTrue();
            assertThat(leido.estaBloqueada(AHORA.plus(Usuario.BLOQUEO))).isFalse();
        }

        @Test
        @DisplayName("Cambiar la contraseña sube la versión de sesión en la base (cierra las sesiones abiertas)")
        void versionDeSesion() {
            Usuario u = guardarCliente(correo("laura"), AHORA);
            Usuario cargado = usuarios.findById(u.getId()).orElseThrow();
            cargado.cambiarPassword("$2a$04$nuevo", AHORA);
            usuarios.saveAndFlush(cargado);
            em.clear();

            assertThat(usuarios.findById(u.getId()).orElseThrow().getVersionSesion()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Restricciones de la base (RN-18)")
    class Restricciones {

        @Test
        @DisplayName("El correo es único sin distinguir mayúsculas: el índice ux_usuarios_email_ci rechaza el segundo")
        void correoUnico() {
            String email = correo("laura");
            guardarCliente(email, AHORA);

            assertThatThrownBy(() -> usuarios.saveAndFlush(Usuario.nuevoCliente(cliente, "Otra Persona", email.toUpperCase(), "3009876543", null, "$2a$04$x", AHORA)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ux_usuarios_email_ci");
        }

        @Test
        @DisplayName("Un celular que no empieza por 3 o no tiene 10 dígitos lo rechaza la base aunque el servicio falle")
        void celularInvalido() {
            assertThatThrownBy(() -> usuarios.saveAndFlush(Usuario.nuevoCliente(cliente, "Laura Gómez", correo("laura"), "2001234567", null, "$2a$04$x", AHORA)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Enlaces de recuperación (RNF-10, RNF-09)")
    class Recuperacion {

        private TokenRecuperacion token(UUID usuario, String huella, Instant creado) {
            TokenRecuperacion t = TokenRecuperacion.nuevo(usuario, huella, creado);
            tokens.saveAndFlush(t);
            return t;
        }

        @Test
        @DisplayName("findByTokenHash: encuentra el enlace por su huella; una huella desconocida da vacío")
        void buscaPorHuella() {
            Usuario u = guardarCliente(correo("laura"), AHORA);
            String huella = "h" + UUID.randomUUID().toString().replace("-", "");
            token(u.getId(), huella, AHORA);
            em.clear();

            TokenRecuperacion encontrado = tokens.findByTokenHash(huella).orElseThrow();

            assertThat(encontrado.getUsuarioId()).isEqualTo(u.getId());
            assertThat(encontrado.esValido(AHORA)).isTrue();
            assertThat(encontrado.esValido(AHORA.plus(TokenRecuperacion.VIGENCIA))).as("vale 30 minutos exactos").isFalse();
            assertThat(tokens.findByTokenHash("huella-que-no-existe")).isEmpty();
        }

        @Test
        @DisplayName("La huella es única: dos enlaces con la misma no caben")
        void huellaUnica() {
            Usuario u = guardarCliente(correo("laura"), AHORA);
            String huella = "u" + UUID.randomUUID().toString().replace("-", "");
            token(u.getId(), huella, AHORA);

            assertThatThrownBy(() -> token(u.getId(), huella, AHORA)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("borrarPendientes: borra solo los enlaces sin usar de esa cuenta; los usados y los de otra cuenta se quedan")
        void borrarPendientes() {
            Usuario laura = guardarCliente(correo("laura"), AHORA);
            Usuario ana = guardarCliente(correo("ana"), AHORA);
            String h1 = "p1" + UUID.randomUUID().toString().replace("-", "");
            String h2 = "p2" + UUID.randomUUID().toString().replace("-", "");
            String h3 = "p3" + UUID.randomUUID().toString().replace("-", "");
            token(laura.getId(), h1, AHORA);
            TokenRecuperacion usado = TokenRecuperacion.nuevo(laura.getId(), h2, AHORA);
            usado.usar(AHORA);
            tokens.saveAndFlush(usado);
            token(ana.getId(), h3, AHORA);

            tokens.borrarPendientes(laura.getId());
            em.flush();
            em.clear();

            assertThat(tokens.findByTokenHash(h1)).as("el pendiente de Laura se borró").isEmpty();
            assertThat(tokens.findByTokenHash(h2)).as("el ya usado se conserva").isPresent();
            assertThat(tokens.findByTokenHash(h3)).as("el de otra cuenta se conserva").isPresent();
        }

        @Test
        @DisplayName("borrarUsadosOVencidos (conservación de datos): borra los usados y los vencidos, deja los vigentes y devuelve cuántos")
        void conservacionDeDatos() {
            Usuario u = guardarCliente(correo("laura"), AHORA);
            String vigente = "v" + UUID.randomUUID().toString().replace("-", "");
            String vencido = "e" + UUID.randomUUID().toString().replace("-", "");
            String usado = "s" + UUID.randomUUID().toString().replace("-", "");
            token(u.getId(), vigente, AHORA);
            token(u.getId(), vencido, AHORA.minus(TokenRecuperacion.VIGENCIA).minusSeconds(60));
            TokenRecuperacion t = TokenRecuperacion.nuevo(u.getId(), usado, AHORA);
            t.usar(AHORA);
            tokens.saveAndFlush(t);

            int borrados = tokens.borrarUsadosOVencidos(AHORA);
            em.clear();

            assertThat(borrados).isGreaterThanOrEqualTo(2);
            assertThat(tokens.findByTokenHash(vigente)).isPresent();
            assertThat(tokens.findByTokenHash(vencido)).isEmpty();
            assertThat(tokens.findByTokenHash(usado)).isEmpty();
        }
    }
}
