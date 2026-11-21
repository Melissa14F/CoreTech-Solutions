import { expect, test } from '@playwright/test';
import { abrirComo, detalle, dia, EQUIPOS, ok, proyectoAprobado } from './flujos';

const MOTIVO = 'El cliente necesita capacitar a su personal antes del lanzamiento y lo pidió por escrito.';

/** HU-30: cambio de un plan aprobado en tres pasos: lo propone el Product Owner, lo autoriza la administradora y lo acepta el cliente. */
test('cambio de plan: proponer → autorizar → aceptar', async ({ browser, playwright }) => {
  const { cliente, admin, po, codigo } = await proyectoAprobado(playwright, EQUIPOS.maria);
  const ruta = `/api/v1/proyectos/${codigo}/cambios-plan`;
  const propuesta = {
    tipo: 'AGREGAR_FASE', nombreFase: 'Capacitación', posicion: 2, apruebaCliente: true,
    pasos: [{ titulo: 'Taller con el personal', explicacion: 'Dos sesiones' }, { titulo: 'Manual de uso' }],
    motivo: MOTIVO, nuevaEntrega: dia(100), valorAdicional: 1_500_000,
  };

  // Solo propone el Product Owner del equipo (la administradora y el cliente no)
  expect((await admin.api.post(ruta, { data: propuesta })).status()).toBe(403);
  expect((await cliente.api.post(ruta, { data: propuesta })).status()).toBe(403);
  // El motivo es obligatorio
  expect((await po.api.post(ruta, { data: { ...propuesta, motivo: 'corto' } })).status()).toBe(422);

  // 1. El Product Owner propone agregar una fase
  const cambio = await ok(await po.api.post(ruta, { data: propuesta }), 201);
  expect(cambio.estado).toBe('PROPUESTO');
  expect(cambio.fasesAntes).toBe(3);
  expect(cambio.fasesDespues).toBe(4);
  // Mientras haya uno abierto no se puede proponer otro
  expect((await po.api.post(ruta, { data: propuesta })).status()).toBe(409);
  // El cliente todavía no lo ve: falta la autorización
  const vistaCliente = await ok(await cliente.api.get(ruta));
  expect(vistaCliente.cambios).toHaveLength(0);

  // 2. La administradora lo ve por autorizar, con la comparación, y lo autoriza
  const { page: paginaAdmin } = await abrirComo(browser, admin);
  await paginaAdmin.goto('/admin/proyectos');
  await expect(paginaAdmin.getByText(/Cambios de plan por autorizar/i).first()).toBeVisible();
  const porAutorizar = await ok<{ id: string }[]>(await admin.api.get('/api/v1/cambios-plan'));
  expect(porAutorizar.some((c) => c.id === cambio.cambioId)).toBe(true);
  // El cliente no puede autorizar, la administradora sí
  expect((await cliente.api.patch(`/api/v1/cambios-plan/${cambio.cambioId}/autorizar`)).status()).toBe(403);
  const autorizado = await ok(await admin.api.patch(`/api/v1/cambios-plan/${cambio.cambioId}/autorizar`));
  expect(autorizado.estado).toBe('AUTORIZADO');

  // 3. El cliente lo ve en el detalle de su proyecto y lo acepta
  const { page: paginaCliente } = await abrirComo(browser, cliente);
  await paginaCliente.goto(`/cliente/proyectos/${codigo}`);
  await expect(paginaCliente.getByText(/Capacitación/).first()).toBeVisible();
  const aceptado = await ok(await cliente.api.patch(`/api/v1/cambios-plan/${cambio.cambioId}/aceptar`, { data: { acepto: true } }));
  expect(aceptado.estado).toBe('ACEPTADO');
  expect(aceptado.fases).toBe(4);

  // El plan quedó con cuatro fases y el valor total sumó lo adicional
  const d = await detalle(cliente, codigo);
  expect(d.fases).toHaveLength(4);
  expect(d.fechaEstimadaEntrega).toBe(dia(100));
  const resumen = await ok(await cliente.api.get(ruta));
  expect(Number(resumen.valorTotal)).toBe(7_800_000 + 1_500_000);
  expect(resumen.hayCambioAbierto).toBe(false);
});
