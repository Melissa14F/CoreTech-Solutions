import { render, screen } from '@testing-library/react';

import { Campo } from './Campo';

describe('Campo', () => {
  it('enlaza la etiqueta con el control', () => {
    render(
      <Campo etiqueta="Nombre completo">
        <input type="text" />
      </Campo>,
    );

    expect(screen.getByLabelText('Nombre completo')).toHaveClass('campo__control');
  });

  it('sin error no marca el control como inválido', () => {
    render(
      <Campo etiqueta="Correo">
        <input type="email" />
      </Campo>,
    );

    const control = screen.getByLabelText('Correo');
    expect(control).not.toHaveAttribute('aria-invalid');
    expect(control).not.toHaveAttribute('aria-describedby');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('muestra el error y lo enlaza con aria-describedby', () => {
    render(
      <Campo etiqueta="Descripción de la necesidad" error="Cuéntanos un poco más (mínimo 20 caracteres)">
        <textarea />
      </Campo>,
    );

    const control = screen.getByLabelText('Descripción de la necesidad');
    const mensaje = screen.getByRole('alert');
    expect(mensaje).toHaveTextContent('Cuéntanos un poco más (mínimo 20 caracteres)');
    expect(control).toHaveAttribute('aria-invalid', 'true');
    expect(control.getAttribute('aria-describedby')?.split(' ')).toContain(mensaje.id);
    expect(control).toHaveAccessibleDescription('Cuéntanos un poco más (mínimo 20 caracteres)');
  });

  it('describe el control con la ayuda y con el error a la vez', () => {
    render(
      <Campo etiqueta="Celular" ayuda="10 dígitos, empieza por 3" error="Escribe un celular de 10 dígitos que empiece por 3">
        <input type="tel" />
      </Campo>,
    );

    expect(screen.getByLabelText('Celular')).toHaveAccessibleDescription(
      '10 dígitos, empieza por 3 Escribe un celular de 10 dígitos que empiece por 3',
    );
  });

  it('respeta el id que ya trae el control', () => {
    render(
      <Campo etiqueta="Empresa" opcional>
        <input id="registro-empresa" />
      </Campo>,
    );

    expect(screen.getByLabelText(/Empresa/)).toHaveAttribute('id', 'registro-empresa');
    expect(screen.getByText('(opcional)')).toBeInTheDocument();
  });
});
