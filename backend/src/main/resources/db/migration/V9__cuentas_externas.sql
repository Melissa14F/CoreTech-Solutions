-- V9 · Registro e ingreso de clientes con Google o Microsoft (RF-50, D-50; decisión del usuario del 2026-11-20).
--
-- 1. cuentas_externas: el vínculo entre un cliente y su cuenta de Google o Microsoft. Se busca por el identificador
--    que da el proveedor ("sujeto"), que no cambia aunque la persona cambie su correo. Nunca se guardan contraseñas
--    ni tokens del proveedor.
-- 2. usuarios.password_hash pasa a ser opcional: quien se registra con Google o Microsoft no tiene contraseña del
--    portal (puede crearla con "¿Olvidaste tu contraseña?"). Las cuentas del personal la siguen teniendo siempre:
--    las crea el administrador con una contraseña temporal (RF-05).

CREATE TABLE cuentas_externas (
  id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id        uuid         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE,
  proveedor         varchar(12)  NOT NULL,
  sujeto            varchar(255) NOT NULL,
  email             varchar(120) NOT NULL,
  created_at        timestamptz  NOT NULL DEFAULT now(),
  ultimo_ingreso_at timestamptz,
  CONSTRAINT ck_cuentas_externas_proveedor CHECK (proveedor IN ('GOOGLE', 'MICROSOFT')),
  CONSTRAINT ux_cuentas_externas_sujeto    UNIQUE (proveedor, sujeto),
  CONSTRAINT ux_cuentas_externas_usuario   UNIQUE (usuario_id, proveedor)
);

COMMENT ON TABLE cuentas_externas IS 'Cuenta de Google o Microsoft con la que un cliente se registra o ingresa (RF-50)';
COMMENT ON COLUMN cuentas_externas.sujeto IS 'Identificador de la persona en el proveedor ("sub" de OpenID Connect)';
COMMENT ON COLUMN cuentas_externas.email IS 'Correo que dio el proveedor la última vez (verificado por él)';

ALTER TABLE usuarios ALTER COLUMN password_hash DROP NOT NULL;
COMMENT ON COLUMN usuarios.password_hash IS 'BCrypt (RN-22). Vacío solo en clientes que se registraron con Google o Microsoft (V9)';
