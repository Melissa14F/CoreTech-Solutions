import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { api } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { CampoContrasena } from '../../componentes/CampoContrasena';
import { useEnfocarError } from '../../componentes/useEnfocarError';
import { ServicioElegido, agendarServicio, useServicioElegido } from '../../catalogo/ServicioElegido';
import { PlantillaAcceso } from '../../layouts/PlantillaAcceso';
import { useSesion } from '../../sesion/ContextoSesion';
import { AvisoIngresoExterno, BotonesIngresoExterno } from '../../sesion/IngresoExterno';
import { destinoAlIngresar } from '../../sesion/rutas';
import '../../styles/vistas/login.css';
import { separarErrores } from './erroresFormulario';
import { CORREO_VALIDO, LATERAL_INGRESAR, MENSAJE_CORREO_INVALIDO } from './laterales';

/** Cuentas de demostración del perfil dev (R__datos_demo.sql). Solo se muestran en desarrollo. */
const CUENTAS_DEMO = [
  { email: 'laura.gomez@correo.com', nombre: 'Laura Gómez', rol: 'Cliente · ve sus citas y proyectos', iniciales: 'LG', avatar: 'avatar avatar--acento' },
  { email: 'andres.perez@coretechsolutions.co', nombre: 'Andrés Pérez', rol: 'Product Owner · registra el avance de su equipo', iniciales: 'AP', avatar: 'avatar' },
  { email: 'patricia.leon@coretechsolutions.co', nombre: 'Patricia León', rol: 'Administradora · gestiona todo el sistema', iniciales: 'PL', avatar: 'avatar avatar--gris' },
];
const CLAVE_DEMO = 'Demo2026!';

type Errores = Partial<Record<'email' | 'password', string>>;

/** Iniciar sesión (HU-06, prototipo/login.html), con "¿Olvidaste tu contraseña?" (RF-04). */
export function PaginaIngresar() {
  const { ingresar, abrirSesion } = useSesion();
  const [parametros] = useSearchParams();
  const { slug, servicio } = useServicioElegido();
  // Quien viene de una tarjeta del catálogo vuelve a agendar ese servicio (HU-07)
  const volver = parametros.get('volver') ?? (slug ? agendarServicio(slug) : null);
  const { formulario, enfocar } = useEnfocarError();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [recordarme, setRecordarme] = useState(false);
  const [errores, setErrores] = useState<Errores>({});
  const [general, setGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function entrar(correo: string, clave: string, recordar: boolean) {
    setEnviando(true);
    setGeneral(null);
    try {
      const usuario = await ingresar(correo, clave, recordar);
      const destino = usuario.debeCambiarPassword ? '/cambiar-contrasena' : destinoAlIngresar(usuario.rol, volver);
      abrirSesion(usuario, destino);
    } catch (error) {
      const { porCampo, general: mensaje } = separarErrores(error);
      setErrores(porCampo);
      setGeneral(mensaje);
      setEnviando(false);
    }
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: Errores = {};
    const correo = email.trim();
    if (!correo) nuevos.email = 'Escribe el correo con el que creaste tu cuenta.';
    else if (!CORREO_VALIDO.test(correo)) nuevos.email = MENSAJE_CORREO_INVALIDO;
    if (!password) nuevos.password = 'Escribe tu contraseña.';
    setErrores(nuevos);
    setGeneral(null);
    if (Object.keys(nuevos).length > 0) {
      enfocar();
      return;
    }
    void entrar(correo, password, recordarme);
  }

  return (
    <PlantillaAcceso tituloPestana="Iniciar sesión" lateral={LATERAL_INGRESAR}>
      <h1 className="acceso__titulo">Inicia sesión</h1>
      <p className="acceso__texto">Ingresa para ver tus citas y el avance de tus proyectos.</p>
      <ServicioElegido servicio={servicio} texto="Después de ingresar vas a agendar" />

      <AvisoIngresoExterno />
      {general && <Alerta tipo="error" titulo={general} />}

      <BotonesIngresoExterno accion="ingresar" />
      <form ref={formulario} onSubmit={enviar} noValidate>
        <Campo etiqueta="Correo electrónico" error={errores.email}>
          <input
            name="email"
            type="email"
            autoComplete="email"
            placeholder="laura.gomez@correo.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Campo>
        <CampoContrasena
          etiqueta="Contraseña"
          nombre="password"
          autoComplete="current-password"
          valor={password}
          alCambiar={setPassword}
          error={errores.password}
        />

        <div className="login__opciones">
          <label className="casilla">
            <input name="recordarme" type="checkbox" checked={recordarme} onChange={(e) => setRecordarme(e.target.checked)} />
            <span>Recordarme</span>
          </label>
          <RecuperarContrasena />
        </div>

        <Boton type="submit" tamano="grande" bloque cargando={enviando}>Ingresar</Boton>
      </form>

      {import.meta.env.DEV && (
        <>
          <p className="separador">o entra con una cuenta de demostración</p>
          <ul className="accesos-demo" aria-label="Cuentas de demostración (solo en desarrollo)">
            {CUENTAS_DEMO.map((cuenta) => (
              <li key={cuenta.email}>
                <button type="button" className="acceso-demo" disabled={enviando} onClick={() => void entrar(cuenta.email, CLAVE_DEMO, false)}>
                  <span className={cuenta.avatar} aria-hidden="true">{cuenta.iniciales}</span>
                  <span>
                    <span className="acceso-demo__nombre">{cuenta.nombre}</span>
                    <span className="acceso-demo__rol">{cuenta.rol}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="acceso__pie">¿No tienes cuenta? <Link className="enlace" to={slug ? `/registro?servicio=${encodeURIComponent(slug)}` : '/registro'}>Crea una</Link></p>
    </PlantillaAcceso>
  );
}

/**
 * "¿Olvidaste tu contraseña?" (HU-06): se despliega sin salir de la pantalla y pide el enlace por correo.
 * No es un formulario aparte porque va dentro del de iniciar sesión (no se anidan formularios).
 */
function RecuperarContrasena() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [enviado, setEnviado] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function pedirEnlace() {
    const correo = email.trim();
    if (!correo) return setError('Escribe el correo con el que creaste tu cuenta.');
    if (!CORREO_VALIDO.test(correo)) return setError(MENSAJE_CORREO_INVALIDO);
    setError(undefined);
    setEnviando(true);
    try {
      const respuesta = await api.crear<{ mensaje: string }>('/auth/recuperacion', { email: correo }, { sinRedirigirEn401: true });
      setEnviado(respuesta.mensaje);
    } catch (e) {
      const { porCampo, general } = separarErrores(e);
      setError(porCampo.email ?? general ?? undefined);
    } finally {
      setEnviando(false);
    }
  }

  function alPulsarTecla(evento: KeyboardEvent<HTMLInputElement>) {
    if (evento.key === 'Enter') {
      evento.preventDefault(); // no envía el formulario de iniciar sesión
      void pedirEnlace();
    }
  }

  return (
    <details className="recuperar">
      <summary>¿Olvidaste tu contraseña?</summary>
      <div className="recuperar__ayuda">
        {enviado ? (
          <Alerta tipo="exito" titulo="Revisa tu correo">
            {enviado}. El enlace vale 30 minutos y sirve una sola vez.
          </Alerta>
        ) : (
          <>
            <Campo etiqueta="Correo de tu cuenta" error={error} ayuda="Te enviaremos un enlace para crear una contraseña nueva.">
              <input name="emailRecuperacion" type="email" autoComplete="email" value={email}
                     onChange={(e) => setEmail(e.target.value)} onKeyDown={alPulsarTecla} />
            </Campo>
            <Boton variante="contorno" tamano="pequeno" cargando={enviando} onClick={() => void pedirEnlace()}>Enviar enlace</Boton>
          </>
        )}
      </div>
    </details>
  );
}
