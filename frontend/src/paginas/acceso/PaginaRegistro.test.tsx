import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { LAURA, abrirEn, cuerpoEnviado, errores, simularApi, type RespuestaSimulada } from '../../pruebas/aplicacion';

function apiConRegistro(registro: RespuestaSimulada = { estado: 201, cuerpo: {} }) {
  let conectado = false;
  return simularApi(null, {
    'GET /auth/yo': () => (conectado ? { estado: 200, cuerpo: LAURA } : { estado: 401 }),
    'POST /auth/registro': () => {
      conectado = registro.estado === 201;
      return registro;
    },
  });
}

async function llenar() {
  await userEvent.type(await screen.findByLabelText('Nombre completo'), '  Laura Gómez ');
  await userEvent.type(screen.getByLabelText('Correo electrónico'), 'laura.gomez@correo.com');
  await userEvent.type(screen.getByLabelText('Celular'), '3001234567');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'Demo2026!');
  await userEvent.type(screen.getByLabelText('Confirma la contraseña'), 'Demo2026!');
  await userEvent.click(screen.getByRole('checkbox', { name: /política de tratamiento de datos/ }));
}

const crearCuenta = () => userEvent.click(screen.getByRole('button', { name: 'Crear cuenta y agendar' }));

describe('Crear cuenta (HU-05)', () => {
  it('sin datos muestra los mensajes del prototipo bajo cada campo', async () => {
    simularApi(null);
    abrirEn('/registro');
    await userEvent.click(await screen.findByRole('button', { name: 'Crear cuenta y agendar' }));

    expect(screen.getByText('Escribe tu nombre completo.')).toBeInTheDocument();
    expect(screen.getByText('Escribe tu correo; ahí te enviaremos la confirmación de la cita.')).toBeInTheDocument();
    expect(screen.getByText('Escribe tu número de celular.')).toBeInTheDocument();
    expect(screen.getByText('Crea una contraseña.')).toBeInTheDocument();
    expect(screen.getByText('Escribe de nuevo la contraseña.')).toBeInTheDocument();
    expect(screen.getByText('Debes aceptar la política de datos para crear la cuenta.')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre completo')).toHaveFocus();
  });

  it('marca los requisitos de la contraseña mientras se escribe', async () => {
    simularApi(null);
    abrirEn('/registro');
    const lista = await screen.findByRole('list', { name: 'Requisitos de la contraseña' });
    const largo = within(lista).getByText('Mínimo 8 caracteres');
    expect(largo).not.toHaveClass('requisitos__item--cumple');

    await userEvent.type(screen.getByLabelText('Contraseña'), 'Demo2026!');
    expect(largo).toHaveClass('requisitos__item--cumple');
    expect(within(lista).getByText('Un símbolo (!, #, @…)')).toHaveClass('requisitos__item--cumple');
  });

  it('el botón del ojo muestra y oculta la contraseña', async () => {
    simularApi(null);
    abrirEn('/registro');
    const campo = await screen.findByLabelText('Contraseña');
    const [boton] = screen.getAllByRole('button', { name: 'Mostrar contraseña' });
    await userEvent.click(boton!);
    expect(campo).toHaveAttribute('type', 'text');
    expect(boton).toHaveAttribute('aria-pressed', 'true');
  });

  it('crea la cuenta y lleva a agendar la cita', async () => {
    const fetchSimulado = apiConRegistro();
    const enrutador = abrirEn('/registro');
    await llenar();
    await crearCuenta();

    await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/agendar'));
    expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/registro')).toEqual({
      nombre: 'Laura Gómez', email: 'laura.gomez@correo.com', celular: '3001234567', empresa: '',
      password: 'Demo2026!', confirmacion: 'Demo2026!', aceptaDatos: true,
    });
  });

  it('con un correo ya registrado lo dice bajo el correo y ofrece iniciar sesión (D-38)', async () => {
    apiConRegistro({ estado: 409, cuerpo: errores({ campo: 'email', mensaje: 'Ya existe una cuenta con este correo' }) });
    abrirEn('/registro');
    await llenar();
    await crearCuenta();

    expect(await screen.findByText('Ya existe una cuenta con este correo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '¿Quieres iniciar sesión?' })).toHaveAttribute('href', '/ingresar');
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('aria-invalid', 'true');
  });

  it('muestra bajo su campo los errores que devuelve el servidor (422)', async () => {
    apiConRegistro({ estado: 422, cuerpo: errores({ campo: 'celular', mensaje: 'Escribe 10 dígitos que empiecen por 3.' }) });
    abrirEn('/registro');
    await llenar();
    await crearCuenta();

    expect(await screen.findByText('Escribe 10 dígitos que empiecen por 3.')).toBeInTheDocument();
    expect(screen.getByLabelText('Celular')).toHaveAttribute('aria-invalid', 'true');
  });
});
