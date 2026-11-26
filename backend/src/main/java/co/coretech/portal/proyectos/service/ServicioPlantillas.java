package co.coretech.portal.proyectos.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.compartido.exception.NoEncontradoException;
import co.coretech.portal.compartido.service.RegistroAuditoria;
import co.coretech.portal.compartido.service.SesionActual;
import co.coretech.portal.compartido.exception.ValidacionException;
import co.coretech.portal.compartido.domain.CambioDeValor;
import co.coretech.portal.compartido.domain.CategoriaAuditoria;
import co.coretech.portal.proyectos.repository.PlantillasJdbc;
import co.coretech.portal.proyectos.domain.FasePlantilla;
import co.coretech.portal.proyectos.domain.PasoPlantilla;

/**
 * Plantilla de fases de cada servicio (HU-27, RN-27, RN-28). Al crear un proyecto se copia como su plan en
 * borrador; cambiarla solo afecta a los proyectos nuevos. El servicio valida cada regla con un mensaje por campo
 * y la base de datos las vuelve a revisar al confirmar (fn_validar_plantilla).
 */
@Service
public class ServicioPlantillas {

    static final String SERVICIO_NO_ENCONTRADO = "Este servicio ya no está disponible";
    static final int MIN_FASES = 2;
    static final int MAX_FASES = 8;
    static final int MAX_PASOS = 12;

    private final PlantillasJdbc plantillas;
    private final DirectorioServicios servicios;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;

    public ServicioPlantillas(PlantillasJdbc plantillas, DirectorioServicios servicios, RegistroAuditoria auditoria,
            SesionActual sesion) {
        this.plantillas = plantillas;
        this.servicios = servicios;
        this.auditoria = auditoria;
        this.sesion = sesion;
    }

    @Transactional(readOnly = true)
    public List<FasePlantilla> obtener(UUID servicioId) {
        existe(servicioId);
        return plantillas.leer(servicioId);
    }

    /** Reemplaza la plantilla completa en una sola transacción y la deja en la auditoría. */
    @Transactional
    public List<FasePlantilla> guardar(UUID servicioId, List<FasePlantilla> pedidas) {
        String nombreServicio = existe(servicioId);
        List<FasePlantilla> fases = normalizar(pedidas);
        validar(fases, "este servicio");

        List<FasePlantilla> antes = plantillas.leer(servicioId);
        plantillas.reemplazar(servicioId, sesion.usuario().id(), fases);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("servicio", CambioDeValor.nuevo(nombreServicio));
        cambios.put("fases", CambioDeValor.de(antes.size(), fases.size()));
        cambios.put("nombresFases", CambioDeValor.de(nombres(antes), nombres(fases)));
        cambios.put("pasos", CambioDeValor.de(totalPasos(antes), totalPasos(fases)));
        cambios.put("fasesQueApruebaElCliente", CambioDeValor.de(aprueban(antes), aprueban(fases)));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Guardó la plantilla de fases de un servicio", "servicio",
                servicioId.toString(), cambios);
        return plantillas.leer(servicioId);
    }

    private String existe(UUID servicioId) {
        String nombre = servicios.nombres(List.of(servicioId)).get(servicioId);
        if (nombre == null) {
            throw new NoEncontradoException(SERVICIO_NO_ENCONTRADO);
        }
        return nombre;
    }

    /** Recorta los textos, descarta el orden que mande el cliente y deja los vacíos opcionales como nulos. */
    static List<FasePlantilla> normalizar(List<FasePlantilla> pedidas) {
        List<FasePlantilla> fases = new ArrayList<>();
        if (pedidas == null) {
            return fases;
        }
        for (FasePlantilla f : pedidas) {
            List<PasoPlantilla> pasos = new ArrayList<>();
            if (f != null && f.pasos() != null) {
                for (PasoPlantilla p : f.pasos()) {
                    if (p != null) {
                        pasos.add(new PasoPlantilla(0, recortar(p.titulo()), vacioANulo(p.explicacion())));
                    }
                }
            }
            fases.add(f == null ? new FasePlantilla(0, "", null, false, pasos)
                    : new FasePlantilla(0, recortar(f.nombre()), vacioANulo(f.queEs()), f.apruebaCliente(), pasos));
        }
        return fases;
    }

    /** ambito: "este servicio" (plantilla) o "el plan" (proyecto); solo cambia el mensaje de nombre repetido. */
    static void validar(List<FasePlantilla> fases, String ambito) {
        var errores = new ValidacionException.Acumulador();
        if (fases.size() < MIN_FASES) {
            errores.agregar("fases", "El plan necesita al menos 2 fases");
        } else if (fases.size() > MAX_FASES) {
            errores.agregar("fases", "El plan puede tener máximo 8 fases");
        }
        if (!fases.isEmpty() && fases.stream().noneMatch(FasePlantilla::apruebaCliente)) {
            errores.agregar("fases", "Marca al menos una fase que apruebe el cliente");
        }
        Map<String, Integer> vistos = new HashMap<>();
        for (int f = 0; f < fases.size(); f++) {
            FasePlantilla fase = fases.get(f);
            String campo = "fases[" + f + "]";
            String incompleta = "Fase " + (f + 1) + ": completa el nombre y los pasos";
            if (fase.nombre().length() < 3) {
                errores.agregar(campo + ".nombre", "Escribe el nombre de la fase (al menos 3 caracteres).");
                errores.agregar("fases", incompleta);
            } else if (fase.nombre().length() > 60) {
                errores.agregar(campo + ".nombre", "El nombre de la fase puede tener máximo 60 caracteres.");
            } else if (vistos.putIfAbsent(fase.nombre().toLowerCase(), f) != null) {
                errores.agregar(campo + ".nombre", "Ya hay una fase con ese nombre en " + ambito);
            }
            if (fase.queEs() != null && fase.queEs().length() > 140) {
                errores.agregar(campo + ".queEs", "Escribe máximo 140 caracteres.");
            }
            if (fase.pasos().isEmpty()) {
                errores.agregar(campo + ".pasos", "Agrega al menos un paso.");
                errores.agregar("fases", incompleta);
            } else if (fase.pasos().size() > MAX_PASOS) {
                errores.agregar(campo + ".pasos", "Cada fase puede tener máximo 12 pasos.");
            }
            for (int p = 0; p < fase.pasos().size(); p++) {
                PasoPlantilla paso = fase.pasos().get(p);
                String campoPaso = campo + ".pasos[" + p + "]";
                if (paso.titulo().length() < 3) {
                    errores.agregar(campoPaso + ".titulo", "Escribe qué se hace en este paso.");
                    errores.agregar("fases", incompleta);
                } else if (paso.titulo().length() > 80) {
                    errores.agregar(campoPaso + ".titulo", "El título del paso puede tener máximo 80 caracteres.");
                }
                if (paso.explicacion() != null && paso.explicacion().length() > 160) {
                    errores.agregar(campoPaso + ".explicacion", "Escribe máximo 160 caracteres.");
                }
            }
        }
        errores.lanzarSiHay();
    }

    private static String recortar(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static String vacioANulo(String texto) {
        String t = recortar(texto);
        return t.isEmpty() ? null : t;
    }

    private static List<String> nombres(List<FasePlantilla> fases) {
        return fases.stream().map(FasePlantilla::nombre).toList();
    }

    private static int totalPasos(List<FasePlantilla> fases) {
        return fases.stream().mapToInt(f -> f.pasos().size()).sum();
    }

    private static long aprueban(List<FasePlantilla> fases) {
        return fases.stream().filter(FasePlantilla::apruebaCliente).count();
    }
}
