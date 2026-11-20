import { useEffect, useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { ErrorApi, api } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Icono } from '../../componentes/Icono';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { agendarServicio } from '../../catalogo/ServicioElegido';
import { PlantillaAcceso } from '../../layouts/PlantillaAcceso';
import { useSesion } from '../../sesion/ContextoSesion';
import '../../styles/vistas/registro.css';
import { separarErrores } from './erroresFormulario';
import { LATERAL_REGISTRO } from './laterales';

interface DatosPendientes {
  proveedor: string;
  nombre: string;
  email: string;
}

type Errores = Partial<Record<'nombre' | 'celular' | 'empresa' | 'aceptaDatos', string>>;

/** Mismas reglas y mensajes del registro con correo (HU-05). */
function validar(nombre: string, celular: string, acepta: boolean): Errores {
  const e: Errores = {};
  const n = nombre.trim();
  if (!n) e.nombre = 'Escribe tu nombre completo.';
  else if (n.length < 3) e.nombre = 'Escribe al menos 3 caracteres.';
  else if (!/^[\p{L} ]+$/u.test(n)) e.nombre = 'Escribe solo letras y espacios.';
  const c = celular.trim();
  if (!c) e.celular = 'Escribe tu número de celular.';
  else if (!/^3\d{9}$/.test(c)) e.celular = 'Escribe 10 dígitos que empiecen por 3.';
  if (!acepta) e.aceptaDatos = 'Debes aceptar la política de datos para crear la cuenta.';
  return e;
}

/**
 * Completa tu registro (RF-50, D-50, sin prototipo: usa el diseño de registro.html). Quien llega con Google o Microsoft
 * por primera vez todavía no tiene cuenta: el proveedor solo da el nombre y el correo, así que aquí escribe su celular,
 * su empresa (opcional) y acepta la política de datos (HU-05, Ley 1581). El correo no se cambia: es el que el proveedor
 * confirmó y al que llegará toda la comunicación del portal.
 */
export function PaginaCompletarRegistro() {
  const { recargar, abrirSesion } = useSesion();
  const { formulario, enfocar } = useEnfocarError();
  const idTerminos = useId();

  const [pendiente, setPendiente] = useState<DatosPendientes | null>(null);
  const [vencido, setVencido] = useState<string | null>(null);
  const [nombre, setNombre] = useState('');
  const [celular, setCelular] = useState('');
  const [empresa, setEmpresa] = useState('');
  const [acepta, setAcepta] = useState(false);
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    let activo = true;
    api.obtener<DatosPendientes>('/auth/externo/pendiente', { sinRedirigirEn401: true })
      .then((datos) => {
        if (!activo) return;
        setPendiente(datos);
        setNombre(datos.nombre);
      })
      .catch((e) => activo && setVencido(e instanceof ErrorApi && e.estado === 409
        ? e.message
        : 'No pudimos continuar con tu registro. Vuelve a elegir con qué cuenta registrarte.'));
    return () => {
      activo = false;
    };
  }, []);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validar(nombre, celular, acepta);
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) {
      enfocar();
      return;
    }
    setEnviando(true);
    try {
      await api.crear('/auth/externo/completar',
        { nombre: nombre.trim(), celular: celular.trim(), empresa: empresa.trim(), aceptaDatos: acepta },
        { sinRedirigirEn401: true });
      const usuario = await recargar();
      if (usuario) abrirSesion(usuario, agendarServicio(null));
    } catch (error) {
      const { porCampo, general: mensaje } = separarErrores(error);
      setErrores(porCampo);
      setGeneral(mensaje);
      setEnviando(false);
      enfocar();
    }
  }

  return (
    <PlantillaAcceso tituloPestana="Completa tu registro" lateral={LATERAL_REGISTRO}>
      <h1 className="acceso__titulo">Completa tu registro</h1>

      {vencido && (
        <>
          <Alerta tipo="error" titulo={vencido} />
          <Link className="boton boton--acento boton--grande boton--bloque" to="/registro">Volver a registrarme</Link>
        </>
      )}
      {!vencido && !pendiente && <Cargando texto="Un momento…" />}

      {pendiente && (
        <>
          <p className="acceso__texto">
            Entraste con tu cuenta de {pendiente.proveedor}. Solo faltan unos datos para crear tu cuenta de cliente.
          </p>
          {general && <Alerta tipo="error" titulo={general} />}

          <form ref={formulario} onSubmit={enviar} noValidate>
            <Campo etiqueta="Nombre completo" error={errores.nombre}>
              <input name="nombre" type="text" autoComplete="name" maxLength={60} placeholder="Laura Gómez"
                     value={nombre} onChange={(e) => setNombre(e.target.value)} />
            </Campo>
            <Campo etiqueta="Correo electrónico" ayuda={`Es el correo de tu cuenta de ${pendiente.proveedor}: ahí te llegarán la bienvenida y todos los avisos del portal.`}>
              <input name="email" type="email" value={pendiente.email} readOnly />
            </Campo>
            <div className="fila-campos">
              <Campo etiqueta="Celular" error={errores.celular}>
                <input name="celular" type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={10} placeholder="3001234567"
                       value={celular} onChange={(e) => setCelular(e.target.value)} />
              </Campo>
              <Campo etiqueta="Empresa" opcional error={errores.empresa}>
                <input name="empresa" type="text" autoComplete="organization" maxLength={80} placeholder="Nombre de tu negocio"
                       value={empresa} onChange={(e) => setEmpresa(e.target.value)} />
              </Campo>
            </div>

            <div className={['campo', errores.aceptaDatos && 'campo--invalido'].filter(Boolean).join(' ')}>
              <label className="casilla">
                <input name="aceptaDatos" type="checkbox" checked={acepta}
                       aria-invalid={errores.aceptaDatos ? true : undefined}
                       aria-describedby={errores.aceptaDatos ? idTerminos : undefined}
                       onChange={(e) => setAcepta(e.target.checked)} />
                <span>
                  Acepto la <Link className="enlace" to="/legal#datos" target="_blank" rel="noopener">política de tratamiento de datos personales</Link> (Ley 1581 de 2012).
                </span>
              </label>
              {errores.aceptaDatos && (
                <p className="campo__mensaje" id={idTerminos} role="alert">
                  <Icono nombre="alerta" />
                  <span className="campo__mensaje-texto">{errores.aceptaDatos}</span>
                </p>
              )}
            </div>

            <Boton type="submit" tamano="grande" bloque cargando={enviando}>Crear cuenta y agendar</Boton>
          </form>
          <p className="acceso__pie">¿Te equivocaste de cuenta? <Link className="enlace" to="/registro">Empieza de nuevo</Link></p>
        </>
      )}
    </PlantillaAcceso>
  );
}
