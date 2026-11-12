/* ==========================================================================
   Galería de bocetos de pantallas (fase de Análisis y diseño)
   La usan el detalle del proyecto del cliente y el registro de trabajo del
   desarrollador. Cada miniatura es un enlace a la imagen: sin JavaScript se
   abre la imagen; con JavaScript se abre un visor con anterior / siguiente.
   ========================================================================== */
(function () {
  'use strict';

  var miniaturas = document.querySelectorAll('[data-boceto]');
  if (!miniaturas.length) return;

  var ICONO = function (d) { return '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true">' + d + '</svg>'; };
  var visor = document.createElement('dialog');
  visor.className = 'dialogo visor-boceto';
  visor.setAttribute('aria-labelledby', 'visor-boceto-titulo');
  visor.innerHTML =
    '<div class="visor-boceto__cabecera">' +
      '<div><h2 class="visor-boceto__titulo" id="visor-boceto-titulo"></h2><p class="visor-boceto__contador" aria-live="polite"></p></div>' +
      '<button type="button" class="boton-icono boton-icono--pequeno" data-cerrar aria-label="Cerrar">' + ICONO('<path d="M18 6 6 18M6 6l12 12"/>') + '</button>' +
    '</div>' +
    '<div class="visor-boceto__cuerpo">' +
      '<button type="button" class="boton-icono visor-boceto__flecha" data-anterior aria-label="Boceto anterior">' + ICONO('<path d="M15 6l-6 6 6 6"/>') + '</button>' +
      '<img class="visor-boceto__imagen" alt="">' +
      '<button type="button" class="boton-icono visor-boceto__flecha" data-siguiente aria-label="Boceto siguiente">' + ICONO('<path d="M9 6l6 6-6 6"/>') + '</button>' +
    '</div>' +
    '<p class="visor-boceto__nota">Es un boceto: muestra la organización de la pantalla, no los colores ni las fotos finales.</p>';
  document.body.appendChild(visor);

  var grupo = [], indice = 0;
  var imagen = visor.querySelector('img');

  function mostrar(i) {
    indice = (i + grupo.length) % grupo.length;
    var enlace = grupo[indice];
    imagen.src = enlace.getAttribute('href');
    imagen.alt = 'Boceto: ' + enlace.getAttribute('data-titulo');
    visor.querySelector('.visor-boceto__titulo').textContent = enlace.getAttribute('data-titulo');
    visor.querySelector('.visor-boceto__contador').textContent = grupo.length > 1 ? (indice + 1) + ' de ' + grupo.length : '';
    visor.querySelectorAll('[data-anterior], [data-siguiente]').forEach(function (b) { b.hidden = grupo.length < 2; });
  }

  miniaturas.forEach(function (enlace) {
    enlace.addEventListener('click', function (evento) {
      evento.preventDefault();
      grupo = Array.prototype.slice.call(enlace.closest('.bocetos').querySelectorAll('[data-boceto]'));
      mostrar(grupo.indexOf(enlace));
      visor.showModal();
    });
  });
  visor.querySelector('[data-cerrar]').addEventListener('click', function () { visor.close(); });
  visor.querySelector('[data-anterior]').addEventListener('click', function () { mostrar(indice - 1); });
  visor.querySelector('[data-siguiente]').addEventListener('click', function () { mostrar(indice + 1); });
  visor.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowLeft' && grupo.length > 1) mostrar(indice - 1);
    if (e.key === 'ArrowRight' && grupo.length > 1) mostrar(indice + 1);
  });
  // Clic fuera de la imagen (en el fondo) cierra el visor
  visor.addEventListener('click', function (e) { if (e.target === visor) visor.close(); });
})();
