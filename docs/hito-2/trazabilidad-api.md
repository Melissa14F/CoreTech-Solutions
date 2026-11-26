# Trazabilidad y coherencia con los contratos de la API

Este documento enlaza los requisitos con las decisiones, el código y las pruebas, y comprueba que los campos de la API, de los DTO y de la base de datos coinciden. Solo se afirma lo que se contrastó con el código y con `docs/04-api.md`.

## 1. Requisitos → decisión → código → prueba

| Requisito | Decisión | Implementación | Prueba |
|---|---|---|---|
| HU-01 Agendar cita; RN-01 a RN-03 (franja única, horario, sin duplicados) | ADR-002 | `ServicioCitas.agendar`, `ServicioDisponibilidad`, índice `ux_cita_franja` | `CitasTest`, `GestionCitasTest` |
| HU-02 Crear proyecto; RN-04 a RN-06 (una cita, un proyecto; cupo de 3) | ADR-002, ADR-004 | `ServicioProyectos.crear`, `ProyectoRepositorio.insertar` y `copiarPlantilla` | `ProyectosTest`, `ProyectoJpaTest` |
| HU-28 y HU-29 Plan y cotización; RN-27 a RN-29 | ADR-004 | `ServicioPlan`, `ProyectoRepositorio.enviarPlan` y `aprobarPlan` (una sola sentencia por los disparadores) | `PlanTrabajoTest` |
| HU-30 Cambios de un plan aprobado; RN-30 | ADR-002, ADR-004 | `ServicioCambiosPlan`, `CambioPlanRepositorio` (`UPDATE … WHERE estado`) | `CambiosPlanTest` |
| HU-03 Registrar pasos; RN-07, RN-09, RN-16 (lo publicado es inmutable) | — | `ServicioSeguimiento`; disparador `fn_bloquear_cambios` | `InmutabilidadTest` |
| RNF-01 y RNF-10 Seguridad y sesión | ADR-003 | `ConfiguracionSeguridad`, `FiltroSesion`, `FiltroOrigen`, `@PreAuthorize` por permiso | `InventarioEndpointsTest`, `SeguridadTransversalTest` |
| RNF-02 Integridad; RN-20 Auditoría | ADR-002 | Restricciones y disparadores en Flyway; `RegistroAuditoria` dentro de la transacción | `InmutabilidadTest` |
| RNF-13 Correos con reintentos | ADR-001 | Evento `AvisoSolicitado` y envío asíncrono (`EnvioInmediato`, `TareaEnvioCorreos`) | `NotificacionesTest`, `AvisosConcurrentesTest` |
| Mantenibilidad | ADR-001 | Módulos y capas con reglas de dependencia | `ModularidadTest`, `CapasDelModuloTest` |

## 2. Coherencia de campos: contrato → DTO → base de datos

Se contrastaron las rutas siguientes de `docs/04-api.md` con los `record` del paquete `dto` y con las columnas de la base de datos.

| Ruta | Campos del contrato | DTO | Columnas y restricciones que lo respaldan | ¿Coincide? |
|---|---|---|---|---|
| `POST /api/v1/citas` | `servicioId, fechaHora, modalidad, descripcionNecesidad, pais, zonaHoraria` | `DtoCitas.SolicitudCita` | `citas`: `servicio_id`, `fecha_hora`, `modalidad`, `descripcion_necesidad` (CHECK 20–500), `pais`, `zona_horaria` | Sí |
| Respuesta de `POST /citas` | `citaId, codigo, estado, fechaHoraLocal, fechaHoraColombia` | `DtoCitas.CitaCreada` | `citas.id`, `codigo`, `estado`; las dos horas salen de `fecha_hora` y `zona_horaria` | Sí |
| `POST /api/v1/proyectos` | `citaId, nombre, alcance, fechaInicio, fechaEstimadaEntrega, equipoId` | `DtoProyectos.SolicitudCrear` | `proyectos`: `cita_id`, `nombre` (5–60), `alcance` (20–600), `fecha_inicio`, `fecha_estimada_entrega` (≥ inicio + 14), `equipo_id` | Sí |
| Respuesta de `POST /proyectos` | `proyectoId, codigo, estadoPlan, fases` | `DtoProyectos.CreadoDto` | `proyectos.id`, `codigo`, `estado_plan`; `fases` = filas copiadas a `proyecto_fases` | Sí |
| `PATCH /api/v1/proyectos/{codigo}` | `nombre?, alcance?, fechaEstimadaEntrega?, equipoId?, motivo` | `DtoProyectos.SolicitudEditar` | las mismas columnas de `proyectos`; `motivo` (10–300) se guarda en la auditoría | Sí |
| `PATCH /api/v1/proyectos/{codigo}/estado` | `estado, motivo, equipoId?` | `DtoProyectos.SolicitudEstado` | `proyectos.estado`, `motivo_estado` (CHECK 10–300), `equipo_id` | Sí |
| `PATCH /api/v1/pasos/{id}` | `estado` (solo `HECHO`), `nota` | `DtoPasos.SolicitudPaso` | `pasos.estado`, `nota` (CHECK `ck_pasos_nota`); `ck_pasos_hecho` exige nota, fecha y autor | Sí |

**Alcance de esta comprobación.** Son las rutas de los casos de uso centrales (agendar, crear y gestionar el proyecto, registrar un paso). Las demás rutas del contrato (mensajes, reuniones, usuarios, dashboard) no se contrastaron campo por campo en este documento.

## 3. Validaciones que aparecen en los tres niveles

Una regla de negocio se valida en el servicio y la base de datos la repite como última barrera (CLAUDE.md, regla 4). Ejemplos verificados: nombre del proyecto 5–60 (`ck_proyectos_nombre`), alcance 20–600 (`ck_proyectos_alcance`), entrega ≥ inicio + 14 días (`ck_proyectos_fechas`), cotización de 1.000.000 a 200.000.000 COP (`ck_proyectos_valor`). Si la base rechaza un dato, `TraductorErroresBaseDatos` lo convierte en 409 o 422 con un mensaje claro, nunca en 500.
