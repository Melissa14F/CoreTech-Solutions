/* ==========================================================================
   Vista: Crear proyecto (admin/crear-proyecto.html)
   - Al elegir la cita muestra cliente, servicio y quién la atendió, y propone
     un nombre. Llega preseleccionada con ?cita=C-0057 desde la tabla de citas.
   - La entrega estimada debe ser al menos 2 semanas después del inicio.
   - Resumen en vivo de cómo quedará el proyecto.
   El envío lo valida js/principal.js (enlace con data-enviar).
   ========================================================================== */
(function () {
  'use strict';

  var formulario = document.querySelector('[data-formulario-proyecto]');
  var cita = document.getElementById('proyecto-cita');
  var nombre = document.getElementById('proyecto-nombre');
  var inicio = document.getElementById('proyecto-inicio');
  var entrega = document.getElementById('proyecto-entrega');
  var datosCita = document.querySelector('[data-datos-cita]');
  var duracion = document.querySelector('[data-duracion]');
  var DIA = 86400000;

  function poner(clave, texto) {
    var celda = document.querySelector('[data-resumen="' + clave + '"]');
    if (celda.textContent === texto) return;
    celda.textContent = texto;
    celda.classList.remove('esta-actualizado');
    void celda.offsetWidth;
    celda.classList.add('esta-actualizado');
  }

  function aFecha(texto) { var p = texto.split('-'); return new Date(p[0], p[1] - 1, p[2]); }
  function aTexto(fecha) { return fecha.getFullYear() + '-' + String(fecha.getMonth() + 1).padStart(2, '0') + '-' + String(fecha.getDate()).padStart(2, '0'); }
  function legible(texto) { return aFecha(texto).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' }); }

  /* ---------- Cita de origen ---------- */
  function alElegirCita() {
    var opcion = cita.selectedOptions[0];
    var elegida = !!cita.value;
    datosCita.hidden = !elegida;
    if (!elegida) { poner('cliente', '—'); poner('servicio', '—'); return; }
    ['cliente', 'servicio', 'fecha', 'desarrollador'].forEach(function (clave) {
      datosCita.querySelector('[data-dato="' + clave + '"]').textContent = opcion.getAttribute('data-' + clave);
    });
    poner('cliente', opcion.getAttribute('data-cliente'));
    poner('servicio', opcion.getAttribute('data-servicio'));
    if (!nombre.value) { nombre.value = opcion.getAttribute('data-nombre'); alEscribirNombre(); }
  }
  cita.addEventListener('change', alElegirCita);

  /* ---------- Nombre ---------- */
  var nombreResumen = document.querySelector('[data-resumen="nombre"]');
  function alEscribirNombre() {
    var texto = nombre.value.trim();
    nombreResumen.textContent = texto || 'Proyecto sin nombre';
    nombreResumen.classList.toggle('esta-vacio', !texto);
  }
  nombre.addEventListener('input', alEscribirNombre);

  /* ---------- Fechas ---------- */
  function alCambiarFechas() {
    if (inicio.value) entrega.min = aTexto(new Date(aFecha(inicio.value).getTime() + 14 * DIA));
    if (inicio.value && entrega.value) {
      var dias = Math.round((aFecha(entrega.value) - aFecha(inicio.value)) / DIA);
      var semanas = Math.floor(dias / 7);
      duracion.textContent = dias >= 14
        ? 'Duración: ' + semanas + ' semanas' + (dias % 7 ? ' y ' + (dias % 7) + (dias % 7 === 1 ? ' día' : ' días') : '') + '.'
        : 'Mínimo 2 semanas después del inicio.';
      poner('entrega', legible(entrega.value));
    } else {
      poner('entrega', '—');
    }
  }
  inicio.addEventListener('change', alCambiarFechas);
  entrega.addEventListener('change', alCambiarFechas);
  entrega.addEventListener('input', alCambiarFechas);

  /* ---------- Responsable ---------- */
  formulario.querySelectorAll('input[name="desarrollador"]').forEach(function (opcion) {
    opcion.addEventListener('change', function () { poner('desarrollador', opcion.value); });
  });

  /* ---------- Cita que llega en la URL ---------- */
  var desdeUrl = window.CoreTech.parametros.get('cita');
  if (desdeUrl && cita.querySelector('option[value="' + desdeUrl + '"]:not([disabled])')) {
    cita.value = desdeUrl;
    alElegirCita();
  }
  alEscribirNombre();
  alCambiarFechas();
})();
