package co.coretech.portal.comunicacion;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evento para avisar a uno o varios usuarios (RF-39, HU-24): cualquier módulo lo publica con
 * {@code ApplicationEventPublisher.publishEvent(new AvisoSolicitado(...))} dentro de su transacción.
 * <p>
 * Cuando la transacción se confirma, {@code comunicacion} guarda una notificación por destinatario (la campana)
 * y {@code TareaEnvioCorreos} manda el correo en segundo plano, con hasta 3 intentos (RNF-13). Si la transacción
 * se revierte, no se avisa nada. Nunca lleva secretos (contraseñas, enlaces de recuperación): eso va directo por
 * {@code CorreoSaliente} (D-49).
 *
 * @param destinatarios ids de los usuarios (sin repetidos; al menos uno)
 * @param tipo          qué pasó, en mayúsculas: CITA_CONFIRMADA, PASO_HECHO, FASE_ENTREGADA, NUEVO_MENSAJE… (máx. 40)
 * @param titulo        título corto; también es el asunto del correo (máx. 80)
 * @param texto         explicación sencilla, sin tecnicismos (máx. 255)
 * @param enlace        ruta de la SPA donde se ve, por ejemplo "/cliente/proyectos/P-0024" (máx. 255), o null
 */
public record AvisoSolicitado(List<UUID> destinatarios, String tipo, String titulo, String texto, String enlace) {

    public AvisoSolicitado {
        if (destinatarios == null || destinatarios.isEmpty() || destinatarios.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("El aviso necesita al menos un destinatario");
        }
        Set<UUID> sinRepetir = new LinkedHashSet<>(destinatarios);
        destinatarios = List.copyOf(sinRepetir);
        exigir(tipo, 40, "tipo");
        if (!tipo.matches("[A-Z_]+")) {
            throw new IllegalArgumentException("El tipo del aviso va en mayúsculas y guiones bajos: " + tipo);
        }
        exigir(titulo, 80, "titulo");
        exigir(texto, 255, "texto");
        if (enlace != null && (!enlace.startsWith("/") || enlace.startsWith("//") || enlace.length() > 255)) {
            throw new IllegalArgumentException("El enlace del aviso es una ruta de la SPA que empieza con /: " + enlace);
        }
    }

    /** Aviso para un solo usuario. */
    public static AvisoSolicitado para(UUID destinatario, String tipo, String titulo, String texto, String enlace) {
        return new AvisoSolicitado(List.of(destinatario), tipo, titulo, texto, enlace);
    }

    private static void exigir(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank() || valor.length() > maximo) {
            throw new IllegalArgumentException("El " + campo + " del aviso es obligatorio y tiene máximo " + maximo + " caracteres");
        }
    }
}
