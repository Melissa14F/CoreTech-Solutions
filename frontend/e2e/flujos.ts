/**
 * Pasos reutilizables de los flujos E2E. Las personas (cliente, Product Owner, administradora) son sesiones reales contra la
 * API real; la SPA las ve en el navegador con `abrirComo`. Lo único que no se puede hacer por la pantalla es "que pase el
 * tiempo": una cita solo se atiende cuando ya ocurrió y una reunión también, así que `sql()` mueve esas fechas al pasado.
 */
import { expect, type APIRequestContext, type APIResponse, type Browser, type BrowserContext, type Page, type PlaywrightWorkerArgs } from '@playwright/test';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CONTRASENA_DEMO, CUENTAS } from './ayudas';

const BASE = 'http://localhost:5174';
const PROTOTIPO = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

type Playwright = PlaywrightWorkerArgs['playwright'];

/** Equipos de los datos demo (cada uno con su Product Owner). Andrés ya tiene 2 proyectos; los otros tres, ninguno. */
export const EQUIPOS = {
  maria: { id: 'c0000000-0000-4000-8000-000000000002', email: 'maria.ruiz@coretechsolutions.co' },
  sebastian: { id: 'c0000000-0000-4000-8000-000000000003', email: 'sebastian.mora@coretechsolutions.co' },
  camila: { id: 'c0000000-0000-4000-8000-000000000004', email: 'camila.jaimes@coretechsolutions.co' },
};
export type Equipo = (typeof EQUIPOS)[keyof typeof EQUIPOS];

/** Ejecuta una sentencia en la base de las pruebas y devuelve la primera columna de cada fila. */
export function sql(sentencia: string): string[] {
  const r = spawnSync(
    'docker',
    ['compose', '-f', resolve(PROTOTIPO, 'docker-compose.yml'), 'exec', '-T', 'db', 'psql', '-U', 'coretech', '-d', 'coretech_e2e',
      '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-c', sentencia],
    { encoding: 'utf8' },
  );
  if (r.status !== 0) throw new Error(`SQL falló: ${sentencia}\n${r.stderr}`);
  return r.stdout.split('\n').map((l) => l.trim()).filter((l) => l && !/^(UPDATE|INSERT|DELETE)\s/.test(l));
}

// ---------------------------------------------------------------------------------------------- Sesiones

export interface Persona {
  api: APIRequestContext;
  email: string;
}

export async function sesion(playwright: Playwright, email: string, contrasena = CONTRASENA_DEMO): Promise<Persona> {
  const api = await playwright.request.newContext({ baseURL: BASE });
  const r = await api.post('/api/v1/auth/login', { data: { email, password: contrasena, recordarme: false } });
  expect(r.ok(), `login de ${email}: ${r.status()} ${await r.text()}`).toBeTruthy();
  return { api, email };
}

export const comoAdministradora = (p: Playwright) => sesion(p, CUENTAS.patricia);
export const comoProductOwner = (p: Playwright, equipo: Equipo) => sesion(p, equipo.email);

/** Registra a un cliente nuevo por la API (la misma del formulario) y deja su sesión abierta. */
export async function registrarCliente(playwright: Playwright, nombre = 'Cliente de Prueba'): Promise<Persona & { contrasena: string }> {
  const api = await playwright.request.newContext({ baseURL: BASE });
  const email = `e2e-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}@correo.com`;
  const contrasena = 'Segura2026!';
  const r = await api.post('/api/v1/auth/registro', {
    data: { nombre, email, celular: '3001234567', empresa: 'Empresa de Prueba', password: contrasena, confirmacion: contrasena, aceptaDatos: true },
  });
  expect(r.status(), `registro: ${await r.text()}`).toBe(201);
  return { api, email, contrasena };
}

/** Abre el navegador con la sesión de esa persona ya iniciada (misma cookie que tiene su API). */
export async function abrirComo(browser: Browser, persona: Persona, ancho = 1280): Promise<{ contexto: BrowserContext; page: Page }> {
  const contexto = await browser.newContext({ storageState: await persona.api.storageState(), viewport: { width: ancho, height: 900 }, baseURL: BASE });
  return { contexto, page: await contexto.newPage() };
}

/** Comprueba la respuesta (con el estado esperado, si se da) y devuelve su JSON. */
export async function ok<T = any>(r: APIResponse, esperado?: number): Promise<T> {
  const cuerpo = await r.text();
  if (esperado) expect(r.status(), `${r.url()} → ${cuerpo}`).toBe(esperado);
  else expect(r.ok(), `${r.status()} ${r.url()} → ${cuerpo}`).toBeTruthy();
  return (cuerpo ? JSON.parse(cuerpo) : null) as T;
}

// ---------------------------------------------------------------------------------------------- Citas

export interface CitaCreada {
  citaId: string;
  codigo: string;
  fechaHora: string;
  servicioId: string;
}

/** Elige un bloque LIBRE (de los últimos del mes, al azar, para no chocar con otros flujos) y agenda la cita. */
export async function agendarCita(cliente: Persona, slug = 'sistema-inventario'): Promise<CitaCreada> {
  const servicios = await ok<{ servicioId: string; slug: string }[]>(await cliente.api.get('/api/v1/servicios'));
  const servicio = servicios.find((s) => s.slug === slug);
  expect(servicio, `el servicio ${slug} existe en los datos demo`).toBeTruthy();
  const hoy = new Date();
  for (const desplazamiento of [0, 1]) {
    const mes = new Date(hoy.getFullYear(), hoy.getMonth() + desplazamiento, 1);
    const clave = `${mes.getFullYear()}-${String(mes.getMonth() + 1).padStart(2, '0')}`;
    const bloques = await ok<{ fechaHora: string; estado: string }[]>(
      await cliente.api.get(`/api/v1/citas/disponibilidad?servicioId=${servicio!.servicioId}&mes=${clave}&zona=America/Bogota`),
    );
    const libres = bloques.filter((b) => b.estado === 'LIBRE');
    if (libres.length === 0) continue;
    const elegido = libres[libres.length - 1 - Math.floor(Math.random() * Math.min(libres.length, 20))]!;
    const cita = await ok<{ citaId: string; codigo: string }>(
      await cliente.api.post('/api/v1/citas', {
        data: {
          servicioId: servicio!.servicioId, fechaHora: elegido.fechaHora, modalidad: 'VIRTUAL', pais: 'Colombia', zonaHoraria: 'America/Bogota',
          descripcionNecesidad: 'Necesito un sistema para llevar el inventario de mi salón de belleza.',
        },
      }),
      201,
    );
    return { ...cita, fechaHora: elegido.fechaHora, servicioId: servicio!.servicioId };
  }
  throw new Error('No hay bloques libres para agendar la cita');
}

/** Hace que la reunión de la cita "ya haya ocurrido" (solo así se puede llenar el cuestionario). */
export function envejecerCita(citaId: string) {
  sql(`UPDATE citas SET fecha_hora = date_trunc('hour', now()) - ((48 + floor(random() * 4000)) * interval '1 hour'),
              confirmada_at = now() - interval '5 days' WHERE id = '${citaId}'`);
}

export const CUESTIONARIO = {
  resumen: 'El cliente necesita controlar las existencias de su salón y avisar cuando algo se esté acabando.',
  descripcionCliente: 'Dueña de un salón de belleza con tres empleadas; hoy lleva todo en un cuaderno y quiere dejarlo.',
  presupuesto: 'DE_5_A_15M',
  idioma: 'ESPANOL',
  fechaDeseada: 'Antes de febrero',
  urgencia: 'MEDIA',
};

/** Cita agendada → asignada → confirmada → ocurrida → cuestionario lleno (ATENDIDA). */
export async function citaAtendida(cliente: Persona, admin: Persona, po: Persona, equipo: Equipo, slug?: string): Promise<CitaCreada> {
  const cita = await agendarCita(cliente, slug);
  await ok(await admin.api.patch(`/api/v1/citas/${cita.citaId}/asignar`, { data: { equipoId: equipo.id, nota: 'Asignada en la prueba' } }));
  await ok(await po.api.patch(`/api/v1/citas/${cita.citaId}/confirmar`, { data: { enlaceReunion: 'https://meet.google.com/abc-defg-hij' } }));
  envejecerCita(cita.citaId);
  await ok(await po.api.post(`/api/v1/citas/${cita.citaId}/cuestionario`, { data: CUESTIONARIO }), 201);
  return cita;
}

// ---------------------------------------------------------------------------------------------- Proyectos y plan

/** Fecha de hoy más `dias`, como AAAA-MM-DD. */
export const dia = (dias: number) => new Date(Date.now() + dias * 86_400_000).toISOString().slice(0, 10);

export async function crearProyecto(admin: Persona, cita: CitaCreada, equipo: Equipo, nombre = 'Inventario del salón'): Promise<string> {
  const r = await ok<{ codigo: string }>(
    await admin.api.post('/api/v1/proyectos', {
      data: {
        citaId: cita.citaId, nombre, alcance: 'Sistema para controlar el inventario del salón y avisar cuando algo se esté acabando.',
        fechaInicio: dia(0), fechaEstimadaEntrega: dia(60), equipoId: equipo.id,
      },
    }),
    201,
  );
  return r.codigo;
}

export const CONDICIONES = 'Incluye 2 rondas de ajustes en cada fase y 30 días de garantía; no incluye hosting.';

/** Plan de tres fases (solo la primera la aprueba el cliente) con su cotización. */
export function planDeTresFases(valor = 7_800_000, entrega = dia(70)) {
  return {
    fases: [1, 2, 3].map((f) => ({
      nombre: `Fase ${f}`, queEs: 'Qué es', apruebaCliente: f === 1,
      pasos: [{ titulo: `Paso 1 de la fase ${f}`, explicacion: 'Explicación' }, { titulo: `Paso 2 de la fase ${f}` }],
    })),
    valorCotizado: valor, fechaEstimadaEntrega: entrega, condicionesCotizacion: CONDICIONES,
  };
}

export interface FaseDetalle {
  id: string;
  orden: number;
  estado: string;
  pasos: { id: string; orden: number; tipo: string; estado: string }[];
}

export async function detalle(persona: Persona, codigo: string) {
  return ok<{ estado: string; estadoPlan: string; avancePorcentaje: number; garantiaHasta: string | null; fechaEstimadaEntrega: string; fases: FaseDetalle[] }>(
    await persona.api.get(`/api/v1/proyectos/${codigo}`),
  );
}

export const NOTA = 'Quedó listo y ya puedes verlo funcionando en tu cuenta de pruebas.';

/** Marca todos los pasos de la fase como hechos y la entrega o la cierra. Devuelve la respuesta de entregar. */
export async function terminarFase(po: Persona, codigo: string, orden: number) {
  const d = await detalle(po, codigo);
  const fase = d.fases.find((f) => f.orden === orden)!;
  for (const paso of fase.pasos.filter((p) => p.estado !== 'HECHO' && p.estado !== 'QUITADO')) {
    await ok(await po.api.patch(`/api/v1/pasos/${paso.id}`, { data: { estado: 'HECHO', nota: NOTA } }));
  }
  return ok<{ estado: string }>(await po.api.post(`/api/v1/fases/${fase.id}/entregar`));
}

/** Un proyecto con el plan aprobado y la fase 1 en curso, con su cliente, su Product Owner y la administradora. */
export async function proyectoAprobado(playwright: Playwright, equipo: Equipo, slug?: string) {
  const cliente = await registrarCliente(playwright);
  const admin = await comoAdministradora(playwright);
  const po = await comoProductOwner(playwright, equipo);
  const cita = await citaAtendida(cliente, admin, po, equipo, slug);
  const codigo = await crearProyecto(admin, cita, equipo);
  await ok(await po.api.post(`/api/v1/proyectos/${codigo}/plan/enviar`, { data: planDeTresFases() }));
  await ok(await cliente.api.post(`/api/v1/proyectos/${codigo}/plan/aprobar`, { data: { aceptaPlanYCotizacion: true } }));
  return { cliente, admin, po, codigo, cita };
}
