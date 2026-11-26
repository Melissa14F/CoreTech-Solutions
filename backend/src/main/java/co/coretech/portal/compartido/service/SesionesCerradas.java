package co.coretech.portal.compartido.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import co.coretech.portal.compartido.repository.SesionCerradaRepositorio;

/**
 * Las sesiones que se cerraron con «Cerrar sesión» (RNF-10): su {@code jti} se guarda hasta que el token venza y mientras
 * tanto el token deja de valer aunque alguien lo haya copiado. Es el paso entre la capa web (que lee y escribe la cookie)
 * y la base de datos: la web no toca los repositorios.
 */
@Service
public class SesionesCerradas {

    private final SesionCerradaRepositorio cerradas;

    SesionesCerradas(SesionCerradaRepositorio cerradas) {
        this.cerradas = cerradas;
    }

    /** Marca el token como cerrado hasta que venza; de paso borra los ya vencidos. */
    public void cerrar(UUID jti, Instant venceEn, Instant ahora) {
        cerradas.cerrar(jti, venceEn, ahora);
    }

    public boolean estaCerrada(UUID jti) {
        return cerradas.estaCerrada(jti);
    }
}
