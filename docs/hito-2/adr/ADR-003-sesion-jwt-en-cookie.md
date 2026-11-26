# ADR-003 · Sesión con JWT en cookie HttpOnly y versión de sesión

- **Estado:** Aceptada (implementada en P06 y reforzada en P24).
- **Fecha:** 2026-11-19
- **RNF relacionados:** RNF-01 (seguridad por roles), RNF-10 (sesión y contraseñas).

## Contexto y problema

El portal tiene tres roles y datos privados de empresas. Hace falta una sesión que: (a) el JavaScript de la SPA no pueda robar si hay un fallo XSS, (b) se pueda cerrar de verdad al hacer «Cerrar sesión» o al cambiar la contraseña, (c) no requiera guardar estado de sesión en memoria del servidor para poder reiniciar la API sin sacar a nadie.

## Alternativas evaluadas

| Alternativa | A favor | En contra |
|---|---|---|
| Sesión de servidor (`JSESSIONID`) | Simple, revocable | Estado en memoria: se pierde al reiniciar; complica varias instancias |
| JWT en `localStorage` + cabecera `Authorization` | Muy común | Un XSS puede leer el token y robarlo |
| JWT sin forma de revocarlo | Sin estado | Un token robado vale hasta que vence |
| **JWT firmado en cookie `HttpOnly; Secure; SameSite=Strict` + versión de sesión + lista de cierres (elegida)** | JS no lo lee; revocable; sin estado en memoria | Exige proteger contra CSRF y consultar la base en cada petición |

## Decisión

- El token (id del usuario, `version_sesion`, «Recordarme» y `jti`) viaja en la cookie `sesion`: `HttpOnly`, `Secure`, `SameSite=Strict`; dura 8 h o 30 días con «Recordarme». La clave de firma sale de `CORETECH_SESION_SECRETO`.
- **Revocación:** el token deja de valer si venció, si su `jti` está en `sesiones_cerradas` (cierre de sesión), o si `usuarios.version_sesion` cambió (cambio o restablecimiento de contraseña, anonimización). `FiltroSesion` lee al usuario de la base en cada petición, así que desactivar una cuenta corta su sesión de inmediato.
- **CSRF:** `SameSite=Strict` más `FiltroOrigen`, que rechaza POST/PUT/PATCH/DELETE con un `Origin` no permitido.
- **Autorización por permiso, no por rol:** cada endpoint lleva `@PreAuthorize("@permisos.tiene('citas.agendar')")`; la matriz está en la tabla `permisos`. `InventarioEndpointsTest` falla si un endpoint nuevo no declara su permiso.
- **Contraseñas:** BCrypt (costo 10), política RN-22, bloqueo de 15 minutos tras 5 intentos fallidos; límite de intentos por IP (429).

## Consecuencias

**Positivas**
- Un XSS no puede leer la sesión; el cierre de sesión y el cambio de contraseña sí invalidan los tokens.
- Los permisos se cambian en caliente (se leen de la base) sin redesplegar.
- La API puede reiniciarse sin cerrar sesiones: el estado vive en la base, no en memoria.

**Negativas / trade-offs**
- Una consulta a la base por petición para cargar al usuario (compensada por el pool de conexiones y el índice de la PK).
- `SameSite=Strict` obliga a gestionar el regreso de OAuth (Google/Gmail) con un «estado» de un solo uso guardado en memoria, que se pierde si la API se reinicia a mitad del flujo.
- La lista `sesiones_cerradas` crece y requiere limpieza de filas vencidas (se hace al cerrar otra sesión).
