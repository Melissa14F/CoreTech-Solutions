import { expect, test } from '@playwright/test';
import { agendarCita } from './flujos';

/** HU-05, HU-01 y HU-08: una empresa se registra, agenda su cita y después la cancela. */
test('registro → agendar → cancelar', async ({ page }) => {
  const correo = `e2e-registro-${Date.now().toString(36)}@correo.com`;

  // Registro por la pantalla, como lo hace una persona
  await page.goto('/registro');
  await page.getByLabel('Nombre completo').fill('Ana Torres');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Celular').fill('3001234567');
  await page.getByLabel('Empresa').fill('Panadería La Espiga');
  await page.getByLabel('Contraseña', { exact: true }).fill('Segura2026!');
  await page.getByLabel('Confirma la contraseña', { exact: true }).fill('Segura2026!');
  await page.getByRole('checkbox', { name: /política de tratamiento de datos/ }).check();
  await page.getByRole('button', { name: 'Crear cuenta y agendar' }).click();

  // Queda con sesión de cliente, en su panel (sin un servicio elegido no se manda directo al agendamiento)
  await expect(page).toHaveURL(/\/cliente\/proyectos/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

  // Agenda la cita (la sesión del navegador es la misma que usa `page.request`)
  const cita = await agendarCita({ api: page.request, email: correo });
  expect(cita.codigo).toMatch(/^C-\d{4}$/);

  // La ve en «Mis citas», pendiente
  await page.goto('/cliente/citas');
  const tarjeta = page.locator('.tarjeta-cita', { hasText: cita.codigo });
  await expect(tarjeta).toBeVisible();
  await expect(tarjeta).toContainText('Pendiente');

  // La cancela con su confirmación
  await tarjeta.getByRole('button', { name: `Cancelar cita ${cita.codigo}` }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Sí, cancelar' }).click();
  await expect(page.locator('.tarjeta-cita', { hasText: cita.codigo })).toContainText('Cancelada');

  // Y su franja queda libre otra vez para otra persona (RN-02): la API ya no la cuenta como ocupada
  const mes = cita.fechaHora.slice(0, 7);
  const bloques: { fechaHora: string; estado: string }[] = await (
    await page.request.get(`/api/v1/citas/disponibilidad?servicioId=${cita.servicioId}&mes=${mes}&zona=America/Bogota`)
  ).json();
  expect(bloques.find((b) => b.fechaHora === cita.fechaHora)?.estado).toBe('LIBRE');
});
