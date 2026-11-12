/* ==========================================================================
   CoreTech Solutions · Panel de accesibilidad
   - Se inserta solo en todas las pantallas (pestaña en el borde derecho).
   - Aplica los ajustes con clases .modo-* sobre <html>.
   - Recuerda la configuración entre páginas y visitas (localStorage).
   Se carga en el <head> para aplicar los ajustes antes de pintar la página.
   ========================================================================== */
(function () {
  'use strict';

  var CLAVE_ALMACENAMIENTO = 'coretech-accesibilidad';
  var raiz = document.documentElement;
  var CONFIGURACION_INICIAL = {
    leerEnVoz: false, tamanoTexto: 0, altoContraste: false, saturacion: 0, pausarAnimaciones: false,
    fuenteDislexia: false, interlineado: 0, interletrado: 0, daltonismo: 'ninguno',
    resaltarTitulos: false, resaltarEnlaces: false, textoNegrita: false, guiaLectura: false
  };

  function leerConfiguracion() {
    try { return Object.assign({}, CONFIGURACION_INICIAL, JSON.parse(localStorage.getItem(CLAVE_ALMACENAMIENTO) || '{}')); }
    catch (e) { return Object.assign({}, CONFIGURACION_INICIAL); }
  }
  function guardarConfiguracion() {
    try { localStorage.setItem(CLAVE_ALMACENAMIENTO, JSON.stringify(configuracion)); } catch (e) { /* sin almacenamiento */ }
  }
  var configuracion = leerConfiguracion();

  /* ---------- Filtros de color para daltonismo (matrices SVG) ---------- */
  var MATRICES_DALTONISMO = {
    protanopia:   '0.567 0.433 0 0 0  0.558 0.442 0 0 0  0 0.242 0.758 0 0  0 0 0 1 0',
    deuteranopia: '0.625 0.375 0 0 0  0.7 0.3 0 0 0  0 0.3 0.7 0 0  0 0 0 1 0',
    tritanopia:   '0.95 0.05 0 0 0  0 0.433 0.567 0 0  0 0.475 0.525 0 0  0 0 0 1 0'
  };
  function insertarFiltrosDeColor() {
    if (document.getElementById('filtros-daltonismo') || !document.body) return;
    var ns = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(ns, 'svg');
    svg.id = 'filtros-daltonismo';
    svg.setAttribute('aria-hidden', 'true');
    svg.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden';
    Object.keys(MATRICES_DALTONISMO).forEach(function (tipo) {
      var filtro = document.createElementNS(ns, 'filter');
      filtro.id = 'filtro-' + tipo;
      var matriz = document.createElementNS(ns, 'feColorMatrix');
      matriz.setAttribute('type', 'matrix');
      matriz.setAttribute('values', MATRICES_DALTONISMO[tipo]);
      filtro.appendChild(matriz);
      svg.appendChild(filtro);
    });
    document.body.appendChild(svg);
  }

  /* ---------- Aplicar la configuración a la página ---------- */
  function aplicarConfiguracion() {
    var clases = raiz.classList;
    ['modo-texto-1', 'modo-texto-2', 'modo-texto-3',
     'modo-interlineado-1', 'modo-interlineado-2', 'modo-interlineado-3',
     'modo-interletrado-1', 'modo-interletrado-2', 'modo-interletrado-3'].forEach(function (clase) { clases.remove(clase); });

    if (configuracion.tamanoTexto) clases.add('modo-texto-' + configuracion.tamanoTexto);
    if (configuracion.interlineado) clases.add('modo-interlineado-' + configuracion.interlineado);
    if (configuracion.interletrado) clases.add('modo-interletrado-' + configuracion.interletrado);
    clases.toggle('modo-contraste', configuracion.altoContraste);
    clases.toggle('modo-pausa', configuracion.pausarAnimaciones);
    clases.toggle('modo-dislexia', configuracion.fuenteDislexia);
    clases.toggle('modo-titulos', configuracion.resaltarTitulos);
    clases.toggle('modo-enlaces', configuracion.resaltarEnlaces);
    clases.toggle('modo-negrita', configuracion.textoNegrita);
    clases.toggle('modo-guia', configuracion.guiaLectura);
    clases.toggle('modo-voz', configuracion.leerEnVoz);

    var filtros = [];
    if (configuracion.saturacion === 1) filtros.push('saturate(.45)');
    if (configuracion.saturacion === 2) filtros.push('saturate(1.8)');
    if (configuracion.saturacion === 3) filtros.push('grayscale(1)');
    if (configuracion.altoContraste) filtros.push('contrast(1.08)');
    if (configuracion.daltonismo !== 'ninguno') {
      insertarFiltrosDeColor();
      filtros.push('url(#filtro-' + configuracion.daltonismo + ')');
    }
    // El filtro se aplica al contenido de la página, no al panel de accesibilidad
    // (así las muestras de color del panel se ven tal cual)
    raiz.style.setProperty('--filtro-pagina', filtros.length ? filtros.join(' ') : 'none');

    if (configuracion.fuenteDislexia && !document.getElementById('fuente-opendyslexic')) {
      var hojaFuente = document.createElement('link');
      hojaFuente.id = 'fuente-opendyslexic';
      hojaFuente.rel = 'stylesheet';
      hojaFuente.href = 'https://fonts.cdnfonts.com/css/opendyslexic';
      document.head.appendChild(hojaFuente);
    }
    if (!configuracion.leerEnVoz && 'speechSynthesis' in window) window.speechSynthesis.cancel();
    document.dispatchEvent(new CustomEvent('coretech-accesibilidad'));
  }
  aplicarConfiguracion(); // antes de pintar, para evitar parpadeos

  /* ---------- Iconos del panel ---------- */
  var ICONOS = {
    persona: '<circle cx="12" cy="4.5" r="2"/><path d="M5 8.5c2.3.7 4.6 1 7 1s4.7-.3 7-1M12 9.5v4.5M12 14l-3.5 7M12 14l3.5 7"/>',
    voz: '<path d="M11 5 6 9H2v6h4l5 4V5Z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14"/>',
    texto: '<path d="M4 7V5h11v2M9.5 5v14M7 19h5M14 12v-1.5h7V12M17.5 10.5V19M16 19h3"/>',
    contraste: '<circle cx="12" cy="12" r="9"/><path d="M12 3a9 9 0 0 1 0 18Z" fill="currentColor"/>',
    saturacion: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1" fill="currentColor"/>',
    pausa: '<circle cx="12" cy="12" r="9"/><path d="M10 9v6M14 9v6"/>',
    dislexia: '<path d="M4 18 9.5 5h1L16 18M6 13.5h8.5"/><path d="M3 21c2-1 3 1 5 0s3 1 5 0 3 1 5 0 2 1 3 0"/>',
    interlineado: '<path d="M11 6h10M11 12h10M11 18h10M5 4v16M3 6l2-2 2 2M3 18l2 2 2-2"/>',
    interletrado: '<path d="M3 6h18M3 12h12M3 18h18"/><path d="M18 9l3 3-3 3"/>',
    ojo: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
    titulo: '<path d="M4 6v12M12 6v12M4 12h8M16 10l3-2v10"/>',
    enlace: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    negrita: '<path d="M7 5h6a3.5 3.5 0 0 1 0 7H7zM7 12h7a3.5 3.5 0 0 1 0 7H7z"/>',
    guia: '<rect x="3" y="9" width="18" height="6" rx="1"/><path d="M3 5h18M3 19h18"/>',
    cerrar: '<path d="M18 6 6 18M6 6l12 12"/>',
    restablecer: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>'
  };
  function icono(nombre) { return '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true">' + ICONOS[nombre] + '</svg>'; }

  /* ---------- Opciones del panel ---------- */
  var OPCIONES_MOSAICO = [
    { clave: 'leerEnVoz', titulo: 'Leer en voz alta', icono: 'voz' },
    { clave: 'tamanoTexto', titulo: 'Tamaño de texto', icono: 'texto', niveles: ['Normal', 'Grande', 'Más grande', 'Máximo'] },
    { clave: 'altoContraste', titulo: 'Alto contraste', icono: 'contraste' },
    { clave: 'saturacion', titulo: 'Saturación', icono: 'saturacion', niveles: ['Normal', 'Baja', 'Alta', 'Escala de grises'] },
    { clave: 'pausarAnimaciones', titulo: 'Pausar animaciones', icono: 'pausa' },
    { clave: 'fuenteDislexia', titulo: 'Fuente para dislexia', icono: 'dislexia' },
    { clave: 'interlineado', titulo: 'Interlineado', icono: 'interlineado', niveles: ['Normal', 'Amplio', 'Más amplio', 'Máximo'] },
    { clave: 'interletrado', titulo: 'Interletrado', icono: 'interletrado', niveles: ['Normal', 'Amplio', 'Más amplio', 'Máximo'] }
  ];
  var OPCIONES_DALTONISMO = [
    { valor: 'ninguno', nombre: 'Ninguno', detalle: 'Colores originales' },
    { valor: 'protanopia', nombre: 'Protanopia', detalle: 'Dificultad con el rojo' },
    { valor: 'deuteranopia', nombre: 'Deuteranopia', detalle: 'Dificultad con el verde' },
    { valor: 'tritanopia', nombre: 'Tritanopia', detalle: 'Dificultad con el azul' }
  ];
  var OPCIONES_INTERRUPTOR = [
    { clave: 'resaltarTitulos', titulo: 'Resaltar títulos', icono: 'titulo' },
    { clave: 'resaltarEnlaces', titulo: 'Resaltar enlaces', icono: 'enlace' },
    { clave: 'textoNegrita', titulo: 'Texto en negrita', icono: 'negrita' },
    { clave: 'guiaLectura', titulo: 'Guía de lectura', icono: 'guia', detalle: 'Una franja sigue al cursor' }
  ];

  function crearPanel() {
    var hayVoz = 'speechSynthesis' in window;

    // Pestaña fija en el borde derecho
    var botonAbrir = document.createElement('button');
    botonAbrir.type = 'button';
    botonAbrir.className = 'accesibilidad-boton';
    botonAbrir.setAttribute('aria-label', 'Abrir opciones de accesibilidad');
    botonAbrir.setAttribute('aria-haspopup', 'dialog');
    botonAbrir.setAttribute('aria-controls', 'panel-accesibilidad');
    botonAbrir.setAttribute('aria-expanded', 'false');
    botonAbrir.innerHTML = icono('persona') + '<span class="accesibilidad-boton__texto">Accesibilidad</span>';

    var fondo = document.createElement('div');
    fondo.className = 'accesibilidad-fondo';

    var htmlMosaicos = OPCIONES_MOSAICO.map(function (opcion) {
      var barras = '';
      if (opcion.niveles) {
        barras = '<span class="accesibilidad-niveles" aria-hidden="true">' +
          '<i class="accesibilidad-niveles__nivel"></i><i class="accesibilidad-niveles__nivel"></i><i class="accesibilidad-niveles__nivel"></i></span>';
      }
      return '<button type="button" class="accesibilidad-opcion" data-opcion="' + opcion.clave + '">' +
        icono(opcion.icono) + '<span>' + opcion.titulo + '</span>' + barras +
        '<span class="solo-lector" data-estado-opcion></span></button>';
    }).join('');

    var htmlInterruptores = OPCIONES_INTERRUPTOR.map(function (opcion) {
      return '<button type="button" class="accesibilidad-fila" role="switch" aria-checked="false" data-opcion="' + opcion.clave + '">' +
        icono(opcion.icono) + '<span>' + opcion.titulo +
        (opcion.detalle ? '<span class="accesibilidad-fila__detalle">' + opcion.detalle + '</span>' : '') +
        '</span><span class="interruptor" aria-hidden="true"></span></button>';
    }).join('');

    // Opciones de daltonismo como botones visibles (cada uno con su muestra de color)
    var htmlDaltonismo = OPCIONES_DALTONISMO.map(function (opcion) {
      return '<button type="button" class="accesibilidad-daltonismo__opcion" role="radio" aria-checked="false" data-daltonismo="' + opcion.valor + '">' +
        '<span class="accesibilidad-daltonismo__muestra" aria-hidden="true" style="' + (opcion.valor !== 'ninguno' ? 'filter: url(#filtro-' + opcion.valor + ')' : '') + '">' +
          '<i style="background:#e53935"></i><i style="background:#43a047"></i><i style="background:#1e88e5"></i><i style="background:#fdd835"></i>' +
        '</span>' +
        '<span class="accesibilidad-daltonismo__nombre">' + opcion.nombre + '</span>' +
        '<span class="accesibilidad-daltonismo__detalle">' + opcion.detalle + '</span></button>';
    }).join('');

    var panel = document.createElement('div');
    panel.className = 'accesibilidad-panel';
    panel.id = 'panel-accesibilidad';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    panel.setAttribute('aria-labelledby', 'titulo-accesibilidad');
    panel.innerHTML =
      '<div class="accesibilidad-panel__encabezado">' +
        '<span class="accesibilidad-panel__icono">' + icono('persona') + '</span>' +
        '<div><h2 class="accesibilidad-panel__titulo" id="titulo-accesibilidad">Accesibilidad</h2>' +
        '<p class="accesibilidad-panel__descripcion">Ajusta la página a tu manera de leer.</p></div>' +
        '<button type="button" class="boton-icono boton-icono--pequeno" data-cerrar-panel aria-label="Cerrar opciones de accesibilidad">' + icono('cerrar') + '</button>' +
      '</div>' +
      '<div class="accesibilidad-panel__cuerpo">' +
        '<div class="accesibilidad-panel__grilla">' + htmlMosaicos + '</div>' +
        '<p class="accesibilidad-panel__subtitulo" id="titulo-daltonismo">Daltonismo</p>' +
        '<div class="accesibilidad-daltonismo" role="radiogroup" aria-labelledby="titulo-daltonismo">' + htmlDaltonismo + '</div>' +
        '<p class="accesibilidad-panel__subtitulo">Opciones avanzadas</p>' + htmlInterruptores +
      '</div>' +
      '<div class="accesibilidad-panel__pie">' +
        '<button type="button" class="boton boton--oscuro boton--bloque" data-restablecer>' + icono('restablecer') + 'Restablecer configuración</button>' +
      '</div>';

    var guia = document.createElement('div');
    guia.className = 'guia-lectura';
    guia.setAttribute('aria-hidden', 'true');

    document.body.appendChild(botonAbrir);
    document.body.appendChild(fondo);
    document.body.appendChild(panel);
    document.body.appendChild(guia);

    insertarFiltrosDeColor(); // las muestras de color de cada opción usan los filtros

    function actualizarPanel() {
      OPCIONES_MOSAICO.forEach(function (opcion) {
        var boton = panel.querySelector('[data-opcion="' + opcion.clave + '"]');
        var valor = configuracion[opcion.clave];
        if (opcion.niveles) {
          boton.setAttribute('aria-pressed', String(valor > 0));
          boton.querySelectorAll('.accesibilidad-niveles__nivel').forEach(function (barra, indice) {
            barra.classList.toggle('accesibilidad-niveles__nivel--activo', indice < valor);
          });
          boton.querySelector('[data-estado-opcion]').textContent = ': ' + opcion.niveles[valor];
        } else {
          boton.setAttribute('aria-pressed', String(!!valor));
        }
        if (opcion.clave === 'leerEnVoz' && !hayVoz) {
          boton.disabled = true;
          boton.title = 'Tu navegador no permite leer en voz alta';
        }
      });
      OPCIONES_INTERRUPTOR.forEach(function (opcion) {
        panel.querySelector('[data-opcion="' + opcion.clave + '"]').setAttribute('aria-checked', String(configuracion[opcion.clave]));
      });
      panel.querySelectorAll('[data-daltonismo]').forEach(function (boton) {
        boton.setAttribute('aria-checked', String(boton.getAttribute('data-daltonismo') === configuracion.daltonismo));
      });
    }

    function cambiarOpcion(clave) {
      var opcion = OPCIONES_MOSAICO.filter(function (o) { return o.clave === clave; })[0];
      if (opcion && opcion.niveles) configuracion[clave] = (configuracion[clave] + 1) % opcion.niveles.length;
      else configuracion[clave] = !configuracion[clave];
      guardarConfiguracion();
      aplicarConfiguracion();
      actualizarPanel();
      if (clave === 'leerEnVoz' && configuracion.leerEnVoz) {
        leerEnVozAlta('Lectura en voz alta activada. Selecciona un texto o haz clic sobre un párrafo para escucharlo.');
      }
    }

    panel.addEventListener('click', function (evento) {
      var botonOpcion = evento.target.closest('[data-opcion]');
      if (botonOpcion && botonOpcion.tagName === 'BUTTON' && !botonOpcion.disabled) cambiarOpcion(botonOpcion.getAttribute('data-opcion'));
      if (evento.target.closest('[data-cerrar-panel]')) cerrarPanel();
      if (evento.target.closest('[data-restablecer]')) {
        configuracion = Object.assign({}, CONFIGURACION_INICIAL);
        guardarConfiguracion();
        aplicarConfiguracion();
        actualizarPanel();
        if (window.CoreTech) window.CoreTech.mostrarNotificacion('Configuración restablecida.');
      }
    });
    panel.addEventListener('click', function (evento) {
      var botonDaltonismo = evento.target.closest('[data-daltonismo]');
      if (!botonDaltonismo) return;
      configuracion.daltonismo = botonDaltonismo.getAttribute('data-daltonismo');
      guardarConfiguracion();
      aplicarConfiguracion();
      actualizarPanel();
    });

    /* Abrir y cerrar (con el foco controlado) */
    var elementoConFocoPrevio = null;
    function abrirPanel() {
      elementoConFocoPrevio = document.activeElement;
      raiz.classList.add('panel-accesibilidad-abierto');
      botonAbrir.setAttribute('aria-expanded', 'true');
      setTimeout(function () { panel.querySelector('[data-cerrar-panel]').focus(); }, 60);
    }
    function cerrarPanel() {
      raiz.classList.remove('panel-accesibilidad-abierto');
      botonAbrir.setAttribute('aria-expanded', 'false');
      (elementoConFocoPrevio || botonAbrir).focus();
    }
    botonAbrir.addEventListener('click', abrirPanel);
    fondo.addEventListener('click', cerrarPanel);
    document.addEventListener('keydown', function (evento) {
      if (!raiz.classList.contains('panel-accesibilidad-abierto')) return;
      if (evento.key === 'Escape') { cerrarPanel(); return; }
      if (evento.key === 'Tab') { // el foco no sale del panel mientras está abierto
        var enfocables = panel.querySelectorAll('button:not([disabled])');
        var primero = enfocables[0];
        var ultimo = enfocables[enfocables.length - 1];
        if (evento.shiftKey && document.activeElement === primero) { evento.preventDefault(); ultimo.focus(); }
        else if (!evento.shiftKey && document.activeElement === ultimo) { evento.preventDefault(); primero.focus(); }
      }
    });

    /* Guía de lectura: franja que sigue al cursor */
    document.addEventListener('mousemove', function (evento) {
      if (configuracion.guiaLectura) guia.style.top = (evento.clientY - 22) + 'px';
    }, { passive: true });

    /* Leer en voz alta: texto seleccionado o bloque al que se hace clic */
    function leerEnVozAlta(texto) {
      if (!hayVoz || !texto) return;
      window.speechSynthesis.cancel();
      var locucion = new SpeechSynthesisUtterance(texto.replace(/\s+/g, ' ').trim().slice(0, 600));
      locucion.lang = 'es-CO';
      var vocesEnEspanol = window.speechSynthesis.getVoices().filter(function (voz) { return /^es/i.test(voz.lang); });
      if (vocesEnEspanol.length) {
        locucion.voice = vocesEnEspanol.filter(function (voz) { return /CO|MX|US/.test(voz.lang); })[0] || vocesEnEspanol[0];
      }
      window.speechSynthesis.speak(locucion);
    }
    document.addEventListener('mouseup', function (evento) {
      if (!configuracion.leerEnVoz || panel.contains(evento.target)) return;
      var seleccion = String(window.getSelection() || '');
      if (seleccion.trim().length > 1) { leerEnVozAlta(seleccion); return; }
      var bloque = evento.target.closest('p, h1, h2, h3, h4, li, label, td, th, .boton, a');
      if (bloque) leerEnVozAlta(bloque.innerText);
    });

    actualizarPanel();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', crearPanel);
  else crearPanel();
})();
