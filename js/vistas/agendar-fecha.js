/* ==========================================================================
   Vista: Agendar cita · Paso 2 (cliente/agendar-fecha.html)
   - Calendario de mes: cambia entre meses con las flechas.
   - Al elegir un día muestra sus horarios libres.
   - Mantiene actualizado el resumen lateral (incluida la descripción).
   - Si el usuario vuelve desde el paso 3, recupera lo que había elegido.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var formulario = document.querySelector('form[data-validar]');
  if (!formulario) return;

  var meses = Array.prototype.slice.call(document.querySelectorAll('[data-mes]'));
  var tituloMes = document.querySelector('[data-titulo-mes-actual]');
  var botonAnterior = document.querySelector('[data-mes-anterior]');
  var botonSiguiente = document.querySelector('[data-mes-siguiente]');
  var avisoSinDia = document.querySelector('[data-franjas-vacio]');
  var resumenFecha = document.querySelector('[data-resumen="fecha"]');
  var resumenModalidad = document.querySelector('[data-resumen="modalidad"]');
  var resumenDescripcion = document.querySelector('[data-resumen="descripcion"]');
  var descripcion = formulario.querySelector('[name="descripcion"]');
  var mesVisible = 0;

  /* ---------- Meses ---------- */
  function mostrarMes(indice) {
    mesVisible = indice;
    meses.forEach(function (mes, i) { mes.classList.toggle('calendario__mes--oculto', i !== indice); });
    tituloMes.textContent = meses[indice].getAttribute('data-titulo-mes');
    botonAnterior.disabled = indice === 0;
    botonSiguiente.disabled = indice === meses.length - 1;
  }
  botonAnterior.addEventListener('click', function () { mostrarMes(mesVisible - 1); });
  botonSiguiente.addEventListener('click', function () { mostrarMes(mesVisible + 1); });

  /* ---------- Día y horarios ---------- */
  function mostrarHorariosDe(fecha) {
    document.querySelectorAll('[data-franjas-de]').forEach(function (grupo) {
      grupo.classList.toggle('franjas--oculta', grupo.getAttribute('data-franjas-de') !== fecha);
    });
    avisoSinDia.hidden = !!fecha;
  }

  function textoElegido(nombre) {
    var elegido = formulario.querySelector('[name="' + nombre + '"]:checked');
    return elegido ? elegido.getAttribute('data-texto') : '';
  }

  function actualizarResumen() {
    var dia = textoElegido('fecha');
    var hora = textoElegido('hora');
    var modalidad = textoElegido('modalidad');
    resumenFecha.textContent = dia ? dia + (hora ? ' · ' + hora : ' · falta la hora') : 'Sin elegir';
    resumenFecha.classList.toggle('resumen-cita__valor--pendiente', !(dia && hora));
    resumenModalidad.textContent = modalidad || 'Sin elegir';
    resumenModalidad.classList.toggle('resumen-cita__valor--pendiente', !modalidad);
    var texto = descripcion.value.trim();
    resumenDescripcion.textContent = texto || 'Sin escribir';
    resumenDescripcion.title = texto;
    resumenDescripcion.classList.toggle('resumen-cita__valor--pendiente', !texto);
  }

  // La descripción se escribe: el resumen y el enlace "Continuar" se actualizan mientras tanto
  descripcion.addEventListener('input', function () {
    actualizarResumen();
    window.CoreTech.actualizarEnlacesDePaso();
  });

  formulario.addEventListener('change', function (evento) {
    if (evento.target.name === 'fecha') {
      // Al cambiar de día se borra la hora elegida: cada día tiene sus propios horarios
      formulario.querySelectorAll('[name="hora"]:checked').forEach(function (hora) { hora.checked = false; });
      mostrarHorariosDe(evento.target.value);
    }
    actualizarResumen();
    window.CoreTech.actualizarEnlacesDePaso();
  });

  /* ---------- Recuperar lo elegido al volver desde el paso 3 ---------- */
  function marcar(nombre, valor) {
    if (!valor) return null;
    var opcion = formulario.querySelector('[name="' + nombre + '"][value="' + valor + '"]:not(:disabled)');
    if (opcion) opcion.checked = true;
    return opcion;
  }
  var parametros = window.CoreTech.parametros;
  var diaGuardado = marcar('fecha', parametros.get('fecha'));
  if (diaGuardado) {
    mostrarMes(meses.indexOf(diaGuardado.closest('[data-mes]')));
    mostrarHorariosDe(diaGuardado.value);
    var grupo = document.querySelector('[data-franjas-de="' + diaGuardado.value + '"]');
    var horaGuardada = grupo && grupo.querySelector('[name="hora"][value="' + parametros.get('hora') + '"]:not(:disabled)');
    if (horaGuardada) horaGuardada.checked = true;
  } else {
    mostrarMes(0);
    mostrarHorariosDe(null);
  }
  marcar('modalidad', parametros.get('modalidad'));
  if (parametros.get('descripcion')) {
    descripcion.value = parametros.get('descripcion');
    descripcion.dispatchEvent(new Event('input')); // actualiza el contador de caracteres
  }
  actualizarResumen();
  window.CoreTech.actualizarEnlacesDePaso();
})();
