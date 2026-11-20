import { render, screen, within } from '@testing-library/react';

import { EstadoVacio } from './EstadoVacio';
import { Tabla, type ColumnaTabla } from './Tabla';

interface Cita {
  codigo: string;
  cliente: string;
  estado: string;
}

const columnas: ColumnaTabla<Cita>[] = [
  { clave: 'codigo', titulo: 'Código', celda: (c) => c.codigo },
  { clave: 'cliente', titulo: 'Cliente', celda: (c) => c.cliente, ancha: true },
  { clave: 'estado', titulo: 'Estado', celda: (c) => c.estado },
  { clave: 'acciones', titulo: 'Acciones', tituloOculto: true, celda: () => <button type="button">Ver</button> },
];

const citas: Cita[] = [
  { codigo: 'C-0064', cliente: 'Laura Gómez', estado: 'Pendiente' },
  { codigo: 'C-0065', cliente: 'Carlos Duarte', estado: 'Confirmada' },
];

describe('Tabla', () => {
  it('tiene un nombre accesible y encabezados de columna', () => {
    render(<Tabla titulo="Citas de la semana" columnas={columnas} filas={citas} claveFila={(c) => c.codigo} />);

    const tabla = screen.getByRole('table', { name: 'Citas de la semana' });
    expect(within(tabla).getAllByRole('columnheader').map((th) => th.textContent)).toEqual(['Código', 'Cliente', 'Estado', 'Acciones']);
  });

  it('muestra una fila por registro', () => {
    render(<Tabla titulo="Citas" columnas={columnas} filas={citas} claveFila={(c) => c.codigo} />);

    // 1 fila de encabezados + 2 de datos
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(screen.getByRole('cell', { name: 'Laura Gómez' })).toBeInTheDocument();
  });

  it('pone en cada celda la etiqueta de su columna, para verla como tarjeta en pantallas medianas', () => {
    render(<Tabla titulo="Citas" columnas={columnas} filas={citas} claveFila={(c) => c.codigo} />);

    const celda = screen.getByRole('cell', { name: 'C-0064' });
    expect(celda).toHaveAttribute('data-etiqueta', 'Código');
    expect(screen.getByRole('cell', { name: 'Laura Gómez' })).toHaveClass('celda--ancha');
  });

  it('oculta a la vista el encabezado de acciones pero lo deja para lectores de pantalla', () => {
    render(<Tabla titulo="Citas" columnas={columnas} filas={citas} claveFila={(c) => c.codigo} />);

    const encabezado = screen.getByRole('columnheader', { name: 'Acciones' });
    expect(within(encabezado).getByText('Acciones')).toHaveClass('solo-lector');
    expect(screen.getAllByRole('button', { name: 'Ver' })[0]?.closest('td')).not.toHaveAttribute('data-etiqueta');
  });

  it('sin filas muestra el estado vacío ocupando todas las columnas', () => {
    render(
      <Tabla
        titulo="Citas"
        columnas={columnas}
        filas={[]}
        claveFila={(c) => c.codigo}
        vacio={<EstadoVacio titulo="No hay citas con estos filtros" />}
      />,
    );

    const vacio = screen.getByRole('heading', { name: 'No hay citas con estos filtros' });
    expect(vacio.closest('td')).toHaveAttribute('colspan', '4');
  });
});
