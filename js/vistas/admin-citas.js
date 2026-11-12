/* ==========================================================================
   Vista: Citas del administrador (admin/citas.html)
   - Asignar o reasignar el desarrollador de una cita (RN-12: el administrador
     asigna y el desarrollador confirma y se comunica con el cliente).
   - Cancelar una cita pendiente o confirmada con un motivo (queda en auditoría).
   Los filtros y la búsqueda los maneja js/tabla.js.
   Requiere: js/principal.js y js/tabla.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var filaActual = null;

  /* ---------- Abrir y cerrar ventanas ---------- */
  function limpiar(formulario) {
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (campo) { campo.classList.remove('campo--invalido', 'campo--valido'); });
    formulario.querySelectorAll('textarea').forEach(function (area) { area.dispatchEvent(new Event('input')); });
  }

  function resumen(fila) {
    return '<strong>' + fila.getAttribute('data-codigo') + ' · ' + fila.getAttribute('data-cliente') + '</strong>' +
      '<span>' + fila.getAttribute('data-servicio') + ' · ' + fila.getAttribute('data-fecha') + ' · ' + fila.getAttribute('data-modalidad') + '</span>';
  }

  function abrir(tipo, fila) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    filaActual = fila;
    limpiar(formulario);
    dialogo.querySelector('[data-resumen]').innerHTML = resumen(fila);

    if (tipo === 'asignar') {
      var actual = fila.getAttribute('data-desarrollador');
      dialogo.querySelector('[data-titulo-asignar]').textContent = actual ? 'Reasignar equipo' : 'Asignar equipo';
      formulario.querySelectorAll('input[name="desarrollador"]').forEach(function (opcion) {
        opcion.checked = opcion.value === actual;
      });
    }
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
          void filaActual.offsetWidth;   // reinicia la animación
          filaActual.classList.add('esta-resaltada');
        }
        filaActual = null;
        CoreTech.tabla.actualizar();
        actualizarMenu();
      }, 700);
    });
  }

  /* ---------- Contador "sin asignar" del menú lateral ---------- */
  function actualizarMenu() {
    var cantidad = document.querySelectorAll('[data-fila][data-estado~="sin-asignar"]').length;
    var contador = document.querySelector('.navegacion-panel__enlace[aria-current="page"] .navegacion-panel__contador');
    if (!contador) return;
    contador.textContent = cantidad;
    contador.setAttribute('aria-label', cantidad + ' sin asignar');
    contador.hidden = cantidad === 0;
  }

  /* ---------- Asignar o reasignar ---------- */
  alGuardar('asignar', function (fila, datos) {
    var nombre = datos.get('desarrollador');
    var anterior = fila.getAttribute('data-desarrollador');
    var opcion = document.querySelector('input[name="desarrollador"][value="' + nombre + '"]');
    var iniciales = opcion.getAttribute('data-iniciales');

    fila.setAttribute('data-desarrollador', nombre);
    fila.setAttribute('data-estado', fila.getAttribute('data-estado').replace(' sin-asignar', ''));
    fila.classList.remove('fila--atencion');
    var celda = fila.querySelector('[data-celda-desarrollador]');
    celda.innerHTML = '<span class="celda-persona"><span class="avatar" aria-hidden="true"></span><span></span></span>';
    celda.querySelector('.avatar').textContent = iniciales;
    celda.querySelector('.avatar + span').textContent = nombre;

    // Una cita sin asignar pasa a tener "Reasignar"
    var principal = fila.querySelector('[data-abrir="asignar"]');
    if (principal && principal.classList.contains('boton--acento')) {
      principal.classList.replace('boton--acento', 'boton--contorno');
      principal.innerHTML = '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>Reasignar';
    }

    var codigo = fila.getAttribute('data-codigo');
    if (anterior && anterior !== nombre) {
      CoreTech.mostrarNotificacion(codigo + ' pasó de ' + anterior + ' a ' + nombre + '. Les avisamos a los dos.');
    } else {
      CoreTech.mostrarNotificacion(codigo + ' asignada a ' + nombre + '. Le enviamos un correo.');
    }
  });

  /* ---------- Cancelar ---------- */
  alGuardar('cancelar', function (fila) {
    fila.setAttribute('data-estado', 'cancelada');
    fila.classList.remove('fila--atencion');
    fila.classList.add('fila--apagada');
    var insignia = fila.querySelector('[data-insignia-estado]');
    insignia.className = 'insignia insignia--cancelada';
    insignia.textContent = 'Cancelada';
    var nota = fila.querySelector('.celda-nota');
    if (nota) nota.remove();
    if (!fila.getAttribute('data-desarrollador')) {
      fila.querySelector('[data-celda-desarrollador]').innerHTML = '<span class="celda-principal"><small>No se asignó</small></span>';
    }
    fila.querySelector('[data-acciones]').innerHTML = '<span class="celda-principal"><small>Cancelada por la administración hoy</small></span>';
    CoreTech.mostrarNotificacion('Cita ' + fila.getAttribute('data-codigo') + ' cancelada. Avisamos al cliente.');
  });

  actualizarMenu();
})();
