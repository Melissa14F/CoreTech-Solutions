/* ==========================================================================
   Vista: Registrar trabajo (desarrollador/registrar-avance-*.html)
   Modelo:
   - 5 fases de 20 % cada una. El proyecto suma 20 % por cada fase terminada y
     aprobada por el cliente. No hay porcentajes dentro de una fase.
   - Paso hecho: marca con ✓ un paso de la fase actual y deja una nota sencilla.
   - Corrección: se agrega como paso en la fase que se corrige. Si esa fase ya
     estaba aprobada, deja de sumar su 20 % hasta que se termine la corrección;
     mientras tanto se sigue trabajando en la fase actual.
   - La fase se entrega cuando todos sus pasos están hechos y no hay correcciones
     abiertas en fases aprobadas. Nada de lo publicado se borra (RNF-02).
   Si la fase ya está entregada, la página no tiene formulario y no hace nada.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var formulario = document.querySelector('[data-formulario-avance]');
  if (!formulario) return;

  var FASES = ['Toma de requerimientos', 'Análisis y diseño', 'Desarrollo', 'Pruebas', 'Despliegue'];
  var ICONO = {
    reloj: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
    check: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>',
    llave: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>'
  };

  var raiz = document.querySelector('[data-registro-avance]');
  var cliente = raiz.getAttribute('data-cliente');
  var VALOR = Number(raiz.getAttribute('data-valor'));
  var faseActual = Number(raiz.getAttribute('data-fase'));
  var totalProyecto = Number(raiz.getAttribute('data-total'));
  var apruebaCliente = raiz.getAttribute('data-aprueba').split(',').map(Number);   // fases que aprueba el cliente
  var plazoDias = raiz.getAttribute('data-plazo');
  function laApruebaCliente() { return apruebaCliente.indexOf(faseActual) !== -1; }
  var cantidad = Number(document.querySelector('[data-dato-cantidad]').textContent);

  var selectPaso = document.getElementById('registro-paso');
  var selectFase = document.getElementById('correccion-fase');
  var motivo = document.getElementById('avance-motivo');
  var titulo = document.getElementById('correccion-titulo');
  var descripcion = document.getElementById('avance-descripcion');
  var dialogo = document.getElementById('dialogo-publicar');
  var botonEntregar = document.querySelector('[data-entregar]');
  var tipo = 'paso';
  var consecutivo = 1;
  var accionPendiente = null;   // 'publicar' o 'entregar'

  function faseEl(n) { return document.querySelector('.fase[data-fase-numero="' + n + '"]'); }
  function hoy() { return new Date().toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }).replace('.', ''); }
  function estaAprobada(n) { var f = faseEl(n); return f.classList.contains('fase--completa') && !f.classList.contains('fase--con-correccion'); }

  /* ---------- Tipo de registro ---------- */
  function cambiarTipo(nuevo) {
    tipo = nuevo;
    var correccion = tipo === 'correccion';
    document.querySelector('[data-solo-paso]').hidden = correccion;
    document.querySelector('[data-solo-correccion]').hidden = !correccion;
    selectPaso.required = !correccion;
    motivo.required = correccion;
    titulo.required = correccion;
    document.querySelector('[data-etiqueta-descripcion]').textContent = correccion
      ? 'Explícale a ' + cliente + ' qué se va a corregir y por qué'
      : 'Nota para ' + cliente + ': ¿qué se hizo?';
    document.querySelector('[data-boton-publicar] span').textContent = correccion ? 'Agregar corrección' : 'Marcar como hecho';
    formulario.querySelectorAll('.campo').forEach(function (c) { c.classList.remove('campo--invalido'); });
    pintarImpacto();
  }
  document.querySelectorAll('[data-tipo-registro]').forEach(function (opcion) {
    opcion.addEventListener('change', function () { if (opcion.checked) cambiarTipo(opcion.value); });
  });
  selectFase.addEventListener('change', pintarImpacto);
  selectPaso.addEventListener('change', pintarImpacto);

  function pintarImpacto() {
    var impacto = document.querySelector('[data-impacto]');
    if (tipo === 'paso') {
      var faltan = selectPaso.options.length - 1;
      impacto.innerHTML = faltan > 0
        ? 'Después de este paso ' + (faltan === 1 ? 'quedará <strong>1</strong> paso' : 'quedarán <strong>' + faltan + '</strong> pasos') + ' por hacer en ' + FASES[faseActual - 1] + '.'
        : 'Es el último paso: después podrás <strong>entregar la fase</strong> a ' + cliente + '.';
      return;
    }
    var n = Number(selectFase.value);
    impacto.innerHTML = estaAprobada(n)
      ? FASES[n - 1] + ' ya estaba aprobada: dejará de sumar su ' + VALOR + ' % mientras la corriges. Proyecto: <strong>' + totalProyecto + ' %</strong> → <strong>' + (totalProyecto - VALOR) + ' %</strong>.'
      : 'Se agrega como un paso más de ' + FASES[n - 1] + '. El porcentaje del proyecto no cambia.';
  }

  /* ---------- Confirmación ---------- */
  formulario.addEventListener('formulario-valido', function () {
    accionPendiente = 'publicar';
    var texto;
    if (tipo === 'paso') {
      document.querySelector('[data-titulo-publicar]').textContent = '¿Marcar el paso como hecho?';
      texto = '«' + selectPaso.options[selectPaso.selectedIndex].text.replace(' (en curso)', '') + '» quedará con ✓ y ' + cliente + ' verá tu nota.';
    } else {
      var n = Number(selectFase.value);
      document.querySelector('[data-titulo-publicar]').textContent = '¿Agregar la corrección?';
      texto = 'Se agregará «' + titulo.value.trim() + '» en ' + FASES[n - 1] + '.' +
        (estaAprobada(n) ? ' La fase dejará de sumar su ' + VALOR + ' % hasta que la termines.' : '');
    }
    document.querySelector('[data-texto-publicar]').textContent = texto + ' No se podrá editar ni borrar.';
    dialogo.showModal();
  });
  dialogo.querySelector('[data-cerrar-dialogo]').addEventListener('click', function () { dialogo.close(); });
  dialogo.querySelector('[data-confirmar-publicar]').addEventListener('click', function () {
    var boton = this;
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      dialogo.close();
      if (accionPendiente === 'entregar') entregarFase();
      else if (tipo === 'paso') marcarPaso(selectPaso.value, descripcion.value.trim());
      else agregarCorreccion(Number(selectFase.value), titulo.value.trim(), descripcion.value.trim(), motivo.value);
    }, 700);
  });

  /* ---------- Pasos ---------- */
  function ponerNota(paso, nota) {
    var cuerpo = paso.querySelector('.paso-fase__cuerpo');
    var estado = cuerpo.querySelector('.paso-fase__estado');
    if (estado) estado.remove();
    var p = document.createElement('p');
    p.className = 'paso-fase__nota';
    p.innerHTML = '<strong>Tu nota:</strong> ';
    p.appendChild(document.createTextNode(nota));
    cuerpo.appendChild(p);
    cuerpo.insertAdjacentHTML('beforeend', '<span class="paso-fase__estado">Hecho el ' + hoy() + '</span>');
  }

  function marcarHecho(paso, nota) {
    paso.classList.remove('paso-fase--pendiente', 'paso-fase--en-curso');
    paso.classList.add('paso-fase--hecho', 'esta-nuevo');
    paso.querySelector('.paso-fase__marca').innerHTML = ICONO.check;
    var lector = paso.querySelector('.paso-fase__titulo .solo-lector');
    if (lector) lector.textContent = ', hecho';
    ponerNota(paso, nota);
    cantidad += 1;
  }

  function contarPasos(n) {
    var f = faseEl(n);
    var total = f.querySelectorAll('.paso-fase').length;
    var hechos = f.querySelectorAll('.paso-fase--hecho').length;
    var conteo = f.querySelector('[data-conteo-pasos]');
    if (conteo) conteo.textContent = hechos + ' de ' + total + ' pasos hechos';
    return { total: total, hechos: hechos };
  }

  function marcarPaso(id, nota) {
    var fase = faseEl(faseActual);
    var paso = fase.querySelector('[data-paso="' + id + '"]');
    marcarHecho(paso, nota);
    selectPaso.querySelector('option[value="' + id + '"]').remove();
    // El siguiente paso pendiente pasa a "en curso"
    if (!fase.querySelector('.paso-fase--en-curso')) {
      var siguiente = fase.querySelector('.paso-fase--pendiente');
      if (siguiente) {
        siguiente.classList.replace('paso-fase--pendiente', 'paso-fase--en-curso');
        siguiente.querySelector('.paso-fase__cuerpo').insertAdjacentHTML('beforeend', '<span class="paso-fase__estado">En curso</span>');
        var opcion = selectPaso.querySelector('option[value="' + siguiente.getAttribute('data-paso') + '"]');
        if (opcion && opcion.text.indexOf('(en curso)') === -1) opcion.text += ' (en curso)';
      }
    }
    terminarRegistro(paso, '✓ Paso hecho. ' + cliente + ' verá tu nota.');
  }

  /* ---------- Correcciones ---------- */
  function crearPasoCorreccion(id, tituloTexto, explicacion, origen) {
    var li = document.createElement('li');
    li.className = 'paso-fase paso-fase--en-curso paso-fase--correccion esta-nuevo';
    li.setAttribute('data-paso', id);
    li.innerHTML = '<span class="paso-fase__marca" aria-hidden="true"></span><div class="paso-fase__cuerpo">' +
      '<span class="paso-fase__etiqueta">' + ICONO.llave + '</span><p class="paso-fase__titulo"></p>' +
      '<p class="paso-fase__explicacion"></p><span class="paso-fase__estado">En curso</span></div>';
    li.querySelector('.paso-fase__etiqueta').appendChild(document.createTextNode('Corrección · ' + origen + ' el ' + hoy()));
    li.querySelector('.paso-fase__titulo').innerHTML = '<span></span><span class="solo-lector">, en curso</span>';
    li.querySelector('.paso-fase__titulo span').textContent = tituloTexto;
    li.querySelector('.paso-fase__explicacion').textContent = explicacion;
    return li;
  }

  function agregarCorreccion(n, tituloTexto, explicacion, origen) {
    var fase = faseEl(n);
    var id = 'C-' + n + '-' + consecutivo++;
    var aprobada = estaAprobada(n);
    var paso = crearPasoCorreccion(id, tituloTexto, explicacion, origen);
    fase.querySelector('.pasos-fase').appendChild(paso);

    if (n === faseActual) {
      // Es un paso más de la fase actual: se marca con "Paso hecho" cuando termine
      var opcion = document.createElement('option');
      opcion.value = id;
      opcion.text = tituloTexto + ' (corrección)';
      selectPaso.appendChild(opcion);
    } else {
      if (aprobada) {
        totalProyecto -= VALOR;
        fase.classList.add('fase--con-correccion');
        var insignia = fase.querySelector('.insignia');
        insignia.className = 'insignia insignia--pendiente';
        insignia.textContent = 'En corrección';
        fase.querySelector('.fase__marcador').innerHTML = ICONO.llave + '<span class="solo-lector">Fase ' + n + ' en corrección</span>';
        fase.querySelector('.fase__que-es').insertAdjacentHTML('afterend',
          '<p class="fase__aviso-correccion">' + ICONO.llave + '<span>Estamos corrigiendo algo de esta fase. Mientras tanto no suma su ' + VALOR + ' % al proyecto; vuelve a sumarlo al terminar la corrección.</span></p>');
      }
      fase.querySelector('details').open = true;
      agregarAbierta(id, n, tituloTexto);
    }
    contarPasos(n);
    terminarRegistro(paso, aprobada ? 'Corrección agregada. El proyecto queda en ' + totalProyecto + ' % hasta que la termines.' : 'Corrección agregada en ' + FASES[n - 1] + '.');
  }

  var bloqueAbiertas = document.querySelector('[data-correcciones-abiertas]');
  var listaAbiertas = document.querySelector('[data-lista-abiertas]');
  function contarAbiertas() {
    var n = listaAbiertas.children.length;
    document.querySelector('[data-cantidad-abiertas]').textContent = n;
    bloqueAbiertas.hidden = n === 0;
    return n;
  }
  function agregarAbierta(id, n, tituloTexto) {
    var li = document.createElement('li');
    li.className = 'correccion-abierta esta-nuevo';
    li.setAttribute('data-correccion', id);
    li.setAttribute('data-fase', n);
    li.innerHTML = '<div class="correccion-abierta__texto"><strong></strong><small></small></div>' +
      '<button type="button" class="boton boton--contorno boton--pequeno" data-resolver="' + id + '">' + ICONO.check + 'Marcar corregida</button>';
    li.querySelector('strong').textContent = tituloTexto;
    li.querySelector('small').textContent = FASES[n - 1] + ' · abierta hoy';
    listaAbiertas.appendChild(li);
    contarAbiertas();
  }

  // Terminar una corrección de una fase aprobada
  var dialogoResolver = document.getElementById('dialogo-resolver');
  var formularioResolver = dialogoResolver.querySelector('form');
  var porResolver = null;
  listaAbiertas.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-resolver]');
    if (!boton) return;
    porResolver = boton.closest('[data-correccion]');
    var n = Number(porResolver.getAttribute('data-fase'));
    document.querySelector('[data-texto-resolver]').textContent = 'Al terminarla, ' + FASES[n - 1] + ' vuelve a sumar su ' + VALOR + ' % al proyecto.';
    dialogoResolver.showModal();
    formularioResolver.querySelector('textarea').focus();
  });
  dialogoResolver.querySelectorAll('[data-cerrar-dialogo]').forEach(function (b) {
    b.addEventListener('click', function () { dialogoResolver.close(); });
  });
  formularioResolver.addEventListener('formulario-valido', function () {
    var enviar = formularioResolver.querySelector('[type="submit"]');
    enviar.classList.add('esta-cargando');
    setTimeout(function () {
      enviar.classList.remove('esta-cargando');
      dialogoResolver.close();
      var id = porResolver.getAttribute('data-correccion');
      var n = Number(porResolver.getAttribute('data-fase'));
      var fase = faseEl(n);
      var paso = fase.querySelector('[data-paso="' + id + '"]');
      marcarHecho(paso, formularioResolver.querySelector('textarea').value.trim());
      porResolver.remove();
      contarAbiertas();
      // Si ya no le quedan correcciones, la fase vuelve a sumar su 20 %
      if (!fase.querySelector('.paso-fase--correccion:not(.paso-fase--hecho)') && fase.classList.contains('fase--con-correccion')) {
        totalProyecto += VALOR;
        fase.classList.remove('fase--con-correccion');
        var insignia = fase.querySelector('.insignia');
        insignia.className = 'insignia insignia--finalizado';
        insignia.textContent = 'Aprobada';
        fase.querySelector('.fase__marcador').innerHTML = ICONO.check + '<span class="solo-lector">Fase ' + n + ' aprobada</span>';
        var aviso = fase.querySelector('.fase__aviso-correccion');
        if (aviso) aviso.remove();
      }
      contarPasos(n);
      formularioResolver.reset();
      terminarRegistro(paso, 'Corrección terminada. El proyecto vuelve a ' + totalProyecto + ' %.');
    }, 700);
  });

  /* ---------- Entregar la fase ---------- */
  function revisarEntrega() {
    var pasos = contarPasos(faseActual);
    var faltan = pasos.total - pasos.hechos;
    var abiertas = contarAbiertas();
    var texto = document.querySelector('[data-texto-entrega]');
    if (faltan > 0) texto.textContent = faltan === 1 ? 'Falta 1 paso por terminar.' : 'Faltan ' + faltan + ' pasos por terminar.';
    else if (abiertas > 0) texto.textContent = 'Termina primero las correcciones de fases aprobadas.';
    else texto.textContent = laApruebaCliente()
      ? 'Todo listo: ' + cliente + ' tendrá ' + plazoDias + ' días hábiles para revisarla y aprobarla.'
      : 'Todo listo: la cierras tú y el proyecto suma ' + VALOR + ' %. ' + cliente + ' verá lo hecho.';
    botonEntregar.disabled = faltan > 0 || abiertas > 0;
  }
  botonEntregar.addEventListener('click', function () {
    accionPendiente = 'entregar';
    var siguiente = FASES[faseActual] ? ' y empieza ' + FASES[faseActual] + '.' : ' y el proyecto queda terminado.';
    if (laApruebaCliente()) {
      document.querySelector('[data-titulo-publicar]').textContent = '¿Entregar ' + FASES[faseActual - 1] + '?';
      document.querySelector('[data-texto-publicar]').textContent = cliente + ' tendrá ' + plazoDias + ' días hábiles para revisarla. Cuando la apruebe, el proyecto suma ' + VALOR + ' %' + siguiente;
    } else {
      document.querySelector('[data-titulo-publicar]').textContent = '¿Cerrar ' + FASES[faseActual - 1] + '?';
      document.querySelector('[data-texto-publicar]').textContent = 'Esta fase la cierra el equipo: el proyecto suma ' + VALOR + ' %' + siguiente + ' ' + cliente + ' verá todo lo hecho.';
    }
    dialogo.showModal();
  });

  function entregarFase() {
    if (!laApruebaCliente()) { cerrarFase(); return; }
    var fase = faseEl(faseActual);
    fase.classList.add('fase--revision');
    var insignia = fase.querySelector('.insignia');
    insignia.className = 'insignia insignia--pendiente';
    insignia.textContent = 'Esperando a ' + cliente;
    var tarjeta = document.querySelector('.formulario-avance');
    tarjeta.classList.add('esta-cerrado');
    tarjeta.querySelectorAll('input, select, textarea, button').forEach(function (c) { c.disabled = true; });
    tarjeta.querySelector('.formulario-avance__cabecera').insertAdjacentHTML('afterend',
      '<div class="alerta alerta--advertencia formulario-avance__espera" role="status">' + ICONO.reloj +
      '<div><strong>Fase terminada · esperando la aprobación de ' + cliente + '</strong>' +
      '<p>Tiene ' + plazoDias + ' días hábiles para responder; si no lo hace, se le recuerda y la fase se da por aprobada. Si pide ajustes, aparecerán como una corrección en esta fase.</p></div></div>');
    document.querySelector('[data-dato-fase]').textContent = FASES[faseActual - 1] + ' · esperando aprobación';
    window.CoreTech.mostrarNotificacion('Fase entregada. ' + cliente + ' recibirá un correo para aprobarla.');
  }

  // Desarrollo y Despliegue los cierra el equipo: suma 20 % y empieza la siguiente fase
  function cerrarFase() {
    var fase = faseEl(faseActual);
    var nombre = FASES[faseActual - 1];
    fase.classList.remove('fase--actual');
    fase.classList.add('fase--completa');
    fase.removeAttribute('aria-current');
    fase.querySelector('.fase__marcador').innerHTML = ICONO.check + '<span class="solo-lector">Fase ' + faseActual + ' cerrada</span>';
    var insignia = fase.querySelector('.insignia');
    insignia.className = 'insignia insignia--finalizado';
    insignia.textContent = 'Cerrada';
    var bloque = fase.querySelector('.pasos-fase');
    bloque.insertAdjacentHTML('afterend', '<p class="fase__aprobacion">' + ICONO.check + 'Cerraste esta fase hoy.</p>');
    totalProyecto += VALOR;

    if (faseActual === 5) {
      var tarjetaFinal = document.querySelector('.formulario-avance');
      tarjetaFinal.classList.add('esta-cerrado');
      tarjetaFinal.querySelectorAll('input, select, textarea, button').forEach(function (c) { c.disabled = true; });
      document.querySelector('[data-dato-general]').textContent = totalProyecto + ' %';
      window.CoreTech.mostrarNotificacion('Proyecto terminado. ' + cliente + ' recibirá un correo.');
      return;
    }

    // La siguiente fase empieza: sus pasos pasan al formulario
    faseActual += 1;
    var siguiente = faseEl(faseActual);
    siguiente.classList.remove('fase--pendiente');
    siguiente.classList.add('fase--actual');
    siguiente.setAttribute('aria-current', 'step');
    siguiente.querySelector('.fase__marcador').innerHTML = faseActual + '<span class="solo-lector">, fase actual</span>';
    var insigniaSiguiente = siguiente.querySelector('.insignia');
    insigniaSiguiente.className = 'insignia insignia--activo';
    insigniaSiguiente.textContent = 'En curso';
    var pasos = siguiente.querySelectorAll('.paso-fase');
    siguiente.querySelector('.fase__fechas').innerHTML = 'Desde hoy · <span data-conteo-pasos>0 de ' + pasos.length + ' pasos hechos</span>';
    siguiente.querySelector('.pasos-fase__titulo').textContent = 'Pasos';
    siguiente.querySelector('details').open = true;
    selectPaso.innerHTML = '';
    pasos.forEach(function (paso, i) {
      if (i === 0) {
        paso.classList.replace('paso-fase--pendiente', 'paso-fase--en-curso');
        paso.querySelector('.paso-fase__cuerpo').insertAdjacentHTML('beforeend', '<span class="paso-fase__estado">En curso</span>');
      }
      var opcion = document.createElement('option');
      opcion.value = paso.getAttribute('data-paso');
      opcion.text = paso.querySelector('.paso-fase__titulo').firstChild.textContent.trim() + (i === 0 ? ' (en curso)' : '');
      selectPaso.appendChild(opcion);
    });
    var opcionFase = document.createElement('option');
    opcionFase.value = faseActual;
    opcionFase.text = FASES[faseActual - 1] + ' (actual)';
    selectFase.appendChild(opcionFase);
    selectFase.querySelector('option[value="' + (faseActual - 1) + '"]').text = nombre + ' (cerrada)';

    document.querySelector('[data-fase-actual-numero]').textContent = faseActual;
    document.querySelector('[data-fase-nombre]').textContent = FASES[faseActual - 1];
    document.querySelector('[data-texto-tipo-paso]').textContent = 'Marca con ✓ un paso de ' + FASES[faseActual - 1] + '.';
    document.querySelector('[data-titulo-entrega]').textContent = laApruebaCliente() ? 'Entregar ' + FASES[faseActual - 1] + ' a ' + cliente : 'Cerrar ' + FASES[faseActual - 1];
    botonEntregar.querySelector('span').textContent = laApruebaCliente() ? 'Entregar fase' : 'Cerrar fase';
    document.querySelector('[data-tipo-registro][value="paso"]').disabled = false;
    document.querySelector('[data-tipo-registro][value="paso"]').checked = true;
    terminarRegistro(siguiente, nombre + ' cerrada. El proyecto va en ' + totalProyecto + ' % y empieza ' + FASES[faseActual - 1] + '.');
  }

  /* ---------- Después de publicar ---------- */
  function terminarRegistro(elemento, mensaje) {
    document.querySelector('[data-dato-general]').textContent = totalProyecto + ' %';
    document.querySelector('[data-dato-cantidad]').textContent = cantidad;
    var pasos = contarPasos(faseActual);
    document.querySelector('[data-dato-fase]').textContent = FASES[faseActual - 1] + ' · ' + pasos.hechos + ' de ' + pasos.total + ' pasos';
    formulario.reset();
    cambiarTipo('paso');
    descripcion.dispatchEvent(new Event('input'));
    formulario.querySelectorAll('.campo').forEach(function (c) { c.classList.remove('campo--invalido', 'campo--valido'); });
    var sinPasos = selectPaso.options.length === 0;
    document.querySelector('[data-tipo-registro][value="paso"]').disabled = sinPasos;
    if (sinPasos) { document.querySelector('[data-tipo-registro][value="correccion"]').checked = true; cambiarTipo('correccion'); }
    revisarEntrega();
    elemento.scrollIntoView({ behavior: 'smooth', block: 'center' });
    window.CoreTech.mostrarNotificacion(mensaje);
  }

  cambiarTipo('paso');
  revisarEntrega();
})();
