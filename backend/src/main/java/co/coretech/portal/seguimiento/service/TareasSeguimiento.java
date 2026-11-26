package co.coretech.portal.seguimiento.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.compartido.service.AlmacenArchivos;
import co.coretech.portal.seguimiento.ProyectosParaSeguimiento;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.AdjuntoPorBorrar;
import co.coretech.portal.seguimiento.repository.SeguimientoJdbc.FaseEnRevision;

/**
 * Tarea diaria del seguimiento de los proyectos (HU-11, HU-13; RN-13, RN-17, RN-31, RNF-09):
 * <ul>
 *   <li>Fases EN_REVISION: a los 2 días hábiles de entregada, un recordatorio al cliente (una sola vez, marca
 *       {@code plazo_recordado_at}); pasado el plazo de 3 días hábiles, la fase se aprueba sola y queda registrada como
 *       automática.</li>
 *   <li>Garantías: cumplidos los 30 días y sin correcciones abiertas, el proyecto queda FINALIZADO. Con correcciones abiertas
 *       sigue en garantía hasta que se termine la última (eso lo hace {@code ServicioFases} al terminarla).</li>
 *   <li>Archivos adjuntos de proyectos finalizados o cancelados hace más de 12 meses: se borra el archivo del volumen y el
 *       registro queda con {@code archivo_eliminado_at}.</li>
 * </ul>
 * Corre todos los días, también fines de semana: los plazos de la garantía son de días calendario.
 */
@Service
public class TareasSeguimiento {

    private static final Logger LOG = LoggerFactory.getLogger(TareasSeguimiento.class);

    private final SeguimientoJdbc datos;
    private final ServicioFases fases;
    private final ProyectosParaSeguimiento proyectos;
    private final AlmacenArchivos almacen;
    private final Clock reloj;

    public TareasSeguimiento(SeguimientoJdbc datos, ServicioFases fases, ProyectosParaSeguimiento proyectos, AlmacenArchivos almacen,
            Clock reloj) {
        this.datos = datos;
        this.fases = fases;
        this.proyectos = proyectos;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    /**
     * Corre sola cada día. Lleva su propia transacción: llamar a {@link #revisar()} desde aquí no pasa por el proxy y la
     * auditoría (que exige transacción) fallaría.
     */
    @Scheduled(cron = "${coretech.seguimiento.cron:0 15 8 * * *}", zone = "America/Bogota")
    @Transactional
    public void ejecutarCadaDia() {
        int hechos = revisar();
        if (hechos > 0) {
            LOG.info("Seguimiento de proyectos: {} cambio(s) o aviso(s)", hechos);
        }
    }

    /** Devuelve cuántas fases se recordaron o aprobaron, garantías cerradas y archivos borrados hoy. */
    @Transactional
    public int revisar() {
        int hechos = 0;
        for (FaseEnRevision fase : datos.fasesEnRevision()) {
            if (fases.aprobarPorVencimiento(fase)) {
                hechos++;
            } else if (fases.recordarRevision(fase)) {
                hechos++;
            }
        }
        LocalDate hoy = LocalDate.now(reloj);
        for (UUID proyecto : proyectos.garantiasCumplidas(hoy)) {
            if (fases.cerrarGarantia(proyecto)) {
                hechos++;
            }
        }
        return hechos + borrarAdjuntosViejos();
    }

    /** Conservación de datos (RNF-09): el archivo se borra del volumen a los 12 meses; el registro queda. */
    int borrarAdjuntosViejos() {
        Instant limite = LocalDate.now(reloj).minusMonths(12).atStartOfDay(ZoneId.of("America/Bogota")).toInstant();
        List<AdjuntoPorBorrar> porBorrar = datos.adjuntosPorBorrar(proyectos.cerradosAntesDe(limite));
        for (AdjuntoPorBorrar a : porBorrar) {
            almacen.borrar(ServicioSeguimiento.CARPETA, a.archivo());
            datos.marcarArchivoEliminado(a.id());
        }
        return porBorrar.size();
    }
}
