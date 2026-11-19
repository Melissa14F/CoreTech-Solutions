/* ==========================================================================
   Vista: Plantillas de fases (admin/plantillas-fases.html)
   - Lista de servicios a la izquierda; el editor muestra la plantilla elegida.
   - ?servicio=clave abre directamente la plantilla de ese servicio.
   - Si hay cambios sin guardar y se cambia de servicio, se pide confirmación.
   Requiere: js/principal.js y js/plan-editor.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var DATOS = JSON.parse(document.getElementById('datos-plantillas').textContent);
  var guardadas = {};
  Object.keys(DATOS.servicios).forEach(function (k) { guardadas[k] = JSON.parse(JSON.stringify(DATOS.servicios[k].fases)); });

  var contenedor = document.querySelector('[data-editor-plantilla]');
  var titulo = document.querySelector('[data-titulo-plantilla]');
  var botones = Array.prototype.slice.call(document.querySelectorAll('[data-elegir-plantilla]'));
  var editor = CoreTech.editorPlan(contenedor, [], { cliente: 'el cliente', prefijo: 'plantilla' });
  var actual = null;
  var cambios = false;

  editor.alCambiar(function (fases) {
    if (actual) cambios = JSON.stringify(fases) !== JSON.stringify(guardadas[actual]);
  });

  function elegir(clave, enfocar) {
    if (!DATOS.servicios[clave]) clave = DATOS.orden[0];
    actual = null;
    editor.cargar(guardadas[clave]);
    actual = clave;
    cambios = false;
    titulo.textContent = DATOS.servicios[clave].nombre;
    botones.forEach(function (b) { b.setAttribute('aria-current', String(b.getAttribute('data-elegir-plantilla') === clave)); });
    if (enfocar) titulo.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  botones.forEach(function (boton) {
    boton.addEventListener('click', function () {
      var clave = boton.getAttribute('data-elegir-plantilla');
      if (clave === actual) return;
      if (cambios && !window.confirm('Tienes cambios sin guardar en «' + DATOS.servicios[actual].nombre + '». ¿Quieres descartarlos?')) return;
      elegir(clave, window.matchMedia('(max-width: 1024px)').matches);
      history.replaceState(null, '', '?servicio=' + clave);
    });
  });

  document.querySelector('[data-restablecer]').addEventListener('click', function () {
    elegir(actual);
    CoreTech.mostrarNotificacion('Se restableció la última versión guardada.', 'info');
  });

  document.querySelector('[data-guardar-plantilla]').addEventListener('click', function () {
    if (!editor.validar()) {
      CoreTech.mostrarNotificacion('Revisa la plantilla: hay fases o pasos incompletos.', 'error');
      return;
    }
    var fases = editor.obtener();
    fases.forEach(function (f) {
      f.nombre = f.nombre.trim();
      f.pasos.forEach(function (p) { p.titulo = p.titulo.trim(); p.explicacion = (p.explicacion || '').trim(); });
    });
    guardadas[actual] = fases;
    cambios = false;
    var etiqueta = document.querySelector('[data-cantidad-fases="' + actual + '"]');
    if (etiqueta) etiqueta.textContent = fases.length + ' fases';
    CoreTech.mostrarNotificacion('Plantilla de «' + DATOS.servicios[actual].nombre + '» guardada. Se usará en los proyectos nuevos.');
  });

  window.addEventListener('beforeunload', function (evento) {
    if (cambios) { evento.preventDefault(); evento.returnValue = ''; }
  });

  elegir(CoreTech.parametros.get('servicio') || DATOS.orden[0]);
})();
