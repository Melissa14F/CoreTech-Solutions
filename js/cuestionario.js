/* ==========================================================================
   Ver el cuestionario de una cita (solo lectura)
   El Product Owner lo llena al cerrar la cita; el equipo y la administración
   lo consultan. Cualquier botón [data-ver-cuestionario] dentro de un elemento
   con data-cuestionario (JSON) abre #dialogo-ver-cuestionario.
   Para el encabezado usa data-cliente, data-cita o data-codigo,
   data-servicio y data-modalidad del mismo elemento.
   Requiere: el diálogo #dialogo-ver-cuestionario en la página
   ========================================================================== */
(function () {
  'use strict';

  var dialogo = document.getElementById('dialogo-ver-cuestionario');
  if (!dialogo) return;

  var ETIQUETAS = [['resumen', 'Resumen de la reunión'], ['cliente', 'Descripción del cliente'], ['presupuesto', 'Presupuesto aproximado'],
    ['fecha', 'Fecha deseada de entrega'], ['idioma', 'Idioma preferido'], ['urgencia', 'Nivel de urgencia']];

  function texto(elemento, valor) { var e = document.createElement(elemento); e.textContent = valor; return e; }

  window.CoreTech = window.CoreTech || {};
  window.CoreTech.verCuestionario = function (origen) {
    var r = JSON.parse(origen.getAttribute('data-cuestionario') || '{}');
    var caja = dialogo.querySelector('[data-resumen]');
    caja.innerHTML = '';
    caja.appendChild(texto('strong', origen.getAttribute('data-cliente') + ' · ' + origen.getAttribute('data-servicio')));
    caja.appendChild(texto('span', (origen.getAttribute('data-cita') || origen.getAttribute('data-codigo')) + ' · ' + origen.getAttribute('data-modalidad')));
    var lista = dialogo.querySelector('[data-respuestas]');
    lista.innerHTML = '';
    ETIQUETAS.forEach(function (e) {
      var fila = document.createElement('div');
      fila.className = 'respuestas__fila' + (e[0] === 'resumen' || e[0] === 'cliente' ? ' respuestas__fila--ancha' : '');
      fila.appendChild(texto('dt', e[1]));
      var valor = r[e[0]] || (e[0] === 'fecha' ? 'Sin fecha definida' : '—');
      var dd = texto('dd', valor);
      if (e[0] === 'urgencia') { dd.textContent = ''; dd.appendChild(texto('span', valor)); dd.firstChild.className = 'insignia insignia--urgencia-' + valor.toLowerCase(); }
      fila.appendChild(dd);
      lista.appendChild(fila);
    });
    dialogo.showModal();
  };

  document.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-ver-cuestionario]');
    if (boton) window.CoreTech.verCuestionario(boton.closest('[data-cuestionario]'));
    var cerrar = evento.target.closest('#dialogo-ver-cuestionario [data-cerrar-dialogo]');
    if (cerrar) dialogo.close();
  });
})();
