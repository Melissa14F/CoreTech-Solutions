/* ==========================================================================
   Vista: Usuarios (admin/usuarios.html)
   - Crear cuentas de desarrolladores (o administradores) con contraseña temporal.
   - Editar los datos de cualquier usuario (clientes incluidos).
   - Desactivar y activar cuentas. Un desarrollador con proyectos activos no se
     puede desactivar hasta reasignarlos (ningún proyecto queda sin responsable).
   - Restablecer la contraseña enviando un enlace al correo.
   Los filtros, la búsqueda y el menú "Más acciones" los maneja js/tabla.js.
   Requiere: js/principal.js y js/tabla.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var dialogo = document.getElementById('dialogo-usuario');
  var formulario = dialogo.querySelector('form');
  var campos = formulario.elements;
  var bloqueEquipo = dialogo.querySelector('[data-solo-equipo]');
  var campoEspecialidad = dialogo.querySelector('[data-campo-especialidad]');
  var bloqueClave = dialogo.querySelector('[data-clave-temporal]');
  var filaActual = null;
  var modo = 'crear';

  var ICONOS = {
    editar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>',
    puntos: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/></svg>',
    llave: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6M15.5 7.5l3 3L22 7l-3-3"/></svg>',
    apagar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M18.36 6.64a9 9 0 1 1-12.73 0M12 2v10"/></svg>',
    activar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>',
    proyectos: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/></svg>'
  };

  /* ---------- Contraseña temporal ---------- */
  function generarClave() {
    var letras = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    var parte = function (n) { var t = ''; for (var i = 0; i < n; i++) t += letras[Math.floor(Math.random() * letras.length)]; return t; };
    var clave = dialogo.querySelector('[data-clave]');
    clave.textContent = 'Ct-' + parte(4) + '-' + parte(4);
    clave.classList.remove('esta-actualizado');
    void clave.offsetWidth;
    clave.classList.add('esta-actualizado');
  }
  dialogo.querySelector('[data-generar-clave]').addEventListener('click', generarClave);

  /* ---------- Rol: la especialidad solo aplica a desarrolladores ---------- */
  function rolElegido() { var r = formulario.querySelector('input[name="rol"]:checked'); return r ? r.value : 'desarrollador'; }
  function ajustarEspecialidad() {
    var esDesarrollador = !bloqueEquipo.hidden && rolElegido() === 'desarrollador';
    campoEspecialidad.hidden = !esDesarrollador;
    campos.especialidad.required = esDesarrollador;
    var integrantes = dialogo.querySelector('[data-campo-integrantes]');
    if (integrantes) integrantes.hidden = !esDesarrollador;
  }
  formulario.querySelectorAll('input[name="rol"]').forEach(function (radio) { radio.addEventListener('change', ajustarEspecialidad); });

  /* ---------- Abrir el formulario ---------- */
  function limpiar() {
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (campo) { campo.classList.remove('campo--invalido', 'campo--valido'); });
  }

  function abrirFormulario(fila) {
    limpiar();
    filaActual = fila;
    modo = fila ? 'editar' : 'crear';
    var tipo = fila ? fila.getAttribute('data-tipo') : 'desarrollador';
    var esEquipo = tipo !== 'cliente';

    // Los clientes usan cualquier correo; el equipo, el corporativo
    if (esEquipo) campos.correo.setAttribute('pattern', campos.correo.getAttribute('data-patron-equipo'));
    else campos.correo.removeAttribute('pattern');
    dialogo.querySelector('[data-ayuda-correo]').textContent = esEquipo ? 'Será su usuario para iniciar sesión.' : 'Si cambia el correo, el cliente iniciará sesión con el nuevo.';

    // Rol y especialidad: no aplican a clientes ni a la propia cuenta del administrador
    bloqueEquipo.hidden = tipo === 'cliente' || tipo === 'administrador' && !!fila;
    bloqueClave.hidden = !!fila;

    if (fila) {
      campos.nombre.value = fila.getAttribute('data-nombre');
      campos.telefono.value = fila.getAttribute('data-telefono');
      campos.correo.value = fila.getAttribute('data-correo');
      formulario.querySelector('input[name="rol"][value="' + (tipo === 'administrador' ? 'administrador' : 'desarrollador') + '"]').checked = true;
      campos.especialidad.value = fila.getAttribute('data-especialidad') || '';
      dialogo.querySelector('[data-titulo-usuario]').textContent = 'Editar ' + (tipo === 'cliente' ? 'cliente' : tipo === 'administrador' ? 'tu cuenta' : 'desarrollador');
      dialogo.querySelector('[data-texto-usuario]').textContent = 'Los cambios quedan registrados en la auditoría.';
      dialogo.querySelector('[data-boton-usuario]').textContent = 'Guardar cambios';
    } else {
      generarClave();
      dialogo.querySelector('[data-titulo-usuario]').textContent = 'Crear cuenta de equipo';
      dialogo.querySelector('[data-texto-usuario]').textContent = 'Los desarrolladores no se registran solos: tú creas su cuenta y les llega un correo con sus datos de acceso.';
      dialogo.querySelector('[data-boton-usuario]').textContent = 'Crear cuenta';
    }
    ajustarEspecialidad();
    dialogo.showModal();
    campos.nombre.focus();
  }

  document.querySelector('[data-abrir-crear]').addEventListener('click', function () { abrirFormulario(null); });

  /* ---------- Clics dentro de la tabla y las ventanas ---------- */
  document.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-abrir]');
    if (boton) {
      var fila = boton.closest('tr');
      var tipo = boton.getAttribute('data-abrir');
      if (tipo === 'editar') abrirFormulario(fila);
      else abrirConfirmacion(tipo, fila);
    }
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });

  /* ---------- Guardar (crear o editar) ---------- */
  function iniciales(nombre) { return nombre.split(/\s+/).slice(0, 2).map(function (p) { return p.charAt(0).toUpperCase(); }).join(''); }

  function crearFila(datos) {
    var esAdmin = datos.rol === 'administrador';
    var fila = document.createElement('tr');
    fila.setAttribute('data-fila', '');
    fila.setAttribute('data-estado', datos.rol + ' activo');
    fila.setAttribute('data-tipo', datos.rol);
    fila.setAttribute('data-activos', '0');
    var primerNombre = datos.nombre.split(' ')[0];
    fila.innerHTML =
      '<td data-etiqueta="Usuario" class="celda--ancha"><span class="celda-usuario"><span class="avatar' + (esAdmin ? ' avatar--gris' : '') + '" aria-hidden="true" data-celda-iniciales></span><span class="celda-principal"><strong data-celda-nombre></strong><small data-celda-correo></small></span></span></td>' +
      '<td data-etiqueta="Rol"><span class="insignia insignia--' + (esAdmin ? 'oscuro' : 'activo') + '" data-celda-rol>' + (esAdmin ? 'Administrador' : 'Equipo · Product Owner') + '</span></td>' +
      '<td data-etiqueta="Actividad" data-celda-actividad></td>' +
      '<td data-etiqueta="Último acceso"><span class="celda-principal"><small>Aún no ha ingresado</small></span></td>' +
      '<td data-etiqueta="Estado"><span class="insignia insignia--atendida" data-insignia-estado>Activo</span></td>' +
      '<td><div class="celda-acciones" data-acciones>' +
        '<button type="button" class="boton boton--contorno boton--pequeno" data-abrir="editar">' + ICONOS.editar + 'Editar</button>' +
        '<details class="menu-acciones"><summary class="boton-icono boton-icono--pequeno" title="Más acciones">' + ICONOS.puntos + '</summary>' +
        '<div class="menu-acciones__lista" role="menu">' +
          (esAdmin ? '' : '<a class="menu-acciones__opcion" role="menuitem" href="proyectos.html?buscar=' + encodeURIComponent(primerNombre) + '">' + ICONOS.proyectos + 'Ver sus proyectos</a>') +
          '<button type="button" class="menu-acciones__opcion" role="menuitem" data-abrir="clave">' + ICONOS.llave + 'Restablecer contraseña</button>' +
          '<button type="button" class="menu-acciones__opcion menu-acciones__opcion--peligro" role="menuitem" data-abrir="desactivar">' + ICONOS.apagar + 'Desactivar cuenta</button>' +
        '</div></details></div></td>';
    fila.querySelector('summary').setAttribute('aria-label', 'Más acciones para ' + datos.nombre);
    return fila;
  }

  function actividadDesarrollador(especialidad, activos) {
    var unidades = '';
    for (var i = 0; i < 3; i++) unidades += '<span class="carga__unidad' + (i < activos ? ' carga__unidad--ocupada' : '') + '"></span>';
    return '<span class="celda-actividad"><span class="celda-principal"><strong></strong><small>' + activos + ' de 3 proyectos activos</small></span>' +
      '<span class="carga" role="img" aria-label="' + activos + ' de 3 proyectos activos">' + unidades + '</span></span>';
  }

  function pintarFila(fila, datos) {
    fila.setAttribute('data-nombre', datos.nombre);
    fila.setAttribute('data-correo', datos.correo);
    fila.setAttribute('data-telefono', datos.telefono);
    fila.querySelector('[data-celda-nombre]').textContent = datos.nombre;
    fila.querySelector('[data-celda-correo]').textContent = datos.correo;
    fila.querySelector('[data-celda-iniciales]').textContent = iniciales(datos.nombre);
    if (fila.getAttribute('data-tipo') === 'desarrollador') {
      fila.setAttribute('data-especialidad', datos.especialidad);
      var celda = fila.querySelector('[data-celda-actividad]');
      celda.innerHTML = actividadDesarrollador(datos.especialidad, Number(fila.getAttribute('data-activos')));
      celda.querySelector('strong').textContent = datos.especialidad;
    } else if (fila.getAttribute('data-tipo') === 'administrador' && !fila.querySelector('[data-celda-actividad] small')) {
      fila.querySelector('[data-celda-actividad]').innerHTML = '<span class="celda-principal"><small>Gestiona todo el sistema</small></span>';
    }
  }

  // Cambiar el rol de un desarrollador a administrador (solo si no tiene proyectos activos)
  function cambiarRol(fila, rol) {
    var actual = fila.getAttribute('data-tipo');
    if (actual === 'cliente' || actual === rol) return true;
    if (actual === 'desarrollador' && Number(fila.getAttribute('data-activos')) > 0) {
      CoreTech.mostrarNotificacion(fila.getAttribute('data-nombre') + ' tiene proyectos activos: reasígnalos antes de cambiarle el rol.', 'error');
      return false;
    }
    var esAdmin = rol === 'administrador';
    fila.setAttribute('data-tipo', rol);
    fila.setAttribute('data-estado', fila.getAttribute('data-estado').replace(actual, rol));
    var insignia = fila.querySelector('[data-celda-rol]');
    insignia.className = 'insignia insignia--' + (esAdmin ? 'oscuro' : 'activo');
    insignia.textContent = esAdmin ? 'Administrador' : 'Equipo · Product Owner';
    fila.querySelector('[data-celda-iniciales]').classList.toggle('avatar--gris', esAdmin);
    if (esAdmin) fila.querySelector('[data-celda-actividad]').innerHTML = '<span class="celda-principal"><small>Gestiona todo el sistema</small></span>';
    return true;
  }

  formulario.addEventListener('formulario-valido', function () {
    var datos = {
      nombre: campos.nombre.value.trim().replace(/\s+/g, ' '),
      telefono: campos.telefono.value.trim(),
      correo: campos.correo.value.trim().toLowerCase(),
      especialidad: campos.especialidad.value,
      rol: rolElegido()
    };

    // El correo no se puede repetir
    var repetido = Array.prototype.some.call(document.querySelectorAll('[data-fila]'), function (fila) {
      return fila !== filaActual && fila.getAttribute('data-correo') === datos.correo;
    });
    if (repetido) {
      var campo = campos.correo.closest('.campo');
      campo.classList.add('campo--invalido');
      campo.querySelector('.campo__mensaje-texto').textContent = 'Ya existe una cuenta con este correo.';
      campos.correo.focus();
      return;
    }

    var boton = formulario.querySelector('[type="submit"]');
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      var fila = filaActual;
      if (modo === 'crear') {
        fila = crearFila(datos);
        var cuerpo = document.querySelector('[data-cuerpo-tabla]');
        cuerpo.insertBefore(fila, cuerpo.firstElementChild.nextElementSibling);   // debajo de la cuenta propia
        pintarFila(fila, datos);
        CoreTech.mostrarNotificacion('Cuenta creada. Enviamos los datos de acceso a ' + datos.correo + '.');
      } else {
        if (!bloqueEquipo.hidden && !cambiarRol(fila, datos.rol)) return;
        pintarFila(fila, datos);
        CoreTech.mostrarNotificacion('Datos de ' + datos.nombre + ' actualizados.');
      }
      dialogo.close();
      fila.classList.remove('esta-resaltada');
      void fila.offsetWidth;
      fila.classList.add('esta-resaltada');
      CoreTech.tabla.actualizar();
    }, 700);
  });

  /* ---------- Confirmaciones: desactivar, activar y restablecer contraseña ---------- */
  var accionActual = null;

  function abrirConfirmacion(tipo, fila) {
    var ventana = document.getElementById('dialogo-' + tipo);
    var nombre = fila.getAttribute('data-nombre');
    var correo = fila.getAttribute('data-correo');
    var confirmar = ventana.querySelector('[data-confirmar]');
    var texto = {
      desactivar: nombre + ' no podrá iniciar sesión. Sus datos, citas e historial se conservan y puedes activarlo cuando quieras.',
      activar: nombre + ' podrá volver a iniciar sesión con su correo ' + correo + '.',
      clave: 'Enviaremos a ' + correo + ' un enlace para crear una contraseña nueva. La actual dejará de funcionar.'
    }[tipo];
    ventana.querySelector('[data-texto]').textContent = texto;

    var aviso = ventana.querySelector('[data-aviso-proyectos]');
    var activos = Number(fila.getAttribute('data-activos'));
    var bloqueado = tipo === 'desactivar' && fila.getAttribute('data-tipo') === 'desarrollador' && activos > 0;
    aviso.hidden = !bloqueado;
    confirmar.disabled = bloqueado;
    if (bloqueado) {
      aviso.querySelector('[data-aviso-titulo]').textContent = nombre + ' tiene ' + activos + (activos === 1 ? ' proyecto activo.' : ' proyectos activos.');
      aviso.querySelector('[data-enlace-proyectos]').href = 'proyectos.html?buscar=' + encodeURIComponent(nombre.split(' ')[0]);
    }
    accionActual = { tipo: tipo, fila: fila };
    ventana.showModal();
  }

  function cambiarEstadoCuenta(fila, activa) {
    var tipo = fila.getAttribute('data-tipo');
    fila.setAttribute('data-estado', tipo + (activa ? ' activo' : ' inactivo'));
    fila.classList.toggle('fila--apagada', !activa);
    var insignia = fila.querySelector('[data-insignia-estado]');
    insignia.className = 'insignia insignia--' + (activa ? 'atendida' : 'cancelada');
    insignia.textContent = activa ? 'Activo' : 'Inactivo';
    var opcion = fila.querySelector('[data-abrir="desactivar"], [data-abrir="activar"]');
    opcion.setAttribute('data-abrir', activa ? 'desactivar' : 'activar');
    opcion.classList.toggle('menu-acciones__opcion--peligro', activa);
    opcion.innerHTML = activa ? ICONOS.apagar + 'Desactivar cuenta' : ICONOS.activar + 'Activar cuenta';
  }

  document.querySelectorAll('[data-confirmar]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      if (!accionActual) return;
      var accion = accionActual;
      boton.classList.add('esta-cargando');
      setTimeout(function () {
        boton.classList.remove('esta-cargando');
        boton.closest('dialog').close();
        var nombre = accion.fila.getAttribute('data-nombre');
        if (accion.tipo === 'desactivar') { cambiarEstadoCuenta(accion.fila, false); CoreTech.mostrarNotificacion('Cuenta de ' + nombre + ' desactivada.'); }
        if (accion.tipo === 'activar') { cambiarEstadoCuenta(accion.fila, true); CoreTech.mostrarNotificacion('Cuenta de ' + nombre + ' activada.'); }
        if (accion.tipo === 'clave') CoreTech.mostrarNotificacion('Enviamos el enlace para restablecer la contraseña a ' + accion.fila.getAttribute('data-correo') + '.');
        accion.fila.classList.remove('esta-resaltada');
        void accion.fila.offsetWidth;
        accion.fila.classList.add('esta-resaltada');
        accionActual = null;
        CoreTech.tabla.actualizar();
      }, 700);
    });
  });
})();
