import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { dev } from '../../pruebas/especialidad';
import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

const ANDRES = dev('d-andres', 'Andrés Pérez', 'FULL_STACK', {
  tieneCuenta: true, equipo: { equipoId: 'e-1', productOwner: 'Andrés Pérez' }, rol: 'PRODUCT_OWNER',
});
const CAMILO = dev('d-camilo', 'Camilo Suárez', 'QA', { equipo: { equipoId: 'e-1', productOwner: 'Andrés Pérez' }, rol: 'QA' });
const KEVIN = dev('d-kevin', 'Kevin Rojas', 'FULL_STACK', { equipo: { equipoId: 'e-1', productOwner: 'Andrés Pérez' }, rol: 'BACKEND' });
const ANA = dev('d-ana', 'Ana María Vera', 'QA');
const DIEGO = dev('d-diego', 'Diego Ramírez', 'BACKEND', { activo: false });
const TODOS = [ANDRES, CAMILO, KEVIN, ANA, DIEGO];

const fila = async (nombre: string) => (await screen.findByText(nombre, { selector: 'strong' })).closest('tr') as HTMLElement;

function api(extra = {}) {
  return simularApi({ ...PATRICIA, usuarioId: 'u-patricia' }, { 'GET /desarrolladores': { estado: 200, cuerpo: TODOS }, ...extra });
}

describe('Registrar a los desarrolladores con su especialidad (HU-25)', () => {
  it('muestra el resumen por especialidad, la lista con su equipo y rol, y filtra', async () => {
    api();
    abrirEn('/admin/especialidad/desarrolladores');
    const andres = await fila('Andrés Pérez');
    expect(within(andres).getByText('Equipo de Andrés Pérez')).toBeInTheDocument();
    expect(within(andres).getByText('Product Owner · tiene cuenta')).toBeInTheDocument();
    expect(within(await fila('Camilo Suárez')).getByText('Rol: QA')).toBeInTheDocument();
    expect(within(await fila('Ana María Vera')).getByText('Sin equipo')).toBeInTheDocument();
    expect(await fila('Diego Ramírez')).toHaveClass('fila--apagada');
    expect(screen.getByText('disponibles sin equipo').closest('a')).toHaveTextContent('1 disponibles sin equipo');

    await userEvent.click(screen.getByRole('button', { name: /Sin equipo/ }));
    expect(screen.queryByText('Andrés Pérez', { selector: 'strong' })).not.toBeInTheDocument();
    expect(screen.getByText('Ana María Vera', { selector: 'strong' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Inactivos/ }));
    expect(screen.getByText('Diego Ramírez', { selector: 'strong' })).toBeInTheDocument();
  });

  it('valida cada campo con los mensajes del prototipo antes de enviar', async () => {
    const fetchSimulado = api();
    abrirEn('/admin/especialidad/desarrolladores');
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar desarrollador' }));
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }));
    expect(screen.getByText('Escribe el nombre completo.')).toBeInTheDocument();
    expect(screen.getByText('Escribe el número de celular.')).toBeInTheDocument();
    expect(screen.getByText('Escribe el correo.')).toBeInTheDocument();
    expect(screen.getByText('Elige la especialidad.')).toBeInTheDocument();
    expect(fetchSimulado).not.toHaveBeenCalledWith('/api/v1/desarrolladores', expect.objectContaining({ method: 'POST' }));

    await userEvent.type(screen.getByLabelText('Correo corporativo'), 'ana@gmail.com');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }));
    expect(screen.getByText('Usa el correo corporativo que termina en @coretechsolutions.co.')).toBeInTheDocument();
  });

  it('registra un desarrollador y avisa si el correo ya está registrado', async () => {
    const nuevo = dev('d-nuevo', 'Laura Gómez Ruiz', 'BACKEND');
    let intento = 0;
    const fetchSimulado = api({
      'POST /desarrolladores': () => (++intento === 1
        ? { estado: 409, cuerpo: errores({ campo: 'correo', mensaje: 'Ya hay un desarrollador registrado con este correo' }) }
        : { estado: 201, cuerpo: nuevo }),
    });
    abrirEn('/admin/especialidad/desarrolladores');
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar desarrollador' }));
    await userEvent.type(screen.getByLabelText('Nombre completo'), 'Laura Gómez Ruiz');
    await userEvent.type(screen.getByLabelText('Celular'), '3001234567');
    await userEvent.type(screen.getByLabelText('Correo corporativo'), 'laura.gomez@coretechsolutions.co');
    await userEvent.selectOptions(screen.getByLabelText('Especialidad'), 'BACKEND');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }));

    expect(await screen.findByText('Ya hay un desarrollador registrado con este correo')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/desarrolladores')).toEqual({
      nombre: 'Laura Gómez Ruiz', celular: '3001234567', correo: 'laura.gomez@coretechsolutions.co', especialidad: 'BACKEND',
    });
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }));
    expect(await screen.findByText('Laura Gómez Ruiz quedó registrado. Ya puedes sumarlo a un equipo.')).toBeInTheDocument();
  });

  it('avisa que el rol cambia si la nueva especialidad ya no lo permite', async () => {
    api();
    abrirEn('/admin/especialidad/desarrolladores');
    await userEvent.click(within(await fila('Kevin Rojas')).getByRole('button', { name: 'Editar a Kevin Rojas' }));
    await userEvent.selectOptions(screen.getByLabelText('Especialidad'), 'FRONTEND');
    expect(screen.getByText(/Con esta especialidad puede ser Frontend: al guardar, su rol cambia a Frontend/)).toBeInTheDocument();
  });

  it('muestra el error de la API al desactivar a un Product Owner con proyectos o a un único integrante', async () => {
    api({
      'PATCH /desarrolladores/d-andres/activo': {
        estado: 409, cuerpo: errores({ mensaje: 'Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo' }),
      },
    });
    abrirEn('/admin/especialidad/desarrolladores');
    await userEvent.click(within(await fila('Andrés Pérez')).getByLabelText('Más acciones para Andrés Pérez'));
    await userEvent.click(within(await fila('Andrés Pérez')).getByRole('menuitem', { name: 'Desactivar' }));
    const dialogo = screen.getByRole('dialog', { name: 'Desactivar desarrollador' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar' }));
    expect(await within(dialogo).findByText('Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo')).toBeInTheDocument();
    expect(within(dialogo).getByRole('link', { name: 'Ir a su equipo' })).toHaveAttribute('href', '/admin/especialidad/equipos#equipo-andres-perez');
  });
});
