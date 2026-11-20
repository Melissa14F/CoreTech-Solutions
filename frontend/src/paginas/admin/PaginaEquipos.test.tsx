import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Equipo, Miembro } from '../../especialidad/api';
import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { dev } from '../../pruebas/especialidad';

const miembro = (id: string, nombre: string, especialidad: Miembro['especialidad'], rol: Miembro['rol']): Miembro =>
  ({ desarrolladorId: id, nombre, correo: `${id}@coretechsolutions.co`, especialidad, activo: true, rol });

const EQUIPO: Equipo = {
  equipoId: 'e-1',
  productOwner: miembro('d-andres', 'Andrés Pérez', 'FULL_STACK', 'PRODUCT_OWNER'),
  integrantes: [miembro('d-camilo', 'Camilo Suárez', 'QA', 'QA')],
  proyectosActivos: 2,
  maxProyectos: 3,
};
const EN_EQUIPO = { equipoId: 'e-1', productOwner: 'Andrés Pérez' };
const DEVS = [
  dev('d-andres', 'Andrés Pérez', 'FULL_STACK', { equipo: EN_EQUIPO, rol: 'PRODUCT_OWNER', tieneCuenta: true }),
  dev('d-camilo', 'Camilo Suárez', 'QA', { equipo: EN_EQUIPO, rol: 'QA' }),
  dev('d-ana', 'Ana María Vera', 'QA'),
  dev('d-jhon', 'Jhon Pabón', 'BACKEND'),
  dev('d-kevin', 'Kevin Rojas', 'FULL_STACK'),
];

function api(devs = DEVS, extra = {}) {
  return simularApi({ ...PATRICIA, usuarioId: 'u-patricia' }, {
    'GET /equipos': { estado: 200, cuerpo: [EQUIPO] },
    'GET /desarrolladores': { estado: 200, cuerpo: devs },
    ...extra,
  });
}

describe('Armar los equipos de trabajo (HU-26)', () => {
  it('muestra cada equipo con su Product Owner, integrantes y carga, y los disponibles', async () => {
    api();
    abrirEn('/admin/especialidad/equipos');
    const tarjeta = (await screen.findByRole('heading', { name: 'Equipo de Andrés Pérez' })).closest('article') as HTMLElement;
    expect(tarjeta).toHaveAttribute('id', 'equipo-andres-perez');
    expect(within(tarjeta).getByText('2 integrantes · solo Andrés tiene cuenta')).toBeInTheDocument();
    expect(within(tarjeta).getByRole('img', { name: '2 de 3 proyectos activos' })).toBeInTheDocument();
    expect(within(tarjeta).getByText('Product Owner')).toBeInTheDocument();
    const disponibles = screen.getByRole('complementary', { name: 'Disponibles sin equipo' });
    expect(within(disponibles).getByText('Ana María Vera')).toBeInTheDocument();
    expect(within(disponibles).queryByText('Camilo Suárez')).not.toBeInTheDocument();
  });

  it('pide al menos 2 desarrolladores sin equipo para crear uno', async () => {
    api([DEVS[0]!, DEVS[1]!, DEVS[2]!]);
    abrirEn('/admin/especialidad/equipos');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear equipo' }));
    expect(await screen.findByText('Necesitas al menos 2 desarrolladores sin equipo. Regístralos en Desarrolladores.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('valida integrantes y avisa que la contraseña temporal llegará por correo, sin mostrarla', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/especialidad/equipos');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear equipo' }));
    const dialogo = screen.getByRole('dialog', { name: 'Crear equipo' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear equipo' }));
    expect(within(dialogo).getByText('Elige al Product Owner del equipo.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Agrega al menos un integrante además del desarrollador (Product Owner)')).toBeInTheDocument();

    await userEvent.selectOptions(within(dialogo).getByLabelText('Product Owner'), 'd-ana');
    expect(within(dialogo).getByText(/no se muestra en pantalla/)).toBeInTheDocument();
    expect(dialogo.textContent).not.toMatch(/Ct-/);
    // El Product Owner elegido ya no se ofrece como integrante
    expect(within(within(dialogo).getByLabelText('Desarrollador 1')).queryByRole('option', { name: /Ana María Vera/ })).not.toBeInTheDocument();
    expect(fetchSimulado).not.toHaveBeenCalledWith('/api/v1/equipos', expect.objectContaining({ method: 'POST' }));
  });

  it('crea el equipo con los roles que permite cada especialidad', async () => {
    const creado: Equipo = {
      equipoId: 'e-2', productOwner: miembro('d-ana', 'Ana María Vera', 'QA', 'PRODUCT_OWNER'),
      integrantes: [miembro('d-kevin', 'Kevin Rojas', 'FULL_STACK', 'BACKEND')], proyectosActivos: 0, maxProyectos: 3,
    };
    const fetchSimulado = api(DEVS, { 'POST /equipos': { estado: 201, cuerpo: creado } });
    abrirEn('/admin/especialidad/equipos');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear equipo' }));
    const dialogo = screen.getByRole('dialog', { name: 'Crear equipo' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('Product Owner'), 'd-ana');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Desarrollador 1'), 'd-kevin');
    const roles = within(dialogo).getByLabelText('Rol en el equipo 1');
    expect(within(roles).getAllByRole('option').map((o) => o.textContent)).toEqual(['Full stack', 'Frontend', 'Backend']);
    await userEvent.selectOptions(roles, 'BACKEND');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear equipo' }));

    expect(cuerpoEnviado(fetchSimulado, 'POST', '/equipos')).toEqual({
      productOwnerId: 'd-ana', integrantes: [{ desarrolladorId: 'd-kevin', rol: 'BACKEND' }],
    });
    expect(await screen.findByText('Equipo de Ana María Vera creado. Enviamos los datos de acceso a Ana.')).toBeInTheDocument();
  });

  it('al cambiar el Product Owner, el anterior queda como integrante y se explica el cambio', async () => {
    const fetchSimulado = api(DEVS, { 'PUT /equipos/e-1': { estado: 200, cuerpo: EQUIPO } });
    abrirEn('/admin/especialidad/equipos');
    await userEvent.click(await screen.findByRole('button', { name: 'Editar equipo de Andrés Pérez' }));
    const dialogo = screen.getByRole('dialog', { name: 'Editar equipo de Andrés Pérez' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('Product Owner'), 'd-kevin');

    expect(within(dialogo).getByText(/Kevin Rojas tendrá cuenta y quedará a cargo de los 2 proyectos activos del equipo/)).toBeInTheDocument();
    expect(within(dialogo).getByText(/Andrés Pérez pierde el acceso a la plataforma y queda como integrante/)).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));
    expect(cuerpoEnviado(fetchSimulado, 'PUT', '/equipos/e-1')).toEqual({
      productOwnerId: 'd-kevin',
      integrantes: [{ desarrolladorId: 'd-camilo', rol: 'QA' }, { desarrolladorId: 'd-andres', rol: 'FULL_STACK' }],
    });
  });

  it('muestra los errores de la API en el formulario', async () => {
    api(DEVS, { 'POST /equipos': { estado: 409, cuerpo: errores({ campo: 'integrantes', mensaje: 'Este desarrollador ya está en otro equipo' }) } });
    abrirEn('/admin/especialidad/equipos');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear equipo' }));
    const dialogo = screen.getByRole('dialog', { name: 'Crear equipo' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('Product Owner'), 'd-ana');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Desarrollador 1'), 'd-jhon');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear equipo' }));
    expect(await within(dialogo).findByText('Este desarrollador ya está en otro equipo')).toBeInTheDocument();
  });
});
