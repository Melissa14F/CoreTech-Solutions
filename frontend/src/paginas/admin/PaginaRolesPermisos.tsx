import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import { permisos as apiPermisos, type CambioPermiso, type CodigoRol, type MatrizPermisos } from '../../administracion/api';
import { plural } from '../../administracion/formato';
import { Alerta } from '../../componentes/Alerta';
import { Boton } from '../../componentes/Boton';
import { Cargando } from '../../componentes/Cargando';
import { Icono, type NombreIcono } from '../../componentes/Icono';
import { useNotificar } from '../../componentes/Notificaciones';
import { useTituloDocumento } from '../../componentes/useTituloDocumento';
import { separarErrores } from '../acceso/erroresFormulario';
import '../../styles/vistas/admin-roles-permisos.css';

/** Textos de cada rol en el prototipo (prototipo/admin/roles-permisos.html). */
const ROLES: Record<CodigoRol, { nombre: string; corto: string; texto: string; icono: NombreIcono }> = {
  CLIENTE: { nombre: 'Cliente', corto: 'Cliente', texto: 'Se registra desde el sitio. Agenda citas y sigue sus proyectos.', icono: 'persona' },
  PRODUCT_OWNER: { nombre: 'Desarrollador (Product Owner)', corto: 'Desarr.', texto: 'Su cuenta se crea al armar su equipo. Atiende citas y registra avances.', icono: 'codigoEtiqueta' },
  ADMINISTRADOR: { nombre: 'Administrador', corto: 'Admin.', texto: 'Gestiona todo. Sus permisos no se pueden quitar para no perder el control del sistema.', icono: 'escudo' },
};

const clave = (rol: CodigoRol, permiso: string) => `${rol}|${permiso}`;

/** Configurar roles y permisos (HU-21, RN-19, D-35; prototipo/admin/roles-permisos.html). */
export function PaginaRolesPermisos() {
  useTituloDocumento('Roles y permisos');
  const notificar = useNotificar();
  const [matriz, setMatriz] = useState<MatrizPermisos | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  /** Interruptores movidos y aún sin guardar: "ROL|permiso" → nuevo valor. */
  const [pendientes, setPendientes] = useState<Record<string, boolean>>({});
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    apiPermisos.matriz().then(setMatriz).catch((e) => setErrorCarga(separarErrores(e).general ?? 'No pudimos cargar los permisos.'));
  }, []);

  const cambios: CambioPermiso[] = Object.entries(pendientes).map(([k, permitido]) => {
    const [rol, permiso = ''] = k.split('|');
    return { rol: rol as CodigoRol, permiso, permitido };
  });
  const hayCambios = cambios.length > 0;

  // Avisa antes de salir de la página con cambios sin guardar
  useEffect(() => {
    if (!hayCambios) return;
    const avisar = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', avisar);
    return () => window.removeEventListener('beforeunload', avisar);
  }, [hayCambios]);

  // Permisos de cada rol contando los cambios sin guardar (el resumen se actualiza al mover un interruptor)
  const cantidad = useMemo(() => {
    const conteo: Record<string, number> = {};
    for (const fila of matriz?.permisos ?? []) {
      for (const c of fila.celdas) {
        const valor = pendientes[clave(c.rol, fila.codigo)] ?? c.permitido;
        if (valor) conteo[c.rol] = (conteo[c.rol] ?? 0) + 1;
      }
    }
    return conteo;
  }, [matriz, pendientes]);

  function mover(rol: CodigoRol, permiso: string, inicial: boolean, valor: boolean) {
    setPendientes((actual) => {
      const siguiente = { ...actual };
      if (valor === inicial) delete siguiente[clave(rol, permiso)];
      else siguiente[clave(rol, permiso)] = valor;
      return siguiente;
    });
  }

  async function guardar() {
    setGuardando(true);
    try {
      setMatriz(await apiPermisos.guardar(cambios));
      setPendientes({});
      notificar(`${plural(cambios.length, 'permiso actualizado', 'permisos actualizados')}. Aplican de inmediato.`);
    } catch (e) {
      notificar(separarErrores(e).general ?? separarErrores(e).porCampo.permisos ?? 'No se pudieron guardar los permisos.', 'error');
    } finally {
      setGuardando(false);
    }
  }

  function descartar() {
    setPendientes({});
    notificar('Se descartaron los cambios.');
  }

  const grupos = useMemo(() => {
    const lista: { grupo: string; filas: MatrizPermisos['permisos'] }[] = [];
    for (const fila of matriz?.permisos ?? []) {
      const ultimo = lista[lista.length - 1];
      if (ultimo?.grupo === fila.grupo) ultimo.filas.push(fila);
      else lista.push({ grupo: fila.grupo, filas: [fila] });
    }
    return lista;
  }, [matriz]);

  return (
    <>
      <div className="encabezado-vista">
        <div>
          <h1 className="encabezado-vista__titulo">Roles y permisos</h1>
          <p className="encabezado-vista__texto">
            Activa o desactiva lo que puede hacer cada rol. Los permisos con candado los fija una regla del negocio y no se pueden cambiar.
          </p>
        </div>
      </div>

      {errorCarga && <Alerta tipo="error" titulo={errorCarga} />}
      {!matriz && !errorCarga && <Cargando texto="Cargando permisos…" />}

      {matriz && (
        <>
          <ul className="roles-resumen" aria-label="Resumen de roles">
            {matriz.roles.map((r) => (
              <li key={r.codigo} className={['rol-resumen', r.codigo === 'ADMINISTRADOR' && 'rol-resumen--fijo'].filter(Boolean).join(' ')}>
                <span className="rol-resumen__icono"><Icono nombre={ROLES[r.codigo].icono} grande /></span>
                <span className="rol-resumen__nombre">{ROLES[r.codigo].nombre}</span>
                <span className="rol-resumen__dato">
                  <strong>{cantidad[r.codigo] ?? 0}</strong> permisos · <Link to="/admin/usuarios">{plural(r.usuarios, 'usuario', 'usuarios')}</Link>
                </span>
                <span className="rol-resumen__texto">{ROLES[r.codigo].texto}</span>
              </li>
            ))}
          </ul>

          <form className="matriz-contenedor" onSubmit={(e) => { e.preventDefault(); void guardar(); }}>
            <table className="matriz">
              <caption className="solo-lector">Permisos de cada rol</caption>
              <thead>
                <tr>
                  <th scope="col" className="matriz__permiso">Permiso</th>
                  {matriz.roles.map((r) => (
                    <th key={r.codigo} scope="col">
                      {r.codigo === 'CLIENTE' ? 'Cliente' : (
                        <>
                          <span className="texto-largo">{r.codigo === 'PRODUCT_OWNER' ? 'Desarrollador' : 'Administrador'}</span>
                          <abbr className="texto-corto" title={ROLES[r.codigo].nombre}>{ROLES[r.codigo].corto}</abbr>
                        </>
                      )}
                      {r.codigo === 'ADMINISTRADOR' && <> <Icono nombre="candado" /><span className="solo-lector">(bloqueado)</span></>}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {grupos.map(({ grupo, filas }) => (
                  <Fragment key={grupo}>
                    <tr className="matriz__grupo"><th scope="rowgroup" colSpan={matriz.roles.length + 1}>{grupo}</th></tr>
                    {filas.map((fila) => {
                      const cambiada = fila.celdas.some((c) => clave(c.rol, fila.codigo) in pendientes);
                      return (
                        <tr key={fila.codigo} className={['matriz__fila', cambiada && 'esta-cambiada'].filter(Boolean).join(' ')}>
                          <th scope="row" className="matriz__permiso">
                            <span className="matriz__nombre">{fila.nombre}{fila.regla && <span className="matriz__regla">{fila.regla}</span>}</span>
                            {fila.ayuda && <small>{fila.ayuda}</small>}
                          </th>
                          {fila.celdas.map((c) => {
                            const etiqueta = `${ROLES[c.rol].nombre}: ${fila.nombre}`;
                            return (
                              <td key={c.rol} className="matriz__celda" data-etiqueta={ROLES[c.rol].nombre}>
                                {c.fijo ? (
                                  <span className={['permiso-fijo', c.permitido && 'permiso-fijo--si'].filter(Boolean).join(' ')} title={c.motivo ?? undefined}>
                                    <Icono nombre={c.permitido ? 'exito' : 'candado'} />
                                    <span className="solo-lector">{c.permitido ? 'Permitido' : 'No permitido'}. {c.motivo} ({etiqueta})</span>
                                  </span>
                                ) : (
                                  <label className="interruptor-permiso" title={etiqueta}>
                                    <input type="checkbox" role="switch" checked={pendientes[clave(c.rol, fila.codigo)] ?? c.permitido}
                                           onChange={(e) => mover(c.rol, fila.codigo, c.permitido, e.target.checked)} />
                                    <span className="interruptor-permiso__pista" aria-hidden="true" />
                                    <span className="solo-lector">{etiqueta}</span>
                                  </label>
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      );
                    })}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </form>

          <p className="nota-matriz">
            <span>Los cambios aplican de inmediato, desde la siguiente acción de cada usuario, y quedan registrados en la <Link to="/admin/auditoria">auditoría</Link>.</span>
          </p>

          {hayCambios && (
            <div className="barra-cambios" role="region" aria-label="Cambios sin guardar">
              <p className="barra-cambios__texto" aria-live="polite"><strong>{plural(cambios.length, 'cambio', 'cambios')}</strong> sin guardar</p>
              <Boton variante="transparente" disabled={guardando} onClick={descartar}>Descartar</Boton>
              <Boton cargando={guardando} onClick={() => void guardar()}>Guardar cambios</Boton>
            </div>
          )}
        </>
      )}
    </>
  );
}
