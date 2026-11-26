package co.coretech.portal.agenda.repository;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lee {@code horario_laboral} (RN-01, RNF-06): los tramos de atención de cada día de la semana, en hora de Colombia. */
@Repository
public class HorarioLaboral {

    /** Un tramo de atención: de {@code inicio} (incluido) a {@code fin} (excluido). */
    public record Tramo(LocalTime inicio, LocalTime fin) {
    }

    private final JdbcTemplate jdbc;

    HorarioLaboral(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Día de la semana (1 = lunes … 5 = viernes) → tramos, en orden. Los días sin fila no se atienden. */
    public Map<Integer, List<Tramo>> tramos() {
        Map<Integer, List<Tramo>> porDia = new HashMap<>();
        jdbc.query("SELECT dia_semana, hora_inicio, hora_fin FROM horario_laboral ORDER BY dia_semana, hora_inicio",
                fila -> {
                    porDia.computeIfAbsent((int) fila.getShort(1), d -> new ArrayList<>())
                            .add(new Tramo(fila.getObject(2, LocalTime.class), fila.getObject(3, LocalTime.class)));
                });
        return porDia;
    }
}
