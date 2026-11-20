import { useState, type FormEvent } from 'react';

import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { CampoContrasena } from '../../componentes/CampoContrasena';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { PlantillaAcceso } from '../../layouts/PlantillaAcceso';
import { useSesion, useUsuario } from '../../sesion/ContextoSesion';
import { errorConfirmacion, errorContrasenaNueva } from '../../sesion/politicaContrasena';
import { panelDeRol } from '../../sesion/rutas';
import '../../styles/vistas/registro.css';
import { separarErrores } from './erroresFormulario';
import { LATERAL_CAMBIAR } from './laterales';

type Errores = Partial<Record<'passwordActual' | 'passwordNueva' | 'confirmacion', string>>;

/**
 * Cambiar la contraseña (HU-06, RF-05; prototipo/cambiar-contrasena.html). Obligatoria en el primer ingreso
 * con la contraseña temporal: hasta cambiarla, las guardas traen al usuario a esta pantalla.
 */
export function PaginaCambiarContrasena() {
  const usuario = useUsuario();
  const { cambiarPassword, abrirSesion, cerrarSesion } = useSesion();
  const { formulario, enfocar } = useEnfocarError();

  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const temporal = usuario?.debeCambiarPassword ?? false;
  const primerNombre = usuario?.nombre.split(' ')[0] ?? '';

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Errores = {};
    if (!actual) {
      nuevos.passwordActual = temporal ? 'Escribe la contraseña temporal que te llegó al correo.' : 'Escribe tu contraseña actual.';
    }
    const errorNueva = errorContrasenaNueva(nueva, 'Crea tu nueva contraseña.');
    if (errorNueva) nuevos.passwordNueva = errorNueva;
    else if (actual && nueva === actual) {
      nuevos.passwordNueva = temporal ? 'La nueva contraseña debe ser distinta de la temporal.' : 'La nueva contraseña debe ser distinta de la actual.';
    }
    const errorConfirma = errorConfirmacion(nueva, confirmacion, 'Escribe de nuevo la nueva contraseña.');
    if (errorConfirma) nuevos.confirmacion = errorConfirma;
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) {
      enfocar();
      return;
    }
    setEnviando(true);
    try {
      const actualizado = await cambiarPassword(actual, nueva, confirmacion);
      abrirSesion(actualizado, panelDeRol(actualizado.rol));
    } catch (error) {
      const { porCampo, general: mensaje } = separarErrores(error);
      setErrores(porCampo);
      setGeneral(mensaje);
      setEnviando(false);
      enfocar();
    }
  }

  return (
    <PlantillaAcceso tituloPestana="Cambiar contraseña" lateral={LATERAL_CAMBIAR}>
      <h1 className="acceso__titulo">Cambiar contraseña</h1>
      <p className="acceso__texto">
        {temporal
          ? `Hola, ${primerNombre}. Es tu primer ingreso: reemplaza la contraseña temporal que te llegó al correo por una que solo tú conozcas.`
          : `Hola, ${primerNombre}. Escribe tu contraseña actual y la nueva.`}
      </p>

      {general && <Alerta tipo="error" titulo={general} />}

      <form ref={formulario} onSubmit={enviar} noValidate>
        <CampoContrasena etiqueta="Contraseña actual" nombre="passwordActual" autoComplete="current-password"
                         ayuda={temporal ? 'Es la contraseña temporal del correo de bienvenida.' : undefined}
                         valor={actual} alCambiar={setActual} error={errores.passwordActual} />
        <CampoContrasena etiqueta="Nueva contraseña" nombre="passwordNueva" autoComplete="new-password" conRequisitos
                         valor={nueva} alCambiar={setNueva} error={errores.passwordNueva} />
        <CampoContrasena etiqueta="Confirma la nueva contraseña" nombre="confirmacion" autoComplete="new-password"
                         valor={confirmacion} alCambiar={setConfirmacion} error={errores.confirmacion} />
        <Boton type="submit" tamano="grande" bloque cargando={enviando}>Cambiar contraseña</Boton>
      </form>

      {/* Quien llega con la contraseña temporal no tiene panel todavía: esta es su única salida. */}
      <p className="acceso__pie">
        ¿No eres {primerNombre}?{' '}
        <button type="button" className="enlace boton-enlace" onClick={() => void cerrarSesion()}>Cerrar sesión</button>
      </p>
    </PlantillaAcceso>
  );
}
