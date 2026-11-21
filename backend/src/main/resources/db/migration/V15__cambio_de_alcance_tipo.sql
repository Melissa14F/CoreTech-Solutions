-- V15 · Nuevo tipo de cambio de plan: AGREGAR_ALCANCE (HU-30, D-32, P21).
--
-- Va en su propia migración porque PostgreSQL no deja usar un valor de enum en la misma transacción que lo agrega:
-- lo que lo usa (restricciones y la función de validación) está en V16.
ALTER TYPE tipo_cambio_plan ADD VALUE IF NOT EXISTS 'AGREGAR_ALCANCE';
