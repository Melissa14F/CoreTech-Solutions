/* ==========================================================================
   Vista: Propuesta del plan de trabajo (cliente/plan-*.html)
   El cliente aprueba el plan y la cotización, o pide ajustes.
   Requiere: js/principal.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;

  document.querySelectorAll('[data-abrir-dialogo]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      var dialogo = document.getElementById(boton.getAttribute('data-abrir-dialogo'));
      var form = dialogo.querySelector('form');
      form.reset();
      form.querySelectorAll('.campo').forEach(function (c) { c.classList.remove('campo--invalido', 'campo--valido'); });
      dialogo.showModal();
    });
  });

  document.querySelector('#dialogo-aprobar form').addEventListener('formulario-valido', function () {
    document.getElementById('dialogo-aprobar').close();
    CoreTech.dejarMensaje('¡Plan aprobado! Andrés empieza la fase 1 y verás el avance en tu panel.');
    CoreTech.navegarConCarga('mis-proyectos.html');
  });

  document.querySelector('#dialogo-ajustes form').addEventListener('formulario-valido', function () {
    document.getElementById('dialogo-ajustes').close();
    CoreTech.dejarMensaje('Enviamos tus comentarios a Andrés. Te avisaremos cuando el plan esté ajustado.');
    CoreTech.navegarConCarga('mis-proyectos.html');
  });

  // RN-29: rechazo → el proyecto queda en pausa y el administrador decide si lo retoma o lo cancela
  document.querySelector('#dialogo-rechazar form').addEventListener('formulario-valido', function () {
    document.getElementById('dialogo-rechazar').close();
    CoreTech.dejarMensaje('Rechazaste la propuesta. El proyecto quedó en pausa; si no respondes en 30 días, se cancelará.');
    CoreTech.navegarConCarga('mis-proyectos.html');
  });

  document.addEventListener('click', function (evento) {
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });
})();
