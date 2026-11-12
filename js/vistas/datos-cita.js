/* ==========================================================================
   Datos de la cita que se está agendando
   Lo usan: cliente/agendar-confirmar.html y cliente/cita-registrada.html
   Lee servicio, fecha, hora, modalidad y descripción de la dirección de la página y los
   muestra en los elementos marcados con data-cita="…".
   Si la página se abre sin datos, se queda con el ejemplo escrito en el HTML.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var parametros = window.CoreTech.parametros;
  var servicios = window.CoreTech.servicios;
  var DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
  var MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
  var MODALIDADES = {
    presencial: { nombre: 'Presencial', detalle: 'En nuestra oficina: Calle 10 # 4-25, oficina 302, Cúcuta.' },
    virtual: { nombre: 'Virtual', detalle: 'Por videollamada. Te enviaremos el enlace cuando tu cita sea confirmada.' }
  };

  function textoFecha(iso) {
    var partes = iso.split('-').map(Number);
    var fecha = new Date(partes[0], partes[1] - 1, partes[2]);
    if (isNaN(fecha)) return null;
    var dia = DIAS[fecha.getDay()];
    return dia.charAt(0).toUpperCase() + dia.slice(1) + ' ' + fecha.getDate() + ' de ' + MESES[fecha.getMonth()] + ' de ' + fecha.getFullYear();
  }
  function textoHora(hhmm) {
    var partes = hhmm.split(':').map(Number);
    if (isNaN(partes[0])) return null;
    var sufijo = partes[0] < 12 ? 'a. m.' : (partes[0] === 12 ? 'm.' : 'p. m.');
    var hora12 = partes[0] > 12 ? partes[0] - 12 : partes[0];
    var fin = partes[0] + 1;
    var sufijoFin = fin < 12 ? 'a. m.' : (fin === 12 ? 'm.' : 'p. m.');
    return hora12 + ':' + String(partes[1]).padStart(2, '0') + ' ' + sufijo + ' a ' + (fin > 12 ? fin - 12 : fin) + ':00 ' + sufijoFin;
  }

  function escribir(clave, texto) {
    if (!texto) return;
    document.querySelectorAll('[data-cita="' + clave + '"]').forEach(function (elemento) { elemento.textContent = texto; });
  }

  var servicio = servicios[parametros.get('servicio')];
  if (servicio) {
    escribir('servicio', servicio.nombre);
    document.querySelectorAll('[data-cita="imagen"]').forEach(function (imagen) {
      imagen.src = imagen.getAttribute('src').replace(/[^/]+\.webp$/, servicio.imagen);
    });
  }
  if (parametros.get('fecha')) escribir('fecha', textoFecha(parametros.get('fecha')));
  if (parametros.get('hora')) escribir('hora', textoHora(parametros.get('hora')));
  if (parametros.get('descripcion')) escribir('descripcion', parametros.get('descripcion'));
  var modalidad = MODALIDADES[parametros.get('modalidad')];
  if (modalidad) {
    escribir('modalidad', modalidad.nombre);
    escribir('detalle-modalidad', modalidad.detalle);
    document.querySelectorAll('[data-cita-icono]').forEach(function (icono) {
      // Los iconos son <svg>: se usa el atributo hidden (la propiedad .hidden no existe en SVG)
      if (icono.getAttribute('data-cita-icono') === parametros.get('modalidad')) icono.removeAttribute('hidden');
      else icono.setAttribute('hidden', '');
    });
  }

  // Los enlaces "Cambiar" y "Atrás" regresan con los mismos datos
  document.querySelectorAll('[data-volver-con-datos]').forEach(function (enlace) {
    enlace.setAttribute('href', enlace.getAttribute('href').split('?')[0] + location.search);
  });
})();
