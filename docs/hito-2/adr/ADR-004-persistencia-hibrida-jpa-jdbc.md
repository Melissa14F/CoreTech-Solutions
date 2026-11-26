# ADR-004 · Persistencia híbrida: JPA para entidades simples y JDBC para consultas con lógica de PostgreSQL

- **Estado:** Aceptada.
- **Fecha:** 2026-11-19
- **RNF relacionados:** RNF-02 (integridad), RNF-04 (rendimiento), RNF-11 (stack).

## Contexto y problema

El modelo tiene 31 tablas con 17 enums de PostgreSQL, índices parciales, columnas calculadas por disparadores (`proyectos.avance_porcentaje`), funciones SQL (`fn_dias_habiles_entre`, `fn_siguiente_codigo_cita`) y consultas con agregados para el dashboard y la lista de proyectos «en riesgo». Había que decidir cómo acceder a los datos desde Java.

## Alternativas evaluadas

| Alternativa | A favor | En contra |
|---|---|---|
| **Todo con JPA/Hibernate** | Un solo estilo; menos SQL manual | Las consultas con CTE, agregados y funciones propias se vuelven JPQL o SQL nativo difíciles de leer; riesgo de cargas innecesarias (N+1); Hibernate no entiende los disparadores y las columnas calculadas |
| **Todo con JDBC** | Control total del SQL | Mucho código repetitivo para el CRUD simple; se pierde la validación del esquema que da Hibernate |
| **Híbrido (elegida)** | Cada herramienta donde rinde | Dos estilos de acceso conviven |

## Decisión

- **JPA (Spring Data) para entidades con comportamiento propio y CRUD directo:** `Usuario`, `Rol`, `TokenRecuperacion`, `Cita`, `Servicio`, `ContenidoSitio` y `Notificacion`, con sus `JpaRepository`. Buenas prácticas aplicadas: constructor protegido y fábricas estáticas (`Usuario.nuevoCliente`), métodos de dominio que guardan sus reglas (`Cita.cancelar`, `Usuario.registrarIntentoFallido`), `@Enumerated(EnumType.STRING)` con `@JdbcType(PostgreSQLEnumJdbcType.class)`, relación `@ManyToOne(fetch = LAZY)` en `Usuario.rol`, `@Table(indexes = …)` en las tablas consultadas con frecuencia y `ddl-auto=validate` para que Hibernate compare el esquema con Flyway al arrancar.
- **JPA para el aggregate de proyectos:** `Proyecto` (alta, lectura y cambios), con `ProyectoFase` y `Paso` de lectura (`@Immutable`), relacionados con `@OneToMany(fetch = LAZY)` y `@ManyToOne(fetch = LAZY)`. `ProyectoRepositorio` crea el proyecto (`saveAndFlush`), copia la plantilla (SQL nativo `INSERT … SELECT`) y hace los cambios de datos, equipo, estado, plan y cotización (enviar, aprobar, ajustes, rechazo, vencimiento) y la garantía con consultas `@Modifying` de una sola sentencia: JPA sí puede expresar la sentencia exacta que valida un disparador (por ejemplo, reanudar cambia `estado` y `estado_plan` juntos). Los métodos de borrado están bloqueados (un proyecto se cancela, no se borra). `findConFasesByCodigo` usa `@EntityGraph`.
- **Cambios de un plan aprobado:** `CambioPlan` con `CambioPlanRepositorio`. Cada cambio de estado (autorizar, rechazar, aceptar) es una consulta `UPDATE … WHERE estado = <esperado>` que devuelve si cambió una fila: si dos personas responden a la vez solo una gana y la otra recibe 409. Las lecturas de los cambios con seis JOIN (proyecto, cliente, fase, paso, solicitud) siguen en `CambiosPlanJdbc`.
- **También en JPA:** `SesionCerrada` y `CuentaExterna` (esta con `@ManyToOne(fetch = LAZY)` a `Usuario`), que antes eran clases JDBC.
- **JDBC (`JdbcTemplate`, clases `*Jdbc`) todavía en:** la lista y el detalle de proyectos con «en riesgo» (funciones SQL y agregados), las fases, los pasos y las respuestas del plan, las lecturas de los cambios del plan, el seguimiento, el dashboard, los mensajes y la auditoría. Los valores siempre van como parámetros enlazados (solo se concatenan fragmentos fijos del SQL), y con casts explícitos a los enums.
- Las relaciones entre módulos se guardan como `UUID` y no como `@ManyToOne` a entidades de otro módulo, para no violar la regla «un módulo no importa las entidades de otro» (ADR-001). Dentro de un mismo módulo se usa la relación `LAZY` cuando hace falta (`Usuario → Rol`).
- Las entidades JPA **nunca salen del módulo**: la API responde `record` DTO.

### Convención para elegir (comandos y consultas)

| Qué es | Cómo se accede | Nombre |
|---|---|---|
| Escribir, o leer un agregado con sus reglas (crear, cambiar estado, proponer, aceptar) | Spring Data JPA: entidad en `domain` y `JpaRepository` en `repository` | `<Entidad>Repositorio` |
| Cambios de estado que un disparador valida sentencia por sentencia | `@Modifying @Query` de una sola sentencia, condicionada por el estado esperado (`UPDATE … WHERE estado = <esperado>`) | método del `*Repositorio` |
| Leer varias tablas a la vez, agregados o funciones de PostgreSQL (listas, dashboard, vistas con JOIN entre módulos) | `JdbcTemplate` con un `record` de solo lectura | `<Tema>Jdbc` |

Las firmas de los repositorios usan los enums del dominio (`EstadoProyecto`, `EstadoPlan`, `TipoCambioPlan`, `EstadoCambioPlan`), no texto: el compilador revisa los estados. La conversión desde el texto de la API se hace en el servicio, en el borde.

## Consecuencias

**Positivas**
- Las consultas complejas son SQL legible y se prueban contra PostgreSQL real con Testcontainers.
- El CRUD simple no tiene código repetitivo.
- `validate` detecta al arrancar si una entidad no coincide con el esquema.

**Negativas / trade-offs**
- Dos estilos de acceso: quien llega al proyecto debe saber cuándo usar cada uno (la regla práctica: si la tabla tiene entidad y no hay lógica SQL especial, JPA; si no, JDBC).
- Solo 13 de las 31 tablas son `@Entity`; las demás están documentadas en el diccionario de datos pero no tienen clase de entidad. Son una deuda conocida: se migrarían a JPA tabla por tabla, con la misma red de pruebas.
- `ProyectoRepositorio` mezcla consultas JPQL y SQL nativo (la copia de la plantilla). Es deliberado: un `INSERT … SELECT` entre tablas no se expresa bien con entidades.
- Los índices parciales y funcionales no se pueden declarar con `@Index`; viven en las migraciones de Flyway.
