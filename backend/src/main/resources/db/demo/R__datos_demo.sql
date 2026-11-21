-- =====================================================================
-- Datos de demostración (solo perfil dev; nunca en test ni en producción)
-- Coherentes con el prototipo: prototipo/admin/usuarios.html, desarrolladores.html,
-- equipos.html, cliente/mis-proyectos.html y cliente/proyecto-tienda-en-linea.html.
--
-- Cuentas (todas con la contraseña Demo2026!, BCrypt costo 10):
--   Laura Gómez       laura.gomez@correo.com                cliente
--   Andrés Pérez      andres.perez@coretechsolutions.co     desarrollador (Product Owner)
--   Patricia León     patricia.leon@coretechsolutions.co    administradora
--   (y los demás usuarios de admin/usuarios.html)
--
-- Se puede ejecutar muchas veces: los registros tienen id fijo (ON CONFLICT DO NOTHING) y los
-- proyectos se crean solo si su código no existe. Flyway vuelve a ejecutarlo cuando el archivo
-- cambia, pero NO actualiza lo que ya existe: para recargar todo, borra la base
-- (cd prototipo && docker compose down -v && docker compose up -d).
-- No lleva BEGIN/COMMIT: Flyway ya abre una transacción para cada migración.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Usuarios
-- ---------------------------------------------------------------------
INSERT INTO usuarios (id, rol_id, nombre, email, celular, empresa, password_hash, acepta_datos_at, ultimo_acceso_at)
SELECT u.id::uuid, r.id, u.nombre, u.email, u.celular, u.empresa,
       '$2a$10$3J3P93uq11bdQGw.F5N.ZeyzaBwf9bYwlFtHakxE4vBtYmB0bOWYS',
       CASE WHEN u.rol = 'CLIENTE' THEN timestamptz '2026-09-15 09:30-05' END,
       timestamptz '2026-11-11 08:00-05'
  FROM (VALUES
    ('a0000000-0000-4000-8000-000000000001', 'ADMINISTRADOR', 'Patricia León',       'patricia.leon@coretechsolutions.co',  '3157781200', NULL),
    ('a0000000-0000-4000-8000-000000000002', 'PRODUCT_OWNER', 'Andrés Pérez',        'andres.perez@coretechsolutions.co',   '3204459021', NULL),
    ('a0000000-0000-4000-8000-000000000003', 'PRODUCT_OWNER', 'María Ruiz',          'maria.ruiz@coretechsolutions.co',     '3118802344', NULL),
    ('a0000000-0000-4000-8000-000000000004', 'PRODUCT_OWNER', 'Sebastián Mora',      'sebastian.mora@coretechsolutions.co', '3009917366', NULL),
    ('a0000000-0000-4000-8000-000000000005', 'PRODUCT_OWNER', 'Camila Jaimes',       'camila.jaimes@coretechsolutions.co',  '3175520918', NULL),
    ('a0000000-0000-4000-8000-000000000010', 'CLIENTE',       'Laura Gómez',         'laura.gomez@correo.com',              '3142208815', 'Accesorios Laura'),
    ('a0000000-0000-4000-8000-000000000011', 'CLIENTE',       'Felipe Ortega',       'felipe.ortega@correo.com',            '3187734410', NULL),
    ('a0000000-0000-4000-8000-000000000012', 'CLIENTE',       'Ricardo Peñaranda',   'ricardo.penaranda@correo.com',        '3125569031', NULL),
    ('a0000000-0000-4000-8000-000000000013', 'CLIENTE',       'Carlos Díaz',         'carlos.diaz@correo.com',              '3008841277', NULL),
    ('a0000000-0000-4000-8000-000000000014', 'CLIENTE',       'Valentina Ortiz',     'valentina.ortiz@correo.com',          '3162290458', NULL),
    ('a0000000-0000-4000-8000-000000000015', 'CLIENTE',       'Natalia Suárez',      'natalia.suarez@correo.com',           '3215547780', NULL),
    ('a0000000-0000-4000-8000-000000000016', 'CLIENTE',       'Andrea Villamizar',   'andrea.villamizar@correo.com',        '3109924516', NULL),
    ('a0000000-0000-4000-8000-000000000017', 'CLIENTE',       'Miguel Ángel Duarte', 'miguel.duarte@correo.com',            '3014418863', NULL)
  ) AS u(id, rol, nombre, email, celular, empresa)
  JOIN roles r ON r.codigo = u.rol
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Desarrolladores (especialidad) y equipos con su Product Owner (HU-25, HU-26)
-- ---------------------------------------------------------------------
INSERT INTO desarrolladores (id, nombre, correo, celular, especialidad, usuario_id, activo, fecha_ingreso)
SELECT d.id::uuid, d.nombre, d.correo, d.celular, d.especialidad::especialidad, d.usuario_id::uuid, d.activo, date '2025-02-03'
  FROM (VALUES
    ('b0000000-0000-4000-8000-000000000001', 'Andrés Pérez',     'andres.perez@coretechsolutions.co',     '3204459021', 'FULL_STACK', 'a0000000-0000-4000-8000-000000000002', true),
    ('b0000000-0000-4000-8000-000000000002', 'Camilo Suárez',    'camilo.suarez@coretechsolutions.co',    '3184479203', 'QA',         NULL, true),
    ('b0000000-0000-4000-8000-000000000003', 'Diego Castro',     'diego.castro@coretechsolutions.co',     '3168834021', 'BACKEND',    NULL, true),
    ('b0000000-0000-4000-8000-000000000004', 'Sofía Herrera',    'sofia.herrera@coretechsolutions.co',    '3057723190', 'FRONTEND',   NULL, true),
    ('b0000000-0000-4000-8000-000000000005', 'Tomás Rivera',     'tomas.rivera@coretechsolutions.co',     '3012280935', 'MOVIL',      NULL, true),
    ('b0000000-0000-4000-8000-000000000006', 'Valeria Rincón',   'valeria.rincon@coretechsolutions.co',   '3126610482', 'UX_UI',      NULL, true),
    ('b0000000-0000-4000-8000-000000000007', 'María Ruiz',       'maria.ruiz@coretechsolutions.co',       '3118802344', 'FRONTEND',   'a0000000-0000-4000-8000-000000000003', true),
    ('b0000000-0000-4000-8000-000000000008', 'Juan Esteban Gil', 'juan.gil@coretechsolutions.co',         '3009126648', 'QA',         NULL, true),
    ('b0000000-0000-4000-8000-000000000009', 'Kevin Rojas',      'kevin.rojas@coretechsolutions.co',      '3137765012', 'FULL_STACK', NULL, true),
    ('b0000000-0000-4000-8000-000000000010', 'Paula Méndez',     'paula.mendez@coretechsolutions.co',     '3205531877', 'UX_UI',      NULL, true),
    ('b0000000-0000-4000-8000-000000000011', 'Sebastián Mora',   'sebastian.mora@coretechsolutions.co',   '3009917366', 'BACKEND',    'a0000000-0000-4000-8000-000000000004', true),
    ('b0000000-0000-4000-8000-000000000012', 'Daniel Quintero',  'daniel.quintero@coretechsolutions.co',  '3152204779', 'FRONTEND',   NULL, true),
    ('b0000000-0000-4000-8000-000000000013', 'Mariana Peña',     'mariana.pena@coretechsolutions.co',     '3176648120', 'QA',         NULL, true),
    ('b0000000-0000-4000-8000-000000000014', 'Camila Jaimes',    'camila.jaimes@coretechsolutions.co',    '3175520918', 'MOVIL',      'a0000000-0000-4000-8000-000000000005', true),
    ('b0000000-0000-4000-8000-000000000015', 'Isabela Cáceres',  'isabela.caceres@coretechsolutions.co',  '3218804361', 'UX_UI',      NULL, true),
    ('b0000000-0000-4000-8000-000000000016', 'Óscar Lozano',     'oscar.lozano@coretechsolutions.co',     '3049917254', 'MOVIL',      NULL, true),
    ('b0000000-0000-4000-8000-000000000017', 'Ana María Vera',   'ana.vera@coretechsolutions.co',         '3195562013', 'QA',         NULL, true),
    ('b0000000-0000-4000-8000-000000000018', 'Jhon Pabón',       'jhon.pabon@coretechsolutions.co',       '3103348857', 'BACKEND',    NULL, true),
    ('b0000000-0000-4000-8000-000000000019', 'Diego Ramírez',    'diego.ramirez@coretechsolutions.co',    '3016643275', 'BACKEND',    NULL, false)
  ) AS d(id, nombre, correo, celular, especialidad, usuario_id, activo)
ON CONFLICT DO NOTHING;

INSERT INTO equipos (id, max_proyectos, activo) VALUES
  ('c0000000-0000-4000-8000-000000000001', 3, true),   -- Equipo de Andrés Pérez
  ('c0000000-0000-4000-8000-000000000002', 3, true),   -- Equipo de María Ruiz
  ('c0000000-0000-4000-8000-000000000003', 3, true),   -- Equipo de Sebastián Mora
  ('c0000000-0000-4000-8000-000000000004', 3, true)    -- Equipo de Camila Jaimes
ON CONFLICT DO NOTHING;

INSERT INTO integrantes_equipo (equipo_id, desarrollador_id, rol, desde)
SELECT i.equipo::uuid, i.desarrollador::uuid, i.rol::rol_equipo, date '2025-03-03'
  FROM (VALUES
    ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'PRODUCT_OWNER'),
    ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002', 'QA'),
    ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000003', 'BACKEND'),
    ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000004', 'FRONTEND'),
    ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000005', 'MOVIL'),
    ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000006', 'UX_UI'),
    ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000007', 'PRODUCT_OWNER'),
    ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000008', 'QA'),
    ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000009', 'BACKEND'),
    ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000010', 'UX_UI'),
    ('c0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000011', 'PRODUCT_OWNER'),
    ('c0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000012', 'FRONTEND'),
    ('c0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000013', 'QA'),
    ('c0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000014', 'PRODUCT_OWNER'),
    ('c0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000015', 'UX_UI'),
    ('c0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000016', 'MOVIL')
  ) AS i(equipo, desarrollador, rol)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Festivos de Colombia 2026 y 2027 (Ley 51 de 1983; RN-01, CalendarioHabil)
-- ---------------------------------------------------------------------
INSERT INTO dias_no_habiles (fecha, motivo, actualizado_por)
SELECT f.fecha::date, f.motivo, 'a0000000-0000-4000-8000-000000000001'::uuid
  FROM (VALUES
    ('2026-01-01', 'Festivo · Año Nuevo'),
    ('2026-01-12', 'Festivo · Reyes Magos'),
    ('2026-03-23', 'Festivo · San José'),
    ('2026-04-02', 'Festivo · Jueves Santo'),
    ('2026-04-03', 'Festivo · Viernes Santo'),
    ('2026-05-01', 'Festivo · Día del Trabajo'),
    ('2026-05-18', 'Festivo · Ascensión del Señor'),
    ('2026-06-08', 'Festivo · Corpus Christi'),
    ('2026-06-15', 'Festivo · Sagrado Corazón'),
    ('2026-06-29', 'Festivo · San Pedro y San Pablo'),
    ('2026-07-20', 'Festivo · Día de la Independencia'),
    ('2026-08-07', 'Festivo · Batalla de Boyacá'),
    ('2026-08-17', 'Festivo · Asunción de la Virgen'),
    ('2026-10-12', 'Festivo · Día de la Raza'),
    ('2026-11-02', 'Festivo · Todos los Santos'),
    ('2026-11-16', 'Festivo · Independencia de Cartagena'),
    ('2026-12-08', 'Festivo · Inmaculada Concepción'),
    ('2026-12-25', 'Festivo · Navidad'),
    ('2027-01-01', 'Festivo · Año Nuevo'),
    ('2027-01-11', 'Festivo · Reyes Magos'),
    ('2027-03-22', 'Festivo · San José'),
    ('2027-03-25', 'Festivo · Jueves Santo'),
    ('2027-03-26', 'Festivo · Viernes Santo'),
    ('2027-05-01', 'Festivo · Día del Trabajo'),
    ('2027-05-10', 'Festivo · Ascensión del Señor'),
    ('2027-05-31', 'Festivo · Corpus Christi'),
    ('2027-06-07', 'Festivo · Sagrado Corazón'),
    ('2027-07-05', 'Festivo · San Pedro y San Pablo'),
    ('2027-07-20', 'Festivo · Día de la Independencia'),
    ('2027-08-07', 'Festivo · Batalla de Boyacá'),
    ('2027-08-16', 'Festivo · Asunción de la Virgen'),
    ('2027-10-18', 'Festivo · Día de la Raza'),
    ('2027-11-01', 'Festivo · Todos los Santos'),
    ('2027-11-15', 'Festivo · Independencia de Cartagena'),
    ('2027-12-08', 'Festivo · Inmaculada Concepción'),
    ('2027-12-25', 'Festivo · Navidad')
  ) AS f(fecha, motivo)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. Citas y cuestionarios (HU-01, HU-08, HU-09, HU-10)
-- ---------------------------------------------------------------------
-- Citas pasadas (fechas fijas, como en el prototipo)
INSERT INTO citas (id, codigo, cliente_id, servicio_id, equipo_id, fecha_hora, modalidad, descripcion_necesidad,
                   estado, enlace_reunion, confirmada_at, atendida_at, cancelada_at, cancelada_por, motivo_cancelacion, created_at)
SELECT c.id::uuid, c.codigo, c.cliente::uuid, s.id, c.equipo::uuid, c.fecha_hora::timestamptz, c.modalidad::modalidad, c.descripcion,
       c.estado::estado_cita, c.enlace, c.confirmada::timestamptz, c.atendida::timestamptz, c.cancelada::timestamptz,
       c.cancelada_por::uuid, c.motivo, c.creada::timestamptz
  FROM (VALUES
    ('d0000000-0000-4000-8000-000000000045', 'C-0045', 'a0000000-0000-4000-8000-000000000010', 'web-corporativa', NULL,
     '2026-09-08 09:00-05', 'VIRTUAL', 'Quiero una página para mostrar mis accesorios y recibir pedidos por WhatsApp.',
     'CANCELADA', NULL, NULL, NULL, '2026-09-04 18:10-05', 'a0000000-0000-4000-8000-000000000010',
     'Ya no puedo asistir ese día por un viaje.', '2026-09-01 11:20-05'),
    ('d0000000-0000-4000-8000-000000000051', 'C-0051', 'a0000000-0000-4000-8000-000000000010', 'tienda-en-linea', 'c0000000-0000-4000-8000-000000000001',
     '2026-09-22 10:00-05', 'VIRTUAL', 'Necesito una tienda en línea para vender accesorios con pagos por PSE y tarjeta.',
     'ATENDIDA', 'https://meet.google.com/ctx-laur-a51', '2026-09-17 15:40-05', '2026-09-22 11:05-05', NULL, NULL, NULL, '2026-09-15 09:40-05'),
    ('d0000000-0000-4000-8000-000000000058', 'C-0058', 'a0000000-0000-4000-8000-000000000010', 'sistema-inventario', 'c0000000-0000-4000-8000-000000000001',
     '2026-10-27 15:00-05', 'PRESENCIAL', 'Quiero controlar el inventario del salón de belleza: productos, entradas y salidas.',
     'ATENDIDA', NULL, '2026-10-22 09:15-05', '2026-10-27 16:10-05', NULL, NULL, NULL, '2026-10-20 17:05-05')
,
    ('d0000000-0000-4000-8000-000000000057', 'C-0057', 'a0000000-0000-4000-8000-000000000017', 'web-corporativa', 'c0000000-0000-4000-8000-000000000004',
     '2026-11-05 10:00-05', 'VIRTUAL', 'Quiero renovar la web de mi firma de asesoría tributaria y contable; la actual no se ve bien en celular.',
     'ATENDIDA', 'https://meet.google.com/ctx-migu-a57', '2026-11-02 09:30-05', '2026-11-05 11:10-05', NULL, NULL, NULL, '2026-10-30 16:20-05')
  ) AS c(id, codigo, cliente, servicio, equipo, fecha_hora, modalidad, descripcion, estado, enlace, confirmada, atendida,
         cancelada, cancelada_por, motivo, creada)
  JOIN servicios s ON s.slug = c.servicio
ON CONFLICT DO NOTHING;

-- Citas próximas: día hábil (lunes a viernes, sin festivo) a una semana de hoy, para que siempre sean futuras
INSERT INTO citas (id, codigo, cliente_id, servicio_id, equipo_id, fecha_hora, modalidad, descripcion_necesidad, estado, created_at)
SELECT c.id::uuid, c.codigo, c.cliente::uuid, s.id, c.equipo::uuid,
       ((SELECT min(d)::date FROM generate_series(current_date + c.dias, current_date + c.dias + 10, interval '1 day') AS d
          WHERE extract(isodow FROM d) < 6 AND NOT EXISTS (SELECT 1 FROM dias_no_habiles n WHERE n.fecha = d::date))
        + c.hora::time) AT TIME ZONE 'America/Bogota',
       c.modalidad::modalidad, c.descripcion, 'PENDIENTE', now()
  FROM (VALUES
    ('d0000000-0000-4000-8000-000000000061', 'C-0061', 'a0000000-0000-4000-8000-000000000010', 'web-corporativa', 'c0000000-0000-4000-8000-000000000001',
     7, '09:00', 'VIRTUAL', 'Quiero una página corporativa para el salón con los servicios y los precios.'),
    ('d0000000-0000-4000-8000-000000000062', 'C-0062', 'a0000000-0000-4000-8000-000000000011', 'app-movil', NULL,
     8, '10:00', 'PRESENCIAL', 'Necesitamos una aplicación para que los clientes del gimnasio reserven sus clases.')
  ) AS c(id, codigo, cliente, servicio, equipo, dias, hora, modalidad, descripcion)
  JOIN servicios s ON s.slug = c.servicio
ON CONFLICT DO NOTHING;

INSERT INTO cuestionarios_cita (cita_id, resumen, descripcion_cliente, presupuesto, fecha_deseada, idioma, urgencia, registrado_por, created_at)
SELECT q.cita::uuid, q.resumen, q.cliente, q.presupuesto::rango_presupuesto, q.fecha_deseada, 'ESPANOL', q.urgencia::urgencia,
       'a0000000-0000-4000-8000-000000000002'::uuid, q.creado::timestamptz
  FROM (VALUES
    ('d0000000-0000-4000-8000-000000000051',
     'Laura vende accesorios por Instagram y quiere su propia tienda con catálogo por talla y color, carrito y pagos con PSE y tarjeta.',
     'Negocio familiar en Cúcuta con 2 personas; despachan pedidos a domicilio en la ciudad.',
     'DE_5_A_15M', 'Antes de la temporada de diciembre', 'ALTA', '2026-09-22 11:05-05'),
    ('d0000000-0000-4000-8000-000000000058',
     'Laura quiere controlar los productos del salón: existencias por bodega, entradas, salidas y alertas cuando algo se acaba.',
     'Salón de belleza con 6 empleadas; hoy llevan el inventario en un cuaderno y en hojas de cálculo.',
     'DE_5_A_15M', 'Primer trimestre de 2027', 'MEDIA', '2026-10-27 16:10-05')
,
    ('d0000000-0000-4000-8000-000000000057',
     'Miguel quiere renovar la web de Duarte Asesores para presentar sus servicios tributarios y contables, publicar artículos y recibir solicitudes de asesoría por formulario.',
     'Socio fundador de Duarte Asesores, firma de asesoría tributaria en Cúcuta con 8 empleados y clientes empresariales. Su web actual es de 2018 y no se ve bien en celular.',
     'DE_5_A_15M', 'Antes de febrero de 2027', 'MEDIA', '2026-11-05 11:10-05')
  ) AS q(cita, resumen, cliente, presupuesto, fecha_deseada, urgencia, creado)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 5. Proyectos (HU-02, HU-28, HU-29, HU-03): se recorre el mismo camino que la aplicación,
--    porque los disparadores lo exigen: plan en BORRADOR copiado de la plantilla → ENVIADO →
--    APROBADO → fases y pasos con avance.
-- ---------------------------------------------------------------------
DO $$
DECLARE
  laura     constant uuid := 'a0000000-0000-4000-8000-000000000010';
  andres    constant uuid := 'a0000000-0000-4000-8000-000000000002';
  patricia  constant uuid := 'a0000000-0000-4000-8000-000000000001';
  equipo    constant uuid := 'c0000000-0000-4000-8000-000000000001';
  proyecto  constant uuid := 'e0000000-0000-4000-8000-000000000031';
  f1 uuid; f2 uuid; f3 uuid;
BEGIN
  IF EXISTS (SELECT 1 FROM proyectos WHERE codigo = 'P-0031') THEN
    RETURN;
  END IF;

  -- P-0031 · Tienda en línea Laura: fases 1 y 2 aprobadas, fase 3 (Desarrollo) en curso → 40 %
  INSERT INTO proyectos (id, codigo, nombre, alcance, cita_id, cliente_id, servicio_id, equipo_id, creado_por,
                         fecha_inicio, fecha_estimada_entrega, created_at)
  SELECT proyecto, 'P-0031', 'Tienda en línea Laura',
         'Tienda en línea de accesorios con catálogo por talla y color, carrito, pagos con PSE y tarjeta, pago contra entrega en Cúcuta y panel de pedidos.',
         'd0000000-0000-4000-8000-000000000051', laura, s.id, equipo, patricia,
         date '2026-09-29', date '2026-12-18', timestamptz '2026-09-23 10:00-05'
    FROM servicios s WHERE s.slug = 'tienda-en-linea';

  INSERT INTO proyecto_fases (proyecto_id, orden, nombre, que_es, aprueba_cliente)
  SELECT proyecto, pf.orden, pf.nombre, pf.que_es, pf.aprueba_cliente
    FROM plantilla_fases pf JOIN servicios s ON s.id = pf.servicio_id
   WHERE s.slug = 'tienda-en-linea';

  INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion)
  SELECT f.id, pp.orden, 'PASO', pp.titulo, pp.explicacion
    FROM proyecto_fases f
    JOIN plantilla_fases pf ON pf.orden = f.orden
    JOIN servicios s ON s.id = pf.servicio_id AND s.slug = 'tienda-en-linea'
    JOIN plantilla_pasos pp ON pp.plantilla_fase_id = pf.id
   WHERE f.proyecto_id = proyecto;

  SELECT id INTO f1 FROM proyecto_fases WHERE proyecto_id = proyecto AND orden = 1;
  SELECT id INTO f2 FROM proyecto_fases WHERE proyecto_id = proyecto AND orden = 2;
  SELECT id INTO f3 FROM proyecto_fases WHERE proyecto_id = proyecto AND orden = 3;

  -- El Product Owner ajustó el plan en borrador: un paso más en Desarrollo
  INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion)
  VALUES (f3, 5, 'PASO', 'Pago contra entrega', 'Tus clientes de Cúcuta podrán pagar en efectivo cuando reciban el pedido.');

  -- Envío del plan con la cotización y aprobación del cliente (RN-29)
  UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 9500000,
         condiciones_cotizacion = 'Incluye diseño, construcción, publicación, manuales y capacitación. No incluye fotos de productos ni el costo del dominio.',
         plan_enviado_at = timestamptz '2026-09-25 17:00-05', plan_plazo_respuesta = date '2026-09-30', plan_vence_el = date '2026-10-09'
   WHERE id = proyecto;
  INSERT INTO respuestas_plan (proyecto_id, cliente_id, respuesta, created_at)
  VALUES (proyecto, laura, 'APROBADO', timestamptz '2026-09-28 09:12-05');
  UPDATE proyectos SET estado_plan = 'APROBADO', plan_aprobado_at = timestamptz '2026-09-28 09:12-05' WHERE id = proyecto;

  -- Fase 1 · Toma de requerimientos (aprobada por Laura)
  UPDATE pasos SET estado = 'HECHO', registrado_por = andres, hecho_at = v.hecho::timestamptz, nota = v.nota
    FROM (VALUES
      (1, 'Hablamos de los accesorios que vendes, de tus clientes y de cómo entregas los pedidos.', '2026-09-30 16:00-05'),
      (2, 'Quedaron 14 funciones: ver productos, buscar, agregar al carrito, pagar y recibir confirmación, entre otras.', '2026-10-02 15:30-05'),
      (3, 'Te lo envié por correo para que lo revisaras con calma.', '2026-10-05 11:00-05')
    ) AS v(orden, nota, hecho)
   WHERE pasos.proyecto_fase_id = f1 AND pasos.orden = v.orden;
  UPDATE proyecto_fases SET estado = 'APROBADA', iniciada_at = timestamptz '2026-09-29 08:00-05',
         entregada_at = timestamptz '2026-10-05 11:00-05', plazo_aprobacion = date '2026-10-08',
         aprobada_at = timestamptz '2026-10-06 10:20-05', aprobada_por = laura
   WHERE id = f1;

  -- Fase 2 · Análisis y diseño (aprobada; un ajuste pedido por Laura y una corrección del equipo)
  UPDATE pasos SET estado = 'HECHO', registrado_por = andres, hecho_at = v.hecho::timestamptz, nota = v.nota
    FROM (VALUES
      (1, 'Diseñé la página de inicio, el catálogo, el producto y el carrito. Toca cada imagen para verla en grande.', '2026-10-10 17:00-05'),
      (2, 'Quedó listo cómo se guardan productos, precios, clientes y pedidos.', '2026-10-14 12:00-05')
    ) AS v(orden, nota, hecho)
   WHERE pasos.proyecto_fase_id = f2 AND pasos.orden = v.orden;
  INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion, origen, estado, nota, hecho_at, registrado_por, created_at)
  VALUES
    (f2, 3, 'AJUSTE', 'Elegir talla y color en la página de producto',
     'Tus clientes podrán escoger la talla y el color antes de comprar.', 'Lo pidió el cliente', 'HECHO',
     'Así quedó la página de producto: ya se puede elegir talla y color.', timestamptz '2026-10-21 16:30-05', andres,
     timestamptz '2026-10-16 09:00-05'),
    (f2, 4, 'CORRECCION', 'Guardar existencias por talla y color',
     'Así sabrás cuántas unidades te quedan de cada talla y color, y no venderás algo que no tienes.', 'La encontramos al construir', 'HECHO',
     'Corregido. Mientras lo arreglaba (5 al 8 nov) esta fase no sumó su 20 %.', timestamptz '2026-11-08 18:00-05', andres,
     timestamptz '2026-11-05 10:00-05');
  UPDATE proyecto_fases SET estado = 'APROBADA', iniciada_at = timestamptz '2026-10-07 08:00-05',
         entregada_at = timestamptz '2026-10-21 16:30-05', plazo_aprobacion = date '2026-10-26', rondas_ajuste_usadas = 1,
         aprobada_at = timestamptz '2026-10-23 11:45-05', aprobada_por = laura
   WHERE id = f2;

  -- Fase 3 · Desarrollo (en curso: 3 de 5 pasos hechos)
  UPDATE pasos SET estado = 'HECHO', registrado_por = andres, hecho_at = v.hecho::timestamptz, nota = v.nota
    FROM (VALUES
      (1, 'Tus clientes ya pueden crear su cuenta e iniciar sesión.', '2026-10-28 17:00-05'),
      (2, 'El catálogo ya muestra los productos con sus filtros.', '2026-11-03 16:00-05'),
      (3, 'Ya se puede pagar con PSE y con tarjeta.', '2026-11-09 16:20-05')
    ) AS v(orden, nota, hecho)
   WHERE pasos.proyecto_fase_id = f3 AND pasos.orden = v.orden;
  UPDATE pasos SET estado = 'EN_CURSO' WHERE proyecto_fase_id = f3 AND orden = 4;
  UPDATE proyecto_fases SET estado = 'EN_CURSO', iniciada_at = timestamptz '2026-10-24 08:00-05' WHERE id = f3;
END $$;

DO $$
DECLARE
  laura     constant uuid := 'a0000000-0000-4000-8000-000000000010';
  patricia  constant uuid := 'a0000000-0000-4000-8000-000000000001';
  equipo    constant uuid := 'c0000000-0000-4000-8000-000000000001';
  proyecto  constant uuid := 'e0000000-0000-4000-8000-000000000035';
BEGIN
  IF EXISTS (SELECT 1 FROM proyectos WHERE codigo = 'P-0035') THEN
    RETURN;
  END IF;

  -- P-0035 · Inventario del salón: plan y cotización enviados, esperando la respuesta de Laura (HU-29)
  INSERT INTO proyectos (id, codigo, nombre, alcance, cita_id, cliente_id, servicio_id, equipo_id, creado_por,
                         fecha_inicio, fecha_estimada_entrega)
  SELECT proyecto, 'P-0035', 'Inventario del salón',
         'Sistema de inventario para el salón de belleza: productos por bodega, entradas y salidas, alertas de existencias bajas y reportes.',
         'd0000000-0000-4000-8000-000000000058', laura, s.id, equipo, patricia,
         current_date + 7, current_date + 90
    FROM servicios s WHERE s.slug = 'sistema-inventario';

  INSERT INTO proyecto_fases (proyecto_id, orden, nombre, que_es, aprueba_cliente)
  SELECT proyecto, pf.orden, pf.nombre, pf.que_es, pf.aprueba_cliente
    FROM plantilla_fases pf JOIN servicios s ON s.id = pf.servicio_id
   WHERE s.slug = 'sistema-inventario';

  INSERT INTO pasos (proyecto_fase_id, orden, tipo, titulo, explicacion)
  SELECT f.id, pp.orden, 'PASO', pp.titulo, pp.explicacion
    FROM proyecto_fases f
    JOIN plantilla_fases pf ON pf.orden = f.orden
    JOIN servicios s ON s.id = pf.servicio_id AND s.slug = 'sistema-inventario'
    JOIN plantilla_pasos pp ON pp.plantilla_fase_id = pf.id
   WHERE f.proyecto_id = proyecto;

  -- Plazos de demostración relativos a hoy, para que la propuesta siga "por aprobar"
  -- (3 y 10 días hábiles los calcula CalendarioHabil en la aplicación real)
  UPDATE proyectos SET estado_plan = 'ENVIADO', valor_cotizado = 7800000,
         condiciones_cotizacion = 'Incluye el sistema, la carga de tus productos, los manuales y la capacitación. No incluye computadores ni lectores de código de barras.',
         plan_enviado_at = now() - interval '1 day', plan_plazo_respuesta = current_date + 3, plan_vence_el = current_date + 14
   WHERE id = proyecto;
END $$;

-- ---------------------------------------------------------------------
-- 6. Secuencias de los códigos visibles: la primera cita o proyecto nuevo sigue al último demo
-- ---------------------------------------------------------------------
SELECT setval('seq_codigo_cita', greatest(
         (SELECT coalesce(max(substring(codigo FROM 3)::bigint), 1) FROM citas WHERE codigo ~ '^C-[0-9]+$'),
         (SELECT last_value FROM seq_codigo_cita)));
SELECT setval('seq_codigo_proyecto', greatest(
         (SELECT coalesce(max(substring(codigo FROM 3)::bigint), 1) FROM proyectos WHERE codigo ~ '^P-[0-9]+$'),
         (SELECT last_value FROM seq_codigo_proyecto)));
