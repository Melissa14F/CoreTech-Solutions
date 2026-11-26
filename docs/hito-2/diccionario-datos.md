# Diccionario de datos · CoreTech Solutions

Motor: **PostgreSQL 15**. El esquema lo crea Flyway (`src/main/resources/db/migration/V1…V16`) y Hibernate solo lo **valida** (`ddl-auto=validate`). Las restricciones `CHECK`, los índices parciales y funcionales y los disparadores viven en SQL porque JPA no los expresa; la columna «Mapeo ORM» indica cómo se lee cada columna en el código.

Convenciones: PK = clave primaria · FK = llave foránea (todas `DEFERRABLE INITIALLY IMMEDIATE`) · `timestamptz` se mapea a `java.time.Instant` · `uuid` a `java.util.UUID` · los enums de PostgreSQL usan `@Enumerated(EnumType.STRING)` + `@JdbcType(PostgreSQLEnumJdbcType.class)`.

Resumen de entidades mapeadas con `@Entity` (13; todas con su detalle en las secciones 1 a 8) y sus repositorios:

| Tabla | Entidad (paquete `domain`) | Repositorio |
|---|---|---|
| `roles` | `acceso.domain.Rol` | `RolRepositorio` (JpaRepository) |
| `usuarios` | `acceso.domain.Usuario` | `UsuarioRepositorio` (JpaRepository) |
| `tokens_recuperacion` | `acceso.domain.TokenRecuperacion` | `TokenRecuperacionRepositorio` (JpaRepository) |
| `citas` | `agenda.domain.Cita` | `CitaRepositorio` (JpaRepository + JPQL) |
| `servicios` | `catalogo.domain.Servicio` | `ServicioRepositorio` (JpaRepository) |
| `contenido_sitio` | `catalogo.domain.ContenidoSitio` | `ContenidoRepositorio` (JpaRepository) |
| `notificaciones` | `comunicacion.domain.Notificacion` | `comunicacion.repository.NotificacionRepositorio` |
| `sesiones_cerradas` | `compartido.domain.SesionCerrada` | `compartido.repository.SesionCerradaRepositorio` (JpaRepository) |
| `cuentas_externas` | `acceso.domain.CuentaExterna` (`@ManyToOne` LAZY a `Usuario`) | `acceso.repository.CuentaExternaRepositorio` (JpaRepository) |
| `proyectos` | `proyectos.domain.Proyecto` | `proyectos.repository.ProyectoRepositorio` (JpaRepository: alta, lecturas y cambios de estado, datos y equipo) |
| `cambios_plan` | `proyectos.domain.CambioPlan` (`pasos_propuestos` jsonb como `@JdbcTypeCode(SqlTypes.JSON)`) | `proyectos.repository.CambioPlanRepositorio` (JpaRepository: proponer, autorizar, rechazar y aceptar con `UPDATE … WHERE estado = <esperado>`) |
| `proyecto_fases` | `proyectos.domain.ProyectoFase` (lectura, `@Immutable`) | por `Proyecto.fases` (`@OneToMany`, LAZY) |
| `pasos` | `proyectos.domain.Paso` (lectura, `@Immutable`) | por `ProyectoFase.pasos` (`@OneToMany`, LAZY) |

Los cambios de `proyectos` (datos, equipo, estado, reanudar, plan y cotización, garantía) son consultas `@Modifying` de **una sola sentencia** en `ProyectoRepositorio`, no cambios a la entidad, porque los disparadores de PostgreSQL validan cada sentencia completa (reanudar cambia `estado` y `estado_plan` juntos). Un proyecto nunca se borra: los métodos de borrado del repositorio están bloqueados. `ProyectoFase` y `Paso` siguen siendo de lectura (`@Immutable`). La cadena `Proyecto → ProyectoFase → Paso` usa `@OneToMany(fetch = LAZY)` con `@OrderBy("orden ASC")`, y cada hijo apunta a su padre con `@ManyToOne(fetch = LAZY)`. Las fases y los pasos no se cargan hasta pedirlos; `ProyectoRepositorio.findConFasesByCodigo` usa `@EntityGraph` para traer proyecto y fases en una sola consulta (sin el problema N+1).

Las demás tablas (fases y pasos del plan, respuestas del plan, lecturas con varios JOIN de los cambios del plan, seguimiento, mensajes, auditoría…) y las escrituras de fases y pasos se siguen haciendo con `JdbcTemplate` por las razones del [ADR-004](adr/ADR-004-persistencia-hibrida-jpa-jdbc.md). Los tres modelos de proyectos se detallan en la sección 8.

---

## 1. `roles` — roles del sistema

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `smallserial` | PK | Identificador | `@Id Short id` |
| `codigo` | `varchar(20)` | NOT NULL, UNIQUE | `CLIENTE`, `PRODUCT_OWNER` o `ADMINISTRADOR` | `String codigo` |
| `nombre` | `varchar(40)` | NOT NULL | Nombre visible | `String nombre` |
| `descripcion` | `varchar(200)` | — | Texto de ayuda | (no mapeada; solo la lee SQL) |

Índices: PK `roles_pkey`; único `roles_codigo_key`.

## 2. `usuarios` — cuentas (clientes, Product Owner, administradora)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK, DEFAULT `gen_random_uuid()` | Identificador (es el `sub` del JWT) | `@Id UUID id` |
| `rol_id` | `smallint` | NOT NULL, FK → `roles.id` | Rol de la cuenta | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name="rol_id") Rol rol` |
| `nombre` | `varchar(60)` | NOT NULL, CHECK `ck_usuarios_nombre` (3–60) | Nombre o razón social | `String nombre` |
| `email` | `varchar(120)` | NOT NULL, UNIQUE, CHECK `ck_usuarios_email` (formato) | Correo de ingreso | `String email` |
| `celular` | `varchar(10)` | NOT NULL, CHECK `ck_usuarios_celular` (`^3[0-9]{9}$`) | Celular colombiano | `String celular` |
| `empresa` | `varchar(80)` | — | Empresa del cliente | `String empresa` |
| `password_hash` | `varchar(255)` | NULL permitido desde V9 (cuentas Google/Microsoft) | Hash BCrypt (costo 10) | `@Column(name="password_hash")` |
| `debe_cambiar_password` | `boolean` | NOT NULL, DEFAULT false | Contraseña temporal (RF-05) | `@Column(name="debe_cambiar_password")` |
| `acepta_datos_at` | `timestamptz` | — | Aceptación de la política de datos (RNF-09) | `Instant aceptaDatosAt` |
| `activo` | `boolean` | NOT NULL, DEFAULT true | Cuenta habilitada | `boolean activo` |
| `intentos_fallidos` | `smallint` | NOT NULL, DEFAULT 0, CHECK `ck_usuarios_intentos` (0–5) | Contador de bloqueo (RNF-10) | `short intentosFallidos` |
| `bloqueado_hasta` | `timestamptz` | — | Fin del bloqueo de 15 min | `Instant bloqueadoHasta` |
| `ultimo_acceso_at` | `timestamptz` | — | Último ingreso | `Instant ultimoAccesoAt` |
| `version_sesion` | `integer` | NOT NULL, DEFAULT 0 (V7) | Sube al cambiar clave; invalida los JWT anteriores | `int versionSesion` |
| `anonimizado_at` | `timestamptz` | CHECK `ck_usuarios_anonimo` (`anonimizado_at IS NULL OR NOT activo`) | Supresión de datos (Ley 1581) | `Instant anonimizadoAt` |
| `created_at` / `updated_at` | `timestamptz` | NOT NULL, DEFAULT `now()` | Auditoría de fila | `creadoEn` / `actualizadoEn` |

Índices: PK; `usuarios_email_key` (UNIQUE `email`); **`ux_usuarios_email_ci`** UNIQUE sobre `lower(email)` (correo sin distinguir mayúsculas, índice funcional creado por Flyway).

## 3. `tokens_recuperacion` — enlaces para restablecer la contraseña

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `usuario_id` | `uuid` | NOT NULL, FK → `usuarios.id` | Dueño del enlace | `UUID usuarioId` |
| `token_hash` | `varchar(255)` | NOT NULL, UNIQUE | SHA-256 del token (nunca el token) | `String tokenHash` |
| `expira_at` | `timestamptz` | NOT NULL | Vence a los 30 min | `Instant expiraEn` |
| `usado_at` | `timestamptz` | — | Un solo uso | `Instant usadoEn` |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT `now()` | Creación | `Instant creadoEn` |

## 4. `citas` — citas con la empresa (HU-01, HU-08 a HU-10)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `codigo` | `varchar(10)` | NOT NULL, UNIQUE | Código visible `C-0064` (`fn_siguiente_codigo_cita`) | `String codigo` |
| `cliente_id` | `uuid` | NOT NULL, FK → `usuarios.id` | Cliente que agenda | `UUID clienteId` |
| `servicio_id` | `uuid` | NOT NULL, FK → `servicios.id` | Servicio solicitado | `UUID servicioId` |
| `equipo_id` | `uuid` | FK → `equipos.id` | Equipo asignado por la administradora | `UUID equipoId` |
| `fecha_hora` | `timestamptz` | NOT NULL, CHECK `ck_citas_franja` (minuto 0 en hora de Colombia) | Instante de la reunión (RN-23) | `Instant fechaHora` |
| `modalidad` | enum `modalidad` | NOT NULL | `PRESENCIAL`, `VIRTUAL` | `@Enumerated(STRING) @JdbcType(PostgreSQLEnumJdbcType) Modalidad` |
| `descripcion_necesidad` | `varchar(500)` | NOT NULL, CHECK `ck_citas_descripcion` (20–500) | Necesidad del cliente | `String descripcionNecesidad` |
| `pais` | `varchar(60)` | NOT NULL, DEFAULT `'Colombia'`, CHECK `ck_citas_pais` | País del cliente | `String pais` |
| `zona_horaria` | `varchar(40)` | NOT NULL, DEFAULT `'America/Bogota'`, CHECK `ck_citas_zona` (IANA) | Zona del cliente | `String zonaHoraria` |
| `estado` | enum `estado_cita` | NOT NULL, DEFAULT `'PENDIENTE'` | `PENDIENTE`, `CONFIRMADA`, `ATENDIDA`, `CANCELADA` | `@Enumerated(STRING) @JdbcType(...) EstadoCita` |
| `enlace_reunion` | `varchar(255)` | CHECK `ck_citas_enlace` (obligatorio si es virtual y está CONFIRMADA), `ck_citas_enlace_url` | Enlace de la videollamada (RN-12) | `String enlaceReunion` |
| `nota_cliente`, `nota_asignacion` | `varchar(300)` | — | Notas del equipo y de la administradora | `String` |
| `motivo_cancelacion` | `varchar(300)` | CHECK `ck_citas_motivo` (15–300) | Motivo de la cancelación | `String motivoCancelacion` |
| `cancelada_por` | `uuid` | FK → `usuarios.id` | Quién canceló | `UUID canceladaPor` |
| `confirmada_at`, `atendida_at`, `cancelada_at` | `timestamptz` | CHECK `ck_citas_cancelada`, `ck_citas_atendida` | Marcas de cada estado | `Instant …En` |
| `created_at` / `updated_at` | `timestamptz` | NOT NULL | Auditoría de fila | `creadaEn` / `actualizadaEn` |

Índices (declarados en `@Table(indexes = …)` los que JPA permite):

| Índice | Columnas | Tipo | Para qué sirve |
|---|---|---|---|
| `ux_cita_franja` | `fecha_hora` WHERE `estado IN ('PENDIENTE','CONFIRMADA')` | UNIQUE parcial (Flyway) | **RN-02**: dos clientes no toman la misma franja; resuelve la carrera con 409 |
| `ux_cita_cliente_hora` | `(cliente_id, fecha_hora)` WHERE `estado <> 'CANCELADA'` | UNIQUE parcial compuesto (Flyway) | **RN-03**: un cliente no tiene dos citas a la misma hora |
| `citas_cliente_id_idx` | `cliente_id` | B-tree | «Mis citas» del cliente |
| `citas_equipo_id_idx` | `equipo_id` | B-tree | Citas del equipo del Product Owner |
| `citas_fecha_hora_estado_idx` | `(fecha_hora, estado)` | B-tree compuesto | Disponibilidad del mes y recordatorios |

## 5. `servicios` — catálogo

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `nombre` | `varchar(40)` | NOT NULL, UNIQUE, CHECK 4–40 | Nombre del servicio | `String nombre` |
| `slug` | `varchar(50)` | NOT NULL, UNIQUE | Identificador de URL (`?servicio=tienda-en-linea`) | `String slug` |
| `descripcion_corta` | `varchar(90)` | NOT NULL, CHECK 20–90 | Texto de la tarjeta | `String descripcionCorta` |
| `imagen_url` / `imagen_alt` | `varchar(255)` / `varchar(120)` | NOT NULL; alt CHECK 10–120 | Imagen y su texto alternativo (accesibilidad) | `String` |
| `orden` | `smallint` | NOT NULL, CHECK `> 0` | Posición en el carrusel | `short orden` |
| `visible` | `boolean` | NOT NULL, DEFAULT true | Oculto = no se agenda (RN-21) | `boolean visible` |
| `created_at` / `updated_at` | `timestamptz` | NOT NULL | Auditoría de fila | `creadoEn` / `actualizadoEn` |

## 6. `contenido_sitio` — textos editables de la página principal

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `clave` | `varchar(40)` | PK | Clave del texto | `@Id String clave` |
| `seccion` | `varchar(30)` | NOT NULL | Sección de la página | `String seccion` |
| `valor` | `varchar(300)` | NOT NULL, CHECK `ck_contenido_valor` (entre `min_longitud` y `max_longitud`) | Texto publicado | `String valor` |
| `min_longitud`, `max_longitud` | `smallint` | NOT NULL, CHECK `ck_contenido_rango` | Largo permitido | `short` |
| `actualizado_por` | `uuid` | FK → `usuarios.id` | Quién lo editó | `UUID actualizadoPor` |
| `updated_at` | `timestamptz` | NOT NULL | Última edición | `Instant actualizadoEn` |

## 7. `notificaciones` — campana y cola de correos (RF-39, RNF-13)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `usuario_id` | `uuid` | NOT NULL, FK → `usuarios.id` | Destinatario | `UUID usuarioId` |
| `tipo` | `varchar(40)` | NOT NULL | Tipo en MAYÚSCULAS (`CITA_REGISTRADA`) | `String tipo` |
| `titulo` | `varchar(80)` | NOT NULL | Título (también asunto del correo) | `String titulo` |
| `mensaje` | `varchar(255)` | NOT NULL | Texto sencillo | `String mensaje` |
| `enlace` | `varchar(255)` | — | Ruta de la SPA | `String enlace` |
| `leida` | `boolean` | NOT NULL, DEFAULT false | Vista en la campana | `boolean leida` |
| `enviada_email` | `boolean` | NOT NULL, DEFAULT false | Ya salió el correo | `boolean enviadaEmail` |
| `intentos_envio` | `smallint` | NOT NULL, DEFAULT 0, CHECK `ck_notif_intentos` (0–3) | Reintentos del correo | `short intentosEnvio` |
| `ultimo_error` | `varchar(255)` | — | Último fallo de envío | `String ultimoError` |
| `created_at` | `timestamptz` | NOT NULL | Creación | `Instant creadaEn` |

Índice: **`notificaciones_usuario_id_leida_idx`** `(usuario_id, leida)` — el conteo de «no leídas» de la campana se pide cada 60 s.

## 8. Núcleo del negocio: proyectos, fases y pasos

El aggregate de proyectos se escribe con `ProyectoRepositorio` (consultas `UPDATE` de una sola sentencia, por los disparadores) y se lee con las entidades `Proyecto`, `ProyectoFase` y `Paso` (relaciones LAZY). Las columnas marcadas «sin mapear» las lee SQL directo (lista «en riesgo», detalle) y no tienen atributo en la entidad.

### 8.1 `proyectos` — entidad `proyectos.domain.Proyecto`

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `codigo` | `varchar(10)` | NOT NULL, UNIQUE | Código visible `P-0024` (`fn_siguiente_codigo_proyecto`) | `String codigo` |
| `nombre` | `varchar(60)` | NOT NULL, CHECK `ck_proyectos_nombre` (5–60) | Nombre del proyecto | `String nombre` |
| `alcance` | `varchar(600)` | NOT NULL, CHECK `ck_proyectos_alcance` (20–600) | Qué se construye | `String alcance` |
| `cita_id` | `uuid` | NOT NULL, UNIQUE, FK → `citas` | Una cita ATENDIDA origina un solo proyecto (RN-04) | `@Column(name="cita_id") UUID citaId` |
| `cliente_id` | `uuid` | NOT NULL, FK → `usuarios` | Cliente dueño | `UUID clienteId` |
| `servicio_id` | `uuid` | NOT NULL, FK → `servicios` | Servicio contratado | `UUID servicioId` |
| `equipo_id` | `uuid` | NOT NULL, FK → `equipos` | Equipo ejecutor (cupo de 3, RN-05) | `UUID equipoId` |
| `creado_por` | `uuid` | NOT NULL, FK → `usuarios` | Administradora que lo creó | `UUID creadoPor` |
| `fecha_inicio` | `date` | NOT NULL | Inicio | `LocalDate fechaInicio` |
| `fecha_estimada_entrega` | `date` | NOT NULL, CHECK `ck_proyectos_fechas` (≥ inicio + 14 días) | Entrega estimada | `LocalDate fechaEstimadaEntrega` |
| `estado` | enum `estado_proyecto` | NOT NULL, DEFAULT `ACTIVO`; CHECK `ck_proyectos_vencido`, `ck_proyectos_garantia`, `ck_proyectos_final` | `ACTIVO`, `PAUSADO`, `EN_GARANTIA`, `FINALIZADO`, `CANCELADO` | `@Enumerated(STRING) @JdbcType(PostgreSQLEnumJdbcType) EstadoProyecto` |
| `estado_plan` | enum `estado_plan` | NOT NULL, DEFAULT `BORRADOR`; trigger `fn_validar_estado_plan`; CHECK `ck_proyectos_enviado`, `ck_proyectos_aprobado` | `BORRADOR`, `ENVIADO`, `AJUSTES_PEDIDOS`, `APROBADO`, `VENCIDO`, `RECHAZADO` | `@Enumerated(STRING) @JdbcType(...) EstadoPlan` |
| `valor_cotizado` | `numeric(14,0)` | CHECK `ck_proyectos_valor` (1.000.000 a 200.000.000 COP) | Cotización inicial | `BigDecimal valorCotizado` |
| `condiciones_cotizacion` | `varchar(600)` | CHECK `ck_proyectos_condic` (20–600) | Qué incluye y qué no | `String condicionesCotizacion` |
| `plan_enviado_at` | `timestamptz` | — | Cuándo se envió el plan | `Instant planEnviadoEn` |
| `plan_plazo_respuesta` | `date` | — | Plazo del cliente (3 días hábiles) | `LocalDate planPlazoRespuesta` |
| `plan_vence_el` | `date` | CHECK `ck_proyectos_vence` (> plazo) | Vence sin respuesta (10 días hábiles) | `LocalDate planVenceEl` |
| `plan_aprobado_at` | `timestamptz` | CHECK `ck_proyectos_aprobado` | Aprobación del cliente | `Instant planAprobadoEn` |
| `avance_porcentaje` | `numeric(5,2)` | NOT NULL, DEFAULT 0, CHECK `ck_proyectos_avance` (0–100) | Lo calcula el disparador `tg_recalcular_avance` | `@Column(insertable=false, updatable=false) BigDecimal` |
| `motivo_estado` | `varchar(300)` | CHECK `ck_proyectos_motivo` (10–300) | Motivo de pausa o cancelación | `String motivoEstado` |
| `garantia_hasta` | `date` | — | Fin de la garantía de 30 días | `LocalDate garantiaHasta` |
| `finalizado_at` | `timestamptz` | — | Cierre | `Instant finalizadoEn` |
| `plan_recordado_at`, `plan_cancelacion_avisada_at` | `timestamptz` | — (V12) | Marcas para no repetir avisos | `Instant planRecordadoEn`, `planCancelacionAvisadaEn` |
| `version` | `integer` | NOT NULL, DEFAULT 0 | Se sube en cada cambio | `int version` |
| `created_at`, `updated_at` | `timestamptz` | NOT NULL, DEFAULT `now()` | Auditoría de fila | `@Column(insertable=false)` |

Índices: PK; `proyectos_codigo_key` y `proyectos_cita_id_key` (UNIQUE); `proyectos_cliente_id_idx` `(cliente_id)`; **`proyectos_equipo_id_estado_idx` `(equipo_id, estado)`**, para contar el cupo del equipo.

### 8.2 `proyecto_fases` — entidad `proyectos.domain.ProyectoFase` (lectura)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `proyecto_id` | `uuid` | NOT NULL, FK → `proyectos` | Proyecto dueño | `@ManyToOne(fetch = LAZY) @JoinColumn Proyecto proyecto` |
| `orden` | `smallint` | NOT NULL, CHECK `ck_pf_orden` (1–8) | Posición en el plan | `short orden` |
| `nombre` | `varchar(60)` | NOT NULL, CHECK `ck_pf_nombre` (3–60) | Nombre de la fase | `String nombre` |
| `que_es` | `varchar(140)` | — | Explicación sencilla | sin mapear |
| `aprueba_cliente` | `boolean` | NOT NULL, DEFAULT false | Si el cliente aprueba la fase | `boolean apruebaCliente` |
| `cambio_plan_id` | `uuid` | FK → `cambios_plan` | Cambio que la creó | sin mapear |
| `estado` | enum `estado_fase` | NOT NULL, DEFAULT `PENDIENTE` | `PENDIENTE`, `EN_CURSO`, `EN_REVISION`, `APROBADA`, `EN_CORRECCION`, `QUITADA` | `@Enumerated(STRING) @JdbcType(...) EstadoFase` |
| `iniciada_at`, `entregada_at` | `timestamptz` | — | Marcas de la fase | sin mapear |
| `plazo_aprobacion` | `date` | — | 3 días hábiles para aprobar | sin mapear |
| `rondas_ajuste_usadas` | `smallint` | NOT NULL, CHECK `ck_pf_rondas` (0–2) | Rondas de ajuste usadas | `short rondasAjusteUsadas` |
| `aprobada_at` | `timestamptz` | CHECK `ck_pf_aprobada` | Aprobación | `Instant aprobadaEn` |
| `aprobada_por`, `aprobacion_automatica`, `plazo_recordado_at` | `uuid`, `boolean`, `timestamptz` | — | Quién aprobó y avisos (V13) | sin mapear |

Índices: PK; **`ux_pf_orden` UNIQUE parcial `(proyecto_id, orden) WHERE estado <> 'QUITADA'`**; `ux_pf_nombre` UNIQUE parcial sobre `lower(trim(nombre))`; **`proyecto_fases_proyecto_id_orden_idx` `(proyecto_id, orden)`**.

### 8.3 `pasos` — entidad `proyectos.domain.Paso` (lectura)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `proyecto_fase_id` | `uuid` | NOT NULL, FK → `proyecto_fases` ON DELETE CASCADE | Fase dueña | `@ManyToOne(fetch = LAZY) ProyectoFase fase` |
| `orden` | `smallint` | NOT NULL | Posición | `short orden` |
| `tipo` | enum `tipo_paso` | NOT NULL, DEFAULT `PASO`; CHECK `ck_pasos_origen` | `PASO`, `CORRECCION`, `AJUSTE`, `ALCANCE` | `@Enumerated(STRING) @JdbcType(...) TipoPaso` |
| `titulo` | `varchar(80)` | NOT NULL, CHECK `ck_pasos_titulo` (3–80) | Título | `String titulo` |
| `explicacion` | `varchar(500)` | CHECK `ck_pasos_explicacion` (V3) | Explicación | sin mapear |
| `origen` | `varchar(60)` | — | «Lo pidió el cliente» en ajustes y alcance | sin mapear |
| `estado` | enum `estado_paso` | NOT NULL, DEFAULT `PENDIENTE` | `PENDIENTE`, `EN_CURSO`, `HECHO`, `QUITADO` | `@Enumerated(STRING) @JdbcType(...) EstadoPaso` |
| `nota` | `varchar(500)` | CHECK `ck_pasos_nota` | Nota del trabajo hecho (20–400) | `String nota` |
| `hecho_at`, `registrado_por` | `timestamptz`, `uuid` | CHECK `ck_pasos_hecho`: un paso HECHO exige nota, fecha y autor | Quién y cuándo | `Instant hechoEn` |
| `created_at` | `timestamptz` | NOT NULL | Creación | sin mapear |

Un paso HECHO es **inmutable** (disparador `fn_bloquear_cambios`). Índice: **`pasos_proyecto_fase_id_orden_idx` `(proyecto_fase_id, orden)`**.

### 8.4 `cambios_plan` — entidad `proyectos.domain.CambioPlan`

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `proyecto_id` | `uuid` | NOT NULL, FK → `proyectos` | Proyecto | `UUID proyectoId` |
| `tipo` | enum `tipo_cambio_plan` | NOT NULL; CHECK `ck_cp_*` por tipo | `AGREGAR_FASE`, `QUITAR_FASE`, `CAMBIAR_FECHA`, `QUITAR_PASO`, `AGREGAR_ALCANCE` (V15) | `@Enumerated(STRING) @JdbcType(...) TipoCambioPlan` |
| `nombre_fase`, `posicion` | `varchar(60)`, `smallint` | CHECK `ck_cp_agregar` (3–60; 1–8) | Fase nueva | `String`, `Short` |
| `aprueba_cliente` | `boolean` | NOT NULL, DEFAULT false | La fase nueva la aprueba el cliente | `boolean apruebaCliente` |
| `pasos_propuestos` | `jsonb` | CHECK: arreglo de 1 a 12 pasos | Pasos de la fase nueva | `@JdbcTypeCode(SqlTypes.JSON) String` |
| `proyecto_fase_id`, `paso_id` | `uuid` | FK | Fase o paso afectado | `UUID` |
| `solicitud_cambio_id`, `titulo_paso`, `explicacion_paso` | `uuid`, `varchar(80)`, `varchar(400)` | CHECK `ck_cp_alcance` (V16); UNIQUE parcial `ux_cp_solicitud` | Alcance que el cliente pidió | `UUID`, `String`, `String` |
| `motivo` | `varchar(500)` | NOT NULL, CHECK `ck_cp_motivo` (20–500) | Por qué se propone | `String motivo` |
| `nueva_fecha_entrega` | `date` | CHECK `ck_cp_fase_fecha` | Fecha nueva | `LocalDate` |
| `valor_adicional` | `numeric(14,0)` | NOT NULL, DEFAULT 0, CHECK `ck_cp_valor` (0–200.000.000) | Costo extra | `BigDecimal valorAdicional` |
| `estado` | enum `estado_cambio_plan` | NOT NULL, DEFAULT `PROPUESTO`; trigger `tg_validar_estado_cambio_plan` | `PROPUESTO`, `AUTORIZADO`, `ACEPTADO`, `RECHAZADO_ADMIN`, `RECHAZADO_CLIENTE` | `@Enumerated(STRING) @JdbcType(...) EstadoCambioPlan` |
| `propuesto_por`, `autorizado_por` | `uuid` | NOT NULL / NULL, FK → `usuarios` | Quién propone y quién autoriza | `UUID` |
| `autorizado_at`, `respondido_at`, `created_at` | `timestamptz` | — | Marcas | `Instant` |
| `motivo_rechazo` | `varchar(400)` | — | Por qué se rechazó | `String` |

Cada cambio de estado es `UPDATE … WHERE estado = <esperado>`: si dos personas responden a la vez, solo una gana (409 para la otra).

### 8.5 `sesiones_cerradas` — entidad `compartido.domain.SesionCerrada` (V7)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `jti` | `uuid` | PK | Identificador del token cerrado | `@Id UUID jti` |
| `expira_at` | `timestamptz` | NOT NULL | Hasta cuándo vale el token (después se borra la fila) | `@Column(name="expira_at") Instant expiraEn` |

Índice: `ix_sesiones_cerradas_expira` `(expira_at)` (declarado en `@Table(indexes)`).

### 8.6 `cuentas_externas` — entidad `acceso.domain.CuentaExterna` (V9)

| Columna | Tipo SQL | Restricciones | Descripción | Mapeo ORM |
|---|---|---|---|---|
| `id` | `uuid` | PK | Identificador | `@Id UUID id` |
| `usuario_id` | `uuid` | NOT NULL, FK → `usuarios` ON DELETE CASCADE | Cliente vinculado | `@ManyToOne(fetch = LAZY) @JoinColumn Usuario usuario` |
| `proveedor` | `varchar(12)` | NOT NULL, CHECK (`GOOGLE`, `MICROSOFT`) | Proveedor de identidad | `@Enumerated(STRING) ProveedorExterno` |
| `sujeto` | `varchar(255)` | NOT NULL; UNIQUE `(proveedor, sujeto)` | «sub» de OpenID Connect | `String sujeto` |
| `email` | `varchar(120)` | NOT NULL | Correo verificado por el proveedor | `String email` |
| `created_at`, `ultimo_ingreso_at` | `timestamptz` | — | Marcas | `Instant creadaEn`, `ultimoIngresoEn` |

Índice: UNIQUE `ux_cuentas_externas_usuario` `(usuario_id, proveedor)`.

## 9. Tablas de apoyo sin entidad (acceso por SQL)

Se leen y escriben con `JdbcTemplate` (ver ADR-004). Se documentan porque completan el modelo y participan en las relaciones del diagrama de clases.

| Tabla | Columnas principales | Relaciones y restricciones |
|---|---|---|
| `permisos` | `id smallserial` PK; `codigo varchar(50)` UNIQUE; `modulo varchar(40)`; `descripcion varchar(200)` | Catálogo de permisos (`citas.agendar`, `proyectos.planificar`…) |
| `rol_permisos` | `rol_id smallint`, `permiso_id smallint` (PK compuesta); `bloqueado boolean`; `asignado_at timestamptz` | **Relación N:M entre `roles` y `permisos`**; `bloqueado` = fijado por una regla del negocio |
| `plantilla_fases` | `id uuid` PK; `servicio_id` FK; `orden smallint`; `nombre varchar(60)`; `que_es varchar(140)`; `aprueba_cliente` | UNIQUE `(servicio_id, lower(trim(nombre)))`; de 2 a 8 por servicio (RN-28) |
| `plantilla_pasos` | `id uuid` PK; `plantilla_fase_id` FK; `orden`; `titulo varchar(80)`; `explicacion varchar(160)` | Mínimo un paso por fase (trigger al confirmar) |
| `cuestionarios_cita` | `id uuid` PK; `cita_id` UNIQUE FK; `resumen varchar(1000)`; `descripcion_cliente varchar(600)`; `presupuesto`, `idioma`, `urgencia` (enums); `registrado_por` FK | **1:1 con `citas`**; inmutable (RN-24) |
| `respuestas_plan` | `id uuid` PK; `proyecto_id` FK; `cliente_id` FK; `respuesta tipo_respuesta_plan`; `comentario varchar(600)` | Historial: no se edita |
| `solicitudes_cambio` | `id uuid` PK; `proyecto_fase_id` FK; `cliente_id` FK; `tipo tipo_paso`; `descripcion varchar(800)`; `estado estado_cambio`; `paso_id` UNIQUE; `costo numeric(12,2)`; `dias_adicionales` | Ajustes y alcance que pide el cliente |
| `adjuntos_paso` | `id uuid` PK; `paso_id` FK; `titulo varchar(80)`; `url varchar(255)`; `tipo_mime` (PNG, JPG, SVG, PDF); `tamano_bytes` (1 a 5 MB) | Archivos de evidencia |
| `mensajes` | `id uuid` PK; `proyecto_id` FK; `autor_id` FK; `contenido varchar(1000)` (10–1000) | Índice `(proyecto_id, created_at)`; el autor edita 5 minutos |
| `auditoria` | `id bigserial` PK; `usuario_id`; `categoria varchar(20)`; `accion varchar(60)`; `entidad`, `entidad_id`; `cambios jsonb`; `ip`, `agente` | Índices `(entidad, entidad_id)` y `(categoria, created_at)`; no se edita ni se borra |
| `horario_laboral`, `dias_no_habiles` | `dia_semana`, `hora_inicio`, `hora_fin`; `fecha` PK, `motivo` | Base de las franjas de las citas (RN-01) |

---

---

## Integridad referencial: reglas que protege la base

- Todas las llaves foráneas son `DEFERRABLE INITIALLY IMMEDIATE`.
- Borrados: no se borran citas, proyectos ni pasos publicados (se pasan a estados `CANCELADA`, `CANCELADO`, `QUITADO`); la única cascada es `pasos.proyecto_fase_id`.
- La base traduce cada violación a un código HTTP claro (`CatalogoErroresBaseDatos`): `23505` → 409, `23514` → 422, `P0001` → 409.
