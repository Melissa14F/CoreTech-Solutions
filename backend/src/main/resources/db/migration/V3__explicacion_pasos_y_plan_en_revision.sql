-- V3 · Decisiones D-23 y D-26 del 2026-11-19 (docs/PROGRESO.md).

-- D-23 · Explicación de un paso (RNF-14, HU-12, HU-27).
-- Paso del plan (tipo PASO): opcional y de máximo 160 caracteres, igual que en la plantilla, que la
-- deja vacía; antes era NOT NULL y copiar un paso de plantilla sin explicación fallaba.
-- Corrección, ajuste o cambio de alcance: obligatoria, de 20 a 400 caracteres.
-- coalesce: sin él, una explicación NULL dejaría pasar el CHECK.
ALTER TABLE pasos ALTER COLUMN explicacion DROP NOT NULL;
ALTER TABLE pasos
  ADD CONSTRAINT ck_pasos_explicacion CHECK (
    (tipo = 'PASO' AND (explicacion IS NULL OR char_length(trim(explicacion)) <= 160))
    OR (tipo <> 'PASO' AND coalesce(char_length(trim(explicacion)) BETWEEN 20 AND 400, false)));

COMMENT ON COLUMN pasos.explicacion IS 'Para alguien que no programa. PASO: opcional, máx. 160. CORRECCION, AJUSTE o ALCANCE: 20 a 400 (D-23)';

-- D-26 · Un plan ENVIADO, RECHAZADO o VENCIDO no se edita (RN-27, HU-28).
-- Sus fases y pasos solo cambian en BORRADOR o AJUSTES_PEDIDOS (y, con el plan aprobado, según RN-30,
-- que ya protegen tg_proteger_fases y tg_proteger_pasos). Al aprobar el plan, el servicio cambia primero
-- proyectos.estado_plan y después inicia la fase 1.
CREATE FUNCTION fn_plan_en_revision() RETURNS trigger AS $$
DECLARE plan estado_plan;
BEGIN
  IF TG_TABLE_NAME = 'proyecto_fases' THEN
    SELECT estado_plan INTO plan FROM proyectos
     WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.proyecto_id ELSE NEW.proyecto_id END;
  ELSE
    -- Si la fase ya no existe, se está quitando la fase completa: lo validó este mismo disparador en la fase
    SELECT p.estado_plan INTO plan
      FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id
     WHERE f.id = CASE WHEN TG_OP = 'DELETE' THEN OLD.proyecto_fase_id ELSE NEW.proyecto_fase_id END;
  END IF;
  IF plan IN ('ENVIADO','RECHAZADO','VENCIDO') THEN
    RAISE EXCEPTION 'Con el plan en estado % no se cambian sus fases ni sus pasos: solo en borrador o con ajustes pedidos (RN-27)', plan;
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER tg_plan_en_revision_fases BEFORE INSERT OR UPDATE OR DELETE ON proyecto_fases
  FOR EACH ROW EXECUTE FUNCTION fn_plan_en_revision();
CREATE TRIGGER tg_plan_en_revision_pasos BEFORE INSERT OR UPDATE OR DELETE ON pasos
  FOR EACH ROW EXECUTE FUNCTION fn_plan_en_revision();

COMMENT ON FUNCTION fn_plan_en_revision() IS 'D-26: con el plan ENVIADO, RECHAZADO o VENCIDO no se cambian fases ni pasos (RN-27)';
