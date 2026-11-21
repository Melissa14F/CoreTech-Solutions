-- V14 · Resumen de la reunión de seguimiento y código visible de las solicitudes (HU-16, D-42, P20).
--
-- Después de la reunión, el desarrollador (Product Owner) llena el resumen y la solicitud queda ATENDIDA. El resumen no se
-- edita ni se borra (RNF-02): lo garantiza fn_bloquear_cambios, la misma función de la auditoría.

CREATE SEQUENCE seq_codigo_solicitud START WITH 1 INCREMENT BY 1 MINVALUE 1;

CREATE FUNCTION fn_siguiente_codigo_solicitud()
RETURNS varchar(10)
LANGUAGE sql
AS $$
  SELECT fn_formatear_codigo('S', nextval('seq_codigo_solicitud'))
$$;

COMMENT ON SEQUENCE seq_codigo_solicitud IS 'Número del código visible de la solicitud de reunión (S-0001)';
COMMENT ON FUNCTION fn_siguiente_codigo_solicitud() IS 'Siguiente código de solicitud de reunión: S-0001, S-0002…';

CREATE TABLE resumenes_reunion (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  solicitud_id    uuid NOT NULL UNIQUE REFERENCES solicitudes_reunion (id) DEFERRABLE INITIALLY IMMEDIATE,
  resumen         varchar(1000) NOT NULL,
  que_pidio       varchar(600)  NOT NULL,
  siguiente_paso  varchar(20)   NOT NULL,
  registrado_por  uuid NOT NULL REFERENCES usuarios (id) DEFERRABLE INITIALLY IMMEDIATE,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_resumen_texto     CHECK (char_length(trim(resumen)) BETWEEN 30 AND 1000),
  CONSTRAINT ck_resumen_pidio     CHECK (char_length(trim(que_pidio)) BETWEEN 20 AND 600),
  CONSTRAINT ck_resumen_siguiente CHECK (siguiente_paso IN ('NINGUNO', 'DENTRO_DEL_PLAN', 'CAMBIO_DE_PLAN'))
);

CREATE TRIGGER tg_resumenes_inmutables BEFORE UPDATE OR DELETE ON resumenes_reunion
  FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios();

COMMENT ON TABLE resumenes_reunion IS 'Lo que se habló en una reunión de seguimiento (D-42). Inmutable (RNF-02): se escribe una vez, al dejar la solicitud ATENDIDA.';
COMMENT ON COLUMN resumenes_reunion.resumen IS '30–1000 caracteres';
COMMENT ON COLUMN resumenes_reunion.que_pidio IS 'Lo que pidió el cliente en la reunión, 20–600 caracteres';
COMMENT ON COLUMN resumenes_reunion.siguiente_paso IS 'NINGUNO, DENTRO_DEL_PLAN o CAMBIO_DE_PLAN';
