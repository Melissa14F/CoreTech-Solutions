package co.coretech.portal.compartido.repository;

import java.time.LocalDate;
import java.util.Set;

/**
 * Festivos de {@code dias_no_habiles} que usa {@code CalendarioHabil}.
 * <p>
 * La tabla la administra el módulo agenda (RNF-06: los festivos se cambian desde el panel); aquí
 * solo se lee, para que {@code compartido} no dependa de ningún módulo de negocio.
 */
public interface DiasNoHabilesRepositorio {

    /** Festivos entre {@code desde} y {@code hasta}, ambos incluidos. */
    Set<LocalDate> entre(LocalDate desde, LocalDate hasta);
}
