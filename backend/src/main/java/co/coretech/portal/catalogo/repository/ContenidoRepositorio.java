package co.coretech.portal.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.coretech.portal.catalogo.domain.ContenidoSitio;

public interface ContenidoRepositorio extends JpaRepository<ContenidoSitio, String> {

    /** La publicación más reciente (para "Última publicación: … por …"). */
    List<ContenidoSitio> findTop1ByActualizadoPorIsNotNullOrderByActualizadoEnDesc();
}
