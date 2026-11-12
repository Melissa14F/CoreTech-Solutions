/* ==========================================================================
   Tablas de gestión del administrador: filtros por estado, búsqueda y
   filtro por un campo (por ejemplo, desarrollador). Se usa en varias vistas.

   Marcado esperado dentro de [data-tabla]:
     <button data-filtro-estado="pendiente"> ... <span data-cantidad-estado="pendiente">
     <input data-buscar>                 búsqueda libre en el texto de la fila
     <select data-filtro-por="desarrollador">  compara con data-desarrollador de la fila
     <select data-filtro-por="periodo" data-filtro-modo="lista">  busca el valor dentro de una lista
     <tr data-fila data-estado="pendiente sin-asignar" data-desarrollador="Andrés Pérez">
     <tr data-sin-resultados>            fila que aparece cuando no hay coincidencias
     <span data-total-visible>           cantidad de filas visibles

   La URL puede traer ?estado=sin-asignar o ?buscar=C-0060 (enlaces del dashboard).
   Las vistas llaman a CoreTech.tabla.actualizar() después de cambiar una fila.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var tabla = document.querySelector('[data-tabla]');
  if (!tabla) return;

  var botones = tabla.querySelectorAll('[data-filtro-estado]');
  var buscador = tabla.querySelector('[data-buscar]');
  var selectores = tabla.querySelectorAll('[data-filtro-por]');
  var vacio = tabla.querySelector('[data-sin-resultados]');
  var totalVisible = tabla.querySelector('[data-total-visible]');
  var estadoActual = 'todas';

  function filas() { return Array.prototype.slice.call(tabla.querySelectorAll('[data-fila]')); }
  function normalizar(texto) { return texto.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''); }
  function tieneEstado(fila, estado) { return estado === 'todas' || (' ' + fila.getAttribute('data-estado') + ' ').indexOf(' ' + estado + ' ') !== -1; }

  function contar() {
    tabla.querySelectorAll('[data-cantidad-estado]').forEach(function (contador) {
      var estado = contador.getAttribute('data-cantidad-estado');
      contador.textContent = filas().filter(function (fila) { return tieneEstado(fila, estado); }).length;
    });
  }

  function aplicar() {
    var texto = buscador ? normalizar(buscador.value.trim()) : '';
    var visibles = 0;
    filas().forEach(function (fila) {
      var coincide = tieneEstado(fila, estadoActual) && (!texto || normalizar(fila.textContent).indexOf(texto) !== -1);
      selectores.forEach(function (selector) {
        var valor = selector.value;
        if (!valor) return;
        var dato = fila.getAttribute('data-' + selector.getAttribute('data-filtro-por')) || '';
        // data-filtro-modo="lista": el dato es una lista de palabras (por ejemplo, "hoy semana mes")
        var cumple = selector.getAttribute('data-filtro-modo') === 'lista' ? (' ' + dato + ' ').indexOf(' ' + valor + ' ') !== -1 : dato === valor;
        if (!cumple) coincide = false;
      });
      fila.hidden = !coincide;
      if (coincide) visibles += 1;
    });
    if (vacio) vacio.hidden = visibles > 0;
    if (totalVisible) totalVisible.textContent = visibles + (visibles === 1 ? ' resultado' : ' resultados');
  }

  function elegirEstado(estado) {
    estadoActual = estado;
    botones.forEach(function (boton) { boton.setAttribute('aria-pressed', String(boton.getAttribute('data-filtro-estado') === estado)); });
    aplicar();
  }

  botones.forEach(function (boton) {
    boton.addEventListener('click', function () { elegirEstado(boton.getAttribute('data-filtro-estado')); });
  });
  if (buscador) buscador.addEventListener('input', aplicar);
  selectores.forEach(function (selector) { selector.addEventListener('change', aplicar); });

  // Botón "Limpiar filtros" del estado vacío
  tabla.querySelectorAll('[data-limpiar-filtros]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      if (buscador) buscador.value = '';
      selectores.forEach(function (selector) { selector.value = ''; });
      elegirEstado('todas');
    });
  });

  // Menús "Más acciones": se cierra al elegir una opción, al hacer clic fuera o con Escape
  function cerrarMenus(excepto) {
    tabla.querySelectorAll('.menu-acciones[open]').forEach(function (menu) { if (menu !== excepto) menu.open = false; });
  }
  document.addEventListener('click', function (evento) {
    var menu = evento.target.closest('.menu-acciones');
    if (evento.target.closest('.menu-acciones__opcion')) { cerrarMenus(); return; }
    cerrarMenus(menu);
  });
  // El menú se posiciona fijo en la pantalla para que el contenedor con
  // desplazamiento horizontal de la tabla no lo recorte.
  function ubicarMenu(menu) {
    var lista = menu.querySelector('.menu-acciones__lista');
    var boton = menu.querySelector('summary').getBoundingClientRect();
    lista.style.position = 'fixed';
    lista.style.right = 'auto';
    var ancho = lista.offsetWidth, alto = lista.offsetHeight;
    var izquierda = Math.max(8, Math.min(boton.right - ancho, window.innerWidth - ancho - 8));
    var arriba = boton.bottom + 6 + alto > window.innerHeight ? boton.top - alto - 6 : boton.bottom + 6;
    lista.style.left = izquierda + 'px';
    lista.style.top = arriba + 'px';
  }
  // "toggle" no se propaga: se escucha en fase de captura para incluir filas agregadas después
  document.addEventListener('toggle', function (evento) {
    var menu = evento.target;
    if (menu.classList && menu.classList.contains('menu-acciones') && menu.open) ubicarMenu(menu);
  }, true);
  function reubicarAbiertos() { tabla.querySelectorAll('.menu-acciones[open]').forEach(ubicarMenu); }
  window.addEventListener('scroll', reubicarAbiertos, { passive: true });
  window.addEventListener('resize', reubicarAbiertos);
  tabla.querySelectorAll('.tabla-contenedor').forEach(function (contenedor) {
    contenedor.addEventListener('scroll', reubicarAbiertos, { passive: true });
  });

  document.addEventListener('keydown', function (evento) {
    if (evento.key !== 'Escape') return;
    var abierto = tabla.querySelector('.menu-acciones[open]');
    if (abierto) { abierto.open = false; abierto.querySelector('summary').focus(); }
  });

  // Filtros que llegan en la URL
  var parametros = new URLSearchParams(location.search);
  if (parametros.get('buscar') && buscador) buscador.value = parametros.get('buscar');
  var estadoInicial = parametros.get('estado');
  var existe = estadoInicial && tabla.querySelector('[data-filtro-estado="' + estadoInicial + '"]');

  contar();
  elegirEstado(existe ? estadoInicial : 'todas');

  window.CoreTech = window.CoreTech || {};
  window.CoreTech.tabla = { actualizar: function () { contar(); aplicar(); } };
})();
