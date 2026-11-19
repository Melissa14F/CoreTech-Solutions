/* ==========================================================================
   Vista: Auditoría (admin/auditoria.html)
   - Exporta a CSV los eventos que se ven con los filtros actuales.
   El registro es de solo lectura: esta vista no tiene acciones de edición.
   Los filtros, la búsqueda y los conteos los maneja js/tabla.js.
   Requiere: js/principal.js y js/tabla.js
   ========================================================================== */
(function () {
  'use strict';

  function celdaCsv(texto) { return '"' + String(texto).replace(/"/g, '""') + '"'; }

  document.querySelector('[data-exportar]').addEventListener('click', function () {
    var filas = Array.prototype.filter.call(document.querySelectorAll('[data-fila]'), function (fila) { return !fila.hidden; });
    if (!filas.length) {
      window.CoreTech.mostrarNotificacion('No hay eventos para exportar con estos filtros.', 'error');
      return;
    }
    var lineas = [['Fecha', 'Usuario', 'Acción', 'Elemento', 'Módulo', 'Cambios', 'Origen'].map(celdaCsv).join(',')];
    filas.forEach(function (fila) {
      var cambios = Array.prototype.map.call(fila.querySelectorAll('.cambio'), function (cambio) {
        return cambio.querySelector('.cambio__campo').textContent + ': ' + cambio.querySelector('.cambio__antes').textContent + ' → ' + cambio.querySelector('.cambio__despues').textContent;
      }).join(' | ');
      lineas.push([fila.getAttribute('data-iso'), fila.getAttribute('data-csv-usuario'), fila.getAttribute('data-csv-accion'), fila.getAttribute('data-csv-elemento'),
        fila.getAttribute('data-csv-modulo'), cambios, fila.getAttribute('data-csv-origen')].map(celdaCsv).join(','));
    });
    // BOM para que Excel lea bien las tildes
    var archivo = new Blob(['﻿' + lineas.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    var enlace = document.createElement('a');
    enlace.href = URL.createObjectURL(archivo);
    enlace.download = 'auditoria-coretech-2026-11-11.csv';
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    setTimeout(function () { URL.revokeObjectURL(enlace.href); }, 1000);
    window.CoreTech.mostrarNotificacion('Exportamos ' + filas.length + (filas.length === 1 ? ' evento.' : ' eventos.'));
  });
})();
