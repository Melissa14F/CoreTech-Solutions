/* ==========================================================================
   Vista: Servicios del catálogo (admin/servicios.html)
   - Crear y editar servicios con vista previa en vivo de la tarjeta.
   - Elegir una imagen de la galería o subir una propia (se previsualiza).
   - Mostrar u ocultar un servicio del catálogo.
   - Ordenar con los botones subir y bajar (orden del carrusel).
   - Eliminar solo si no tiene citas; si tiene, se propone ocultarlo.
   Requiere: js/principal.js (window.CoreTech)
   ========================================================================== */
(function () {
  'use strict';

  var CoreTech = window.CoreTech;
  var lista = document.querySelector('[data-lista-servicios]');
  var editor = document.getElementById('dialogo-servicio');
  var formulario = editor.querySelector('form');
  var campos = formulario.elements;
  var archivo = editor.querySelector('[data-archivo]');
  var opcionPropia = editor.querySelector('[data-opcion-propia]');
  var itemActual = null;
  var imagenPropia = '';   // imagen subida (data URL)

  var ICONOS = {
    ojo: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>',
    ojoNo: '<svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M9.9 4.24A9 9 0 0 1 12 4c6.5 0 10 7 10 7a18 18 0 0 1-2.16 3.19M6.6 6.6A18 18 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 5.4-1.6M1 1l22 22"/><path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/></svg>'
  };

  function items() { return Array.prototype.slice.call(lista.children); }
  function resaltar(item) { item.classList.remove('esta-movido'); void item.offsetWidth; item.classList.add('esta-movido'); }

  /* ---------- Posiciones, botones de orden y totales ---------- */
  function actualizarLista() {
    var posicion = 0, ocultos = 0;
    items().forEach(function (item, indice, todos) {
      var visible = item.getAttribute('data-visible') === 'true';
      if (visible) posicion += 1; else ocultos += 1;
      item.querySelector('[data-posicion]').textContent = visible ? posicion : '–';
      item.querySelector('[data-mover="-1"]').disabled = indice === 0;
      item.querySelector('[data-mover="1"]').disabled = indice === todos.length - 1;
    });
    document.querySelector('[data-total-visibles]').textContent = posicion;
    document.querySelector('[data-total-ocultos]').textContent = ocultos + (ocultos === 1 ? ' oculto' : ' ocultos');
  }

  /* ---------- Ordenar ---------- */
  lista.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-mover]');
    if (!boton) return;
    var item = boton.closest('li');
    var direccion = Number(boton.getAttribute('data-mover'));
    var vecino = direccion < 0 ? item.previousElementSibling : item.nextElementSibling;
    if (!vecino) return;
    if (direccion < 0) lista.insertBefore(item, vecino); else lista.insertBefore(vecino, item);
    actualizarLista();
    resaltar(item);
    // Mantiene el foco en el mismo botón para poder mover varias veces seguidas con el teclado
    var mismo = item.querySelector('[data-mover="' + direccion + '"]');
    (mismo.disabled ? item.querySelector('[data-mover="' + -direccion + '"]') : mismo).focus();
    CoreTech.mostrarNotificacion('Orden actualizado: "' + item.getAttribute('data-nombre') + '" cambió de lugar en el carrusel.');
  });

  /* ---------- Mostrar u ocultar ---------- */
  function cambiarVisibilidad(item, visible) {
    var nombre = item.getAttribute('data-nombre');
    item.setAttribute('data-visible', String(visible));
    item.classList.toggle('servicio-item--oculto', !visible);
    var estado = item.querySelector('[data-item-estado]');
    estado.className = 'insignia insignia--' + (visible ? 'atendida' : 'pausado');
    estado.textContent = visible ? 'Visible' : 'Oculto';
    var boton = item.querySelector('[data-alternar]');
    boton.innerHTML = visible ? ICONOS.ojoNo : ICONOS.ojo;
    boton.setAttribute('aria-label', (visible ? 'Ocultar ' : 'Mostrar ') + nombre + (visible ? ' del catálogo' : ' en el catálogo'));
    boton.title = visible ? 'Ocultar del catálogo' : 'Mostrar en el catálogo';
    actualizarLista();
    resaltar(item);
  }

  lista.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-alternar]');
    if (!boton) return;
    var item = boton.closest('li');
    var visible = item.getAttribute('data-visible') !== 'true';
    if (visible && !item.getAttribute('data-imagen')) {
      CoreTech.mostrarNotificacion('Agrega una imagen antes de mostrar "' + item.getAttribute('data-nombre') + '" en el catálogo.', 'error');
      abrirEditor(item);
      return;
    }
    cambiarVisibilidad(item, visible);
    CoreTech.mostrarNotificacion('"' + item.getAttribute('data-nombre') + '" ' + (visible ? 'ya se muestra en el catálogo.' : 'quedó oculto: no se muestra ni se puede agendar.'));
  });

  /* ---------- Vista previa del editor ---------- */
  var previa = {
    tarjeta: editor.querySelector('[data-previa]'),
    imagen: editor.querySelector('[data-previa-imagen]'),
    nombre: editor.querySelector('[data-previa-nombre]'),
    descripcion: editor.querySelector('[data-previa-descripcion]'),
    oculto: editor.querySelector('[data-previa-oculto]')
  };

  function rutaImagen() {
    var elegida = formulario.querySelector('input[name="imagen"]:checked');
    if (!elegida) return '';
    return elegida.value === 'propia' ? imagenPropia : '../img/servicios/' + elegida.value;
  }

  function pintarPrevia() {
    previa.nombre.textContent = campos.nombre.value.trim() || 'Nombre del servicio';
    previa.descripcion.textContent = campos.descripcion.value.trim() || 'La descripción aparecerá aquí.';
    var ruta = rutaImagen();
    previa.imagen.classList.toggle('tarjeta-servicio__imagen--vacia', !ruta);
    previa.imagen.innerHTML = ruta ? '<img alt="" width="1200" height="800">' : 'Sin imagen';
    if (ruta) {
      previa.imagen.querySelector('img').src = ruta;
      previa.imagen.querySelector('img').alt = campos.alt.value.trim();
    }
    var visible = formulario.querySelector('[data-casilla-visible]').checked;
    previa.tarjeta.classList.toggle('esta-oculta', !visible);
    previa.oculto.hidden = visible;
  }
  formulario.addEventListener('input', pintarPrevia);
  formulario.addEventListener('change', pintarPrevia);

  /* ---------- Subir una imagen propia ---------- */
  var etiquetaPropia = opcionPropia.closest('label');
  opcionPropia.addEventListener('click', function () { archivo.click(); });
  archivo.addEventListener('change', function () {
    var elegido = archivo.files[0];
    if (!elegido) return;
    if (elegido.size > 2 * 1024 * 1024) {
      CoreTech.mostrarNotificacion('La imagen pesa más de 2 MB. Comprímela o usa formato WebP.', 'error');
      return;
    }
    var lector = new FileReader();
    lector.onload = function () {
      imagenPropia = lector.result;
      etiquetaPropia.classList.add('tiene-imagen');
      etiquetaPropia.querySelector('span').style.backgroundImage = 'url("' + imagenPropia + '")';
      opcionPropia.checked = true;
      pintarPrevia();
    };
    lector.readAsDataURL(elegido);
  });

  /* ---------- Abrir el editor ---------- */
  function abrirEditor(item) {
    itemActual = item || null;
    formulario.reset();
    formulario.querySelectorAll('.campo').forEach(function (campo) { campo.classList.remove('campo--invalido', 'campo--valido'); });
    imagenPropia = '';
    etiquetaPropia.classList.remove('tiene-imagen');
    etiquetaPropia.querySelector('span').style.backgroundImage = '';

    if (item) {
      campos.nombre.value = item.getAttribute('data-nombre');
      campos.descripcion.value = item.getAttribute('data-descripcion');
      campos.alt.value = item.getAttribute('data-alt');
      var imagen = item.getAttribute('data-imagen');
      var opcion = imagen && formulario.querySelector('input[name="imagen"][value="' + imagen + '"]');
      if (opcion) opcion.checked = true;
      else if (imagen && imagen.indexOf('data:') === 0) {
        imagenPropia = imagen;
        etiquetaPropia.classList.add('tiene-imagen');
        etiquetaPropia.querySelector('span').style.backgroundImage = 'url("' + imagen + '")';
        opcionPropia.checked = true;
      }
      formulario.querySelector('[data-casilla-visible]').checked = item.getAttribute('data-visible') === 'true';
      editor.querySelector('[data-titulo-editor]').textContent = 'Editar servicio';
      editor.querySelector('[data-boton-editor]').textContent = 'Guardar cambios';
    } else {
      editor.querySelector('[data-titulo-editor]').textContent = 'Nuevo servicio';
      editor.querySelector('[data-boton-editor]').textContent = 'Crear servicio';
    }
    formulario.querySelectorAll('textarea, input[type="text"]').forEach(function (control) { control.dispatchEvent(new Event('input', { bubbles: true })); });
    pintarPrevia();
    editor.showModal();
  }

  document.querySelector('[data-nuevo]').addEventListener('click', function () { abrirEditor(null); });
  lista.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-editar]');
    if (boton) abrirEditor(boton.closest('li'));
  });
  document.addEventListener('click', function (evento) {
    var cerrar = evento.target.closest('[data-cerrar-dialogo]');
    if (cerrar) cerrar.closest('dialog').close();
  });

  /* ---------- Guardar ---------- */
  function claveDe(nombre) {
    return nombre.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }

  function crearItem() {
    var molde = lista.querySelector('li');
    var item = molde.cloneNode(true);
    item.classList.remove('servicio-item--oculto', 'esta-movido');
    item.setAttribute('data-citas', '0');
    item.querySelector('.servicio-item__citas').textContent = 'Sin citas todavía';
    return item;
  }

  function aplicar(item, datos) {
    var nombre = datos.nombre;
    item.setAttribute('data-servicio', claveDe(nombre));
    item.setAttribute('data-nombre', nombre);
    item.setAttribute('data-descripcion', datos.descripcion);
    item.setAttribute('data-alt', datos.alt);
    item.setAttribute('data-imagen', datos.imagen);
    item.querySelector('[data-item-nombre]').textContent = nombre;
    item.querySelector('[data-item-descripcion]').textContent = datos.descripcion;
    var miniatura = item.querySelector('.servicio-item__miniatura');
    miniatura.innerHTML = '<img alt="" width="96" height="64" data-item-imagen>';
    miniatura.querySelector('img').src = datos.imagen.indexOf('data:') === 0 ? datos.imagen : '../img/servicios/' + datos.imagen;
    item.querySelector('.servicio-item__orden').setAttribute('aria-label', 'Cambiar el orden de ' + nombre);
    item.querySelector('[data-mover="-1"]').setAttribute('aria-label', 'Subir ' + nombre);
    item.querySelector('[data-mover="1"]').setAttribute('aria-label', 'Bajar ' + nombre);
    item.querySelector('[data-eliminar]').setAttribute('aria-label', 'Eliminar ' + nombre);
    cambiarVisibilidad(item, datos.visible);
  }

  formulario.addEventListener('formulario-valido', function () {
    var elegida = formulario.querySelector('input[name="imagen"]:checked');
    if (elegida.value === 'propia' && !imagenPropia) {
      var campo = elegida.closest('.campo');
      campo.classList.add('campo--invalido');
      campo.querySelector('.campo__mensaje-texto').textContent = 'Sube la imagen o elige una de la galería.';
      return;
    }
    var datos = {
      nombre: campos.nombre.value.trim(),
      descripcion: campos.descripcion.value.trim(),
      alt: campos.alt.value.trim(),
      imagen: elegida.value === 'propia' ? imagenPropia : elegida.value,
      visible: formulario.querySelector('[data-casilla-visible]').checked
    };
    var repetido = items().some(function (item) { return item !== itemActual && item.getAttribute('data-nombre').toLowerCase() === datos.nombre.toLowerCase(); });
    if (repetido) {
      var campoNombre = campos.nombre.closest('.campo');
      campoNombre.classList.add('campo--invalido');
      campoNombre.querySelector('.campo__mensaje-texto').textContent = 'Ya existe un servicio con este nombre.';
      campos.nombre.focus();
      return;
    }
    var boton = formulario.querySelector('[type="submit"]');
    boton.classList.add('esta-cargando');
    setTimeout(function () {
      boton.classList.remove('esta-cargando');
      editor.close();
      var item = itemActual;
      if (!item) {
        item = crearItem();
        // Un servicio nuevo visible va después del último visible
        var visibles = items().filter(function (i) { return i.getAttribute('data-visible') === 'true'; });
        var ultimo = visibles[visibles.length - 1];
        lista.insertBefore(item, ultimo ? ultimo.nextElementSibling : lista.firstElementChild);
      }
      aplicar(item, datos);
      item.classList.add('esta-visible');
      item.scrollIntoView({ behavior: 'smooth', block: 'center' });
      CoreTech.mostrarNotificacion(itemActual ? 'Cambios guardados en "' + datos.nombre + '".' : 'Servicio "' + datos.nombre + '" creado' + (datos.visible ? ' y publicado en el catálogo.' : ' como oculto.'));
      itemActual = null;
    }, 700);
  });

  /* ---------- Eliminar ---------- */
  var ventanaEliminar = document.getElementById('dialogo-eliminar');
  var botonEliminar = ventanaEliminar.querySelector('[data-confirmar-eliminar]');
  var botonOcultar = ventanaEliminar.querySelector('[data-ocultar-en-su-lugar]');
  var porEliminar = null;

  lista.addEventListener('click', function (evento) {
    var boton = evento.target.closest('[data-eliminar]');
    if (!boton) return;
    porEliminar = boton.closest('li');
    var nombre = porEliminar.getAttribute('data-nombre');
    var citas = Number(porEliminar.getAttribute('data-citas'));
    var tieneCitas = citas > 0;
    ventanaEliminar.querySelector('[data-titulo-eliminar]').textContent = tieneCitas ? 'No se puede eliminar' : '¿Eliminar "' + nombre + '"?';
    ventanaEliminar.querySelector('[data-texto-eliminar]').textContent = tieneCitas
      ? '"' + nombre + '" tiene ' + citas + ' citas y proyectos asociados que deben conservarse. Puedes ocultarlo para que deje de aparecer en el catálogo.'
      : 'Se borrará del catálogo de forma definitiva. Esta acción queda registrada en la auditoría.';
    botonEliminar.hidden = tieneCitas;
    botonOcultar.hidden = !tieneCitas || porEliminar.getAttribute('data-visible') !== 'true';
    ventanaEliminar.showModal();
  });

  botonOcultar.addEventListener('click', function () {
    ventanaEliminar.close();
    cambiarVisibilidad(porEliminar, false);
    CoreTech.mostrarNotificacion('"' + porEliminar.getAttribute('data-nombre') + '" quedó oculto del catálogo.');
  });

  botonEliminar.addEventListener('click', function () {
    botonEliminar.classList.add('esta-cargando');
    setTimeout(function () {
      botonEliminar.classList.remove('esta-cargando');
      ventanaEliminar.close();
      var nombre = porEliminar.getAttribute('data-nombre');
      porEliminar.remove();
      actualizarLista();
      CoreTech.mostrarNotificacion('Servicio "' + nombre + '" eliminado.');
    }, 600);
  });

  actualizarLista();
})();
