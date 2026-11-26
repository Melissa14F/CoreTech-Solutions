# ADR-001 · Monolito modular con capas internas

- **Estado:** Aceptada (confirmada en la fase de diseño; vigente en el código).
- **Fecha:** 2026-11-18
- **RNF relacionados:** RNF-02 (integridad), RNF-04 (rendimiento), RNF-05 (disponibilidad y respaldo), RNF-11 (stack), RNF-13 (correos).

## Contexto y problema

CoreTech necesita un backend que ejecute reglas de negocio con fuerte consistencia (una cita no puede duplicarse, un equipo no puede pasar de 3 proyectos activos, un plan aprobado no se modifica), con 200 usuarios simultáneos y respuestas por debajo de 500 ms (P95). El equipo es pequeño, despliega en un solo servidor y debe poder crecer por módulos (por ejemplo, un futuro módulo `pagos`). Había que elegir el estilo arquitectónico.

## Alternativas evaluadas

| Alternativa | A favor | En contra |
|---|---|---|
| **Arquitectura en capas tradicional** (controller/service/repository globales) | Simple y conocida | Con el tiempo todo depende de todo; sin fronteras entre dominios |
| **Microservicios** | Escalado independiente | Transacciones distribuidas para cosas como «crear proyecto + reservar cupo + avisar»; operación y despliegue desproporcionados para el equipo |
| **Arquitectura hexagonal pura** | Dominio aislado de la infraestructura | Mucho código de adaptadores para un dominio de este tamaño |
| **Monolito modular (elegida)** | Una transacción ACID por caso de uso; módulos por dominio con dependencias controladas | Un solo despliegue: si falla la API, falla todo |

## Decisión

Un único backend Spring Boot dividido en **módulos por dominio** (`acceso`, `agenda`, `proyectos`, `seguimiento`, `comunicacion`, `catalogo`, `especialidad`, `dashboard`, `compartido`). Dentro de cada módulo hay cinco paquetes: `controller` (controladores REST), `dto` (records de entrada y salida), `service` (servicios con las reglas), `domain` (entidades y enums) y `repository` (acceso a datos); las excepciones de negocio viven en `compartido.exception`.

Reglas de dependencia, vigiladas por pruebas automáticas:
1. Controlador → servicio → repositorio; las reglas viven en el servicio.
2. Un módulo usa a otro solo por su servicio público (puertos en la raíz del paquete, p. ej. `acceso.DirectorioUsuarios`) o por **eventos** de Spring.
3. La API responde con DTO (`record`), nunca con entidades.

`ModularidadTest` (Spring Modulith) verifica los límites entre módulos y `CapasDelModuloTest` las capas dentro de cada uno.

### Cómo responde el estilo a los RNF críticos

| RNF crítico | Cómo lo atiende el monolito modular | Dónde se ve |
|---|---|---|
| **Concurrencia** (200 usuarios simultáneos; dos clientes no pueden tomar la misma franja ni un equipo pasar de 3 proyectos) | Una sola base y una transacción ACID por caso de uso: la unicidad y los cupos los garantiza PostgreSQL (índices únicos parciales, `FOR UPDATE`, `UPDATE … WHERE estado`) | ADR-002; `ux_cita_franja`; `AvisosConcurrentesTest` |
| **Rendimiento** (P95 < 500 ms) | Los módulos se llaman en el mismo proceso, sin red entre ellos; las lecturas pesadas usan SQL propio y las fases se cargan de forma perezosa (`LAZY`, `@EntityGraph`) | ADR-004; `ProyectoRepositorio.findConFasesByCodigo` |
| **Seguridad** (RNF-01, RNF-10) | Un único punto de entrada: la cadena de filtros de `ConfiguracionSeguridad` protege todos los módulos; cada endpoint exige su permiso | ADR-003; `InventarioEndpointsTest` |
| **Mantenibilidad** | Fronteras por dominio verificadas con pruebas: un módulo solo usa a otro por su servicio público o por eventos, y dentro de cada módulo las capas tienen un sentido fijo | `ModularidadTest`, `CapasDelModuloTest` |

## Consecuencias

**Positivas**
- Una sola transacción ACID por caso de uso (RNF-02); los módulos se llaman en el mismo proceso (RNF-04).
- Un solo contenedor de API y una sola base: un respaldo cubre todo (RNF-05).
- Las fronteras se verifican con pruebas, no con acuerdos verbales.
- Si un módulo creciera, puede extraerse porque ya se comunica por puertos y eventos.

**Negativas / trade-offs**
- Un único punto de despliegue y de falla; escalar significa duplicar toda la API.
- Hay que mantener la disciplina de los puertos para no crear ciclos entre módulos (p. ej. `comunicacion` y `especialidad`); a veces exige definir una interfaz en el módulo «más abajo» y que la implemente el otro.
- El límite de intentos por IP (`FiltroLimitePeticiones`) es en memoria: sirve para un solo servidor.
