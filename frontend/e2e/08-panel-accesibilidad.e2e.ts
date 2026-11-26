import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

/**
 * RNF-07: el panel de accesibilidad existe en todas las pantallas, se maneja con teclado, aplica sus ajustes y no introduce
 * violaciones WCAG 2.1 AA ni abierto ni con los ajustes activos (axe-core, a 375 y 1440 px).
 */
const ETIQUETAS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const ANCHOS = [375, 1440];

async function abrirInicio(page: Page, ancho: number) {
  page_ = page;
  await page.setViewportSize({ width: ancho, height: 900 });
  await page.goto('/');
  await page.locator('h1').first().waitFor({ state: 'visible' });
}

/** Nombre accesible del elemento con el foco (se lee directo: el foco se mueve unos milisegundos después de abrir). */
const enfocado = () => page_.evaluate(() => document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.tagName ?? '');
let page_: Page;

async function violaciones(page: Page): Promise<string[]> {
  const r = await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG).analyze();
  return r.violations.map((v) => `[${v.impact}] ${v.id}: ${v.help}\n    ${v.nodes.slice(0, 4).map((n) => n.target.join(' ')).join('\n    ')}`);
}

for (const ancho of ANCHOS) {
  test(`el panel abierto no tiene violaciones de axe (${ancho} px)`, async ({ page }) => {
    await abrirInicio(page, ancho);
    await page.getByRole('button', { name: 'Abrir opciones de accesibilidad' }).click();
    await expect(page.getByRole('dialog', { name: 'Accesibilidad' })).toBeVisible();
    expect(await violaciones(page)).toEqual([]);
  });

  test(`con los ajustes activos (texto máximo, alto contraste, daltonismo, títulos resaltados) no hay violaciones (${ancho} px)`, async ({ page }) => {
    await abrirInicio(page, ancho);
    await page.getByRole('button', { name: 'Abrir opciones de accesibilidad' }).click();
    const panel = page.getByRole('dialog', { name: 'Accesibilidad' });
    for (let i = 0; i < 3; i++) await panel.getByRole('button', { name: /Tamaño de texto/ }).click();
    await panel.getByRole('button', { name: /Alto contraste/ }).click();
    await panel.getByRole('radio', { name: /Tritanopia/ }).click();
    await panel.getByRole('switch', { name: /Resaltar títulos/ }).click();
    await panel.getByRole('switch', { name: /Resaltar enlaces/ }).click();
    await page.keyboard.press('Escape');
    await expect(page.locator('html')).toHaveClass(/modo-texto-3/);
    await page.waitForTimeout(700); // que terminen las transiciones de color y del panel: axe mide los colores del momento
    expect(await violaciones(page)).toEqual([]);
    // Ninguno de esos ajustes debe provocar desplazamiento horizontal
    const { ancho: visible, scroll } = await page.evaluate(() => ({ ancho: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
    expect(scroll).toBeLessThanOrEqual(visible);
  });
}

test('se maneja solo con teclado: Enter abre, el foco queda dentro, Escape cierra y vuelve a la pestaña', async ({ page }) => {
  await abrirInicio(page, 1440);
  const pestana = page.getByRole('button', { name: 'Abrir opciones de accesibilidad' });
  await pestana.focus();
  await page.keyboard.press('Enter');
  const panel = page.getByRole('dialog', { name: 'Accesibilidad' });
  await expect(panel).toBeVisible();
  await expect.poll(enfocado).toBe('Cerrar opciones de accesibilidad');
  // El foco no se escapa del panel aunque se avance muchas veces
  for (let i = 0; i < 30; i++) await page.keyboard.press('Tab');
  expect(await page.evaluate(() => !!document.activeElement?.closest('#panel-accesibilidad'))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(panel).toBeHidden();
  await expect.poll(enfocado).toBe('Abrir opciones de accesibilidad');
});

test('el ajuste se recuerda al cambiar de pantalla y al recargar', async ({ page }) => {
  await abrirInicio(page, 1440);
  await page.getByRole('button', { name: 'Abrir opciones de accesibilidad' }).click();
  await page.getByRole('dialog', { name: 'Accesibilidad' }).getByRole('button', { name: /Tamaño de texto/ }).click();
  await page.keyboard.press('Escape');
  await expect(page.locator('html')).toHaveClass(/modo-texto-1/);

  await page.goto('/legal');
  await expect(page.locator('html')).toHaveClass(/modo-texto-1/);
  await page.reload();
  await expect(page.locator('html')).toHaveClass(/modo-texto-1/);
});
