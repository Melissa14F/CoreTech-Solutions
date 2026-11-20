-- V8 · Correo del sistema enlazado con Gmail (decisión del usuario del 2026-11-19, D-51).
--
-- El administrador conecta la cuenta de Gmail desde el panel ("Conectar con Gmail", OAuth 2.0): Google entrega un
-- permiso revocable (refresh token) solo para enviar correos. Aquí se guarda cifrado (AES-GCM, clave en la variable
-- CORETECH_CIFRADO_CLAVE); nunca la contraseña de la cuenta. Hay a lo sumo una conexión (id = 1).

CREATE TABLE correo_sistema (
  id              smallint     PRIMARY KEY DEFAULT 1,
  cuenta          varchar(120) NOT NULL,
  token_cifrado   text         NOT NULL,
  estado          varchar(12)  NOT NULL DEFAULT 'CONECTADO',
  conectado_por   uuid         REFERENCES usuarios (id) DEFERRABLE INITIALLY IMMEDIATE,
  conectado_at    timestamptz  NOT NULL DEFAULT now(),
  ultimo_error    varchar(255),
  ultimo_error_at timestamptz,
  CONSTRAINT ck_correo_sistema_unico  CHECK (id = 1),
  CONSTRAINT ck_correo_sistema_estado CHECK (estado IN ('CONECTADO', 'REVOCADO'))
);

COMMENT ON TABLE correo_sistema IS 'Cuenta de Gmail que envía los correos del portal (una sola fila). D-51';
COMMENT ON COLUMN correo_sistema.token_cifrado IS 'Permiso de Google (refresh token) cifrado con AES-GCM; nunca la contraseña';
COMMENT ON COLUMN correo_sistema.estado IS 'CONECTADO, o REVOCADO si Google retiró el permiso (hay que volver a conectar)';
