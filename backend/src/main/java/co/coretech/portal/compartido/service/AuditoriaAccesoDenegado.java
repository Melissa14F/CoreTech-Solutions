package co.coretech.portal.compartido.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;

/**
 * RNF-01: un intento rechazado por falta de permiso (403) queda en la auditoría, categoría «Seguridad».
 * Se guarda en una transacción propia: la petición ya falló y no hay otra que lo acompañe. Si guardarlo
 * falla, solo se anota en el log: el usuario igual recibe su 403.
 */
@Service
public class AuditoriaAccesoDenegado {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAccesoDenegado.class);
    private static final int MAX_RUTA = 200;

    private final RegistroAuditoria auditoria;

    public AuditoriaAccesoDenegado(RegistroAuditoria auditoria) {
        this.auditoria = auditoria;
    }

    /** @param metodo GET, POST…; @param ruta ruta sin parámetros de consulta (pueden traer datos del usuario) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String metodo, String ruta) {
        try {
            String destino = ruta.length() > MAX_RUTA ? ruta.substring(0, MAX_RUTA) : ruta;
            auditoria.registrar(CategoriaAuditoria.SEGURIDAD, "Intentó una acción sin permiso", "endpoint", null,
                    Map.of("solicitud", CambioDeValor.nuevo(metodo + " " + destino)));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar en la auditoría un acceso denegado: {}", e.getMessage());
        }
    }
}
