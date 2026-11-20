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
