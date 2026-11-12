/* ==========================================================================
   Vista: Solicitudes de reunión del administrador (admin/solicitudes.html)
   - Programar una solicitud en uno de los horarios que sugirió el cliente
     (con enlace si es virtual), en nombre del responsable.
   - Rechazar una solicitud o cancelar una reunión programada, con motivo.
   Los filtros, la búsqueda y el menú "Más acciones" los maneja js/tabla.js.
   Requiere: js/principal.js y js/tabla.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var filaActual = null;

  function limpiar(formulario) {
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (campo) { campo.classList.remove('campo--invalido', 'campo--valido'); });
    formulario.querySelectorAll('textarea').forEach(function (area) { area.dispatchEvent(new Event('input')); });
  }

  function abrir(tipo, fila) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    filaActual = fila;
    limpiar(formulario);
    var resumen = dialogo.querySelector('[data-resumen]');
    resumen.innerHTML = '<strong></strong><span></span>';
    resumen.querySelector('strong').textContent = fila.getAttribute('data-codigo') + ' · ' + fila.getAttribute('data-motivo');
    resumen.querySelector('span').textContent = fila.getAttribute('data-cliente') + ' · ' + fila.getAttribute('data-proyecto') + ' · Responsable: ' + fila.getAttribute('data-desarrollador');

    if (tipo === 'programar') {
      // Horarios sugeridos por el cliente
      var contenedor = dialogo.querySelector('[data-opciones-horario]');
      contenedor.innerHTML = '';
      fila.getAttribute('data-opciones').split('|').forEach(function (opcion) {
        var etiqueta = document.createElement('label');
        etiqueta.className = 'opcion-simple';
        etiqueta.innerHTML = '<input type="radio" name="horario" required data-mensaje-vacio="Elige uno de los horarios." aria-describedby="programar-horario-mensaje"><span></span>';
        etiqueta.querySelector('input').value = opcion;
        etiqueta.querySelector('span').textContent = opcion;
        contenedor.appendChild(etiqueta);
      });
      var esVirtual = fila.getAttribute('data-modalidad') === 'Virtual';
      dialogo.querySelector('[data-campo-enlace]').hidden = !esVirtual;
      dialogo.querySelector('#programar-enlace').required = esVirtual;
      dialogo.querySelector('[data-nota-presencial]').hidden = esVirtual;
    }
    dialogo.showModal();
  }

  document.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-abrir]');
    if (boton) abrir(boton.getAttribute('data-abrir'), boton.closest('tr'));
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });

  function alGuardar(tipo, aplicar) {
    var dialogo = document.getElementById('dialogo-' + tipo);
    var formulario = dialogo.querySelector('form');
    formulario.addEventListener('formulario-valido', function () {
      var boton = formulario.querySelector('[type="submit"]');
      boton.classList.add('esta-cargando');
      setTimeout(function () {
        boton.classList.remove('esta-cargando');
        dialogo.close();
        if (filaActual) {
          aplicar(filaActual, new FormData(formulario));
          filaActual.classList.remove('esta-resaltada');
          void filaActual.offsetWidth;
          filaActual.classList.add('esta-resaltada');
        }
        filaActual = null;
        CoreTech.tabla.actualizar();
      }, 700);
    });
  }

  function cambiarEstado(fila, estado, texto, clase, detalle, acciones) {
    fila.setAttribute('data-estado', estado);
    var insignia = fila.querySelector('[data-insignia-estado]');
    insignia.className = 'insignia insignia--' + clase;
    insignia.textContent = texto;
    fila.querySelector('[data-celda-detalle]').textContent = detalle;
    fila.querySelector('[data-acciones]').innerHTML = acciones;
  }

  // "Jueves 19 de noviembre, 10:00 a. m." → "Jue 19 nov · 10:00 a. m."
  function corto(horario) {
    var partes = horario.match(/^(\w{3})\w*\s(\d+) de (\w{3})\w*, (.+)$/);
    return partes ? partes[1] + ' ' + partes[2] + ' ' + partes[3] + ' · ' + partes[4] : horario;
  }

  alGuardar('programar', function (fila, datos) {
    var horario = datos.get('horario');
    cambiarEstado(fila, 'programada', 'Programada', 'programada', corto(horario),
      '<span class="celda-principal"><small>Programada por la administración</small></span>');
    CoreTech.mostrarNotificacion('Reunión ' + fila.getAttribute('data-codigo') + ' programada para el ' + horario.charAt(0).toLowerCase() + horario.slice(1) + '. Avisamos al cliente y a ' + fila.getAttribute('data-desarrollador') + '.');
  });

  alGuardar('rechazar', function (fila) {
    cambiarEstado(fila, 'rechazada', 'Rechazada', 'cancelada', 'Rechazada hoy por la administración', '<span class="celda-principal"><small>Cerrada</small></span>');
    fila.classList.add('fila--apagada');
    CoreTech.mostrarNotificacion('Solicitud ' + fila.getAttribute('data-codigo') + ' rechazada. El cliente verá el motivo.');
  });

  alGuardar('cancelar', function (fila) {
    cambiarEstado(fila, 'rechazada', 'Cancelada', 'cancelada', 'Cancelada hoy por la administración', '<span class="celda-principal"><small>Cerrada</small></span>');
    fila.classList.add('fila--apagada');
    CoreTech.mostrarNotificacion('Reunión ' + fila.getAttribute('data-codigo') + ' cancelada. Avisamos al cliente y al responsable.');
  });
})();
