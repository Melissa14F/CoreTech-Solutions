-- V11 · Permiso para preparar y enviar el plan de trabajo con su cotización (HU-28, RN-27 a RN-29, P17).
--
-- Hasta ahora ningún permiso cubría editar y enviar el plan: el Product Owner solo registraba avances (RN-07) y
-- el administrador gestionaba proyectos. El permiso va en la matriz de Roles y permisos justo después de
-- "Editar, pausar o cancelar proyectos". El cliente no lo puede tener (responde el plan con proyectos.ver_propios).

-- ux_permisos_orden no es diferible: se corre el orden en dos pasos para dejar libre el lugar 13.
UPDATE permisos SET orden = orden + 100 WHERE orden >= 13;
UPDATE permisos SET orden = orden - 99  WHERE orden >= 113;

INSERT INTO permisos (codigo, modulo, descripcion, nombre, ayuda, regla, orden) VALUES
  ('proyectos.planificar', 'proyectos', 'Preparar y enviar el plan de trabajo y la cotización (RN-27 a RN-29)',
   'Preparar y enviar el plan y la cotización', 'Fases, pasos, valor y entrega de los proyectos de su equipo.', 'RN-27 a RN-29', 13);

INSERT INTO rol_permisos (rol_id, permiso_id, bloqueado)
SELECT r.id, p.id, true
  FROM roles r JOIN permisos p ON p.codigo = 'proyectos.planificar'
 WHERE r.codigo IN ('PRODUCT_OWNER', 'ADMINISTRADOR');

INSERT INTO rol_permisos_vetados (rol_id, permiso_id, motivo)
SELECT r.id, p.id, 'No aplica a este rol'
  FROM roles r JOIN permisos p ON p.codigo = 'proyectos.planificar'
 WHERE r.codigo = 'CLIENTE';
