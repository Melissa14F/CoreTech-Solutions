/* ==========================================================================
   Vista: Citas y reuniones del desarrollador (desarrollador/citas-reuniones.html)
   - Pestañas accesibles: "Citas asignadas" y "Solicitudes de reunión".
     El enlace #solicitudes abre directamente la segunda pestaña.
   - Confirmar cita (RN-12): si es virtual, el enlace de la videollamada es obligatorio.
   - Marcar como atendida una cita que ya pasó.
   - Programar o rechazar una solicitud de reunión del cliente (RN-14 y RN-15).
   - Al terminar cada acción la tarjeta cambia de grupo y se actualizan los contadores.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;

  /* ---------- Pestañas ---------- */
  var pestanas = Array.prototype.slice.call(document.querySelectorAll('[role="tab"]'));

  function activarPestana(pestana, mover) {
    pestanas.forEach(function (otra) {
      var activa = otra === pestana;
      otra.setAttribute('aria-selected', String(activa));
      otra.tabIndex = activa ? 0 : -1;
      document.getElementById(otra.getAttribute('aria-controls')).hidden = !activa;
    });
    if (mover) pestana.focus();
  }

  pestanas.forEach(function (pestana, indice) {
    pestana.addEventListener('click', function () {
      activarPestana(pestana);
      history.replaceState(null, '', '#' + pestana.getAttribute('aria-controls'));
    });
    pestana.addEventListener('keydown', function (evento) {
      var destino = null;
      if (evento.key === 'ArrowRight') destino = pestanas[(indice + 1) % pestanas.length];
      if (evento.key === 'ArrowLeft') destino = pestanas[(indice - 1 + pestanas.length) % pestanas.length];
      if (evento.key === 'Home') destino = pestanas[0];
      if (evento.key === 'End') destino = pestanas[pestanas.length - 1];
      if (destino) { evento.preventDefault(); activarPestana(destino, true); }
    });
  });

  var inicial = location.hash === '#solicitudes' ? pestanas[1] : pestanas[0];
  activarPestana(inicial);
  // El navegador baja hasta el ancla; con pestañas no hace falta, se vuelve arriba.
  if (location.hash === '#solicitudes') {
    var subir = function () { window.scrollTo(0, 0); };
    subir();
    window.addEventListener('load', function () { setTimeout(subir, 0); });
  }

  /* ---------- Contadores y grupos vacíos ---------- */
  function cantidad(lista) { return document.querySelectorAll('[data-lista="' + lista + '"] > li').length; }

  function ponerContador(elemento, valor, etiqueta) {
    if (!elemento) return;
    elemento.textContent = valor;
    elemento.setAttribute('data-valor', valor);
    if (etiqueta) elemento.setAttribute('aria-label', valor + ' ' + etiqueta);
  }

  function actualizarContadores() {
    var porConfirmar = cantidad('confirmar');
    var porResponder = cantidad('por-responder');

    ponerContador(document.querySelector('[data-contador-pestana="citas"]'), porConfirmar, 'por confirmar');
    ponerContador(document.querySelector('[data-contador-pestana="solicitudes"]'), porResponder, 'por responder');
    ponerContador(document.querySelector('[data-cantidad-grupo="confirmar"]'), porConfirmar);
    ponerContador(document.querySelector('[data-cantidad-grupo="por-responder"]'), porResponder);

    // Contador del menú lateral: todo lo que espera una respuesta del desarrollador
    var pendientes = porConfirmar + porResponder;
    var menu = document.querySelector('.navegacion-panel__enlace[aria-current="page"] .navegacion-panel__contador');
    if (menu) {
      menu.textContent = pendientes;
      menu.setAttribute('aria-label', pendientes + ' pendientes');
      menu.hidden = pendientes === 0;
    }

    document.querySelectorAll('[data-lista]').forEach(function (lista) {
      var vacio = document.querySelector('[data-vacio="' + lista.getAttribute('data-lista') + '"]');
      var hayTarjetas = lista.children.length > 0;
      lista.hidden = !hayTarjetas;
      if (vacio) vacio.hidden = hayTarjetas;
    });
  }

  /* ---------- Ventanas de diálogo ---------- */
  var tarjetaActual = null;

  function resumen(tarjeta) {
    var titulo = tarjeta.querySelector('.tarjeta-cita__titulo').textContent;
    var codigo = tarjeta.getAttribute('data-cita') || tarjeta.getAttribute('data-solicitud');
    var detalle = tarjeta.getAttribute('data-fecha') || tarjeta.getAttribute('data-proyecto') || '';
    return '<strong>' + tarjeta.getAttribute('data-cliente') + ' · ' + titulo + '</strong>' +
           '<span>' + codigo + ' · ' + detalle + ' · ' + tarjeta.getAttribute('data-modalidad') + '</span>';
  }

  function limpiarFormulario(formulario) {
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (campo) { campo.classList.remove('campo--invalido', 'campo--valido'); });
    formulario.querySelectorAll('[aria-invalid]').forEach(function (control) { control.removeAttribute('aria-invalid'); });
    formulario.querySelectorAll('textarea').forEach(function (area) { area.dispatchEvent(new Event('input')); });
  }

  function abrir(tipo, tarjeta) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    tarjetaActual = tarjeta;
    limpiarFormulario(formulario);

    var caja = dialogo.querySelector('[data-resumen]');
    if (caja) caja.innerHTML = resumen(tarjeta);
    var texto = dialogo.querySelector('[data-resumen-texto]');
    if (texto) texto.textContent = tarjeta.getAttribute('data-cita') + ' con ' + tarjeta.getAttribute('data-cliente') + ', ' + tarjeta.getAttribute('data-fecha') + '. Pasará al historial y quedará en los reportes de la administración.';

    // El enlace solo se pide cuando la reunión es virtual
    var campoEnlace = dialogo.querySelector('[data-campo-enlace]');
    if (campoEnlace) {
      var esVirtual = tarjeta.getAttribute('data-modalidad') === 'Virtual';
      campoEnlace.hidden = !esVirtual;
      campoEnlace.querySelector('input').required = esVirtual;
    }
    dialogo.showModal();
  }

  document.querySelectorAll('[data-abrir]').forEach(function (boton) {
    boton.addEventListener('click', function () { abrir(boton.getAttribute('data-abrir'), boton.closest('li')); });
  });
  document.querySelectorAll('[data-cerrar-dialogo]').forEach(function (boton) {
    boton.addEventListener('click', function () { boton.closest('dialog').close(); });
  });

  // Simula el guardado: botón con carga, cierra la ventana y aplica el cambio.
  function alGuardar(tipo, aplicar) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    formulario.addEventListener('formulario-valido', function () {
      var boton = formulario.querySelector('[type="submit"]');
      boton.classList.add('esta-cargando');
      setTimeout(function () {
        boton.classList.remove('esta-cargando');
        dialogo.close();
        if (tarjetaActual) aplicar(tarjetaActual, new FormData(formulario));
        tarjetaActual = null;
        actualizarContadores();
      }, 700);
    });
  }

  /* ---------- Ayudas para cambiar una tarjeta ---------- */
  function cambiarEstado(tarjeta, texto, clase) {
    var estado = tarjeta.querySelector('[data-estado]');
    estado.textContent = texto;
    estado.className = 'insignia insignia--' + clase;
  }

  function cambiarNota(tarjeta, texto) {
    var nota = tarjeta.querySelector('[data-nota]');
    nota.textContent = texto;
    nota.hidden = false;
  }

  function ponerEnlace(tarjeta, url) {
    var cuerpo = tarjeta.querySelector('[data-nota]').parentNode;
    var enlace = document.createElement('span');
    enlace.className = 'tarjeta-cita__enlace';
    enlace.textContent = url.replace(/^https?:\/\//, '');
    enlace.insertAdjacentHTML('afterbegin', '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>');
    cuerpo.appendChild(enlace);
  }

  function botonEscribir(tarjeta) {
    var nombre = tarjeta.getAttribute('data-cliente').split(' ')[0];
    return '<a class="boton boton--fantasma boton--pequeno" href="mailto:' + tarjeta.getAttribute('data-correo') + '">' +
      '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>Escribir a ' + nombre + '</a>';
  }

  function moverA(tarjeta, lista, alInicio) {
    var destino = document.querySelector('[data-lista="' + lista + '"]');
    if (alInicio) destino.insertBefore(tarjeta, destino.firstChild); else destino.appendChild(tarjeta);
    tarjeta.classList.remove('tarjeta-cita--aviso');
    tarjeta.classList.add('esta-resaltada');
    tarjeta.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  /* ---------- Confirmar cita ---------- */
  alGuardar('confirmar', function (tarjeta, datos) {
    var enlace = datos.get('enlace');
    cambiarEstado(tarjeta, 'Confirmada', 'confirmada');
    cambiarNota(tarjeta, enlace
      ? 'Confirmaste la cita y enviaste el enlace a ' + tarjeta.getAttribute('data-cliente') + '.'
      : 'Confirmaste la cita. ' + tarjeta.getAttribute('data-cliente') + ' te espera en la oficina de CoreTech.');
    if (enlace) ponerEnlace(tarjeta, enlace);
    tarjeta.querySelector('[data-acciones]').innerHTML = botonEscribir(tarjeta);
    moverA(tarjeta, 'confirmadas', true);
    CoreTech.mostrarNotificacion('Cita ' + tarjeta.getAttribute('data-cita') + ' confirmada. Avisamos al cliente por correo.');
  });

  /* ---------- Marcar como atendida ---------- */
  alGuardar('atendida', function (tarjeta, datos) {
    var resumenReunion = (datos.get('resumen') || '').trim();
    cambiarEstado(tarjeta, 'Atendida', 'atendida');
    cambiarNota(tarjeta, resumenReunion ? 'Resumen: ' + resumenReunion : 'Marcaste esta cita como atendida.');
    tarjeta.querySelector('[data-acciones]').innerHTML = botonEscribir(tarjeta);
    tarjeta.classList.add('tarjeta-cita--pasada');
    moverA(tarjeta, 'historial', true);
    CoreTech.mostrarNotificacion('Cita ' + tarjeta.getAttribute('data-cita') + ' marcada como atendida.');
  });

  /* ---------- Programar solicitud ---------- */
  alGuardar('programar', function (tarjeta, datos) {
    var horario = datos.get('horario');
    var enlace = datos.get('enlace');
    cambiarEstado(tarjeta, 'Programada', 'programada');
    cambiarNota(tarjeta, 'Programada para el ' + horario.charAt(0).toLowerCase() + horario.slice(1) + '. Laura ya lo ve en su proyecto.');
    var sugeridas = tarjeta.querySelector('[data-sugeridas]');
    if (sugeridas) sugeridas.remove();
    if (enlace) ponerEnlace(tarjeta, enlace);
    tarjeta.querySelector('[data-acciones]').innerHTML = botonEscribir(tarjeta);
    moverA(tarjeta, 'programadas', true);
    CoreTech.mostrarNotificacion('Reunión programada. Le avisamos a ' + tarjeta.getAttribute('data-cliente') + '.');
  });

  /* ---------- Rechazar solicitud ---------- */
  alGuardar('rechazar', function (tarjeta, datos) {
    cambiarEstado(tarjeta, 'Rechazada', 'cancelada');
    cambiarNota(tarjeta, 'Respuesta: «' + datos.get('motivo').trim() + '»');
    var sugeridas = tarjeta.querySelector('[data-sugeridas]');
    if (sugeridas) sugeridas.remove();
    tarjeta.querySelector('[data-acciones]').innerHTML = botonEscribir(tarjeta);
    tarjeta.classList.add('tarjeta-cita--pasada');
    moverA(tarjeta, 'respondidas', true);
    CoreTech.mostrarNotificacion('Solicitud rechazada. El cliente verá tu motivo.');
  });

  actualizarContadores();
})();
