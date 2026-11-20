import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { CampoContrasena } from '../../componentes/CampoContrasena';
import { Icono } from '../../componentes/Icono';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { ServicioElegido, agendarServicio, useServicioElegido } from '../../catalogo/ServicioElegido';
import { PlantillaAcceso } from '../../layouts/PlantillaAcceso';
import { useSesion, type DatosRegistro } from '../../sesion/ContextoSesion';
import { BotonesIngresoExterno } from '../../sesion/IngresoExterno';
import { errorConfirmacion, errorContrasenaNueva } from '../../sesion/politicaContrasena';
import '../../styles/vistas/registro.css';
import { separarErrores } from './erroresFormulario';
import { CORREO_VALIDO, LATERAL_REGISTRO, MENSAJE_CORREO_INVALIDO } from './laterales';

type CampoRegistro = keyof DatosRegistro;
type Errores = Partial<Record<CampoRegistro, string>>;

const VACIO: DatosRegistro = { nombre: '', email: '', celular: '', empresa: '', password: '', confirmacion: '', aceptaDatos: false };

/** Mismas reglas y mensajes del prototipo (registro.html) y del backend (DtoAcceso.SolicitudRegistro). */
function validar(d: DatosRegistro): Errores {
  const e: Errores = {};
  const nombre = d.nombre.trim();
  if (!nombre) e.nombre = 'Escribe tu nombre completo.';
  else if (nombre.length < 3) e.nombre = 'Escribe al menos 3 caracteres.';
  else if (!/^[\p{L} ]+$/u.test(nombre)) e.nombre = 'Escribe solo letras y espacios.';

  const correo = d.email.trim();
  if (!correo) e.email = 'Escribe tu correo; ahí te enviaremos la confirmación de la cita.';
  else if (!CORREO_VALIDO.test(correo)) e.email = MENSAJE_CORREO_INVALIDO;

  const celular = d.celular.trim();
  if (!celular) e.celular = 'Escribe tu número de celular.';
  else if (!/^3\d{9}$/.test(celular)) e.celular = 'Escribe 10 dígitos que empiecen por 3.';

  const clave = errorContrasenaNueva(d.password, 'Crea una contraseña.');
  if (clave) e.password = clave;
  const confirmacion = errorConfirmacion(d.password, d.confirmacion, 'Escribe de nuevo la contraseña.');
  if (confirmacion) e.confirmacion = confirmacion;
  if (!d.aceptaDatos) e.aceptaDatos = 'Debes aceptar la política de datos para crear la cuenta.';
  return e;
}

/** Crear cuenta (HU-05, prototipo/registro.html). Al terminar, el cliente pasa a agendar su cita. */
export function PaginaRegistro() {
  const { registrar, abrirSesion } = useSesion();
  const { slug, servicio } = useServicioElegido();
  const { formulario, enfocar } = useEnfocarError();
  const idTerminos = useId();

  const [datos, setDatos] = useState<DatosRegistro>(VACIO);
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [correoRepetido, setCorreoRepetido] = useState(false);
  const [enviando, setEnviando] = useState(false);

  const cambiar = <K extends CampoRegistro>(campo: K, valor: DatosRegistro[K]) => setDatos((d) => ({ ...d, [campo]: valor }));

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validar(datos);
    setErrores(nuevos);
    setGeneral(null);
    setCorreoRepetido(false);
    if (Object.keys(nuevos).length > 0) {
      enfocar();
      return;
    }
    setEnviando(true);
    try {
      const usuario = await registrar({
        ...datos,
        nombre: datos.nombre.trim(),
        email: datos.email.trim(),
        celular: datos.celular.trim(),
        empresa: datos.empresa.trim(),
      });
      abrirSesion(usuario, agendarServicio(slug));
    } catch (error) {
      const { porCampo, general: mensaje } = separarErrores(error);
      setErrores(porCampo);
      setGeneral(mensaje);
      // D-38: el 409 del correo repetido va bajo el correo, con el enlace para iniciar sesión.
      setCorreoRepetido(error instanceof ErrorApi && error.estado === 409 && Boolean(porCampo.email));
      setEnviando(false);
      enfocar();
    }
  }

  return (
    <PlantillaAcceso tituloPestana="Crear cuenta" lateral={LATERAL_REGISTRO}>
      <h1 className="acceso__titulo">Crea tu cuenta</h1>
      <p className="acceso__texto">Con tu cuenta agendas la cita y después sigues el avance de tu proyecto.</p>
      <ServicioElegido servicio={servicio} texto="Vas a agendar" />

      {general && <Alerta tipo="error" titulo={general} />}

      <BotonesIngresoExterno accion="registrar" />
      <form ref={formulario} onSubmit={enviar} noValidate>
        <Campo etiqueta="Nombre completo" error={errores.nombre}>
          <input name="nombre" type="text" autoComplete="name" maxLength={60} placeholder="Laura Gómez"
                 value={datos.nombre} onChange={(e) => cambiar('nombre', e.target.value)} />
        </Campo>
        <Campo
          etiqueta="Correo electrónico"
          error={errores.email}
          despues={correoRepetido && (
            <p className="campo__ayuda"><Link className="enlace" to="/ingresar">¿Quieres iniciar sesión?</Link></p>
          )}
        >
          <input name="email" type="email" autoComplete="email" maxLength={120} placeholder="laura.gomez@correo.com"
                 value={datos.email} onChange={(e) => cambiar('email', e.target.value)} />
        </Campo>
        <div className="fila-campos">
          <Campo etiqueta="Celular" error={errores.celular}>
            <input name="celular" type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={10} placeholder="3001234567"
                   value={datos.celular} onChange={(e) => cambiar('celular', e.target.value)} />
          </Campo>
          <Campo etiqueta="Empresa" opcional error={errores.empresa}>
            <input name="empresa" type="text" autoComplete="organization" maxLength={80} placeholder="Nombre de tu negocio"
                   value={datos.empresa} onChange={(e) => cambiar('empresa', e.target.value)} />
          </Campo>
        </div>
        <CampoContrasena etiqueta="Contraseña" nombre="password" autoComplete="new-password" conRequisitos
                         valor={datos.password} alCambiar={(v) => cambiar('password', v)} error={errores.password} />
        <CampoContrasena etiqueta="Confirma la contraseña" nombre="confirmacion" autoComplete="new-password"
                         valor={datos.confirmacion} alCambiar={(v) => cambiar('confirmacion', v)} error={errores.confirmacion} />

        <div className={['campo', errores.aceptaDatos && 'campo--invalido'].filter(Boolean).join(' ')}>
          <label className="casilla">
            <input name="aceptaDatos" type="checkbox" checked={datos.aceptaDatos}
                   aria-invalid={errores.aceptaDatos ? true : undefined}
                   aria-describedby={errores.aceptaDatos ? idTerminos : undefined}
                   onChange={(e) => cambiar('aceptaDatos', e.target.checked)} />
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

      <p className="acceso__pie">¿Ya tienes cuenta? <Link className="enlace" to={slug ? `/ingresar?servicio=${encodeURIComponent(slug)}` : '/ingresar'}>Inicia sesión</Link></p>
    </PlantillaAcceso>
  );
}
