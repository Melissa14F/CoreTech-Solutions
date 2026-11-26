// Prueba de carga de RNF-04: 200 usuarios simultáneos y P95 < 500 ms en lo más usado del portal.
//
//   · 120 clientes agendan citas: ven los bloques libres (GET disponibilidad), agendan (POST /citas) y cancelan para liberar el bloque.
//   ·  60 clientes abren el detalle de su proyecto (GET /proyectos/{codigo}).
//   ·  20 Product Owners registran pasos (PATCH /pasos/{id}); son 120 pasos en total, uno por iteración, y cada paso se marca una sola vez.
// Todos con su propia sesión (login real, cookie HttpOnly) y con pausas de 1 a 3 s entre acciones, como una persona.
//
// Primero se siembran los datos (node sembrar.mjs) y se enciende la API de pruebas (puerto 8090). Después, desde prototipo/carga:
//   docker run --rm -v "${PWD}:/carga" -e BASE_URL=http://host.docker.internal:8090 grafana/k6 run --summary-export=/carga/resultado.json /carga/carga.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const datos = JSON.parse(open('./datos.json'));
const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8090';
const ORIGEN = __ENV.ORIGEN || 'http://localhost:5174'; // el origen que acepta el perfil e2e (FiltroOrigen)
const PICO_AGENDAR = Number(__ENV.VUS_AGENDAR || 120);
const PICO_DETALLE = Number(__ENV.VUS_DETALLE || 60);
const VUS_REGISTRAR = Number(__ENV.VUS_REGISTRAR || 20);
const MANTENER = __ENV.MANTENER || '90s';

const cabeceras = { 'Content-Type': 'application/json', Origin: ORIGEN };

export const options = {
  // Cada usuario virtual conserva su sesión (la cookie HttpOnly) entre iteraciones, como un navegador abierto
  noCookiesReset: true,
  scenarios: {
    agendar: {
      executor: 'ramping-vus', exec: 'agendar', startVUs: 0, gracefulRampDown: '10s',
      stages: [{ duration: '20s', target: PICO_AGENDAR }, { duration: MANTENER, target: PICO_AGENDAR }, { duration: '5s', target: 0 }],
    },
    detalle: {
      executor: 'ramping-vus', exec: 'detalle', startVUs: 0, gracefulRampDown: '10s',
      stages: [{ duration: '20s', target: PICO_DETALLE }, { duration: MANTENER, target: PICO_DETALLE }, { duration: '5s', target: 0 }],
    },
    // Entran cuando ya están los otros 180 (y la rampa terminó): los 200 trabajan a la vez.
    // Cada paso se marca una sola vez: con VUS_REGISTRAR=0 (o sin pasos nuevos) este escenario se omite.
    ...(VUS_REGISTRAR > 0 ? {
      registrar: { executor: 'per-vu-iterations', exec: 'registrar', vus: VUS_REGISTRAR, iterations: 12, startTime: '25s', maxDuration: '5m' },
    } : {}),
  },
  thresholds: {
    // RNF-04: P95 < 500 ms en agendar, abrir el detalle de un proyecto y registrar un paso
    'http_req_duration{endpoint:crear_cita}': ['p(95)<500'],
    'http_req_duration{endpoint:disponibilidad}': ['p(95)<500'],
    'http_req_duration{endpoint:detalle_proyecto}': ['p(95)<500'],
    'http_req_duration{endpoint:registrar_paso}': ['p(95)<500'],
    // Y sin errores inesperados (un 409 de «bloque ocupado» al agendar es una respuesta correcta, no un error)
    'http_req_failed{endpoint:crear_cita}': ['rate<0.01'],
    'http_req_failed{endpoint:disponibilidad}': ['rate<0.01'],
    'http_req_failed{endpoint:detalle_proyecto}': ['rate<0.01'],
    'http_req_failed{endpoint:registrar_paso}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// SIN_PAUSAS=1: los usuarios virtuales no esperan entre acciones, así que hay ~200 peticiones en vuelo a la vez (peor caso)
const SIN_PAUSAS = __ENV.SIN_PAUSAS === '1';
function pausa() {
  if (!SIN_PAUSAS) sleep(1 + Math.random() * 2);
}

/** Muestra los primeros fallos de cada usuario virtual (no todos: si algo falla, falla miles de veces). */
let avisados = 0;
function avisar(que, respuesta) {
  if (avisados++ < 3) console.error(`${que}: HTTP ${respuesta.status} ${String(respuesta.body).slice(0, 160)}`);
}

/** Inicia sesión una sola vez por usuario virtual (la cookie queda en su jar). El login no cuenta en los umbrales. */
let conSesion = false;
function iniciarSesion(email, password) {
  if (conSesion) return;
  const r = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ email, password, recordarme: false }), { headers: cabeceras, tags: { endpoint: 'login' } });
  if (r.status !== 200) throw new Error(`login de ${email}: ${r.status} ${r.body}`);
  conSesion = true;
}

// ------------------------------------------------------------------------------------------------ Agendar
export function agendar() {
  const cliente = datos.clientes[(exec.vu.idInInstance - 1) % datos.clientes.length];
  iniciarSesion(cliente, datos.contrasenaCliente);

  const hoy = new Date();
  const mes = new Date(hoy.getFullYear(), hoy.getMonth() + 1, 1); // el mes siguiente tiene todos sus bloques
  const clave = `${mes.getFullYear()}-${String(mes.getMonth() + 1).padStart(2, '0')}`;
  const libres = http.get(`${BASE}/api/v1/citas/disponibilidad?servicioId=${datos.servicioId}&mes=${clave}&zona=America/Bogota`,
    { tags: { endpoint: 'disponibilidad' } });
  check(libres, { 'disponibilidad 200': (r) => r.status === 200 });
  if (libres.status !== 200) { avisar('disponibilidad', libres); return pausa(); }
  const bloques = libres.json().filter((b) => b.estado === 'LIBRE');
  if (bloques.length === 0) return pausa();
  pausa();

  const elegido = bloques[Math.floor(Math.random() * bloques.length)];
  const cita = http.post(`${BASE}/api/v1/citas`, JSON.stringify({
    servicioId: datos.servicioId, fechaHora: elegido.fechaHora, modalidad: 'VIRTUAL', pais: 'Colombia', zonaHoraria: 'America/Bogota',
    descripcionNecesidad: 'Necesito un sistema para llevar el inventario de mi salón de belleza.',
  }), { headers: cabeceras, tags: { endpoint: 'crear_cita' }, responseCallback: http.expectedStatuses(201, 409) });
  check(cita, { 'cita creada o bloque ocupado': (r) => r.status === 201 || r.status === 409 });
  if (cita.status !== 201 && cita.status !== 409) avisar('crear cita', cita);

  if (cita.status === 201) {
    pausa();
    // Se cancela para liberar el bloque y que los demás puedan agendar durante toda la prueba
    const cancelada = http.patch(`${BASE}/api/v1/citas/${cita.json().citaId}/cancelar`, JSON.stringify({ motivo: 'Ya no necesito la cita, gracias.' }),
      { headers: cabeceras, tags: { endpoint: 'cancelar_cita' } });
    check(cancelada, { 'cita cancelada': (r) => r.status === 200 });
  }
  pausa();
}

// ------------------------------------------------------------------------------------------------ Detalle del proyecto
export function detalle() {
  const proyecto = datos.proyectos[(exec.vu.idInInstance - 1) % datos.proyectos.length];
  iniciarSesion(proyecto.clienteEmail, datos.contrasenaCliente);
  const r = http.get(`${BASE}/api/v1/proyectos/${proyecto.codigo}`, { tags: { endpoint: 'detalle_proyecto' } });
  check(r, { 'detalle 200': (x) => x.status === 200, 'trae las fases': (x) => x.status === 200 && x.json().fases.length >= 2 });
  if (r.status !== 200) avisar('detalle', r);
  pausa();
}

// ------------------------------------------------------------------------------------------------ Registrar un paso
const productOwners = Object.keys(datos.pasosPorProductOwner);
// idInInstance numera los usuarios virtuales de TODA la prueba (los de este escenario son el 181 al 200), no sirve de índice.
// En su primera vuelta los 20 arrancan juntos, así que el contador de iteraciones del escenario les da un 0 a 19 único.
let miNumero = -1;
export function registrar() {
  if (miNumero < 0) miNumero = exec.scenario.iterationInTest;
  const v = miNumero;
  const po = productOwners[v % productOwners.length];
  const hermanos = Math.ceil(VUS_REGISTRAR / productOwners.length); // usuarios virtuales que comparten a este Product Owner
  const pasos = datos.pasosPorProductOwner[po];
  // iterationInScenario cuenta las iteraciones de ESTE usuario virtual (iterationInInstance cuenta las de todos)
  const paso = pasos[Math.floor(v / productOwners.length) + exec.vu.iterationInScenario * hermanos];
  if (__ENV.DEPURAR && v < 3) console.log(`registrar v=${v} po=${po} hermanos=${hermanos} iter=${exec.vu.iterationInScenario} pasos=${pasos && pasos.length} paso=${paso}`);
  if (!paso) return; // ya no quedan pasos de este Product Owner para este usuario virtual
  iniciarSesion(po, datos.contrasenaPersonal);
  const r = http.patch(`${BASE}/api/v1/pasos/${paso}`, JSON.stringify({ estado: 'HECHO', nota: 'Quedó listo y ya puedes verlo funcionando en tu cuenta de pruebas.' }),
    { headers: cabeceras, tags: { endpoint: 'registrar_paso' } });
  check(r, { 'paso registrado': (x) => x.status === 200 });
  if (r.status !== 200) avisar('registrar paso', r);
  pausa();
}
