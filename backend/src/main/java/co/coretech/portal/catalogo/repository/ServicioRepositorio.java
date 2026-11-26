package co.coretech.portal.catalogo.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import co.coretech.portal.catalogo.domain.Servicio;

public interface ServicioRepositorio extends JpaRepository<Servicio, UUID> {

    List<Servicio> findAllByOrderByOrdenAsc();

    List<Servicio> findByVisibleTrueOrderByOrdenAsc();

    /** Nombre único sin distinguir mayúsculas (servicios_nombre_key lo exige exacto; el servicio, también sin mayúsculas). */
    boolean existsByNombreIgnoreCase(String nombre);

    /** Igual, sin contar el servicio que se está editando. */
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

    boolean existsBySlug(String slug);

    @Query(value = "SELECT coalesce(max(orden), 0)::int FROM servicios", nativeQuery = true)
    int ultimaPosicion();
}
