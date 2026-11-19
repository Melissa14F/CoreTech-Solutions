/* ==========================================================================
   Editor de fases y pasos (compartido)
   Lo usan la plantilla de fases de cada servicio (admin/plantillas-fases.html)
   y el plan de trabajo de un proyecto (plan-de-trabajo-*.html).

   Reglas:
   - De 2 a 8 fases; al menos una la aprueba el cliente.
   - Cada fase tiene nombre (3 a 60 caracteres, sin repetir) y al menos un paso.
   - Cada fase vale lo mismo: 100 % dividido entre el número de fases.

   Uso:  var editor = CoreTech.editorPlan(contenedor, fases, { cliente: 'Laura' });
         editor.validar()  -> true / false (marca los errores y lleva el foco al primero)
         editor.obtener()  -> copia de las fases
         editor.cargar(fases)
   ========================================================================== */
(function () {
  'use strict';
  var MIN_FASES = 2, MAX_FASES = 8, MAX_PASOS = 12;

  var ICONOS = {
    arriba: '<path d="m18 15-6-6-6 6"/>',
    abajo: '<path d="m6 9 6 6 6-6"/>',
    quitar: '<path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/>',
    mas: '<path d="M12 5v14M5 12h14"/>',
    alerta: '<circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>',
    usuario: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    flecha: '<path d="m9 6 6 6-6 6"/>'
  };
  function icono(nombre, clase) {
    return '<svg class="icono' + (clase ? ' ' + clase : '') + '" viewBox="0 0 24 24" aria-hidden="true">' + ICONOS[nombre] + '</svg>';
  }
  function escapar(texto) {
    return String(texto == null ? '' : texto).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function porcentaje(n) {
    var v = 100 / n;
    return (Math.round(v * 10) / 10).toString().replace('.', ',') + ' %';
  }
  function copiar(fases) { return JSON.parse(JSON.stringify(fases)); }
  function mensaje(id) {
    return '<p class="campo__mensaje" id="' + id + '-mensaje" aria-live="polite">' + icono('alerta') + '<span class="campo__mensaje-texto"></span></p>';
  }

  function editorPlan(contenedor, fasesIniciales, opciones) {
    opciones = opciones || {};
    var cliente = opciones.cliente || 'el cliente';
    var prefijo = opciones.prefijo || 'plan';
    var fases = copiar(fasesIniciales || []);
    var abiertas = [0];
    var oyentes = [];

    contenedor.classList.add('editor-plan');
    contenedor.innerHTML =
      '<div class="editor-plan__resumen" data-resumen-plan aria-live="polite"></div>' +
      '<div class="alerta alerta--error editor-plan__errores" data-errores-plan hidden>' + icono('alerta') + '<div><p class="editor-plan__errores-titulo">Revisa el plan antes de guardar:</p><ul data-lista-errores></ul></div></div>' +
      '<ol class="editor-plan__fases" data-lista-fases></ol>' +
      '<div class="editor-plan__pie"><button type="button" class="boton boton--contorno" data-agregar-fase>' + icono('mas') + 'Agregar fase</button>' +
      '<p class="editor-plan__limite" data-limite-fases></p></div>';
    var lista = contenedor.querySelector('[data-lista-fases]');
    var resumen = contenedor.querySelector('[data-resumen-plan]');
    var cajaErrores = contenedor.querySelector('[data-errores-plan]');
    var botonAgregar = contenedor.querySelector('[data-agregar-fase]');
    var limite = contenedor.querySelector('[data-limite-fases]');

    function avisar() { oyentes.forEach(function (fn) { fn(copiar(fases)); }); }

    function htmlPaso(f, p, paso, total) {
      var id = prefijo + '-f' + f + '-p' + p;
      return '<li class="paso-editor" data-paso="' + p + '">' +
        '<span class="paso-editor__numero" aria-hidden="true">' + (p + 1) + '</span>' +
        '<div class="paso-editor__campos">' +
          '<div class="campo campo--compacto"><label class="solo-lector" for="' + id + '-titulo">Paso ' + (p + 1) + ' de la fase ' + (f + 1) + ': título</label>' +
          '<input class="campo__control" id="' + id + '-titulo" type="text" maxlength="80" placeholder="Qué se hace, en pocas palabras" value="' + escapar(paso.titulo) + '" data-campo-paso="titulo" aria-describedby="' + id + '-titulo-mensaje">' +
          mensaje(id + '-titulo') + '</div>' +
          '<div class="campo campo--compacto"><label class="solo-lector" for="' + id + '-explicacion">Paso ' + (p + 1) + ': explicación para ' + escapar(cliente) + '</label>' +
          '<input class="campo__control paso-editor__explicacion" id="' + id + '-explicacion" type="text" maxlength="160" placeholder="Explicación sencilla para ' + escapar(cliente) + ' (opcional)" value="' + escapar(paso.explicacion) + '" data-campo-paso="explicacion"></div>' +
        '</div>' +
        '<div class="paso-editor__acciones" role="group" aria-label="Acciones del paso ' + (p + 1) + '">' +
          '<button type="button" class="boton-icono boton-icono--mini" data-mover-paso="-1" aria-label="Subir paso ' + (p + 1) + '"' + (p === 0 ? ' disabled' : '') + '>' + icono('arriba') + '</button>' +
          '<button type="button" class="boton-icono boton-icono--mini" data-mover-paso="1" aria-label="Bajar paso ' + (p + 1) + '"' + (p === total - 1 ? ' disabled' : '') + '>' + icono('abajo') + '</button>' +
          '<button type="button" class="boton-icono boton-icono--mini boton-icono--peligro" data-quitar-paso aria-label="Quitar paso ' + (p + 1) + '"' + (total === 1 ? ' disabled' : '') + '>' + icono('quitar') + '</button>' +
        '</div></li>';
    }

    function htmlFase(f, fase) {
      var id = prefijo + '-f' + f;
      var n = fases.length;
      var pasos = fase.pasos.map(function (paso, p) { return htmlPaso(f, p, paso, fase.pasos.length); }).join('');
      return '<li class="fase-editor' + (fase.aprueba ? ' fase-editor--cliente' : '') + '" data-fase="' + f + '">' +
        '<details class="fase-editor__detalle"' + (abiertas.indexOf(f) !== -1 ? ' open' : '') + '>' +
          '<summary class="fase-editor__resumen">' +
            '<span class="fase-editor__numero" aria-hidden="true">' + (f + 1) + '</span>' +
            '<span class="fase-editor__titulo"><strong data-titulo-fase>' + (escapar(fase.nombre) || 'Fase sin nombre') + '</strong>' +
              '<small data-detalle-fase>Fase ' + (f + 1) + ' de ' + n + ' · ' + fase.pasos.length + (fase.pasos.length === 1 ? ' paso' : ' pasos') + ' · vale ' + porcentaje(n) + '</small></span>' +
            '<span class="insignia ' + (fase.aprueba ? 'insignia--aprueba' : 'insignia--neutra') + '" data-quien-fase>' + (fase.aprueba ? icono('usuario') + 'La aprueba ' + escapar(cliente) : 'La cierra el equipo') + '</span>' +
            icono('abajo', 'fase-editor__flecha') +
          '</summary>' +
          '<div class="fase-editor__cuerpo">' +
            '<div class="fase-editor__fila">' +
              '<div class="campo"><label class="campo__etiqueta" for="' + id + '-nombre">Nombre de la fase</label>' +
              '<input class="campo__control" id="' + id + '-nombre" type="text" maxlength="60" value="' + escapar(fase.nombre) + '" data-campo-fase="nombre" aria-describedby="' + id + '-nombre-mensaje">' +
              mensaje(id + '-nombre') + '</div>' +
              '<label class="casilla fase-editor__aprueba"><input type="checkbox" data-campo-fase="aprueba"' + (fase.aprueba ? ' checked' : '') + '><span>La aprueba ' + escapar(cliente) + '<small>Tiene 3 días hábiles para aprobarla o pedir ajustes.</small></span></label>' +
            '</div>' +
            '<div class="campo"><label class="campo__etiqueta" for="' + id + '-que-es">Qué es <span class="campo__opcional">lo verá ' + escapar(cliente) + '</span></label>' +
            '<input class="campo__control" id="' + id + '-que-es" type="text" maxlength="140" value="' + escapar(fase.que_es) + '" data-campo-fase="que_es"></div>' +
            '<fieldset class="pasos-editor"><legend class="campo__etiqueta">Pasos <span class="campo__opcional">' + fase.pasos.length + ' de ' + MAX_PASOS + ' como máximo</span></legend>' +
              '<ol class="pasos-editor__lista">' + pasos + '</ol>' +
              '<p class="campo__mensaje pasos-editor__mensaje" aria-live="polite">' + icono('alerta') + '<span class="campo__mensaje-texto" data-error-pasos></span></p>' +
              '<button type="button" class="boton boton--fantasma boton--pequeno" data-agregar-paso' + (fase.pasos.length >= MAX_PASOS ? ' disabled' : '') + '>' + icono('mas') + 'Agregar paso</button>' +
            '</fieldset>' +
            '<div class="fase-editor__acciones">' +
              '<button type="button" class="boton boton--contorno boton--pequeno" data-mover-fase="-1"' + (f === 0 ? ' disabled' : '') + '>' + icono('arriba') + 'Subir fase</button>' +
              '<button type="button" class="boton boton--contorno boton--pequeno" data-mover-fase="1"' + (f === n - 1 ? ' disabled' : '') + '>' + icono('abajo') + 'Bajar fase</button>' +
              '<button type="button" class="boton boton--contorno boton--pequeno boton--texto-peligro" data-quitar-fase' + (n <= MIN_FASES ? ' disabled title="Un plan necesita al menos 2 fases"' : '') + '>' + icono('quitar') + 'Quitar fase</button>' +
            '</div>' +
          '</div>' +
        '</details></li>';
    }

    function actualizarResumen() {
      var n = fases.length;
      var aprueba = fases.filter(function (f) { return f.aprueba; }).length;
      var pasos = fases.reduce(function (s, f) { return s + f.pasos.length; }, 0);
      resumen.innerHTML =
        '<span class="editor-plan__dato"><strong>' + n + '</strong> fases</span>' +
        '<span class="editor-plan__dato"><strong>' + (n ? porcentaje(n) : '—') + '</strong> vale cada una</span>' +
        '<span class="editor-plan__dato"><strong>' + pasos + '</strong> pasos</span>' +
        '<span class="editor-plan__dato' + (aprueba ? '' : ' editor-plan__dato--error') + '"><strong>' + aprueba + '</strong> las aprueba ' + escapar(cliente) + '</span>' +
        '<span class="editor-plan__barra" aria-hidden="true">' + fases.map(function (f) {
          return '<span class="editor-plan__segmento' + (f.aprueba ? ' editor-plan__segmento--cliente' : '') + '"></span>';
        }).join('') + '</span>';
      botonAgregar.disabled = n >= MAX_FASES;
      limite.textContent = n >= MAX_FASES ? 'Llegaste al máximo de 8 fases.' : 'Entre 2 y 8 fases. Las fases nuevas empiezan con un paso.';
    }

    function pintar(foco) {
      lista.innerHTML = fases.map(function (fase, f) { return htmlFase(f, fase); }).join('');
      actualizarResumen();
      if (!cajaErrores.hidden) validar(true);
      if (foco) {
        var objetivo = lista.querySelector(foco);
        if (objetivo) objetivo.focus();
      }
      avisar();
    }

    function indices(elemento) {
      var li = elemento.closest('[data-fase]');
      var paso = elemento.closest('[data-paso]');
      return { f: li ? Number(li.getAttribute('data-fase')) : -1, p: paso ? Number(paso.getAttribute('data-paso')) : -1 };
    }
    function recordarAbiertas() {
      abiertas = [];
      lista.querySelectorAll('[data-fase]').forEach(function (li) {
        if (li.querySelector('details').open) abiertas.push(Number(li.getAttribute('data-fase')));
      });
    }
    function mover(arreglo, i, d) {
      var j = i + d;
      if (j < 0 || j >= arreglo.length) return false;
      var t = arreglo[i]; arreglo[i] = arreglo[j]; arreglo[j] = t;
      return true;
    }

    lista.addEventListener('input', function (evento) {
      var c = evento.target;
      var ix = indices(c);
      if (c.hasAttribute('data-campo-fase') && c.type !== 'checkbox') {
        fases[ix.f][c.getAttribute('data-campo-fase')] = c.value;
        if (c.getAttribute('data-campo-fase') === 'nombre') {
          c.closest('[data-fase]').querySelector('[data-titulo-fase]').textContent = c.value.trim() || 'Fase sin nombre';
        }
      } else if (c.hasAttribute('data-campo-paso')) {
        fases[ix.f].pasos[ix.p][c.getAttribute('data-campo-paso')] = c.value;
      }
      var campo = c.closest('.campo');
      if (campo && campo.classList.contains('campo--invalido')) validar(true);
      avisar();
    });

    lista.addEventListener('change', function (evento) {
      var c = evento.target;
      if (c.type === 'checkbox' && c.getAttribute('data-campo-fase') === 'aprueba') {
        var ix = indices(c);
        fases[ix.f].aprueba = c.checked;
        var li = c.closest('[data-fase]');
        li.classList.toggle('fase-editor--cliente', c.checked);
        var insignia = li.querySelector('[data-quien-fase]');
        insignia.className = 'insignia ' + (c.checked ? 'insignia--aprueba' : 'insignia--neutra');
        insignia.innerHTML = c.checked ? icono('usuario') + 'La aprueba ' + escapar(cliente) : 'La cierra el equipo';
        actualizarResumen();
        if (!cajaErrores.hidden) validar(true);
        avisar();
      }
    });

    lista.addEventListener('click', function (evento) {
      var b = evento.target.closest('button');
      if (!b || b.disabled) return;
      var ix = indices(b);
      recordarAbiertas();
      if (b.hasAttribute('data-agregar-paso')) {
        fases[ix.f].pasos.push({ titulo: '', explicacion: '' });
        pintar('[data-fase="' + ix.f + '"] [data-paso="' + (fases[ix.f].pasos.length - 1) + '"] [data-campo-paso="titulo"]');
      } else if (b.hasAttribute('data-quitar-paso')) {
        fases[ix.f].pasos.splice(ix.p, 1);
        pintar('[data-fase="' + ix.f + '"] [data-agregar-paso]');
      } else if (b.hasAttribute('data-mover-paso')) {
        var d = Number(b.getAttribute('data-mover-paso'));
        if (mover(fases[ix.f].pasos, ix.p, d)) pintar('[data-fase="' + ix.f + '"] [data-paso="' + (ix.p + d) + '"] [data-mover-paso="' + d + '"]:not([disabled]), [data-fase="' + ix.f + '"] [data-paso="' + (ix.p + d) + '"] [data-campo-paso="titulo"]');
      } else if (b.hasAttribute('data-mover-fase')) {
        var df = Number(b.getAttribute('data-mover-fase'));
        if (mover(fases, ix.f, df)) {
          abiertas = abiertas.map(function (a) { return a === ix.f ? ix.f + df : (a === ix.f + df ? ix.f : a); });
          pintar('[data-fase="' + (ix.f + df) + '"] [data-mover-fase="' + df + '"]:not([disabled]), [data-fase="' + (ix.f + df) + '"] summary');
          CoreTech.mostrarNotificacion('"' + (fases[ix.f + df].nombre || 'Fase') + '" ahora es la fase ' + (ix.f + df + 1) + '.', 'info');
        }
      } else if (b.hasAttribute('data-quitar-fase')) {
        var nombre = fases[ix.f].nombre || 'La fase';
        fases.splice(ix.f, 1);
        abiertas = abiertas.filter(function (a) { return a !== ix.f; }).map(function (a) { return a > ix.f ? a - 1 : a; });
        pintar('[data-agregar-fase]');
        botonAgregar.focus();
        CoreTech.mostrarNotificacion('"' + nombre + '" se quitó. Ahora son ' + fases.length + ' fases y cada una vale ' + porcentaje(fases.length) + '.', 'info');
      }
    });

    botonAgregar.addEventListener('click', function () {
      if (fases.length >= MAX_FASES) return;
      recordarAbiertas();
      fases.push({ nombre: '', que_es: '', aprueba: false, pasos: [{ titulo: '', explicacion: '' }] });
      abiertas.push(fases.length - 1);
      pintar('[data-fase="' + (fases.length - 1) + '"] [data-campo-fase="nombre"]');
      CoreTech.mostrarNotificacion('Fase ' + fases.length + ' agregada. Ahora cada fase vale ' + porcentaje(fases.length) + '.', 'info');
    });

    function marcar(control, texto) {
      var campo = control.closest('.campo');
      campo.classList.toggle('campo--invalido', !!texto);
      control.setAttribute('aria-invalid', String(!!texto));
      campo.querySelector('.campo__mensaje-texto').textContent = texto || '';
    }

    function validar(silencioso) {
      var errores = [];
      var primero = null;
      var nombres = {};
      lista.querySelectorAll('[data-fase]').forEach(function (li) {
        var f = Number(li.getAttribute('data-fase'));
        var fase = fases[f];
        var errorFase = false;
        var control = li.querySelector('[data-campo-fase="nombre"]');
        var nombre = (fase.nombre || '').trim();
        var clave = nombre.toLowerCase();
        var texto = '';
        if (nombre.length < 3) texto = 'Escribe el nombre de la fase (al menos 3 caracteres).';
        else if (nombres[clave] !== undefined) texto = 'Ya hay una fase con este nombre (fase ' + (nombres[clave] + 1) + ').';
        if (nombre) nombres[clave] = nombres[clave] === undefined ? f : nombres[clave];
        marcar(control, texto);
        if (texto) { errorFase = true; primero = primero || control; }
        li.querySelectorAll('[data-paso]').forEach(function (pl) {
          var p = Number(pl.getAttribute('data-paso'));
          var c = pl.querySelector('[data-campo-paso="titulo"]');
          var t = (fase.pasos[p].titulo || '').trim().length < 3 ? 'Escribe qué se hace en este paso.' : '';
          marcar(c, t);
          if (t) { errorFase = true; primero = primero || c; }
        });
        var errorPasos = li.querySelector('[data-error-pasos]');
        errorPasos.textContent = fase.pasos.length ? '' : 'Agrega al menos un paso.';
        errorPasos.closest('.campo__mensaje').classList.toggle('es-visible', !fase.pasos.length);
        if (!fase.pasos.length) { errorFase = true; primero = primero || li.querySelector('[data-agregar-paso]'); }
        li.classList.toggle('fase-editor--invalida', errorFase);
        if (errorFase) {
          errores.push('Fase ' + (f + 1) + (nombre ? ' (' + nombre + ')' : '') + ': completa el nombre y los pasos.');
          if (!silencioso) li.querySelector('details').open = true;
        }
      });
      if (fases.length < MIN_FASES) errores.push('El plan necesita al menos ' + MIN_FASES + ' fases.');
      if (fases.length > MAX_FASES) errores.push('El plan puede tener máximo ' + MAX_FASES + ' fases.');
      if (!fases.some(function (f) { return f.aprueba; })) errores.push('Marca al menos una fase que apruebe ' + cliente + '.');

      cajaErrores.hidden = !errores.length;
      contenedor.querySelector('[data-lista-errores]').innerHTML = errores.map(function (e) { return '<li>' + escapar(e) + '</li>'; }).join('');
      if (errores.length && !silencioso) {
        if (primero) primero.focus(); else cajaErrores.scrollIntoView({ block: 'center' });
      }
      return !errores.length;
    }

    pintar();
    return {
      validar: function () { return validar(false); },
      obtener: function () { return copiar(fases); },
      cargar: function (nuevas) { fases = copiar(nuevas); abiertas = [0]; cajaErrores.hidden = true; pintar(); },
      alCambiar: function (fn) { oyentes.push(fn); fn(copiar(fases)); }
    };
  }

  window.CoreTech = window.CoreTech || {};
  window.CoreTech.editorPlan = editorPlan;
  window.CoreTech.porcentajeFase = porcentaje;
})();
