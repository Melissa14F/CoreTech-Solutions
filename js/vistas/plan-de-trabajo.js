/* ==========================================================================
   Vista: Plan de trabajo de un proyecto
   (desarrollador/plan-de-trabajo-*.html y admin/plan-de-trabajo-*.html)
   - Editor de fases y pasos del plan borrador.
   - Cotización: valor en COP (se escribe con puntos de miles) y fecha estimada.
   - "Enviar al cliente" valida todo y pide confirmación.
   Requiere: js/principal.js y js/plan-editor.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var raiz = document.querySelector('[data-plan]');
  var cliente = raiz.getAttribute('data-cliente');
  var DATOS = JSON.parse(document.getElementById('datos-plan').textContent);
  var editor = CoreTech.editorPlan(document.querySelector('[data-editor-plan]'), DATOS.fases, { cliente: cliente, prefijo: 'plan' });
  var VALOR_MIN = 1000000, VALOR_MAX = 200000000;

  /* ---------- Resumen de la cotización según las fases ---------- */
  var salidas = {};
  document.querySelectorAll('[data-resumen-cotizacion]').forEach(function (s) { salidas[s.getAttribute('data-resumen-cotizacion')] = s; });
  editor.alCambiar(function (fases) {
    salidas.fases.textContent = fases.length;
    salidas.porcentaje.textContent = fases.length ? CoreTech.porcentajeFase(fases.length) : '—';
    salidas.aprueba.textContent = fases.filter(function (f) { return f.aprueba; }).length;
  });

  /* ---------- Marcar errores en un campo ---------- */
  function marcar(control, texto) {
    var campo = control.closest('.campo');
    campo.classList.toggle('campo--invalido', !!texto);
    campo.classList.toggle('campo--valido', !texto && !!control.value.trim());
    control.setAttribute('aria-invalid', String(!!texto));
    var salida = campo.querySelector('.campo__mensaje-texto');
    if (salida) salida.textContent = texto || '';
    return !texto;
  }
  function revisarBasico(control) {
    var v = control.value.trim();
    if (control.required && !v) return control.getAttribute('data-mensaje-vacio') || 'Completa este campo.';
    if (v && control.minLength > 0 && v.length < control.minLength) return 'Escribe al menos ' + control.minLength + ' caracteres.';
    return '';
  }

  /* ---------- Valor en pesos con puntos de miles ---------- */
  var valor = document.getElementById('cotizacion-valor');
  function numero(texto) { return Number(String(texto).replace(/\D/g, '')) || 0; }
  function conPuntos(n) { return n ? n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.') : ''; }
  valor.addEventListener('input', function () {
    var fin = valor.value.length - valor.selectionEnd;
    valor.value = conPuntos(numero(valor.value));
    var pos = Math.max(0, valor.value.length - fin);
    valor.setSelectionRange(pos, pos);
    if (valor.closest('.campo--invalido')) revisarValor();
  });
  function revisarValor() {
    var n = numero(valor.value);
    var t = !n ? 'Escribe el valor del proyecto.' : (n < VALOR_MIN || n > VALOR_MAX ? 'El valor debe estar entre $1.000.000 y $200.000.000.' : '');
    return marcar(valor, t);
  }
  valor.addEventListener('blur', revisarValor);

  var entrega = document.getElementById('cotizacion-entrega');
  function revisarEntrega() {
    var v = entrega.value;
    var t = !v ? 'Elige la fecha estimada de entrega.' : (v < entrega.min ? entrega.getAttribute('data-mensaje-rango') : (v > entrega.max ? 'Revisa el año de la fecha.' : ''));
    return marcar(entrega, t);
  }
  entrega.addEventListener('change', revisarEntrega);

  var condiciones = document.getElementById('cotizacion-condiciones');
  [condiciones].forEach(function (c) {
    c.addEventListener('blur', function () { marcar(c, revisarBasico(c)); });
    c.addEventListener('input', function () { if (c.closest('.campo--invalido')) marcar(c, revisarBasico(c)); });
  });

  /* ---------- Enviar al cliente ---------- */
  var dialogoEnviar = document.getElementById('dialogo-enviar');
  function validarTodo() {
    var primero = null;
    var okPlan = editor.validar();
    var okValor = revisarValor(); if (!okValor) primero = primero || valor;
    var okEntrega = revisarEntrega(); if (!okEntrega) primero = primero || entrega;
    var okResto = true;
    [condiciones].forEach(function (c) {
      if (!marcar(c, revisarBasico(c))) { okResto = false; primero = primero || c; }
    });
    if (!okPlan) {
      CoreTech.mostrarNotificacion('Revisa las fases: hay datos incompletos.', 'error');
      return false;
    }
    if (primero) {
      primero.focus();
      CoreTech.mostrarNotificacion('Revisa los campos marcados en rojo.', 'error');
      return false;
    }
    return okValor && okEntrega && okResto;
  }
  document.querySelectorAll('[data-enviar-plan]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      if (!validarTodo()) return;
      var fases = editor.obtener();
      var r = dialogoEnviar.querySelector('[data-resumen-envio]');
      r.querySelector('[data-envio="fases"]').textContent = fases.length + ' · ' + CoreTech.porcentajeFase(fases.length) + ' cada una';
      r.querySelector('[data-envio="aprueba"]').textContent = fases.filter(function (f) { return f.aprueba; }).length;
      r.querySelector('[data-envio="valor"]').textContent = '$' + valor.value + ' COP';
      var p = entrega.value.split('-');
      var meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
      r.querySelector('[data-envio="entrega"]').textContent = Number(p[2]) + ' ' + meses[Number(p[1]) - 1] + ' ' + p[0];
      dialogoEnviar.showModal();
    });
  });
  dialogoEnviar.querySelector('form').addEventListener('formulario-valido', function () {
    dialogoEnviar.close();
    CoreTech.dejarMensaje('Plan enviado a ' + cliente + '. Tiene hasta el ' + raiz.getAttribute('data-plazo') + ' para aprobarlo o pedir ajustes.');
    CoreTech.navegarConCarga(raiz.getAttribute('data-destino'));
  });

  /* ---------- Cerrar diálogos ---------- */
  document.addEventListener('click', function (evento) {
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });
})();
