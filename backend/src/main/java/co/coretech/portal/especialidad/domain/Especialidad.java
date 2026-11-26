package co.coretech.portal.especialidad.domain;

import java.util.List;

/** Especialidad de un desarrollador; define qué roles puede tener en un equipo (RN-26). Mismos nombres que el enum de la base. */
public enum Especialidad {
    FRONTEND(RolEquipo.FRONTEND),
    BACKEND(RolEquipo.BACKEND),
    FULL_STACK(RolEquipo.FULL_STACK, RolEquipo.FRONTEND, RolEquipo.BACKEND),
    MOVIL(RolEquipo.MOVIL),
    QA(RolEquipo.QA),
    UX_UI(RolEquipo.UX_UI);

    private final List<RolEquipo> roles;

    Especialidad(RolEquipo... roles) {
        this.roles = List.of(roles);
    }

    /** Roles de integrante permitidos (sin Product Owner); el primero es el que le corresponde por defecto. */
    public List<RolEquipo> rolesPermitidos() {
        return roles;
    }

    public boolean permite(RolEquipo rol) {
        return roles.contains(rol);
    }
}
