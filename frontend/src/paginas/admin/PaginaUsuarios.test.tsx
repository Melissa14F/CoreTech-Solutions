import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { PATRICIA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';
import { USUARIOS, U_ANDRES, U_LAURA } from '../../pruebas/administracion';

function api(extra = {}) {
  return simularApi({ ...PATRICIA, usuarioId: 'u-patricia' }, {
    'GET /usuarios': { estado: 200, cuerpo: USUARIOS },
    ...extra,
  });
}

/** Abre «Más acciones» de una fila; jsdom no oculta el contenido de un <details> cerrado, por eso se busca dentro de la fila. */
const abrirMenu = async (nombre: string) => userEvent.click(within(await fila(nombre)).getByLabelText(`Más acciones para ${nombre}`));

const fila = async (nombre: string) => (await screen.findByText(nombre, { selector: 'strong' })).closest('tr') as HTMLElement;

describe('Gestionar las cuentas de usuario (HU-20)', () => {
  it('lista las cuentas con su rol, actividad y estado, y filtra por rol y por texto', async () => {
    api();
    abrirEn('/admin/usuarios');
    const andres = await fila('Andrés Pérez');
    expect(within(andres).getByText('Product Owner')).toBeInTheDocument();
    expect(within(andres).getByText('Equipo de Andrés Pérez')).toBeInTheDocument();
    expect(within(andres).getByRole('img', { name: '2 de 3 proyectos activos' })).toBeInTheDocument();
    expect(within(await fila('Laura Gómez')).getByText('2 proyectos · 1 cita pendiente')).toBeInTheDocument();
    expect(within(await fila('Sofía Navarro')).getByText('Bloqueada')).toBeInTheDocument();
    expect(await fila('Diego Ramírez')).toHaveClass('fila--apagada');
    // Su propia cuenta no tiene "Más acciones"
    expect(within(await fila('Patricia León')).getByText('Tu cuenta')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Clientes/ }));
    expect(screen.queryByText('Andrés Pérez', { selector: 'strong' })).not.toBeInTheDocument();
    expect(screen.getByText('2 resultados')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Todos/ }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar usuario' }), 'ramirez');
    expect(screen.getByText('1 resultado')).toBeInTheDocument();
    await userEvent.clear(screen.getByRole('searchbox', { name: 'Buscar usuario' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar usuario' }), 'nadie');
    expect(screen.getByText('No hay usuarios con estos filtros')).toBeInTheDocument();
  });

  it('crea una cuenta de administrador sin mostrar la contraseña temporal', async () => {
    const nueva = { ...U_LAURA, usuarioId: 'u-daniela', nombre: 'Daniela Castro', email: 'daniela.castro@coretechsolutions.co', rol: 'ADMINISTRADOR', debeCambiarPassword: true };
    const fetchSimulado = api({ 'POST /usuarios': { estado: 201, cuerpo: nueva } });
    abrirEn('/admin/usuarios');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear cuenta de administrador' }));

    expect(screen.getByText(/le llegará por correo/)).toBeInTheDocument();
    expect(screen.queryByText(/^Ct-/)).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Nombre completo'), 'Daniela  Castro');
    await userEvent.type(screen.getByLabelText('Celular'), '3001234567');
    await userEvent.type(screen.getByLabelText('Correo'), 'Daniela.Castro@coretechsolutions.co');
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Cuenta creada. Enviamos los datos de acceso a daniela.castro@coretechsolutions.co.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/usuarios')).toEqual({
      nombre: 'Daniela Castro', celular: '3001234567', email: 'daniela.castro@coretechsolutions.co',
    });
    expect(await fila('Daniela Castro')).toBeInTheDocument();
  });

  it('marca cada campo con el mensaje del prototipo y el correo repetido que responde la API', async () => {
    const fetchSimulado = api({ 'POST /usuarios': { estado: 409, cuerpo: errores({ campo: 'email', mensaje: 'Ya existe una cuenta con este correo' }) } });
    abrirEn('/admin/usuarios');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear cuenta de administrador' }));
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(screen.getByText('Escribe el nombre completo.')).toBeInTheDocument();
    expect(screen.getByText('Escribe el número de celular.')).toBeInTheDocument();
    expect(screen.getByText('Escribe el correo.')).toBeInTheDocument();
    expect(fetchSimulado).not.toHaveBeenCalledWith('/api/v1/usuarios', expect.objectContaining({ method: 'POST' }));

    await userEvent.type(screen.getByLabelText('Nombre completo'), 'Daniela Castro');
    await userEvent.type(screen.getByLabelText('Celular'), '3001234567');
    await userEvent.type(screen.getByLabelText('Correo'), 'daniela@gmail.com');
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(screen.getByText('Usa el correo corporativo que termina en @coretechsolutions.co.')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Correo'));
    await userEvent.type(screen.getByLabelText('Correo'), 'patricia.leon@coretechsolutions.co');
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(await screen.findByText('Ya existe una cuenta con este correo')).toBeInTheDocument();
    expect(screen.getByLabelText('Correo')).toHaveAttribute('aria-invalid', 'true');
  });

  it('no deja desactivar a un Product Owner con proyectos activos', async () => {
    api();
    abrirEn('/admin/usuarios');
    await abrirMenu('Andrés Pérez');
    await userEvent.click(within(await fila('Andrés Pérez')).getByRole('menuitem', { name: 'Desactivar cuenta' }));

    const dialogo = screen.getByRole('dialog', { name: 'Desactivar cuenta' });
    expect(within(dialogo).getByText('Andrés Pérez tiene 2 proyectos activos.')).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Desactivar' })).toBeDisabled();
  });

  it('desactiva una cuenta y muestra el error de la API si no se puede', async () => {
    const fetchSimulado = api({
      'PATCH /usuarios/u-laura/activo': { estado: 200, cuerpo: { ...U_LAURA, activo: false } },
      'PATCH /usuarios/u-andres/activo': { estado: 409, cuerpo: errores({ mensaje: 'Elige otro desarrollador (Product Owner) para su equipo antes de desactivarlo' }) },
    });
    abrirEn('/admin/usuarios');
    await abrirMenu('Laura Gómez');
    await userEvent.click(within(await fila('Laura Gómez')).getByRole('menuitem', { name: 'Desactivar cuenta' }));
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Desactivar cuenta' })).getByRole('button', { name: 'Desactivar' }));

    expect(await screen.findByText('Cuenta de Laura Gómez desactivada.')).toBeInTheDocument();
    expect(cuerpoEnviado(fetchSimulado, 'PATCH', '/usuarios/u-laura/activo')).toEqual({ activo: false });
    await waitFor(async () => expect(within(await fila('Laura Gómez')).getByText('Inactivo')).toBeInTheDocument());
  });

  it('anonimiza los datos de un cliente después de confirmar', async () => {
    const fetchSimulado = api({
      'POST /usuarios/u-laura/anonimizar': { estado: 200, cuerpo: { ...U_LAURA, nombre: 'Cliente anónimo', email: 'anonimo-x@anonimo.invalid', activo: false, anonimizada: true } },
    });
    abrirEn('/admin/usuarios');
    await abrirMenu('Laura Gómez');
    await userEvent.click(within(await fila('Laura Gómez')).getByRole('menuitem', { name: 'Anonimizar datos' }));
    const dialogo = screen.getByRole('dialog', { name: 'Anonimizar datos' });
    expect(within(dialogo).getByText(/Esta acción no se puede deshacer/)).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Anonimizar datos' }));

    expect(await screen.findByText('Los datos de Laura Gómez quedaron anonimizados.')).toBeInTheDocument();
    expect(fetchSimulado).toHaveBeenCalledWith('/api/v1/usuarios/u-laura/anonimizar', expect.objectContaining({ method: 'POST' }));
    expect(within(await fila('Cliente anónimo')).getByText('Datos anonimizados')).toBeInTheDocument();
  });

  it('desbloquea una cuenta bloqueada y envía el enlace para restablecer la contraseña', async () => {
    api({
      'POST /usuarios/u-sofia/desbloquear': { estado: 200, cuerpo: { ...USUARIOS[4], bloqueadaHasta: null } },
      'POST /usuarios/u-andres/recuperacion': { estado: 201, cuerpo: { enviadoA: U_ANDRES.email, expiraEnMinutos: 30 } },
    });
    abrirEn('/admin/usuarios');
    await abrirMenu('Sofía Navarro');
    await userEvent.click(within(await fila('Sofía Navarro')).getByRole('menuitem', { name: 'Desbloquear cuenta' }));
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Desbloquear cuenta' })).getByRole('button', { name: 'Desbloquear' }));
    expect(await screen.findByText('Cuenta de Sofía Navarro desbloqueada.')).toBeInTheDocument();

    await abrirMenu('Andrés Pérez');
    await userEvent.click(within(await fila('Andrés Pérez')).getByRole('menuitem', { name: 'Restablecer contraseña' }));
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Restablecer contraseña' })).getByRole('button', { name: 'Enviar enlace' }));
    expect(await screen.findByText(`Enviamos el enlace para restablecer la contraseña a ${U_ANDRES.email}.`)).toBeInTheDocument();
  });
});
