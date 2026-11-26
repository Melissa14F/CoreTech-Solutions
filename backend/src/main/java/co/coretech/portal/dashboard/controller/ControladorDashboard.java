package co.coretech.portal.dashboard.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.coretech.portal.dashboard.service.ServicioDashboard;
import co.coretech.portal.dashboard.service.ServicioDashboard.Tablero;

/**
 * {@code GET /api/v1/dashboard?periodo=semana|mes|trimestre} (HU-23): los indicadores, las alertas y la carga de los equipos.
 * Es de lectura y solo lo ve quien puede ver los proyectos de todos los clientes ({@code proyectos.ver_todos}, el administrador).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
class ControladorDashboard {

    private final ServicioDashboard servicio;

    ControladorDashboard(ServicioDashboard servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('proyectos.ver_todos')")
    Tablero tablero(@RequestParam(name = "periodo", required = false) String periodo) {
        return servicio.tablero(periodo);
    }
}
