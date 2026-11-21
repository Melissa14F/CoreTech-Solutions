-- V10 · P16 (HU-17): el filtro "en riesgo" de la lista de proyectos se calcula en una sola consulta y
-- necesita contar días hábiles dentro de SQL. Misma regla que CalendarioHabil.diasHabilesEntre: cuenta los
-- días de (desde, hasta], de lunes a viernes y sin los festivos de dias_no_habiles. Si hasta <= desde, devuelve 0.
CREATE FUNCTION fn_dias_habiles_entre(p_desde date, p_hasta date)
RETURNS integer
LANGUAGE sql
STABLE
AS $$
  SELECT count(*)::integer
    FROM generate_series(p_desde + 1, p_hasta, interval '1 day') AS d
   WHERE extract(isodow FROM d) < 6
     AND NOT EXISTS (SELECT 1 FROM dias_no_habiles h WHERE h.fecha = d::date)
$$;

COMMENT ON FUNCTION fn_dias_habiles_entre(date, date) IS 'Días hábiles en (desde, hasta]: lunes a viernes sin festivos (igual que CalendarioHabil)';
