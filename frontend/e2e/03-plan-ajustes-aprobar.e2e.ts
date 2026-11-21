import { expect, test } from '@playwright/test';
import { abrirComo, citaAtendida, comoAdministradora, comoProductOwner, crearProyecto, detalle, EQUIPOS, ok, planDeTresFases, registrarCliente } from './flujos';

/** HU-28 y HU-29: el equipo envía el plan con su cotización, el cliente pide ajustes, se reenvía y se aprueba. */
test('plan → ajustes → aprobar', async ({ browser, playwright }) => {
  const equipo = EQUIPOS.sebastian;
  const cliente = await registrarCliente(playwright, 'Julián Cárdenas');
  const admin = await comoAdministradora(playwright);
  const po = await comoProductOwner(playwright, equipo);
  const cita = await citaAtendida(cliente, admin, po, equipo);
  const codigo = await crearProyecto(admin, cita, equipo, 'Tienda de repuestos');
  const ruta = `/api/v1/proyectos/${codigo}`;

  // El cliente no ve el plan mientras es un borrador
  expect((await cliente.api.get(`${ruta}/plan`)).status()).toBe(409);

  // Cotización fuera de rango: nada se envía
  const barata = await po.api.post(`${ruta}/plan/enviar`, { data: planDeTresFases(999_999) });
  expect(barata.status()).toBe(422);
  expect((await ok(await admin.api.get(`${ruta}/plan`))).estadoPlan).toBe('BORRADOR');

  // El Product Owner envía el plan y la cotización
  const enviado = await ok(await po.api.post(`${ruta}/plan/enviar`, { data: planDeTresFases() }));
  expect(enviado.estadoPlan).toBe('ENVIADO');

  // El cliente lo abre en la pantalla «Plan y cotización» y ve el valor
  const { page } = await abrirComo(browser, cliente);
  await page.goto(`/cliente/proyectos/${codigo}/propuesta`);
  await expect(page.getByText(/7\.800\.000/).first()).toBeVisible();
  await expect(page.getByRole('button', { name: /Aprobar/ }).first()).toBeVisible();

  // Pide ajustes con un comentario
  const comentario = 'Quisiera que la fase 2 incluya también la capacitación del personal que usará el sistema.';
  const ajustes = await ok(await cliente.api.post(`${ruta}/plan/ajustes`, { data: { comentario } }), 201);
  expect(ajustes.estadoPlan).toBe('AJUSTES_PEDIDOS');

  // El equipo lo ve, lo corrige y lo vuelve a enviar con otro valor
  const plan = await ok(await po.api.get(`${ruta}/plan`));
  expect(plan.estadoPlan).toBe('AJUSTES_PEDIDOS');
  expect(plan.ajustesPedidos.texto).toBe(comentario);
  const reenviado = await ok(await po.api.post(`${ruta}/plan/enviar`, { data: planDeTresFases(8_500_000) }));
  expect(reenviado.estadoPlan).toBe('ENVIADO');

  // Aprobar sin aceptar explícitamente no vale; aceptando, el plan queda fijo y empieza la fase 1
  const sinAceptar = await cliente.api.post(`${ruta}/plan/aprobar`, { data: { aceptaPlanYCotizacion: false } });
  expect(sinAceptar.ok()).toBeFalsy();
  const aprobado = await ok(await cliente.api.post(`${ruta}/plan/aprobar`, { data: { aceptaPlanYCotizacion: true } }));
  expect(aprobado.estadoPlan).toBe('APROBADO');
  expect(aprobado.estado).toBe('ACTIVO');
  const d = await detalle(cliente, codigo);
  expect(d.fases.find((f) => f.orden === 1)?.estado).toBe('EN_CURSO');
  expect(Number(d.avancePorcentaje)).toBe(0);

  // Un plan aprobado ya no se edita ni se aprueba dos veces
  expect((await po.api.put(`${ruta}/plan`, { data: planDeTresFases() })).status()).toBe(409);
  expect((await cliente.api.post(`${ruta}/plan/aprobar`, { data: { aceptaPlanYCotizacion: true } })).status()).toBe(409);

  // En pantalla, el cliente ya ve su proyecto en curso
  await page.goto(`/cliente/proyectos/${codigo}`);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Tienda de repuestos');
});
