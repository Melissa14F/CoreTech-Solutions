package co.coretech.portal.compartido.controller;

import org.springframework.http.HttpStatus;

/** Cómo se responde un error de la base de datos: código HTTP, campo (opcional) y mensaje. */
record ErrorTraducido(HttpStatus estado, String campo, String mensaje) {

    static ErrorTraducido conflicto(String mensaje) {
        return new ErrorTraducido(HttpStatus.CONFLICT, null, mensaje);
    }

    static ErrorTraducido conflicto(String campo, String mensaje) {
        return new ErrorTraducido(HttpStatus.CONFLICT, campo, mensaje);
    }

    static ErrorTraducido invalido(String mensaje) {
        return new ErrorTraducido(HttpStatus.UNPROCESSABLE_ENTITY, null, mensaje);
    }

    static ErrorTraducido invalido(String campo, String mensaje) {
        return new ErrorTraducido(HttpStatus.UNPROCESSABLE_ENTITY, campo, mensaje);
    }

    ErrorTraducido conCampo(String otroCampo) {
        return new ErrorTraducido(estado, otroCampo, mensaje);
    }
}
