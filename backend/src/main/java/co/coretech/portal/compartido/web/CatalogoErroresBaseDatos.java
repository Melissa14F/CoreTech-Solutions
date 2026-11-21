package co.coretech.portal.compartido.web;

import static co.coretech.portal.compartido.web.ErrorTraducido.conflicto;
import static co.coretech.portal.compartido.web.ErrorTraducido.invalido;
import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tabla "Errores de la base de datos → respuesta HTTP" de docs/04-api.md (decisión D-25).
 * <p>
 * El servicio valida cada regla antes de guardar; esta tabla se usa cuando la base es la que la
 * detecta (por ejemplo, dos peticiones a la vez). Si cambia una restricción o un disparador,
 * actualiza esta clase y la tabla del documento a la vez.
 */
final class CatalogoErroresBaseDatos {

    /** Regla para un disparador que tiene varios RAISE: se elige la primera cuyo texto aparece en el mensaje. */
    record ReglaPorTexto(String contiene, ErrorTraducido error) {
    }

    private static final String ESTADO_CAMBIO = "Estos datos cambiaron y la acción ya no se puede hacer. Recarga para ver lo último.";
    private static final String PROPONER_CAMBIO_PARA_QUITAR_PASO = "Para quitar un paso, propón un cambio de plan";
    /** HU-18. */
    static final String SERVICIO_EN_USO = "Este servicio tiene citas o proyectos; puedes ocultarlo";
    /** HU-21 (mismos textos que ServicioPermisos). */
    static final String PERMISO_FIJO = "Este permiso lo exige una regla del negocio";
    static final String PERMISO_VETADO = "Una regla del negocio no permite dar este permiso a este rol";

    /** Unicidad (23505) y CHECK (23514) por nombre de restricción. */
    private static final Map<String, ErrorTraducido> POR_RESTRICCION = Map.ofEntries(
            // Unicidad
            entry("usuarios_email_key", conflicto("email", "Ya existe una cuenta con este correo")),
            entry("ux_usuarios_email_ci", conflicto("email", "Ya existe una cuenta con este correo")),
            entry("desarrolladores_correo_key", conflicto("correo", "Ya hay un desarrollador registrado con este correo")),
            entry("integrantes_equipo_desarrollador_id_key", conflicto("desarrolladorId", "Este desarrollador ya está en otro equipo")),
            entry("ux_un_po_por_equipo", conflicto("El equipo ya tiene un desarrollador (Product Owner)")),
            entry("servicios_nombre_key", conflicto("nombre", "Ya existe un servicio con este nombre.")),
            // RN-21: un servicio con citas o proyectos no se borra (llaves foráneas, 23503)
            entry("citas_servicio_id_fkey", conflicto(SERVICIO_EN_USO)),
            entry("proyectos_servicio_id_fkey", conflicto(SERVICIO_EN_USO)),
            entry("ux_plantilla_fase_nombre", invalido("nombre", "Ya hay una fase con ese nombre en este servicio")),
            entry("ux_pf_nombre", invalido("nombre", "Ya hay una fase con ese nombre en el plan")),
            entry("ux_cita_franja", conflicto("fechaHora", "La hora que elegiste ya no está disponible, elige otra")),
            entry("ux_cita_cliente_hora", conflicto("fechaHora", "Ya tienes una cita")),
            entry("cuestionarios_cita_cita_id_key", conflicto("Esta cita ya tiene su cuestionario")),
            entry("proyectos_cita_id_key", conflicto("citaId", "Esta cita ya se convirtió en un proyecto")),
            entry("ux_cambio_plan_abierto", conflicto("Este proyecto ya tiene un cambio de plan esperando respuesta")),
            entry("ux_solicitud_abierta", conflicto("Ya tienes una solicitud de reunión en espera para este proyecto")),
            // CHECK que el usuario puede provocar desde un formulario
            entry("ck_proyectos_fechas", invalido("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio")),
            entry("ck_proyectos_valor", invalido("valorCotizado", "El valor debe estar entre $1.000.000 y $200.000.000")),
            entry("ck_proyectos_condic", invalido("condicionesCotizacion", "Qué incluye y qué no debe tener entre 20 y 600 caracteres")),
            entry("ck_cp_valor", invalido("valorAdicional", "El valor adicional debe estar entre $0 y $200.000.000")),
            entry("ck_cp_motivo", invalido("motivo", "El motivo debe tener entre 20 y 500 caracteres")),
            entry("ck_cp_alcance", invalido("tituloPaso", "Completa el título (3 a 80 caracteres) y la explicación (20 a 400) del trabajo nuevo")),
            entry("ux_cp_solicitud", conflicto("Esa solicitud de algo nuevo ya tiene una cotización en curso")),
            entry("ck_citas_descripcion", invalido("descripcionNecesidad", "Cuéntanos un poco más (mínimo 20 caracteres)")),
            entry("ck_pasos_titulo", invalido("titulo", "El título debe tener entre 3 y 80 caracteres")),
            entry("ck_pasos_nota", invalido("nota", "La nota debe tener entre 20 y 400 caracteres (entre 20 y 500 en una corrección o un ajuste)")),
            entry("ck_pasos_explicacion", invalido("explicacion", "La explicación de un paso tiene máximo 160 caracteres; la de una corrección o un ajuste, entre 20 y 400")),
            entry("ck_rp_comentario", invalido("comentario", "Escribe al menos 20 caracteres (máximo 600)")),
            entry("ck_mensajes_contenido", invalido("contenido", "El mensaje debe tener entre 10 y 1000 caracteres")),
            entry("ck_resumen_texto", invalido("resumen", "El resumen debe tener entre 30 y 1000 caracteres")),
            entry("ck_resumen_pidio", invalido("quePidio", "Lo que pidió el cliente debe tener entre 20 y 600 caracteres")),
            entry("ck_resumen_siguiente", invalido("siguientePaso", "Elige cuál es el siguiente paso")),
            entry("ck_usuarios_celular", invalido("celular", "Escribe un celular de 10 dígitos que empiece por 3")),
            entry("ck_dev_celular", invalido("celular", "Escribe un celular de 10 dígitos que empiece por 3")),
            entry("ck_dev_correo", invalido("correo", "El correo debe terminar en @coretechsolutions.co")),
            entry("ck_servicios_nombre", invalido("nombre", "El nombre debe tener entre 4 y 40 caracteres.")),
            entry("ck_servicios_desc", invalido("descripcionCorta", "La descripción debe tener entre 20 y 90 caracteres.")),
            entry("ck_servicios_alt", invalido("imagenAlt", "El texto alternativo debe tener entre 10 y 120 caracteres.")),
            entry("ck_contenido_valor", invalido("El texto no cumple el largo permitido. Revisa los campos marcados.")));

    /** Disparadores (P0001) por la función que hizo el RAISE; la última regla de cada lista es la de respaldo. */
    private static final Map<String, List<ReglaPorTexto>> POR_FUNCION = Map.of(
            "fn_revisar_fases", List.of(
                    new ReglaPorTexto("(tiene 0)", invalido("El plan necesita al menos 2 fases")),
                    new ReglaPorTexto("(tiene 1)", invalido("El plan necesita al menos 2 fases")),
                    new ReglaPorTexto("de 2 a 8 fases", invalido("El plan puede tener máximo 8 fases")),
                    new ReglaPorTexto("apruebe el cliente", invalido("Marca al menos una fase que apruebe el cliente")),
                    new ReglaPorTexto("", invalido("Cada fase necesita al menos un paso"))),
            "fn_validar_integrante", List.of(
                    new ReglaPorTexto("inactivo", invalido("desarrolladorId", "Este desarrollador está inactivo")),
                    new ReglaPorTexto("necesita una cuenta", invalido("desarrolladorId", "El desarrollador (Product Owner) necesita una cuenta en la plataforma")),
                    new ReglaPorTexto("", invalido("rol", "El rol no corresponde a la especialidad del desarrollador"))),
            "fn_validar_cambio_plan", List.of(
                    new ReglaPorTexto("distinta de la actual", invalido("nuevaEntrega", "Elige una fecha distinta a la entrega actual")),
                    new ReglaPorTexto("conservar al menos un paso", invalido("pasoId", "La fase debe conservar al menos un paso")),
                    new ReglaPorTexto("quitar una fase pendiente", conflicto("Solo se quita una fase pendiente; las fases aprobadas no cambian")),
                    new ReglaPorTexto("quita un paso pendiente", conflicto("Solo se quita un paso que no ha empezado")),
                    new ReglaPorTexto("cambio de alcance por cotizar", conflicto("Esa solicitud de algo nuevo ya no está por cotizar")),
                    new ReglaPorTexto("", conflicto("Solo se cambia el plan de un proyecto activo con el plan aprobado"))),
            // D-39: quitar un paso sin un cambio QUITAR_PASO aceptado (o borrarlo con el plan aprobado) es un
            // estado incompatible (409), no un permiso. Los demás RAISE de la función usan el mensaje genérico.
            "fn_proteger_pasos", List.of(
                    new ReglaPorTexto("requiere un cambio autorizado", conflicto(PROPONER_CAMBIO_PARA_QUITAR_PASO)),
                    new ReglaPorTexto("Solo se quitan pasos", conflicto(PROPONER_CAMBIO_PARA_QUITAR_PASO)),
                    new ReglaPorTexto("", conflicto(ESTADO_CAMBIO))),
            "fn_proteger_mensajes", List.of(
                    new ReglaPorTexto("no se borran", conflicto("Los mensajes no se borran")),
                    new ReglaPorTexto("", conflicto("Ya no puedes editar este mensaje"))),
            "fn_plan_en_revision", List.of(
                    new ReglaPorTexto("", conflicto("Este plan no se puede cambiar mientras está enviado al cliente, rechazado o vencido"))),
            // V6 · RN-19: permiso fijo que se intenta quitar o permiso vetado que se intenta dar (HU-21)
            "fn_proteger_rol_permisos", List.of(
                    new ReglaPorTexto("vetado", invalido("permisos", PERMISO_VETADO)),
                    new ReglaPorTexto("", invalido("permisos", PERMISO_FIJO))));

    private CatalogoErroresBaseDatos() {
    }

    static Optional<ErrorTraducido> porRestriccion(String nombre) {
        return Optional.ofNullable(nombre).map(POR_RESTRICCION::get);
    }

    static Optional<ErrorTraducido> porFuncion(String funcion, String mensajeBaseDatos) {
        String mensaje = mensajeBaseDatos == null ? "" : mensajeBaseDatos;
        return Optional.ofNullable(funcion)
                .map(POR_FUNCION::get)
                .flatMap(reglas -> reglas.stream().filter(r -> mensaje.contains(r.contiene())).findFirst())
                .map(ReglaPorTexto::error);
    }

    /** Respaldo por SQLState cuando la restricción o la función no está en la tabla. */
    static Optional<ErrorTraducido> porEstadoSql(String sqlState) {
        if (sqlState == null) {
            return Optional.empty();
        }
        return switch (sqlState) {
            case "23505" -> Optional.of(conflicto("Ya existe un registro con esos datos"));
            case "23503" -> Optional.of(conflicto("Estos datos están relacionados con otros registros y la acción no se puede completar"));
            case "23502" -> Optional.of(invalido("Falta un dato obligatorio"));
            case "23514" -> Optional.of(invalido("Hay un dato con un valor no permitido. Revisa el formulario."));
            case "P0001" -> Optional.of(conflicto(ESTADO_CAMBIO));
            case "40001", "40P01" -> Optional.of(conflicto("Otra persona cambió estos datos al mismo tiempo. Intenta de nuevo."));
            default -> Optional.empty();
        };
    }
}
