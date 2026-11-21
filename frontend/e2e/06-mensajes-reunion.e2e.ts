import { expect, test } from '@playwright/test';
import { abrirComo, dia, EQUIPOS, ok, proyectoAprobado, sql } from './flujos';

/** Primer día hábil (lunes a viernes) a partir de dentro de `desde` días, en formato AAAA-MM-DD. */
function diaHabil(desde: number) {
  for (let n = desde; ; n++) {
    const fecha = new Date(Date.now() + n * 864e5);
    if (![0, 6].includes(fecha.getUTCDay())) return dia(n);
  }
}

/** HU-14, HU-15 y HU-16: el cliente y el equipo se escriben; el cliente pide una reunión, se programa y se cierra con su resumen. */
test('mensajes y solicitud de reunión', async ({ browser, playwright }) => {
  const { cliente, po, codigo } = await proyectoAprobado(playwright, EQUIPOS.sebastian);
  const base = `/api/v1/proyectos/${codigo}`;

  // Mensajes: el cliente escribe con un tema; el Product Owner responde
  const hilo = await ok<{ puedeEscribir: boolean; temas: string[] }>(await cliente.api.get(`${base}/mensajes`));
  expect(hilo.puedeEscribir).toBe(true);
  expect((await cliente.api.post(`${base}/mensajes`, { data: { tema: hilo.temas[0], contenido: 'corto' } })).status()).toBe(422);
  const pregunta = '¿Cuándo podré ver el primer avance de mi sistema de inventario?';
  await ok(await cliente.api.post(`${base}/mensajes`, { data: { tema: hilo.temas[0], contenido: pregunta } }), 201);
  const respuesta = 'Esta semana terminamos la primera fase y te la mostramos en una reunión.';
  await ok(await po.api.post(`${base}/mensajes`, { data: { contenido: respuesta } }), 201);

  // Ambos lo ven en la pantalla del detalle (cliente) y del registro de trabajo (equipo)
  const { page: paginaCliente } = await abrirComo(browser, cliente);
  await paginaCliente.goto(`/cliente/proyectos/${codigo}`);
  await expect(paginaCliente.getByText(pregunta).first()).toBeVisible();
  await expect(paginaCliente.getByText(respuesta).first()).toBeVisible();
  const { page: paginaPo } = await abrirComo(browser, po);
  await paginaPo.goto(`/equipo/proyectos/${codigo}/registrar`);
  await expect(paginaPo.getByText(pregunta).first()).toBeVisible();

  // Solicitud de reunión: el cliente propone una hora (se prueban días hábiles hasta que uno no sea festivo)
  const reuniones = await ok<{ puedeSolicitar: boolean; motivos: string[] }>(await cliente.api.get(`${base}/solicitudes-reunion`));
  expect(reuniones.puedeSolicitar).toBe(true);
  const razon = 'Quiero revisar contigo el primer avance y resolver unas dudas.';
  let solicitud: { id: string; codigo: string; estado: string } | undefined;
  let hora = '';
  for (let desde = 3; !solicitud && desde < 20; desde++) {
    hora = `${diaHabil(desde)}T10:00:00-05:00`;
    const r = await cliente.api.post(`${base}/solicitudes-reunion`, { data: { motivo: reuniones.motivos[0], razon, modalidad: 'VIRTUAL', opcion1: hora } });
    if (r.status() === 201) solicitud = await r.json();
  }
  expect(solicitud?.estado).toBe('PENDIENTE');
  // Solo una solicitud en espera por proyecto (RN-15)
  const otra = await cliente.api.post(`${base}/solicitudes-reunion`, { data: { motivo: reuniones.motivos[0], razon, modalidad: 'VIRTUAL', opcion1: hora } });
  expect(otra.status()).toBe(409);

  // El Product Owner la ve en «Citas y reuniones» y la programa (virtual: necesita el enlace)
  await paginaPo.goto('/equipo/citas');
  await expect(paginaPo.getByText(solicitud!.codigo).first()).toBeVisible();
  expect((await po.api.patch(`/api/v1/solicitudes-reunion/${solicitud!.id}`, { data: { accion: 'PROGRAMAR', opcion: 1 } })).status()).toBe(422);
  const programada = await ok(
    await po.api.patch(`/api/v1/solicitudes-reunion/${solicitud!.id}`, { data: { accion: 'PROGRAMAR', opcion: 1, enlace: 'https://meet.google.com/xyz-abcd-efg' } }),
  );
  expect(programada.estado).toBe('PROGRAMADA');

  // El resumen espera a que la reunión ocurra; luego se llena y la solicitud queda ATENDIDA
  const resumen = {
    resumen: 'Revisamos el avance de la primera fase y el cliente quedó conforme con lo mostrado en la reunión.',
    quePidio: 'Pidió que el reporte de inventario se pueda descargar en Excel.',
    siguientePaso: 'DENTRO_DEL_PLAN',
  };
  expect((await po.api.post(`/api/v1/solicitudes-reunion/${solicitud!.id}/resumen`, { data: resumen })).status()).toBe(409);
  sql(`UPDATE solicitudes_reunion SET fecha_programada = now() - interval '1 hour' WHERE id = '${solicitud!.id}'`);
  const cerrada = await ok(await po.api.post(`/api/v1/solicitudes-reunion/${solicitud!.id}/resumen`, { data: resumen }), 201);
  expect(cerrada.estado).toBe('ATENDIDA');

  // El cliente ve el resumen de su reunión
  const vista = await ok<{ solicitudes: { id: string; resumen: { siguientePaso: string } | null }[] }>(await cliente.api.get(`${base}/solicitudes-reunion`));
  expect(vista.solicitudes.find((s) => s.id === solicitud!.id)?.resumen?.siguientePaso).toBe('DENTRO_DEL_PLAN');
});
