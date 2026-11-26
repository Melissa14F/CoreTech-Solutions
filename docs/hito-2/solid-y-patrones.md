# Diseño limpio: SOLID y patrones de diseño en el código de CoreTech

Rutas relativas a `backend/src/main/java/co/coretech/portal/`. Los fragmentos son del código real.

## 1. Separación estricta por capas y paquetes

Los módulos con reglas de negocio y datos propios (`acceso`, `agenda`, `proyectos`, `seguimiento`, `comunicacion`, `catalogo`, `especialidad`) tienen los cinco paquetes, con los nombres que exige el parcial (los nombres de las clases siguen en español). `dashboard` es de solo lectura: tiene `controller`, `service` y `repository`, sin `domain` ni `dto` propios (decisión D-17). Las excepciones de negocio viven en `compartido.exception` y las comparten todos los módulos:

| Paquete | Contenido |
|---|---|
| `controller/` | `ControladorCitas` (REST, `@PreAuthorize`) |
| `dto/` | `DtoCitas` (records de entrada y salida) |
| `service/` | `ServicioCitas`, `ServicioDisponibilidad` |
| `repository/` | `CitaRepositorio`, clases `*Jdbc` |
| `domain/` | `Cita`, `EstadoCita`, `Modalidad` |
| `compartido/exception/` | `NoEncontradoException`, `ConflictoException`, `ReglaNegocioException`, `ProhibidoException`… (compartidas por todos los módulos) |

Las reglas no son una promesa: `CapasDelModuloTest` (capas dentro del módulo) y `ModularidadTest` (Spring Modulith, límites entre módulos) fallan si alguien las rompe. Un controlador nunca usa un repositorio y un módulo nunca importa las entidades de otro.

## 2. SOLID

### S · Responsabilidad única
Cada clase hace una cosa:
- `ServicioDisponibilidad` solo calcula franjas; `ServicioCitas` solo agenda, lista y cancela.
- `CorreosDeAcceso` solo arma los correos del módulo acceso; `ServidorCorreoEnrutado` solo decide por dónde sale el correo.
- Las validaciones puras (sin base de datos ni sesión) se separan del flujo en clases `Reglas<Recurso>` de funciones estáticas: `ReglasProyecto`, `ReglasPlan` y `ReglasCambioPlan` (módulo proyectos) y `ReglasFase` (módulo seguimiento). El servicio conserva el flujo, los permisos y los avisos; la regla se puede probar sola.
- Las utilidades de texto que antes tenía cada servicio por su cuenta (`recortar`, `limpio`, `sinTildes`) viven ahora en una sola clase, `compartido.service.Textos`: una regla, un solo lugar.

### O · Abierto/cerrado
El tratamiento de errores se amplía sin tocar a los servicios: un nuevo tipo de error de negocio se agrega como subclase de `ExcepcionNegocio` y se traduce en el único manejador de esa jerarquía (`@ExceptionHandler(ExcepcionNegocio.class)`); los servicios no cambian. Los servicios solo lanzan excepciones de negocio:

```java
// agenda/service/ServicioCitas.java
if (citas.franjaOcupada(inicio)) {
    throw new ConflictoException("fechaHora", MENSAJE_FRANJA_OCUPADA);
}
```

### L · Sustitución de Liskov
`NoEncontradoException`, `ProhibidoException`, `ConflictoException`, `ReglaNegocioException` y `CuentaBloqueadaException` extienden `ExcepcionNegocio` y se pueden tratar de forma uniforme; cada una puede sustituirse donde se espera la base sin romper el contrato (mensaje de texto simple).

### I · Segregación de interfaces
Los módulos se comunican por **puertos pequeños**, de una sola operación o de un tema:
- `CorreoSaliente` tiene un único método `enviar(para, asunto, texto)`.
- `acceso.CitasDeUsuarios`, `ProyectosDeUsuarios` y `EquiposDeUsuarios` son tres interfaces separadas en vez de una «interfaz de actividad» gigante.
- `catalogo.DirectorioServicios` solo expone lo que agenda necesita (`nombreSiVisible`, `nombres`).

### D · Inversión de dependencias
El servicio depende de abstracciones, y la infraestructura las implementa:

```java
// compartido/service/CorreoSaliente.java  (abstracción)
public interface CorreoSaliente {
    void enviar(String para, String asunto, String texto);
}
// compartido/service/CorreoSalienteSmtp.java  (implementación)
class CorreoSalienteSmtp implements CorreoSaliente { ... }
```

Otros ejemplos: `ClienteGoogle` / `ClienteGoogleHttp`, `ClienteIdentidadExterna` / `ClienteIdentidadExternaHttp`, `AlmacenArchivos` / `AlmacenArchivosDisco`. En las pruebas se sustituyen por simulacros (`@MockitoBean ClienteGoogle`) sin tocar el servicio.

### Inyección de dependencias por constructor
Todas las dependencias son `private final` y se reciben por constructor; no se usa inyección por campo:

```java
// agenda/service/ServicioCitas.java
private final CitaRepositorio citas;
private final DirectorioServicios catalogo;
private final ServicioDisponibilidad disponibilidad;
private final RegistroAuditoria auditoria;
private final ApplicationEventPublisher eventos;
private final Clock reloj;

public ServicioCitas(CitaRepositorio citas, DirectorioServicios catalogo, ServicioDisponibilidad disponibilidad,
        RegistroAuditoria auditoria, ApplicationEventPublisher eventos, Clock reloj) { ... }
```

Incluso el reloj (`Clock`) se inyecta: las pruebas fijan la fecha sin trucos.

## 3. Patrones de diseño aplicados explícitamente

| Patrón | Dónde | Evidencia |
|---|---|---|
| **DTO** | `*/dto/Dto*.java` | Records inmutables; la API nunca devuelve entidades (`DtoCitas.CitaCreada`, `DtoCitas.SolicitudCita`). |
| **Repository** | `*/repository/` | `CitaRepositorio extends JpaRepository<Cita, UUID>` y las clases `*Jdbc` esconden el acceso a datos tras una interfaz del módulo. |
| **Observer / EventListener** | `comunicacion/AvisoSolicitado`, `acceso/service/CorreosDeAcceso` | El servicio publica un evento (`eventos.publishEvent(AvisoSolicitado.para(...))`) y quienes lo escuchan reaccionan sin que el servicio los conozca. |
| **Adapter / Strategy (puertos)** | `CorreoSaliente` → `CorreoSalienteSmtp`; `ServidorCorreoEnrutado` | Gmail o SMTP según la configuración, con la misma interfaz. |
| **Factory method** | `Usuario.nuevoCliente(...)`, `Usuario.nuevoPersonal(...)`, `Cita.nueva(...)`, `Servicio.nuevo(...)` | Constructores protegidos: una entidad solo nace válida (id generado, estado inicial, fechas). |
| **Rich domain model** | `Cita.cancelar/confirmar/atender`, `Usuario.registrarIntentoFallido` | El estado cambia solo con métodos que expresan la regla, no con setters. |

### Observer: ejemplo completo
```java
// agenda/service/ServicioCitas.java (publica)
eventos.publishEvent(AvisoSolicitado.para(clienteId, "CITA_REGISTRADA",
        "Registramos tu cita " + cita.getCodigo(), "...", "/cliente/citas"));

// acceso/service/CorreosDeAcceso.java (escucha, solo si la transacción se confirmó)
@TransactionalEventListener
void enlaceRecuperacion(EnlaceRecuperacionEmitido evento) {
    correo.enviar(evento.email(), "Restablece tu contraseña de CoreTech", "...");
}
```
El aviso se guarda dentro de la misma transacción (`BEFORE_COMMIT`) y el correo sale en segundo plano; si la transacción se revierte, no se avisa de algo que no ocurrió.

## 4. Manejo centralizado de errores (`@RestControllerAdvice`)

`compartido/controller/ManejadorGlobalErrores.java` es el único lugar donde se decide el código HTTP:

| Origen | Respuesta |
|---|---|
| Validación de datos (`MethodArgumentNotValidException`, `ValidacionException`, `ReglaNegocioException`) | **422** `{ "errores": [{ "campo", "mensaje" }] }` |
| `NoEncontradoException` (también el recurso ajeno de un cliente) | **404** |
| `ProhibidoException` / falta de permiso | **403** (queda en la auditoría) |
| `ConflictoException`, violación de unicidad (`23505`), disparador (`P0001`) | **409** |
| `CuentaBloqueadaException` | **423** |
| Cualquier otro error | **500** con mensaje genérico; el detalle solo va al log |

`TraductorErroresBaseDatos` + `CatalogoErroresBaseDatos` convierten cada restricción de PostgreSQL (`ux_cita_franja`, etc.) en un mensaje claro: el usuario nunca ve texto técnico.

## 5. Pruebas que sostienen el diseño

`ModularidadTest`, `CapasDelModuloTest`, `InventarioEndpointsTest` (todo endpoint pide su permiso), `GestionCitasTest`, `AvisosConcurrentesTest` (diez peticiones simultáneas sin perder avisos) y las pruebas de integración con PostgreSQL real vía Testcontainers.
