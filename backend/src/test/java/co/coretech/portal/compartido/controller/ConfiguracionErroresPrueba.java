package co.coretech.portal.compartido.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Para las pruebas {@code @WebMvcTest} de otros módulos: trae el manejador global de errores (el {@code @RestControllerAdvice}
 * que convierte las excepciones en {@code { "errores": [{ "campo", "mensaje" }] }}) y su traductor de errores de la base de
 * datos, que son visibles solo dentro de este paquete. Así cada prueba de controlador usa el manejador REAL, no uno de mentira.
 * La prueba que la use debe simular además {@code AuditoriaAccesoDenegado} con {@code @MockitoBean}.
 */
@TestConfiguration
@Import({ ManejadorGlobalErrores.class, TraductorErroresBaseDatos.class })
public class ConfiguracionErroresPrueba {
}
