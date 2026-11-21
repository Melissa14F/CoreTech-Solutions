import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Browser, type Page } from '@playwright/test';
import { CUENTAS } from './ayudas';
import { sesion, type Persona } from './flujos';

/**
 * RNF-07 (accesibilidad WCAG 2.1 AA con axe-core) y RNF-03 (ninguna página se desborda) en todas las rutas de la SPA.
 * Usa los datos demo: Laura (cliente) con P-0031 aprobado y P-0035 con el plan enviado; Andrés (Product Owner) y Patricia (administradora).
 */
type Rol = 'anonimo' | 'laura' | 'andres' | 'patricia';

const RUTAS: Record<Rol, string[]> = {
  anonimo: ['/', '/legal', '/ingresar', '/registro', '/restablecer?token=prueba', '/ruta-que-no-existe'],
  laura: [
    '/cliente/proyectos', '/cliente/proyectos/P-0031', '/cliente/proyectos/P-0035/propuesta', '/cliente/citas', '/cliente/agendar',
    '/cambiar-contrasena',
  ],
  andres: [
    '/equipo/proyectos', '/equipo/proyectos/P-0031/registrar', '/equipo/proyectos/P-0031/plan', '/equipo/proyectos/P-0035/plan', '/equipo/citas',
  ],
  patricia: [
    '/admin', '/admin/citas', '/admin/proyectos', '/admin/proyectos/nuevo', '/admin/proyectos/P-0035/plan', '/admin/plantillas', '/admin/solicitudes',
    '/admin/especialidad/desarrolladores', '/admin/especialidad/equipos', '/admin/servicios', '/admin/contenido', '/admin/usuarios', '/admin/roles',
    '/admin/auditoria', '/admin/correo',
  ],
};
const CUENTA: Record<Exclude<Rol, 'anonimo'>, string> = { laura: CUENTAS.laura, andres: CUENTAS.andres, patricia: CUENTAS.patricia };

const ANCHOS_AXE = [375, 1440];
const ANCHOS_DESBORDE = [375, 768, 1024, 1280, 1440];
const ETIQUETAS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const personas = new Map<string, Persona>();

async function abrir(browser: Browser, playwright: Parameters<typeof sesion>[0], rol: Rol, ancho: number): Promise<Page> {
  if (rol === 'anonimo') {
    return (await browser.newContext({ viewport: { width: ancho, height: 900 }, baseURL: 'http://localhost:5174' })).newPage();
  }
  let persona = personas.get(rol);
  if (!persona) {
    persona = await sesion(playwright, CUENTA[rol]);
    personas.set(rol, persona);
  }
  const contexto = await browser.newContext({ storageState: await persona.api.storageState(), viewport: { width: ancho, height: 900 }, baseURL: 'http://localhost:5174' });
  return contexto.newPage();
}

/** Espera a que la pantalla termine de cargar: ya hay un título y no queda ninguna petición en vuelo. */
async function esperarPantalla(page: Page) {
  await page.locator('h1').first().waitFor({ state: 'visible', timeout: 15_000 });
  await page.waitForLoadState('networkidle');
}

for (const rol of Object.keys(RUTAS) as Rol[]) {
  for (const ruta of RUTAS[rol]) {
    test(`${rol} · ${ruta} · sin violaciones de axe (375 y 1440 px)`, async ({ browser, playwright }) => {
      for (const ancho of ANCHOS_AXE) {
        const page = await abrir(browser, playwright, rol, ancho);
        await page.goto(ruta);
        await esperarPantalla(page);
        const resultado = await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG).analyze();
        const resumen = resultado.violations.map(
          (v) => `[${v.impact}] ${v.id}: ${v.help}\n    ${v.nodes.slice(0, 4).map((n) => n.target.join(' ')).join('\n    ')}`,
        );
        expect(resumen, `Violaciones de accesibilidad en ${ruta} a ${ancho}px`).toEqual([]);
        await page.context().close();
      }
    });

    test(`${rol} · ${ruta} · no se desborda (375, 768, 1024, 1280 y 1440 px)`, async ({ browser, playwright }) => {
      for (const ancho of ANCHOS_DESBORDE) {
        const page = await abrir(browser, playwright, rol, ancho);
        await page.goto(ruta);
        await esperarPantalla(page);
        const medidas = await page.evaluate(() => {
          const ancho = document.documentElement.clientWidth;
          const culpables = [...document.querySelectorAll('body *')]
            .filter((e) => {
              const r = e.getBoundingClientRect();
              return r.width > 0 && r.right > ancho + 1 && !e.closest('[hidden], [aria-hidden="true"], dialog:not([open])');
            })
            .slice(0, 4)
            .map((e) => `${e.tagName.toLowerCase()}${e.className ? '.' + String(e.className).split(' ')[0] : ''}`);
          return { ancho, scroll: document.documentElement.scrollWidth, culpables };
        });
        expect(medidas.scroll, `Desborde horizontal en ${ruta} a ${ancho}px (elementos más anchos: ${medidas.culpables.join(', ')})`).toBeLessThanOrEqual(medidas.ancho);
        await page.context().close();
      }
    });
  }
}
