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
    mostrarCuestionario(opcion);
    if (!nombre.value) { nombre.value = opcion.getAttribute('data-nombre'); alEscribirNombre(); }
  }
  cita.addEventListener('change', alElegirCita);

  // Plantilla de fases del servicio: se copia como plan borrador del proyecto
  var PLANTILLAS = JSON.parse(document.getElementById('datos-plantillas-servicio').textContent);
  var cajaPlantilla = document.querySelector('[data-plantilla-proyecto]');
  function mostrarPlantilla() {
    var opcion = cita.options[cita.selectedIndex];
    var servicio = opcion && opcion.getAttribute('data-servicio');
    var fases = servicio && PLANTILLAS[servicio];
    var lista = cajaPlantilla.querySelector('[data-plantilla-fases]');
    if (!fases) {
      cajaPlantilla.querySelector('[data-plantilla-titulo]').textContent = 'Plan de trabajo inicial';
      cajaPlantilla.querySelector('[data-plantilla-texto]').textContent = 'Elige la cita para ver la plantilla de fases de su servicio.';
      lista.innerHTML = '';
      return;
    }
    var aprueba = fases.filter(function (f) { return f[1]; }).length;
    cajaPlantilla.querySelector('[data-plantilla-titulo]').textContent = 'Plan inicial: plantilla «' + servicio + '»';
    cajaPlantilla.querySelector('[data-plantilla-texto]').textContent = fases.length + ' fases de ' + (Math.round(1000 / fases.length) / 10).toString().replace('.', ',') + ' %' +
      ' cada una; el cliente aprueba ' + aprueba + '. Al crear el proyecto se copia como borrador: el equipo puede agregar, quitar o renombrar fases antes de enviarlo con la cotización.';
    lista.innerHTML = fases.map(function (f) {
      return '<li class="plantilla-proyecto__fase' + (f[1] ? ' plantilla-proyecto__fase--cliente' : '') + '">' + f[0] + (f[1] ? ' <small>aprueba el cliente</small>' : '') + '</li>';
    }).join('');
  }
  cita.addEventListener('change', mostrarPlantilla);
  mostrarPlantilla();

  // Cuestionario que llenó el Product Owner al cerrar la cita: ayuda a definir
  // el alcance, la fecha de entrega y el equipo
  var cuestionario = document.querySelector('[data-cuestionario-cita]');
  function mostrarCuestionario(opcion) {
    var datos = opcion.getAttribute('data-cuestionario');
    cuestionario.hidden = !datos;
    if (!datos) return;
    var r = JSON.parse(datos);
    ['data-cuestionario', 'data-cliente', 'data-servicio', 'data-modalidad'].forEach(function (a) {
      if (opcion.hasAttribute(a)) cuestionario.setAttribute(a, opcion.getAttribute(a));
    });
    cuestionario.setAttribute('data-codigo', opcion.value);
    if (!cuestionario.hasAttribute('data-modalidad')) cuestionario.setAttribute('data-modalidad', 'Cita del ' + opcion.getAttribute('data-fecha').toLowerCase());
    cuestionario.querySelectorAll('[data-cuest]').forEach(function (campo) {
      var clave = campo.getAttribute('data-cuest');
      campo.textContent = r[clave] || 'Sin fecha definida';
    });
    var urgencia = cuestionario.querySelector('[data-cuest="urgencia"]');
    urgencia.className = 'insignia insignia--urgencia-' + r.urgencia.toLowerCase();
  }

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
