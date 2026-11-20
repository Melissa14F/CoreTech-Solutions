-- V7 · Invalidar sesiones (RNF-10). La sesión es un JWT en una cookie que vale hasta 30 días; sin esto,
-- cerrar sesión solo borraba la cookie del navegador y cambiar o restablecer la contraseña no cerraba
-- las sesiones abiertas en otros dispositivos.
--
-- 1. usuarios.version_sesion: va dentro del token. Cambiar o restablecer la contraseña (y anonimizar la
--    cuenta) la sube y todas las sesiones anteriores dejan de valer.
-- 2. sesiones_cerradas: "Cerrar sesión" guarda el id del token (jti) hasta que vence, para que esa cookie no
--    sirva aunque alguien la haya copiado. Las filas vencidas se borran al cerrar otra sesión.

ALTER TABLE usuarios ADD COLUMN version_sesion integer NOT NULL DEFAULT 0;
COMMENT ON COLUMN usuarios.version_sesion IS 'Sube al cambiar o restablecer la contraseña: invalida las sesiones abiertas (RNF-10)';

CREATE TABLE sesiones_cerradas (
  jti       uuid        PRIMARY KEY,
  expira_at timestamptz NOT NULL
);
CREATE INDEX ix_sesiones_cerradas_expira ON sesiones_cerradas (expira_at);
COMMENT ON TABLE sesiones_cerradas IS 'Tokens de sesión cerrados con "Cerrar sesión" que aún no vencen (RNF-10)';
