CREATE TYPE "especialidad" AS ENUM (
  'FRONTEND',
  'BACKEND',
  'FULL_STACK',
  'MOVIL',
  'QA',
  'UX_UI'
);

CREATE TYPE "rol_equipo" AS ENUM (
  'PRODUCT_OWNER',
  'FRONTEND',
  'BACKEND',
  'FULL_STACK',
  'MOVIL',
  'QA',
  'UX_UI'
);

CREATE TYPE "rango_presupuesto" AS ENUM (
  'MENOS_5M',
  'DE_5_A_15M',
  'DE_15_A_30M',
  'MAS_30M',
  'SIN_DEFINIR'
);

CREATE TYPE "idioma" AS ENUM (
  'ESPANOL',
  'INGLES',
  'PORTUGUES',
  'OTRO'
);

CREATE TYPE "urgencia" AS ENUM (
  'BAJA',
  'MEDIA',
  'ALTA'
);

CREATE TYPE "estado_cita" AS ENUM (
  'PENDIENTE',
  'CONFIRMADA',
  'ATENDIDA',
  'CANCELADA'
);

CREATE TYPE "modalidad" AS ENUM (
  'PRESENCIAL',
  'VIRTUAL'
);

CREATE TYPE "estado_proyecto" AS ENUM (
  'ACTIVO',
  'PAUSADO',
  'EN_GARANTIA',
  'FINALIZADO',
  'CANCELADO'
);

CREATE TYPE "estado_plan" AS ENUM (
  'BORRADOR',
  'ENVIADO',
  'AJUSTES_PEDIDOS',
  'APROBADO',
  'VENCIDO',
  'RECHAZADO'
);

CREATE TYPE "tipo_respuesta_plan" AS ENUM (
  'APROBADO',
  'AJUSTES_PEDIDOS',
  'RECHAZADO'
);

CREATE TYPE "estado_fase" AS ENUM (
  'PENDIENTE',
  'EN_CURSO',
  'EN_REVISION',
  'APROBADA',
  'EN_CORRECCION',
  'QUITADA'
);

CREATE TYPE "estado_paso" AS ENUM (
  'PENDIENTE',
  'EN_CURSO',
  'HECHO',
  'QUITADO'
);

CREATE TYPE "tipo_paso" AS ENUM (
  'PASO',
  'CORRECCION',
  'AJUSTE',
  'ALCANCE'
);

CREATE TYPE "estado_solicitud" AS ENUM (
  'PENDIENTE',
  'PROGRAMADA',
  'RECHAZADA',
  'CANCELADA',
  'ATENDIDA'
);

CREATE TYPE "estado_cambio" AS ENUM (
  'ABIERTO',
  'POR_COTIZAR',
  'COTIZADO',
  'ACEPTADO',
  'RECHAZADO',
  'RESUELTO'
);

CREATE TYPE "tipo_cambio_plan" AS ENUM (
  'AGREGAR_FASE',
  'QUITAR_FASE',
  'CAMBIAR_FECHA',
  'QUITAR_PASO'
);

CREATE TYPE "estado_cambio_plan" AS ENUM (
  'PROPUESTO',
  'AUTORIZADO',
  'ACEPTADO',
  'RECHAZADO_ADMIN',
  'RECHAZADO_CLIENTE'
);

CREATE TABLE "roles" (
  "id" smallserial PRIMARY KEY,
  "codigo" varchar(20) UNIQUE NOT NULL,
  "nombre" varchar(40) NOT NULL,
  "descripcion" varchar(200)
);

CREATE TABLE "permisos" (
  "id" smallserial PRIMARY KEY,
  "codigo" varchar(50) UNIQUE NOT NULL,
  "modulo" varchar(40) NOT NULL,
  "descripcion" varchar(200) NOT NULL
);

CREATE TABLE "rol_permisos" (
  "rol_id" smallint NOT NULL,
  "permiso_id" smallint NOT NULL,
  "bloqueado" boolean NOT NULL DEFAULT false,
  "asignado_at" timestamptz NOT NULL DEFAULT (now()),
  PRIMARY KEY ("rol_id", "permiso_id")
);

CREATE TABLE "usuarios" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "rol_id" smallint NOT NULL,
  "nombre" varchar(60) NOT NULL,
  "email" varchar(120) UNIQUE NOT NULL,
  "celular" varchar(10) NOT NULL,
  "empresa" varchar(80),
  "password_hash" varchar(255) NOT NULL,
  "debe_cambiar_password" boolean NOT NULL DEFAULT false,
  "acepta_datos_at" timestamptz,
  "activo" boolean NOT NULL DEFAULT true,
  "intentos_fallidos" smallint NOT NULL DEFAULT 0,
  "bloqueado_hasta" timestamptz,
  "ultimo_acceso_at" timestamptz,
  "anonimizado_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "tokens_recuperacion" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "usuario_id" uuid NOT NULL,
  "token_hash" varchar(255) UNIQUE NOT NULL,
  "expira_at" timestamptz NOT NULL,
  "usado_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "desarrolladores" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "nombre" varchar(60) NOT NULL,
  "correo" varchar(120) UNIQUE NOT NULL,
  "celular" varchar(10) NOT NULL,
  "especialidad" especialidad NOT NULL,
  "usuario_id" uuid UNIQUE,
  "activo" boolean NOT NULL DEFAULT true,
  "fecha_ingreso" date NOT NULL DEFAULT (current_date),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "equipos" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "max_proyectos" smallint NOT NULL DEFAULT 3,
  "activo" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "integrantes_equipo" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "equipo_id" uuid NOT NULL,
  "desarrollador_id" uuid UNIQUE NOT NULL,
  "rol" rol_equipo NOT NULL,
  "desde" date NOT NULL DEFAULT (current_date)
);

CREATE TABLE "servicios" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "nombre" varchar(40) UNIQUE NOT NULL,
  "slug" varchar(50) UNIQUE NOT NULL,
  "descripcion_corta" varchar(90) NOT NULL,
  "imagen_url" varchar(255) NOT NULL,
  "imagen_alt" varchar(120) NOT NULL,
  "orden" smallint NOT NULL,
  "visible" boolean NOT NULL DEFAULT true,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "contenido_sitio" (
  "clave" varchar(40) PRIMARY KEY,
  "seccion" varchar(30) NOT NULL,
  "valor" varchar(300) NOT NULL,
  "min_longitud" smallint NOT NULL,
  "max_longitud" smallint NOT NULL,
  "actualizado_por" uuid,
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "horario_laboral" (
  "id" smallserial PRIMARY KEY,
  "dia_semana" smallint NOT NULL,
  "hora_inicio" time NOT NULL,
  "hora_fin" time NOT NULL,
  "actualizado_por" uuid,
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "dias_no_habiles" (
  "fecha" date PRIMARY KEY,
  "motivo" varchar(80) NOT NULL,
  "actualizado_por" uuid,
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "citas" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "codigo" varchar(10) UNIQUE NOT NULL,
  "cliente_id" uuid NOT NULL,
  "servicio_id" uuid NOT NULL,
  "equipo_id" uuid,
  "fecha_hora" timestamptz NOT NULL,
  "modalidad" modalidad NOT NULL,
  "descripcion_necesidad" varchar(500) NOT NULL,
  "pais" varchar(60) NOT NULL DEFAULT 'Colombia',
  "zona_horaria" varchar(40) NOT NULL DEFAULT 'America/Bogota',
  "estado" estado_cita NOT NULL DEFAULT 'PENDIENTE',
  "enlace_reunion" varchar(255),
  "nota_cliente" varchar(300),
  "nota_asignacion" varchar(300),
  "motivo_cancelacion" varchar(300),
  "cancelada_por" uuid,
  "confirmada_at" timestamptz,
  "atendida_at" timestamptz,
  "cancelada_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "cuestionarios_cita" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "cita_id" uuid UNIQUE NOT NULL,
  "resumen" varchar(1000) NOT NULL,
  "descripcion_cliente" varchar(600) NOT NULL,
  "presupuesto" rango_presupuesto NOT NULL,
  "fecha_deseada" varchar(60),
  "idioma" idioma NOT NULL,
  "urgencia" urgencia NOT NULL,
  "registrado_por" uuid NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "plantilla_fases" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "servicio_id" uuid NOT NULL,
  "orden" smallint NOT NULL,
  "nombre" varchar(60) NOT NULL,
  "que_es" varchar(140),
  "aprueba_cliente" boolean NOT NULL DEFAULT false,
  "actualizado_por" uuid,
  "updated_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "plantilla_pasos" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "plantilla_fase_id" uuid NOT NULL,
  "orden" smallint NOT NULL,
  "titulo" varchar(80) NOT NULL,
  "explicacion" varchar(160)
);

CREATE TABLE "proyectos" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "codigo" varchar(10) UNIQUE NOT NULL,
  "nombre" varchar(60) NOT NULL,
  "alcance" varchar(600) NOT NULL,
  "cita_id" uuid UNIQUE NOT NULL,
  "cliente_id" uuid NOT NULL,
  "servicio_id" uuid NOT NULL,
  "equipo_id" uuid NOT NULL,
  "creado_por" uuid NOT NULL,
  "fecha_inicio" date NOT NULL,
  "fecha_estimada_entrega" date NOT NULL,
  "estado" estado_proyecto NOT NULL DEFAULT 'ACTIVO',
  "estado_plan" estado_plan NOT NULL DEFAULT 'BORRADOR',
  "valor_cotizado" numeric(14,0),
  "condiciones_cotizacion" varchar(600),
  "plan_enviado_at" timestamptz,
  "plan_plazo_respuesta" date,
  "plan_vence_el" date,
  "plan_aprobado_at" timestamptz,
  "avance_porcentaje" numeric(5,2) NOT NULL DEFAULT 0,
  "motivo_estado" varchar(300),
  "version" integer NOT NULL DEFAULT 0,
  "created_at" timestamptz NOT NULL DEFAULT (now()),
  "updated_at" timestamptz NOT NULL DEFAULT (now()),
  "garantia_hasta" date,
  "finalizado_at" timestamptz
);

CREATE TABLE "proyecto_fases" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "proyecto_id" uuid NOT NULL,
  "orden" smallint NOT NULL,
  "nombre" varchar(60) NOT NULL,
  "que_es" varchar(140),
  "aprueba_cliente" boolean NOT NULL DEFAULT false,
  "cambio_plan_id" uuid,
  "estado" estado_fase NOT NULL DEFAULT 'PENDIENTE',
  "iniciada_at" timestamptz,
  "entregada_at" timestamptz,
  "plazo_aprobacion" date,
  "rondas_ajuste_usadas" smallint NOT NULL DEFAULT 0,
  "aprobada_at" timestamptz,
  "aprobada_por" uuid,
  "aprobacion_automatica" boolean NOT NULL DEFAULT false
);

CREATE TABLE "pasos" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "proyecto_fase_id" uuid NOT NULL,
  "orden" smallint NOT NULL,
  "tipo" tipo_paso NOT NULL DEFAULT 'PASO',
  "titulo" varchar(80) NOT NULL,
  "explicacion" varchar(500) NOT NULL,
  "origen" varchar(60),
  "estado" estado_paso NOT NULL DEFAULT 'PENDIENTE',
  "nota" varchar(500),
  "hecho_at" timestamptz,
  "registrado_por" uuid,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "adjuntos_paso" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "paso_id" uuid NOT NULL,
  "titulo" varchar(80) NOT NULL,
  "url" varchar(255) NOT NULL,
  "tipo_mime" varchar(40) NOT NULL,
  "tamano_bytes" integer NOT NULL,
  "archivo_eliminado_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "solicitudes_cambio" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "proyecto_fase_id" uuid NOT NULL,
  "cliente_id" uuid NOT NULL,
  "tipo" tipo_paso NOT NULL,
  "descripcion" varchar(800) NOT NULL,
  "estado" estado_cambio NOT NULL,
  "paso_id" uuid UNIQUE,
  "costo" numeric(12,2),
  "dias_adicionales" smallint,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "respuestas_plan" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "proyecto_id" uuid NOT NULL,
  "cliente_id" uuid NOT NULL,
  "respuesta" tipo_respuesta_plan NOT NULL,
  "comentario" varchar(600),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "cambios_plan" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "proyecto_id" uuid NOT NULL,
  "tipo" tipo_cambio_plan NOT NULL,
  "nombre_fase" varchar(60),
  "posicion" smallint,
  "aprueba_cliente" boolean NOT NULL DEFAULT false,
  "pasos_propuestos" jsonb,
  "proyecto_fase_id" uuid,
  "motivo" varchar(500) NOT NULL,
  "paso_id" uuid,
  "nueva_fecha_entrega" date,
  "valor_adicional" numeric(14,0) NOT NULL DEFAULT 0,
  "estado" estado_cambio_plan NOT NULL DEFAULT 'PROPUESTO',
  "propuesto_por" uuid NOT NULL,
  "autorizado_por" uuid,
  "autorizado_at" timestamptz,
  "respondido_at" timestamptz,
  "motivo_rechazo" varchar(400),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "mensajes" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "proyecto_id" uuid NOT NULL,
  "autor_id" uuid NOT NULL,
  "tema" varchar(40),
  "contenido" varchar(1000) NOT NULL,
  "leido_at" timestamptz,
  "editado_at" timestamptz,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "solicitudes_reunion" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "codigo" varchar(10) UNIQUE NOT NULL,
  "proyecto_id" uuid NOT NULL,
  "cliente_id" uuid NOT NULL,
  "proyecto_fase_id" uuid NOT NULL,
  "motivo" varchar(40) NOT NULL,
  "razon" varchar(500) NOT NULL,
  "modalidad" modalidad NOT NULL,
  "opcion_1" timestamptz NOT NULL,
  "opcion_2" timestamptz,
  "estado" estado_solicitud NOT NULL DEFAULT 'PENDIENTE',
  "fecha_programada" timestamptz,
  "enlace_reunion" varchar(255),
  "motivo_rechazo" varchar(300),
  "gestionada_por" uuid,
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "notificaciones" (
  "id" uuid PRIMARY KEY DEFAULT (gen_random_uuid()),
  "usuario_id" uuid NOT NULL,
  "tipo" varchar(40) NOT NULL,
  "titulo" varchar(80) NOT NULL,
  "mensaje" varchar(255) NOT NULL,
  "enlace" varchar(255),
  "leida" boolean NOT NULL DEFAULT false,
  "enviada_email" boolean NOT NULL DEFAULT false,
  "intentos_envio" smallint NOT NULL DEFAULT 0,
  "ultimo_error" varchar(255),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE TABLE "auditoria" (
  "id" bigserial PRIMARY KEY,
  "usuario_id" uuid,
  "categoria" varchar(20) NOT NULL,
  "accion" varchar(60) NOT NULL,
  "entidad" varchar(40) NOT NULL,
  "entidad_id" varchar(40),
  "cambios" jsonb,
  "ip" varchar(45),
  "agente" varchar(120),
  "created_at" timestamptz NOT NULL DEFAULT (now())
);

CREATE INDEX ON "integrantes_equipo" ("equipo_id");

CREATE UNIQUE INDEX ON "horario_laboral" ("dia_semana", "hora_inicio");

CREATE INDEX ON "citas" ("cliente_id");

CREATE INDEX ON "citas" ("equipo_id");

CREATE INDEX ON "citas" ("fecha_hora", "estado");

CREATE UNIQUE INDEX ON "plantilla_fases" ("servicio_id", "orden");

CREATE UNIQUE INDEX ON "plantilla_pasos" ("plantilla_fase_id", "orden");

CREATE INDEX ON "proyectos" ("cliente_id");

CREATE INDEX ON "proyectos" ("equipo_id", "estado");

CREATE INDEX ON "proyecto_fases" ("proyecto_id", "orden");

CREATE INDEX ON "pasos" ("proyecto_fase_id", "orden");

CREATE INDEX ON "mensajes" ("proyecto_id", "created_at");

CREATE INDEX ON "notificaciones" ("usuario_id", "leida");

CREATE INDEX ON "auditoria" ("entidad", "entidad_id");

CREATE INDEX ON "auditoria" ("categoria", "created_at");

COMMENT ON TABLE "roles" IS 'Roles del sistema (HU-21). Datos iniciales: 3 filas.';

COMMENT ON COLUMN "roles"."codigo" IS 'CLIENTE · PRODUCT_OWNER · ADMINISTRADOR';

COMMENT ON COLUMN "permisos"."codigo" IS 'Ej.: proyectos.registrar_paso';

COMMENT ON COLUMN "permisos"."modulo" IS 'Citas, Proyectos, Sitio web, Seguridad…';

COMMENT ON TABLE "rol_permisos" IS 'Tabla intermedia N:M entre roles y permisos (HU-21).';

COMMENT ON COLUMN "rol_permisos"."bloqueado" IS 'Fijado por una regla de negocio: no se puede quitar (RN-19)';

COMMENT ON TABLE "usuarios" IS 'Clientes, desarrolladores (Product Owners) de cada equipo y administradores.';

COMMENT ON COLUMN "usuarios"."nombre" IS '3–60 caracteres (HU-05, HU-20)';

COMMENT ON COLUMN "usuarios"."email" IS 'Único (RN-18). Administradores y desarrolladores (Product Owners): dominio @coretechsolutions.co';

COMMENT ON COLUMN "usuarios"."celular" IS 'Patrón 3XXXXXXXXX';

COMMENT ON COLUMN "usuarios"."empresa" IS 'Opcional, solo clientes (HU-05)';

COMMENT ON COLUMN "usuarios"."password_hash" IS 'BCrypt; regla de contraseña segura (RN-22)';

COMMENT ON COLUMN "usuarios"."debe_cambiar_password" IS 'Contraseña temporal: administradores y desarrolladores (Product Owners) (RN-18)';

COMMENT ON COLUMN "usuarios"."acepta_datos_at" IS 'Aceptación de la Ley 1581 de 2012 (HU-05)';

COMMENT ON COLUMN "usuarios"."intentos_fallidos" IS 'Se reinicia al ingresar (RN-22)';

COMMENT ON COLUMN "usuarios"."bloqueado_hasta" IS '15 minutos tras 5 intentos fallidos (RN-22)';

COMMENT ON COLUMN "usuarios"."anonimizado_at" IS 'Derecho de supresión (Ley 1581): el administrador reemplaza nombre, correo, celular y empresa por valores anónimos; el historial se conserva';

COMMENT ON COLUMN "tokens_recuperacion"."expira_at" IS '30 minutos después de pedirlo (HU-06)';

COMMENT ON COLUMN "tokens_recuperacion"."usado_at" IS 'Un solo uso';

COMMENT ON TABLE "desarrolladores" IS 'Registro de todo el personal técnico (HU-25). No inician sesión, salvo el desarrollador (Product Owner).';

COMMENT ON COLUMN "desarrolladores"."nombre" IS '5–60 caracteres (HU-25)';

COMMENT ON COLUMN "desarrolladores"."correo" IS 'Corporativo @coretechsolutions.co';

COMMENT ON COLUMN "desarrolladores"."celular" IS '3XXXXXXXXX';

COMMENT ON COLUMN "desarrolladores"."especialidad" IS 'Define los roles que puede tener en un equipo (RN-26)';

COMMENT ON COLUMN "desarrolladores"."usuario_id" IS 'Solo el desarrollador (Product Owner) tiene cuenta (RN-25); NULL para los demás';

COMMENT ON TABLE "equipos" IS 'Se identifica por su desarrollador (Product Owner), por ejemplo "Equipo de Andrés Pérez" (HU-26).';

COMMENT ON COLUMN "equipos"."max_proyectos" IS 'Cupo de proyectos activos (RN-05)';

COMMENT ON TABLE "integrantes_equipo" IS '• RN-25: CREATE UNIQUE INDEX ux_un_po_por_equipo ON integrantes_equipo(equipo_id) WHERE rol = ''PRODUCT_OWNER'';
• RN-26: el disparador tg_validar_integrante verifica que el rol corresponda a la especialidad
  y que el desarrollador (Product Owner) tenga cuenta (desarrolladores.usuario_id no nulo).
';

COMMENT ON COLUMN "integrantes_equipo"."desarrollador_id" IS 'Un desarrollador solo puede estar en un equipo (RN-26)';

COMMENT ON COLUMN "integrantes_equipo"."rol" IS 'Permitido por su especialidad (RN-26)';

COMMENT ON TABLE "servicios" IS 'Catálogo de servicios (HU-07, HU-18). Un servicio con citas no se borra: se oculta (RN-21).';

COMMENT ON COLUMN "servicios"."nombre" IS '4–40 caracteres (HU-18)';

COMMENT ON COLUMN "servicios"."descripcion_corta" IS '20–90 caracteres';

COMMENT ON COLUMN "servicios"."imagen_alt" IS '10–120 caracteres (accesibilidad)';

COMMENT ON COLUMN "servicios"."visible" IS 'Solo los visibles salen en el sitio y al agendar';

COMMENT ON TABLE "contenido_sitio" IS 'Textos editables de la página principal (HU-19). CHECK (char_length(valor) BETWEEN min_longitud AND max_longitud).';

COMMENT ON COLUMN "contenido_sitio"."clave" IS 'Ej.: portada-titulo-1, pie-mision';

COMMENT ON COLUMN "contenido_sitio"."seccion" IS 'Portada, Servicios, Proceso, Contacto, Pie de página';

COMMENT ON TABLE "horario_laboral" IS 'RN-01: 8:00–12:00 y 14:00–18:00 en franjas de 1 hora. En tabla para cambiarlo sin tocar código (RNF-06).';

COMMENT ON COLUMN "horario_laboral"."dia_semana" IS '1 = lunes … 5 = viernes';

COMMENT ON COLUMN "horario_laboral"."actualizado_por" IS 'Administrador que lo configuró';

COMMENT ON TABLE "dias_no_habiles" IS 'Festivos que el calendario muestra como no disponibles (RN-01).';

COMMENT ON COLUMN "dias_no_habiles"."motivo" IS 'Ej.: Festivo · Inmaculada Concepción';

COMMENT ON COLUMN "dias_no_habiles"."actualizado_por" IS 'Administrador que lo registró';

COMMENT ON TABLE "citas" IS '• RN-02: CREATE UNIQUE INDEX ux_cita_franja ON citas(fecha_hora) WHERE estado IN (''PENDIENTE'',''CONFIRMADA'');
• RN-03: el cliente puede tener varias citas pendientes; el índice anterior impide dos a la misma hora.
• RN-12: CHECK (estado <> ''CONFIRMADA'' OR modalidad = ''PRESENCIAL'' OR enlace_reunion IS NOT NULL).
';

COMMENT ON COLUMN "citas"."codigo" IS 'C-0061';

COMMENT ON COLUMN "citas"."equipo_id" IS 'Lo asigna el administrador (HU-09)';

COMMENT ON COLUMN "citas"."fecha_hora" IS 'Inicio de la franja de 1 hora (RN-01)';

COMMENT ON COLUMN "citas"."descripcion_necesidad" IS '20–500 caracteres (HU-01)';

COMMENT ON COLUMN "citas"."pais" IS 'País donde vive el cliente (RN-23)';

COMMENT ON COLUMN "citas"."zona_horaria" IS 'Zona IANA del cliente; fecha_hora se guarda como instante (RN-23)';

COMMENT ON COLUMN "citas"."enlace_reunion" IS 'Obligatorio si es VIRTUAL al confirmar (RN-12)';

COMMENT ON COLUMN "citas"."nota_cliente" IS 'Nota opcional del desarrollador (Product Owner) al confirmar';

COMMENT ON COLUMN "citas"."nota_asignacion" IS 'Nota del administrador para el desarrollador (Product Owner)';

COMMENT ON COLUMN "citas"."motivo_cancelacion" IS '15–300 caracteres cuando la cancela el administrador';

COMMENT ON TABLE "cuestionarios_cita" IS 'Lo llena el desarrollador (Product Owner) después de la cita (HU-10); lo ven el administrador y el equipo.';

COMMENT ON COLUMN "cuestionarios_cita"."cita_id" IS 'Uno por cita; al guardarlo la cita queda ATENDIDA (RN-24)';

COMMENT ON COLUMN "cuestionarios_cita"."resumen" IS '30–1000 caracteres';

COMMENT ON COLUMN "cuestionarios_cita"."descripcion_cliente" IS '30–600 caracteres';

COMMENT ON COLUMN "cuestionarios_cita"."fecha_deseada" IS 'Como la dijo el cliente; opcional';

COMMENT ON COLUMN "cuestionarios_cita"."registrado_por" IS 'Desarrollador (Product Owner) del equipo asignado';

COMMENT ON TABLE "plantilla_fases" IS 'Plantilla de fases de cada servicio (HU-27). De 2 a 8 fases y al menos una que apruebe
el cliente (RN-28). Al crear un proyecto se copia como su plan borrador (RN-27).
';

COMMENT ON COLUMN "plantilla_fases"."orden" IS '1 a 8 (RN-28)';

COMMENT ON COLUMN "plantilla_fases"."nombre" IS '3–60 caracteres, sin repetir en el servicio';

COMMENT ON COLUMN "plantilla_fases"."que_es" IS 'Qué significa la fase, en lenguaje sencillo';

COMMENT ON TABLE "plantilla_pasos" IS 'Pasos de cada fase de la plantilla (HU-27). Cada fase tiene al menos un paso (RN-28).';

COMMENT ON COLUMN "plantilla_pasos"."orden" IS '1 a 12';

COMMENT ON COLUMN "plantilla_pasos"."explicacion" IS 'Explicada para alguien que no programa';

COMMENT ON TABLE "proyectos" IS 'CHECK (avance_porcentaje BETWEEN 0 AND 100); lo calcula fn_recalcular_avance.
CHECK (fecha_estimada_entrega >= fecha_inicio + 14).
Para enviar el plan: valor_cotizado y condiciones_cotizacion no nulos (RN-29).
';

COMMENT ON COLUMN "proyectos"."codigo" IS 'P-0031';

COMMENT ON COLUMN "proyectos"."nombre" IS '5–60 caracteres (HU-02)';

COMMENT ON COLUMN "proyectos"."alcance" IS '20–600 caracteres';

COMMENT ON COLUMN "proyectos"."cita_id" IS '1:1 con la cita atendida (RN-04)';

COMMENT ON COLUMN "proyectos"."equipo_id" IS 'Un solo equipo responsable (RN-05)';

COMMENT ON COLUMN "proyectos"."fecha_estimada_entrega" IS 'Al menos 14 días después del inicio; se confirma en la cotización';

COMMENT ON COLUMN "proyectos"."estado_plan" IS 'RN-27, RN-29';

COMMENT ON COLUMN "proyectos"."valor_cotizado" IS 'Cotización en COP: 1.000.000 a 200.000.000 (RN-29)';

COMMENT ON COLUMN "proyectos"."condiciones_cotizacion" IS 'Qué incluye y qué no';

COMMENT ON COLUMN "proyectos"."plan_plazo_respuesta" IS 'plan_enviado_at + 3 días hábiles: se recuerda al cliente (RN-29)';

COMMENT ON COLUMN "proyectos"."plan_vence_el" IS 'plan_enviado_at + 10 días hábiles: la propuesta vence (RN-29)';

COMMENT ON COLUMN "proyectos"."avance_porcentaje" IS '100 × fases aprobadas / fases del plan (RN-08)';

COMMENT ON COLUMN "proyectos"."motivo_estado" IS 'Motivo al pausar, reanudar o cancelar (HU-17)';

COMMENT ON COLUMN "proyectos"."version" IS 'Bloqueo optimista';

COMMENT ON COLUMN "proyectos"."garantia_hasta" IS 'Cierre de la última fase + 30 días (RN-31)';

COMMENT ON COLUMN "proyectos"."finalizado_at" IS 'Fin de la garantía (RN-13)';

COMMENT ON TABLE "proyecto_fases" IS 'Plan de trabajo de cada proyecto: sus propias fases (copiadas de la plantilla del servicio).
Cada fase vale 100 % / número de fases (RN-28). Una fase aprobada no cambia (RN-30).
';

COMMENT ON COLUMN "proyecto_fases"."orden" IS 'Secuencia obligatoria (RN-08)';

COMMENT ON COLUMN "proyecto_fases"."nombre" IS 'Copiado de la plantilla; editable en el borrador';

COMMENT ON COLUMN "proyecto_fases"."aprueba_cliente" IS 'RN-17, RN-28';

COMMENT ON COLUMN "proyecto_fases"."cambio_plan_id" IS 'Cambio aceptado que la agregó (RN-30)';

COMMENT ON COLUMN "proyecto_fases"."entregada_at" IS 'Cuando el desarrollador (Product Owner) la entrega (HU-13)';

COMMENT ON COLUMN "proyecto_fases"."plazo_aprobacion" IS 'entregada_at + 3 días hábiles (RN-17)';

COMMENT ON COLUMN "proyecto_fases"."rondas_ajuste_usadas" IS '0–2 (RN-17)';

COMMENT ON COLUMN "proyecto_fases"."aprobada_por" IS 'Cliente, o el desarrollador (Product Owner) si la fase la cierra el equipo';

COMMENT ON COLUMN "proyecto_fases"."aprobacion_automatica" IS 'Venció el plazo sin respuesta (RN-17)';

COMMENT ON TABLE "pasos" IS 'Pasos, correcciones y ajustes de cada fase. Un paso HECHO no se edita ni se borra (RNF-02); un paso PENDIENTE se puede quitar mientras el plan está en BORRADOR o AJUSTES_PEDIDOS (RN-09, RN-27). Con el plan aprobado, un paso PENDIENTE solo se quita con un cambio QUITAR_PASO aceptado: pasa a QUITADO y no se borra (RN-30). Máximo 12 pasos de tipo PASO por fase sin contar los QUITADO; las correcciones y ajustes no cuentan (RN-28). Título de 3 a 80 caracteres, igual que en la plantilla.';

COMMENT ON COLUMN "pasos"."origen" IS 'Correcciones: Lo pidió el cliente · Al construir · En pruebas';

COMMENT ON COLUMN "pasos"."nota" IS 'Nota para el cliente al marcarlo hecho: 20–400 caracteres en un PASO (RN-09); 20–500 en una corrección o ajuste, con lo que se resolvió (HU-12)';

COMMENT ON COLUMN "pasos"."registrado_por" IS 'Desarrollador (Product Owner) que lo marcó (RN-07)';

COMMENT ON TABLE "adjuntos_paso" IS 'Bocetos y documentos que el cliente ve en el paso (HU-04).';

COMMENT ON COLUMN "adjuntos_paso"."titulo" IS 'Ej.: Página de producto';

COMMENT ON COLUMN "adjuntos_paso"."tipo_mime" IS 'image/svg+xml, image/png, application/pdf';

COMMENT ON COLUMN "adjuntos_paso"."tamano_bytes" IS 'Máximo 5 MB por archivo (HU-03)';

COMMENT ON COLUMN "adjuntos_paso"."archivo_eliminado_at" IS 'Conservación de datos: el archivo se borra 12 meses después de cancelar o finalizar el proyecto; el registro queda';

COMMENT ON TABLE "solicitudes_cambio" IS 'Ajustes (usan una de las 2 rondas) y cambios de alcance (se cotizan) pedidos por el cliente (RN-17).';

COMMENT ON COLUMN "solicitudes_cambio"."tipo" IS 'Solo AJUSTE o ALCANCE (HU-11)';

COMMENT ON COLUMN "solicitudes_cambio"."descripcion" IS '20–800 caracteres';

COMMENT ON COLUMN "solicitudes_cambio"."paso_id" IS 'Paso de corrección que generó (1:1)';

COMMENT ON COLUMN "solicitudes_cambio"."costo" IS 'Solo cambios de alcance';

COMMENT ON TABLE "respuestas_plan" IS 'Respuestas del cliente al plan enviado (HU-29, RN-29). Historial: no se edita.';

COMMENT ON COLUMN "respuestas_plan"."respuesta" IS 'APROBADO: aprobó plan y cotización · AJUSTES_PEDIDOS: el plan vuelve al equipo · RECHAZADO: no los quiere, el proyecto queda PAUSADO; si no responde en 30 días calendario desde esta respuesta, se avisa al administrador para cancelarlo (RN-29)';

COMMENT ON COLUMN "respuestas_plan"."comentario" IS '20–600 caracteres si pide ajustes o rechaza';

COMMENT ON TABLE "cambios_plan" IS 'Cambio de un plan aprobado (HU-30, RN-30): agregar una fase, quitar una pendiente, cambiar
solo la fecha de entrega o quitar un paso pendiente. Lo propone el PO, lo autoriza el administrador
y lo acepta el cliente. Excepción: QUITAR_PASO en una fase que no aprueba el cliente se aplica al
autorizarlo (pasa de PROPUESTO a ACEPTADO) y solo se avisa al cliente. Un solo cambio abierto por proyecto.
';

COMMENT ON COLUMN "cambios_plan"."nombre_fase" IS 'Fase nueva (AGREGAR_FASE)';

COMMENT ON COLUMN "cambios_plan"."posicion" IS 'Orden que tendrá la fase nueva';

COMMENT ON COLUMN "cambios_plan"."pasos_propuestos" IS 'Títulos de los pasos de la fase nueva';

COMMENT ON COLUMN "cambios_plan"."proyecto_fase_id" IS 'Fase pendiente que se quita (QUITAR_FASE)';

COMMENT ON COLUMN "cambios_plan"."motivo" IS '20–500 caracteres';

COMMENT ON COLUMN "cambios_plan"."paso_id" IS 'Paso pendiente de tipo PASO que se quita (QUITAR_PASO); queda en estado QUITADO, no se borra';

COMMENT ON COLUMN "cambios_plan"."nueva_fecha_entrega" IS 'Obligatoria salvo en QUITAR_PASO (no cambia la entrega). En CAMBIAR_FECHA debe ser distinta de la actual';

COMMENT ON COLUMN "cambios_plan"."valor_adicional" IS 'COP';

COMMENT ON COLUMN "cambios_plan"."propuesto_por" IS 'Desarrollador (Product Owner)';

COMMENT ON COLUMN "cambios_plan"."autorizado_por" IS 'Administrador';

COMMENT ON COLUMN "cambios_plan"."respondido_at" IS 'Respuesta del cliente';

COMMENT ON COLUMN "mensajes"."autor_id" IS 'Cliente dueño o desarrollador (Product Owner) del equipo (RN-14)';

COMMENT ON COLUMN "mensajes"."tema" IS 'Obligatorio cuando escribe el cliente';

COMMENT ON COLUMN "mensajes"."contenido" IS '10–1000 caracteres (HU-14)';

COMMENT ON COLUMN "mensajes"."editado_at" IS 'El autor puede editar tema y contenido durante 5 minutos después de enviarlo (HU-14); el texto anterior queda en auditoría';

COMMENT ON TABLE "solicitudes_reunion" IS 'RN-15: CREATE UNIQUE INDEX ux_solicitud_abierta ON solicitudes_reunion(proyecto_id) WHERE estado = ''PENDIENTE'';';

COMMENT ON COLUMN "solicitudes_reunion"."codigo" IS 'S-0004';

COMMENT ON COLUMN "solicitudes_reunion"."proyecto_fase_id" IS 'Fase del plan sobre la que se pide';

COMMENT ON COLUMN "solicitudes_reunion"."motivo" IS 'Revisar el avance, Cambio en los requerimientos…';

COMMENT ON COLUMN "solicitudes_reunion"."razon" IS '20–500 caracteres';

COMMENT ON COLUMN "solicitudes_reunion"."motivo_rechazo" IS '15–300 caracteres';

COMMENT ON COLUMN "notificaciones"."tipo" IS 'CITA_CONFIRMADA, PASO_HECHO, FASE_ENTREGADA, NUEVO_MENSAJE…';

COMMENT ON COLUMN "notificaciones"."intentos_envio" IS 'Intentos de envío del correo: máximo 3 (HU-24, RNF-13)';

COMMENT ON COLUMN "notificaciones"."ultimo_error" IS 'Error del último intento fallido del correo';

COMMENT ON TABLE "auditoria" IS 'Registro inmutable (RN-20, RNF-02): el disparador tg_auditoria_inmutable rechaza UPDATE y DELETE.';

COMMENT ON COLUMN "auditoria"."usuario_id" IS 'Quién lo hizo';

COMMENT ON COLUMN "auditoria"."categoria" IS 'citas, proyectos, avances, usuarios, sitio, seguridad';

COMMENT ON COLUMN "auditoria"."accion" IS 'Ej.: Marcó un paso como hecho';

COMMENT ON COLUMN "auditoria"."entidad_id" IS 'Código o id del registro afectado';

COMMENT ON COLUMN "auditoria"."cambios" IS 'Campo, valor anterior y valor nuevo';

COMMENT ON COLUMN "auditoria"."agente" IS 'Navegador y sistema';

ALTER TABLE "rol_permisos" ADD FOREIGN KEY ("rol_id") REFERENCES "roles" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "rol_permisos" ADD FOREIGN KEY ("permiso_id") REFERENCES "permisos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "usuarios" ADD FOREIGN KEY ("rol_id") REFERENCES "roles" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "tokens_recuperacion" ADD FOREIGN KEY ("usuario_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "desarrolladores" ADD FOREIGN KEY ("usuario_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "integrantes_equipo" ADD FOREIGN KEY ("equipo_id") REFERENCES "equipos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "integrantes_equipo" ADD FOREIGN KEY ("desarrollador_id") REFERENCES "desarrolladores" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "contenido_sitio" ADD FOREIGN KEY ("actualizado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "horario_laboral" ADD FOREIGN KEY ("actualizado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "dias_no_habiles" ADD FOREIGN KEY ("actualizado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "citas" ADD FOREIGN KEY ("cliente_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "citas" ADD FOREIGN KEY ("servicio_id") REFERENCES "servicios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "citas" ADD FOREIGN KEY ("equipo_id") REFERENCES "equipos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "citas" ADD FOREIGN KEY ("cancelada_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cuestionarios_cita" ADD FOREIGN KEY ("cita_id") REFERENCES "citas" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cuestionarios_cita" ADD FOREIGN KEY ("registrado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "plantilla_fases" ADD FOREIGN KEY ("servicio_id") REFERENCES "servicios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "plantilla_fases" ADD FOREIGN KEY ("actualizado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "plantilla_pasos" ADD FOREIGN KEY ("plantilla_fase_id") REFERENCES "plantilla_fases" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyectos" ADD FOREIGN KEY ("cita_id") REFERENCES "citas" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyectos" ADD FOREIGN KEY ("cliente_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyectos" ADD FOREIGN KEY ("servicio_id") REFERENCES "servicios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyectos" ADD FOREIGN KEY ("equipo_id") REFERENCES "equipos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyectos" ADD FOREIGN KEY ("creado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyecto_fases" ADD FOREIGN KEY ("proyecto_id") REFERENCES "proyectos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyecto_fases" ADD FOREIGN KEY ("cambio_plan_id") REFERENCES "cambios_plan" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "proyecto_fases" ADD FOREIGN KEY ("aprobada_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "pasos" ADD FOREIGN KEY ("proyecto_fase_id") REFERENCES "proyecto_fases" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "pasos" ADD FOREIGN KEY ("registrado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "adjuntos_paso" ADD FOREIGN KEY ("paso_id") REFERENCES "pasos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "respuestas_plan" ADD FOREIGN KEY ("proyecto_id") REFERENCES "proyectos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "respuestas_plan" ADD FOREIGN KEY ("cliente_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cambios_plan" ADD FOREIGN KEY ("proyecto_id") REFERENCES "proyectos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cambios_plan" ADD FOREIGN KEY ("proyecto_fase_id") REFERENCES "proyecto_fases" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cambios_plan" ADD FOREIGN KEY ("paso_id") REFERENCES "pasos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cambios_plan" ADD FOREIGN KEY ("propuesto_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "cambios_plan" ADD FOREIGN KEY ("autorizado_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_cambio" ADD FOREIGN KEY ("proyecto_fase_id") REFERENCES "proyecto_fases" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_cambio" ADD FOREIGN KEY ("cliente_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_cambio" ADD FOREIGN KEY ("paso_id") REFERENCES "pasos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "mensajes" ADD FOREIGN KEY ("proyecto_id") REFERENCES "proyectos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "mensajes" ADD FOREIGN KEY ("autor_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_reunion" ADD FOREIGN KEY ("proyecto_id") REFERENCES "proyectos" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_reunion" ADD FOREIGN KEY ("cliente_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_reunion" ADD FOREIGN KEY ("proyecto_fase_id") REFERENCES "proyecto_fases" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "solicitudes_reunion" ADD FOREIGN KEY ("gestionada_por") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "notificaciones" ADD FOREIGN KEY ("usuario_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "auditoria" ADD FOREIGN KEY ("usuario_id") REFERENCES "usuarios" ("id") DEFERRABLE INITIALLY IMMEDIATE;

-- =====================================================================
-- RESTRICCIONES DE NEGOCIO Y VALIDACIONES DE LOS FORMULARIOS (HU)
-- =====================================================================

-- Usuarios (HU-05, HU-06, HU-20, RN-18, RN-22)
ALTER TABLE usuarios
  ADD CONSTRAINT ck_usuarios_nombre   CHECK (char_length(trim(nombre)) BETWEEN 3 AND 60),
  ADD CONSTRAINT ck_usuarios_email    CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  ADD CONSTRAINT ck_usuarios_celular  CHECK (celular ~ '^3[0-9]{9}$'),
  ADD CONSTRAINT ck_usuarios_intentos CHECK (intentos_fallidos BETWEEN 0 AND 5),
  -- Derecho de supresión (Ley 1581): una cuenta anonimizada queda desactivada
  ADD CONSTRAINT ck_usuarios_anonimo  CHECK (anonimizado_at IS NULL OR NOT activo);
CREATE UNIQUE INDEX ux_usuarios_email_ci ON usuarios (lower(email));

-- Desarrolladores y equipos (HU-25, HU-26, RN-05, RN-25, RN-26)
ALTER TABLE desarrolladores
  ADD CONSTRAINT ck_dev_nombre  CHECK (char_length(trim(nombre)) BETWEEN 5 AND 60),
  ADD CONSTRAINT ck_dev_correo  CHECK (correo ~* '^[^@\s]+@coretechsolutions\.co$'),
  ADD CONSTRAINT ck_dev_celular CHECK (celular ~ '^3[0-9]{9}$');
ALTER TABLE equipos
  ADD CONSTRAINT ck_equipos_cupo CHECK (max_proyectos BETWEEN 1 AND 3);
-- RN-25: un solo Product Owner por equipo
CREATE UNIQUE INDEX ux_un_po_por_equipo ON integrantes_equipo (equipo_id) WHERE rol = 'PRODUCT_OWNER';
-- RN-26: el rol depende de la especialidad; el Product Owner debe tener cuenta y estar activo
CREATE OR REPLACE FUNCTION fn_validar_integrante() RETURNS trigger AS $$
DECLARE d desarrolladores%ROWTYPE;
BEGIN
  SELECT * INTO d FROM desarrolladores WHERE id = NEW.desarrollador_id;
  IF NOT d.activo THEN
    RAISE EXCEPTION 'El desarrollador % está inactivo', d.nombre;
  END IF;
  IF NEW.rol = 'PRODUCT_OWNER' THEN
    IF d.usuario_id IS NULL THEN
      RAISE EXCEPTION 'El Product Owner % necesita una cuenta en la plataforma', d.nombre;
    END IF;
  ELSIF NOT (NEW.rol::text = d.especialidad::text
             OR (d.especialidad = 'FULL_STACK' AND NEW.rol IN ('FRONTEND','BACKEND'))) THEN
    RAISE EXCEPTION 'Con la especialidad % no puede tener el rol %', d.especialidad, NEW.rol;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_validar_integrante BEFORE INSERT OR UPDATE ON integrantes_equipo
  FOR EACH ROW EXECUTE FUNCTION fn_validar_integrante();

-- Servicios (HU-18)
ALTER TABLE servicios
  ADD CONSTRAINT ck_servicios_nombre CHECK (char_length(nombre) BETWEEN 4 AND 40),
  ADD CONSTRAINT ck_servicios_desc   CHECK (char_length(descripcion_corta) BETWEEN 20 AND 90),
  ADD CONSTRAINT ck_servicios_alt    CHECK (char_length(imagen_alt) BETWEEN 10 AND 120),
  ADD CONSTRAINT ck_servicios_orden  CHECK (orden > 0);

-- Contenido del sitio (HU-19)
ALTER TABLE contenido_sitio
  ADD CONSTRAINT ck_contenido_rango CHECK (min_longitud > 0 AND max_longitud >= min_longitud),
  ADD CONSTRAINT ck_contenido_valor CHECK (char_length(valor) BETWEEN min_longitud AND max_longitud);

-- Horario laboral (RN-01)
ALTER TABLE horario_laboral
  ADD CONSTRAINT ck_horario_dia   CHECK (dia_semana BETWEEN 1 AND 5),
  ADD CONSTRAINT ck_horario_rango CHECK (hora_fin > hora_inicio);

-- Citas (HU-01, HU-08, HU-09, HU-10, RN-01, RN-02, RN-11, RN-12)
ALTER TABLE citas
  ADD CONSTRAINT ck_citas_descripcion CHECK (char_length(trim(descripcion_necesidad)) BETWEEN 20 AND 500),
  ADD CONSTRAINT ck_citas_franja      CHECK (date_part('minute', fecha_hora AT TIME ZONE 'America/Bogota') = 0),
  ADD CONSTRAINT ck_citas_enlace      CHECK (estado <> 'CONFIRMADA' OR modalidad = 'PRESENCIAL' OR enlace_reunion IS NOT NULL),
  ADD CONSTRAINT ck_citas_enlace_url  CHECK (enlace_reunion IS NULL OR enlace_reunion ~ '^https?://.+\..+'),
  ADD CONSTRAINT ck_citas_cancelada   CHECK (estado <> 'CANCELADA' OR cancelada_at IS NOT NULL),
  ADD CONSTRAINT ck_citas_motivo      CHECK (motivo_cancelacion IS NULL OR char_length(motivo_cancelacion) BETWEEN 15 AND 300);
-- RN-02: una sola cita activa por franja
CREATE UNIQUE INDEX ux_cita_franja ON citas (fecha_hora) WHERE estado IN ('PENDIENTE','CONFIRMADA');
-- RN-03: el cliente puede tener varias pendientes, pero no dos a la misma hora
CREATE UNIQUE INDEX ux_cita_cliente_hora ON citas (cliente_id, fecha_hora) WHERE estado <> 'CANCELADA';

-- País y zona horaria del cliente (RN-23)
ALTER TABLE citas
  ADD CONSTRAINT ck_citas_pais CHECK (char_length(trim(pais)) BETWEEN 2 AND 60),
  ADD CONSTRAINT ck_citas_zona CHECK (zona_horaria ~ '^[A-Za-z_]+(/[A-Za-z_]+){1,2}$');

-- Cuestionario de la cita (HU-10, RN-24)
ALTER TABLE cuestionarios_cita
  ADD CONSTRAINT ck_cuest_resumen CHECK (char_length(trim(resumen)) BETWEEN 30 AND 1000),
  ADD CONSTRAINT ck_cuest_cliente CHECK (char_length(trim(descripcion_cliente)) BETWEEN 30 AND 600);
ALTER TABLE citas
  ADD CONSTRAINT ck_citas_atendida CHECK (estado <> 'ATENDIDA' OR atendida_at IS NOT NULL);

-- Plantillas de fases por servicio (HU-27, RN-27, RN-28)
ALTER TABLE plantilla_fases
  ADD CONSTRAINT ck_plf_orden  CHECK (orden BETWEEN 1 AND 8),
  ADD CONSTRAINT ck_plf_nombre CHECK (char_length(trim(nombre)) BETWEEN 3 AND 60);
CREATE UNIQUE INDEX ux_plantilla_fase_nombre ON plantilla_fases (servicio_id, lower(trim(nombre)));
ALTER TABLE plantilla_pasos
  ADD CONSTRAINT ck_plp_orden  CHECK (orden BETWEEN 1 AND 12),
  ADD CONSTRAINT ck_plp_titulo CHECK (char_length(trim(titulo)) BETWEEN 3 AND 80);

-- RN-28: de 2 a 8 fases, al menos una que apruebe el cliente y al menos un paso por fase.
-- Se revisa al confirmar la transacción, para poder editar la plantilla en varios pasos.
CREATE OR REPLACE FUNCTION fn_revisar_fases(n int, aprueba int, sin_pasos int, que text) RETURNS void AS $$
BEGIN
  IF n NOT BETWEEN 2 AND 8 THEN RAISE EXCEPTION '% debe tener de 2 a 8 fases (tiene %) (RN-28)', que, n; END IF;
  IF aprueba = 0 THEN RAISE EXCEPTION '% necesita al menos una fase que apruebe el cliente (RN-28)', que; END IF;
  IF sin_pasos > 0 THEN RAISE EXCEPTION 'Cada fase de % necesita al menos un paso (RN-28)', que; END IF;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_validar_plantilla() RETURNS trigger AS $$
DECLARE s uuid; n int; a int; sp int;
BEGIN
  IF TG_TABLE_NAME = 'plantilla_pasos' THEN
    SELECT servicio_id INTO s FROM plantilla_fases
     WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.plantilla_fase_id ELSE NEW.plantilla_fase_id END;
  ELSE
    s := CASE WHEN TG_OP = 'DELETE' THEN OLD.servicio_id ELSE NEW.servicio_id END;
  END IF;
  SELECT count(*), count(*) FILTER (WHERE aprueba_cliente) INTO n, a FROM plantilla_fases WHERE servicio_id = s;
  IF s IS NULL OR n = 0 THEN RETURN NULL; END IF;
  SELECT count(*) INTO sp FROM plantilla_fases f
   WHERE f.servicio_id = s AND NOT EXISTS (SELECT 1 FROM plantilla_pasos p WHERE p.plantilla_fase_id = f.id);
  PERFORM fn_revisar_fases(n, a, sp, 'La plantilla');
  RETURN NULL;
END $$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER tg_validar_plantilla_fases AFTER INSERT OR UPDATE OR DELETE ON plantilla_fases
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fn_validar_plantilla();
CREATE CONSTRAINT TRIGGER tg_validar_plantilla_pasos AFTER INSERT OR UPDATE OR DELETE ON plantilla_pasos
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fn_validar_plantilla();

-- Proyectos (HU-02, HU-17, RN-04, RN-08, RN-13)
ALTER TABLE proyectos
  ADD CONSTRAINT ck_proyectos_nombre  CHECK (char_length(trim(nombre)) BETWEEN 5 AND 60),
  ADD CONSTRAINT ck_proyectos_alcance CHECK (char_length(trim(alcance)) BETWEEN 20 AND 600),
  ADD CONSTRAINT ck_proyectos_fechas  CHECK (fecha_estimada_entrega >= fecha_inicio + 14),
  ADD CONSTRAINT ck_proyectos_avance  CHECK (avance_porcentaje BETWEEN 0 AND 100),
  ADD CONSTRAINT ck_proyectos_motivo  CHECK (motivo_estado IS NULL OR char_length(motivo_estado) BETWEEN 10 AND 300),
  -- Cotización (RN-29): valor en COP y condiciones; obligatorias desde que se envía el plan
  ADD CONSTRAINT ck_proyectos_valor   CHECK (valor_cotizado IS NULL OR valor_cotizado BETWEEN 1000000 AND 200000000),
  ADD CONSTRAINT ck_proyectos_condic  CHECK (condiciones_cotizacion IS NULL OR char_length(trim(condiciones_cotizacion)) BETWEEN 20 AND 600),
  ADD CONSTRAINT ck_proyectos_enviado CHECK (estado_plan = 'BORRADOR' OR (valor_cotizado IS NOT NULL AND condiciones_cotizacion IS NOT NULL
                                             AND plan_enviado_at IS NOT NULL AND plan_plazo_respuesta IS NOT NULL AND plan_vence_el IS NOT NULL)),
  -- RN-29: se recuerda a los 3 días hábiles y la propuesta vence a los 10
  ADD CONSTRAINT ck_proyectos_vence   CHECK (plan_vence_el IS NULL OR plan_vence_el > plan_plazo_respuesta),
  -- Una propuesta vencida o rechazada deja el proyecto en pausa hasta que el administrador lo reanuda o lo cancela
  ADD CONSTRAINT ck_proyectos_vencido CHECK (estado_plan NOT IN ('VENCIDO','RECHAZADO') OR estado IN ('PAUSADO','CANCELADO')),
  ADD CONSTRAINT ck_proyectos_aprobado CHECK (estado_plan <> 'APROBADO' OR plan_aprobado_at IS NOT NULL),
  -- Garantía de 30 días y cierre (RN-13, RN-31)
  ADD CONSTRAINT ck_proyectos_garantia CHECK (estado NOT IN ('EN_GARANTIA','FINALIZADO') OR (avance_porcentaje = 100 AND garantia_hasta IS NOT NULL)),
  ADD CONSTRAINT ck_proyectos_final   CHECK (estado <> 'FINALIZADO' OR (finalizado_at IS NOT NULL AND finalizado_at::date >= garantia_hasta));

-- RN-29: solo se envía un plan que cumpla RN-28; el cliente solo responde un plan enviado
-- (aprueba, pide ajustes o rechaza); una propuesta rechazada o sin respuesta (10 días hábiles)
-- deja el proyecto en Pausado (libera el cupo del equipo). Para retomarlo, el administrador lo
-- reanuda (eligiendo equipo) y el plan vuelve a borrador en la misma operación.
CREATE OR REPLACE FUNCTION fn_validar_estado_plan() RETURNS trigger AS $$
DECLARE n int; a int; sp int;
BEGIN
  IF NEW.estado_plan = OLD.estado_plan THEN RETURN NEW; END IF;
  IF NEW.estado_plan = 'ENVIADO' THEN
    IF OLD.estado_plan NOT IN ('BORRADOR','AJUSTES_PEDIDOS') THEN RAISE EXCEPTION 'Solo se envía un plan en borrador o con ajustes pedidos (RN-29)'; END IF;
    IF NEW.estado <> 'ACTIVO' THEN RAISE EXCEPTION 'Solo se envía el plan de un proyecto activo (RN-29)'; END IF;
    SELECT count(*), count(*) FILTER (WHERE aprueba_cliente),
           count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM pasos p WHERE p.proyecto_fase_id = f.id))
      INTO n, a, sp FROM proyecto_fases f WHERE f.proyecto_id = NEW.id AND f.estado <> 'QUITADA';
    PERFORM fn_revisar_fases(n, a, sp, 'El plan');
  ELSIF NEW.estado_plan IN ('APROBADO','AJUSTES_PEDIDOS','RECHAZADO','VENCIDO') AND OLD.estado_plan <> 'ENVIADO' THEN
    RAISE EXCEPTION 'Solo un plan enviado se aprueba, se ajusta, se rechaza o vence (RN-29)';
  ELSIF NEW.estado_plan IN ('RECHAZADO','VENCIDO') THEN
    NEW.estado := 'PAUSADO';
    NEW.motivo_estado := coalesce(NEW.motivo_estado, CASE NEW.estado_plan
      WHEN 'RECHAZADO' THEN 'El cliente rechazó el plan y la cotización (RN-29)'
      ELSE 'La propuesta venció sin respuesta del cliente (RN-29)' END);
  ELSIF NEW.estado_plan = 'BORRADOR' THEN
    IF OLD.estado_plan NOT IN ('RECHAZADO','VENCIDO') OR NEW.estado <> 'ACTIVO' THEN
      RAISE EXCEPTION 'Un plan enviado no vuelve a borrador: el cliente pide ajustes. Solo una propuesta rechazada o vencida vuelve a borrador, al reanudar el proyecto (RN-29)';
    END IF;
    NEW.plan_enviado_at := NULL;
    NEW.plan_plazo_respuesta := NULL;
    NEW.plan_vence_el := NULL;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_validar_estado_plan BEFORE UPDATE OF estado_plan ON proyectos
  FOR EACH ROW EXECUTE FUNCTION fn_validar_estado_plan();

-- Fases del plan de cada proyecto (RN-08, RN-17, RN-28, RN-30)
ALTER TABLE proyecto_fases
  ADD CONSTRAINT ck_pf_orden    CHECK (orden BETWEEN 1 AND 8),
  ADD CONSTRAINT ck_pf_nombre   CHECK (char_length(trim(nombre)) BETWEEN 3 AND 60),
  ADD CONSTRAINT ck_pf_rondas   CHECK (rondas_ajuste_usadas BETWEEN 0 AND 2),
  ADD CONSTRAINT ck_pf_aprobada CHECK (estado <> 'APROBADA' OR aprobada_at IS NOT NULL);
CREATE UNIQUE INDEX ux_pf_orden  ON proyecto_fases (proyecto_id, orden) WHERE estado <> 'QUITADA';
CREATE UNIQUE INDEX ux_pf_nombre ON proyecto_fases (proyecto_id, lower(trim(nombre))) WHERE estado <> 'QUITADA';

-- RN-30: con el plan aprobado, las fases solo cambian con un cambio aceptado por el cliente,
-- y una fase aprobada no cambia nunca.
CREATE OR REPLACE FUNCTION fn_proteger_fases() RETURNS trigger AS $$
DECLARE plan estado_plan;
BEGIN
  SELECT estado_plan INTO plan FROM proyectos
   WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.proyecto_id ELSE NEW.proyecto_id END;
  IF TG_OP = 'DELETE' THEN
    IF plan = 'APROBADO' THEN RAISE EXCEPTION 'Con el plan aprobado una fase no se borra: se marca QUITADA con un cambio aceptado (RN-30)'; END IF;
    RETURN OLD;
  END IF;
  IF TG_OP = 'UPDATE' AND OLD.estado IN ('APROBADA','EN_CORRECCION')
     AND (NEW.nombre, NEW.orden, NEW.aprueba_cliente) IS DISTINCT FROM (OLD.nombre, OLD.orden, OLD.aprueba_cliente) THEN
    RAISE EXCEPTION 'La fase "%" ya fue aprobada: no cambia (RN-30)', OLD.nombre;
  END IF;
  IF plan = 'APROBADO' THEN
    IF TG_OP = 'INSERT' AND NOT EXISTS (SELECT 1 FROM cambios_plan c WHERE c.id = NEW.cambio_plan_id
                                          AND c.proyecto_id = NEW.proyecto_id AND c.tipo = 'AGREGAR_FASE' AND c.estado = 'ACEPTADO') THEN
      RAISE EXCEPTION 'Agregar una fase a un plan aprobado requiere un cambio aceptado por el cliente (RN-30)';
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.nombre, NEW.aprueba_cliente) IS DISTINCT FROM (OLD.nombre, OLD.aprueba_cliente) THEN
      RAISE EXCEPTION 'Con el plan aprobado no se cambia el nombre ni quién aprueba una fase (RN-30)';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.estado = 'QUITADA' AND OLD.estado <> 'QUITADA' AND (OLD.estado <> 'PENDIENTE' OR NOT EXISTS (
         SELECT 1 FROM cambios_plan c WHERE c.proyecto_fase_id = OLD.id AND c.tipo = 'QUITAR_FASE' AND c.estado = 'ACEPTADO')) THEN
      RAISE EXCEPTION 'Solo se quita una fase pendiente con un cambio aceptado por el cliente (RN-30)';
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_proteger_fases BEFORE INSERT OR UPDATE OR DELETE ON proyecto_fases
  FOR EACH ROW EXECUTE FUNCTION fn_proteger_fases();

-- RN-28 también para el plan ya enviado o aprobado (se revisa al confirmar la transacción)
CREATE OR REPLACE FUNCTION fn_validar_plan_fases() RETURNS trigger AS $$
DECLARE p uuid; plan estado_plan; n int; a int;
BEGIN
  p := CASE WHEN TG_OP = 'DELETE' THEN OLD.proyecto_id ELSE NEW.proyecto_id END;
  SELECT estado_plan INTO plan FROM proyectos WHERE id = p;
  IF plan IS NULL OR plan IN ('BORRADOR','AJUSTES_PEDIDOS') THEN RETURN NULL; END IF;
  SELECT count(*), count(*) FILTER (WHERE aprueba_cliente) INTO n, a
    FROM proyecto_fases WHERE proyecto_id = p AND estado <> 'QUITADA';
  PERFORM fn_revisar_fases(n, a, 0, 'El plan');
  RETURN NULL;
END $$ LANGUAGE plpgsql;
CREATE CONSTRAINT TRIGGER tg_validar_plan_fases AFTER INSERT OR UPDATE OR DELETE ON proyecto_fases
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fn_validar_plan_fases();

-- RN-08: avance = 100 × fases aprobadas (sin corrección abierta) / fases del plan
CREATE OR REPLACE FUNCTION fn_recalcular_avance() RETURNS trigger AS $$
DECLARE p uuid;
BEGIN
  p := CASE WHEN TG_OP = 'DELETE' THEN OLD.proyecto_id ELSE NEW.proyecto_id END;
  UPDATE proyectos SET avance_porcentaje = COALESCE((
      SELECT round(100.0 * count(*) FILTER (WHERE estado = 'APROBADA') / NULLIF(count(*), 0), 2)
        FROM proyecto_fases WHERE proyecto_id = p AND estado <> 'QUITADA'), 0)
   WHERE id = p;
  RETURN NULL;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_recalcular_avance AFTER INSERT OR DELETE OR UPDATE OF estado ON proyecto_fases
  FOR EACH ROW EXECUTE FUNCTION fn_recalcular_avance();

-- Respuesta del cliente al plan (HU-29, RN-29): historial que no se edita
ALTER TABLE respuestas_plan
  -- coalesce: sin él, un comentario NULL dejaría pasar el CHECK
  ADD CONSTRAINT ck_rp_comentario CHECK (respuesta = 'APROBADO' OR coalesce(char_length(trim(comentario)) BETWEEN 20 AND 600, false));

-- Cambios de un plan aprobado: agregar o quitar una fase, cambiar la fecha o quitar un paso (HU-30, RN-30)
ALTER TABLE cambios_plan
  -- QUITAR_PASO: solo el paso, sin fecha nueva ni valor adicional; los demás tipos no llevan paso y sí fecha
  ADD CONSTRAINT ck_cp_quitar_paso CHECK (tipo <> 'QUITAR_PASO' OR (paso_id IS NOT NULL AND proyecto_fase_id IS NULL AND nombre_fase IS NULL
                                          AND pasos_propuestos IS NULL AND nueva_fecha_entrega IS NULL AND valor_adicional = 0)),
  ADD CONSTRAINT ck_cp_fase_fecha  CHECK (tipo = 'QUITAR_PASO' OR (paso_id IS NULL AND nueva_fecha_entrega IS NOT NULL)),
  -- coalesce: sin él, un nombre, posición o pasos NULL dejarían pasar el CHECK
  ADD CONSTRAINT ck_cp_agregar CHECK (tipo <> 'AGREGAR_FASE' OR coalesce(char_length(trim(nombre_fase)) BETWEEN 3 AND 60 AND posicion BETWEEN 1 AND 8
                                      AND jsonb_typeof(pasos_propuestos) = 'array' AND jsonb_array_length(pasos_propuestos) BETWEEN 1 AND 12
                                      AND proyecto_fase_id IS NULL, false)),
  ADD CONSTRAINT ck_cp_quitar  CHECK (tipo <> 'QUITAR_FASE' OR (proyecto_fase_id IS NOT NULL AND nombre_fase IS NULL)),
  ADD CONSTRAINT ck_cp_fecha   CHECK (tipo <> 'CAMBIAR_FECHA' OR (proyecto_fase_id IS NULL AND nombre_fase IS NULL AND pasos_propuestos IS NULL)),
  ADD CONSTRAINT ck_cp_motivo  CHECK (char_length(trim(motivo)) BETWEEN 20 AND 500),
  ADD CONSTRAINT ck_cp_valor   CHECK (valor_adicional BETWEEN 0 AND 200000000),
  ADD CONSTRAINT ck_cp_autoriz CHECK (estado IN ('PROPUESTO','RECHAZADO_ADMIN') OR (autorizado_por IS NOT NULL AND autorizado_at IS NOT NULL)),
  ADD CONSTRAINT ck_cp_rechazo CHECK (estado NOT IN ('RECHAZADO_ADMIN','RECHAZADO_CLIENTE') OR coalesce(char_length(trim(motivo_rechazo)) BETWEEN 10 AND 400, false));
CREATE UNIQUE INDEX ux_cambio_plan_abierto ON cambios_plan (proyecto_id) WHERE estado IN ('PROPUESTO','AUTORIZADO');

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
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_validar_cambio_plan BEFORE INSERT ON cambios_plan
  FOR EACH ROW EXECUTE FUNCTION fn_validar_cambio_plan();

-- RN-30: recorrido del cambio. PROPUESTO → AUTORIZADO (administrador) → ACEPTADO (cliente), o rechazado
-- en cualquiera de los dos. Excepción: QUITAR_PASO en una fase que no aprueba el cliente se aplica al
-- autorizarlo (PROPUESTO → ACEPTADO) y solo se avisa al cliente. Un cambio cerrado ya no cambia.
CREATE OR REPLACE FUNCTION fn_validar_estado_cambio_plan() RETURNS trigger AS $$
DECLARE cliente_aprueba boolean;
BEGIN
  IF NEW.estado = OLD.estado THEN RETURN NEW; END IF;
  cliente_aprueba := NEW.tipo <> 'QUITAR_PASO' OR EXISTS (
    SELECT 1 FROM pasos p JOIN proyecto_fases f ON f.id = p.proyecto_fase_id WHERE p.id = NEW.paso_id AND f.aprueba_cliente);
  IF OLD.estado NOT IN ('PROPUESTO','AUTORIZADO') THEN
    RAISE EXCEPTION 'Este cambio de plan ya se cerró (RN-30)';
  ELSIF OLD.estado = 'PROPUESTO' AND NOT (NEW.estado = 'RECHAZADO_ADMIN'
        OR (NEW.estado = 'AUTORIZADO' AND cliente_aprueba) OR (NEW.estado = 'ACEPTADO' AND NOT cliente_aprueba)) THEN
    RAISE EXCEPTION 'Un cambio propuesto lo autoriza o lo rechaza el administrador; si la fase la aprueba el cliente, después lo acepta el cliente (RN-30)';
  ELSIF OLD.estado = 'AUTORIZADO' AND NEW.estado NOT IN ('ACEPTADO','RECHAZADO_CLIENTE') THEN
    RAISE EXCEPTION 'Un cambio autorizado solo lo acepta o lo rechaza el cliente (RN-30)';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_validar_estado_cambio_plan BEFORE UPDATE OF estado ON cambios_plan
  FOR EACH ROW EXECUTE FUNCTION fn_validar_estado_cambio_plan();

-- Pasos y correcciones (HU-03, HU-12, RN-09, RN-16)
-- Nota: 20–400 caracteres en un paso; 20–500 en una corrección o ajuste (lo que se resolvió, HU-12)
ALTER TABLE pasos
  ADD CONSTRAINT ck_pasos_titulo  CHECK (char_length(trim(titulo)) BETWEEN 3 AND 80),
  ADD CONSTRAINT ck_pasos_nota    CHECK (nota IS NULL
                                         OR (tipo = 'PASO'  AND char_length(trim(nota)) BETWEEN 20 AND 400)
                                         OR (tipo <> 'PASO' AND char_length(trim(nota)) BETWEEN 20 AND 500)),
  ADD CONSTRAINT ck_pasos_hecho   CHECK (estado <> 'HECHO' OR (nota IS NOT NULL AND hecho_at IS NOT NULL AND registrado_por IS NOT NULL)),
  ADD CONSTRAINT ck_pasos_origen  CHECK (tipo = 'PASO' OR origen IS NOT NULL);
ALTER TABLE adjuntos_paso
  ADD CONSTRAINT ck_adjuntos_mime   CHECK (tipo_mime IN ('image/svg+xml','image/png','image/jpeg','application/pdf')),
  ADD CONSTRAINT ck_adjuntos_tamano CHECK (tamano_bytes BETWEEN 1 AND 5242880);

-- Solicitudes de cambio (HU-11, RN-17)
ALTER TABLE solicitudes_cambio
  ADD CONSTRAINT ck_cambio_tipo  CHECK (tipo IN ('AJUSTE','ALCANCE')),
  ADD CONSTRAINT ck_cambio_desc  CHECK (char_length(trim(descripcion)) BETWEEN 20 AND 800),
  ADD CONSTRAINT ck_cambio_costo CHECK (costo IS NULL OR (tipo = 'ALCANCE' AND costo >= 0)),
  ADD CONSTRAINT ck_cambio_dias  CHECK (dias_adicionales IS NULL OR dias_adicionales >= 0);

-- Mensajes (HU-14)
ALTER TABLE mensajes
  ADD CONSTRAINT ck_mensajes_contenido CHECK (char_length(trim(contenido)) BETWEEN 10 AND 1000);
-- HU-14: el autor edita tema y contenido solo durante los 5 minutos siguientes al envío; no se borran
CREATE OR REPLACE FUNCTION fn_proteger_mensajes() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Los mensajes no se borran (HU-14)'; END IF;
  IF (NEW.proyecto_id, NEW.autor_id, NEW.created_at) IS DISTINCT FROM (OLD.proyecto_id, OLD.autor_id, OLD.created_at) THEN
    RAISE EXCEPTION 'De un mensaje solo se editan el tema y el contenido (HU-14)';
  END IF;
  IF (NEW.tema, NEW.contenido) IS DISTINCT FROM (OLD.tema, OLD.contenido) THEN
    IF now() > OLD.created_at + interval '5 minutes' THEN
      RAISE EXCEPTION 'Un mensaje solo se edita durante los 5 minutos siguientes a su envío (HU-14)';
    END IF;
    NEW.editado_at := now();
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_proteger_mensajes BEFORE UPDATE OR DELETE ON mensajes
  FOR EACH ROW EXECUTE FUNCTION fn_proteger_mensajes();

-- Notificaciones: hasta 3 intentos de envío del correo (HU-24, RNF-13)
ALTER TABLE notificaciones
  ADD CONSTRAINT ck_notif_intentos CHECK (intentos_envio BETWEEN 0 AND 3);

-- Solicitudes de reunión (HU-15, HU-16, RN-15)
ALTER TABLE solicitudes_reunion
  ADD CONSTRAINT ck_sr_motivo   CHECK (motivo IN ('Revisar el avance','Cambio en los requerimientos','Duda técnica','Reportar un error','Otro')),
  ADD CONSTRAINT ck_sr_razon    CHECK (char_length(trim(razon)) BETWEEN 20 AND 500),
  ADD CONSTRAINT ck_sr_opciones CHECK (opcion_2 IS NULL OR opcion_2 <> opcion_1),
  ADD CONSTRAINT ck_sr_rechazo  CHECK (motivo_rechazo IS NULL OR char_length(motivo_rechazo) BETWEEN 15 AND 300);
CREATE UNIQUE INDEX ux_solicitud_abierta ON solicitudes_reunion (proyecto_id) WHERE estado = 'PENDIENTE';

-- Auditoría inmutable (RN-20, RNF-02)
CREATE OR REPLACE FUNCTION fn_bloquear_cambios() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'La tabla % no admite % (registro inmutable)', TG_TABLE_NAME, TG_OP;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_auditoria_inmutable BEFORE UPDATE OR DELETE ON auditoria
  FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios();
CREATE TRIGGER tg_respuestas_plan_inmutables BEFORE UPDATE OR DELETE ON respuestas_plan
  FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios();
-- El cuestionario de la cita no se edita ni se borra (RN-24, RNF-02)
CREATE TRIGGER tg_cuestionarios_inmutables BEFORE UPDATE OR DELETE ON cuestionarios_cita
  FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios();
-- Lo publicado al cliente (un paso HECHO) no se edita ni se borra (RN-09, RNF-02).
-- Un paso pendiente se borra solo con el plan en borrador o con ajustes pedidos (RN-27).
-- Con el plan aprobado, un paso pendiente se quita (pasa a QUITADO, no se borra) solo con
-- un cambio QUITAR_PASO aceptado (RN-30). Un paso QUITADO tampoco cambia.
CREATE OR REPLACE FUNCTION fn_proteger_pasos() RETURNS trigger AS $$
DECLARE plan estado_plan;
BEGIN
  IF OLD.estado = 'HECHO' THEN
    RAISE EXCEPTION 'El paso "%" ya se publicó al cliente: no se edita ni se borra (RN-09)', OLD.titulo;
  END IF;
  IF OLD.estado = 'QUITADO' THEN
    RAISE EXCEPTION 'El paso "%" ya se quitó del plan: no cambia (RN-30)', OLD.titulo;
  END IF;
  IF TG_OP = 'UPDATE' AND NEW.estado = 'QUITADO' AND NOT EXISTS (
       SELECT 1 FROM cambios_plan c WHERE c.paso_id = OLD.id AND c.tipo = 'QUITAR_PASO' AND c.estado = 'ACEPTADO') THEN
    RAISE EXCEPTION 'Quitar un paso del plan aprobado requiere un cambio autorizado por el administrador (y aceptado por el cliente si la fase la aprueba él) (RN-30)';
  END IF;
  IF TG_OP = 'DELETE' THEN
    SELECT p.estado_plan INTO plan
      FROM proyecto_fases f JOIN proyectos p ON p.id = f.proyecto_id
     WHERE f.id = OLD.proyecto_fase_id;
    -- Si la fase ya no existe, se está quitando la fase completa (lo validó fn_proteger_fases)
    IF FOUND AND plan NOT IN ('BORRADOR','AJUSTES_PEDIDOS') THEN
      RAISE EXCEPTION 'Solo se quitan pasos mientras el plan está en borrador o con ajustes pedidos (RN-27)';
    END IF;
    RETURN OLD;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER tg_proteger_pasos BEFORE UPDATE OR DELETE ON pasos
  FOR EACH ROW EXECUTE FUNCTION fn_proteger_pasos();
-- =====================================================================
-- DATOS INICIALES (catálogos)
-- =====================================================================
INSERT INTO roles (codigo, nombre, descripcion) VALUES
 ('CLIENTE','Cliente','Agenda citas y sigue sus proyectos'),
 ('PRODUCT_OWNER','Product Owner','Registra el trabajo del equipo y habla con el cliente'),
 ('ADMINISTRADOR','Administrador','Gestiona citas, proyectos, equipos y el sitio');
INSERT INTO horario_laboral (dia_semana, hora_inicio, hora_fin)
 SELECT d, h.i, h.f FROM generate_series(1,5) d, (VALUES ('08:00'::time,'12:00'::time),('14:00','18:00')) h(i,f);

-- Catálogo de servicios (HU-18) y plantilla de fases de cada uno (HU-27, RN-27, RN-28)
INSERT INTO servicios (nombre, slug, descripcion_corta, imagen_url, imagen_alt, orden, visible) VALUES
 ('Desarrollo de tienda en línea', 'tienda-en-linea', 'Catálogo, carrito de compras y pagos en línea para vender por internet.', 'img/servicios/tienda-en-linea.webp', 'Ilustración de una tienda en línea con productos y carrito de compras', 1, true),
 ('Página web corporativa', 'web-corporativa', 'Sitio institucional adaptable a celular, rápido y fácil de actualizar.', 'img/servicios/web-corporativa.webp', 'Ilustración de un sitio web en computador y en celular', 2, true),
 ('Aplicación móvil', 'app-movil', 'Apps para Android e iOS conectadas a los procesos de tu negocio.', 'img/servicios/app-movil.webp', 'Ilustración de dos pantallas de una aplicación móvil', 3, true),
 ('Sistema de inventario', 'sistema-inventario', 'Control de existencias, entradas, salidas y reportes al día.', 'img/servicios/sistema-inventario.webp', 'Ilustración de un panel con gráfica de barras y cajas de inventario', 4, true),
 ('Integración de pagos', 'integracion-pagos', 'Conecta tu sitio con pasarelas de pago seguras y conciliación automática.', 'img/servicios/integracion-pagos.webp', 'Ilustración de una tarjeta de pago protegida por un escudo con candado', 5, true);
-- Fases y pasos en una sola transacción: RN-28 se revisa al confirmar (COMMIT)
BEGIN;
INSERT INTO plantilla_fases (servicio_id, orden, nombre, que_es, aprueba_cliente)
 SELECT s.id, v.orden, v.nombre, v.que_es, v.aprueba FROM (VALUES
 ('tienda-en-linea', 1, 'Toma de requerimientos', 'Entendemos tu negocio y escribimos todo lo que necesitas.', true),
 ('tienda-en-linea', 2, 'Análisis y diseño', 'Diseñamos cómo se verá y cómo funcionará, antes de construirlo.', true),
 ('tienda-en-linea', 3, 'Desarrollo', 'Construimos tu proyecto, paso a paso.', false),
 ('tienda-en-linea', 4, 'Pruebas', 'Revisamos que todo funcione bien antes de publicarlo.', true),
 ('tienda-en-linea', 5, 'Despliegue', 'Lo publicamos en internet y te enseñamos a usarlo.', false),
 ('web-corporativa', 1, 'Requerimientos y contenido', 'Definimos las páginas del sitio y reunimos tus textos, fotos y logo.', true),
 ('web-corporativa', 2, 'Diseño', 'Te mostramos cómo se verá el sitio en computador y en celular.', true),
 ('web-corporativa', 3, 'Desarrollo', 'Construimos el sitio con el diseño aprobado.', false),
 ('web-corporativa', 4, 'Pruebas', 'Revisamos enlaces, formularios y que cargue rápido.', true),
 ('web-corporativa', 5, 'Publicación', 'Publicamos el sitio en tu dominio y te enseñamos a actualizarlo.', false),
 ('app-movil', 1, 'Toma de requerimientos', 'Entendemos tu negocio y escribimos todo lo que hará la app.', true),
 ('app-movil', 2, 'Análisis y diseño', 'Diseñamos las pantallas y cómo se guardará la información.', true),
 ('app-movil', 3, 'Desarrollo', 'Construimos la app y su panel de administración.', false),
 ('app-movil', 4, 'Pruebas', 'Probamos la app en Android y iPhone.', true),
 ('app-movil', 5, 'Despliegue', 'Publicamos la app en App Store y Google Play.', false),
 ('sistema-inventario', 1, 'Toma de requerimientos', 'Conocemos tus bodegas, productos y cómo controlas hoy el inventario.', true),
 ('sistema-inventario', 2, 'Análisis y diseño', 'Diseñamos las pantallas, los reportes y cómo se guardará la información.', true),
 ('sistema-inventario', 3, 'Desarrollo', 'Construimos el sistema.', false),
 ('sistema-inventario', 4, 'Pruebas', 'Revisamos que los cálculos de existencias sean correctos.', true),
 ('sistema-inventario', 5, 'Carga de datos y capacitación', 'Cargamos tus productos y existencias reales y capacitamos a tu equipo.', true),
 ('sistema-inventario', 6, 'Puesta en marcha', 'Dejamos el sistema funcionando en tu negocio.', false),
 ('integracion-pagos', 1, 'Requerimientos y elección de pasarela', 'Elegimos contigo la pasarela y los medios de pago.', true),
 ('integracion-pagos', 2, 'Integración', 'Conectamos tu sitio con la pasarela.', false),
 ('integracion-pagos', 3, 'Pruebas de pagos', 'Hacemos pagos de prueba con cada medio.', true),
 ('integracion-pagos', 4, 'Salida a producción', 'Activamos los pagos reales.', false)
 ) v(slug, orden, nombre, que_es, aprueba) JOIN servicios s ON s.slug = v.slug;
INSERT INTO plantilla_pasos (plantilla_fase_id, orden, titulo, explicacion)
 SELECT f.id, v.orden, v.titulo, v.explicacion FROM (VALUES
 ('tienda-en-linea', 1, 1, 'Reunión inicial', 'Conversamos contigo para entender tu negocio y lo que quieres lograr con la tienda.'),
 ('tienda-en-linea', 1, 2, 'Lista de lo que hará la tienda', 'Escribimos en palabras sencillas todo lo que podrán hacer tus clientes y tú.'),
 ('tienda-en-linea', 1, 3, 'Documento para revisar', 'Te enviamos la lista completa para que confirmes que no falta nada.'),
 ('tienda-en-linea', 2, 1, 'Bocetos de las pantallas', 'Dibujamos cómo se verá cada página antes de construirla.'),
 ('tienda-en-linea', 2, 2, 'Organización de la información', 'Definimos cómo se guardarán tus productos, clientes y pedidos.'),
 ('tienda-en-linea', 3, 1, 'Base de la tienda e inicio de sesión', 'Preparamos el lugar en internet donde vivirá tu tienda.'),
 ('tienda-en-linea', 3, 2, 'Catálogo de productos', 'Las páginas donde tus clientes ven y buscan tus productos.'),
 ('tienda-en-linea', 3, 3, 'Carrito y pagos en línea', 'Tus clientes agregan productos y pagan de forma segura.'),
 ('tienda-en-linea', 3, 4, 'Pedidos y correos de confirmación', 'Recibes cada pedido y tu cliente recibe un correo con el resumen.'),
 ('tienda-en-linea', 4, 1, 'Probar cada función', 'Hacemos compras de prueba para encontrar errores.'),
 ('tienda-en-linea', 4, 2, 'Probar en celulares y computadores', 'Revisamos que se vea y funcione bien en cualquier pantalla.'),
 ('tienda-en-linea', 4, 3, 'Prueba contigo', 'Usas la tienda como si fueras un cliente y nos dices si todo está bien.'),
 ('tienda-en-linea', 5, 1, 'Dirección en internet', 'Dejamos tu tienda en su propia dirección web.'),
 ('tienda-en-linea', 5, 2, 'Publicación', 'La tienda queda abierta para que cualquier persona pueda comprar.'),
 ('tienda-en-linea', 5, 3, 'Manuales', 'Te entregamos los manuales de uso y la documentación técnica.'),
 ('tienda-en-linea', 5, 4, 'Capacitación', 'Te enseñamos a usarlo y resolvemos tus dudas.'),
 ('web-corporativa', 1, 1, 'Reunión inicial', 'Conversamos sobre tu empresa, tus clientes y lo que debe lograr el sitio.'),
 ('web-corporativa', 1, 2, 'Mapa del sitio', 'Listamos las páginas y lo que tendrá cada una.'),
 ('web-corporativa', 1, 3, 'Entrega de contenido', 'Nos envías textos, fotos y logo; te ayudamos con lo que falte.'),
 ('web-corporativa', 2, 1, 'Bocetos de las páginas', 'Dibujamos cada página antes de construirla.'),
 ('web-corporativa', 2, 2, 'Diseño visual', 'Aplicamos tus colores, letras e imágenes.'),
 ('web-corporativa', 3, 1, 'Páginas del sitio', 'Construimos cada página con su contenido.'),
 ('web-corporativa', 3, 2, 'Formulario y WhatsApp', 'Tus visitantes te escriben desde el sitio.'),
 ('web-corporativa', 3, 3, 'Buscadores', 'Preparamos el sitio para que Google lo encuentre.'),
 ('web-corporativa', 4, 1, 'Revisión en distintos equipos', 'Probamos en celulares, tabletas y computadores.'),
 ('web-corporativa', 4, 2, 'Prueba contigo', 'Revisas el sitio y nos dices si todo está bien.'),
 ('web-corporativa', 5, 1, 'Dominio y hosting', 'Conectamos tu dirección web y el alojamiento.'),
 ('web-corporativa', 5, 2, 'Publicación', 'El sitio queda abierto al público.'),
 ('web-corporativa', 5, 3, 'Manuales', 'Te entregamos los manuales de uso y la documentación técnica.'),
 ('web-corporativa', 5, 4, 'Capacitación', 'Te enseñamos a usarlo y resolvemos tus dudas.'),
 ('app-movil', 1, 1, 'Reunión inicial', 'Conversamos contigo para entender el proceso que la app va a resolver.'),
 ('app-movil', 1, 2, 'Lista de lo que hará la app', 'Escribimos en palabras sencillas todo lo que podrán hacer tus clientes y tú.'),
 ('app-movil', 1, 3, 'Documento para revisar', 'Te enviamos la lista completa para que confirmes que no falta nada.'),
 ('app-movil', 2, 1, 'Bocetos de las pantallas', 'Dibujamos cómo se verá cada pantalla.'),
 ('app-movil', 2, 2, 'Organización de la información', 'Definimos cómo se guardarán tus datos.'),
 ('app-movil', 2, 3, 'Prototipo para revisar', 'Te mostramos las pantallas para que las pruebes antes de construir.'),
 ('app-movil', 3, 1, 'Base de la app e inicio de sesión', 'Preparamos la app y la entrada con usuario y contraseña.'),
 ('app-movil', 3, 2, 'Funciones principales', 'Construimos las funciones acordadas.'),
 ('app-movil', 3, 3, 'Panel de administración', 'Tú gestionas la información desde un panel web.'),
 ('app-movil', 4, 1, 'Probar cada función', 'Usamos la app como tus clientes para encontrar errores.'),
 ('app-movil', 4, 2, 'Probar en Android y iPhone', 'Revisamos que funcione en distintos celulares.'),
 ('app-movil', 4, 3, 'Prueba contigo', 'Usas la app y nos dices si todo está bien.'),
 ('app-movil', 5, 1, 'Cuentas de desarrollador', 'Te ayudamos a crear tus cuentas en Apple y Google.'),
 ('app-movil', 5, 2, 'Publicación en App Store y Google Play', 'Subimos la app y atendemos la revisión de las tiendas.'),
 ('app-movil', 5, 3, 'Manuales', 'Te entregamos los manuales de uso y la documentación técnica.'),
 ('app-movil', 5, 4, 'Capacitación', 'Te enseñamos a usarlo y resolvemos tus dudas.'),
 ('sistema-inventario', 1, 1, 'Visita o reunión inicial', 'Vemos cómo registras hoy entradas y salidas.'),
 ('sistema-inventario', 1, 2, 'Lista de lo que hará el sistema', 'Escribimos las funciones y los reportes que necesitas.'),
 ('sistema-inventario', 2, 1, 'Bocetos de las pantallas', 'Dibujamos cada pantalla antes de construirla.'),
 ('sistema-inventario', 2, 2, 'Organización de la información', 'Definimos productos, bodegas, movimientos y usuarios.'),
 ('sistema-inventario', 3, 1, 'Productos y bodegas', 'Registro de productos con código y existencias por bodega.'),
 ('sistema-inventario', 3, 2, 'Entradas y salidas', 'Movimientos con responsable y fecha.'),
 ('sistema-inventario', 3, 3, 'Alertas y reportes', 'Avisos de existencias bajas y reportes descargables.'),
 ('sistema-inventario', 4, 1, 'Probar cada función', 'Registramos movimientos de prueba y revisamos los saldos.'),
 ('sistema-inventario', 4, 2, 'Prueba contigo', 'Tu equipo lo usa con datos de ejemplo.'),
 ('sistema-inventario', 5, 1, 'Carga de datos', 'Pasamos tus productos y existencias desde tus hojas de cálculo.'),
 ('sistema-inventario', 5, 2, 'Verificación del inventario inicial', 'Comparas las existencias cargadas con el conteo físico.'),
 ('sistema-inventario', 5, 3, 'Manuales', 'Te entregamos los manuales de uso y la documentación técnica.'),
 ('sistema-inventario', 5, 4, 'Capacitación', 'Te enseñamos a usarlo y resolvemos tus dudas.'),
 ('sistema-inventario', 6, 1, 'Instalación', 'Publicamos el sistema y configuramos los usuarios.'),
 ('sistema-inventario', 6, 2, 'Acompañamiento la primera semana', 'Resolvemos dudas mientras tu equipo empieza a usarlo.'),
 ('integracion-pagos', 1, 1, 'Reunión inicial', 'Revisamos tu sitio y cómo cobras hoy.'),
 ('integracion-pagos', 1, 2, 'Comparación de pasarelas', 'Te mostramos costos y condiciones de cada opción.'),
 ('integracion-pagos', 1, 3, 'Apertura de la cuenta', 'Te acompañamos a crear tu cuenta en la pasarela elegida.'),
 ('integracion-pagos', 2, 1, 'Conexión en modo de pruebas', 'Conectamos el pago sin mover dinero real.'),
 ('integracion-pagos', 2, 2, 'Confirmación y conciliación', 'Tu sistema marca cada pago como recibido.'),
 ('integracion-pagos', 3, 1, 'Pagos de prueba', 'Pagamos con tarjeta, PSE y los demás medios.'),
 ('integracion-pagos', 3, 2, 'Pagos rechazados y devoluciones', 'Revisamos qué pasa cuando un pago falla.'),
 ('integracion-pagos', 4, 1, 'Activación', 'Cambiamos a modo real y hacemos un primer pago controlado.'),
 ('integracion-pagos', 4, 2, 'Manuales', 'Te entregamos los manuales de uso y la documentación técnica.'),
 ('integracion-pagos', 4, 3, 'Capacitación', 'Te enseñamos a usarlo y resolvemos tus dudas.')
 ) v(slug, fase, orden, titulo, explicacion)
 JOIN servicios s ON s.slug = v.slug JOIN plantilla_fases f ON f.servicio_id = s.id AND f.orden = v.fase;
COMMIT;
