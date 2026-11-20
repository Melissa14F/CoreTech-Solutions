-- V6 · Matriz de permisos igual a prototipo/admin/roles-permisos.html (HU-21, RN-19).
--
-- 1. Cada permiso guarda cómo se muestra: nombre, ayuda, regla que lo fija y orden en la matriz.
-- 2. rol_permisos_vetados: combinaciones rol–permiso que una regla del negocio prohíbe (las casillas
--    con candado "no permitido"). rol_permisos solo guarda lo permitido, así que sin esta tabla no se
--    distingue "apagado y editable" de "prohibido por una regla".
-- 3. "Editar o borrar avances publicados" y "Editar o borrar registros de auditoría" se agregan como
--    permisos vetados para los tres roles: la pantalla los muestra con candado y nadie los puede dar
--    (RNF-02; además, los disparadores de pasos y de auditoría lo impiden).
-- 4. fn_proteger_rol_permisos (última barrera de RN-19): no se quita un permiso fijo ni se da uno vetado.

ALTER TABLE permisos
  ADD COLUMN nombre varchar(60),
  ADD COLUMN ayuda  varchar(80),
  ADD COLUMN regla  varchar(30),
  ADD COLUMN orden  smallint;

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('proyectos.editar_avances',   'proyectos', 'Editar o borrar avances publicados: nadie puede (RNF-02)'),
  ('seguridad.editar_auditoria', 'seguridad', 'Editar o borrar registros de auditoría: nadie puede (RNF-02)');

UPDATE permisos p
   SET nombre = v.nombre, ayuda = v.ayuda, regla = v.regla, orden = v.orden
  FROM (VALUES
    ('citas.agendar',              1, 'Agendar citas',                              'Elegir servicio, fecha, hora y modalidad.',                         'RN-01 a RN-03'),
    ('citas.cancelar_propia',      2, 'Cancelar su cita pendiente',                 'Solo mientras la cita está pendiente.',                             'RN-12'),
    ('citas.ver_asignadas',        3, 'Ver citas asignadas',                        'Las citas que la administración le asignó.',                        'RN-12'),
    ('citas.atender',              4, 'Confirmar y marcar citas como atendidas',    'Con el enlace de la videollamada si es virtual.',                   'RN-12'),
    ('citas.ver_todas',            5, 'Ver todas las citas',                        'Las citas de todos los clientes.',                                  'RN-10'),
    ('citas.asignar',              6, 'Asignar desarrollador a una cita',           NULL,                                                                'RN-12'),
    ('proyectos.ver_propios',      7, 'Ver sus proyectos y avances',                'El cliente ve los suyos; el desarrollador, los que tiene a cargo.', 'RN-10 y RN-11'),
    ('proyectos.ver_todos',        8, 'Ver proyectos de otros clientes',            NULL,                                                                'RN-10'),
    ('proyectos.registrar_avance', 9, 'Registrar avances',                          'Solo en los proyectos de los que es responsable.',                  'RN-07'),
    ('proyectos.editar_avances',  10, 'Editar o borrar avances publicados',         'Los avances quedan como registro y no se modifican.',               'RNF-02'),
    ('proyectos.crear',           11, 'Crear proyectos y asignar responsable',      NULL,                                                                'RN-04 y RN-05'),
    ('proyectos.gestionar',       12, 'Editar, pausar o cancelar proyectos',        'Nombre, alcance y fecha estimada de entrega.',                      NULL),
    ('reuniones.solicitar',       13, 'Solicitar reunión',                          'Desde un proyecto activo propio, una a la vez.',                    'RN-14 y RN-15'),
    ('reuniones.programar',       14, 'Programar o rechazar solicitudes',           'De los proyectos que tiene a cargo.',                               NULL),
    ('sitio.servicios',           15, 'Editar servicios del catálogo',              'Crear, editar, ocultar y ordenar servicios.',                       NULL),
    ('sitio.contenido',           16, 'Editar el contenido de la página principal', 'Textos, misión, visión, contacto y pie de página.',                 NULL),
    ('administracion.dashboard',  17, 'Ver el dashboard',                           'Indicadores del negocio y del equipo.',                             NULL),
    ('seguridad.cuentas',         18, 'Crear y editar cuentas',                     'Crear desarrolladores, activar y desactivar cuentas.',              NULL),
    ('seguridad.permisos',        19, 'Cambiar los permisos de los roles',          NULL,                                                                'Solo administradores'),
    ('seguridad.auditoria',       20, 'Ver la auditoría',                           'Quién hizo qué y cuándo.',                                          NULL),
    ('seguridad.editar_auditoria',21, 'Editar o borrar registros de auditoría',     'La auditoría no se puede alterar.',                                 'RNF-02')
  ) AS v(codigo, orden, nombre, ayuda, regla)
 WHERE p.codigo = v.codigo;

ALTER TABLE permisos
  ALTER COLUMN nombre SET NOT NULL,
  ALTER COLUMN orden SET NOT NULL,
  ADD CONSTRAINT ux_permisos_orden UNIQUE (orden);

COMMENT ON COLUMN permisos.nombre IS 'Como se muestra en Roles y permisos (HU-21)';
COMMENT ON COLUMN permisos.ayuda IS 'Texto de ayuda bajo el nombre';
COMMENT ON COLUMN permisos.regla IS 'Regla que lo fija (RN-xx), si la hay';
COMMENT ON COLUMN permisos.orden IS 'Orden en la matriz';

CREATE TABLE rol_permisos_vetados (
  rol_id     smallint    NOT NULL REFERENCES roles (id),
  permiso_id smallint    NOT NULL REFERENCES permisos (id),
  motivo     varchar(60) NOT NULL,
  PRIMARY KEY (rol_id, permiso_id)
);

COMMENT ON TABLE rol_permisos_vetados IS 'Combinaciones rol–permiso que una regla del negocio prohíbe: casilla con candado "no permitido" (HU-21, RN-19)';
COMMENT ON COLUMN rol_permisos_vetados.motivo IS 'Lo que muestra el candado: "Fijo por RN-12", "No aplica a este rol"…';

INSERT INTO rol_permisos_vetados (rol_id, permiso_id, motivo)
SELECT r.id, p.id, m.motivo
  FROM (VALUES
    ('CLIENTE',       'citas.atender',               'Fijo por RN-12'),
    ('CLIENTE',       'citas.ver_todas',             'Fijo por RN-10'),
    ('CLIENTE',       'citas.asignar',               'Fijo por RN-12'),
    ('CLIENTE',       'proyectos.ver_todos',         'Fijo por RN-10'),
    ('CLIENTE',       'proyectos.registrar_avance',  'Fijo por RN-07'),
    ('CLIENTE',       'proyectos.editar_avances',    'Fijo por RNF-02'),
    ('CLIENTE',       'proyectos.crear',             'Fijo por RN-04 y RN-05'),
    ('CLIENTE',       'proyectos.gestionar',         'No aplica a este rol'),
    ('CLIENTE',       'reuniones.programar',         'No aplica a este rol'),
    ('CLIENTE',       'sitio.servicios',             'No aplica a este rol'),
    ('CLIENTE',       'sitio.contenido',             'No aplica a este rol'),
    ('CLIENTE',       'administracion.dashboard',    'No aplica a este rol'),
    ('CLIENTE',       'seguridad.cuentas',           'No aplica a este rol'),
    ('CLIENTE',       'seguridad.permisos',          'Fijo por Solo administradores'),
    ('CLIENTE',       'seguridad.auditoria',         'No aplica a este rol'),
    ('CLIENTE',       'seguridad.editar_auditoria',  'Fijo por RNF-02'),
    ('PRODUCT_OWNER', 'proyectos.editar_avances',    'Fijo por RNF-02'),
    ('PRODUCT_OWNER', 'reuniones.solicitar',         'Fijo por RN-14 y RN-15'),
    ('PRODUCT_OWNER', 'seguridad.permisos',          'Fijo por Solo administradores'),
    ('PRODUCT_OWNER', 'seguridad.editar_auditoria',  'Fijo por RNF-02'),
    ('ADMINISTRADOR', 'proyectos.registrar_avance',  'Fijo por RN-07'),
    ('ADMINISTRADOR', 'proyectos.editar_avances',    'Fijo por RNF-02'),
    ('ADMINISTRADOR', 'reuniones.solicitar',         'Fijo por RN-14 y RN-15'),
    ('ADMINISTRADOR', 'seguridad.editar_auditoria',  'Fijo por RNF-02')
  ) AS m(rol, permiso, motivo)
  JOIN roles r ON r.codigo = m.rol
  JOIN permisos p ON p.codigo = m.permiso;

-- RN-19 en la base: un permiso fijo (bloqueado) no se quita ni deja de ser fijo, y uno vetado no se da.
CREATE FUNCTION fn_proteger_rol_permisos() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP IN ('DELETE', 'UPDATE') AND OLD.bloqueado
     AND (TG_OP = 'DELETE' OR NOT NEW.bloqueado OR NEW.rol_id <> OLD.rol_id OR NEW.permiso_id <> OLD.permiso_id) THEN
    RAISE EXCEPTION 'El permiso % del rol % es fijo (RN-19)', OLD.permiso_id, OLD.rol_id;
  END IF;
  IF TG_OP IN ('INSERT', 'UPDATE') AND EXISTS (
       SELECT 1 FROM rol_permisos_vetados v WHERE v.rol_id = NEW.rol_id AND v.permiso_id = NEW.permiso_id) THEN
    RAISE EXCEPTION 'El permiso % está vetado para el rol % (RN-19)', NEW.permiso_id, NEW.rol_id;
  END IF;
  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER tg_proteger_rol_permisos BEFORE INSERT OR UPDATE OR DELETE ON rol_permisos
  FOR EACH ROW EXECUTE FUNCTION fn_proteger_rol_permisos();
