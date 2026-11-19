/* ==========================================================================
   Vista: Agendar cita · Paso 2 (cliente/agendar-fecha.html)
   - País donde vive el cliente (y zona horaria si el país tiene varias): el
     calendario y las horas se muestran en su hora local. Los horarios de
     atención son los de Colombia (lunes a viernes, 8–12 y 14–18), que no
     cambia de hora en el año (UTC−5).
   - Calendario de mes: cambia entre meses con las flechas. Si en la zona del
     cliente una hora cae en otro día, aparece en ese día.
   - Al elegir un día muestra sus horarios libres; la hora guarda el día y la
     hora de Colombia, que es como se registra la cita.
   - Mantiene actualizado el resumen lateral (incluida la descripción).
   - Si el usuario vuelve desde el paso 3, recupera lo que había elegido.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var formulario = document.querySelector('form[data-validar]');
  if (!formulario) return;

  var D = JSON.parse(document.getElementById('datos-horarios').textContent);
  var COLOMBIA = 'America/Bogota';
  var DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
  var MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

  var calendario = document.querySelector('.calendario');
  var horarios = document.querySelector('.horarios');
  var leyenda = calendario.querySelector('.leyenda');
  var mensajeHora = document.getElementById('hora-mensaje');
  var tituloMes = document.querySelector('[data-titulo-mes-actual]');
  var botonAnterior = document.querySelector('[data-mes-anterior]');
  var botonSiguiente = document.querySelector('[data-mes-siguiente]');
  var avisoSinDia = document.querySelector('[data-franjas-vacio]');
  var resumenFecha = document.querySelector('[data-resumen="fecha"]');
  var resumenModalidad = document.querySelector('[data-resumen="modalidad"]');
  var resumenDescripcion = document.querySelector('[data-resumen="descripcion"]');
  var descripcion = formulario.querySelector('[name="descripcion"]');
  var fechaColombia = formulario.querySelector('[data-fecha-colombia]');
  var selectPais = formulario.querySelector('[name="pais"]');
  var selectZona = formulario.querySelector('[name="zona"]');
  var campoZona = formulario.querySelector('[data-campo-zona]');
  var meses = [];
  var mesVisible = 0;

  /* ---------- Fechas y horas ---------- */
  function iso(y, m, d) { return y + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0'); }
  function partesIso(texto) { return texto.split('-').map(Number); }
  function diaSemana(fecha) { var p = partesIso(fecha); return new Date(Date.UTC(p[0], p[1] - 1, p[2])).getUTCDay(); }
  function textoDia(fecha) { var p = partesIso(fecha); return DIAS[diaSemana(fecha)] + ' ' + p[2] + ' de ' + MESES[p[1] - 1]; }
  function sumarDias(fecha, n) { var p = partesIso(fecha); var d = new Date(Date.UTC(p[0], p[1] - 1, p[2] + n)); return iso(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate()); }
  function texto12(h, m) {
    var sufijo = h === 12 && m === 0 ? 'm.' : (h < 12 ? 'a. m.' : 'p. m.');
    var h12 = h % 12 === 0 ? 12 : h % 12;
    return h12 + ':' + String(m).padStart(2, '0') + ' ' + sufijo;
  }

  // Día y hora de Colombia (UTC−5) → día y hora en la zona del cliente
  var formatos = {};
  function enZona(fecha, hora, zona) {
    var p = partesIso(fecha), t = hora.split(':').map(Number);
    var instante = new Date(Date.UTC(p[0], p[1] - 1, p[2], t[0] + 5, t[1]));
    formatos[zona] = formatos[zona] || new Intl.DateTimeFormat('en-CA', { timeZone: zona, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    var v = {};
    formatos[zona].formatToParts(instante).forEach(function (parte) { v[parte.type] = Number(parte.value); });
    return { fecha: iso(v.year, v.month, v.day), h: v.hour % 24, m: v.minute };
  }

  /* ---------- País y zona horaria ---------- */
  function zonaActual() { return selectZona.value || COLOMBIA; }
  function llenarZonas(pais, zona) {
    var zonas = D.paises[pais] || [[COLOMBIA, 'Colombia']];
    selectZona.innerHTML = zonas.map(function (z) { return '<option value="' + z[0] + '">' + z[1] + '</option>'; }).join('');
    if (zona && zonas.some(function (z) { return z[0] === zona; })) selectZona.value = zona;
    campoZona.hidden = zonas.length < 2;
    var ayuda = document.querySelector('[data-zona-ayuda]');
    ayuda.textContent = pais === 'Colombia'
      ? 'Lo usamos para adaptar la cita a tu zona horaria. Atendemos de lunes a viernes, de 8:00 a. m. a 12:00 m. y de 2:00 a 6:00 p. m.'
      : 'Te mostramos el calendario y las horas en tu hora local. Atendemos de lunes a viernes, de 8:00 a. m. a 12:00 m. y de 2:00 a 6:00 p. m. hora de Colombia; al lado de cada hora verás su equivalente allá.';
  }

  /* ---------- Construir el calendario y los horarios en la zona elegida ---------- */
  function motivo(fecha, haySlots) {
    if (haySlots) return 'Sin cupos';
    if (fecha < D.hoy) return 'Ya pasó';
    if (fecha === D.hoy) return 'Hoy';
    if (D.festivos[fecha]) return 'Festivo en Colombia: ' + D.festivos[fecha];
    var dia = diaSemana(fecha);
    if (dia === 0 || dia === 6) return 'Fin de semana';
    if (fecha > D.hasta) return 'Fuera del plazo';
    return 'Sin horarios de atención';
  }

  function construir() {
    var zona = zonaActual();
    var otraZona = zona !== COLOMBIA;
    // Horarios agrupados por día local
    var porDia = {};
    Object.keys(D.dias).forEach(function (fechaCol) {
      D.dias[fechaCol].forEach(function (franja) {
        var local = enZona(fechaCol, franja[0], zona);
        (porDia[local.fecha] = porDia[local.fecha] || []).push({ fechaCol: fechaCol, hora: franja[0], estado: franja[1], nota: franja[2], h: local.h, m: local.m });
      });
    });
    Object.keys(porDia).forEach(function (dia) { porDia[dia].sort(function (a, b) { return a.h * 60 + a.m - (b.h * 60 + b.m); }); });

    // Meses a mostrar: del mes de "hoy" al del último día con horarios
    var dias = Object.keys(porDia).sort();
    var inicio = partesIso(D.hoy), fin = partesIso(dias[dias.length - 1]);
    calendario.querySelectorAll('[data-mes]').forEach(function (m) { m.remove(); });
    horarios.querySelectorAll('[data-franjas-de]').forEach(function (g) { g.remove(); });
    meses = [];
    for (var y = inicio[0], mes = inicio[1]; y < fin[0] || (y === fin[0] && mes <= fin[1]); mes === 12 ? (y++, mes = 1) : mes++) {
      meses.push(construirMes(y, mes, porDia));
    }
    meses.forEach(function (m) { calendario.insertBefore(m, leyenda); });

    // Horarios de cada día con cupos
    dias.forEach(function (dia) {
      var libres = porDia[dia].filter(function (f) { return f.estado === 'libre'; }).length;
      if (!libres) return;
      horarios.insertBefore(construirFranjas(dia, porDia[dia], libres, otraZona), mensajeHora);
    });
    mostrarMes(0);
    mostrarHorariosDe(null);
  }

  function construirMes(y, mes, porDia) {
    var contenedor = document.createElement('div');
    var titulo = MESES[mes - 1].charAt(0).toUpperCase() + MESES[mes - 1].slice(1) + ' ' + y;
    contenedor.className = 'calendario__mes calendario__mes--oculto';
    contenedor.setAttribute('data-mes', iso(y, mes, 1).slice(0, 7));
    contenedor.setAttribute('data-titulo-mes', titulo);
    var primero = iso(y, mes, 1);
    var vacios = (diaSemana(primero) + 6) % 7;   // la semana empieza el lunes
    var html = '<p class="calendario__titulo-sin-js">' + titulo + '</p>';
    for (var i = 0; i < vacios; i++) html += '<span class="calendario__vacio" aria-hidden="true"></span>';
    var total = new Date(Date.UTC(y, mes, 0)).getUTCDate();
    for (var d = 1; d <= total; d++) {
      var fecha = iso(y, mes, d);
      var franjas = porDia[fecha] || [];
      var libres = franjas.filter(function (f) { return f.estado === 'libre'; }).length;
      var clases = 'calendario__dia' + (fecha === D.hoy ? ' calendario__dia--hoy' : '');
      if (!libres || fecha <= D.hoy) {
        var razon = motivo(fecha, franjas.length > 0);
        clases += ' calendario__dia--inactivo' + (D.festivos[fecha] ? ' calendario__dia--festivo' : '');
        html += '<span class="' + clases + '" title="' + razon + '"' + (fecha === D.hoy ? ' aria-current="date"' : '') + ' aria-hidden="true"><span class="calendario__numero">' + d + '</span></span>';
        continue;
      }
      var cupos = libres === 1 ? '1 horario libre' : libres + ' horarios libres';
      if (libres <= 2) clases += ' calendario__dia--pocos';
      html += '<label class="' + clases + '" title="' + cupos + '">' +
        '<input class="control-oculto" type="radio" name="dia" value="' + fecha + '" required data-texto="' + textoDia(fecha) + '" data-mensaje-vacio="Elige el día de tu cita." aria-describedby="fecha-mensaje">' +
        '<span class="calendario__numero" aria-hidden="true">' + d + '</span><span class="calendario__cupos" aria-hidden="true"></span>' +
        '<span class="solo-lector">' + textoDia(fecha) + ', ' + cupos + '</span></label>';
    }
    contenedor.innerHTML = html;
    return contenedor;
  }

  function construirFranjas(dia, franjas, libres, otraZona) {
    var grupo = document.createElement('div');
    grupo.className = 'franjas franjas--oculta';
    grupo.setAttribute('data-franjas-de', dia);
    var html = '<div class="franjas__cabecera"><p class="franjas__dia">' + textoDia(dia) + '</p><p class="franjas__conteo">' + libres + ' de ' + franjas.length + ' libres</p></div>';
    var periodos = [['Madrugada', 0, 5], ['Mañana', 5, 12], ['Tarde', 12, 18], ['Noche', 18, 24]];
    periodos.forEach(function (periodo) {
      var lista = franjas.filter(function (f) { return f.h >= periodo[1] && f.h < periodo[2]; });
      if (!lista.length) return;
      html += '<div><p class="franjas__grupo-titulo">' + periodo[0] + '</p><div class="franjas__lista">';
      lista.forEach(function (f) {
        var local = texto12(f.h, f.m);
        var hc = f.hora.split(':').map(Number);
        var colombia = texto12(hc[0], hc[1]);
        var otroDia = otraZona && f.fechaCol !== dia;
        var completo = colombia + (otroDia ? ' del ' + textoDia(f.fechaCol).toLowerCase() : '') + ' en Colombia';
        var detalle = otraZona ? '<small class="franja__colombia" title="' + completo + '" aria-hidden="true">' + (otroDia ? textoDia(f.fechaCol).slice(0, 3) + ' ' : '') + colombia + ' COL</small><span class="solo-lector">, ' + completo + '</span>' : '';
        if (f.estado === 'tuya') {
          html += '<label class="franja franja--ocupada franja--tuya" title="Ya tienes una cita a esta hora"><input class="control-oculto" type="radio" name="hora" value="' + f.hora + '" disabled>' + local + '<span class="franja__estado">' + f.nota + '</span></label>';
        } else if (f.estado === 'ocupada') {
          html += '<label class="franja franja--ocupada"><input class="control-oculto" type="radio" name="hora" value="' + f.hora + '" disabled>' + local + detalle + '<span class="solo-lector">, ocupada</span></label>';
        } else {
          html += '<label class="franja' + (otraZona ? ' franja--doble' : '') + '"><input class="control-oculto" type="radio" name="hora" value="' + f.hora + '" data-fecha="' + f.fechaCol + '" required' +
            ' data-texto="' + local + '" data-texto-colombia="' + colombia + '" data-otro-dia="' + (otroDia ? textoDia(f.fechaCol) : '') + '" data-mensaje-vacio="Elige la hora de tu cita." aria-describedby="hora-mensaje">' + local + detalle + '</label>';
        }
      });
      html += '</div></div>';
    });
    grupo.innerHTML = html;
    return grupo;
  }

  /* ---------- Meses ---------- */
  function mostrarMes(indice) {
    mesVisible = Math.max(0, Math.min(indice, meses.length - 1));
    meses.forEach(function (mes, i) { mes.classList.toggle('calendario__mes--oculto', i !== mesVisible); });
    tituloMes.textContent = meses[mesVisible].getAttribute('data-titulo-mes');
    botonAnterior.disabled = mesVisible === 0;
    botonSiguiente.disabled = mesVisible === meses.length - 1;
  }
  botonAnterior.addEventListener('click', function () { mostrarMes(mesVisible - 1); });
  botonSiguiente.addEventListener('click', function () { mostrarMes(mesVisible + 1); });

  /* ---------- Día y horarios ---------- */
  function mostrarHorariosDe(fecha) {
    horarios.querySelectorAll('[data-franjas-de]').forEach(function (grupo) {
      grupo.classList.toggle('franjas--oculta', grupo.getAttribute('data-franjas-de') !== fecha);
    });
    avisoSinDia.hidden = !!fecha;
  }

  function elegido(nombre) { return formulario.querySelector('[name="' + nombre + '"]:checked'); }

  function actualizarResumen() {
    var dia = elegido('dia');
    var hora = elegido('hora');
    var modalidad = elegido('modalidad');
    fechaColombia.value = hora ? hora.getAttribute('data-fecha') : '';
    var texto = 'Sin elegir';
    var extra = '';
    if (dia) {
      texto = dia.getAttribute('data-texto') + ' · ' + (hora ? hora.getAttribute('data-texto') : 'falta la hora');
      if (hora && zonaActual() !== COLOMBIA) {
        var otroDia = hora.getAttribute('data-otro-dia');
        texto += ', tu hora';
        extra = 'En Colombia: ' + (otroDia ? otroDia.toLowerCase() + ', ' : '') + hora.getAttribute('data-texto-colombia');
      }
    }
    resumenFecha.textContent = texto;
    if (extra) {
      var pequeno = document.createElement('small');
      pequeno.className = 'resumen-cita__extra';
      pequeno.textContent = extra;
      resumenFecha.appendChild(pequeno);
    }
    resumenFecha.classList.toggle('resumen-cita__valor--pendiente', !(dia && hora));
    resumenModalidad.textContent = modalidad ? modalidad.getAttribute('data-texto') : 'Sin elegir';
    resumenModalidad.classList.toggle('resumen-cita__valor--pendiente', !modalidad);
    var necesidad = descripcion.value.trim();
    resumenDescripcion.textContent = necesidad || 'Sin escribir';
    resumenDescripcion.title = necesidad;
    resumenDescripcion.classList.toggle('resumen-cita__valor--pendiente', !necesidad);
  }

  function actualizarTodo() { actualizarResumen(); window.CoreTech.actualizarEnlacesDePaso(); }

  // La descripción se escribe: el resumen y el enlace "Continuar" se actualizan mientras tanto
  descripcion.addEventListener('input', actualizarTodo);

  formulario.addEventListener('change', function (evento) {
    var nombre = evento.target.name;
    if (nombre === 'pais' || nombre === 'zona') {
      if (nombre === 'pais') llenarZonas(selectPais.value);
      construir();   // al cambiar de zona se borran día y hora: los horarios cambian
    }
    if (nombre === 'dia') {
      // Al cambiar de día se borra la hora elegida: cada día tiene sus propios horarios
      formulario.querySelectorAll('[name="hora"]:checked').forEach(function (hora) { hora.checked = false; });
      mostrarHorariosDe(evento.target.value);
    }
    actualizarTodo();
  });

  /* ---------- Recuperar lo elegido al volver desde el paso 3 ---------- */
  var parametros = window.CoreTech.parametros;
  if (parametros.get('pais') && D.paises[parametros.get('pais')]) selectPais.value = parametros.get('pais');
  llenarZonas(selectPais.value, parametros.get('zona'));
  construir();

  var horaGuardada = parametros.get('fecha') && parametros.get('hora') &&
    formulario.querySelector('[name="hora"][value="' + parametros.get('hora') + '"][data-fecha="' + parametros.get('fecha') + '"]:not(:disabled)');
  if (horaGuardada) {
    var diaLocal = horaGuardada.closest('[data-franjas-de]').getAttribute('data-franjas-de');
    var radioDia = formulario.querySelector('[name="dia"][value="' + diaLocal + '"]');
    radioDia.checked = true;
    horaGuardada.checked = true;
    mostrarMes(meses.indexOf(radioDia.closest('[data-mes]')));
    mostrarHorariosDe(diaLocal);
  }
  var modalidadGuardada = parametros.get('modalidad') && formulario.querySelector('[name="modalidad"][value="' + parametros.get('modalidad') + '"]');
  if (modalidadGuardada) modalidadGuardada.checked = true;
  if (parametros.get('descripcion')) {
    descripcion.value = parametros.get('descripcion');
    descripcion.dispatchEvent(new Event('input')); // actualiza el contador de caracteres
  }
  actualizarTodo();
})();
