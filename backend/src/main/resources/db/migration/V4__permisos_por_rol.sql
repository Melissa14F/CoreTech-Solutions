-- V4 · Permisos y su matriz por rol (HU-21, RN-19, RNF-01).
-- datos_iniciales.sql (V1) traía los roles pero no los permisos. Esta es la matriz de
-- prototipo/admin/roles-permisos.html. bloqueado = lo fija una regla del negocio: el administrador
-- no puede quitarlo (RN-19). Los endpoints se autorizan por el código del permiso, nunca por el rol:
-- @PreAuthorize("@permisos.tiene('citas.agendar')").
--
-- No están "Editar o borrar avances publicados" ni "Editar o borrar registros de auditoría": nadie
-- puede hacerlo (RNF-02) y la base lo impide con disparadores, así que no son permisos que se den.
-- Las pantallas que lleguen después agregan sus permisos con una migración nueva.

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('citas.agendar',               'citas',       'Agendar citas: elegir servicio, fecha, hora y modalidad (RN-01 a RN-03)'),
  ('citas.cancelar_propia',       'citas',       'Cancelar su cita pendiente (RN-11)'),
  ('citas.ver_asignadas',         'citas',       'Ver las citas asignadas a su equipo (RN-12)'),
  ('citas.atender',               'citas',       'Confirmar citas y marcarlas como atendidas con su cuestionario (RN-12, RN-24)'),
  ('citas.ver_todas',             'citas',       'Ver todas las citas (RN-10)'),
  ('citas.asignar',               'citas',       'Asignar o reasignar el equipo de una cita y cancelarla (RN-11)'),
  ('proyectos.ver_propios',       'proyectos',   'Ver sus proyectos y avances (RN-10)'),
  ('proyectos.ver_todos',         'proyectos',   'Ver los proyectos de todos los clientes (RN-10)'),
  ('proyectos.registrar_avance',  'proyectos',   'Registrar avances: pasos, correcciones y entrega de fases (RN-07)'),
  ('proyectos.crear',             'proyectos',   'Crear proyectos y asignar el equipo responsable (RN-04, RN-05)'),
  ('proyectos.gestionar',         'proyectos',   'Editar, pausar, reanudar o cancelar proyectos'),
  ('reuniones.solicitar',         'reuniones',   'Solicitar una reunión de seguimiento (RN-14, RN-15)'),
  ('reuniones.programar',         'reuniones',   'Programar o rechazar solicitudes de reunión'),
  ('sitio.servicios',             'sitio',       'Editar los servicios del catálogo'),
  ('sitio.contenido',             'sitio',       'Editar el contenido de la página principal'),
  ('administracion.dashboard',    'gestion',     'Ver el dashboard del negocio'),
  ('seguridad.cuentas',           'seguridad',   'Crear y editar cuentas, activarlas, desactivarlas y restablecer contraseñas'),
  ('seguridad.permisos',          'seguridad',   'Cambiar los permisos de los roles'),
  ('seguridad.auditoria',         'seguridad',   'Ver y exportar la auditoría');

-- Matriz: (rol, permiso, bloqueado)
INSERT INTO rol_permisos (rol_id, permiso_id, bloqueado)
SELECT r.id, p.id, m.bloqueado
  FROM (VALUES
    -- Cliente
    ('CLIENTE',       'citas.agendar',              false),
    ('CLIENTE',       'citas.cancelar_propia',      false),
    ('CLIENTE',       'proyectos.ver_propios',      true),
    ('CLIENTE',       'reuniones.solicitar',        false),
    -- Desarrollador (Product Owner)
    ('PRODUCT_OWNER', 'citas.ver_asignadas',        true),
    ('PRODUCT_OWNER', 'citas.atender',              true),
    ('PRODUCT_OWNER', 'proyectos.ver_propios',      true),
    ('PRODUCT_OWNER', 'proyectos.registrar_avance', true),
    ('PRODUCT_OWNER', 'reuniones.programar',        false),
    -- Administrador: todos los suyos son fijos (RN-19). Registrar avances es solo del Product Owner (RN-07).
    ('ADMINISTRADOR', 'citas.agendar',              true),
    ('ADMINISTRADOR', 'citas.cancelar_propia',      true),
    ('ADMINISTRADOR', 'citas.ver_asignadas',        true),
    ('ADMINISTRADOR', 'citas.atender',              true),
    ('ADMINISTRADOR', 'citas.ver_todas',            true),
    ('ADMINISTRADOR', 'citas.asignar',              true),
    ('ADMINISTRADOR', 'proyectos.ver_propios',      true),
    ('ADMINISTRADOR', 'proyectos.ver_todos',        true),
    ('ADMINISTRADOR', 'proyectos.crear',            true),
    ('ADMINISTRADOR', 'proyectos.gestionar',        true),
    ('ADMINISTRADOR', 'reuniones.programar',        true),
    ('ADMINISTRADOR', 'sitio.servicios',            true),
    ('ADMINISTRADOR', 'sitio.contenido',            true),
    ('ADMINISTRADOR', 'administracion.dashboard',   true),
    ('ADMINISTRADOR', 'seguridad.cuentas',          true),
    ('ADMINISTRADOR', 'seguridad.permisos',         true),
    ('ADMINISTRADOR', 'seguridad.auditoria',        true)
  ) AS m(rol, permiso, bloqueado)
  JOIN roles r ON r.codigo = m.rol
  JOIN permisos p ON p.codigo = m.permiso;
