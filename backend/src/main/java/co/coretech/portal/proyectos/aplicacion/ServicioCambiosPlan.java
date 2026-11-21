package co.coretech.portal.proyectos.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.ProhibidoException;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.compartido.aplicacion.UsuarioSesion;
import co.coretech.portal.compartido.aplicacion.ValidacionException.Acumulador;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.ProductOwner;
import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.PasoPlantilla;
import co.coretech.portal.proyectos.datos.CambiosPlanJdbc;
import co.coretech.portal.proyectos.datos.CambiosPlanJdbc.AlcancePorCotizar;
import co.coretech.portal.proyectos.datos.CambiosPlanJdbc.CambioFila;
import co.coretech.portal.proyectos.datos.CambiosPlanJdbc.FaseDelPlan;
import co.coretech.portal.proyectos.datos.CambiosPlanJdbc.PasoDelPlan;
import co.coretech.portal.proyectos.datos.ProyectosJdbc;
import co.coretech.portal.proyectos.datos.ProyectosJdbc.Fila;

/**
 * Cambios de un plan aprobado (HU-30, RN-30, D-32, D-33): agregar o quitar una fase pendiente, cambiar solo la fecha de
 * entrega, quitar un paso pendiente o cotizar un cambio de alcance que pidió el cliente.
 * <p>
 * Lo propone el desarrollador (Product Owner) del equipo; lo autoriza el administrador; lo acepta el cliente y solo entonces se
 * aplica al plan. Excepción: quitar un paso de una fase que cierra el equipo se aplica al autorizarlo y solo se avisa al cliente.
 * Mientras tanto el plan sigue igual y un proyecto solo tiene un cambio abierto a la vez. Un cliente ajeno recibe 404 (RN-10)
 * y quien no es del proyecto, 403; ambos intentos quedan en la auditoría, por eso los métodos no revierten en esos casos.
 */
@Service
public class ServicioCambiosPlan {

    static final String NO_ENCONTRADO = "Proyecto no encontrado";
    static final String CAMBIO_NO_ENCONTRADO = "Cambio de plan no encontrado";
    static final String SOLO_PRODUCT_OWNER = "Solo el desarrollador (Product Owner) del equipo propone cambios del plan";
    static final String NO_ES_DE_TU_EQUIPO = "Este proyecto no es de tu equipo";
    static final String ESTADO_NO_ADMITE = "Solo se cambia el plan de un proyecto activo con el plan aprobado";
    static final String YA_HAY_ABIERTO = "Este proyecto ya tiene un cambio de plan esperando respuesta";
    static final String SOLO_FASE_PENDIENTE = "Solo se quita una fase pendiente; las fases aprobadas no cambian";
    static final String SOLO_PASO_SIN_EMPEZAR = "Solo se quita un paso que no ha empezado";
    static final String FASE_CONSERVA_UN_PASO = "La fase debe conservar al menos un paso";
    static final String FECHA_DISTINTA = "Elige una fecha distinta a la entrega actual";
    static final String YA_RESPONDIDO = "Este cambio de plan ya fue respondido";
    static final String NO_ESPERA_TU_RESPUESTA = "Este cambio no está esperando tu respuesta";
    static final String PLAN_CAMBIO = "El plan cambió desde que se propuso este cambio: pídele al equipo que lo proponga de nuevo";
    static final String DATOS_DESACTUALIZADOS = "El proyecto cambió, recarga para ver lo último";
    static final long VALOR_MAXIMO = 200_000_000L;
    static final LocalDate ULTIMA_FECHA = LocalDate.of(2028, 12, 31);
    static final Set<String> TIPOS = Set.of("AGREGAR_FASE", "QUITAR_FASE", "CAMBIAR_FECHA", "QUITAR_PASO", "AGREGAR_ALCANCE");

    /** Un paso de la fase nueva. */
    public record PasoNuevo(String titulo, String explicacion) {
    }

    /**
     * Lo que manda el desarrollador. Cada tipo usa lo suyo: AGREGAR_FASE ({@code nombreFase}, {@code posicion},
     * {@code apruebaCliente}, {@code pasos}), QUITAR_FASE ({@code faseId}), QUITAR_PASO ({@code pasoId}) y AGREGAR_ALCANCE
     * ({@code solicitudCambioId}, {@code tituloPaso}, {@code explicacionPaso}). Todos menos QUITAR_PASO llevan nueva entrega
     * (AAAA-MM-DD) y valor adicional.
     */
    public record Propuesta(String tipo, String nombreFase, Integer posicion, Boolean apruebaCliente, List<PasoNuevo> pasos, UUID faseId,
            UUID pasoId, UUID solicitudCambioId, String tituloPaso, String explicacionPaso, String motivo, String nuevaEntrega,
            BigDecimal valorAdicional) {
    }

    /** Una fase del plan en la comparación; {@code porcentaje} es lo que vale (100 dividido entre las fases, RN-08). */
    public record FaseVista(String nombre, BigDecimal porcentaje, String estado) {
    }

    /** Cómo está o cómo queda el plan: fases con su valor, avance, entrega y valor total del proyecto. */
    public record Foto(List<FaseVista> fases, BigDecimal avance, LocalDate entrega, BigDecimal valorTotal) {
    }

    /**
     * Un cambio de plan como lo ven el equipo, el administrador y el cliente. {@code antes} y {@code despues} solo vienen
     * mientras el cambio está abierto. {@code seAplicaAlAutorizar} es verdadero para quitar un paso de una fase que cierra el
     * equipo (no pasa por el cliente).
     */
    public record CambioVista(UUID id, String proyectoCodigo, String proyecto, String cliente, String tipo, String estado, String motivo,
            String propuestoPor, Instant creadoAt, Instant autorizadoAt, Instant respondidoAt, String motivoRechazo, String faseNombre,
            Integer posicion, Boolean apruebaCliente, List<PasoNuevo> pasosNuevos, String pasoTitulo, String pasoFase, String tituloPaso,
            String explicacionPaso, String solicitud, LocalDate nuevaEntrega, BigDecimal valorAdicional, Foto antes, Foto despues,
            boolean seAplicaAlAutorizar, boolean puedeAutorizar, boolean puedeAceptar) {
    }

    /** Los cambios de un proyecto y lo que el Product Owner puede hacer ahora. */
    public record CambiosDelProyecto(boolean puedeProponer, boolean hayCambioAbierto, List<AlcancePorCotizar> alcancesPorCotizar,
            List<CambioVista> cambios, BigDecimal valorCotizado, BigDecimal valorAdicionalAceptado, BigDecimal valorTotal) {
    }

    /** Resultado de proponer (HU-30). */
    public record Creado(UUID cambioId, String estado, int fasesAntes, int fasesDespues, BigDecimal avanceDespues) {
    }

    /** Resultado de autorizar o rechazar. */
    public record Respondido(UUID cambioId, String estado, Instant autorizadoAt) {
    }

    /** Resultado de aceptar: cómo quedó el plan. */
    public record Aceptado(String estado, int fases, BigDecimal avancePorcentaje, LocalDate fechaEstimadaEntrega, BigDecimal valorTotal) {
    }

    private final CambiosPlanJdbc datos;
    private final ProyectosJdbc proyectos;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final ObjectMapper json;
    private final Clock reloj;

    public ServicioCambiosPlan(CambiosPlanJdbc datos, ProyectosJdbc proyectos, EquipoConsultas equipos, DirectorioUsuarios usuarios,
            RegistroAuditoria auditoria, SesionActual sesion, ApplicationEventPublisher eventos, ObjectMapper json, Clock reloj) {
        this.datos = datos;
        this.proyectos = proyectos;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.json = json;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------------------------------- Consultar

    /** Los cambios de un proyecto: el cliente dueño (solo los que ya autorizó el administrador), el Product Owner del equipo o el administrador. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public CambiosDelProyecto deProyecto(String codigo) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = buscar(codigo);
        boolean cliente = f.clienteId().equals(usuario.id());
        boolean administrador = esAdministrador(usuario);
        if (!cliente && !administrador) {
            exigirProductOwner(f, usuario, "consultar los cambios del plan de");
        }
        List<CambioFila> filas = datos.deProyecto(f.id()).stream()
                .filter(c -> !cliente || Set.of("AUTORIZADO", "ACEPTADO", "RECHAZADO_CLIENTE").contains(c.estado())).toList();
        boolean abierto = datos.deProyecto(f.id()).stream().anyMatch(c -> abierto(c.estado()));
        boolean productOwner = !cliente && !administrador;
        boolean puedeProponer = productOwner && f.estado().equals("ACTIVO") && f.estadoPlan().equals("APROBADO") && !abierto;
        BigDecimal cotizado = datos.valorCotizado(f.id());
        BigDecimal adicional = datos.valorAdicionalAceptado(f.id());
        return new CambiosDelProyecto(puedeProponer, abierto, productOwner ? datos.alcancesPorCotizar(f.id()) : List.of(),
                filas.stream().map(c -> vista(c, usuario)).toList(), cotizado, adicional, cotizado.add(adicional));
    }

    /** Los cambios que esperan la autorización del administrador, con el plan antes y después. */
    @Transactional(readOnly = true)
    public List<CambioVista> porAutorizar() {
        UsuarioSesion usuario = sesion.usuario();
        return datos.porEstado("PROPUESTO").stream().map(c -> vista(c, usuario)).toList();
    }

    // ------------------------------------------------------------------------------------------- Proponer

    /** El desarrollador (Product Owner) del equipo propone un cambio; queda PROPUESTO y el administrador lo autoriza o lo rechaza. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Creado proponer(String codigo, Propuesta p) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = buscar(codigo);
        exigirProductOwner(f, usuario, "proponer un cambio en el plan de");
        proyectos.bloquear(codigo);
        if (!f.estado().equals("ACTIVO") || !f.estadoPlan().equals("APROBADO")) {
            throw new ConflictoException(ESTADO_NO_ADMITE);
        }
        if (proyectos.hayCambioDePlanAbierto(f.id())) {
            throw new ConflictoException(YA_HAY_ABIERTO);
        }

        String tipo = p.tipo() == null ? "" : p.tipo();
        Acumulador errores = new Acumulador();
        if (!TIPOS.contains(tipo)) {
            errores.agregar("tipo", "Elige el tipo de cambio.");
            errores.lanzarSiHay();
        }
        List<FaseDelPlan> fases = datos.fases(f.id());
        String motivo = p.motivo() == null ? "" : p.motivo().trim();
        validarMotivo(motivo, errores);

        LocalDate entrega = null;
        BigDecimal valor = BigDecimal.ZERO;
        if (!tipo.equals("QUITAR_PASO")) {
            entrega = validarEntrega(p.nuevaEntrega(), f, errores);
            valor = validarValor(p.valorAdicional(), errores);
            if (tipo.equals("CAMBIAR_FECHA") && entrega != null && entrega.equals(f.fechaEntrega())) {
                errores.agregar("nuevaEntrega", FECHA_DISTINTA);
            }
        }

        String nombreFase = null;
        Integer posicion = null;
        boolean apruebaCliente = false;
        String pasosJson = null;
        UUID faseId = null;
        UUID pasoId = null;
        UUID solicitudId = null;
        String tituloPaso = null;
        String explicacionPaso = null;
        List<FaseDelPlan> despues = fases;

        switch (tipo) {
            case "AGREGAR_FASE" -> {
                nombreFase = p.nombreFase() == null ? "" : p.nombreFase().trim();
                apruebaCliente = Boolean.TRUE.equals(p.apruebaCliente());
                List<PasoPlantilla> pasos = new ArrayList<>();
                for (PasoNuevo n : p.pasos() == null ? List.<PasoNuevo>of() : p.pasos()) {
                    if (n != null) {
                        String t = n.titulo() == null ? "" : n.titulo().trim();
                        String e = n.explicacion() == null || n.explicacion().isBlank() ? null : n.explicacion().trim();
                        pasos.add(new PasoPlantilla(0, t, e));
                    }
                }
                posicion = validarFaseNueva(nombreFase, p.posicion(), pasos, fases, errores);
                final String nombre = nombreFase;
                final boolean aprueba = apruebaCliente;
                pasosJson = escribir(pasos.stream().map(x -> new PasoNuevo(x.titulo(), x.explicacion())).toList());
                if (posicion != null) {
                    List<FaseDelPlan> nueva = new ArrayList<>(fases);
                    nueva.add(posicion - 1, new FaseDelPlan(null, posicion, nombre, "PENDIENTE", aprueba));
                    despues = nueva;
                }
            }
            case "QUITAR_FASE" -> {
                FaseDelPlan fase = fases.stream().filter(x -> x.id().equals(p.faseId())).findFirst().orElse(null);
                if (fase == null) {
                    errores.agregar("faseId", "Elige la fase que quieres quitar.");
                } else if (!fase.estado().equals("PENDIENTE")) {
                    throw new ConflictoException(SOLO_FASE_PENDIENTE);
                } else {
                    faseId = fase.id();
                    despues = fases.stream().filter(x -> !x.id().equals(fase.id())).toList();
                }
            }
            case "QUITAR_PASO" -> {
                PasoDelPlan paso = p.pasoId() == null ? null : datos.paso(p.pasoId()).filter(x -> x.proyectoId().equals(f.id())).orElse(null);
                if (paso == null) {
                    errores.agregar("pasoId", "Elige el paso que quieres quitar.");
                } else if (!paso.tipo().equals("PASO") || !paso.estado().equals("PENDIENTE")
                        || !Set.of("PENDIENTE", "EN_CURSO").contains(paso.faseEstado())) {
                    throw new ConflictoException(SOLO_PASO_SIN_EMPEZAR);
                } else if (paso.pasosQueQuedan() == 0) {
                    errores.agregar("pasoId", FASE_CONSERVA_UN_PASO);
                } else {
                    pasoId = paso.id();
                }
            }
            case "AGREGAR_ALCANCE" -> {
                AlcancePorCotizar alcance = datos.alcancesPorCotizar(f.id()).stream()
                        .filter(x -> x.id().equals(p.solicitudCambioId())).findFirst().orElse(null);
                if (alcance == null) {
                    errores.agregar("solicitudCambioId", "Elige la solicitud de algo nuevo que vas a cotizar.");
                } else {
                    solicitudId = alcance.id();
                }
                tituloPaso = p.tituloPaso() == null ? "" : p.tituloPaso().trim();
                explicacionPaso = p.explicacionPaso() == null ? "" : p.explicacionPaso().trim();
                if (tituloPaso.length() < 3 || tituloPaso.length() > 80) {
                    errores.agregar("tituloPaso", "El título del paso debe tener entre 3 y 80 caracteres.");
                }
                if (explicacionPaso.length() < 20 || explicacionPaso.length() > 400) {
                    errores.agregar("explicacionPaso", "La explicación del paso debe tener entre 20 y 400 caracteres.");
                }
            }
            default -> {
            }
        }
        if (tipo.equals("QUITAR_FASE") && despues != fases) {
            validarPlan(despues);
        }
        errores.lanzarSiHay();

        Instant ahora = Instant.now(reloj);
        UUID id = datos.insertar(f.id(), tipo, nombreFase, posicion, apruebaCliente, pasosJson, faseId, pasoId, solicitudId, tituloPaso,
                explicacionPaso, motivo, entrega, valor, usuario.id(), ahora);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("tipo", CambioDeValor.nuevo(tipo));
        cambios.put("motivo", CambioDeValor.nuevo(motivo));
        if (entrega != null) {
            cambios.put("nuevaEntrega", CambioDeValor.nuevo(entrega.toString()));
            cambios.put("valorAdicional", CambioDeValor.nuevo(valor.toPlainString()));
        }
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Propuso un cambio de plan", "proyecto", f.codigo(), cambios);
        avisar(usuarios.administradoresActivos(), "CAMBIO_PLAN_PROPUESTO", "Cambio de plan por autorizar: " + f.codigo(),
                usuario.nombre() + " propone " + nombreDelTipo(tipo) + " en " + f.nombre() + ".", "/admin/proyectos");
        return new Creado(id, "PROPUESTO", fases.size(), despues.size(), avance(despues));
    }

    // ------------------------------------------------------------------------------------------- Responder

    /**
     * El administrador autoriza un cambio propuesto: el cliente lo ve y lo acepta. Quitar un paso de una fase que cierra el
     * equipo se aplica en este momento (queda ACEPTADO) y solo se avisa al cliente.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Respondido autorizar(UUID cambioId) {
        UsuarioSesion usuario = sesion.usuario();
        CambioFila c = cambio(cambioId);
        proyectos.bloquear(c.proyectoCodigo());
        exigirAdministrador(usuario, c.proyectoCodigo());
        exigirPlanAprobado(c);
        if (!c.estado().equals("PROPUESTO")) {
            throw new ConflictoException(YA_RESPONDIDO);
        }
        boolean seAplica = c.tipo().equals("QUITAR_PASO") && !c.pasoFaseApruebaCliente();
        Instant ahora = Instant.now(reloj);
        String nuevoEstado = seAplica ? "ACEPTADO" : "AUTORIZADO";
        if (!datos.autorizar(c.id(), nuevoEstado, usuario.id(), ahora)) {
            throw new ConflictoException(DATOS_DESACTUALIZADOS);
        }
        if (c.tipo().equals("AGREGAR_ALCANCE")) {
            int dias = (int) Math.max(0, ChronoUnit.DAYS.between(c.entregaActual(), c.nuevaEntrega()));
            datos.cotizarSolicitud(c.solicitudId(), c.valorAdicional(), dias);
        }
        if (seAplica) {
            aplicar(c);
        }

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("PROPUESTO", nuevoEstado));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, seAplica ? "Autorizó y aplicó un cambio de plan" : "Autorizó un cambio de plan",
                "proyecto", c.proyectoCodigo(), cambios);
        if (seAplica) {
            avisar(List.of(c.clienteId()), "CAMBIO_PLAN_APLICADO", "Se quitó un paso de tu plan",
                    "Se quitó «" + c.pasoTitulo() + "» de " + c.pasoFase() + " en " + c.proyecto() + ". El resto del plan sigue igual.",
                    "/cliente/proyectos/" + c.proyectoCodigo());
        } else {
            avisar(List.of(c.clienteId()), "CAMBIO_PLAN_AUTORIZADO", "Hay un cambio de plan por revisar",
                    "El equipo propone " + nombreDelTipo(c.tipo()) + " en " + c.proyecto() + ". Míralo y acepta o no el cambio.",
                    "/cliente/proyectos/" + c.proyectoCodigo());
        }
        avisarAlEquipo(c, seAplica ? "CAMBIO_PLAN_APLICADO" : "CAMBIO_PLAN_AUTORIZADO",
                seAplica ? "Se quitó el paso que propusiste" : "Tu cambio de plan quedó autorizado",
                seAplica ? "El administrador autorizó quitar «" + c.pasoTitulo() + "» y ya se aplicó."
                        : "El administrador autorizó " + nombreDelTipo(c.tipo()) + " en " + c.proyecto() + ". Falta la respuesta del cliente.");
        return new Respondido(c.id(), nuevoEstado, ahora);
    }

    /**
     * Rechaza un cambio con un motivo de 10 a 400 caracteres: el administrador, si está PROPUESTO; el cliente, si está AUTORIZADO.
     * El plan sigue igual.
     */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Respondido rechazar(UUID cambioId, String motivo) {
        UsuarioSesion usuario = sesion.usuario();
        CambioFila c = cambio(cambioId);
        proyectos.bloquear(c.proyectoCodigo());
        boolean administrador = esAdministrador(usuario);
        if (!administrador) {
            exigirClienteDueno(c, usuario, "rechazar un cambio del plan de");
        }
        String texto = motivo == null ? "" : motivo.trim();
        Acumulador errores = new Acumulador();
        if (texto.isEmpty()) {
            errores.agregar("motivo", "Escribe el motivo: " + (administrador ? "el equipo" : "el equipo y el administrador") + " lo verán.");
        } else if (texto.length() < 10) {
            errores.agregar("motivo", "Escribe al menos 10 caracteres.");
        } else if (texto.length() > 400) {
            errores.agregar("motivo", "Escribe máximo 400 caracteres.");
        }
        String esperado = administrador ? "PROPUESTO" : "AUTORIZADO";
        if (!c.estado().equals(esperado)) {
            throw new ConflictoException(administrador ? YA_RESPONDIDO : NO_ESPERA_TU_RESPUESTA);
        }
        errores.lanzarSiHay();

        Instant ahora = Instant.now(reloj);
        boolean hecho = administrador ? datos.rechazarComoAdministrador(c.id(), texto) : datos.rechazarComoCliente(c.id(), texto, ahora);
        if (!hecho) {
            throw new ConflictoException(DATOS_DESACTUALIZADOS);
        }
        if (!administrador && c.tipo().equals("AGREGAR_ALCANCE")) {
            datos.rechazarSolicitud(c.solicitudId());
        }
        String nuevoEstado = administrador ? "RECHAZADO_ADMIN" : "RECHAZADO_CLIENTE";
        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de(c.estado(), nuevoEstado));
        cambios.put("motivo", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Rechazó un cambio de plan", "proyecto", c.proyectoCodigo(), cambios);
        if (administrador) {
            avisarAlEquipo(c, "CAMBIO_PLAN_RECHAZADO", "No se autorizó tu cambio de plan", recortar(texto, 255));
        } else {
            avisarAlEquipo(c, "CAMBIO_PLAN_RECHAZADO", "El cliente no aceptó el cambio de plan", recortar(texto, 255));
            avisar(usuarios.administradoresActivos(), "CAMBIO_PLAN_RECHAZADO", "El cliente no aceptó un cambio de plan",
                    c.cliente() + " no aceptó el cambio en " + c.proyecto() + ".", "/admin/proyectos");
        }
        return new Respondido(c.id(), nuevoEstado, c.autorizadoAt());
    }

    /** El cliente dueño acepta un cambio autorizado: se aplica al plan en esta misma transacción. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Aceptado aceptar(UUID cambioId) {
        UsuarioSesion usuario = sesion.usuario();
        CambioFila c = cambio(cambioId);
        proyectos.bloquear(c.proyectoCodigo());
        exigirClienteDueno(c, usuario, "aceptar un cambio del plan de");
        exigirPlanAprobado(c);
        if (!c.estado().equals("AUTORIZADO")) {
            throw new ConflictoException(NO_ESPERA_TU_RESPUESTA);
        }
        if (!datos.aceptar(c.id(), Instant.now(reloj))) {
            throw new ConflictoException(DATOS_DESACTUALIZADOS);
        }
        aplicar(c);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estado", CambioDeValor.de("AUTORIZADO", "ACEPTADO"));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Aceptó un cambio de plan", "proyecto", c.proyectoCodigo(), cambios);
        avisarAlEquipo(c, "CAMBIO_PLAN_ACEPTADO", "El cliente aceptó el cambio de plan",
                c.cliente() + " aceptó " + nombreDelTipo(c.tipo()) + " en " + c.proyecto() + ". Ya está aplicado.");
        avisar(usuarios.administradoresActivos(), "CAMBIO_PLAN_ACEPTADO", "El cliente aceptó un cambio de plan",
                c.cliente() + " aceptó el cambio en " + c.proyecto() + ".", "/admin/proyectos");

        BigDecimal valorTotal = datos.valorCotizado(c.proyectoId()).add(datos.valorAdicionalAceptado(c.proyectoId()));
        return new Aceptado("ACEPTADO", datos.fases(c.proyectoId()).size(), datos.avance(c.proyectoId()),
                c.nuevaEntrega() != null ? c.nuevaEntrega() : c.entregaActual(), valorTotal);
    }

    // ------------------------------------------------------------------------------------------- Aplicar al plan

    /**
     * Aplica un cambio ya aceptado. Se vuelve a comprobar que el plan siga permitiéndolo: entre la propuesta y la respuesta el
     * proyecto pudo avanzar (por ejemplo, la fase que se iba a quitar empezó); si ya no se puede, 409 y no se aplica nada.
     */
    private void aplicar(CambioFila c) {
        switch (c.tipo()) {
            case "AGREGAR_FASE" -> {
                List<FaseDelPlan> fases = datos.fases(c.proyectoId());
                int minimo = posicionMinima(fases);
                if (c.posicion() < minimo || c.posicion() > fases.size() + 1 || fases.size() >= 8) {
                    throw new ConflictoException(PLAN_CAMBIO);
                }
                datos.agregarFase(c.proyectoId(), c.id(), c.posicion(), c.nombreFase(), c.apruebaCliente(), leerPasos(c.pasosPropuestos()));
            }
            case "QUITAR_FASE" -> {
                List<FaseDelPlan> fases = datos.fases(c.proyectoId());
                FaseDelPlan fase = fases.stream().filter(x -> x.id().equals(c.faseId())).findFirst().orElse(null);
                if (fase == null || !fase.estado().equals("PENDIENTE") || fases.size() <= 2
                        || fases.stream().noneMatch(x -> x.apruebaCliente() && !x.id().equals(fase.id()))) {
                    throw new ConflictoException(PLAN_CAMBIO);
                }
                datos.quitarFase(c.proyectoId(), fase.id());
            }
            case "QUITAR_PASO" -> {
                PasoDelPlan paso = datos.paso(c.pasoId()).orElseThrow(() -> new ConflictoException(PLAN_CAMBIO));
                if (!paso.estado().equals("PENDIENTE") || !Set.of("PENDIENTE", "EN_CURSO").contains(paso.faseEstado()) || paso.pasosQueQuedan() == 0) {
                    throw new ConflictoException(PLAN_CAMBIO);
                }
                datos.quitarPaso(paso.id());
            }
            case "AGREGAR_ALCANCE" -> {
                FaseDelPlan actual = datos.faseActual(c.proyectoId()).orElseThrow(() -> new ConflictoException(PLAN_CAMBIO));
                UUID paso = datos.agregarPasoDeAlcance(actual.id(), c.tituloPaso(), c.explicacionPaso());
                datos.aceptarSolicitud(c.solicitudId(), paso);
            }
            default -> {
            }
        }
        if (c.nuevaEntrega() != null) {
            datos.actualizarEntrega(c.proyectoId(), c.nuevaEntrega());
        }
    }

    // ------------------------------------------------------------------------------------------- Validaciones

    private static void validarMotivo(String motivo, Acumulador errores) {
        if (motivo.isEmpty()) {
            errores.agregar("motivo", "Cuéntale al administrador por qué hace falta este cambio.");
        } else if (motivo.length() < 20) {
            errores.agregar("motivo", "Escribe al menos 20 caracteres.");
        } else if (motivo.length() > 500) {
            errores.agregar("motivo", "Escribe máximo 500 caracteres.");
        }
    }

    private static LocalDate validarEntrega(String texto, Fila f, Acumulador errores) {
        if (texto == null || texto.isBlank()) {
            errores.agregar("nuevaEntrega", "Elige la nueva fecha de entrega.");
            return null;
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            errores.agregar("nuevaEntrega", "Elige una fecha válida.");
            return null;
        }
        if (f.fechaInicio() != null && fecha.isBefore(f.fechaInicio().plusDays(14))) {
            errores.agregar("nuevaEntrega", "La entrega debe ser al menos 14 días después del inicio del proyecto.");
            return null;
        }
        if (fecha.isAfter(ULTIMA_FECHA)) {
            errores.agregar("nuevaEntrega", "Elige una fecha de entrega hasta el 31 de diciembre de 2028.");
            return null;
        }
        return fecha;
    }

    private static BigDecimal validarValor(BigDecimal valor, Acumulador errores) {
        if (valor == null) {
            errores.agregar("valorAdicional", "Escribe el valor adicional (puede ser $0).");
            return BigDecimal.ZERO;
        }
        if (valor.stripTrailingZeros().scale() > 0 || valor.signum() < 0 || valor.compareTo(BigDecimal.valueOf(VALOR_MAXIMO)) > 0) {
            errores.agregar("valorAdicional", "El valor adicional debe ser un número entero entre $0 y $200.000.000");
            return BigDecimal.ZERO;
        }
        return valor.setScale(0, RoundingMode.UNNECESSARY);
    }

    /** La fase nueva va después de la fase actual (las ya empezadas no se mueven); devuelve su posición si es válida. */
    private Integer validarFaseNueva(String nombre, Integer posicion, List<PasoPlantilla> pasos, List<FaseDelPlan> fases, Acumulador errores) {
        if (nombre.length() < 3) {
            errores.agregar("nombreFase", "Escribe el nombre de la fase (al menos 3 caracteres).");
        } else if (nombre.length() > 60) {
            errores.agregar("nombreFase", "El nombre de la fase puede tener máximo 60 caracteres.");
        } else if (fases.stream().anyMatch(x -> x.nombre().trim().equalsIgnoreCase(nombre))) {
            errores.agregar("nombreFase", "Ya hay una fase con ese nombre en el plan");
        }
        if (pasos.isEmpty()) {
            errores.agregar("pasos", "Agrega al menos un paso.");
        } else if (pasos.size() > 12) {
            errores.agregar("pasos", "Cada fase puede tener máximo 12 pasos.");
        }
        for (PasoPlantilla paso : pasos) {
            if (paso.titulo().length() < 3 || paso.titulo().length() > 80) {
                errores.agregar("pasos", "El título de cada paso debe tener entre 3 y 80 caracteres.");
                break;
            }
            if (paso.explicacion() != null && paso.explicacion().length() > 160) {
                errores.agregar("pasos", "La explicación de un paso puede tener máximo 160 caracteres.");
                break;
            }
        }
        if (fases.size() >= 8) {
            errores.agregar("fases", "El plan puede tener máximo 8 fases");
        }
        int minimo = posicionMinima(fases);
        if (posicion == null || posicion < minimo || posicion > fases.size() + 1) {
            errores.agregar("posicion", "La fase nueva va después de la fase actual.");
            return null;
        }
        return posicion;
    }

    /** La primera posición libre: después de la última fase que ya empezó o terminó (RN-30). */
    private static int posicionMinima(List<FaseDelPlan> fases) {
        return fases.stream().filter(x -> !x.estado().equals("PENDIENTE")).mapToInt(FaseDelPlan::orden).max().orElse(0) + 1;
    }

    /** RN-28: de 2 a 8 fases y al menos una que apruebe el cliente. */
    private static void validarPlan(List<FaseDelPlan> fases) {
        Acumulador errores = new Acumulador();
        if (fases.size() < 2) {
            errores.agregar("fases", "El plan necesita al menos 2 fases");
        } else if (fases.size() > 8) {
            errores.agregar("fases", "El plan puede tener máximo 8 fases");
        }
        if (fases.stream().noneMatch(FaseDelPlan::apruebaCliente)) {
            errores.agregar("fases", "Marca al menos una fase que apruebe el cliente");
        }
        errores.lanzarSiHay();
    }

    // ------------------------------------------------------------------------------------------- Vistas

    private CambioVista vista(CambioFila c, UsuarioSesion usuario) {
        boolean abierto = abierto(c.estado());
        boolean seAplica = c.tipo().equals("QUITAR_PASO") && !c.pasoFaseApruebaCliente();
        Foto antes = null;
        Foto despues = null;
        if (abierto) {
            List<FaseDelPlan> fases = datos.fases(c.proyectoId());
            BigDecimal total = datos.valorCotizado(c.proyectoId()).add(datos.valorAdicionalAceptado(c.proyectoId()));
            antes = new Foto(porcentajes(fases), datos.avance(c.proyectoId()), c.entregaActual(), total);
            List<FaseDelPlan> nuevas = fases;
            if (c.tipo().equals("AGREGAR_FASE") && c.posicion() != null) {
                List<FaseDelPlan> lista = new ArrayList<>(fases);
                lista.add(Math.min(Math.max(c.posicion() - 1, 0), lista.size()), new FaseDelPlan(null, c.posicion(), c.nombreFase(), "PENDIENTE", c.apruebaCliente()));
                nuevas = lista;
            } else if (c.tipo().equals("QUITAR_FASE")) {
                nuevas = fases.stream().filter(x -> !x.id().equals(c.faseId())).toList();
            }
            despues = new Foto(porcentajes(nuevas), avance(nuevas), c.nuevaEntrega() != null ? c.nuevaEntrega() : c.entregaActual(),
                    total.add(c.valorAdicional()));
        }
        List<PasoNuevo> pasos = c.pasosPropuestos() == null ? null : leerPasos(c.pasosPropuestos()).stream()
                .map(x -> new PasoNuevo(x.titulo(), x.explicacion())).toList();
        return new CambioVista(c.id(), c.proyectoCodigo(), c.proyecto(), c.cliente(), c.tipo(), c.estado(), c.motivo(), c.propuestoPorNombre(),
                c.creadoAt(), c.autorizadoAt(), c.respondidoAt(), c.motivoRechazo(), c.tipo().equals("QUITAR_FASE") ? c.faseNombre() : c.nombreFase(),
                c.posicion(), c.tipo().equals("AGREGAR_FASE") ? c.apruebaCliente() : null, pasos, c.pasoTitulo(), c.pasoFase(), c.tituloPaso(),
                c.explicacionPaso(), c.solicitudDescripcion(), c.nuevaEntrega(), c.valorAdicional(), antes, despues, seAplica,
                esAdministrador(usuario) && c.estado().equals("PROPUESTO"), c.clienteId().equals(usuario.id()) && c.estado().equals("AUTORIZADO"));
    }

    private static List<FaseVista> porcentajes(List<FaseDelPlan> fases) {
        BigDecimal cada = fases.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(fases.size()), 1, RoundingMode.HALF_UP);
        return fases.stream().map(x -> new FaseVista(x.nombre(), cada, x.estado())).toList();
    }

    /** RN-08: 100 por las fases aprobadas entre las fases del plan. */
    private static BigDecimal avance(List<FaseDelPlan> fases) {
        if (fases.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long aprobadas = fases.stream().filter(x -> x.estado().equals("APROBADA")).count();
        return BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(aprobadas)).divide(BigDecimal.valueOf(fases.size()), 2, RoundingMode.HALF_UP);
    }

    private static boolean abierto(String estado) {
        return estado.equals("PROPUESTO") || estado.equals("AUTORIZADO");
    }

    private static String nombreDelTipo(String tipo) {
        return switch (tipo) {
            case "AGREGAR_FASE" -> "agregar una fase";
            case "QUITAR_FASE" -> "quitar una fase";
            case "CAMBIAR_FECHA" -> "cambiar la fecha de entrega";
            case "QUITAR_PASO" -> "quitar un paso";
            default -> "cotizar un trabajo nuevo";
        };
    }

    // ------------------------------------------------------------------------------------------- JSON

    private String escribir(List<PasoNuevo> pasos) {
        try {
            return json.writeValueAsString(pasos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar los pasos de la fase nueva", e);
        }
    }

    private List<PasoPlantilla> leerPasos(String texto) {
        try {
            List<PasoNuevo> pasos = json.readValue(texto, new TypeReference<List<PasoNuevo>>() {
            });
            List<PasoPlantilla> resultado = new ArrayList<>();
            for (PasoNuevo p : pasos) {
                resultado.add(new PasoPlantilla(resultado.size() + 1, p.titulo(), p.explicacion()));
            }
            return resultado;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Los pasos del cambio no se pudieron leer", e);
        }
    }

    // ------------------------------------------------------------------------------------------- Permisos

    private static boolean esAdministrador(UsuarioSesion usuario) {
        return usuario.tienePermiso("proyectos.gestionar");
    }

    private Fila buscar(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
        return proyectos.porCodigo(codigo, LocalDate.now(reloj)).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private CambioFila cambio(UUID id) {
        return datos.porId(id).orElseThrow(() -> new NoEncontradoException(CAMBIO_NO_ENCONTRADO));
    }

    /** Solo el Product Owner del equipo del proyecto: un cliente ajeno recibe 404 y los demás 403; queda en la auditoría. */
    private void exigirProductOwner(Fila f, UsuarioSesion usuario, String accion) {
        Optional<UUID> equipo = equipos.equipoDelProductOwner(usuario.id());
        if (equipo.isPresent() && equipo.get().equals(f.equipoId())) {
            return;
        }
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Intentó " + accion + " otro equipo", "proyecto", f.codigo(), new LinkedHashMap<>());
        if (esAdministrador(usuario)) {
            throw new ProhibidoException(SOLO_PRODUCT_OWNER);
        }
        if (usuario.tienePermiso("proyectos.registrar_avance") || usuario.tienePermiso("proyectos.planificar")) {
            throw new ProhibidoException(NO_ES_DE_TU_EQUIPO);
        }
        throw new NoEncontradoException(NO_ENCONTRADO);
    }

    private void exigirAdministrador(UsuarioSesion usuario, String codigo) {
        if (!esAdministrador(usuario)) {
            auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Intentó autorizar un cambio de plan sin ser administrador", "proyecto", codigo,
                    new LinkedHashMap<>());
            throw new ProhibidoException("Solo la administración autoriza los cambios del plan");
        }
    }

    /** Solo el cliente dueño; otro cliente recibe 404 (RN-10) y el equipo, 403. */
    private void exigirClienteDueno(CambioFila c, UsuarioSesion usuario, String accion) {
        if (c.clienteId().equals(usuario.id())) {
            return;
        }
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Intentó " + accion + " un proyecto ajeno", "proyecto", c.proyectoCodigo(),
                new LinkedHashMap<>());
        if (usuario.tienePermiso("proyectos.registrar_avance") || usuario.tienePermiso("proyectos.planificar")) {
            throw new ProhibidoException("Solo el cliente dueño del proyecto responde este cambio");
        }
        throw new NoEncontradoException(CAMBIO_NO_ENCONTRADO);
    }

    private void exigirPlanAprobado(CambioFila c) {
        Fila f = proyectos.porCodigo(c.proyectoCodigo(), LocalDate.now(reloj)).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
        if (!f.estado().equals("ACTIVO") || !f.estadoPlan().equals("APROBADO")) {
            throw new ConflictoException(ESTADO_NO_ADMITE);
        }
    }

    // ------------------------------------------------------------------------------------------- Avisos

    private void avisar(List<UUID> destinatarios, String tipo, String titulo, String texto, String enlace) {
        if (!destinatarios.isEmpty()) {
            eventos.publishEvent(new AvisoSolicitado(destinatarios, tipo, recortar(titulo, 80), recortar(texto, 255), enlace));
        }
    }

    private void avisarAlEquipo(CambioFila c, String tipo, String titulo, String texto) {
        if (c.equipoId() != null) {
            equipos.productOwner(c.equipoId()).map(ProductOwner::usuarioId).ifPresent(
                    destino -> avisar(List.of(destino), tipo, titulo, texto, "/equipo/proyectos/" + c.proyectoCodigo() + "/registrar"));
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
