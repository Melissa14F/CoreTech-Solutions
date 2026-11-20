package co.coretech.portal.compartido.web;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.stereotype.Component;

/**
 * Convierte un error de PostgreSQL (restricción o disparador) en la respuesta HTTP de la tabla de
 * docs/04-api.md. Busca el {@link PSQLException} en la cadena de causas, porque llega envuelto por
 * Spring, Hibernate o la transacción (los disparadores diferidos fallan al confirmar).
 * <p>
 * La restricción se identifica por su nombre; el disparador, por la función que hizo el RAISE, que
 * PostgreSQL informa en el contexto del error ("PL/pgSQL function fn_…() line N at RAISE").
 */
@Component
class TraductorErroresBaseDatos {

    /** Todas las funciones del esquema empiezan por fn_; sirve con mensajes en inglés o en español. */
    private static final Pattern FUNCION = Pattern.compile("\\b(fn_\\w+)\\(");

    Optional<ErrorTraducido> traducir(Throwable error) {
        return buscarErrorPostgres(error).flatMap(TraductorErroresBaseDatos::traducir);
    }

    private static Optional<ErrorTraducido> traducir(SQLException error) {
        ServerErrorMessage detalle = error instanceof PSQLException psql ? psql.getServerErrorMessage() : null;
        String sqlState = detalle != null && detalle.getSQLState() != null ? detalle.getSQLState() : error.getSQLState();
        if (detalle != null) {
            Optional<ErrorTraducido> especifico = "P0001".equals(sqlState)
                    ? CatalogoErroresBaseDatos.porFuncion(funcion(detalle.getWhere()), detalle.getMessage())
                    : CatalogoErroresBaseDatos.porRestriccion(detalle.getConstraint());
            if (especifico.isPresent()) {
                return especifico;
            }
            if ("23502".equals(sqlState) && detalle.getColumn() != null) {
                return CatalogoErroresBaseDatos.porEstadoSql(sqlState).map(e -> e.conCampo(enCamelCase(detalle.getColumn())));
            }
        }
        return CatalogoErroresBaseDatos.porEstadoSql(sqlState);
    }

    private static Optional<SQLException> buscarErrorPostgres(Throwable error) {
        SQLException respaldo = null;
        for (Throwable actual = error; actual != null; actual = actual.getCause() == actual ? null : actual.getCause()) {
            if (actual instanceof SQLException sql) {
                for (SQLException siguiente = sql; siguiente != null; siguiente = siguiente.getNextException()) {
                    if (siguiente instanceof PSQLException) {
                        return Optional.of(siguiente);
                    }
                }
                respaldo = respaldo == null ? sql : respaldo;
            }
        }
        return Optional.ofNullable(respaldo);
    }

    static String funcion(String contexto) {
        if (contexto == null) {
            return null;
        }
        Matcher m = FUNCION.matcher(contexto);
        return m.find() ? m.group(1) : null;
    }

    static String enCamelCase(String columna) {
        StringBuilder sb = new StringBuilder();
        boolean mayuscula = false;
        for (char c : columna.toCharArray()) {
            if (c == '_') {
                mayuscula = true;
            } else {
                sb.append(mayuscula ? Character.toUpperCase(c) : c);
                mayuscula = false;
            }
        }
        return sb.toString();
    }
}
