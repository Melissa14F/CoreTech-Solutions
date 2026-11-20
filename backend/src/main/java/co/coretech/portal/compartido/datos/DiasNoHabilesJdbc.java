package co.coretech.portal.compartido.datos;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lee los festivos con SQL (solo lectura; sin entidad JPA, porque la tabla es del módulo agenda). */
@Repository
class DiasNoHabilesJdbc implements DiasNoHabilesRepositorio {

    private final JdbcTemplate jdbc;

    DiasNoHabilesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<LocalDate> entre(LocalDate desde, LocalDate hasta) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT fecha FROM dias_no_habiles WHERE fecha BETWEEN ? AND ?", LocalDate.class, desde, hasta));
    }
}
