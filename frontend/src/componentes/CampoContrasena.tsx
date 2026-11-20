import { useId, useState, type ReactNode, type Ref } from 'react';

import { MAXIMO_CONTRASENA, REQUISITOS, cumpleRegla } from '../sesion/politicaContrasena';
import { Campo } from './Campo';
import { Icono } from './Icono';

interface PropiedadesCampoContrasena {
  etiqueta: string;
  valor: string;
  alCambiar: (valor: string) => void;
  autoComplete: 'current-password' | 'new-password';
  nombre: string;
  error?: string;
  ayuda?: ReactNode;
  /** Muestra la lista de requisitos de RN-22, que se marcan mientras se escribe (contraseña nueva). */
  conRequisitos?: boolean;
  referencia?: Ref<HTMLInputElement>;
}

/**
 * Campo de contraseña del prototipo: botón para mostrarla u ocultarla (aria-pressed) y, si es una
 * contraseña nueva, los requisitos de RN-22 marcados en vivo (registro.css, .requisitos).
 */
export function CampoContrasena({ etiqueta, valor, alCambiar, autoComplete, nombre, error, ayuda, conRequisitos = false, referencia }: PropiedadesCampoContrasena) {
  const [visible, setVisible] = useState(false);
  const idRequisitos = `requisitos-${useId()}`;

  return (
    <Campo
      etiqueta={etiqueta}
      error={error}
      ayuda={ayuda}
      accion={
        <button
          type="button"
          className="campo__accion"
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={visible}
          onClick={() => setVisible((v) => !v)}
        >
          <Icono nombre="ojo" />
        </button>
      }
      debajo={conRequisitos && (
        <ul className="requisitos" id={idRequisitos} aria-label="Requisitos de la contraseña">
          {REQUISITOS.map(({ regla, texto }) => {
            const cumple = cumpleRegla(regla, valor);
            return (
              <li key={regla} className={['requisitos__item', cumple && 'requisitos__item--cumple'].filter(Boolean).join(' ')}>
                {texto}
                <span className="solo-lector">{cumple ? ' (cumple)' : ' (falta)'}</span>
              </li>
            );
          })}
        </ul>
      )}
    >
      <input
        ref={referencia}
        name={nombre}
        type={visible ? 'text' : 'password'}
        autoComplete={autoComplete}
        maxLength={autoComplete === 'new-password' ? MAXIMO_CONTRASENA : undefined}
        aria-describedby={conRequisitos ? idRequisitos : undefined}
        value={valor}
        onChange={(evento) => alCambiar(evento.target.value)}
      />
    </Campo>
  );
}
