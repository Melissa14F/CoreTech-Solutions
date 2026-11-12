/* ==========================================================================
   Vista: Detalle del proyecto del cliente
   - Abre y cierra los formularios "Enviar mensaje" y "Solicitar reunión".
   - Un mensaje enviado se agrega a la conversación de la tarjeta lateral.
   - Al enviar la solicitud, muestra su estado y oculta el botón de reunión
     (RN-15: una sola solicitud abierta por proyecto).
   - Mensajes y solicitud se recuerdan mientras dure la sesión del navegador.
   - Si una fase está entregada, el cliente la aprueba (empieza la siguiente)
     o pide cambios (la fase vuelve a trabajo y Andrés registra una corrección).
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var dialogo = document.getElementById('dialogo-reunion');
  if (!dialogo) return;
  var formulario = dialogo.querySelector('form');
  var codigoProyecto = document.querySelector('[data-proyecto]').getAttribute('data-proyecto');
  var claveSolicitud = 'coretech-solicitud-' + codigoProyecto;
  var bloqueSolicitar = document.querySelector('[data-bloque-solicitar]');
  var estadoSolicitud = document.querySelector('[data-solicitud-enviada]');

  function textoOpcion(nombre) {
    var control = formulario.querySelector('[name="' + nombre + '"]');
    return control && control.value ? control.options[control.selectedIndex].text : '';
  }

  function mostrarSolicitud(datos) {
    // El bloque sigue visible para poder enviar mensajes; solo se oculta el botón de reunión
    bloqueSolicitar.querySelector('[data-abrir-reunion]').hidden = true;
    bloqueSolicitar.querySelector('.solicitar-reunion__texto').innerHTML = '<strong>¿Necesitas algo más?</strong>Escríbele un mensaje a tu desarrollador mientras responde tu solicitud.';
    estadoSolicitud.hidden = false;
    var resumen = estadoSolicitud.querySelector('[data-resumen-solicitud]');
    resumen.innerHTML = '';
    [['Motivo', datos.motivo], ['Modalidad', datos.modalidad], ['Opción 1', datos.opcion1], ['Opción 2', datos.opcion2]].forEach(function (fila) {
      if (!fila[1]) return;
      var linea = document.createElement('span');
      linea.textContent = fila[0] + ': ' + fila[1];
      resumen.appendChild(linea);
    });
  }

  // Si ya se envió una solicitud en esta sesión, se muestra su estado
  try {
    var guardada = JSON.parse(sessionStorage.getItem(claveSolicitud) || 'null');
    if (guardada) mostrarSolicitud(guardada);
  } catch (e) { /* sin almacenamiento */ }

  document.querySelectorAll('[data-abrir-reunion]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      dialogo.showModal();
      formulario.querySelector('select, textarea, input').focus();
    });
  });
  dialogo.querySelectorAll('[data-cerrar-dialogo]').forEach(function (boton) {
    boton.addEventListener('click', function () { dialogo.close(); });
  });

  // principal.js valida el formulario y, si está completo, avisa con "formulario-valido"
  formulario.addEventListener('formulario-valido', function () {
    var enviar = formulario.querySelector('[type="submit"]');
    enviar.classList.add('esta-cargando');
    setTimeout(function () {
      enviar.classList.remove('esta-cargando');
      var datos = {
        motivo: textoOpcion('motivo'),
        modalidad: (formulario.querySelector('[name="modalidad"]:checked') || {}).value,
        opcion1: textoOpcion('dia_1') + ' · ' + textoOpcion('hora_1'),
        opcion2: textoOpcion('dia_2') ? textoOpcion('dia_2') + (textoOpcion('hora_2') ? ' · ' + textoOpcion('hora_2') : '') : ''
      };
      try { sessionStorage.setItem(claveSolicitud, JSON.stringify(datos)); } catch (e) { /* sin almacenamiento */ }
      dialogo.close();
      formulario.reset();
      mostrarSolicitud(datos);
      estadoSolicitud.scrollIntoView({ behavior: 'smooth', block: 'center' });
      window.CoreTech.mostrarNotificacion('Solicitud enviada a Andrés Pérez. Te avisaremos cuando la programe.');
    }, 800);
  });

  /* ---------- Mensajes al desarrollador ---------- */
  var dialogoMensaje = document.getElementById('dialogo-mensaje');
  var formularioMensaje = dialogoMensaje.querySelector('form');
  var lista = document.querySelector('[data-lista-mensajes]');
  var avisoVacio = document.querySelector('[data-mensajes-vacio]');
  var claveMensajes = 'coretech-mensajes-' + codigoProyecto;

  function agregarMensaje(datos, nuevo) {
    var item = document.createElement('li');
    item.className = 'mensaje mensaje--tuyo' + (nuevo ? ' mensaje--nuevo' : '');
    var meta = document.createElement('p');
    meta.className = 'mensaje__meta';
    var autor = document.createElement('span');
    autor.className = 'mensaje__autor';
    autor.textContent = 'Tú';
    var cuando = document.createElement('span');
    cuando.textContent = datos.fecha + ' · ' + datos.tema;
    meta.append(autor, cuando);
    var texto = document.createElement('p');
    texto.className = 'mensaje__texto';
    texto.textContent = datos.mensaje;
    item.append(meta, texto);
    lista.appendChild(item);
    avisoVacio.hidden = true;
    lista.scrollTop = lista.scrollHeight;
  }

  function leerMensajes() {
    try { return JSON.parse(sessionStorage.getItem(claveMensajes) || '[]'); } catch (e) { return []; }
  }
  leerMensajes().forEach(function (datos) { agregarMensaje(datos, false); });

  document.querySelectorAll('[data-abrir-mensaje]').forEach(function (boton) {
    boton.addEventListener('click', function () {
      dialogoMensaje.showModal();
      formularioMensaje.querySelector('select').focus();
    });
  });
  dialogoMensaje.querySelectorAll('[data-cerrar-dialogo]').forEach(function (boton) {
    boton.addEventListener('click', function () { dialogoMensaje.close(); });
  });

  formularioMensaje.addEventListener('formulario-valido', function () {
    var enviar = formularioMensaje.querySelector('[type="submit"]');
    enviar.classList.add('esta-cargando');
    setTimeout(function () {
      enviar.classList.remove('esta-cargando');
      var datos = {
        tema: formularioMensaje.querySelector('[name="tema"]').value,
        mensaje: formularioMensaje.querySelector('[name="mensaje"]').value.trim(),
        fecha: 'Ahora'
      };
      var guardados = leerMensajes();
      guardados.push(datos);
      try { sessionStorage.setItem(claveMensajes, JSON.stringify(guardados)); } catch (e) { /* sin almacenamiento */ }
      dialogoMensaje.close();
      formularioMensaje.reset();
      formularioMensaje.querySelector('[name="mensaje"]').dispatchEvent(new Event('input')); // reinicia el contador
      agregarMensaje(datos, true);
      lista.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      window.CoreTech.mostrarNotificacion('Mensaje enviado a Andrés Pérez. Te avisaremos cuando responda.');
    }, 700);
  });

  /* ---------- Aprobar la fase terminada o pedir cambios ---------- */
  var caja = document.querySelector('[data-revision-fase]');
  if (!caja) return;
  var faseRevision = caja.closest('.fase');
  var dialogoAprobar = document.getElementById('dialogo-aprobar');
  var dialogoCambios = document.getElementById('dialogo-cambios');
  var formularioCambios = dialogoCambios.querySelector('form');
  var ICONO_CHECK = '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';
  var ICONO_LLAVE = '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>';

  document.querySelector('[data-abrir-aprobar]').addEventListener('click', function () { dialogoAprobar.showModal(); });
  document.querySelector('[data-abrir-cambios]').addEventListener('click', function () {
    dialogoCambios.showModal();
    formularioCambios.querySelector('textarea').focus();
  });
  [dialogoAprobar, dialogoCambios].forEach(function (d) {
    d.querySelectorAll('[data-cerrar-dialogo]').forEach(function (b) { b.addEventListener('click', function () { d.close(); }); });
  });

  // Aprobar: la fase suma su 20 % y empieza la siguiente
  dialogoAprobar.querySelector('[data-confirmar-aprobar]').addEventListener('click', function () {
    var boton = this;
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      dialogoAprobar.close();
      var nombre = faseRevision.querySelector('.fase__nombre').textContent;
      var siguienteNombre = caja.getAttribute('data-siguiente');
      var bloqueHablar = faseRevision.querySelectorAll('[data-bloque-solicitar], [data-solicitud-enviada]');
      var numero = Number(faseRevision.getAttribute('data-fase-numero'));

      caja.remove();
      faseRevision.querySelector('.pasos-fase').insertAdjacentHTML('afterend', '<p class="fase__aprobacion">' + ICONO_CHECK + 'Aprobaste esta fase hoy.</p>');
      faseRevision.classList.remove('fase--actual', 'fase--revision');
      faseRevision.classList.add('fase--completa');
      faseRevision.removeAttribute('aria-current');
      faseRevision.querySelector('.fase__marcador').innerHTML = ICONO_CHECK + '<span class="solo-lector">Fase ' + numero + ' aprobada</span>';
      var insignia = faseRevision.querySelector('.insignia');
      insignia.className = 'insignia insignia--finalizado';
      insignia.textContent = 'Aprobada';

      // El proyecto suma 20 %
      var anillo = document.querySelector('[data-anillo-general]');
      var general = Math.min(100, parseInt(anillo.textContent, 10) + 20);
      anillo.style.setProperty('--valor', general);
      anillo.setAttribute('aria-label', 'Avance general ' + general + ' %');
      anillo.querySelector('.anillo__valor').textContent = general + ' %';

      var siguiente = faseRevision.nextElementSibling;
      if (siguiente && siguienteNombre) {
        var pasos = siguiente.querySelectorAll('.paso-fase').length;
        siguiente.classList.remove('fase--pendiente');
        siguiente.classList.add('fase--actual');
        siguiente.setAttribute('aria-current', 'step');
        siguiente.querySelector('.fase__marcador').innerHTML = (numero + 1) + '<span class="solo-lector">, fase actual</span>';
        siguiente.querySelector('.fase__fechas').textContent = 'Desde hoy · 0 de ' + pasos + ' pasos hechos';
        var insigniaSiguiente = siguiente.querySelector('.insignia');
        insigniaSiguiente.className = 'insignia insignia--activo';
        insigniaSiguiente.textContent = 'En curso';
        siguiente.querySelector('.pasos-fase__titulo').textContent = 'Pasos';
        var primero = siguiente.querySelector('.paso-fase');
        if (primero) {
          primero.classList.remove('paso-fase--pendiente');
          primero.classList.add('paso-fase--en-curso');
          primero.querySelector('.paso-fase__cuerpo').insertAdjacentHTML('beforeend', '<span class="paso-fase__estado">En curso</span>');
        }
        var cuerpo = siguiente.querySelector('.fase__cuerpo');
        bloqueHablar.forEach(function (bloque) { cuerpo.appendChild(bloque); });
        siguiente.querySelector('details').open = true;
        var texto = document.querySelector('.solicitar-reunion__texto');
        if (texto) texto.innerHTML = texto.innerHTML.replace(nombre, siguienteNombre);
        document.querySelector('[data-general-fase]').textContent = 'Fase ' + (numero + 1) + ' de 5';
        document.querySelector('[data-general-detalle]').textContent = siguienteNombre + ' · 0 de ' + pasos + ' pasos hechos';
        siguiente.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      var estado = document.querySelector('[data-estado-proyecto]');
      estado.className = 'insignia insignia--activo';
      estado.textContent = 'En curso';
      window.CoreTech.mostrarNotificacion(nombre + ' aprobada. Tu proyecto va en ' + general + ' %.');
    }, 700);
  });

  // Pedir cambios:
  //  - Ajuste: usa una de las rondas incluidas; queda como corrección y la fase vuelve a trabajo.
  //  - Algo nuevo (cambio de alcance): queda por cotizar; la fase se puede aprobar igual.
  var rondas = Number(caja.getAttribute('data-rondas'));
  formularioCambios.addEventListener('formulario-valido', function () {
    var enviar = formularioCambios.querySelector('[type="submit"]');
    enviar.classList.add('esta-cargando');
    setTimeout(function () {
      enviar.classList.remove('esta-cargando');
      dialogoCambios.close();
      var tipo = formularioCambios.querySelector('[name="tipo_cambio"]:checked').value;
      var texto = formularioCambios.querySelector('textarea').value.trim();
      var ajuste = tipo === 'ajuste';
      var paso = document.createElement('li');
      paso.className = 'paso-fase paso-fase--pendiente paso-fase--correccion esta-nuevo' + (ajuste ? '' : ' paso-fase--alcance');
      paso.innerHTML = '<span class="paso-fase__marca" aria-hidden="true"></span><div class="paso-fase__cuerpo">' +
        '<span class="paso-fase__etiqueta">' + ICONO_LLAVE + (ajuste ? 'Ajuste · Lo pediste hoy' : 'Cambio de alcance · por cotizar') + '</span>' +
        '<p class="paso-fase__titulo">' + (ajuste ? 'Ajuste que pediste' : 'Función nueva que pediste') + '<span class="solo-lector">, pendiente</span></p><p class="paso-fase__explicacion"></p>' +
        (ajuste ? '' : '<p class="paso-fase__nota"><strong>Siguiente:</strong> Andrés te enviará el costo y cuántos días agrega a la entrega. Solo se hace si lo aceptas.</p>') + '</div>';
      paso.querySelector('.paso-fase__explicacion').textContent = texto;
      faseRevision.querySelector('.pasos-fase').appendChild(paso);
      formularioCambios.reset();
      formularioCambios.querySelector('textarea').dispatchEvent(new Event('input'));

      if (ajuste) {
        rondas -= 1;
        caja.classList.add('revision-fase--espera');
        caja.querySelector('.revision-fase__acciones').remove();
        caja.querySelector('.revision-fase__texto').innerHTML =
          '<strong>Enviaste tu ajuste. La fase volvió a trabajo.</strong>' +
          '<p>Andrés lo hará y te la entregará de nuevo para que la apruebes. Te quedan ' + rondas + ' de 2 rondas de ajustes incluidas.</p>';
        faseRevision.classList.remove('fase--revision');
        var insignia = faseRevision.querySelector('.insignia');
        insignia.className = 'insignia insignia--pendiente';
        insignia.textContent = 'En ajustes';
        var estado = document.querySelector('[data-estado-proyecto]');
        estado.className = 'insignia insignia--pendiente';
        estado.textContent = 'En ajustes';
      }
      var conteo = faseRevision.querySelector('[data-conteo-pasos]');
      if (conteo) conteo.textContent = faseRevision.querySelectorAll('.paso-fase--hecho').length + ' de ' + faseRevision.querySelectorAll('.paso-fase').length + ' pasos hechos';
      paso.scrollIntoView({ behavior: 'smooth', block: 'center' });
      window.CoreTech.mostrarNotificacion(ajuste
        ? 'Ajuste enviado a Andrés Pérez. Te avisaremos cuando vuelva a entregar la fase.'
        : 'Solicitud enviada. Andrés te enviará el costo y el tiempo antes de hacerla.');
    }, 700);
  });
})();
