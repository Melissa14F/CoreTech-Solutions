package co.coretech.portal.proyectos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.acceso.DirectorioUsuarios;
import co.coretech.portal.acceso.DirectorioUsuarios.ContactoCorreo;
import co.coretech.portal.catalogo.DirectorioServicios;
import co.coretech.portal.comunicacion.AvisoSolicitado;
import co.coretech.portal.compartido.aplicacion.CalendarioHabil;
import co.coretech.portal.compartido.aplicacion.ConflictoException;
import co.coretech.portal.compartido.aplicacion.NoEncontradoException;
import co.coretech.portal.compartido.aplicacion.ProhibidoException;
import co.coretech.portal.compartido.aplicacion.RegistroAuditoria;
import co.coretech.portal.compartido.aplicacion.SesionActual;
import co.coretech.portal.compartido.aplicacion.UsuarioSesion;
import co.coretech.portal.compartido.aplicacion.ValidacionException;
import co.coretech.portal.compartido.aplicacion.ValidacionException.Acumulador;
import co.coretech.portal.compartido.dominio.CambioDeValor;
import co.coretech.portal.compartido.dominio.CategoriaAuditoria;
import co.coretech.portal.especialidad.EquipoConsultas;
import co.coretech.portal.especialidad.EquipoConsultas.Equipo;
import co.coretech.portal.proyectos.CitasParaProyectos;
import co.coretech.portal.proyectos.CitasParaProyectos.CitaOrigen;
import co.coretech.portal.proyectos.aplicacion.ServicioPlantillas.FasePlantilla;
import co.coretech.portal.proyectos.datos.PlanesJdbc;
import co.coretech.portal.proyectos.datos.PlanesJdbc.Cotizacion;
import co.coretech.portal.proyectos.datos.PlanesJdbc.Respuesta;
import co.coretech.portal.proyectos.datos.ProyectosJdbc;
import co.coretech.portal.proyectos.datos.ProyectosJdbc.Fila;

/**
 * Plan de trabajo y cotización de un proyecto (HU-28, HU-29, RN-06, RN-27 a RN-29).
 * <p>
 * Lo edita y lo envía el Product Owner del equipo o el administrador (permiso {@code proyectos.planificar}); lo responde
 * el cliente dueño del proyecto (aprobar, pedir ajustes o rechazar). Solo se edita en BORRADOR o AJUSTES_PEDIDOS. Al enviar
 * se fijan en una sola sentencia el estado y las fechas de plazo (3 y 10 días hábiles). Al aprobar, primero cambia el estado
 * del plan y después empieza la fase 1 (D-26). Los accesos no permitidos (otro equipo, proyecto ajeno) quedan en la
 * auditoría aunque la petición responda 403 o 404, por eso esos métodos no revierten en esos dos casos.
 */
@Service
public class ServicioPlan {

    static final String NO_ENCONTRADO = "Proyecto no encontrado";
    static final String NO_ESPERA_RESPUESTA = "Este plan no está esperando tu respuesta";
    static final String NO_ES_DE_TU_EQUIPO = "Este proyecto no es de tu equipo";
    static final long VALOR_MINIMO = 1_000_000L;
    static final long VALOR_MAXIMO = 200_000_000L;
    static final LocalDate ULTIMA_FECHA = LocalDate.of(2028, 12, 31);

    /** Lo que llega al guardar o enviar. La fecha viene como texto AAAA-MM-DD; todo es opcional al guardar un borrador. */
    public record Solicitud(List<FasePlantilla> fases, BigDecimal valorCotizado, String condicionesCotizacion,
            String fechaEstimadaEntrega) {
    }

    /** Un comentario o un motivo del cliente. */
    public record Comentario(String texto, Instant fecha) {
    }

    /**
     * El plan como lo ven el equipo y el cliente. {@code editable} es verdadero solo para el equipo con el plan en borrador
     * o con ajustes pedidos; {@code porcentajePorFase} es el avance que suma cada fase.
     */
    public record VistaPlan(String codigo, String nombre, String estado, String estadoPlan, String cliente,
            String productOwner, String servicio, String citaCodigo, Instant citaFecha, LocalDate fechaInicio,
            LocalDate fechaEstimadaEntrega, BigDecimal valorCotizado, String condicionesCotizacion, Instant creadoAt,
            Instant enviadoAt, Instant aprobadoAt, LocalDate plazoRespuesta, LocalDate venceEl, List<FasePlantilla> fases,
            BigDecimal porcentajePorFase, Comentario ajustesPedidos, Comentario rechazo, boolean editable) {
    }

    /** Resultado de enviar el plan (HU-28). */
    public record Enviado(String estadoPlan, BigDecimal porcentajePorFase, LocalDate plazoRespuesta, LocalDate venceEl) {
    }

    /** Resultado de aprobar (HU-29). */
    public record Aprobado(String estadoPlan, String estado, Instant planAprobadoAt) {
    }

    /** Resultado de pedir ajustes o rechazar (HU-29). */
    public record Respondido(String estadoPlan, String estado, UUID respuestaId) {
    }

    private final ProyectosJdbc proyectos;
    private final PlanesJdbc planes;
    private final CitasParaProyectos citas;
    private final EquipoConsultas equipos;
    private final DirectorioUsuarios usuarios;
    private final DirectorioServicios servicios;
    private final RegistroAuditoria auditoria;
    private final SesionActual sesion;
    private final ApplicationEventPublisher eventos;
    private final CalendarioHabil calendario;
    private final Clock reloj;

    public ServicioPlan(ProyectosJdbc proyectos, PlanesJdbc planes, CitasParaProyectos citas, EquipoConsultas equipos,
            DirectorioUsuarios usuarios, DirectorioServicios servicios, RegistroAuditoria auditoria, SesionActual sesion,
            ApplicationEventPublisher eventos, CalendarioHabil calendario, Clock reloj) {
        this.proyectos = proyectos;
        this.planes = planes;
        this.citas = citas;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.servicios = servicios;
        this.auditoria = auditoria;
        this.sesion = sesion;
        this.eventos = eventos;
        this.calendario = calendario;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------------------------------- Leer

    /** Lo lee el equipo (Product Owner del proyecto o administrador) o el cliente dueño; para el cliente un borrador no existe aún. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public VistaPlan obtener(String codigo) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = buscar(codigo);
        if (usuario.tienePermiso("proyectos.planificar")) {
            exigirEquipo(f, usuario, "consultar");
        } else {
            exigirCliente(f, usuario, "consultar");
            if (f.estadoPlan().equals("BORRADOR")) {
                throw new ConflictoException("Todavía no hay un plan para revisar: el equipo lo está preparando");
            }
        }
        return vista(f, usuario.tienePermiso("proyectos.planificar"));
    }

    // ------------------------------------------------------------------------------------------- Editar y enviar (HU-28)

    /** Guarda el borrador sin enviarlo. Con ajustes pedidos la cotización ya existe, así que debe seguir completa. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public VistaPlan guardar(String codigo, Solicitud pedida) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = bloqueado(codigo);
        exigirEquipo(f, usuario, "editar");
        exigirEditable(f);

        List<FasePlantilla> fases = ServicioPlantillas.normalizar(pedida.fases());
        Acumulador errores = new Acumulador();
        validarFases(fases, errores);
        Cotizacion actual = planes.cotizacion(f.id());
        boolean completa = f.estadoPlan().equals("AJUSTES_PEDIDOS");
        Datos datos = validarCotizacion(pedida, f, completa, errores);
        errores.lanzarSiHay();

        planes.reemplazarFases(f.id(), fases);
        planes.guardarCotizacion(f.id(), datos.valor(), datos.condiciones(), datos.entrega());

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("fases", CambioDeValor.de(planes.fases(f.id()).size(), fases.size()));
        if (datos.valor() != null || actual.valor() != null) {
            cambios.put("valorCotizado", CambioDeValor.de(actual.valor(), datos.valor()));
        }
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Guardó el borrador del plan de trabajo", "proyecto", f.codigo(), cambios);
        return vista(buscar(codigo), true);
    }

    /** Envía el plan con su cotización al cliente (RN-29): 3 días hábiles para responder y vence a los 10. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Enviado enviar(String codigo, Solicitud pedida) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = bloqueado(codigo);
        exigirEquipo(f, usuario, "enviar");
        exigirEditable(f);

        List<FasePlantilla> fases = ServicioPlantillas.normalizar(pedida.fases());
        Acumulador errores = new Acumulador();
        validarFases(fases, errores);
        Datos datos = validarCotizacion(pedida, f, true, errores);
        errores.lanzarSiHay();

        LocalDate hoy = LocalDate.now(reloj);
        LocalDate plazo = calendario.sumarDiasHabiles(hoy, 3);
        LocalDate vence = calendario.sumarDiasHabiles(hoy, 10);
        // Las fases se guardan primero: la base revisa RN-28 al pasar a ENVIADO sobre lo que haya guardado.
        planes.reemplazarFases(f.id(), fases);
        planes.enviar(f.id(), datos.valor(), datos.condiciones(), datos.entrega(), plazo, vence);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estadoPlan", CambioDeValor.de(f.estadoPlan(), "ENVIADO"));
        cambios.put("valorCotizado", CambioDeValor.de(f.valorCotizado(), datos.valor()));
        cambios.put("fechaEstimadaEntrega", CambioDeValor.de(f.fechaEntrega().toString(), datos.entrega().toString()));
        cambios.put("fases", CambioDeValor.nuevo(fases.size()));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Envió el plan de trabajo y la cotización", "proyecto", f.codigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(f.clienteId(), "PLAN_ENVIADO", "Plan y cotización listos",
                recortar(f.nombre() + ": revisa el plan de trabajo y la cotización. Tienes hasta el " + Fechas.conDia(plazo)
                        + " para aprobarlos o pedir ajustes."),
                "/cliente/proyectos/" + f.codigo() + "/propuesta"));
        return new Enviado("ENVIADO", porcentajePorFase(fases.size()), plazo, vence);
    }

    /** Vuelve a avisar al cliente que tiene el plan por responder (HU-28, plan enviado). */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public void recordar(String codigo) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = bloqueado(codigo);
        exigirEquipo(f, usuario, "recordar");
        if (!f.estadoPlan().equals("ENVIADO")) {
            throw new ConflictoException("Este plan no está esperando la respuesta de " + nombreCliente(f));
        }
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Recordó al cliente su propuesta pendiente", "proyecto", f.codigo(),
                new LinkedHashMap<>());
        eventos.publishEvent(AvisoSolicitado.para(f.clienteId(), "PLAN_RECORDATORIO", "Tu propuesta espera tu respuesta",
                recortar(f.nombre() + ": tienes hasta el " + Fechas.conDia(f.planPlazoRespuesta())
                        + " para aprobar el plan y la cotización o pedir ajustes."),
                "/cliente/proyectos/" + f.codigo() + "/propuesta"));
    }

    // ------------------------------------------------------------------------------------------- Responder (HU-29)

    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Aprobado aprobar(String codigo, boolean aceptaPlanYCotizacion) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = bloqueado(codigo);
        exigirCliente(f, usuario, "aprobar");
        exigirEnviado(f);
        if (!aceptaPlanYCotizacion) {
            throw new ValidacionException(List.of(new ValidacionException.Falla("aceptaPlanYCotizacion",
                    "Confirma que leíste el plan y la cotización.")));
        }
        planes.aprobar(f.id());
        planes.iniciarPrimeraFase(f.id());
        planes.insertarRespuesta(f.id(), usuario.id(), "APROBADO", null);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estadoPlan", CambioDeValor.de("ENVIADO", "APROBADO"));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Aprobó el plan de trabajo y la cotización", "proyecto", f.codigo(), cambios);

        avisarAlEquipo(f, "PLAN_APROBADO", "El cliente aprobó el plan de " + f.codigo(),
                nombreCliente(f) + " aprobó el plan y la cotización de " + f.nombre() + ": empieza la fase 1.");
        Cotizacion c = planes.cotizacion(f.id());
        return new Aprobado("APROBADO", f.estado(), c.aprobadoAt());
    }

    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Respondido pedirAjustes(String codigo, String comentario) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = bloqueado(codigo);
        exigirCliente(f, usuario, "pedir ajustes");
        exigirEnviado(f);
        String texto = comentario == null ? "" : comentario.trim();
        String po = productOwnerDe(f.equipoId());
        validarRespuesta(texto, "comentario", "Cuéntanos qué quieres cambiar.",
                "Escribe al menos 20 caracteres para que " + (po.isEmpty() ? "el equipo" : po) + " te entienda.");

        planes.cambiarEstadoPlan(f.id(), "AJUSTES_PEDIDOS");
        UUID respuesta = planes.insertarRespuesta(f.id(), usuario.id(), "AJUSTES_PEDIDOS", texto);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estadoPlan", CambioDeValor.de("ENVIADO", "AJUSTES_PEDIDOS"));
        cambios.put("comentario", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Pidió ajustes al plan de trabajo", "proyecto", f.codigo(), cambios);

        avisarAlEquipo(f, "PLAN_AJUSTES_PEDIDOS", "El cliente pidió ajustes al plan de " + f.codigo(),
                nombreCliente(f) + " pide cambios en " + f.nombre() + ": " + texto);
        return new Respondido("AJUSTES_PEDIDOS", f.estado(), respuesta);
    }

    /** Rechaza la propuesta: plan RECHAZADO y proyecto PAUSADO (el equipo libera su cupo); la administración decide qué sigue. */
    @Transactional(noRollbackFor = { ProhibidoException.class, NoEncontradoException.class })
    public Respondido rechazar(String codigo, String motivo) {
        UsuarioSesion usuario = sesion.usuario();
        Fila f = bloqueado(codigo);
        exigirCliente(f, usuario, "rechazar");
        exigirEnviado(f);
        String texto = motivo == null ? "" : motivo.trim();
        validarRespuesta(texto, "motivo", "Cuéntanos por qué rechazas la propuesta.",
                "Cuéntanos por qué la rechazas (mínimo 20 caracteres)");

        planes.cambiarEstadoPlan(f.id(), "RECHAZADO");
        UUID respuesta = planes.insertarRespuesta(f.id(), usuario.id(), "RECHAZADO", texto);

        Map<String, CambioDeValor> cambios = new LinkedHashMap<>();
        cambios.put("estadoPlan", CambioDeValor.de("ENVIADO", "RECHAZADO"));
        cambios.put("estado", CambioDeValor.de(f.estado(), "PAUSADO"));
        cambios.put("motivo", CambioDeValor.nuevo(texto));
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, "Rechazó el plan de trabajo y la cotización", "proyecto", f.codigo(), cambios);

        eventos.publishEvent(AvisoSolicitado.para(f.clienteId(), "PLAN_RECHAZADO_CLIENTE", "Rechazaste la propuesta de " + f.codigo(),
                recortar(f.nombre() + " quedó en pausa. Si no respondes en 30 días, el proyecto se cancelará."),
                "/cliente/proyectos/" + f.codigo() + "/propuesta"));
        avisarAlEquipo(f, "PLAN_RECHAZADO", "El cliente rechazó la propuesta de " + f.codigo(),
                nombreCliente(f) + " rechazó el plan de " + f.nombre() + ": " + texto);
        List<UUID> administradores = usuarios.administradoresActivos();
        if (!administradores.isEmpty()) {
            eventos.publishEvent(new AvisoSolicitado(administradores, "PLAN_RECHAZADO", "Propuesta rechazada: " + f.codigo(),
                    recortar(nombreCliente(f) + " rechazó el plan de " + f.nombre() + ". Contáctalo; el proyecto quedó en pausa."),
                    "/admin/proyectos?buscar=" + f.codigo()));
        }
        return new Respondido("RECHAZADO", "PAUSADO", respuesta);
    }

    // ------------------------------------------------------------------------------------------- Validaciones

    private record Datos(BigDecimal valor, String condiciones, LocalDate entrega) {
    }

    private static void validarFases(List<FasePlantilla> fases, Acumulador errores) {
        try {
            ServicioPlantillas.validar(fases, "el plan");
        } catch (ValidacionException e) {
            e.fallas().forEach(falla -> errores.agregar(falla.campo(), falla.mensaje()));
        }
    }

    /**
     * RN-29: valor entre $1.000.000 y $200.000.000, entrega al menos 14 días después del inicio y qué incluye (20 a 600).
     * Con {@code obligatoria} falso (borrador) solo se revisa lo que venga escrito.
     */
    private static Datos validarCotizacion(Solicitud pedida, Fila f, boolean obligatoria, Acumulador errores) {
        BigDecimal valor = pedida.valorCotizado();
        String condiciones = pedida.condicionesCotizacion() == null ? "" : pedida.condicionesCotizacion().trim();
        String textoFecha = pedida.fechaEstimadaEntrega() == null ? "" : pedida.fechaEstimadaEntrega().trim();

        if (valor == null) {
            if (obligatoria) {
                errores.agregar("valorCotizado", "Escribe el valor del proyecto.");
            }
        } else if (valor.signum() < 0 || valor.stripTrailingZeros().scale() > 0 || valor.compareTo(BigDecimal.valueOf(VALOR_MINIMO)) < 0
                || valor.compareTo(BigDecimal.valueOf(VALOR_MAXIMO)) > 0) {
            errores.agregar("valorCotizado", "El valor debe estar entre $1.000.000 y $200.000.000.");
        }

        if (condiciones.isEmpty()) {
            if (obligatoria) {
                errores.agregar("condicionesCotizacion", "Escribe qué incluye el valor.");
            }
        } else if (condiciones.length() < 20) {
            errores.agregar("condicionesCotizacion", "Escribe al menos 20 caracteres.");
        } else if (condiciones.length() > 600) {
            errores.agregar("condicionesCotizacion", "Escribe máximo 600 caracteres.");
        }
        if (obligatoria && (errores.tiene("valorCotizado") || errores.tiene("condicionesCotizacion"))
                && (valor == null || condiciones.isEmpty())) {
            errores.agregar("cotizacion", "Completa el valor y lo que incluye antes de enviar el plan");
        }

        LocalDate entrega = null;
        if (textoFecha.isEmpty()) {
            if (obligatoria) {
                errores.agregar("fechaEstimadaEntrega", "Elige la fecha estimada de entrega.");
            }
        } else {
            try {
                entrega = LocalDate.parse(textoFecha);
                if (entrega.isBefore(f.fechaInicio().plusDays(14))) {
                    errores.agregar("fechaEstimadaEntrega", "La entrega debe ser al menos 2 semanas después del inicio ("
                            + Fechas.corta(f.fechaInicio()) + ").");
                } else if (entrega.isAfter(ULTIMA_FECHA)) {
                    errores.agregar("fechaEstimadaEntrega", "Revisa el año de la fecha.");
                }
            } catch (DateTimeParseException e) {
                errores.agregar("fechaEstimadaEntrega", "Escribe la fecha como AAAA-MM-DD.");
            }
        }
        return new Datos(valor == null ? null : valor.setScale(0, java.math.RoundingMode.DOWN), condiciones.isEmpty() ? null : condiciones, entrega);
    }

    private static void validarRespuesta(String texto, String campo, String vacio, String corto) {
        var errores = new Acumulador();
        if (texto.isEmpty()) {
            errores.agregar(campo, vacio);
        } else if (texto.length() < 20) {
            errores.agregar(campo, corto);
        } else if (texto.length() > 600) {
            errores.agregar(campo, "Escribe máximo 600 caracteres.");
        }
        errores.lanzarSiHay();
    }

    // ------------------------------------------------------------------------------------------- Permisos y estado

    /** Solo el Product Owner del equipo del proyecto o el administrador; si no, 403 y queda en la auditoría. */
    private void exigirEquipo(Fila f, UsuarioSesion usuario, String accion) {
        if (usuario.tienePermiso("proyectos.gestionar")) {
            return;
        }
        Optional<UUID> equipo = equipos.equipoDelProductOwner(usuario.id());
        if (equipo.isEmpty() || !equipo.get().equals(f.equipoId())) {
            denegar("Intentó " + accion + " el plan de otro equipo", f);
            throw new ProhibidoException(NO_ES_DE_TU_EQUIPO);
        }
    }

    /** Un proyecto de otro cliente no se revela: 404 y queda en la auditoría (RN-10). */
    private void exigirCliente(Fila f, UsuarioSesion usuario, String accion) {
        if (!f.clienteId().equals(usuario.id())) {
            denegar("Intentó " + accion + " el plan de un proyecto ajeno", f);
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
    }

    private void denegar(String accion, Fila f) {
        auditoria.registrar(CategoriaAuditoria.PROYECTOS, accion, "proyecto", f.codigo(), new LinkedHashMap<>());
    }

    private void exigirEditable(Fila f) {
        if (!f.estado().equals("ACTIVO")) {
            throw new ConflictoException("Solo se prepara el plan de un proyecto activo");
        }
        switch (f.estadoPlan()) {
            case "BORRADOR", "AJUSTES_PEDIDOS" -> {
            }
            case "ENVIADO" -> throw new ConflictoException("Enviaste el plan a " + nombreCliente(f)
                    + "; si pide ajustes, vuelve a ti con sus comentarios para que lo ajustes y lo reenvíes");
            case "APROBADO" -> throw new ConflictoException(
                    "Este plan ya fue aprobado: cualquier cambio necesita la autorización del administrador y la aceptación del cliente");
            default -> throw new ConflictoException("Este plan está " + f.estadoPlan().toLowerCase()
                    + ": reanuda el proyecto para prepararlo de nuevo");
        }
    }

    private static void exigirEnviado(Fila f) {
        if (!f.estadoPlan().equals("ENVIADO") || !f.estado().equals("ACTIVO")) {
            throw new ConflictoException(NO_ESPERA_RESPUESTA);
        }
    }

    // ------------------------------------------------------------------------------------------- Ayudas

    private Fila bloqueado(String codigo) {
        codigoValido(codigo);
        proyectos.bloquear(codigo);
        return buscar(codigo);
    }

    private Fila buscar(String codigo) {
        codigoValido(codigo);
        return proyectos.porCodigo(codigo, LocalDate.now(reloj)).orElseThrow(() -> new NoEncontradoException(NO_ENCONTRADO));
    }

    private static void codigoValido(String codigo) {
        if (codigo == null || !codigo.matches("P-\\d{4,}")) {
            throw new NoEncontradoException(NO_ENCONTRADO);
        }
    }

    private VistaPlan vista(Fila f, boolean paraElEquipo) {
        Cotizacion c = planes.cotizacion(f.id());
        List<FasePlantilla> fases = planes.fases(f.id());
        ContactoCorreo cliente = usuarios.perfiles(List.of(f.clienteId())).get(f.clienteId());
        Equipo equipo = f.equipoId() == null ? null : equipos.porIds(List.of(f.equipoId())).get(f.equipoId());
        Optional<CitaOrigen> cita = f.citaId() == null ? Optional.empty() : citas.buscar(f.citaId());
        Comentario ajustes = f.estadoPlan().equals("AJUSTES_PEDIDOS") ? comentario(planes.ultimaRespuesta(f.id(), "AJUSTES_PEDIDOS")) : null;
        Comentario rechazo = f.estadoPlan().equals("RECHAZADO") ? comentario(planes.ultimaRespuesta(f.id(), "RECHAZADO")) : null;
        boolean editable = paraElEquipo && f.estado().equals("ACTIVO") && List.of("BORRADOR", "AJUSTES_PEDIDOS").contains(f.estadoPlan());
        return new VistaPlan(f.codigo(), f.nombre(), f.estado(), f.estadoPlan(), cliente == null ? "" : cliente.nombre(),
                equipo == null ? "" : equipo.productOwner(), servicios.nombres(List.of(f.servicioId())).getOrDefault(f.servicioId(), ""),
                cita.map(CitaOrigen::codigo).orElse(null), cita.map(CitaOrigen::fechaHora).orElse(null), f.fechaInicio(),
                c.entrega(), c.valor(), c.condiciones(), f.creadoAt(), c.enviadoAt(), c.aprobadoAt(), f.planPlazoRespuesta(),
                f.planVenceEl(), fases, porcentajePorFase(fases.size()), ajustes, rechazo, editable);
    }

    private static Comentario comentario(Optional<Respuesta> r) {
        return r.map(x -> new Comentario(x.comentario(), x.creadaEn())).orElse(null);
    }

    private static BigDecimal porcentajePorFase(int fases) {
        return fases == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(fases), 1, java.math.RoundingMode.HALF_UP);
    }

    private String nombreCliente(Fila f) {
        ContactoCorreo cliente = usuarios.perfiles(List.of(f.clienteId())).get(f.clienteId());
        return cliente == null ? "el cliente" : cliente.nombre();
    }

    private String productOwnerDe(UUID equipoId) {
        Equipo e = equipoId == null ? null : equipos.porIds(List.of(equipoId)).get(equipoId);
        return e == null ? "" : e.productOwner();
    }

    /** Avisa al Product Owner del equipo del proyecto. */
    private void avisarAlEquipo(Fila f, String tipo, String titulo, String texto) {
        Equipo e = f.equipoId() == null ? null : equipos.porIds(List.of(f.equipoId())).get(f.equipoId());
        if (e != null) {
            eventos.publishEvent(AvisoSolicitado.para(e.productOwnerId(), tipo, recortar(titulo, 80), recortar(texto),
                    "/equipo/proyectos/" + f.codigo() + "/plan"));
        }
    }

    private static String recortar(String texto) {
        return recortar(texto, 255);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

}
