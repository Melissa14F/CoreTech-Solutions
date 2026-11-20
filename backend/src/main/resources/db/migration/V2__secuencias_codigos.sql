-- V2 · Secuencias de los códigos visibles de citas (C-0001) y proyectos (P-0001).
-- El servicio que crea la cita o el proyecto llama a fn_siguiente_codigo_cita() o
-- fn_siguiente_codigo_proyecto() y guarda el resultado en la columna "codigo" (varchar(10)).

CREATE SEQUENCE seq_codigo_cita START WITH 1 INCREMENT BY 1 MINVALUE 1;
CREATE SEQUENCE seq_codigo_proyecto START WITH 1 INCREMENT BY 1 MINVALUE 1;

-- Arma el código visible: prefijo + guion + número con al menos 4 dígitos.
-- Desde 10000 usa todos los dígitos (C-12345): nunca recorta el número.
CREATE FUNCTION fn_formatear_codigo(p_prefijo text, p_numero bigint)
RETURNS varchar(10)
LANGUAGE sql
IMMUTABLE STRICT
AS $$
  SELECT (p_prefijo || '-' || lpad(p_numero::text, greatest(4, length(p_numero::text)), '0'))::varchar(10)
$$;

CREATE FUNCTION fn_siguiente_codigo_cita()
RETURNS varchar(10)
LANGUAGE sql
VOLATILE
AS $$
  SELECT fn_formatear_codigo('C', nextval('seq_codigo_cita'))
$$;

CREATE FUNCTION fn_siguiente_codigo_proyecto()
RETURNS varchar(10)
LANGUAGE sql
VOLATILE
AS $$
  SELECT fn_formatear_codigo('P', nextval('seq_codigo_proyecto'))
$$;

COMMENT ON SEQUENCE seq_codigo_cita IS 'Número del código visible de la cita (C-0001)';
COMMENT ON SEQUENCE seq_codigo_proyecto IS 'Número del código visible del proyecto (P-0001)';
COMMENT ON FUNCTION fn_siguiente_codigo_cita() IS 'Siguiente código de cita: C-0001, C-0002…';
COMMENT ON FUNCTION fn_siguiente_codigo_proyecto() IS 'Siguiente código de proyecto: P-0001, P-0002…';
