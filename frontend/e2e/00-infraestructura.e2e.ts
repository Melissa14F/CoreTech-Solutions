import { expect, test } from '@playwright/test';
import { CUENTAS, iniciarSesion } from './ayudas';

test('la API y la SPA arrancan con los datos demo y se puede iniciar sesión', async ({ page }) => {
  await iniciarSesion(page, CUENTAS.laura);
  await expect(page).toHaveURL(/\/cliente\/proyectos/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
