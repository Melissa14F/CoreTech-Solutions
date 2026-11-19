/* ==========================================================================
   Vista: Desarrolladores (admin/desarrolladores.html)
   - Registrar y editar desarrolladores (nombre, celular, correo corporativo y
     especialidad). No reciben cuenta: solo el Product Owner de cada equipo
     entra a la plataforma (su cuenta se crea en Equipos).
   - Desactivar y activar. Un Product Owner no se desactiva mientras su equipo
     no tenga otro; un integrante que se desactiva sale de su equipo.
   - El resumen por especialidad se recalcula con cada cambio.
   Los filtros y la búsqueda los maneja js/tabla.js.
   Requiere: js/principal.js y js/tabla.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var dialogo = document.getElementById('dialogo-desarrollador');
  var formulario = dialogo.querySelector('form');
  var campos = formulario.elements;
  var cuerpo = document.querySelector('[data-cuerpo-tabla]');
  var filaActual = null;

  // Rol en el equipo que puede tomar cada especialidad (además de Product Owner)
  var ROLES_POSIBLES = {
    'Frontend': ['Frontend'], 'Backend': ['Backend'], 'Full stack': ['Full stack', 'Frontend', 'Backend'],
    'Móvil': ['Móvil'], 'QA': ['QA'], 'UX/UI': ['UX/UI']
  };
  var ICONOS = {
    editar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>',
    puntos: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/></svg>',
    apagar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M18.36 6.64a9 9 0 1 1-12.73 0M12 2v10"/></svg>',
    activar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>'
  };

  function filas() { return Array.prototype.slice.call(document.querySelectorAll('[data-fila]')); }
  function iniciales(nombre) { return nombre.split(/\s+/).slice(0, 2).map(function (p) { return p.charAt(0).toUpperCase(); }).join(''); }
  function tieneEstado(fila, estado) { return (' ' + fila.getAttribute('data-estado') + ' ').indexOf(' ' + estado + ' ') !== -1; }
  function resaltar(fila) { fila.classList.remove('esta-resaltada'); void fila.offsetWidth; fila.classList.add('esta-resaltada'); }

  /* ---------- Resumen por especialidad ---------- */
  function actualizarResumen() {
    document.querySelectorAll('[data-conteo-especialidad]').forEach(function (n) {
      var esp = n.getAttribute('data-conteo-especialidad');
      n.textContent = filas().filter(function (f) { return f.getAttribute('data-especialidad') === esp && tieneEstado(f, 'activo'); }).length;
    });
    document.querySelector('[data-total-sin-equipo]').textContent =
      filas().filter(function (f) { return tieneEstado(f, 'activo') && tieneEstado(f, 'sin-equipo'); }).length;
    CoreTech.tabla.actualizar();
  }
  document.querySelector('[data-ver-sin-equipo]').addEventListener('click', function (evento) {
    evento.preventDefault();
    document.querySelector('[data-filtro-estado="sin-equipo"]').click();
    document.querySelector('[data-tabla]').scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  /* ---------- Registrar y editar ---------- */
  function limpiar() {
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (c) { c.classList.remove('campo--invalido', 'campo--valido'); });
    dialogo.querySelector('[data-aviso-rol]').hidden = true;
  }

  function abrirFormulario(fila) {
    limpiar();
    filaActual = fila;
    if (fila) {
      campos.nombre.value = fila.getAttribute('data-nombre');
      campos.telefono.value = fila.getAttribute('data-telefono');
      campos.correo.value = fila.getAttribute('data-correo');
      campos.especialidad.value = fila.getAttribute('data-especialidad');
      dialogo.querySelector('[data-titulo]').textContent = 'Editar desarrollador';
      dialogo.querySelector('[data-texto]').textContent = fila.getAttribute('data-rol') === 'Product Owner'
        ? 'Es Product Owner: su correo es también su usuario para iniciar sesión.'
        : 'Los cambios quedan registrados en la auditoría.';
      dialogo.querySelector('[data-boton]').textContent = 'Guardar cambios';
    } else {
      dialogo.querySelector('[data-titulo]').textContent = 'Registrar desarrollador';
      dialogo.querySelector('[data-texto]').textContent = 'Queda en el registro de CoreTech. Sin cuenta en la plataforma: solo el Product Owner de cada equipo entra a la plataforma.';
      dialogo.querySelector('[data-boton]').textContent = 'Registrar';
    }
    dialogo.showModal();
    campos.nombre.focus();
  }

  // Si cambia la especialidad de alguien que está en un equipo, se avisa si su rol ya no corresponde
  campos.especialidad.addEventListener('change', function () {
    var aviso = dialogo.querySelector('[data-aviso-rol]');
    var rol = filaActual && filaActual.getAttribute('data-rol');
    var posibles = ROLES_POSIBLES[campos.especialidad.value] || [];
    var choca = rol && rol !== 'Product Owner' && campos.especialidad.value && posibles.indexOf(rol) === -1;
    aviso.hidden = !choca;
    if (choca) {
      aviso.querySelector('[data-aviso-rol-texto]').textContent = 'En el ' + filaActual.getAttribute('data-equipo') + ' tiene el rol ' + rol +
        '. Con esta especialidad puede ser ' + posibles.join(', ') + ': al guardar, su rol cambia a ' + posibles[0] + '.';
    }
  });

  document.querySelector('[data-abrir-crear]').addEventListener('click', function () { abrirFormulario(null); });

  function crearFila() {
    var fila = document.createElement('tr');
    fila.setAttribute('data-fila', '');
    fila.setAttribute('data-estado', 'activo sin-equipo');
    fila.setAttribute('data-equipo', '');
    fila.setAttribute('data-rol', '');
    fila.innerHTML =
      '<td data-etiqueta="Desarrollador" class="celda--ancha"><span class="celda-usuario"><span class="avatar" aria-hidden="true" data-celda-iniciales></span><span class="celda-principal"><strong data-celda-nombre></strong><small data-celda-correo></small></span></span></td>' +
      '<td data-etiqueta="Especialidad"><span class="insignia insignia--especialidad" data-celda-especialidad></span></td>' +
      '<td data-etiqueta="Equipo y rol" data-celda-equipo><span class="celda-principal"><small class="texto-tenue">Sin equipo</small></span></td>' +
      '<td data-etiqueta="Contacto"><span class="celda-principal"><small data-celda-telefono></small><small class="texto-tenue">Ingresó en nov 2026</small></span></td>' +
      '<td data-etiqueta="Estado"><span class="insignia insignia--atendida" data-insignia-estado>Activo</span></td>' +
      '<td><div class="celda-acciones"><button type="button" class="boton boton--contorno boton--pequeno" data-abrir="editar">' + ICONOS.editar + 'Editar</button>' +
        '<details class="menu-acciones"><summary class="boton-icono boton-icono--pequeno" title="Más acciones">' + ICONOS.puntos + '</summary>' +
        '<div class="menu-acciones__lista" role="menu"><button type="button" class="menu-acciones__opcion menu-acciones__opcion--peligro" role="menuitem" data-abrir="desactivar">' + ICONOS.apagar + 'Desactivar</button></div></details></div></td>';
    return fila;
  }

  function pintarFila(fila, datos) {
    fila.setAttribute('data-nombre', datos.nombre);
    fila.setAttribute('data-correo', datos.correo);
    fila.setAttribute('data-telefono', datos.telefono);
    fila.setAttribute('data-especialidad', datos.especialidad);
    fila.querySelector('[data-celda-nombre]').textContent = datos.nombre;
    fila.querySelector('[data-celda-correo]').textContent = datos.correo;
    fila.querySelector('[data-celda-iniciales]').textContent = iniciales(datos.nombre);
    fila.querySelector('[data-celda-telefono]').textContent = datos.telefono;
    fila.querySelector('[data-celda-especialidad]').textContent = datos.especialidad;
    fila.querySelector('summary').setAttribute('aria-label', 'Más acciones para ' + datos.nombre);
    // Rol que ya no corresponde a la especialidad: toma el primero posible
    var rol = fila.getAttribute('data-rol');
    var posibles = ROLES_POSIBLES[datos.especialidad];
    if (rol && rol !== 'Product Owner' && posibles.indexOf(rol) === -1) {
      fila.setAttribute('data-rol', posibles[0]);
      fila.querySelector('[data-celda-rol]').textContent = 'Rol: ' + posibles[0];
    }
  }

  formulario.addEventListener('formulario-valido', function () {
    var datos = {
      nombre: campos.nombre.value.trim().replace(/\s+/g, ' '),
      telefono: campos.telefono.value.trim(),
      correo: campos.correo.value.trim().toLowerCase(),
      especialidad: campos.especialidad.value
    };
    var repetido = filas().some(function (f) { return f !== filaActual && f.getAttribute('data-correo') === datos.correo; });
    if (repetido) {
      var campo = campos.correo.closest('.campo');
      campo.classList.add('campo--invalido');
      campo.querySelector('.campo__mensaje-texto').textContent = 'Ya hay un desarrollador registrado con este correo.';
      campos.correo.focus();
      return;
    }
    var boton = formulario.querySelector('[type="submit"]');
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      var fila = filaActual;
      if (!fila) {
        fila = crearFila();
        cuerpo.insertBefore(fila, cuerpo.firstElementChild);
        pintarFila(fila, datos);
        CoreTech.mostrarNotificacion(datos.nombre + ' quedó registrado. Ya puedes sumarlo a un equipo.');
      } else {
        pintarFila(fila, datos);
        CoreTech.mostrarNotificacion('Datos de ' + datos.nombre + ' actualizados.');
      }
      dialogo.close();
      resaltar(fila);
      actualizarResumen();
    }, 600);
  });

  /* ---------- Desactivar y activar ---------- */
  var ventana = document.getElementById('dialogo-estado');
  var confirmar = ventana.querySelector('[data-confirmar-estado]');
  var accion = null;

  function abrirEstado(tipo, fila) {
    var nombre = fila.getAttribute('data-nombre');
    var equipo = fila.getAttribute('data-equipo');
    var rol = fila.getAttribute('data-rol');
    var bloqueo = ventana.querySelector('[data-estado-bloqueo]');
    var esPO = rol === 'Product Owner';
    var activar = tipo === 'activar';
    ventana.querySelector('[data-estado-titulo]').textContent = (activar ? 'Activar' : 'Desactivar') + ' desarrollador';
    ventana.querySelector('[data-estado-icono]').className = 'dialogo__icono dialogo__icono--' + (activar ? 'acento' : 'peligro');
    ventana.querySelector('[data-estado-icono]').innerHTML = (activar ? ICONOS.activar : ICONOS.apagar).replace('class="icono"', 'class="icono icono--grande"');
    ventana.querySelector('[data-estado-texto]').textContent = activar
      ? nombre + ' vuelve a estar disponible para sumarse a un equipo.'
      : nombre + ' queda en el registro como inactivo y no se puede asignar a equipos.' + (equipo && !esPO ? ' Saldrá del ' + equipo + '.' : '');
    confirmar.textContent = activar ? 'Activar' : 'Desactivar';
    confirmar.className = 'boton ' + (activar ? 'boton--acento' : 'boton--peligro');
    var bloqueado = !activar && esPO;
    bloqueo.hidden = !bloqueado;
    confirmar.disabled = bloqueado;
    if (bloqueado) {
      bloqueo.querySelector('[data-estado-bloqueo-titulo]').textContent = nombre + ' es el Product Owner del ' + equipo + '.';
      bloqueo.querySelector('[data-estado-enlace]').href = 'equipos.html#equipo-' + fila.getAttribute('data-id');
    }
    accion = { tipo: tipo, fila: fila };
    ventana.showModal();
  }

  function cambiarEstado(fila, activo) {
    var estados = [activo ? 'activo' : 'inactivo'];
    if (!activo && fila.getAttribute('data-equipo')) {
      // Al desactivarse sale de su equipo
      fila.setAttribute('data-equipo', '');
      fila.setAttribute('data-rol', '');
      fila.querySelector('[data-celda-equipo]').innerHTML = '<span class="celda-principal"><small class="texto-tenue">Sin equipo</small></span>';
      var ver = fila.querySelector('a.menu-acciones__opcion');
      if (ver) ver.remove();
    }
    estados.push(fila.getAttribute('data-equipo') ? 'con-equipo' : 'sin-equipo');
    fila.setAttribute('data-estado', estados.join(' '));
    fila.classList.toggle('fila--apagada', !activo);
    var insignia = fila.querySelector('[data-insignia-estado]');
    insignia.className = 'insignia insignia--' + (activo ? 'atendida' : 'cancelada');
    insignia.textContent = activo ? 'Activo' : 'Inactivo';
    var opcion = fila.querySelector('[data-abrir="desactivar"], [data-abrir="activar"]');
    opcion.setAttribute('data-abrir', activo ? 'desactivar' : 'activar');
    opcion.classList.toggle('menu-acciones__opcion--peligro', activo);
    opcion.innerHTML = activo ? ICONOS.apagar + 'Desactivar' : ICONOS.activar + 'Activar';
  }

  confirmar.addEventListener('click', function () {
    if (!accion) return;
    var a = accion;
    confirmar.classList.add('esta-cargando');
    setTimeout(function () {
      confirmar.classList.remove('esta-cargando');
      ventana.close();
      var equipo = a.fila.getAttribute('data-equipo');
      cambiarEstado(a.fila, a.tipo === 'activar');
      CoreTech.mostrarNotificacion(a.tipo === 'activar'
        ? a.fila.getAttribute('data-nombre') + ' está activo de nuevo.'
        : a.fila.getAttribute('data-nombre') + ' quedó inactivo' + (equipo ? ' y salió del ' + equipo + '.' : '.'));
      resaltar(a.fila);
      accion = null;
      actualizarResumen();
    }, 600);
  });

  /* ---------- Clics ---------- */
  document.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-abrir]');
    if (boton) {
      var fila = boton.closest('tr');
      var tipo = boton.getAttribute('data-abrir');
      if (tipo === 'editar') abrirFormulario(fila);
      else abrirEstado(tipo, fila);
    }
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });
})();
