#!/usr/bin/env bash
# Crea el PRIMER administrador en una base de producción vacía (no hay datos demo). Se usa una sola vez; las demás
# cuentas se crean después desde «Usuarios» en el panel.
#
#   ./scripts/crear-administrador.sh "Nombre Apellido" correo@dominio.com 3001234567     (desde prototipo/)
#
# Genera una contraseña temporal y la muestra UNA vez en pantalla: al ingresar, el portal exige cambiarla (RN-18).
# Variables opcionales: COMPOSE_FILE (por omisión docker-compose.prod.yml), CORETECH_SERVICIO_BD (db).
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ $# -eq 3 ] || { echo "Uso: $0 \"Nombre Apellido\" correo celular"; exit 1; }
NOMBRE="$1"; CORREO="$2"; CELULAR="$3"
export COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BD="${CORETECH_SERVICIO_BD:-db}"

# Contraseña temporal que cumple RN-22 (8+ caracteres con mayúscula, número y símbolo)
set +o pipefail   # tr | head termina con SIGPIPE a propósito
CLAVE="Ct-$(LC_ALL=C tr -dc 'A-HJ-NP-Z' </dev/urandom | head -c 4)$(LC_ALL=C tr -dc '2-9' </dev/urandom | head -c 4)-$(LC_ALL=C tr -dc 'a-hj-np-z' </dev/urandom | head -c 4)"
set -o pipefail
# BCrypt de costo 10 (como la API); htpasswd lo escribe con el prefijo $2y$, que Spring también lee
HASH="$(docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" "$CLAVE" | tr -d ':\r\n' | sed 's/^\$2y\$/$2a$/')"

docker compose exec -T "$BD" psql -U coretech -d coretech -v ON_ERROR_STOP=1 \
  -v nombre="$NOMBRE" -v correo="$CORREO" -v celular="$CELULAR" -v hash="$HASH" <<'SQL'
INSERT INTO usuarios (rol_id, nombre, email, celular, password_hash, debe_cambiar_password)
SELECT id, :'nombre', :'correo', :'celular', :'hash', true FROM roles WHERE codigo = 'ADMINISTRADOR';
SQL
echo
echo "Administrador creado: $CORREO"
echo "Contraseña temporal (se muestra solo ahora; deberá cambiarla al ingresar): $CLAVE"
