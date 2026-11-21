package co.coretech.portal.dashboard.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.coretech.portal.compartido.aplicacion.ReglaNegocioException;
import co.coretech.portal.dashboard.datos.DashboardJdbc;
import co.coretech.portal.dashboard.datos.DashboardJdbc.Actividad;
import co.coretech.portal.dashboard.datos.DashboardJdbc.CambioPorAutorizar;
import co.coretech.portal.dashboard.datos.DashboardJdbc.CitaEnAlerta;
import co.coretech.portal.dashboard.datos.DashboardJdbc.CitasPorEstado;
import co.coretech.portal.dashboard.datos.DashboardJdbc.Conteos;
import co.coretech.portal.dashboard.datos.DashboardJdbc.FaseVencida;
import co.coretech.portal.dashboard.datos.DashboardJdbc.GarantiaCumplida;
import co.coretech.portal.dashboard.datos.DashboardJdbc.ProyectoEnRiesgo;

/**
 * El dashboard del administrador (HU-23, RF-49): indicadores, alertas de lo que requiere atención y la carga de los equipos.
 * Todo se calcula al pedirlo con consultas de solo lectura; el «hoy» sale del {@code Clock} (America/Bogota). El periodo
 * (semana, mes o trimestre, por defecto el mes) mueve el indicador de citas, su comparación y las citas por estado.
 */
@Service
public class ServicioDashboard {

    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("EEE d, h:mm a", ES);
    private static final int MESES_DE_HISTORIAL = 6;

    /** Una alerta de «Requiere atención». {@code nivel}: CRITICA, ADVERTENCIA o INFO; el enlace es una ruta de la SPA. */
    public record Alerta(String id, String nivel, String titulo, String texto, String enlace, String accion) {
    }

    public record IndicadorCitas(String etiqueta, int total, int anterior, Integer variacion, String etiquetaAnterior) {
    }

    public record IndicadorConversion(int proyectos, int citas, Integer porcentaje) {
    }

    public record IndicadorProyectos(int activos, int capacidad, int porcentaje, int planesPorAprobar) {
    }

    public record IndicadorEntregas(int aTiempo, int total, Integer porcentaje, int anio) {
    }

    public record Indicadores(IndicadorCitas citas, IndicadorConversion conversion, IndicadorProyectos proyectos, IndicadorEntregas entregas) {
    }

    public record MesVista(String mes, String etiqueta, int citas, boolean parcial) {
    }

    public record EquipoVista(String id, String productOwner, int activos, int maximo, int libres) {
    }

    public record FaseVista(String fase, int proyectos) {
    }

    public record ServicioVista(String servicio, int citas) {
    }

    /** {@code promedioDiasParaConfirmar} (días hábiles, meta: menos de 1) es nulo si en el periodo no se confirmó ninguna cita. */
    public record EstadosVista(int pendientes, int confirmadas, int atendidas, int canceladas, int total, Double promedioDiasParaConfirmar) {
    }

    public record ActividadVista(Instant fecha, String usuario, String categoria, String accion, String entidad, String entidadId) {
    }

    /** {@code sinDatos} es verdadero cuando no hay citas ni proyectos en el periodo: la pantalla dice «Sin datos en este periodo». */
    public record Tablero(Instant generadoAt, LocalDate corte, String periodo, String etiquetaPeriodo, boolean sinDatos,
            Indicadores indicadores, Conteos conteos, List<Alerta> alertas, List<MesVista> citasPorMes, List<EquipoVista> carga,
            List<FaseVista> proyectosPorFase, List<ServicioVista> servicios, EstadosVista citasPorEstado, List<ActividadVista> actividad) {
    }

    /** Una ventana de tiempo: {@code desde} incluido, {@code hasta} excluido, y la anterior de la misma duración. */
    record Ventana(LocalDate desde, LocalDate hasta, LocalDate anteriorDesde, LocalDate anteriorHasta, String etiqueta, String etiquetaAnterior) {
    }

    private final DashboardJdbc datos;
    private final Clock reloj;

    public ServicioDashboard(DashboardJdbc datos, Clock reloj) {
        this.datos = datos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Tablero tablero(String periodo) {
        String clave = periodo == null || periodo.isBlank() ? "mes" : periodo.trim().toLowerCase(ES);
        LocalDate hoy = LocalDate.now(reloj);
        Ventana v = ventana(clave, hoy);

        LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Conteos conteos = datos.conteos(hoy, lunes, lunes.plusDays(7), reloj.getZone());

        int citas = datos.citasAgendadas(v.desde(), v.hasta());
        int anterior = datos.citasAgendadas(v.anteriorDesde(), v.anteriorHasta());
        Integer variacion = anterior == 0 ? null : (int) Math.round(100.0 * (citas - anterior) / anterior);

        LocalDate primerMes = hoy.withDayOfMonth(1).minusMonths(MESES_DE_HISTORIAL - 1);
        LocalDate finHistorial = hoy.withDayOfMonth(1).plusMonths(1);
        int citasSeisMeses = datos.citasAgendadas(primerMes, finHistorial);
        int proyectosDeCitas = datos.proyectosDeCitas(primerMes, finHistorial);
        int[] entregas = datos.entregasATiempo(hoy.getYear());

        CitasPorEstado estados = datos.citasPorEstado(v.desde(), v.hasta());
        EstadosVista estadosVista = new EstadosVista(estados.pendientes(), estados.confirmadas(), estados.atendidas(), estados.canceladas(),
                estados.pendientes() + estados.confirmadas() + estados.atendidas() + estados.canceladas(), promedio(datos.promedioDiasParaConfirmar(v.desde(), v.hasta())));

        Indicadores indicadores = new Indicadores(new IndicadorCitas(v.etiqueta(), citas, anterior, variacion, v.etiquetaAnterior()),
                new IndicadorConversion(proyectosDeCitas, citasSeisMeses, porcentaje(proyectosDeCitas, citasSeisMeses)),
                new IndicadorProyectos(conteos.proyectosActivos(), conteos.capacidad(),
                        conteos.capacidad() == 0 ? 0 : (int) Math.round(100.0 * conteos.proyectosActivos() / conteos.capacidad()),
                        conteos.planesPorAprobar()),
                new IndicadorEntregas(entregas[0], entregas[1], porcentaje(entregas[0], entregas[1]), hoy.getYear()));

        List<MesVista> meses = datos.citasPorMes(primerMes, hoy.withDayOfMonth(1)).stream()
                .map(m -> new MesVista(m.mes().toString().substring(0, 7), nombreMes(m.mes().getMonth(), true), m.citas(),
                        m.mes().equals(hoy.withDayOfMonth(1)))).toList();
        List<EquipoVista> carga = datos.cargaDeEquipos().stream()
                .map(e -> new EquipoVista(e.id().toString(), e.productOwner(), e.activos(), e.maximo(), Math.max(0, e.maximo() - e.activos()))).toList();
        List<FaseVista> fases = datos.proyectosPorFase().stream().map(f -> new FaseVista(f.fase(), f.proyectos())).toList();
        List<ServicioVista> servicios = datos.serviciosMasSolicitados(primerMes, finHistorial, 5).stream()
                .map(s -> new ServicioVista(s.servicio(), s.citas())).toList();
        List<ActividadVista> actividad = datos.actividadReciente(8).stream()
                .map(a -> new ActividadVista(a.fecha(), a.usuario(), a.categoria(), a.accion(), a.entidad(), a.entidadId())).toList();

        boolean sinDatos = citas == 0 && anterior == 0 && conteos.proyectosActivos() == 0 && estadosVista.total() == 0;
        return new Tablero(Instant.now(reloj), hoy, clave, v.etiqueta(), sinDatos, indicadores, conteos, alertas(hoy, conteos), meses, carga, fases,
                servicios, estadosVista, actividad);
    }

    // ------------------------------------------------------------------------------------------- Periodo

    /** Semana (de lunes a domingo), mes o trimestre calendario; otro valor es un error de validación (422). */
    static Ventana ventana(String clave, LocalDate hoy) {
        switch (clave) {
            case "semana" -> {
                LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new Ventana(lunes, lunes.plusDays(7), lunes.minusDays(7), lunes, "Citas esta semana", "la semana pasada");
            }
            case "mes" -> {
                LocalDate primero = hoy.withDayOfMonth(1);
                return new Ventana(primero, primero.plusMonths(1), primero.minusMonths(1), primero, "Citas en " + nombreMes(hoy.getMonth(), false),
                        nombreMes(primero.minusMonths(1).getMonth(), false));
            }
            case "trimestre" -> {
                LocalDate primero = hoy.withMonth(((hoy.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
                return new Ventana(primero, primero.plusMonths(3), primero.minusMonths(3), primero, "Citas este trimestre", "el trimestre pasado");
            }
            default -> throw new ReglaNegocioException("periodo", "Elige semana, mes o trimestre.");
        }
    }

    private static String nombreMes(Month mes, boolean corto) {
        String nombre = mes.getDisplayName(corto ? TextStyle.SHORT : TextStyle.FULL, ES);
        return corto ? Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1).replace(".", "") : nombre;
    }

    private static Integer porcentaje(int parte, int total) {
        return total == 0 ? null : (int) Math.round(100.0 * parte / total);
    }

    // ------------------------------------------------------------------------------------------- Alertas

    /** «Requiere atención», del más grave al menos grave (HU-23, paso 3: cada alerta lleva al detalle). */
    private List<Alerta> alertas(LocalDate hoy, Conteos conteos) {
        List<Alerta> lista = new ArrayList<>();
        for (ProyectoEnRiesgo p : datos.proyectosEnRiesgo(hoy)) {
            String donde = p.nombre() + (p.fase() == null ? "" : " · en " + p.fase()) + " · " + sinNulo(p.productOwner(), "sin Product Owner");
            switch (p.riesgo()) {
                case "ENTREGA_VENCIDA" -> lista.add(new Alerta("riesgo-" + p.codigo(), "CRITICA", p.codigo() + " está fuera de plazo",
                        donde + " · entrega prevista el " + DIA_MES.format(p.entrega()) + ".", "/admin/proyectos", "Revisar"));
                case "POCO_AVANCE" -> lista.add(new Alerta("riesgo-" + p.codigo(), "ADVERTENCIA", p.codigo() + " entrega pronto con poco avance",
                        donde + " · avance " + p.avance().stripTrailingZeros().toPlainString() + " % · entrega el " + DIA_MES.format(p.entrega()) + ".",
                        "/admin/proyectos", "Revisar"));
                case "SIN_AVANCES" -> lista.add(new Alerta("riesgo-" + p.codigo(), "ADVERTENCIA",
                        p.codigo() + " lleva " + p.diasSinAvance() + " días hábiles sin avances", donde + ".", "/admin/proyectos", "Revisar"));
                default -> lista.add(new Alerta("riesgo-" + p.codigo(), "ADVERTENCIA", p.codigo() + " tiene el plan sin respuesta del cliente",
                        donde + " · pasó el plazo de 3 días hábiles.", "/admin/proyectos", "Revisar"));
            }
        }
        for (CambioPorAutorizar c : datos.cambiosPorAutorizar()) {
            lista.add(new Alerta("cambio-" + c.codigo(), "ADVERTENCIA", c.codigo() + " tiene un cambio de plan por autorizar",
                    c.proyecto() + " · " + sinNulo(c.productOwner(), "El equipo") + " propone " + textoDelCambio(c) + valorYEntrega(c) + ".",
                    "/admin/proyectos", "Autorizar"));
        }
        for (FaseVencida f : datos.fasesVencidas(hoy)) {
            lista.add(new Alerta("fase-" + f.codigo() + "-" + f.fase(), "ADVERTENCIA", f.codigo() + ": la fase «" + f.fase() + "» pasó su plazo de aprobación",
                    f.proyecto() + " · venció el " + DIA_MES.format(f.plazo()) + "; se aprueba sola (RN-17).", "/admin/proyectos", "Revisar"));
        }
        for (GarantiaCumplida g : datos.garantiasCumplidas(hoy)) {
            lista.add(new Alerta("garantia-" + g.codigo(), "ADVERTENCIA", g.codigo() + ": la garantía ya se cumplió y sigue abierta",
                    g.proyecto() + " · terminó el " + DIA_MES.format(g.garantiaHasta()) + " y tiene " + g.correccionesAbiertas()
                            + (g.correccionesAbiertas() == 1 ? " corrección abierta." : " correcciones abiertas."), "/admin/proyectos", "Revisar"));
        }
        for (CitaEnAlerta c : datos.citasSinConfirmar(hoy, 1, reloj.getZone())) {
            lista.add(new Alerta("cita-" + c.codigo(), "ADVERTENCIA", c.codigo() + " sin confirmar hace " + c.diasHabiles()
                    + (c.diasHabiles() == 1 ? " día hábil" : " días hábiles"), c.cliente() + " · " + c.servicio() + " · "
                    + DIA_HORA.format(c.fechaHora().atZone(reloj.getZone())) + " · " + sinNulo(c.productOwner(), "sin Product Owner") + ".",
                    "/admin/citas", "Ver cita"));
        }
        for (CitaEnAlerta c : datos.citasSinEquipo(hoy, reloj.getZone())) {
            lista.add(new Alerta("sinequipo-" + c.codigo(), "INFO", c.codigo() + " no tiene equipo asignado", c.cliente() + " · " + c.servicio() + " · "
                    + DIA_HORA.format(c.fechaHora().atZone(reloj.getZone())) + " · " + (c.modalidad().equals("VIRTUAL") ? "virtual" : "presencial") + ".",
                    "/admin/citas", "Asignar"));
        }
        if (conteos.solicitudesPendientes() > 0) {
            int n = conteos.solicitudesPendientes();
            lista.add(new Alerta("solicitudes", "INFO", n + (n == 1 ? " solicitud de reunión por responder" : " solicitudes de reunión por responder"),
                    "Las piden los clientes desde sus proyectos; normalmente las responde el Product Owner.", "/admin/solicitudes", "Ver solicitudes"));
        }
        lista.sort(Comparator.comparingInt(a -> peso(a.nivel())));
        return lista;
    }

    private static int peso(String nivel) {
        return switch (nivel) {
            case "CRITICA" -> 0;
            case "ADVERTENCIA" -> 1;
            default -> 2;
        };
    }

    private static String textoDelCambio(CambioPorAutorizar c) {
        return switch (c.tipo()) {
            case "AGREGAR_FASE" -> "agregar la fase «" + c.fasePropuesta() + "»";
            case "QUITAR_FASE" -> "quitar una fase";
            case "CAMBIAR_FECHA" -> "cambiar la fecha de entrega";
            case "QUITAR_PASO" -> "quitar un paso";
            default -> "cotizar un trabajo nuevo";
        };
    }

    private static String valorYEntrega(CambioPorAutorizar c) {
        StringBuilder s = new StringBuilder();
        if (c.valorAdicional() != null && c.valorAdicional().signum() > 0) {
            s.append(" · +$").append(String.format(ES, "%,d", c.valorAdicional().setScale(0, RoundingMode.HALF_UP).longValue()));
        }
        if (c.nuevaEntrega() != null) {
            s.append(" · entrega ").append(DIA_MES.format(c.nuevaEntrega()));
        }
        return s.toString();
    }

    private static String sinNulo(String texto, String porDefecto) {
        return texto == null || texto.isBlank() ? porDefecto : texto;
    }

    private static Double promedio(Double valor) {
        return valor == null ? null : BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
