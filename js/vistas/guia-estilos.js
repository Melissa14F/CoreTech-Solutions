/* ==========================================================================
   Vista: Guía de estilos (guia-estilos.html)
   - Botones de ejemplo que muestran los mensajes flotantes.
   - El formulario de muestra no envía nada: solo enseña la validación.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  document.querySelectorAll('[data-mostrar-mensaje]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      var tipo = boton.getAttribute('data-mostrar-mensaje');
      window.CoreTech.mostrarNotificacion(tipo === 'error' ? 'No pudimos guardar los cambios. Intenta de nuevo.' : 'Cambios guardados correctamente.', tipo);
    });
  });

  var formulario = document.querySelector('.muestra--formulario');
  formulario.addEventListener('formulario-valido', function () {
    window.CoreTech.mostrarNotificacion('El formulario de ejemplo es válido. En la guía no se envía.');
  });

  // Los botones de muestra no hacen nada al presionarlos
  document.querySelectorAll('#botones button, #botones a[aria-disabled]').forEach(function (boton) {
    boton.addEventListener('click', function (evento) { evento.preventDefault(); });
  });
})();
