-- V5 · Textos editables de la página principal (HU-07, HU-19, RF-45, RNF-06).
-- V1 creó la tabla contenido_sitio, pero sin filas. Son los textos y los límites de
-- prototipo/admin/contenido.html (minlength y maxlength de cada campo); los valores son los de
-- prototipo/index.html. El administrador los cambia desde "Contenido del sitio" sin desplegar.
-- ck_contenido_valor revisa que cada valor quede entre su mínimo y su máximo.

INSERT INTO contenido_sitio (clave, seccion, valor, min_longitud, max_longitud) VALUES
  -- Portada
  ('portada-etiqueta',     'Portada', 'Software a la medida', 3, 30),
  ('portada-titulo-1',     'Portada', 'Tu idea, hecha software.', 5, 40),
  ('portada-titulo-2',     'Portada', 'Y cada avance, a la vista.', 5, 40),
  ('portada-texto',        'Portada', 'De la primera reunión al despliegue, con un equipo a cargo y tu proyecto visible fase por fase desde tu panel.', 20, 160),
  ('portada-boton',        'Portada', 'Agendar una cita', 3, 24),
  ('portada-boton-2',      'Portada', 'Ver servicios', 3, 24),
  -- Encabezado de servicios
  ('servicios-titulo',     'Servicios', 'Servicios', 3, 30),
  ('servicios-texto',      'Servicios', 'Elige el servicio que necesitas y agenda una cita con nuestro equipo.', 10, 120),
  -- Cómo funciona
  ('proceso-titulo',       'Proceso', 'Cómo funciona', 3, 40),
  ('proceso-texto',        'Proceso', 'Desde la primera reunión hasta el despliegue, siempre sabes en qué va tu proyecto.', 10, 120),
  ('proceso-paso-1',       'Proceso', 'Agenda tu cita', 5, 40),
  ('proceso-paso-1-texto', 'Proceso', 'Elige el servicio, la fecha y si prefieres la reunión presencial o virtual.', 20, 120),
  ('proceso-paso-2',       'Proceso', 'Te asignamos un equipo', 5, 40),
  ('proceso-paso-2-texto', 'Proceso', 'Creamos tu proyecto y un equipo lo construye; su Product Owner es tu contacto de principio a fin.', 20, 120),
  ('proceso-paso-3',       'Proceso', 'Sigue cada fase', 5, 40),
  ('proceso-paso-3-texto', 'Proceso', 'Revisa cada fase, apruébala o pide cambios, y habla con tu Product Owner cuando lo necesites.', 20, 120),
  -- Contacto (también en el pie de página)
  ('contacto-titulo',      'Contacto', 'Hablemos de tu proyecto', 3, 40),
  ('contacto-texto',       'Contacto', 'Atendemos en nuestra oficina de Cúcuta o por videollamada, en el horario que elijas al agendar.', 10, 140),
  ('contacto-direccion',   'Contacto', 'Calle 10 # 4-25, oficina 302, Cúcuta', 10, 70),
  ('contacto-horario',     'Contacto', 'Lunes a viernes, 8:00 a. m. a 12:00 m. y 2:00 p. m. a 6:00 p. m.', 10, 90),
  ('contacto-telefono',    'Contacto', '300 123 4567', 7, 14),
  ('contacto-correo',      'Contacto', 'contacto@coretechsolutions.co', 6, 60),
  -- Llamado final
  ('llamado-titulo',       'Llamado final', '¿Listo para empezar tu proyecto?', 5, 50),
  ('llamado-texto',        'Llamado final', 'Agenda tu primera reunión en menos de dos minutos.', 10, 100),
  -- Pie de página
  ('pie-descripcion',      'Pie de página', 'Empresa de desarrollo de software a la medida en Cúcuta. Creamos tiendas en línea, sitios web, aplicaciones y sistemas de gestión, y te mostramos cada avance de tu proyecto.', 30, 220),
  ('pie-mision',           'Pie de página', 'Desarrollar software a la medida que resuelva necesidades reales de las empresas de la región, con un proceso transparente en el que cada cliente sigue el avance de su proyecto de principio a fin.', 30, 300),
  ('pie-vision',           'Pie de página', 'Para 2030, ser la empresa de desarrollo de software de referencia en el oriente colombiano, reconocida por la calidad de sus soluciones y la confianza que genera en sus clientes.', 30, 300);
