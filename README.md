# CoreTech Solutions · Prototipo de interfaces (Taller 9)

Prototipo de alta fidelidad en HTML, CSS y JavaScript simple (Opción B:
maquetación web estática). La navegación entre pantallas se hace con
hipervínculos `<a>` entre los archivos `.html`.

## Cómo abrirlo

Descomprimir la carpeta y abrir `index.html` con doble clic en el navegador.
No necesita servidor ni instalación.

## Probar el prototipo

En `login.html` se puede entrar de dos formas:

- Escribiendo cualquier correo y contraseña y presionando **Ingresar**
  (entra como la cliente Laura Gómez).
- Con los **accesos de demostración** de cada rol: cliente, desarrollador y
  administradora.

`mapa.html` lista las 36 interfaces con un enlace a cada una y los flujos
principales. `guia-estilos.html` muestra la paleta, la tipografía y todos los
componentes. Ambas se abren desde el pie de página.

## Diseño adaptable

Todas las páginas se probaron en 8 anchos: 320, 375, 768, 1024, 1280, 1440,
1920 y 2560 px, sin desplazamiento horizontal ni errores.

- **Celular (hasta 640 px):** una columna, tablas convertidas en tarjetas y menú
  lateral del panel como menú desplegable.
- **Tableta y portátil (641 a 1599 px):** rejillas de 2 a 4 columnas.
- **Pantallas grandes (1600 px o más):** el contenido crece (hasta 1440 o 1600 px)
  y se centra; desde 2400 px el texto base sube a 18 px.

## Estructura de carpetas

```
coretech-prototipo/
├── index.html              Página principal y catálogo de servicios
├── registro.html           Creación de cuenta del cliente
├── legal.html              Política de datos y términos y condiciones
├── login.html              Inicio de sesión
├── cambiar-contrasena.html Primer ingreso: cambiar la contraseña temporal (HU-06, RF-05)
├── mapa.html               Mapa del sitio: las 36 interfaces y los flujos principales
├── guia-estilos.html       Guía de estilos: colores, tipografía y componentes
├── cliente/                Pantallas del cliente
├── desarrollador/          Pantallas del desarrollador (inicio: proyectos.html)
├── admin/                  Pantallas del administrador (inicio: dashboard.html)
├── css/
│   ├── global.css          Variables, base y componentes compartidos
│   ├── plantillas/         Estructuras que usan varias vistas
│   │   ├── documento.css   Portada e índice de mapa.html y guia-estilos.html
│   │   ├── acceso.css      Registro e inicio de sesión
│   │   ├── panel.css       Pantallas internas (menú lateral y barra superior)
│   │   ├── agendamiento.css Los pasos para agendar una cita
│   │   ├── linea-tiempo.css Línea de tiempo de las fases del proyecto
│   │   ├── proyecto.css    Encabezado y tarjetas laterales de un proyecto
│   │   ├── tabla.css       Tablas de gestión del administrador (tarjetas en móvil)
│   │   ├── tarjeta-servicio.css Tarjeta del catálogo (carrusel y vista previa del editor)
│   │   └── tarjetas.css    Cifras, tarjetas, barra de carga y selector de desarrollador
│   └── vistas/             Un archivo por vista
│       ├── inicio.css      index.html
│       ├── legal.css       legal.html
│       ├── mapa.css        mapa.html
│       ├── guia-estilos.css guia-estilos.html
│       ├── login.css       login.html
│       ├── agendar-servicio.css  cliente/agendar-servicio.html
│       ├── agendar-fecha.css     cliente/agendar-fecha.html
│       ├── agendar-confirmar.css cliente/agendar-confirmar.html
│       ├── cita-registrada.css   cliente/cita-registrada.html
│       ├── mis-citas.css         cliente/mis-citas.html
│       ├── mis-proyectos.css     cliente/mis-proyectos.html
│       ├── proyecto-detalle.css  cliente/proyecto-*.html
│       ├── proyecto-no-encontrado.css cliente/proyecto-no-encontrado.html
│       ├── desarrollador-proyectos.css desarrollador/proyectos.html
│       ├── admin-dashboard.css   admin/dashboard.html
│       ├── admin-crear-proyecto.css admin/crear-proyecto.html
│       ├── admin-proyectos.css   admin/proyectos.html
│       ├── admin-usuarios.css    admin/usuarios.html
│       ├── admin-desarrolladores.css admin/desarrolladores.html
│       ├── admin-equipos.css     admin/equipos.html
│       ├── admin-roles-permisos.css admin/roles-permisos.html
│       ├── admin-servicios.css   admin/servicios.html
│       ├── admin-contenido.css   admin/contenido.html
│       ├── admin-solicitudes.css admin/solicitudes.html
│       ├── admin-auditoria.css   admin/auditoria.html
│       ├── citas-reuniones.css   desarrollador/citas-reuniones.html
│       ├── registrar-avance.css  desarrollador/registrar-avance-*.html
│       ├── plan-trabajo.css      plantillas de fases, planes de trabajo y cambios de plan
│       └── registro.css    registro.html
├── js/
│   ├── principal.js        Animaciones, menú, mensajes y validaciones
│   ├── carrusel.js         Carrusel de servicios
│   ├── accesibilidad.js    Panel de accesibilidad
│   ├── tabla.js            Filtros, búsqueda, conteos y menú "Más acciones" de las tablas
│   ├── cuestionario.js     Ver el cuestionario de una cita (equipo y administrador)
│   ├── plan-editor.js      Editor de fases y pasos (plantillas y plan borrador)
│   └── vistas/             Comportamiento propio de cada vista
│       ├── agendar-fecha.js   País y zona horaria; calendario y horarios en la hora del cliente
│       ├── datos-cita.js      Muestra la cita en la confirmación y en el éxito
│       ├── mis-citas.js       Filtros y cancelación de citas
│       ├── proyecto-detalle.js Formulario para solicitar reunión
│       ├── citas-reuniones.js  Pestañas; confirmar, cuestionario de la cita, programar y rechazar
│       ├── admin-citas.js      Asignar, reasignar y cancelar citas
│       ├── admin-crear-proyecto.js Cita de origen, fechas y resumen en vivo
│       ├── admin-proyectos.js  Editar, reasignar, pausar, reanudar y cancelar
│       ├── admin-usuarios.js   Cuentas: crear administradores, editar, activar y desactivar
│       ├── admin-desarrolladores.js Registro de desarrolladores con su especialidad
│       ├── admin-equipos.js    Armar equipos: Product Owner con cuenta e integrantes con rol
│       ├── admin-roles-permisos.js Matriz de permisos con cambios sin guardar
│       ├── admin-servicios.js  Crear, editar, ordenar, ocultar y eliminar servicios
│       ├── admin-contenido.js  Textos de la página principal con vista previa
│       ├── admin-solicitudes.js Programar, rechazar o cancelar reuniones
│       ├── admin-auditoria.js  Exportar a CSV los eventos filtrados
│       ├── guia-estilos.js     Ejemplos interactivos de la guía
│       ├── registrar-avance.js Pasos hechos, correcciones y entrega de fases
│       ├── plantillas-fases.js Plantilla de fases de cada servicio
│       ├── plan-de-trabajo.js  Plan borrador y cotización; envío al cliente
│       ├── plan-cliente.js     Aprobar el plan o pedir ajustes
│       ├── cambio-fases.js     Proponer, autorizar y aceptar cambios de plan
│       └── galeria-bocetos.js  Bocetos de las pantallas en grande
└── img/
    ├── logo.svg            Logo (temporal, reemplazable)
    └── servicios/          Imágenes del catálogo (temporales, reemplazables)
```

## Proyectos de ejemplo

- **P-0031 · Tienda en línea Laura** (Andrés Pérez): en Desarrollo, con un cambio de plan
  (agregar una fase) esperando que Laura lo acepte.
- **P-0034 · App de reservas** (Andrés Pérez): fase 1 entregada, esperando la aprobación de Laura.
- **P-0035 · Inventario del salón** (Andrés Pérez): plan y cotización enviados; Laura responde
  hasta el martes 17 nov y la propuesta vence el jueves 26 nov.
- **P-0036 · Web corporativa Duarte Asesores** (Camila Jaimes): lo crea el administrador en
  `admin/crear-proyecto.html` y nace con el plan en borrador (`admin/plan-de-trabajo-web-duarte.html`).
- **P-0024 y P-0027** (María Ruiz): cambios de plan por autorizar en `admin/proyectos.html`
  (cambiar la fecha de entrega y agregar una fase).

## Reemplazar imágenes

- **Logo:** reemplazar `img/logo.svg` por otro archivo con el mismo nombre.
- **Servicios:** reemplazar los archivos de `img/servicios/` conservando el
  nombre y la proporción 3:2 (recomendado 1200 × 800 px, formato WebP).

## Organización del CSS

Cada vista carga primero `css/global.css`, luego su plantilla (si tiene) y al
final su propio archivo de `css/vistas/`. Ejemplo en `registro.html`:

```html
<link rel="stylesheet" href="css/global.css">
<link rel="stylesheet" href="css/plantillas/acceso.css">
<link rel="stylesheet" href="css/vistas/registro.css">
```

Lo que se repite en varias vistas (colores, botones, formularios, alertas,
panel de accesibilidad, ventanas de diálogo con formulario) vive solo en
`global.css`, para no duplicar código.
Si una vista no necesita estilos propios, solo carga sus plantillas
(por ejemplo `admin/citas.html` usa únicamente `plantillas/tabla.css`).

## Convenciones del código

- Clases CSS en español con BEM: `bloque__elemento--modificador`.
- Estados con el prefijo `esta-` (`esta-visible`, `esta-abierto`).
- Ajustes de accesibilidad con el prefijo `modo-`, aplicados sobre `<html>`.
- Archivos en minúscula y con guiones (`mis-proyectos.html`).

## Código de la aplicación

Además de las pantallas estáticas, esta carpeta guarda el código del portal:

- `backend/`: API en Java 17 + Spring Boot (monolito modular). Se prueba con `cd backend && ./mvnw verify` (necesita Docker Desktop abierto).
- `frontend/`: SPA en React 18 + Vite + TypeScript. En esa carpeta: `npm install` (una vez), `npm run dev` (http://localhost:5173; la API debe correr en 8080), `npm test` y `npm run build`.

Las pantallas `.html`, `css/`, `js/` e `img/` siguen siendo la referencia visual de la SPA.

### Pruebas y revisiones

| Qué | Comando (desde `prototipo/`) | Notas |
|---|---|---|
| Backend (JUnit + Testcontainers) | `cd backend && ./mvnw verify` | Necesita Docker Desktop abierto. |
| Cobertura del backend | `cd backend && ./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report` | Informe en `backend/target/site/jacoco`. |
| Frontend (Vitest) | `cd frontend && npm test` | En Windows, desde PowerShell. |
| Punta a punta y accesibilidad (Playwright + axe) | `cd frontend && npx playwright test` | Necesita `docker compose up -d`; crea su propia base `coretech_e2e`. Tarda unos 10 minutos. |
| Carga (k6) | ver `carga/README.md` | La API se prueba desde el jar, no con `spring-boot:run`. |
| Dependencias del frontend | `cd frontend && npm audit` | |

Las reglas, los requerimientos no funcionales y su resultado están en `docs/PROGRESO.md` (solo en el repositorio de trabajo; esa carpeta no forma parte de `prototipo/`).

## Cómo levantar todo en desarrollo

Necesitas JDK 17, Node.js 24 y Docker Desktop abierto. Todos los comandos se ejecutan desde esta carpeta (`prototipo/`).

1. **Base de datos y correo** (PostgreSQL 15 y Mailpit):

   ```
   docker compose up -d
   ```

2. **API** (Spring Boot, perfil `dev`, puerto 8080). Al arrancar, Flyway crea el esquema y carga los datos de demostración:

   ```
   cd backend
   ./mvnw spring-boot:run
   ```

   En Windows con PowerShell usa `.\mvnw.cmd spring-boot:run`. Swagger: http://localhost:8080/swagger-ui.html

3. **Frontend** (en otra terminal, puerto 5173; `/api` va a la API por el proxy de Vite):

   ```
   cd frontend
   npm install
   npm run dev
   ```

   Abre http://localhost:5173

4. **Correos de prueba:** los que envía la API llegan a la bandeja de Mailpit, http://localhost:8025

### Cuentas de demostración

Todas con la contraseña **`Demo2026!`** (solo existen en el perfil `dev`):

| Rol | Persona | Correo |
|---|---|---|
| Cliente | Laura Gómez | `laura.gomez@correo.com` |
| Desarrollador (Product Owner) | Andrés Pérez | `andres.perez@coretechsolutions.co` |
| Administradora | Patricia León | `patricia.leon@coretechsolutions.co` |

También están los demás usuarios, desarrolladores y equipos de `admin/usuarios.html`, `admin/desarrolladores.html` y `admin/equipos.html`; los festivos de Colombia de 2026 y 2027; las citas C-0045, C-0051, C-0058, C-0061 y C-0062, y los proyectos de Laura **P-0031** (tienda en línea, 40 %, fase 3 en curso) y **P-0035** (inventario del salón, plan y cotización por aprobar). Los datos están en `backend/src/main/resources/db/demo/R__datos_demo.sql`.

### Empezar desde cero

Para borrar la base y volver a cargar todo (por ejemplo, después de cambiar los datos de demostración):

```
docker compose down -v
docker compose up -d
```

y vuelve a arrancar la API.

---

# Despliegue con Docker y HTTPS (P25)

Producción y pruebas locales usan `docker-compose.prod.yml`: **nginx** (SPA + HTTPS), **api** (Spring Boot, perfil
`prod`), **db** (PostgreSQL 15) y un volumen para los archivos subidos. Solo Nginx abre puertos (80 y 443); la API y la
base quedan en la red interna. Todos los servicios se reinician solos y tienen healthcheck. La base nace vacía: sin
datos de demostración (el administrador se crea según la sección «Primer administrador»).

## 1. Variables (`prototipo/.env`)

Copia `.env.example` como `.env` (nunca se sube a git) y llena, como mínimo:

| Variable | Para qué |
|---|---|
| `CORETECH_DB_CLAVE` | Contraseña de PostgreSQL (se fija la primera vez que se crea el volumen) |
| `CORETECH_SESION_SECRETO` | Clave del JWT, mínimo 32 caracteres |
| `CORETECH_CIFRADO_CLAVE` | Cifra el permiso de Gmail en la base, mínimo 32 caracteres. **No la cambies** después de conectar Gmail |
| `CORETECH_URL_PORTAL` | Dirección pública, por ejemplo `https://tu-dominio` (por omisión `https://localhost`) |
| `CORETECH_GOOGLE_ID`, `CORETECH_GOOGLE_SECRETO` | Cliente OAuth de Google (ver «Correos con Gmail») |

Genera claves largas con `openssl rand -base64 36`.

## 2. Certificado y arranque

```powershell
bash scripts/certificado-local.sh            # solo para probar: certificado autofirmado para localhost
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps  # los tres servicios deben decir "healthy"
```

Abre https://localhost (el navegador avisará que el certificado no es de confianza: es normal con el autofirmado).
En un servidor real, copia tu certificado a `nginx/certs/fullchain.pem` y `nginx/certs/privkey.pem` (por ejemplo
con Let's Encrypt) y reinicia Nginx. Nginx acepta TLS 1.2 y 1.3, redirige HTTP a HTTPS y manda los encabezados de
seguridad (HSTS, CSP…). `nginx/certs/` está en `.gitignore`.

Para detener: `docker compose -f docker-compose.prod.yml down` (los volúmenes se conservan; con `-v` se borra todo).

## 3. Primer administrador

La base de producción no trae cuentas. Crea el primer administrador una sola vez; el script muestra una contraseña
temporal que el portal obliga a cambiar al ingresar. Las demás cuentas se crean después en «Usuarios».

```powershell
bash scripts/crear-administrador.sh "Nombre Apellido" correo@dominio.com 3001234567
```

## 4. Correos con Gmail (D-51)

En producción no hay SMTP: los correos salen por la cuenta que el administrador conecta en «Correo del sistema».

1. En Google Cloud Console crea un proyecto → **APIs y servicios** → habilita **Gmail API**.
2. **Pantalla de consentimiento OAuth**: tipo externo, con la página principal del portal y la política de privacidad
   (`https://<dominio>/legal#datos`). Publica la app **«En producción»** (en «Prueba» el permiso vence a los 7 días).
3. **Credenciales** → ID de cliente OAuth → aplicación web. URI de redirección autorizadas:
   - `https://<dominio>/api/v1/correo-sistema/gmail/callback`
   - `https://<dominio>/api/v1/auth/externo/callback/google` (ingreso de clientes con Google)
4. Copia el ID y el secreto a `.env` (`CORETECH_GOOGLE_ID`, `CORETECH_GOOGLE_SECRETO`) y reinicia la API:
   `docker compose -f docker-compose.prod.yml up -d`.
5. Ingresa como administrador → **Correo del sistema** → «Conectar con Gmail» y acepta con la cuenta del proyecto.
   El portal solo recibe permiso para enviar; nunca la contraseña.
6. Comprueba que el correo de restablecer contraseña y el de una cita lleguen al buzón real (revisa spam).

Microsoft (opcional): registro de aplicación en Azure con la URI `https://<dominio>/api/v1/auth/externo/callback/microsoft`
y las variables `CORETECH_MICROSOFT_ID` y `CORETECH_MICROSOFT_SECRETO`.

## 5. Respaldar y restaurar

Los scripts de `scripts/` usan el compose de desarrollo por omisión; para producción indícales el archivo y el volumen:

```powershell
$env:COMPOSE_FILE = "docker-compose.prod.yml"
$env:CORETECH_ARCHIVOS_VOLUMEN = "coretech-prod_archivos"
bash scripts/respaldo.sh      # crea respaldos/AAAA-MM-DD_HHMMSS/ (base + archivos + SHA256SUMS)
bash scripts/restaurar.sh respaldos/AAAA-MM-DD_HHMMSS   # restaura en una base NUEVA <base>_restaurada
```

El respaldo se puede programar cada día (instrucciones en la cabecera de `scripts/respaldo.sh`). Copia `respaldos/` a
otra máquina: si el disco falla, el respaldo local se pierde con él. La restauración se niega a pisar una base con datos
salvo con `REEMPLAZAR=si`.

## 6. Qué trae el perfil `prod`

Cookie `Secure`, Swagger apagado, sin datos demo, sin detalles de error, IP real desde `X-Forwarded-For` (que Nginx
sobrescribe) y registros en formato JSON (`docker compose -f docker-compose.prod.yml logs api`).
