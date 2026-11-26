# ADR-002 · Control de concurrencia: la base de datos es la última barrera

- **Estado:** Aceptada (implementada y probada con `AvisosConcurrentesTest`, `GestionCitasTest` y pruebas de proyectos).
- **Fecha:** 2026-11-18
- **RNF relacionados:** RNF-02 (integridad), RNF-04 (200 usuarios simultáneos).

## Contexto y problema

Varias operaciones pueden competir al mismo tiempo:
- Dos clientes eligen la **misma franja de cita** (RN-02) o un cliente agenda dos veces a la misma hora (RN-03).
- Dos administradoras asignan proyectos al **mismo equipo** a la vez y se pasaría del cupo de 3 proyectos activos (RN-05).
- Dos personas cambian el **mismo estado** (confirmar y cancelar una cita, aceptar y rechazar un cambio de plan).

Una validación solo en la aplicación («consulto y luego guardo») deja una ventana de tiempo en la que ambas peticiones pasan la validación.

## Alternativas evaluadas

| Alternativa | A favor | En contra |
|---|---|---|
| Validar solo en el servicio (`if` antes de guardar) | Sencillo | Tiene carrera: dos peticiones pasan el `if` a la vez |
| Bloqueo pesimista de todo (`SERIALIZABLE`) | Muy seguro | Reintentos y bloqueos innecesarios; peor rendimiento |
| Bloqueo optimista con `@Version` en todas las entidades | Sin bloqueos | Hay que reintentar en la aplicación y no cubre «unicidad entre filas» |
| **Restricciones en PostgreSQL + bloqueo puntual + `UPDATE … WHERE estado` (elegida)** | La base garantiza lo que la aplicación no puede; costo bajo | Hay que traducir los errores de la base a respuestas HTTP |

## Decisión

Se combinan tres mecanismos, cada uno para su caso:

1. **Índices únicos parciales** para las reglas de unicidad: `ux_cita_franja` (`fecha_hora` donde la cita está PENDIENTE o CONFIRMADA) y `ux_cita_cliente_hora`. El servicio hace `saveAndFlush` dentro de la transacción: si otro cliente tomó la franja, el `INSERT` falla con `23505` y `TraductorErroresBaseDatos` responde **409** con el mensaje «La hora que elegiste ya no está disponible».
2. **Bloqueo pesimista puntual** para el cupo: `EquipoConsultas.bloquear(equipoId)` ejecuta `SELECT … FOR UPDATE` sobre la fila del equipo antes de contar sus proyectos activos.
3. **Transiciones de estado atómicas:** `UPDATE … WHERE estado = <esperado>`; si no cambia ninguna fila, la respuesta es **409** «El proyecto cambió, recarga para ver lo último».

Además, la columna `proyectos.version` permite control optimista donde se necesite, y los disparadores (`fn_validar_estado_plan`, `fn_plan_en_revision`, `fn_bloquear_cambios`) impiden estados inválidos aunque falle una validación de la aplicación. El servicio valida antes de guardar solo para dar un mensaje amable en el caso normal.

## Consecuencias

**Positivas**
- La integridad no depende de que cada programador recuerde validar: aunque se salte el servicio, la base rechaza el dato (RNF-02).
- El bloqueo se aplica solo a la fila del equipo, durante la transacción corta de asignar: no frena al resto.
- Los errores llegan al usuario como 409 o 422 con texto claro, nunca como 500.

**Negativas / trade-offs**
- Parte de las reglas vive en SQL (Flyway), fuera del código Java: hay que mantener `CatalogoErroresBaseDatos` y `docs/04-api.md` sincronizados con cada restricción nueva.
- Los índices parciales y los disparadores no se pueden expresar con JPA; se documentan en el diccionario de datos.
- El cliente que pierde la carrera debe volver a elegir la franja.
