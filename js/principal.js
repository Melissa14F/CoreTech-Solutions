/* ==========================================================================
   CoreTech Solutions · Interacciones generales del prototipo
   - Encabezado y menú móvil
   - Animaciones de aparición al desplazarse
   - Notificaciones flotantes
   - Validación de formularios
   - Conservación del servicio elegido entre pantallas

   Principio: toda la navegación usa enlaces <a href> reales. Este archivo
   solo agrega efectos encima; si JavaScript está desactivado, el prototipo
   se puede recorrer completo igual.
   ========================================================================== */
(function () {
  'use strict';

  var raiz = document.documentElement;
  raiz.classList.remove('sin-js');

  /* ---------- Datos compartidos ---------- */
  var SERVICIOS = {
    'tienda-en-linea':       { nombre: 'Desarrollo de tienda en línea', imagen: 'tienda-en-linea.webp' },
    'web-corporativa':       { nombre: 'Página web corporativa',        imagen: 'web-corporativa.webp' },
    'app-movil':             { nombre: 'Aplicación móvil',              imagen: 'app-movil.webp' },
    'sistema-inventario':    { nombre: 'Sistema de inventario',         imagen: 'sistema-inventario.webp' },
    'integracion-pagos':     { nombre: 'Integración de pagos',          imagen: 'integracion-pagos.webp' },
    'mantenimiento-soporte': { nombre: 'Mantenimiento y soporte',       imagen: 'mantenimiento-soporte.webp' }
  };

  var parametros = new URLSearchParams(location.search);
  var servicioElegido = SERVICIOS[parametros.get('servicio')] ? parametros.get('servicio') : null;

  function movimientoReducido() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches || raiz.classList.contains('modo-pausa');
  }

  var memoriaSesion = {
    leer: function (clave) { try { return sessionStorage.getItem(clave); } catch (e) { return null; } },
    guardar: function (clave, valor) { try { sessionStorage.setItem(clave, valor); } catch (e) { /* sin almacenamiento */ } },
    borrar: function (clave) { try { sessionStorage.removeItem(clave); } catch (e) { /* sin almacenamiento */ } }
  };

  /* ---------- Notificaciones flotantes ---------- */
  var ICONOS_NOTIFICACION = {
    exito: '<path d="M20 6 9 17l-5-5"/>',
    error: '<circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>',
    info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>'
  };

  function mostrarNotificacion(mensaje, tipo) {
    tipo = tipo || 'exito';
    var zona = document.querySelector('.notificaciones');
    if (!zona) {
      zona = document.createElement('div');
      zona.className = 'notificaciones';
      zona.setAttribute('role', 'status');
      zona.setAttribute('aria-live', 'polite');
      document.body.appendChild(zona);
    }
    var notificacion = document.createElement('div');
    notificacion.className = 'notificacion notificacion--' + tipo;
    notificacion.innerHTML =
      '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true">' + ICONOS_NOTIFICACION[tipo] + '</svg>' +
      '<span class="notificacion__texto"></span>' +
      '<button type="button" class="notificacion__cerrar" aria-label="Cerrar mensaje">' +
      '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg></button>';
    notificacion.querySelector('.notificacion__texto').textContent = mensaje;

    function cerrar() {
      notificacion.classList.add('esta-saliendo');
      setTimeout(function () { notificacion.remove(); }, 260);
    }
    notificacion.querySelector('.notificacion__cerrar').addEventListener('click', cerrar);
    zona.appendChild(notificacion);
    setTimeout(cerrar, 5000);
  }

  // Mensaje que deja una pantalla para mostrarse en la siguiente
  function dejarMensaje(mensaje, tipo) {
    memoriaSesion.guardar('coretech-mensaje', JSON.stringify({ mensaje: mensaje, tipo: tipo || 'exito' }));
  }
  var mensajePendiente = memoriaSesion.leer('coretech-mensaje');
  if (mensajePendiente) {
    memoriaSesion.borrar('coretech-mensaje');
    try {
      var datos = JSON.parse(mensajePendiente);
      setTimeout(function () { mostrarNotificacion(datos.mensaje, datos.tipo); }, 350);
    } catch (e) { /* mensaje dañado: se ignora */ }
  }

  /* ---------- Navegación con estado de carga ---------- */
  function navegarConCarga(url, boton) {
    if (boton) boton.classList.add('esta-cargando');
    setTimeout(function () { location.href = url; }, movimientoReducido() ? 120 : 800);
  }

  function agregarServicio(url) {
    if (!servicioElegido) return url;
    var destino = new URL(url, location.href);
    destino.searchParams.set('servicio', servicioElegido);
    return url.split('?')[0].split('#')[0] + destino.search + destino.hash;
  }

  // Si el usuario viene de un servicio, algunos enlaces cambian de destino
  // (ej. al iniciar sesión va directo a agendar en vez de a "Mis proyectos")
  if (servicioElegido) {
    document.querySelectorAll('[data-destino-con-servicio]').forEach(function (enlace) {
      enlace.setAttribute('href', enlace.getAttribute('data-destino-con-servicio'));
    });
  }

  // Enlaces que deben conservar el servicio elegido (registro → agendar, etc.)
  function actualizarEnlacesConServicio() {
    document.querySelectorAll('[data-conservar-servicio]').forEach(function (enlace) {
      if (!enlace.hasAttribute('data-ruta-base')) enlace.setAttribute('data-ruta-base', enlace.getAttribute('href').split('?')[0]);
      enlace.setAttribute('href', agregarServicio(enlace.getAttribute('data-ruta-base')));
    });
  }
  actualizarEnlacesConServicio();

  // Paso 1 de agendar: el servicio marcado se refleja en el resumen y en el enlace "Continuar"
  var opcionesDeServicio = document.querySelectorAll('[data-elegir-servicio]');
  function mostrarServicioEnResumen() {
    document.querySelectorAll('[data-resumen="servicio"]').forEach(function (salida) {
      var servicio = SERVICIOS[servicioElegido];
      salida.textContent = servicio ? servicio.nombre : 'Sin elegir';
      salida.classList.toggle('resumen-cita__valor--pendiente', !servicio);
    });
  }
  opcionesDeServicio.forEach(function (opcion) {
    if (opcion.value === servicioElegido) opcion.checked = true;
    opcion.addEventListener('change', function () {
      servicioElegido = opcion.value;
      actualizarEnlacesConServicio();
      mostrarServicioEnResumen();
    });
  });
  if (opcionesDeServicio.length || document.querySelector('[data-resumen="servicio"]')) mostrarServicioEnResumen();

  // Pasos de un formulario: el enlace "Continuar" lleva en la dirección los datos
  // elegidos (ej. agendar-confirmar.html?servicio=…&fecha=…&hora=…&modalidad=…)
  function actualizarEnlacesDePaso() {
    document.querySelectorAll('[data-pasar-campos]').forEach(function (enlace) {
      var formulario = enlace.closest('form');
      var base = enlace.getAttribute('data-ruta-base') || enlace.getAttribute('href').split('?')[0];
      var datos = new URLSearchParams();
      if (servicioElegido) datos.set('servicio', servicioElegido);
      enlace.getAttribute('data-pasar-campos').split(',').forEach(function (nombre) {
        var control = formulario.querySelector('[name="' + nombre + '"]:checked') ||
                      formulario.querySelector('[name="' + nombre + '"]:not([type="radio"]):not([type="checkbox"])');
        if (control && control.value) datos.set(nombre, control.value);
      });
      var consulta = datos.toString();
      enlace.setAttribute('href', base + (consulta ? '?' + consulta : ''));
    });
  }
  // Enlaces que conservan todos los datos de la dirección actual (paso 3 → cita registrada)
  document.querySelectorAll('[data-conservar-consulta]').forEach(function (enlace) {
    enlace.setAttribute('href', enlace.getAttribute('href').split('?')[0] + location.search);
  });

  document.querySelectorAll('[data-pasar-campos]').forEach(function (enlace) {
    var formulario = enlace.closest('form');
    if (formulario) formulario.addEventListener('change', actualizarEnlacesDePaso);
  });
  actualizarEnlacesDePaso();

  // Tarjeta "Vas a agendar: …"
  document.querySelectorAll('[data-servicio-elegido]').forEach(function (caja) {
    if (!servicioElegido) { caja.hidden = true; return; }
    var servicio = SERVICIOS[servicioElegido];
    caja.hidden = false;
    caja.querySelector('img').src = caja.getAttribute('data-servicio-elegido') + 'img/servicios/' + servicio.imagen;
    caja.querySelector('.servicio-elegido__nombre').textContent = servicio.nombre;
  });

  /* ---------- Encabezado y menú móvil ---------- */
  var encabezado = document.querySelector('.encabezado');
  if (encabezado) {
    var marcarDesplazamiento = function () { encabezado.classList.toggle('esta-desplazado', window.scrollY > 8); };
    marcarDesplazamiento();
    window.addEventListener('scroll', marcarDesplazamiento, { passive: true });
  }

  var botonMenu = document.querySelector('.boton-menu');
  var menuMovil = document.getElementById('menu-movil');
  if (botonMenu && menuMovil) {
    var abrirMenu = function (abrir) {
      botonMenu.setAttribute('aria-expanded', String(abrir));
      botonMenu.setAttribute('aria-label', abrir ? 'Cerrar menú' : 'Abrir menú');
      menuMovil.classList.toggle('esta-abierto', abrir);
    };
    botonMenu.addEventListener('click', function () { abrirMenu(botonMenu.getAttribute('aria-expanded') !== 'true'); });
    menuMovil.addEventListener('click', function (evento) { if (evento.target.closest('a')) abrirMenu(false); });
    document.addEventListener('keydown', function (evento) { if (evento.key === 'Escape') abrirMenu(false); });
  }

  // Menú lateral del panel (pantallas internas) en celular
  var botonMenuPanel = document.querySelector('.boton-menu-panel');
  if (botonMenuPanel) {
    var velo = document.createElement('div');
    velo.className = 'panel__velo';
    document.body.appendChild(velo);
    var abrirMenuPanel = function (abrir) {
      raiz.classList.toggle('panel-menu-abierto', abrir);
      botonMenuPanel.setAttribute('aria-expanded', String(abrir));
      if (abrir) { var primerEnlace = document.querySelector('.panel__lateral a'); if (primerEnlace) primerEnlace.focus(); }
    };
    botonMenuPanel.addEventListener('click', function () { abrirMenuPanel(!raiz.classList.contains('panel-menu-abierto')); });
    velo.addEventListener('click', function () { abrirMenuPanel(false); });
    document.addEventListener('keydown', function (evento) {
      if (evento.key === 'Escape' && raiz.classList.contains('panel-menu-abierto')) { abrirMenuPanel(false); botonMenuPanel.focus(); }
    });
  }

  // Marca en el menú la sección que realmente se está viendo.
  // Regla: la sección activa es la última cuyo inicio ya pasó la línea de
  // lectura (35 % de la pantalla). En la portada no se marca ninguna y al
  // llegar al final de la página se marca la última (Contacto).
  var enlacesDeSeccion = Array.prototype.slice.call(document.querySelectorAll('.menu-principal__enlace[href^="#"]'));
  var seccionesDelMenu = enlacesDeSeccion
    .map(function (enlace) { return document.querySelector(enlace.getAttribute('href')); })
    .filter(Boolean);

  function marcarSeccionActual() {
    var lineaDeLectura = window.innerHeight * 0.35;
    var activa = null;
    seccionesDelMenu.forEach(function (seccion) {
      if (seccion.getBoundingClientRect().top <= lineaDeLectura) activa = seccion;
    });
    var llegoAlFinal = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4;
    if (llegoAlFinal && seccionesDelMenu.length) activa = seccionesDelMenu[seccionesDelMenu.length - 1];
    enlacesDeSeccion.forEach(function (enlace) {
      var esActiva = !!activa && enlace.getAttribute('href') === '#' + activa.id;
      if (esActiva) enlace.setAttribute('aria-current', 'location');
      else enlace.removeAttribute('aria-current');
    });
  }
  if (seccionesDelMenu.length) {
    var esperandoCuadro = false;
    window.addEventListener('scroll', function () {
      if (esperandoCuadro) return;
      esperandoCuadro = true;
      requestAnimationFrame(function () { marcarSeccionActual(); esperandoCuadro = false; });
    }, { passive: true });
    window.addEventListener('resize', marcarSeccionActual);
    marcarSeccionActual();
  }

  /* ---------- Aparición al desplazarse y barras de progreso ---------- */
  var animables = document.querySelectorAll('.aparecer, .paso, .barra-progreso[data-animar]');
  if ('IntersectionObserver' in window) {
    var observador = new IntersectionObserver(function (entradas) {
      entradas.forEach(function (entrada) {
        if (!entrada.isIntersecting) return;
        var esBarra = entrada.target.classList.contains('barra-progreso');
        entrada.target.classList.add(esBarra ? 'esta-lleno' : 'esta-visible');
        observador.unobserve(entrada.target);
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });
    animables.forEach(function (elemento) { observador.observe(elemento); });
  } else {
    animables.forEach(function (elemento) { elemento.classList.add('esta-visible', 'esta-lleno'); });
  }

  /* ---------- Mostrar u ocultar contraseña ---------- */
  document.querySelectorAll('[data-mostrar-clave]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      var campo = document.getElementById(boton.getAttribute('data-mostrar-clave'));
      var estaVisible = campo.type === 'text';
      campo.type = estaVisible ? 'password' : 'text';
      boton.setAttribute('aria-pressed', String(!estaVisible));
      boton.setAttribute('aria-label', estaVisible ? 'Mostrar contraseña' : 'Ocultar contraseña');
    });
  });

  /* ---------- Requisitos de contraseña en vivo ---------- */
  var REGLAS_CLAVE = {
    largo:     function (valor) { return valor.length >= 8; },
    mayuscula: function (valor) { return /[A-ZÁÉÍÓÚÑ]/.test(valor); },
    numero:    function (valor) { return /\d/.test(valor); },
    simbolo:   function (valor) { return /[^A-Za-z0-9ÁÉÍÓÚÑáéíóúñ]/.test(valor); }
  };
  function claveEsSegura(valor) {
    return Object.keys(REGLAS_CLAVE).every(function (regla) { return REGLAS_CLAVE[regla](valor); });
  }
  document.querySelectorAll('[data-requisitos-de]').forEach(function (lista) {
    var campo = document.getElementById(lista.getAttribute('data-requisitos-de'));
    campo.addEventListener('input', function () {
      lista.querySelectorAll('[data-regla]').forEach(function (item) {
        item.classList.toggle('requisitos__item--cumple', REGLAS_CLAVE[item.getAttribute('data-regla')](campo.value));
      });
    });
  });

  /* ---------- Contador de caracteres en textos largos ---------- */
  document.querySelectorAll('[data-contador-de]').forEach(function (contador) {
    var campo = document.getElementById(contador.getAttribute('data-contador-de'));
    var actualizar = function () { contador.textContent = campo.value.length + ' / ' + campo.maxLength; };
    campo.addEventListener('input', actualizar);
    actualizar();
  });

  /* ---------- Validación de formularios ---------- */
  /* Número o fecha fuera de los límites min / max del campo */
  function fueraDeRango(control, valor) {
    if (control.type === 'number') {
      return (control.min !== '' && Number(valor) < Number(control.min)) || (control.max !== '' && Number(valor) > Number(control.max));
    }
    if (control.type === 'date') {   // formato AAAA-MM-DD: se puede comparar como texto
      return (control.min !== '' && valor < control.min) || (control.max !== '' && valor > control.max);
    }
    return false;
  }

  function validarCampo(control) {
    var campo = control.closest('.campo');
    if (!campo) return true;
    var valor;
    if (control.type === 'checkbox') valor = control.checked;
    else if (control.type === 'radio') valor = !!document.querySelector('input[name="' + control.name + '"]:checked');
    else valor = control.value.trim();
    var error = '';

    if (control.required && !valor) {
      error = control.getAttribute('data-mensaje-vacio') || 'Completa este campo.';
    } else if (valor && control.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(valor)) {
      error = 'Escribe un correo válido, por ejemplo nombre@empresa.com.';
    } else if (valor && control.minLength > 0 && valor.length < control.minLength) {
      error = control.getAttribute('data-mensaje-corto') || ('Escribe al menos ' + control.minLength + ' caracteres.');
    } else if (valor && fueraDeRango(control, valor)) {
      error = control.getAttribute('data-mensaje-rango') || ('Escribe un valor entre ' + control.min + ' y ' + control.max + '.');
    } else if (valor && control.pattern && !new RegExp('^(?:' + control.pattern + ')$').test(valor)) {
      error = control.getAttribute('data-mensaje-formato') || 'Revisa el formato.';
    } else if (valor && control.hasAttribute('data-clave-segura') && !claveEsSegura(valor)) {
      error = 'La contraseña aún no cumple todos los requisitos.';
    } else if (valor && control.hasAttribute('data-igual-a')) {
      var original = document.getElementById(control.getAttribute('data-igual-a'));
      if (original && original.value !== control.value) error = 'Las contraseñas no coinciden.';
    }

    var salida = campo.querySelector('.campo__mensaje-texto');
    if (salida) salida.textContent = error;
    campo.classList.toggle('campo--invalido', !!error);
    campo.classList.toggle('campo--valido', !error && !!valor && control.type !== 'checkbox' && control.type !== 'radio');
    control.setAttribute('aria-invalid', String(!!error));
    return !error;
  }

  function validarFormulario(formulario) {
    var primerError = null;
    var camposConError = [];
    formulario.querySelectorAll('input, select, textarea').forEach(function (control) {
      // Si un campo ya tiene error (por ejemplo, un grupo de opciones), otro control
      // del mismo campo no debe borrarlo
      var campo = control.closest('.campo');
      if (campo && camposConError.indexOf(campo) !== -1) return;
      if (!validarCampo(control)) {
        if (campo) camposConError.push(campo);
        if (!primerError) primerError = control;
      }
    });
    if (primerError) {
      primerError.focus();
      mostrarNotificacion(formulario.getAttribute('data-mensaje-error') || 'Revisa los campos marcados en rojo.', 'error');
      return false;
    }
    return true;
  }

  document.querySelectorAll('form[data-validar]').forEach(function (formulario) {
    formulario.setAttribute('novalidate', '');
    var enlaceEnviar = formulario.querySelector('[data-enviar]');

    formulario.querySelectorAll('input, select, textarea').forEach(function (control) {
      control.addEventListener('blur', function () {
        var campo = control.closest('.campo');
        if (control.value || (campo && campo.classList.contains('campo--invalido'))) validarCampo(control);
      });
      control.addEventListener('input', function () {
        var campo = control.closest('.campo');
        if (campo && campo.classList.contains('campo--invalido')) validarCampo(control);
      });
      control.addEventListener('change', function () { if (control.type === 'checkbox' || control.type === 'radio') validarCampo(control); });
    });

    // El botón de envío es un enlace <a>: se valida antes de seguirlo.
    // Si el formulario no tiene enlace (ej. dentro de una ventana de diálogo),
    // al ser válido avisa con el evento "formulario-valido" para que la vista decida.
    function enviar(evento) {
      evento.preventDefault();
      if (!validarFormulario(formulario)) return;
      if (!enlaceEnviar) { formulario.dispatchEvent(new CustomEvent('formulario-valido')); return; }
      var mensajeExito = (enlaceEnviar && enlaceEnviar.getAttribute('data-mensaje-exito')) || formulario.getAttribute('data-mensaje-exito');
      if (mensajeExito) dejarMensaje(mensajeExito);
      navegarConCarga(enlaceEnviar.getAttribute('href'), enlaceEnviar);
    }
    if (enlaceEnviar) enlaceEnviar.addEventListener('click', enviar);
    formulario.addEventListener('submit', enviar); // tecla Enter dentro de un campo
  });

  /* ---------- Enlaces de acción con carga y mensaje ---------- */
  document.querySelectorAll('a[data-accion]').forEach(function (enlace) {
    enlace.addEventListener('click', function (evento) {
      evento.preventDefault();
      if (enlace.getAttribute('data-mensaje-exito')) dejarMensaje(enlace.getAttribute('data-mensaje-exito'));
      navegarConCarga(enlace.getAttribute('href'), enlace.classList.contains('boton') ? enlace : null);
    });
  });

  /* ---------- API pública para los demás scripts ---------- */
  window.CoreTech = {
    servicios: SERVICIOS,
    mostrarNotificacion: mostrarNotificacion,
    actualizarEnlacesDePaso: actualizarEnlacesDePaso,
    parametros: parametros,
    dejarMensaje: dejarMensaje,
    navegarConCarga: navegarConCarga
  };
})();
