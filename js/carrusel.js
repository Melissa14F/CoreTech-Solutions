/* ==========================================================================
   CoreTech Solutions · Carrusel accesible
   - Avanza solo cada 5 segundos.
   - Se detiene al pasar el cursor, al enfocarlo con el teclado o con el
     botón de pausa (requisito WCAG 2.2.2).
   - Se controla con flechas, puntos, teclado (← →) y deslizando el dedo.
   - No avanza solo si el usuario pidió reducir el movimiento.
   Estructura esperada: ver la sección "Servicios" de index.html.
   ========================================================================== */
(function () {
  'use strict';

  var INTERVALO_MS = 5000;
  var DISTANCIA_MINIMA_DESLIZAR = 40;

  function movimientoReducido() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
      document.documentElement.classList.contains('modo-pausa');
  }

  function iniciarCarrusel(carrusel) {
    var ventana = carrusel.querySelector('.carrusel__ventana');
    var pista = carrusel.querySelector('.carrusel__pista');
    var diapositivas = Array.prototype.slice.call(pista.children);
    var botonAnterior = carrusel.querySelector('[data-carrusel-anterior]');
    var botonSiguiente = carrusel.querySelector('[data-carrusel-siguiente]');
    var botonPausa = carrusel.querySelector('[data-carrusel-pausa]');
    var zonaPuntos = carrusel.querySelector('.carrusel__puntos');
    var textoEstado = carrusel.querySelector('.carrusel__estado');

    var posicion = 0;
    var temporizador = null;
    var pausadoPorUsuario = false;
    var cursorEncima = false;
    var focoDentro = false;

    function tarjetasPorVista() {
      var ancho = ventana.clientWidth;
      if (ancho < 600) return 1;
      if (ancho < 960) return 2;
      return 3;
    }
    function ultimaPosicion() { return Math.max(0, diapositivas.length - tarjetasPorVista()); }

    function dibujarPuntos() {
      zonaPuntos.innerHTML = '';
      for (var i = 0; i <= ultimaPosicion(); i++) {
        var punto = document.createElement('button');
        punto.type = 'button';
        punto.className = 'carrusel__punto';
        punto.setAttribute('aria-label', 'Ver grupo ' + (i + 1) + ' de ' + (ultimaPosicion() + 1));
        punto.addEventListener('click', irA.bind(null, i, true));
        zonaPuntos.appendChild(punto);
      }
    }

    function irA(nuevaPosicion, esManual) {
      var ultima = ultimaPosicion();
      if (nuevaPosicion > ultima) nuevaPosicion = 0;
      if (nuevaPosicion < 0) nuevaPosicion = ultima;
      posicion = nuevaPosicion;

      var separacion = parseFloat(getComputedStyle(pista).columnGap) || 0;
      var paso = diapositivas[0].getBoundingClientRect().width + separacion;
      pista.style.transform = 'translateX(' + (-posicion * paso) + 'px)';

      // Solo las tarjetas visibles se pueden enfocar y las lee el lector de pantalla
      var visibles = tarjetasPorVista();
      diapositivas.forEach(function (diapositiva, indice) {
        var esVisible = indice >= posicion && indice < posicion + visibles;
        diapositiva.setAttribute('aria-hidden', String(!esVisible));
        diapositiva.querySelectorAll('a, button').forEach(function (elemento) { elemento.tabIndex = esVisible ? 0 : -1; });
      });
      zonaPuntos.querySelectorAll('.carrusel__punto').forEach(function (punto, indice) {
        punto.setAttribute('aria-current', String(indice === posicion));
      });
      if (textoEstado) textoEstado.textContent = (posicion + 1) + ' / ' + (ultima + 1);
      if (esManual) reanudar();
    }

    function detener() {
      if (temporizador) { clearInterval(temporizador); temporizador = null; }
    }
    function reanudar() {
      detener();
      if (pausadoPorUsuario || cursorEncima || focoDentro || movimientoReducido()) return;
      temporizador = setInterval(function () { irA(posicion + 1); }, INTERVALO_MS);
    }

    function actualizarBotonPausa() {
      if (!botonPausa) return;
      var detenido = pausadoPorUsuario || movimientoReducido();
      botonPausa.setAttribute('aria-label', detenido ? 'Reanudar carrusel' : 'Pausar carrusel');
      botonPausa.setAttribute('aria-pressed', String(detenido));
      botonPausa.querySelector('[data-icono-pausa]').style.display = detenido ? 'none' : '';
      botonPausa.querySelector('[data-icono-reproducir]').style.display = detenido ? '' : 'none';
    }

    botonAnterior.addEventListener('click', function () { irA(posicion - 1, true); });
    botonSiguiente.addEventListener('click', function () { irA(posicion + 1, true); });
    if (botonPausa) {
      botonPausa.addEventListener('click', function () {
        if (document.documentElement.classList.contains('modo-pausa')) {
          if (window.CoreTech) window.CoreTech.mostrarNotificacion('Las animaciones están en pausa desde el panel de accesibilidad.', 'info');
          return;
        }
        pausadoPorUsuario = !pausadoPorUsuario;
        actualizarBotonPausa();
        reanudar();
      });
    }

    carrusel.addEventListener('mouseenter', function () { cursorEncima = true; detener(); });
    carrusel.addEventListener('mouseleave', function () { cursorEncima = false; reanudar(); });
    carrusel.addEventListener('focusin', function () { focoDentro = true; detener(); });
    carrusel.addEventListener('focusout', function (evento) {
      if (!carrusel.contains(evento.relatedTarget)) { focoDentro = false; reanudar(); }
    });
    carrusel.addEventListener('keydown', function (evento) {
      if (evento.key === 'ArrowRight') { evento.preventDefault(); irA(posicion + 1, true); }
      if (evento.key === 'ArrowLeft') { evento.preventDefault(); irA(posicion - 1, true); }
    });

    // Deslizar con el dedo
    var inicioToque = null;
    ventana.addEventListener('touchstart', function (evento) { inicioToque = evento.touches[0].clientX; detener(); }, { passive: true });
    ventana.addEventListener('touchend', function (evento) {
      if (inicioToque === null) return;
      var recorrido = evento.changedTouches[0].clientX - inicioToque;
      if (Math.abs(recorrido) > DISTANCIA_MINIMA_DESLIZAR) irA(posicion + (recorrido < 0 ? 1 : -1), true);
      else reanudar();
      inicioToque = null;
    });

    var esperaRedimension;
    window.addEventListener('resize', function () {
      clearTimeout(esperaRedimension);
      esperaRedimension = setTimeout(function () { dibujarPuntos(); irA(Math.min(posicion, ultimaPosicion())); }, 150);
    });
    document.addEventListener('coretech-accesibilidad', function () { actualizarBotonPausa(); reanudar(); });
    document.addEventListener('visibilitychange', function () { if (document.hidden) detener(); else reanudar(); });

    dibujarPuntos();
    irA(0);
    actualizarBotonPausa();
    reanudar();
  }

  document.querySelectorAll('[data-carrusel]').forEach(iniciarCarrusel);
})();
