import { expect, test } from '@playwright/test';
import { abrirComo, agendarCita, comoAdministradora, comoProductOwner, crearProyecto, CUESTIONARIO, dia, envejecerCita, EQUIPOS, ok, registrarCliente } from './flujos';

/** HU-09, HU-10 y HU-02: el administrador asigna el equipo, el Product Owner confirma y llena el cuestionario, y se crea el proyecto. */
test('asignar → confirmar → cuestionario → crear proyecto', async ({ browser, playwright }) => {
  const equipo = EQUIPOS.maria;
  const cliente = await registrarCliente(playwright, 'Rosa Medina');
  const admin = await comoAdministradora(playwright);
  const po = await comoProductOwner(playwright, equipo);
  const cita = await agendarCita(cliente);

  // La administradora ve la cita sin equipo y la asigna
  const { page: paginaAdmin } = await abrirComo(browser, admin);
  await paginaAdmin.goto('/admin/citas');
  await expect(paginaAdmin.getByText(cita.codigo).first()).toBeVisible();
  await ok(await admin.api.patch(`/api/v1/citas/${cita.citaId}/asignar`, { data: { equipoId: equipo.id, nota: 'Asignada desde la prueba' } }));

  // Sin confirmar no se puede llenar el cuestionario
  const sinConfirmar = await po.api.post(`/api/v1/citas/${cita.citaId}/cuestionario`, { data: CUESTIONARIO });
  expect(sinConfirmar.status()).toBe(409);

  // El Product Owner la ve en su lista y la confirma con el enlace (las virtuales lo exigen)
  const { page: paginaPo } = await abrirComo(browser, po);
  await paginaPo.goto('/equipo/citas');
  await expect(paginaPo.getByText(cita.codigo).first()).toBeVisible();
  const sinEnlace = await po.api.patch(`/api/v1/citas/${cita.citaId}/confirmar`, { data: {} });
  expect(sinEnlace.status()).toBe(422);
  await ok(await po.api.patch(`/api/v1/citas/${cita.citaId}/confirmar`, { data: { enlaceReunion: 'https://meet.google.com/abc-defg-hij' } }));

  // La reunión aún no ocurrió: el cuestionario espera (409). Cuando ya pasó, queda ATENDIDA
  expect((await po.api.post(`/api/v1/citas/${cita.citaId}/cuestionario`, { data: CUESTIONARIO })).status()).toBe(409);
  envejecerCita(cita.citaId);
  const cuestionario = await ok(await po.api.post(`/api/v1/citas/${cita.citaId}/cuestionario`, { data: CUESTIONARIO }), 201);
  expect(cuestionario.citaEstado).toBe('ATENDIDA');

  // El proyecto se crea desde la cita atendida y nace con el plan en borrador copiado de la plantilla
  const codigo = await crearProyecto(admin, cita, equipo);
  expect(codigo).toMatch(/^P-\d{4}$/);
  const lista = await ok<{ proyectos: { codigo: string; estadoPlan: string; fases: number; avancePorcentaje: number }[] }>(
    await admin.api.get(`/api/v1/proyectos?q=${codigo}`),
  );
  const creado = lista.proyectos.find((p) => p.codigo === codigo)!;
  expect(creado.estadoPlan).toBe('BORRADOR');
  expect(creado.fases).toBe(6); // la plantilla de «Sistema de inventario» tiene 6 fases
  expect(Number(creado.avancePorcentaje)).toBe(0);

  // Una cita se convierte en proyecto una sola vez
  const repetido = await admin.api.post('/api/v1/proyectos', {
    data: { citaId: cita.citaId, nombre: 'Otro proyecto', alcance: 'Otro alcance que no debería crearse nunca.', fechaInicio: dia(0), fechaEstimadaEntrega: dia(60), equipoId: equipo.id },
  });
  expect(repetido.status()).toBe(409);

  // La administradora lo ve en «Proyectos» y la cliente en «Mis proyectos»
  await paginaAdmin.goto('/admin/proyectos');
  await expect(paginaAdmin.getByText(codigo).first()).toBeVisible();
  const { page: paginaCliente } = await abrirComo(browser, cliente);
  await paginaCliente.goto('/cliente/proyectos');
  await expect(paginaCliente.getByText('Inventario del salón').first()).toBeVisible();
});
