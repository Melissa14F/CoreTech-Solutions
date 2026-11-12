/* ==========================================================================
   Vista: Contenido del sitio (admin/contenido.html)
   - Cada texto se refleja al instante en la vista previa de su sección.
   - Marca las secciones con cambios (en el índice y en la tarjeta) y cuenta
     los textos sin publicar. Descartar vuelve a los textos publicados.
   - Publicar valida todos los campos (js/principal.js) y guarda.
   - El índice resalta la sección que se está viendo.
   - Avisa antes de salir de la página si quedan cambios sin publicar.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var formulario = document.querySelector('[data-formulario-contenido]');
  var controles = Array.prototype.slice.call(formulario.querySelectorAll('input, textarea'));
  var barra = document.querySelector('[data-barra-cambios]');
  var temporizadores = {};

  function cambiado(control) { return control.value !== control.defaultValue; }

  /* ---------- Vista previa ---------- */
  function pintar(control) {
    var destino = document.querySelector('[data-previa="' + control.name + '"]');
    if (!destino) return;
    var texto = control.value.trim();
    destino.textContent = texto || 'Sin texto';
    destino.classList.toggle('esta-vacio', !texto);
    // Destello breve en lo que acaba de cambiar
    destino.classList.add('esta-cambiando');
    clearTimeout(temporizadores[control.name]);
    temporizadores[control.name] = setTimeout(function () { destino.classList.remove('esta-cambiando'); }, 500);
  }

  /* ---------- Marcas de cambios ---------- */
  function actualizarMarcas() {
    var total = 0;
    document.querySelectorAll('[data-seccion]').forEach(function (seccion) {
      var cambios = Array.prototype.filter.call(seccion.querySelectorAll('input, textarea'), cambiado).length;
      total += cambios;
      seccion.classList.toggle('tiene-cambios', cambios > 0);
      seccion.querySelector('[data-marca-cambio]').hidden = cambios === 0;
      document.querySelector('[data-enlace-seccion="' + seccion.getAttribute('data-seccion') + '"]').classList.toggle('tiene-cambios', cambios > 0);
    });
    barra.hidden = total === 0;
    document.querySelector('[data-cantidad-cambios]').textContent = total + (total === 1 ? ' texto' : ' textos');
    return total;
  }

  controles.forEach(function (control) {
    control.addEventListener('input', function () { pintar(control); actualizarMarcas(); });
  });

  /* ---------- Descartar y publicar ---------- */
  document.querySelector('[data-descartar]').addEventListener('click', function () {
    controles.forEach(function (control) {
      if (!cambiado(control)) return;
      control.value = control.defaultValue;
      control.dispatchEvent(new Event('input'));   // actualiza contador y vista previa
      var campo = control.closest('.campo');
      if (campo) campo.classList.remove('campo--invalido', 'campo--valido');
    });
    actualizarMarcas();
    window.CoreTech.mostrarNotificacion('Se descartaron los cambios. Los textos publicados siguen igual.');
  });

  formulario.addEventListener('formulario-valido', function () {
    var cantidad = actualizarMarcas();
    var boton = formulario.querySelector('[type="submit"]');
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      controles.forEach(function (control) {
        control.defaultValue = control.value;
        var campo = control.closest('.campo');
        if (campo) campo.classList.remove('campo--valido');
      });
      actualizarMarcas();
      document.querySelector('.indice-contenido__nota').textContent = 'Última publicación: hoy por Patricia León';
      window.CoreTech.mostrarNotificacion(cantidad + (cantidad === 1 ? ' texto publicado' : ' textos publicados') + ' en la página principal.');
    }, 800);
  });

  window.addEventListener('beforeunload', function (evento) {
    if (controles.some(cambiado)) { evento.preventDefault(); evento.returnValue = ''; }
  });

  /* ---------- Índice: sección que se está viendo ---------- */
  var enlaces = document.querySelectorAll('[data-enlace-seccion]');
  function marcarActual() {
    var linea = window.innerHeight * 0.3;
    var actual = null;
    document.querySelectorAll('[data-seccion]').forEach(function (seccion) {
      if (seccion.getBoundingClientRect().top <= linea) actual = seccion.getAttribute('data-seccion');
    });
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) {
      var todas = document.querySelectorAll('[data-seccion]');
      actual = todas[todas.length - 1].getAttribute('data-seccion');
    }
    enlaces.forEach(function (enlace) {
      if (enlace.getAttribute('data-enlace-seccion') === (actual || 'portada')) enlace.setAttribute('aria-current', 'true');
      else enlace.removeAttribute('aria-current');
    });
  }
  window.addEventListener('scroll', marcarActual, { passive: true });
  marcarActual();
  actualizarMarcas();
})();
