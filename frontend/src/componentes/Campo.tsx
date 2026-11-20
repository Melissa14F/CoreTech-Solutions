import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';

import { Icono } from './Icono';

interface PropiedadesControl {
  id?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
  className?: string;
}

interface PropiedadesCampo {
  etiqueta: ReactNode;
  /** El control del formulario: input, select o textarea con la clase campo__control. */
  children: ReactElement<PropiedadesControl>;
  ayuda?: ReactNode;
  /** Mensaje de error del campo (el mismo texto que devuelve la API en un 422). */
  error?: string;
  opcional?: boolean;
  id?: string;
  /** Botón dentro del control, a la derecha (por ejemplo, mostrar u ocultar la contraseña): usa .campo__envoltura. */
  accion?: ReactNode;
  /** Contenido entre el control y el mensaje (por ejemplo, la lista de requisitos de la contraseña). */
  debajo?: ReactNode;
  /** Contenido después del mensaje de error (por ejemplo, un enlace de ayuda). */
  despues?: ReactNode;
  /** Contador "12 / 40" bajo el control (.campo__pie), para textos con largo máximo. */
  contador?: { actual: number; maximo: number };
}

/**
 * Campo de formulario del prototipo: etiqueta, control, ayuda y mensaje de error.
 * Enlaza el control con la etiqueta (htmlFor/id) y con la ayuda y el error (aria-describedby),
 * y marca aria-invalid cuando hay error, para que los lectores de pantalla lo anuncien.
 */
export function Campo({ etiqueta, children, ayuda, error, opcional = false, id, accion, debajo, despues, contador }: PropiedadesCampo) {
  const generado = useId();
  const idControl = id ?? children.props.id ?? `campo-${generado}`;
  const idAyuda = `${idControl}-ayuda`;
  const idMensaje = `${idControl}-mensaje`;
  const describe = [children.props['aria-describedby'], ayuda ? idAyuda : null, error ? idMensaje : null]
    .filter(Boolean).join(' ') || undefined;

  const control = isValidElement(children)
    ? cloneElement(children, {
        id: idControl,
        'aria-describedby': describe,
        'aria-invalid': error ? true : undefined,
        className: ['campo__control', children.props.className].filter(Boolean).join(' '),
      })
    : children;

  const mensaje = error ? (
    <p className="campo__mensaje" id={idMensaje} role="alert">
      <Icono nombre="alerta" />
      <span className="campo__mensaje-texto">{error}</span>
    </p>
  ) : null;

  return (
    <div className={['campo', error && 'campo--invalido'].filter(Boolean).join(' ')}>
      <label className="campo__etiqueta" htmlFor={idControl}>
        {etiqueta}
        {opcional && <span className="campo__opcional"> (opcional)</span>}
      </label>
      {accion ? <div className="campo__envoltura">{control}{accion}</div> : control}
      {ayuda && <p className="campo__ayuda" id={idAyuda}>{ayuda}</p>}
      {debajo}
      {contador ? (
        <div className="campo__pie">
          {mensaje ?? <span />}
          <span className="campo__contador" aria-hidden="true">{contador.actual} / {contador.maximo}</span>
        </div>
      ) : mensaje}
      {despues}
    </div>
  );
}
