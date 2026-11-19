/* ==========================================================================
   Vista: Equipos (admin/equipos.html)
   - Crear y editar equipos con desarrolladores del registro.
   - El Product Owner es el único con cuenta: al elegir uno nuevo se muestra
     su contraseña temporal.
   - Cada integrante recibe un rol permitido por su especialidad
     (un Full stack puede ser Frontend, Backend o Full stack).
   - Un desarrollador solo puede estar en un equipo.
   Requiere: js/principal.js
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var DATOS = JSON.parse(document.getElementById('datos-equipos').textContent);
  var DEVS = DATOS.desarrolladores;
  var EQUIPOS = DATOS.equipos;
  var ROLES = DATOS.rolesPosibles;

  var dialogo = document.getElementById('dialogo-equipo');
  var formulario = dialogo.querySelector('form');
  var selectPO = formulario.elements.po;
  var lista = dialogo.querySelector('[data-integrantes]');
  var plantilla = document.getElementById('plantilla-integrante');
  var bloqueClave = dialogo.querySelector('[data-clave-temporal]');
  var avisoPO = dialogo.querySelector('[data-aviso-po]');
  var campoIntegrantes = dialogo.querySelector('[data-campo-integrantes]');
  var errorIntegrantes = dialogo.querySelector('[data-error-integrantes]');
  var equipoActual = null;   // id del equipo que se edita (null = nuevo)

  var ICONOS = {
    llave: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6M15.5 7.5l3 3L22 7l-3-3"/></svg>',
    editar: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>',
    proyectos: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/></svg>'
  };

  function iniciales(nombre) { return nombre.split(/\s+/).slice(0, 2).map(function (p) { return p.charAt(0).toUpperCase(); }).join(''); }
  function escapar(t) { var d = document.createElement('div'); d.textContent = t; return d.innerHTML; }

  // Equipo al que pertenece un desarrollador (sin contar el que se está editando)
  function equipoDe(id) {
    for (var e in EQUIPOS) {
      if (e === equipoActual) continue;
      if (EQUIPOS[e].po === id) return e;
      if (EQUIPOS[e].miembros.some(function (m) { return m[0] === id; })) return e;
    }
    return null;
  }
  function disponible(id) { return DEVS[id].activo && !equipoDe(id); }
  function idsOrdenados() { return Object.keys(DEVS).sort(function (a, b) { return DEVS[a].nombre.localeCompare(DEVS[b].nombre, 'es'); }); }

  /* ---------- Contraseña temporal ---------- */
  function generarClave() {
    var letras = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    var parte = function (n) { var t = ''; for (var i = 0; i < n; i++) t += letras[Math.floor(Math.random() * letras.length)]; return t; };
    var clave = dialogo.querySelector('[data-clave]');
    clave.textContent = 'Ct-' + parte(4) + '-' + parte(4);
    clave.classList.remove('esta-actualizado'); void clave.offsetWidth; clave.classList.add('esta-actualizado');
  }
  dialogo.querySelector('[data-generar-clave]').addEventListener('click', generarClave);

  /* ---------- Opciones de los selectores ---------- */
  function llenarPO(valor) {
    var html = '<option value="">Elige un desarrollador</option>';
    idsOrdenados().forEach(function (id) {
      if (!disponible(id)) return;
      html += '<option value="' + id + '">' + escapar(DEVS[id].nombre) + ' · ' + DEVS[id].especialidad + '</option>';
    });
    selectPO.innerHTML = html;
    selectPO.value = valor || '';
  }

  function llenarMiembro(select, valor) {
    var html = '<option value="">Elige un desarrollador</option>';
    idsOrdenados().forEach(function (id) {
      if (!disponible(id)) return;
      html += '<option value="' + id + '">' + escapar(DEVS[id].nombre) + ' · ' + DEVS[id].especialidad + '</option>';
    });
    select.innerHTML = html;
    select.value = valor || '';
  }

  function llenarRol(fila, valor) {
    var id = fila.querySelector('[data-miembro]').value;
    var select = fila.querySelector('[data-rol]');
    var roles = id ? ROLES[DEVS[id].especialidad] : [];
    select.innerHTML = roles.length ? roles.map(function (r) { return '<option>' + r + '</option>'; }).join('') : '<option value="">Primero elige</option>';
    select.disabled = !roles.length;
    if (valor && roles.indexOf(valor) !== -1) select.value = valor;
  }

  // Un desarrollador ya elegido (como PO o en otra fila) no se ofrece de nuevo
  function marcarElegidos() {
    var elegidos = [selectPO.value].concat(Array.prototype.map.call(lista.querySelectorAll('[data-miembro]'), function (s) { return s.value; }));
    // El selector del Product Owner no se bloquea: elegir a un integrante lo asciende (ver abajo)
    Array.prototype.slice.call(lista.querySelectorAll('[data-miembro]')).forEach(function (select) {
      Array.prototype.forEach.call(select.options, function (op) {
        op.disabled = !!op.value && op.value !== select.value && elegidos.indexOf(op.value) !== -1;
      });
    });
  }

  function agregarFila(id, rol) {
    var fila = plantilla.content.firstElementChild.cloneNode(true);
    var miembro = fila.querySelector('[data-miembro]');
    lista.appendChild(fila);
    llenarMiembro(miembro, id);
    llenarRol(fila, rol);
    miembro.addEventListener('change', function () { llenarRol(fila); marcarElegidos(); limpiarError(); });
    fila.querySelector('[data-quitar-integrante]').addEventListener('click', function () { fila.remove(); marcarElegidos(); });
    marcarElegidos();
    return fila;
  }
  dialogo.querySelector('[data-agregar-integrante]').addEventListener('click', function () {
    agregarFila().querySelector('[data-miembro]').focus();
  });

  /* ---------- Cambio de Product Owner ---------- */
  var poPrevio = '';
  selectPO.addEventListener('focus', function () { poPrevio = selectPO.value; });
  selectPO.addEventListener('change', function () {
    var anterior = equipoActual ? EQUIPOS[equipoActual].po : null;
    // Si el nuevo Product Owner era integrante, sale de la lista de integrantes;
    // el Product Owner que deja el cargo pasa a ser integrante con su rol por especialidad
    Array.prototype.forEach.call(lista.querySelectorAll('.integrantes__fila'), function (fila) {
      if (fila.querySelector('[data-miembro]').value === selectPO.value) fila.remove();
    });
    if (poPrevio && poPrevio !== selectPO.value && DEVS[poPrevio]) {
      var vacia = Array.prototype.filter.call(lista.querySelectorAll('.integrantes__fila'), function (f) { return !f.querySelector('[data-miembro]').value; })[0];
      if (vacia) vacia.remove();
      agregarFila(poPrevio);
    }
    poPrevio = selectPO.value;
    marcarElegidos();
    var nuevo = selectPO.value && selectPO.value !== anterior;
    bloqueClave.hidden = !nuevo;
    if (nuevo) generarClave();
    avisoPO.hidden = !(equipoActual && nuevo);
    if (equipoActual && nuevo) {
      var activos = EQUIPOS[equipoActual].activos;
      avisoPO.querySelector('[data-aviso-po-texto]').textContent = DEVS[selectPO.value].nombre + ' tendrá cuenta y quedará a cargo' +
        (activos ? ' de los ' + activos + ' proyectos activos del equipo' : ' del equipo') + '. ' + DEVS[anterior].nombre + ' pierde el acceso a la plataforma y queda como integrante (puedes quitarlo).';
    }
  });

  /* ---------- Abrir ---------- */
  function abrir(equipo) {
    equipoActual = equipo;
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (c) { c.classList.remove('campo--invalido', 'campo--valido'); });
    limpiarError();
    lista.innerHTML = '';
    avisoPO.hidden = true;
    if (equipo) {
      var datos = EQUIPOS[equipo];
      llenarPO(datos.po);
      datos.miembros.forEach(function (m) { agregarFila(m[0], m[1]); });
      bloqueClave.hidden = true;
      dialogo.querySelector('[data-titulo]').textContent = 'Editar ' + 'equipo de ' + DEVS[datos.po].nombre;
      dialogo.querySelector('[data-boton]').textContent = 'Guardar cambios';
    } else {
      llenarPO('');
      agregarFila();
      bloqueClave.hidden = true;
      dialogo.querySelector('[data-titulo]').textContent = 'Crear equipo';
      dialogo.querySelector('[data-boton]').textContent = 'Crear equipo';
    }
    poPrevio = selectPO.value;
    marcarElegidos();
    dialogo.showModal();
    selectPO.focus();
  }

  document.querySelector('[data-crear-equipo]').addEventListener('click', function () {
    equipoActual = null;
    var libres = Object.keys(DEVS).filter(disponible);
    if (libres.length < 2) {
      CoreTech.mostrarNotificacion('Necesitas al menos 2 desarrolladores sin equipo. Regístralos en Desarrolladores.', 'error');
      return;
    }
    abrir(null);
  });
  document.addEventListener('click', function (evento) {
    var editar = evento.target.closest('[data-editar-equipo]');
    if (editar) abrir(editar.closest('[data-equipo]').getAttribute('data-equipo'));
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });

  /* ---------- Validar integrantes ---------- */
  function limpiarError() {
    campoIntegrantes.classList.remove('integrantes--invalido');
    errorIntegrantes.textContent = '';
    lista.querySelectorAll('.es-invalida').forEach(function (f) { f.classList.remove('es-invalida'); });
  }
  function error(texto, fila) {
    campoIntegrantes.classList.add('integrantes--invalido');
    errorIntegrantes.textContent = texto;
    if (fila) { fila.classList.add('es-invalida'); fila.querySelector('[data-miembro]').focus(); }
    CoreTech.mostrarNotificacion(texto, 'error');
  }
  function leerIntegrantes() {
    limpiarError();
    var filas = Array.prototype.slice.call(lista.querySelectorAll('.integrantes__fila'));
    var vacia = filas.filter(function (f) { return !f.querySelector('[data-miembro]').value; })[0];
    if (vacia && filas.length > 1) { error('Elige el desarrollador de cada fila o quita las filas vacías.', vacia); return null; }
    var miembros = filas.filter(function (f) { return f.querySelector('[data-miembro]').value; })
      .map(function (f) { return [f.querySelector('[data-miembro]').value, f.querySelector('[data-rol]').value]; });
    if (!miembros.length) { error('Agrega al menos un integrante además del Product Owner.', filas[0]); return null; }
    return miembros;
  }

  /* ---------- Pintar una tarjeta ---------- */
  function htmlMiembro(id, rol) {
    var po = rol === 'Product Owner';
    return '<li class="tarjeta-equipo__miembro' + (po ? ' tarjeta-equipo__miembro--po' : '') + '">' +
      '<span class="avatar avatar--pequeno' + (po ? '' : ' avatar--gris') + '" aria-hidden="true">' + iniciales(DEVS[id].nombre) + '</span>' +
      '<span class="celda-principal"><strong>' + escapar(DEVS[id].nombre) + '</strong><small>Especialidad: ' + DEVS[id].especialidad + '</small></span>' +
      '<span class="insignia ' + (po ? 'insignia--oscuro' : 'insignia--especialidad') + '">' + (po ? ICONOS.llave : '') + rol + '</span></li>';
  }

  function crearTarjeta(id) {
    var art = document.createElement('article');
    art.className = 'tarjeta tarjeta-equipo';
    art.id = 'equipo-' + id;
    art.setAttribute('data-equipo', id);
    art.setAttribute('data-activos', '0');
    art.innerHTML = '<header class="tarjeta-equipo__cabecera"><div><h2 class="tarjeta-equipo__titulo" data-equipo-titulo></h2><p class="tarjeta-equipo__subtitulo" data-equipo-subtitulo></p></div>' +
      '<span class="tarjeta-equipo__carga"><span class="carga" role="img" aria-label="0 de 3 proyectos activos"><span class="carga__unidad"></span><span class="carga__unidad"></span><span class="carga__unidad"></span></span><small>0 de 3 proyectos activos</small></span></header>' +
      '<ul class="tarjeta-equipo__lista" data-equipo-lista></ul>' +
      '<footer class="tarjeta-equipo__pie"><a class="enlace-flecha" href="proyectos.html" data-equipo-proyectos>' + ICONOS.proyectos + 'Ver sus proyectos</a>' +
      '<button type="button" class="boton boton--contorno boton--pequeno" data-editar-equipo>' + ICONOS.editar + 'Editar equipo</button></footer>';
    document.querySelector('[data-rejilla-equipos]').appendChild(art);
    return art;
  }

  function pintarTarjeta(id) {
    var datos = EQUIPOS[id];
    var tarjeta = document.getElementById('equipo-' + id) || crearTarjeta(id);
    var nombrePO = DEVS[datos.po].nombre;
    tarjeta.querySelector('[data-equipo-titulo]').textContent = 'Equipo de ' + nombrePO;
    tarjeta.querySelector('[data-equipo-subtitulo]').textContent = (datos.miembros.length + 1) + ' integrantes · solo ' + nombrePO.split(' ')[0] + ' tiene cuenta';
    tarjeta.querySelector('[data-equipo-lista]').innerHTML = htmlMiembro(datos.po, 'Product Owner') +
      datos.miembros.map(function (m) { return htmlMiembro(m[0], m[1]); }).join('');
    tarjeta.querySelector('[data-equipo-proyectos]').href = 'proyectos.html?buscar=' + encodeURIComponent(nombrePO.split(' ')[0]);
    tarjeta.classList.remove('esta-resaltada'); void tarjeta.offsetWidth; tarjeta.classList.add('esta-resaltada');
    tarjeta.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function pintarDisponibles() {
    equipoActual = null;
    var libres = idsOrdenados().filter(disponible);
    var cont = document.querySelector('[data-disponibles]');
    cont.innerHTML = libres.length ? libres.map(function (id) {
      return '<li class="disponibles__item"><span class="avatar avatar--pequeno avatar--gris" aria-hidden="true">' + iniciales(DEVS[id].nombre) + '</span><span class="celda-principal"><strong>' + escapar(DEVS[id].nombre) + '</strong><small>' + DEVS[id].especialidad + '</small></span></li>';
    }).join('') : '<li class="disponibles__vacio">Todos los desarrolladores activos ya están en un equipo.</li>';
  }

  /* ---------- Guardar ---------- */
  formulario.addEventListener('formulario-valido', function () {
    var miembros = leerIntegrantes();
    if (!miembros) return;
    var po = selectPO.value;
    var esNuevo = !equipoActual;
    var boton = formulario.querySelector('[type="submit"]');
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      var id = equipoActual;
      var cambioPO = id && EQUIPOS[id].po !== po;
      if (!id) { id = po; EQUIPOS[id] = { po: po, activos: 0, miembros: [] }; }
      EQUIPOS[id].po = po;
      EQUIPOS[id].miembros = miembros;
      dialogo.close();
      pintarTarjeta(id);
      pintarDisponibles();
      var correo = DEVS[po].nombre.split(' ')[0];
      CoreTech.mostrarNotificacion(esNuevo
        ? 'Equipo de ' + DEVS[po].nombre + ' creado. Enviamos los datos de acceso a ' + correo + '.'
        : cambioPO ? 'Equipo actualizado. ' + DEVS[po].nombre + ' es ahora el Product Owner y recibió su cuenta.' : 'Equipo de ' + DEVS[po].nombre + ' actualizado.');
      equipoActual = null;
    }, 600);
  });
})();
