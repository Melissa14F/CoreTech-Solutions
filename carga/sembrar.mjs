// Siembra los datos de la prueba de carga (RNF-04) en la API de pruebas (puerto 8090, base `coretech_e2e`):
//   · 10 proyectos con el plan APROBADO, cada uno con 12 pasos pendientes en la fase en curso (los que el Product Owner marcará).
//     10 = todo el cupo de los 4 equipos de los datos demo (3 + 3 + 3 + 1): se llenan por la API, igual que lo haría el negocio.
//   · 190 clientes más, solo para agendar citas (el plan de 200 usuarios simultáneos: 120 agendan + 60 ven su proyecto + 20 registran).
// Escribe carga/datos.json, que lee carga.js. Uso (desde prototipo/carga, con la API de pruebas encendida):  node sembrar.mjs
import { spawnSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const API = process.env.API_URL ?? 'http://localhost:8090';
const ORIGEN = process.env.ORIGEN ?? 'http://localhost:5174'; // el que acepta el perfil e2e (FiltroOrigen)
const CLIENTES_EXTRA = Number(process.env.CLIENTES_EXTRA ?? 190);
const PASOS_POR_PROYECTO = 12; // el máximo de un plan
const CONTRASENA_DEMO = 'Demo2026!';
const CONTRASENA_CLIENTE = 'Segura2026!';
const prototipo = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** Ejecuta una sentencia en la base de las pruebas y devuelve lo que imprime (sin la línea "UPDATE n"). */
function sql(sentencia) {
  const r = spawnSync('docker', ['compose', '-f', resolve(prototipo, 'docker-compose.yml'), 'exec', '-T', 'db', 'psql', '-U', 'coretech',
    '-d', 'coretech_e2e', '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-c', sentencia], { encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`SQL falló: ${sentencia}\n${r.stderr}`);
  return r.stdout.split('\n').map((l) => l.trim()).filter((l) => l && !/^(UPDATE|INSERT|DELETE)\s/.test(l)).join('\n');
}

/** Una llamada a la API con la cookie de la sesión; falla si el estado no es el esperado. */
async function llamar(sesion, metodo, ruta, cuerpo, esperado = [200, 201, 204]) {
  const r = await fetch(API + ruta, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', Origin: ORIGEN, ...(sesion ? { Cookie: sesion } : {}) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  const texto = await r.text();
  if (!esperado.includes(r.status)) throw new Error(`${metodo} ${ruta} → ${r.status} ${texto}`);
  return { estado: r.status, json: texto ? JSON.parse(texto) : null, cookie: r.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ') };
}

async function ingresar(email, password) {
  const { cookie } = await llamar(null, 'POST', '/api/v1/auth/login', { email, password, recordarme: false });
  return cookie;
}

async function registrarCliente(i) {
  const email = `carga-${Date.now().toString(36)}-${i}@correo.com`;
  const { cookie } = await llamar(null, 'POST', '/api/v1/auth/registro', {
    nombre: 'Cliente de Carga', email, celular: '3001234567', empresa: 'Empresa de Carga',
    password: CONTRASENA_CLIENTE, confirmacion: CONTRASENA_CLIENTE, aceptaDatos: true,
  });
  return { email, cookie };
}

const dia = (n) => new Date(Date.now() + n * 86_400_000).toISOString().slice(0, 10);

/** Un bloque LIBRE de los últimos del mes siguiente (al azar), para no chocar con las citas de la prueba. */
async function agendar(cliente, servicioId) {
  const hoy = new Date();
  for (const desplazamiento of [1, 0]) {
    const mes = new Date(hoy.getFullYear(), hoy.getMonth() + desplazamiento, 1);
    const clave = `${mes.getFullYear()}-${String(mes.getMonth() + 1).padStart(2, '0')}`;
    const { json: bloques } = await llamar(cliente.cookie, 'GET', `/api/v1/citas/disponibilidad?servicioId=${servicioId}&mes=${clave}&zona=America/Bogota`);
    const libres = bloques.filter((b) => b.estado === 'LIBRE');
    if (libres.length === 0) continue;
    const elegido = libres[libres.length - 1 - Math.floor(Math.random() * Math.min(libres.length, 20))];
    const { json } = await llamar(cliente.cookie, 'POST', '/api/v1/citas', {
      servicioId, fechaHora: elegido.fechaHora, modalidad: 'VIRTUAL', pais: 'Colombia', zonaHoraria: 'America/Bogota',
      descripcionNecesidad: 'Necesito un sistema para llevar el inventario de mi salón de belleza.',
    }, [201]);
    return json.citaId;
  }
  throw new Error('No hay bloques libres para agendar');
}

function planDeCarga() {
  const pasos = Array.from({ length: PASOS_POR_PROYECTO }, (_, n) => ({ titulo: `Paso ${n + 1} de la fase 1`, explicacion: 'Explicación' }));
  return {
    fases: [
      { nombre: 'Fase 1', queEs: 'Qué es', apruebaCliente: true, pasos },
      { nombre: 'Fase 2', queEs: 'Qué es', apruebaCliente: false, pasos: [{ titulo: 'Paso 1 de la fase 2' }, { titulo: 'Paso 2 de la fase 2' }] },
    ],
    valorCotizado: 7_800_000, fechaEstimadaEntrega: dia(70),
    condicionesCotizacion: 'Incluye 2 rondas de ajustes en cada fase y 30 días de garantía; no incluye hosting.',
  };
}

// ------------------------------------------------------------------------------------------------------------------
const admin = await ingresar('patricia.leon@coretechsolutions.co', CONTRASENA_DEMO);
const { json: servicios } = await llamar(admin, 'GET', '/api/v1/servicios');
const servicio = servicios.find((s) => s.slug === 'sistema-inventario') ?? servicios[0];
const { json: equipos } = await llamar(admin, 'GET', '/api/v1/citas/equipos-disponibles');
console.log(`Equipos con cupo: ${equipos.map((e) => `${e.productOwner} ${e.proyectosActivos}/${e.maxProyectos}`).join(' · ')}`);

const proyectos = [];
const pasosPorProductOwner = {};

for (const equipo of equipos) {
  // La lista de equipos no trae el correo del Product Owner: sale de su integrante con ese rol
  const poEmail = sql(`SELECT d.correo FROM integrantes_equipo i JOIN desarrolladores d ON d.id = i.desarrollador_id
                        WHERE i.equipo_id = '${equipo.equipoId}' AND i.rol = 'PRODUCT_OWNER' LIMIT 1`);
  const po = await ingresar(poEmail, CONTRASENA_DEMO);
  const cupo = equipo.maxProyectos - equipo.proyectosActivos;
  for (let n = 0; n < cupo; n++) {
    const cliente = await registrarCliente(`p${proyectos.length}`);
    const citaId = await agendar(cliente, servicio.servicioId);
    await llamar(admin, 'PATCH', `/api/v1/citas/${citaId}/asignar`, { equipoId: equipo.equipoId, nota: 'Asignada para la prueba de carga' });
    await llamar(po, 'PATCH', `/api/v1/citas/${citaId}/confirmar`, { enlaceReunion: 'https://meet.google.com/abc-defg-hij' });
    // Una cita solo se atiende cuando ya ocurrió: se mueve al pasado (lo mismo que hacen las pruebas E2E)
    sql(`UPDATE citas SET fecha_hora = date_trunc('hour', now()) - ((48 + floor(random() * 4000)) * interval '1 hour'), confirmada_at = now() - interval '5 days' WHERE id = '${citaId}'`);
    await llamar(po, 'POST', `/api/v1/citas/${citaId}/cuestionario`, {
      resumen: 'El cliente necesita controlar las existencias de su salón y avisar cuando algo se esté acabando.',
      descripcionCliente: 'Dueña de un salón de belleza con tres empleadas; hoy lleva todo en un cuaderno y quiere dejarlo.',
      presupuesto: 'DE_5_A_15M', idioma: 'ESPANOL', fechaDeseada: 'Antes de febrero', urgencia: 'MEDIA',
    }, [201]);
    const { json: creado } = await llamar(admin, 'POST', '/api/v1/proyectos', {
      citaId, nombre: `Carga ${proyectos.length + 1}`, alcance: 'Proyecto sembrado para la prueba de carga del portal.',
      fechaInicio: dia(0), fechaEstimadaEntrega: dia(60), equipoId: equipo.equipoId,
    }, [201]);
    const codigo = creado.codigo;
    await llamar(po, 'POST', `/api/v1/proyectos/${codigo}/plan/enviar`, planDeCarga());
    await llamar(cliente.cookie, 'POST', `/api/v1/proyectos/${codigo}/plan/aprobar`, { aceptaPlanYCotizacion: true });
    const { json: d } = await llamar(po, 'GET', `/api/v1/proyectos/${codigo}`);
    const fase1 = d.fases.find((f) => f.orden === 1);
    const pasos = fase1.pasos.filter((p) => p.estado === 'PENDIENTE' || p.estado === 'EN_CURSO').map((p) => p.id);
    (pasosPorProductOwner[poEmail] ??= []).push(...pasos);
    proyectos.push({ codigo, clienteEmail: cliente.email, poEmail });
    console.log(`  ${codigo} (${poEmail}) con ${pasos.length} pasos pendientes`);
  }
}

// Clientes que solo agendan: se registran de 10 en 10
const clientes = [];
for (let i = 0; i < CLIENTES_EXTRA; i += 10) {
  const lote = await Promise.all(Array.from({ length: Math.min(10, CLIENTES_EXTRA - i) }, (_, k) => registrarCliente(`c${i + k}`)));
  clientes.push(...lote.map((c) => c.email));
}

const datos = {
  servicioId: servicio.servicioId,
  contrasenaCliente: CONTRASENA_CLIENTE,
  contrasenaPersonal: CONTRASENA_DEMO,
  proyectos,
  pasosPorProductOwner,
  clientes,
};
writeFileSync(resolve(dirname(fileURLToPath(import.meta.url)), 'datos.json'), JSON.stringify(datos, null, 2));
const totalPasos = Object.values(pasosPorProductOwner).reduce((s, l) => s + l.length, 0);
console.log(`Listo: ${proyectos.length} proyectos, ${totalPasos} pasos pendientes y ${clientes.length} clientes para agendar → carga/datos.json`);
