import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import { correoSistemaApi, navegador, type EstadoCorreo } from '../../administracion/correoSistema';
import { fechaEvento } from '../../administracion/formato';
import { ErrorApi } from '../../api/cliente';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Campo } from '../../componentes/Campo';
import { Cargando } from '../../componentes/Cargando';
import { Dialogo } from '../../componentes/Dialogo';
import { Insignia } from '../../componentes/Insignia';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { useUsuario } from '../../sesion/ContextoSesion';

/** Lo que dice la pantalla al volver de Google (?resultado=…). */
const RESULTADOS: Record<string, { tipo: 'exito' | 'advertencia' | 'error'; texto: string }> = {
  conectado: { tipo: 'exito', texto: 'Gmail quedó conectado. Desde ahora los correos del portal salen desde esta cuenta.' },
  cancelado: { tipo: 'advertencia', texto: 'Cancelaste la conexión en Google. El correo del sistema no cambió.' },
  sin_permiso_envio: {
    tipo: 'advertencia',
    texto: 'Google no dio el permiso para enviar correos. Vuelve a conectar y deja marcada la opción «Enviar correo electrónico en tu nombre».',
  },
  vencido: { tipo: 'error', texto: 'La conexión tardó demasiado o ya se había usado. Pulsa «Conectar con Gmail» otra vez.' },
  error: { tipo: 'error', texto: 'Google no completó la conexión. Intenta de nuevo en unos minutos.' },
};

/**
 * Correo del sistema (D-51, sin prototipo: pantalla nueva con los tokens del panel). El administrador conecta la
 * cuenta de Gmail que envía los correos del portal, sin escribir su contraseña: Google le pide iniciar sesión y
 * aceptar el permiso «Enviar correo en tu nombre». Aquí también se prueba el envío y se desconecta.
 */
export function PaginaCorreoSistema() {
  useTituloDocumento('Correo del sistema');
  const usuario = useUsuario();
  const notificar = useNotificar();
  const [parametros, setParametros] = useSearchParams();
  const [estado, setEstado] = useState<EstadoCorreo | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [conectando, setConectando] = useState(false);
  const [errorConexion, setErrorConexion] = useState<string | null>(null);
  const [desconectar, setDesconectar] = useState(false);
  const [resultado] = useState(() => parametros.get('resultado'));

  const cargar = useCallback(async () => {
    try {
      setEstado(await correoSistemaApi.estado());
      setErrorCarga(null);
    } catch (e) {
      setErrorCarga(e instanceof ErrorApi ? e.message : 'No pudimos cargar el estado del correo.');
    }
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  // El aviso del regreso de Google se muestra una vez; se quita de la dirección para que no reaparezca al recargar.
  useEffect(() => {
    if (parametros.has('resultado')) setParametros({}, { replace: true });
  }, [parametros, setParametros]);

  const conectar = async () => {
    setConectando(true);
    setErrorConexion(null);
    try {
      const { url } = await correoSistemaApi.conectar();
      navegador.ir(url);
    } catch (e) {
      setErrorConexion(e instanceof ErrorApi ? e.message : 'No pudimos iniciar la conexión con Google.');
      setConectando(false);
    }
  };

  const aviso = resultado ? RESULTADOS[resultado] : undefined;

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Correo del sistema</h1>
          <p className="encabezado-vista__texto">
            La cuenta de Gmail desde la que salen los correos del portal: avisos de citas y proyectos, enlaces para
            restablecer la contraseña y contraseñas temporales.
          </p>
        </div>
      </div>

      {aviso && <Alerta tipo={aviso.tipo} titulo={aviso.texto} />}
      {errorCarga && <Alerta tipo="error" titulo={errorCarga} />}
      {!estado && !errorCarga && <Cargando texto="Revisando el correo del sistema…" />}

      {estado && (
        <div className="correo-sistema">
          <section className="tarjeta" aria-labelledby="titulo-cuenta">
            <div className="correo-sistema__cabecera">
              <h2 className="tarjeta__titulo" id="titulo-cuenta">Cuenta de Gmail</h2>
              <EstadoInsignia estado={estado} />
            </div>

            {!estado.configurado ? (
              <Alerta tipo="advertencia" titulo="Falta configurar el cliente de Google en el servidor">
                Crea un ID de cliente OAuth en Google Cloud Console y pon CORETECH_GOOGLE_ID y CORETECH_GOOGLE_SECRETO
                en el archivo .env del servidor. Después recarga esta página.
              </Alerta>
            ) : estado.conectado ? (
              <>
                <dl className="correo-sistema__datos">
                  <div><dt>Envía desde</dt><dd>{estado.cuenta}</dd></div>
                  {estado.conectadoEn && <div><dt>Conectada</dt><dd>{fechaEvento(estado.conectadoEn)}</dd></div>}
                </dl>
                {estado.ultimoError && (
                  <Alerta tipo="advertencia" titulo="El último envío falló">
                    {estado.ultimoError}
                    {estado.ultimoErrorEn && ` (${fechaEvento(estado.ultimoErrorEn)})`}
                  </Alerta>
                )}
                <div className="correo-sistema__acciones">
                  <Boton variante="contorno" cargando={conectando} onClick={() => void conectar()}>Cambiar de cuenta</Boton>
                  <Boton variante="peligro" onClick={() => setDesconectar(true)}>Desconectar</Boton>
                </div>
              </>
            ) : (
              <>
                {estado.estado === 'REVOCADO' ? (
                  <Alerta tipo="error" titulo={`Google retiró el permiso de ${estado.cuenta}`}>
                    Los correos esperan hasta que vuelvas a conectar la cuenta.
                  </Alerta>
                ) : (
                  <p className="correo-sistema__texto">
                    Al pulsar el botón, Google te pide iniciar sesión con la cuenta que enviará los correos (por ejemplo, helix.jmdev2026@gmail.com)
                    y aceptar el permiso «Enviar correo electrónico en tu nombre». El portal no ve ni guarda tu
                    contraseña, y no puede leer tus correos. Puedes quitar el permiso cuando quieras desde tu cuenta de
                    Google o con «Desconectar».
                  </p>
                )}
                <div className="correo-sistema__acciones">
                  <Boton cargando={conectando} onClick={() => void conectar()}>
                    {estado.estado === 'REVOCADO' ? 'Volver a conectar' : 'Conectar con Gmail'}
                  </Boton>
                </div>
              </>
            )}
            {errorConexion && <Alerta tipo="error" titulo={errorConexion} />}
          </section>

          <EnvioPrueba
            destinoInicial={estado.cuenta ?? usuario?.email ?? ''}
            conectado={estado.conectado}
            alTerminar={() => void cargar()}
          />
        </div>
      )}

      <Dialogo
        abierto={desconectar}
        alCerrar={() => setDesconectar(false)}
        tono="peligro"
        icono="apagar"
        titulo="¿Desconectar el correo del sistema?"
        texto={`El portal deja de enviar correos desde ${estado?.cuenta ?? 'esta cuenta'} y le devuelve el permiso a Google. Los correos esperarán hasta que conectes otra cuenta.`}
        acciones={
          <>
            <Boton variante="contorno" onClick={() => setDesconectar(false)}>Cancelar</Boton>
            <Boton
              variante="peligro"
              onClick={async () => {
                try {
                  await correoSistemaApi.desconectar();
                  notificar('El correo del sistema quedó desconectado.');
                } catch (e) {
                  notificar(e instanceof ErrorApi ? e.message : 'No pudimos desconectarlo.', 'error');
                }
                setDesconectar(false);
                void cargar();
              }}
            >
              Desconectar
            </Boton>
          </>
        }
      />
    </>
  );
}

function EstadoInsignia({ estado }: { estado: EstadoCorreo }) {
  if (estado.conectado) return <Insignia variante="atendida">Conectado</Insignia>;
  if (estado.estado === 'REVOCADO') return <Insignia variante="vencido">Hay que reconectar</Insignia>;
  return <Insignia variante="neutra">Sin conectar</Insignia>;
}

/** «Enviar correo de prueba»: sale de inmediato y dice si llegó a Gmail o al buzón de desarrollo. */
function EnvioPrueba({ destinoInicial, conectado, alTerminar }: { destinoInicial: string; conectado: boolean; alTerminar: () => void }) {
  const [para, setPara] = useState(destinoInicial);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorCampo, setErrorCampo] = useState<string | undefined>();
  const [exito, setExito] = useState<string | null>(null);

  useEffect(() => setPara(destinoInicial), [destinoInicial]);

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    setErrorCampo(undefined);
    setExito(null);
    try {
      const r = await correoSistemaApi.enviarPrueba(para);
      setExito(r.mensaje);
    } catch (e) {
      if (e instanceof ErrorApi && e.porCampo.para) setErrorCampo(e.porCampo.para);
      else setError(e instanceof ErrorApi ? e.message : 'No pudimos enviar el correo.');
    } finally {
      setEnviando(false);
      alTerminar();
    }
  };

  return (
    <section className="tarjeta" aria-labelledby="titulo-prueba">
      <h2 className="tarjeta__titulo" id="titulo-prueba">Enviar correo de prueba</h2>
      <p className="correo-sistema__texto">
        {conectado
          ? 'Comprueba que el correo llega: se envía de inmediato desde la cuenta conectada.'
          : 'Sin Gmail conectado, en desarrollo el correo llega al buzón local (Mailpit).'}
      </p>
      <form className="correo-sistema__prueba" onSubmit={(e) => void enviar(e)} noValidate>
        <Campo etiqueta="Enviar a" error={errorCampo}>
          <input type="email" value={para} onChange={(e) => setPara(e.target.value)} autoComplete="email" maxLength={120} />
        </Campo>
        <Boton type="submit" cargando={enviando} icono="correo">Enviar correo de prueba</Boton>
      </form>
      {exito && <Alerta tipo="exito" titulo={exito} />}
      {error && <Alerta tipo="error" titulo={error} />}
    </section>
  );
}
