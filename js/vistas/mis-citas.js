/* ==========================================================================
   Vista: Mis citas (cliente/mis-citas.html)
   - Filtra las citas (todas, próximas, atendidas y canceladas).
   - Cancela la cita pendiente con una ventana de confirmación (RN-12).
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var filtros = document.querySelectorAll('[data-filtro]');
  var grupos = document.querySelectorAll('[data-grupo]');
  var sinResultados = document.querySelector('[data-sin-resultados]');
  var dialogo = document.getElementById('dialogo-cancelar');
  var filtroActual = 'todas';

  /* ---------- Filtros ---------- */
  function contar(tipo) { return document.querySelectorAll('[data-cita-tipo="' + tipo + '"]').length; }

  function aplicarFiltro(filtro) {
    filtroActual = filtro;
    filtros.forEach(function (boton) { boton.setAttribute('aria-pressed', String(boton.getAttribute('data-filtro') === filtro)); });
    var hayVisibles = false;
    grupos.forEach(function (grupo) {
      var tipo = grupo.getAttribute('data-grupo');
      var visible = (filtro === 'todas' || filtro === tipo) && contar(tipo) > 0;
      grupo.hidden = !visible;
      if (visible) hayVisibles = true;
    });
    sinResultados.hidden = hayVisibles;
  }

  function actualizarCantidades() {
    ['proximas', 'pasadas', 'canceladas'].forEach(function (tipo) {
      document.querySelector('[data-cantidad="' + tipo + '"]').textContent = contar(tipo);
    });
    document.querySelector('[data-cantidad="todas"]').textContent = document.querySelectorAll('[data-cita-tipo]').length;
  }

  filtros.forEach(function (boton) {
    boton.addEventListener('click', function () { aplicarFiltro(boton.getAttribute('data-filtro')); });
  });

  /* ---------- Cancelar cita ---------- */
  var citaPorCancelar = null;
  document.querySelectorAll('[data-abrir-cancelar]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      citaPorCancelar = document.getElementById('cita-' + boton.getAttribute('data-abrir-cancelar'));
      dialogo.showModal();
    });
  });
  dialogo.querySelector('[data-cerrar-dialogo]').addEventListener('click', function () { dialogo.close(); });

  dialogo.querySelector('[data-confirmar-cancelar]').addEventListener('click', function () {
    var boton = this;
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      dialogo.close();
      if (!citaPorCancelar) return;

      // La cita pasa al grupo de canceladas
      var estado = citaPorCancelar.querySelector('[data-estado-cita]');
      estado.textContent = 'Cancelada';
      estado.className = 'insignia insignia--cancelada';
      citaPorCancelar.querySelector('[data-nota-cita]').textContent = 'Cancelaste esta cita. La franja quedó libre.';
      citaPorCancelar.querySelector('[data-acciones-cita]').innerHTML =
        '<a class="boton boton--contorno boton--pequeno" href="agendar-servicio.html">Agendar otra cita</a>';
      citaPorCancelar.classList.remove('tarjeta-cita--destacada');
      citaPorCancelar.classList.add('tarjeta-cita--cancelada');
      citaPorCancelar.setAttribute('data-cita-tipo', 'canceladas');
      document.querySelector('[data-lista-canceladas]').appendChild(citaPorCancelar);


      actualizarCantidades();
      aplicarFiltro(filtroActual);
      window.CoreTech.mostrarNotificacion('Cita C-0061 cancelada. La franja quedó libre.');
      citaPorCancelar = null;
    }, 700);
  });

  aplicarFiltro('todas');
})();
