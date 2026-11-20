import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import { api } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton, EnlaceBoton } from '../../componentes/Boton';
import { CampoContrasena } from '../../componentes/CampoContrasena';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { PlantillaAcceso } from '../../layouts/PlantillaAcceso';
import { errorConfirmacion, errorContrasenaNueva } from '../../sesion/politicaContrasena';
import '../../styles/vistas/registro.css';
import { separarErrores } from './erroresFormulario';
import { LATERAL_RESTABLECER } from './laterales';

const ENLACE_INVALIDO = 'Este enlace ya no es válido, solicita uno nuevo';

type Errores = Partial<Record<'passwordNueva' | 'confirmacion', string>>;

/**
 * Restablecer la contraseña con el enlace del correo (HU-06, RF-04). Vista nueva sin prototipo (D-37):
 * usa el diseño de cambiar-contrasena.html. El enlace vale 30 minutos y sirve una sola vez.
 */
export function PaginaRestablecer() {
  const [parametros] = useSearchParams();
  const token = parametros.get('token') ?? '';
  const { formulario, enfocar } = useEnfocarError();

  const [nueva, setNueva] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [estado, setEstado] = useState<'formulario' | 'listo' | 'invalido'>(token ? 'formulario' : 'invalido');
  const [mensajeListo, setMensajeListo] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Errores = {};
    const errorNueva = errorContrasenaNueva(nueva, 'Crea tu nueva contraseña.');
    if (errorNueva) nuevos.passwordNueva = errorNueva;
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
      const respuesta = await api.crear<{ mensaje: string }>('/auth/restablecer', { token, passwordNueva: nueva, confirmacion },
        { sinRedirigirEn401: true });
      setMensajeListo(respuesta.mensaje);
      setEstado('listo');
    } catch (error) {
      const { porCampo, general: mensaje } = separarErrores(error);
      if (porCampo.token) {
        setEstado('invalido');
      } else {
        setErrores(porCampo);
        setGeneral(mensaje);
        enfocar();
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <PlantillaAcceso tituloPestana="Restablecer contraseña" lateral={LATERAL_RESTABLECER}>
      <h1 className="acceso__titulo">Restablece tu contraseña</h1>

      {estado === 'invalido' && (
        <>
          <Alerta tipo="error" titulo={ENLACE_INVALIDO}>
            Los enlaces valen 30 minutos y sirven una sola vez. Pide uno nuevo en "¿Olvidaste tu contraseña?".
          </Alerta>
          <EnlaceBoton a="/ingresar" tamano="grande" bloque>Pedir un enlace nuevo</EnlaceBoton>
        </>
      )}

      {estado === 'listo' && (
        <>
          <Alerta tipo="exito" titulo={mensajeListo} />
          <EnlaceBoton a="/ingresar" tamano="grande" bloque>Iniciar sesión</EnlaceBoton>
        </>
      )}

      {estado === 'formulario' && (
        <>
          <p className="acceso__texto">Crea una contraseña nueva para tu cuenta. Después entrarás con ella.</p>
          {general && <Alerta tipo="error" titulo={general} />}
          <form ref={formulario} onSubmit={enviar} noValidate>
            <CampoContrasena etiqueta="Nueva contraseña" nombre="passwordNueva" autoComplete="new-password" conRequisitos
                             valor={nueva} alCambiar={setNueva} error={errores.passwordNueva} />
            <CampoContrasena etiqueta="Confirma la nueva contraseña" nombre="confirmacion" autoComplete="new-password"
                             valor={confirmacion} alCambiar={setConfirmacion} error={errores.confirmacion} />
            <Boton type="submit" tamano="grande" bloque cargando={enviando}>Guardar contraseña</Boton>
          </form>
        </>
      )}
    </PlantillaAcceso>
  );
}
