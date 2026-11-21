import { expect, type APIRequestContext, type Page } from '@playwright/test';

/** Cuentas de los datos demo (todas con la misma contraseña). */
export const CONTRASENA_DEMO = 'Demo2026!';
export const CUENTAS = {
  laura: 'laura.gomez@correo.com',
  andres: 'andres.perez@coretechsolutions.co',
  patricia: 'patricia.leon@coretechsolutions.co',
} as const;

/** Inicia sesión por la pantalla, como lo haría una persona, y espera a llegar a su panel. */
export async function iniciarSesion(page: Page, email: string, contrasena = CONTRASENA_DEMO) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(/\/(cliente|equipo|admin)/);
}

export async function cerrarSesion(page: Page) {
  await page.context().clearCookies();
}

/** Inicia sesión por la API (más rápido, para preparar datos que no son lo que se prueba). */
export async function sesionPorApi(request: APIRequestContext, email: string, contrasena = CONTRASENA_DEMO) {
  const r = await request.post('/api/v1/auth/login', { data: { email, password: contrasena } });
  expect(r.ok(), `login de ${email}: ${r.status()}`).toBeTruthy();
}

/** Lee el cuerpo JSON de una respuesta que debe ser correcta. */
export async function json<T = any>(respuesta: import('@playwright/test').APIResponse): Promise<T> {
  expect(respuesta.ok(), `${respuesta.url()} → ${respuesta.status()} ${await respuesta.text()}`).toBeTruthy();
  return (await respuesta.json()) as T;
}
