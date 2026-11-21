-- V12 · Marcas de los avisos automáticos del plan (HU-29, RN-29, P17).
--
-- La tarea diaria avisaba solo el día exacto (comparaba el número de días): si el servidor estaba apagado ese día, el aviso
-- se perdía. Ahora cada aviso deja su marca y sale el primer día en que la tarea corre y ya toca, una sola vez.
-- Las marcas se borran al reenviar el plan y al reanudar el proyecto, para que el ciclo siguiente avise de nuevo.

ALTER TABLE proyectos
  ADD COLUMN plan_recordado_at            timestamptz,
  ADD COLUMN plan_cancelacion_avisada_at  timestamptz;

COMMENT ON COLUMN proyectos.plan_recordado_at IS 'Cuándo la tarea diaria recordó al cliente su propuesta pendiente (una vez por envío del plan)';
COMMENT ON COLUMN proyectos.plan_cancelacion_avisada_at IS 'Cuándo se avisó a la administración que un plan vencido o rechazado cumplió 30 días sin respuesta (RN-29)';
