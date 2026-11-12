/* ==========================================================================
   Vista: Proyectos del administrador (admin/proyectos.html)
   - Editar nombre, alcance y fecha estimada de entrega (con motivo).
   - Reasignar el responsable: la carga de cada desarrollador se calcula con
     los proyectos activos de la tabla; quien tiene 3 queda sin cupo (RN-05).
   - Pausar, reanudar o cancelar un proyecto (con motivo, queda en auditoría).
   - El proyecto recién creado (P-0035) aparece si se llega con ?buscar=P-0035.
   Los filtros y la búsqueda los maneja js/tabla.js.
   Requiere: js/principal.js y js/tabla.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var filaActual = null;
  var HOY = '2026-11-11';

  /* ---------- Proyecto recién creado ---------- */
  var nueva = document.querySelector('[data-fila-nueva]');
  if (nueva && CoreTech.parametros.get('buscar') === 'P-0035') {
    nueva.removeAttribute('data-fila-nueva');
    nueva.setAttribute('data-fila', '');
    nueva.hidden = false;
    nueva.classList.add('esta-resaltada');
    CoreTech.tabla.actualizar();
  } else if (nueva) {
    nueva.remove();
  }

  /* ---------- Carga de cada desarrollador ---------- */
  function cargaDe(nombre) {
    return document.querySelectorAll('[data-fila][data-activo="true"][data-desarrollador="' + nombre + '"]').length;
  }

  function prepararSelector(fila) {
    var actual = fila.getAttribute('data-desarrollador');
    var esActivo = fila.getAttribute('data-activo') === 'true';
    document.querySelectorAll('#dialogo-reasignar input[name="desarrollador"]').forEach(function (opcion) {
      var nombre = opcion.value;
      var carga = cargaDe(nombre);
      var etiqueta = opcion.closest('label');
      var esActual = nombre === actual;
      var sinCupo = !esActual && esActivo && carga >= 3;
      opcion.disabled = esActual || sinCupo;
      opcion.checked = false;
      etiqueta.querySelector('[data-detalle-carga]').textContent = esActual
        ? 'Responsable actual'
        : (sinCupo ? 'Sin cupo: ya tiene 3 proyectos activos' : carga + ' de 3 proyectos activos');
      var visual = etiqueta.querySelector('[data-carga-visual]');
      visual.setAttribute('aria-label', carga + ' de 3 proyectos activos');
      visual.querySelectorAll('.carga__unidad').forEach(function (unidad, indice) {
        unidad.classList.toggle('carga__unidad--ocupada', indice < carga);
      });
    });
  }

  /* ---------- Abrir y cerrar ---------- */
  function limpiar(formulario) {
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (campo) { campo.classList.remove('campo--invalido', 'campo--valido'); });
    formulario.querySelectorAll('textarea').forEach(function (area) { area.dispatchEvent(new Event('input')); });
  }

  function abrir(tipo, fila) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    filaActual = fila;
    limpiar(formulario);
    dialogo.querySelector('[data-resumen]').innerHTML = '<strong></strong><span></span>';
    dialogo.querySelector('[data-resumen] strong').textContent = fila.getAttribute('data-codigo') + ' · ' + fila.getAttribute('data-nombre');
    dialogo.querySelector('[data-resumen] span').textContent = 'Cliente: ' + fila.getAttribute('data-cliente') + ' · Responsable: ' + fila.getAttribute('data-desarrollador');

    if (tipo === 'editar') {
      formulario.elements.nombre.value = fila.getAttribute('data-nombre');
      formulario.elements.alcance.value = fila.getAttribute('data-alcance');
      formulario.elements.entrega.value = fila.getAttribute('data-entrega') < HOY ? '' : fila.getAttribute('data-entrega');
    }
    if (tipo === 'reasignar') prepararSelector(fila);
    dialogo.showModal();
  }

  document.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-abrir]');
    if (boton) abrir(boton.getAttribute('data-abrir'), boton.closest('tr'));
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });

  function alGuardar(tipo, aplicar) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    formulario.addEventListener('formulario-valido', function () {
      var boton = formulario.querySelector('[type="submit"]');
      boton.classList.add('esta-cargando');
      setTimeout(function () {
        boton.classList.remove('esta-cargando');
        dialogo.close();
        if (filaActual) {
          aplicar(filaActual, new FormData(formulario));
          filaActual.classList.remove('esta-resaltada');
          void filaActual.offsetWidth;
          filaActual.classList.add('esta-resaltada');
        }
        filaActual = null;
        CoreTech.tabla.actualizar();
      }, 700);
    });
  }

  /* ---------- Ayudas ---------- */
  function cambiarEstado(fila, estados, texto, clase, activo) {
    fila.setAttribute('data-estado', estados);
    fila.setAttribute('data-activo', String(activo));
    var insignia = fila.querySelector('[data-insignia-estado]');
    insignia.className = 'insignia insignia--' + clase;
    insignia.textContent = texto;
  }

  function legible(iso) {
    var p = iso.split('-');
    return new Date(p[0], p[1] - 1, p[2]).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', '').replace(/ de /g, ' ');
  }

  function diasHasta(iso) {
    var p = iso.split('-'), h = HOY.split('-');
    return Math.round((new Date(p[0], p[1] - 1, p[2]) - new Date(h[0], h[1] - 1, h[2])) / 86400000);
  }

  var ICONO = {
    pausa: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>',
    play: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 3 14 9-14 9Z"/></svg>'
  };
  function cambiarBotonPausa(fila, pausado) {
    var opcion = fila.querySelector('[data-abrir="pausar"], [data-abrir="reanudar"]');
    opcion.setAttribute('data-abrir', pausado ? 'reanudar' : 'pausar');
    opcion.innerHTML = (pausado ? ICONO.play : ICONO.pausa) + (pausado ? 'Reanudar' : 'Pausar');
  }

  /* ---------- Editar ---------- */
  alGuardar('editar', function (fila, datos) {
    var nombre = datos.get('nombre').trim();
    var entrega = datos.get('entrega');
    fila.setAttribute('data-nombre', nombre);
    fila.setAttribute('data-alcance', datos.get('alcance').trim());
    fila.querySelector('[data-celda-nombre]').textContent = nombre;
    if (entrega !== fila.getAttribute('data-entrega')) {
      fila.setAttribute('data-entrega', entrega);
      fila.querySelector('[data-celda-entrega]').textContent = legible(entrega);
      var plazo = fila.querySelector('[data-celda-plazo]');
      plazo.textContent = 'Faltan ' + diasHasta(entrega) + ' días · fecha actualizada hoy';
      plazo.className = 'plazo';
      // Si estaba fuera de plazo y la nueva fecha es futura, deja de estar en riesgo por fecha
      if (fila.getAttribute('data-estado').indexOf('riesgo') !== -1 && fila.querySelector('.insignia--vencido')) {
        cambiarEstado(fila, 'activo', 'En curso', 'activo', true);
        fila.classList.remove('fila--atencion');
      }
    }
    CoreTech.mostrarNotificacion('Cambios guardados en ' + fila.getAttribute('data-codigo') + '. Avisamos al cliente y al responsable.');
  });

  /* ---------- Reasignar ---------- */
  alGuardar('reasignar', function (fila, datos) {
    var nombre = datos.get('desarrollador');
    var anterior = fila.getAttribute('data-desarrollador');
    var iniciales = document.querySelector('#dialogo-reasignar input[value="' + nombre + '"]').getAttribute('data-iniciales');
    fila.setAttribute('data-desarrollador', nombre);
    var celda = fila.querySelector('[data-celda-desarrollador]');
    celda.innerHTML = '<span class="celda-persona"><span class="avatar" aria-hidden="true"></span><span></span></span>';
    celda.querySelector('.avatar').textContent = iniciales;
    celda.querySelector('.avatar + span').textContent = nombre;
    CoreTech.mostrarNotificacion(fila.getAttribute('data-codigo') + ' pasó de ' + anterior + ' a ' + nombre + '.');
  });

  /* ---------- Pausar y reanudar ---------- */
  alGuardar('pausar', function (fila) {
    cambiarEstado(fila, 'pausado', 'Pausado', 'pausado', false);
    fila.classList.remove('fila--atencion');
    fila.classList.add('fila--apagada');
    fila.querySelector('[data-celda-plazo]').textContent = 'Pausado desde hoy';
    fila.querySelector('[data-celda-plazo]').className = 'plazo';
    cambiarBotonPausa(fila, true);
    CoreTech.mostrarNotificacion(fila.getAttribute('data-codigo') + ' pausado. Ya no cuenta como proyecto activo de ' + fila.getAttribute('data-desarrollador') + '.');
  });

  alGuardar('reanudar', function (fila) {
    var responsable = fila.getAttribute('data-desarrollador');
    if (cargaDe(responsable) >= 3) {
      CoreTech.mostrarNotificacion(responsable + ' ya tiene 3 proyectos activos. Reasigna el proyecto antes de reanudarlo.', 'error');
      return;
    }
    cambiarEstado(fila, 'activo', 'En curso', 'activo', true);
    fila.classList.remove('fila--apagada');
    fila.querySelector('[data-celda-plazo]').textContent = 'Faltan ' + diasHasta(fila.getAttribute('data-entrega')) + ' días · reanudado hoy';
    cambiarBotonPausa(fila, false);
    CoreTech.mostrarNotificacion(fila.getAttribute('data-codigo') + ' reanudado.');
  });

  /* ---------- Cancelar ---------- */
  alGuardar('cancelar', function (fila) {
    cambiarEstado(fila, 'cancelado', 'Cancelado', 'cancelada', false);
    fila.classList.remove('fila--atencion');
    fila.classList.add('fila--apagada');
    fila.querySelector('[data-celda-plazo]').textContent = 'Cancelado hoy';
    fila.querySelector('[data-celda-plazo]').className = 'plazo';
    fila.querySelector('[data-acciones]').innerHTML = '<span class="celda-principal"><small>Cerrado: se conserva el historial</small></span>';
    CoreTech.mostrarNotificacion(fila.getAttribute('data-codigo') + ' cancelado. Avisamos al cliente y al responsable.');
  });
})();
