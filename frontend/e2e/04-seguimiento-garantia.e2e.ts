import { expect, test } from '@playwright/test';
import { abrirComo, detalle, EQUIPOS, NOTA, ok, proyectoAprobado, terminarFase } from './flujos';

/** HU-03, HU-11, HU-12 y HU-13: pasos hechos → entregar → ajuste → aprobar la fase → las demás fases → garantía. */
test('pasos → entregar → aprobar fase → garantía', async ({ browser, playwright }) => {
  const { cliente, po, codigo } = await proyectoAprobado(playwright, EQUIPOS.camila);

  // Una fase no se entrega con pasos pendientes
  let d = await detalle(po, codigo);
  const fase1 = d.fases.find((f) => f.orden === 1)!;
  expect((await po.api.post(`/api/v1/fases/${fase1.id}/entregar`)).status()).toBe(409);

  // La nota del paso exige 20 caracteres
  const primero = fase1.pasos[0]!;
  expect((await po.api.patch(`/api/v1/pasos/${primero.id}`, { data: { estado: 'HECHO', nota: 'corta' } })).status()).toBe(422);
  await ok(await po.api.patch(`/api/v1/pasos/${primero.id}`, { data: { estado: 'HECHO', nota: NOTA } }));

  // El cliente ve el avance de su proyecto en la pantalla
  const { page } = await abrirComo(browser, cliente);
  await page.goto(`/cliente/proyectos/${codigo}`);
  await expect(page.getByText(NOTA).first()).toBeVisible();

  // Se entrega la fase 1: queda EN_REVISION, esperando al cliente
  expect((await terminarFase(po, codigo, 1)).estado).toBe('EN_REVISION');

  // El cliente pide un ajuste: la fase vuelve a EN_CURSO con un paso de ajuste
  const pedido = 'El botón de pagar debería mostrar el costo del envío antes de confirmar la compra.';
  const cambio = await ok(await cliente.api.post(`/api/v1/fases/${fase1.id}/solicitudes-cambio`, { data: { tipo: 'AJUSTE', descripcion: pedido } }), 201);
  expect(cambio.rondasUsadas).toBe(1);
  d = await detalle(cliente, codigo);
  const enCurso = d.fases.find((f) => f.orden === 1)!;
  expect(enCurso.estado).toBe('EN_CURSO');
  expect(enCurso.pasos.some((p) => p.tipo === 'AJUSTE' && p.estado !== 'HECHO')).toBe(true);

  // El equipo hace el ajuste, vuelve a entregar y el cliente aprueba: la fase vale un tercio
  expect((await terminarFase(po, codigo, 1)).estado).toBe('EN_REVISION');
  const aprobada = await ok(await cliente.api.post(`/api/v1/fases/${fase1.id}/aprobar`));
  expect(aprobada.estado).toBe('APROBADA');
  expect(Number(aprobada.avancePorcentaje)).toBeCloseTo(33.3, 0);

  // Las fases 2 y 3 las cierra el equipo (no necesitan al cliente); al terminar la última, el proyecto entra en garantía
  expect((await terminarFase(po, codigo, 2)).estado).toBe('APROBADA');
  expect((await terminarFase(po, codigo, 3)).estado).toBe('APROBADA');
  d = await detalle(cliente, codigo);
  expect(d.estado).toBe('EN_GARANTIA');
  expect(Number(d.avancePorcentaje)).toBe(100);
  expect(d.garantiaHasta).toBeTruthy();

  // El cliente lo ve en garantía
  await page.goto(`/cliente/proyectos/${codigo}`);
  await expect(page.getByText(/garant[ií]a/i).first()).toBeVisible();
});
