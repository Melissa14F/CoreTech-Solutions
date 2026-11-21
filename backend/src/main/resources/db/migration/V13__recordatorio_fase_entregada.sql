-- V13 · Recordatorio de la fase entregada (HU-11, RN-17, P19).
--
-- Una fase que aprueba el cliente queda EN_REVISION con plazo de 3 días hábiles. A los 2 días hábiles se le recuerda
-- (una sola vez) y, vencido el plazo, la fase se aprueba sola y se registra como automática. La marca evita que el
-- recordatorio se repita o se pierda si el servidor estuvo apagado (mismo patrón de V12 con el plan).
ALTER TABLE proyecto_fases ADD COLUMN plazo_recordado_at timestamptz;

COMMENT ON COLUMN proyecto_fases.plazo_recordado_at IS 'Cuándo se le recordó al cliente que tiene una fase por revisar; una sola vez (RN-17)';
