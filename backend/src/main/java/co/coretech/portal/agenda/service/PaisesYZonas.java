package co.coretech.portal.agenda.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Países que el cliente puede elegir al agendar y las zonas IANA de cada uno (HU-01, RN-23). Es la misma lista de
 * {@code prototipo/cliente/agendar-fecha.html}. Un país con una sola zona no pide elegirla; con varias, sí.
 */
final class PaisesYZonas {

    static final String PAIS_NEGOCIO = "Colombia";

    private static final Map<String, List<String>> ZONAS = new LinkedHashMap<>();

    static {
        ZONAS.put("Colombia", List.of("America/Bogota"));
        ZONAS.put("Argentina", List.of("America/Argentina/Buenos_Aires"));
        ZONAS.put("Bolivia", List.of("America/La_Paz"));
        ZONAS.put("Brasil", List.of("America/Sao_Paulo", "America/Manaus"));
        ZONAS.put("Canadá", List.of("America/Toronto", "America/Winnipeg", "America/Edmonton", "America/Vancouver"));
        ZONAS.put("Chile", List.of("America/Santiago"));
        ZONAS.put("Costa Rica", List.of("America/Costa_Rica"));
        ZONAS.put("Ecuador", List.of("America/Guayaquil"));
        ZONAS.put("El Salvador", List.of("America/El_Salvador"));
        ZONAS.put("España", List.of("Europe/Madrid", "Atlantic/Canary"));
        ZONAS.put("Estados Unidos", List.of("America/New_York", "America/Chicago", "America/Denver", "America/Phoenix",
                "America/Los_Angeles"));
        ZONAS.put("Guatemala", List.of("America/Guatemala"));
        ZONAS.put("Honduras", List.of("America/Tegucigalpa"));
        ZONAS.put("México", List.of("America/Mexico_City", "America/Cancun", "America/Mazatlan", "America/Tijuana"));
        ZONAS.put("Panamá", List.of("America/Panama"));
        ZONAS.put("Paraguay", List.of("America/Asuncion"));
        ZONAS.put("Perú", List.of("America/Lima"));
        ZONAS.put("Puerto Rico", List.of("America/Puerto_Rico"));
        ZONAS.put("República Dominicana", List.of("America/Santo_Domingo"));
        ZONAS.put("Uruguay", List.of("America/Montevideo"));
        ZONAS.put("Venezuela", List.of("America/Caracas"));
        ZONAS.put("Alemania", List.of("Europe/Berlin"));
        ZONAS.put("Francia", List.of("Europe/Paris"));
        ZONAS.put("Italia", List.of("Europe/Rome"));
        ZONAS.put("Países Bajos", List.of("Europe/Amsterdam"));
        ZONAS.put("Portugal", List.of("Europe/Lisbon"));
        ZONAS.put("Reino Unido", List.of("Europe/London"));
        ZONAS.put("Suiza", List.of("Europe/Zurich"));
        ZONAS.put("Australia", List.of("Australia/Sydney", "Australia/Perth"));
        ZONAS.put("Japón", List.of("Asia/Tokyo"));
    }

    private PaisesYZonas() {
    }

    static boolean existe(String pais) {
        return pais != null && ZONAS.containsKey(pais);
    }

    /** Zonas del país; vacía si el país no está en la lista. */
    static List<String> zonasDe(String pais) {
        return ZONAS.getOrDefault(pais, List.of());
    }
}
