/* ==========================================================================
   Vista: Roles y permisos (admin/roles-permisos.html)
   - Cuenta los permisos de cada rol y los actualiza al mover un interruptor.
   - Muestra una barra con los cambios sin guardar: descartar o guardar.
   - Avisa antes de salir de la página si quedan cambios sin guardar.
   Los permisos con candado los fija una regla del negocio (no son interruptores).
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var matriz = document.querySelector('[data-matriz]');
  var interruptores = Array.prototype.slice.call(matriz.querySelectorAll('input[role="switch"]'));
  var barra = document.querySelector('[data-barra-cambios]');
  var textoCambios = document.querySelector('[data-cantidad-cambios]');

  function cambiados() {
    return interruptores.filter(function (casilla) { return String(casilla.checked) !== casilla.getAttribute('data-inicial'); });
  }

  // Permisos activos por rol: interruptores encendidos + permisos fijos permitidos
  function contarPermisos() {
    ['cliente', 'desarrollador', 'administrador'].forEach(function (rol, indice) {
      var columna = indice + 2;   // la columna 1 es el nombre del permiso
      var total = 0;
      matriz.querySelectorAll('.matriz__fila').forEach(function (fila) {
        var celda = fila.children[columna - 1];
        var casilla = celda.querySelector('input');
        if (casilla ? casilla.checked : celda.querySelector('.permiso-fijo--si')) total += 1;
      });
      var contador = document.querySelector('[data-resumen-rol="' + rol + '"] [data-cantidad-permisos]');
      if (contador.textContent !== String(total)) {
        contador.textContent = total;
        contador.classList.remove('esta-actualizado');
        void contador.offsetWidth;
        contador.classList.add('esta-actualizado');
      }
    });
  }

  function actualizar() {
    var lista = cambiados();
    interruptores.forEach(function (casilla) {
      var fila = casilla.closest('tr');
      fila.classList.toggle('esta-cambiada', Array.prototype.some.call(fila.querySelectorAll('input[role="switch"]'), function (c) {
        return String(c.checked) !== c.getAttribute('data-inicial');
      }));
    });
    barra.hidden = lista.length === 0;
    textoCambios.textContent = lista.length + (lista.length === 1 ? ' cambio' : ' cambios');
    contarPermisos();
  }

  interruptores.forEach(function (casilla) { casilla.addEventListener('change', actualizar); });

  document.querySelector('[data-descartar]').addEventListener('click', function () {
    interruptores.forEach(function (casilla) { casilla.checked = casilla.getAttribute('data-inicial') === 'true'; });
    actualizar();
    window.CoreTech.mostrarNotificacion('Se descartaron los cambios.');
  });

  document.querySelector('[data-guardar]').addEventListener('click', function () {
    var boton = this;
    var cantidad = cambiados().length;
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      interruptores.forEach(function (casilla) { casilla.setAttribute('data-inicial', String(casilla.checked)); });
      actualizar();
      window.CoreTech.mostrarNotificacion(cantidad + (cantidad === 1 ? ' permiso actualizado' : ' permisos actualizados') + '. Aplican desde el próximo inicio de sesión.');
    }, 700);
  });

  // Evita perder cambios al salir de la página
  window.addEventListener('beforeunload', function (evento) {
    if (cambiados().length) { evento.preventDefault(); evento.returnValue = ''; }
  });
  matriz.addEventListener('submit', function (evento) { evento.preventDefault(); });

  actualizar();
})();
