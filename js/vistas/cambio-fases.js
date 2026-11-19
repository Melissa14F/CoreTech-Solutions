/* ==========================================================================
   Cambios de un plan aprobado
   - Product Owner: propone agregar una fase, quitar una pendiente, cambiar solo la
     fecha de entrega o quitar un paso pendiente (con vista previa del porcentaje por
     fase y del avance).
   - Administrador: autoriza o rechaza la propuesta.
   - Cliente: acepta o no acepta el cambio ya autorizado. Quitar un paso de una fase
     que no aprueba el cliente se aplica al autorizarlo; al cliente solo se le avisa.
   Reglas: de 2 a 8 fases; al menos una la aprueba el cliente; las fases
   aprobadas no cambian.
   Requiere: js/principal.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  function pct(n) { return (Math.round(1000 / n) / 10).toString().replace('.', ',') + ' %'; }
  function cerrarDialogo(evento) {
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  }
  function abrir(dialogo) {
    var form = dialogo.querySelector('form');
    if (form) {
      form.reset();
      form.querySelectorAll('.campo').forEach(function (c) { c.classList.remove('campo--invalido', 'campo--valido'); });
    }
    dialogo.showModal();
  }
  document.addEventListener('click', cerrarDialogo);

  /* ---------- Proponer (Product Owner) ---------- */
  var dialogoProponer = document.getElementById('dialogo-proponer-cambio');
  if (dialogoProponer) {
    var DATOS = JSON.parse(document.getElementById('datos-cambio-fases').textContent);
    var form = dialogoProponer.querySelector('form');
    var previa = dialogoProponer.querySelector('[data-vista-previa-cambio]');
    var grupos = dialogoProponer.querySelectorAll('[data-si-tipo]');
    var ocultables = dialogoProponer.querySelectorAll('[data-oculto-si-tipo]');
    var n = DATOS.fases.length;
    var hechas = DATOS.fases.filter(function (f) { return f.estado === 'aprobada'; }).length;

    function tipo() { var r = form.querySelector('input[name="tipo"]:checked'); return r ? r.value : 'agregar'; }
    function aplicarTipo() {
      var t = tipo();
      grupos.forEach(function (g) {
        var visible = g.getAttribute('data-si-tipo') === t;
        g.hidden = !visible;
        g.querySelectorAll('input:not([type="checkbox"]), select, textarea').forEach(function (c) {
          c.required = visible;
          if (!visible) { var campo = c.closest('.campo'); if (campo) campo.classList.remove('campo--invalido'); }
        });
      });
      // Campos que sobran en un tipo (quitar un paso no cambia la entrega ni el valor)
      ocultables.forEach(function (g) {
        var oculto = g.getAttribute('data-oculto-si-tipo') === t;
        g.hidden = oculto;
        g.querySelectorAll('input, select, textarea').forEach(function (c) {
          c.required = !oculto;
          if (oculto) { var campo = c.closest('.campo'); if (campo) campo.classList.remove('campo--invalido'); }
        });
      });
      actualizarPrevia();
    }
    function pasoElegido() {
      var select = form.elements.paso;
      return select && select.value ? select.options[select.selectedIndex] : null;
    }
    function actualizarPrevia() {
      var t = tipo();
      if (t === 'paso') {
        var opcion = pasoElegido();
        if (!opcion) { previa.innerHTML = '<p>Las fases, su valor, el avance y la entrega no cambian: solo se quita un paso que no ha empezado.</p>'; return true; }
        var aprueba = opcion.getAttribute('data-aprueba') === 'true';
        previa.innerHTML = '<p>Se quita «' + opcion.textContent + '» de <strong>' + opcion.getAttribute('data-fase') + '</strong>. Las fases, su valor, el avance y la entrega no cambian.</p>' +
          '<p>' + (aprueba
            ? 'Como esta fase la aprueba ' + DATOS.cliente + ', después del administrador también la debe aceptar ' + DATOS.cliente + '.'
            : 'Esta fase la cierra el equipo: basta con que la autorice el administrador, y a ' + DATOS.cliente + ' le avisamos.') + '</p>';
        return true;
      }
      if (t === 'fecha') {
        var nueva = form.elements.entrega.value;
        var igual = nueva && nueva === DATOS.entrega;
        previa.innerHTML = '<p>Las fases y el avance no cambian: solo se mueve la fecha de entrega.</p>' +
          (igual ? '<p class="alerta alerta--error">Elige una fecha distinta a la entrega actual.</p>' : '');
        return !igual;
      }
      var total = t === 'agregar' ? n + 1 : n - 1;
      var avanceAntes = Math.round(1000 * hechas / n) / 10;
      var avanceDespues = Math.round(1000 * hechas / total) / 10;
      var linea = 'Pasa de <strong>' + n + '</strong> a <strong>' + total + '</strong> fases. Cada fase valdría <strong>' + pct(total) + '</strong> (hoy ' + pct(n) + '). ' +
        'El avance pasaría de <strong>' + String(avanceAntes).replace('.', ',') + ' %</strong> a <strong>' + String(avanceDespues).replace('.', ',') + ' %</strong>.';
      var aviso = '';
      if (t === 'quitar') {
        var i = form.elements.quitar.value;
        if (total < 2) aviso = 'Un plan necesita al menos 2 fases.';
        else if (i !== '' && DATOS.fases[i].aprueba && DATOS.fases.filter(function (f) { return f.aprueba; }).length === 1) aviso = 'Es la única fase que aprueba ' + DATOS.cliente + ': no se puede quitar.';
      } else if (total > 8) aviso = 'Un plan puede tener máximo 8 fases.';
      previa.innerHTML = '<p>' + linea + '</p>' + (aviso ? '<p class="alerta alerta--error">' + aviso + '</p>' : '');
      return !aviso;
    }
    form.addEventListener('change', function (e) { if (e.target.name === 'tipo') aplicarTipo(); else actualizarPrevia(); });

    document.querySelectorAll('[data-proponer-cambio]').forEach(function (b) {
      b.addEventListener('click', function () { abrir(dialogoProponer); aplicarTipo(); });
    });
    form.addEventListener('formulario-valido', function () {
      if (!actualizarPrevia()) { CoreTech.mostrarNotificacion('Este cambio no cumple las reglas del plan.', 'error'); return; }
      if (tipo() === 'agregar') {
        var pasos = form.elements.pasos.value.split('\n').map(function (p) { return p.trim(); }).filter(Boolean);
        if (!pasos.length) { CoreTech.mostrarNotificacion('Escribe al menos un paso para la fase nueva.', 'error'); return; }
      }
      dialogoProponer.close();
      var opcion = tipo() === 'paso' ? pasoElegido() : null;
      if (opcion && opcion.getAttribute('data-aprueba') !== 'true') {
        CoreTech.mostrarNotificacion('Propuesta enviada al administrador. Cuando la autorice, se quitará el paso y le avisaremos a ' + DATOS.cliente + '.');
      } else {
        CoreTech.mostrarNotificacion('Propuesta enviada al administrador. Cuando la autorice, se la mostraremos a ' + DATOS.cliente + '.');
      }
    });
  }

  /* ---------- Responder (administrador o cliente) ---------- */
  var tarjetas = Array.prototype.slice.call(document.querySelectorAll('[data-cambio-fases]'))
    .filter(function (t) { return t.querySelector('[data-responder-cambio]'); });
  if (!tarjetas.length) return;
  var dAceptar = document.getElementById('dialogo-cambio-aceptar');
  var dRechazar = document.getElementById('dialogo-cambio-rechazar');
  var actual = null; // tarjeta que se está respondiendo

  function terminar(tarjeta, mensaje) {
    tarjeta.hidden = true;
    CoreTech.mostrarNotificacion(mensaje);
    var contador = document.querySelector('[data-cantidad-cambios]');
    if (contador) {
      var resto = Math.max(0, Number(contador.textContent) - 1);
      contador.textContent = resto;
      var seccion = contador.closest('[data-seccion-cambios]');
      if (seccion && !resto) seccion.hidden = true;
    }
  }
  function textoAceptado(tarjeta) {
    var t = tarjeta.getAttribute('data-tipo');
    if (t === 'fecha') return 'Aceptaste la nueva fecha de entrega. Tus fases y tu avance siguen igual.';
    if (t === 'quitar') return 'Aceptaste el cambio. Tu plan ahora tiene una fase menos y el avance se recalculó.';
    if (t === 'paso') return 'Aceptaste el cambio. Se quitó el paso; tus fases y tu avance siguen igual.';
    return 'Aceptaste el cambio. Tu plan ahora tiene una fase más y el avance se recalculó.';
  }

  tarjetas.forEach(function (tarjeta) {
    tarjeta.addEventListener('click', function (evento) {
      var b = evento.target.closest('[data-responder-cambio]');
      if (!b) return;
      actual = tarjeta;
      var accion = b.getAttribute('data-responder-cambio');
      if (accion === 'autorizar') {
        terminar(tarjeta, 'Cambio autorizado. Se lo enviamos a ' + tarjeta.getAttribute('data-cliente-nombre') + ' para que lo acepte.');
      } else if (accion === 'aceptar') {
        abrir(dAceptar);
      } else {
        abrir(dRechazar);
      }
    });
  });
  if (dAceptar) dAceptar.querySelector('form').addEventListener('formulario-valido', function () {
    dAceptar.close();
    terminar(actual, textoAceptado(actual));
  });
  dRechazar.querySelector('form').addEventListener('formulario-valido', function () {
    dRechazar.close();
    terminar(actual, dAceptar ? 'Le contamos al equipo que no aceptaste el cambio. Tu plan sigue igual.'
                              : 'Cambio rechazado. Avisamos a ' + actual.getAttribute('data-po') + ' con tu motivo; el plan de ' + actual.getAttribute('data-cliente') + ' sigue igual.');
  });
})();
