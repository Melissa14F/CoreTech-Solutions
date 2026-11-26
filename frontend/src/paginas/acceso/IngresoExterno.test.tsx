import { cleanup, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { LAURA, abrirEn, cuerpoEnviado, errores, simularApi } from '../../pruebas/aplicacion';

const PROVEEDORES = [
  { codigo: 'GOOGLE', nombre: 'Google', url: '/api/v1/auth/externo/google' },
  { codigo: 'MICROSOFT', nombre: 'Microsoft', url: '/api/v1/auth/externo/microsoft' },
];
const PENDIENTE = { proveedor: 'Google', nombre: 'Sofía Duarte', email: 'sofia.duarte@gmail.com' };

describe('ingreso con Google y Microsoft (RF-50)', () => {
  it('Iniciar sesión y Crear cuenta muestran un botón por proveedor activo, como enlaces al servidor', async () => {
    for (const ruta of ['/ingresar', '/registro']) {
      simularApi(null, { 'GET /auth/proveedores': { estado: 200, cuerpo: PROVEEDORES } });
      abrirEn(ruta);
      const google = await screen.findByRole('link', { name: 'Continuar con Google' });
      expect(google).toHaveAttribute('href', '/api/v1/auth/externo/google');
      expect(screen.getByRole('link', { name: 'Continuar con Microsoft' })).toHaveAttribute('href', '/api/v1/auth/externo/microsoft');
      expect(screen.getByText(/^o (ingresa|regístrate) con tu correo$/)).toBeInTheDocument();
      cleanup(); // desmonta la pantalla anterior (no vaciar el <body> a mano: el panel de accesibilidad vive en un portal)
    }
  });

  it('sin proveedores configurados no se muestra nada y el formulario de siempre sigue ahí', async () => {
    simularApi(null, { 'GET /auth/proveedores': { estado: 200, cuerpo: [] } });
    abrirEn('/ingresar');
    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Continuar con/ })).not.toBeInTheDocument();
  });

  it('solo se ofrece uno si el otro no está configurado', async () => {
    simularApi(null, { 'GET /auth/proveedores': { estado: 200, cuerpo: [PROVEEDORES[0]] } });
    abrirEn('/registro');
    expect(await screen.findByRole('link', { name: 'Continuar con Google' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Continuar con Microsoft' })).not.toBeInTheDocument();
  });

  it.each([
    ['cancelado&proveedor=google', 'Cancelaste el ingreso con Google. Puedes intentarlo de nuevo o usar tu correo y contraseña.'],
    ['correo_sin_verificar&proveedor=microsoft', 'Microsoft aún no ha verificado tu correo. Verifícalo allá o ingresa con tu correo y contraseña.'],
    ['solo_clientes&proveedor=google', 'Esa cuenta es del equipo de CoreTech: ingresa con tu correo y contraseña.'],
    ['error&proveedor=microsoft', 'No pudimos ingresar con Microsoft. Intenta de nuevo en unos minutos.'],
    ['vencido', 'El ingreso tardó demasiado. Vuelve a intentarlo.'],
  ])('al volver sin entrar (?externo=%s) lo explica sin tecnicismos', async (consulta, mensaje) => {
    simularApi(null);
    abrirEn(`/ingresar?externo=${consulta}`);
    expect(await screen.findByText(mensaje)).toBeInTheDocument();
  });

  it('un motivo desconocido no muestra nada', async () => {
    simularApi(null);
    abrirEn('/ingresar?externo=inventado');
    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  describe('Completa tu registro', () => {
    it('muestra el nombre y el correo del proveedor, y crea la cuenta con lo que falta', async () => {
      const fetchSimulado = simularApi(null, {
        'GET /auth/externo/pendiente': { estado: 200, cuerpo: PENDIENTE },
        'POST /auth/externo/completar': { estado: 201, cuerpo: { usuarioId: 'u1', rol: 'CLIENTE', nombre: 'Sofía Duarte' } },
      });
      const enrutador = abrirEn('/completar-registro');

      expect(await screen.findByText(/Entraste con tu cuenta de Google/)).toBeInTheDocument();
      expect(screen.getByLabelText('Nombre completo')).toHaveValue('Sofía Duarte');
      const correo = screen.getByLabelText('Correo electrónico');
      expect(correo).toHaveValue('sofia.duarte@gmail.com');
      expect(correo).toHaveAttribute('readonly');
      expect(screen.getByText(/ahí te llegarán la bienvenida y todos los avisos del portal/)).toBeInTheDocument();

      // Después de crearla, la sesión ya existe: la lee de /auth/yo
      simularApi(LAURA, {
        'GET /auth/externo/pendiente': { estado: 200, cuerpo: PENDIENTE },
        'POST /auth/externo/completar': { estado: 201, cuerpo: { usuarioId: 'u1', rol: 'CLIENTE', nombre: 'Sofía Duarte' } },
      });
      await userEvent.type(screen.getByLabelText('Celular'), '3142208815');
      await userEvent.type(screen.getByLabelText(/Empresa/), 'Duarte SAS');
      await userEvent.click(screen.getByRole('checkbox'));
      await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta y agendar' }));

      await waitFor(() => expect(enrutador.state.location.pathname).toBe('/cliente/agendar'));
      expect(fetchSimulado.mock.calls.length).toBeGreaterThan(0);
    });

    it('envía solo lo que la persona escribió (el correo lo pone el servidor)', async () => {
      const fetchSimulado = simularApi(null, {
        'GET /auth/externo/pendiente': { estado: 200, cuerpo: PENDIENTE },
        'POST /auth/externo/completar': { estado: 422, cuerpo: errores({ campo: 'celular', mensaje: 'Escribe 10 dígitos que empiecen por 3.' }) },
      });
      abrirEn('/completar-registro');
      await screen.findByLabelText('Celular');
      await userEvent.type(screen.getByLabelText('Celular'), '3142208815');
      await userEvent.click(screen.getByRole('checkbox'));
      await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta y agendar' }));

      await waitFor(() => expect(cuerpoEnviado(fetchSimulado, 'POST', '/auth/externo/completar')).toEqual({
        nombre: 'Sofía Duarte', celular: '3142208815', empresa: '', aceptaDatos: true,
      }));
      expect(await screen.findByText('Escribe 10 dígitos que empiecen por 3.')).toBeInTheDocument();
    });

    it('valida celular y política antes de enviar, con los mismos mensajes del registro', async () => {
      const fetchSimulado = simularApi(null, { 'GET /auth/externo/pendiente': { estado: 200, cuerpo: PENDIENTE } });
      abrirEn('/completar-registro');
      await screen.findByLabelText('Celular');
      await userEvent.type(screen.getByLabelText('Celular'), '12345');
      await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta y agendar' }));

      expect(await screen.findByText('Escribe 10 dígitos que empiecen por 3.')).toBeInTheDocument();
      expect(screen.getByText('Debes aceptar la política de datos para crear la cuenta.')).toBeInTheDocument();
      expect(fetchSimulado.mock.calls.some(([url, op]) => String(url).endsWith('/completar') && op?.method === 'POST')).toBe(false);
    });

    it('si el registro venció, lo dice y ofrece empezar de nuevo', async () => {
      simularApi(null, {
        'GET /auth/externo/pendiente': { estado: 409, cuerpo: errores({ mensaje: 'El registro tardó demasiado. Vuelve a elegir con qué cuenta registrarte.' }) },
      });
      abrirEn('/completar-registro');
      expect(await screen.findByText('El registro tardó demasiado. Vuelve a elegir con qué cuenta registrarte.')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Volver a registrarme' })).toHaveAttribute('href', '/registro');
    });
  });
});
