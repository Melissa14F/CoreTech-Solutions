-- V16 · Cambio de alcance cotizado como cambio de plan (HU-11, HU-30, D-32, P21).
--
-- Cuando el cliente pide «algo nuevo» (solicitud ALCANCE, POR_COTIZAR), el desarrollador (Product Owner) lo cotiza con un
-- cambio de plan AGREGAR_ALCANCE: título y explicación del paso, costo (valor adicional) y nueva fecha de entrega. Lo autoriza el
-- administrador y lo acepta el cliente; al aceptarlo se crea el paso ALCANCE en la fase en curso y el costo suma al valor total.
--
-- Recorrido de la solicitud: POR_COTIZAR → COTIZADO (autorizado) → ACEPTADO / RECHAZADO (cliente) → RESUELTO (paso hecho).
-- Si el administrador rechaza la cotización, la solicitud sigue POR_COTIZAR para que se cotice de nuevo.

ALTER TABLE cambios_plan
  ADD COLUMN solicitud_cambio_id uuid REFERENCES solicitudes_cambio (id) DEFERRABLE INITIALLY IMMEDIATE,
  ADD COLUMN titulo_paso varchar(80),
  ADD COLUMN explicacion_paso varchar(400);

ALTER TABLE cambios_plan
  ADD CONSTRAINT ck_cp_alcance CHECK (
    (tipo = 'AGREGAR_ALCANCE' AND solicitud_cambio_id IS NOT NULL
       AND coalesce(char_length(trim(titulo_paso)) BETWEEN 3 AND 80, false)
       AND coalesce(char_length(trim(explicacion_paso)) BETWEEN 20 AND 400, false)
       AND proyecto_fase_id IS NULL AND nombre_fase IS NULL AND pasos_propuestos IS NULL AND paso_id IS NULL)
    OR (tipo <> 'AGREGAR_ALCANCE' AND solicitud_cambio_id IS NULL AND titulo_paso IS NULL AND explicacion_paso IS NULL));

-- Una solicitud tiene a lo sumo una cotización viva; si el administrador rechaza una, se puede cotizar otra
CREATE UNIQUE INDEX ux_cp_solicitud ON cambios_plan (solicitud_cambio_id)
  WHERE solicitud_cambio_id IS NOT NULL AND estado IN ('PROPUESTO', 'AUTORIZADO', 'ACEPTADO');

CREATE OR REPLACE FUNCTION fn_validar_cambio_plan() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM proyectos WHERE id = NEW.proyecto_id AND estado_plan = 'APROBADO' AND estado = 'ACTIVO') THEN
    RAISE EXCEPTION 'Solo se cambia el plan de un proyecto activo con el plan aprobado (RN-30)';
  END IF;
  IF NEW.tipo = 'CAMBIAR_FECHA' AND EXISTS (SELECT 1 FROM proyectos WHERE id = NEW.proyecto_id
                                             AND fecha_estimada_entrega = NEW.nueva_fecha_entrega) THEN
    RAISE EXCEPTION 'La nueva fecha de entrega debe ser distinta de la actual (RN-30)';
  END IF;
  IF NEW.tipo = 'QUITAR_FASE' AND NOT EXISTS (SELECT 1 FROM proyecto_fases f WHERE f.id = NEW.proyecto_fase_id
                                                AND f.proyecto_id = NEW.proyecto_id AND f.estado = 'PENDIENTE') THEN
    RAISE EXCEPTION 'Solo se puede quitar una fase pendiente del mismo proyecto (RN-30)';
  END IF;
  -- QUITAR_PASO: un paso pendiente del plan (no una corrección), de la fase actual o de una pendiente,
  -- y la fase debe conservar al menos un paso (RN-28)
  IF NEW.tipo = 'QUITAR_PASO' THEN
    IF NOT EXISTS (SELECT 1 FROM pasos p JOIN proyecto_fases f ON f.id = p.proyecto_fase_id
                    WHERE p.id = NEW.paso_id AND f.proyecto_id = NEW.proyecto_id
                      AND p.tipo = 'PASO' AND p.estado = 'PENDIENTE' AND f.estado IN ('PENDIENTE','EN_CURSO')) THEN
      RAISE EXCEPTION 'Solo se quita un paso pendiente de la fase actual o de una fase pendiente del mismo proyecto (RN-30)';
    END IF;
    IF (SELECT count(*) FROM pasos p WHERE p.proyecto_fase_id = (SELECT proyecto_fase_id FROM pasos WHERE id = NEW.paso_id)
          AND p.estado <> 'QUITADO' AND p.id <> NEW.paso_id) = 0 THEN
      RAISE EXCEPTION 'La fase debe conservar al menos un paso (RN-28)';
    END IF;
  END IF;
  -- AGREGAR_ALCANCE: la solicitud es de tipo ALCANCE, del mismo proyecto y está por cotizar (D-32)
  IF NEW.tipo = 'AGREGAR_ALCANCE' AND NOT EXISTS (
       SELECT 1 FROM solicitudes_cambio s JOIN proyecto_fases f ON f.id = s.proyecto_fase_id
        WHERE s.id = NEW.solicitud_cambio_id AND f.proyecto_id = NEW.proyecto_id
          AND s.tipo = 'ALCANCE' AND s.estado = 'POR_COTIZAR') THEN
    RAISE EXCEPTION 'Solo se cotiza un cambio de alcance por cotizar del mismo proyecto (RN-30)';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

COMMENT ON COLUMN cambios_plan.solicitud_cambio_id IS 'Solicitud de alcance que se cotiza (AGREGAR_ALCANCE, D-32)';
COMMENT ON COLUMN cambios_plan.titulo_paso IS 'Título del paso ALCANCE que se crea al aceptarlo, 3–80 caracteres';
COMMENT ON COLUMN cambios_plan.explicacion_paso IS 'Explicación del paso ALCANCE, 20–400 caracteres';
