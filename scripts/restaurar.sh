#!/usr/bin/env bash
# Restauración de CoreTech (RNF-05: restaurar en menos de 4 h): base de datos y archivos desde un respaldo de respaldo.sh.
#
#   ./scripts/restaurar.sh respaldos/2026-11-21_023000 [base_destino]
#
# La base de destino es, por omisión, «<base>_restaurada»: una base NUEVA al lado de la actual, para revisar el respaldo
# sin tocar nada. Para recuperarse de una caída, restaura con el nombre real de la base:
#   REEMPLAZAR=si ./scripts/restaurar.sh respaldos/<carpeta> coretech
# (borra esa base y la vuelve a crear desde el respaldo; detén antes la API para que no haya conexiones abiertas).
#
# Los archivos se extraen en CORETECH_ARCHIVOS_DESTINO_DIR (una carpeta) o CORETECH_ARCHIVOS_DESTINO_VOLUMEN (un volumen
# de Docker). Por omisión van a <respaldo>/archivos-restaurados, para no pisar los archivos en uso.
#
# Variables (opcionales): CORETECH_SERVICIO_BD (db) · CORETECH_BD (coretech) · REEMPLAZAR (no)
set -euo pipefail
export MSYS_NO_PATHCONV=1

[ $# -ge 1 ] || { echo "Uso: $0 <carpeta-del-respaldo> [base_destino]" >&2; exit 2; }
RESPALDO="$(cd "$1" && pwd)"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"   # docker compose toma el docker-compose.yml de esta carpeta (en Windows no entiende rutas /c/…)
COMPOSE=(docker compose)
SERVICIO_BD="${CORETECH_SERVICIO_BD:-db}"
BD_ORIGEN="${CORETECH_BD:-coretech}"
BD_DESTINO="${2:-${BD_ORIGEN}_restaurada}"
REEMPLAZAR="${REEMPLAZAR:-no}"
DESTINO_DIR="${CORETECH_ARCHIVOS_DESTINO_DIR:-$RESPALDO/archivos-restaurados}"
DESTINO_VOLUMEN="${CORETECH_ARCHIVOS_DESTINO_VOLUMEN:-}"

[ -f "$RESPALDO/coretech.dump" ] || { echo "ERROR: $RESPALDO no tiene coretech.dump" >&2; exit 1; }
# El nombre de la base va dentro de comandos SQL: solo letras minúsculas, números y guion bajo
[[ "$BD_DESTINO" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || { echo "ERROR: nombre de base no válido: $BD_DESTINO" >&2; exit 1; }

# Ejecuta una consulta SQL en la base $1 (dentro del contenedor, con sus credenciales) y devuelve el resultado sin adornos
sql_en() {
  "${COMPOSE[@]}" exec -T "$SERVICIO_BD" sh -c 'psql -U "$POSTGRES_USER" -d "$0" -v ON_ERROR_STOP=1 -tAc "$1"' "$1" "$2"
}

inicio=$SECONDS
echo "[$(date +%F' '%T)] Comprobando que el respaldo está completo…"
( cd "$RESPALDO" && sha256sum --check --quiet SHA256SUMS ) \
  || { echo "ERROR: el respaldo está dañado (SHA256SUMS no coincide)" >&2; exit 1; }

existe="$(sql_en postgres "SELECT 1 FROM pg_database WHERE datname = '$BD_DESTINO'")"
if [ -n "$existe" ]; then
  tablas="$(sql_en "$BD_DESTINO" "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")"
  if [ "$tablas" != "0" ] && [ "$REEMPLAZAR" != "si" ]; then
    echo "ERROR: la base «$BD_DESTINO» ya existe y tiene $tablas tablas. Usa otro nombre o REEMPLAZAR=si." >&2
    exit 1
  fi
  sql_en postgres "DROP DATABASE \"$BD_DESTINO\" WITH (FORCE)" > /dev/null
fi
sql_en postgres "CREATE DATABASE \"$BD_DESTINO\"" > /dev/null

echo "[$(date +%F' '%T)] Restaurando la base en «$BD_DESTINO»…"
"${COMPOSE[@]}" exec -T "$SERVICIO_BD" sh -c 'pg_restore -U "$POSTGRES_USER" -d "$0" --no-owner --exit-on-error' "$BD_DESTINO" \
  < "$RESPALDO/coretech.dump"

if [ -f "$RESPALDO/archivos.tar.gz" ]; then
  echo "[$(date +%F' '%T)] Restaurando los archivos…"
  if [ -n "$DESTINO_VOLUMEN" ]; then
    docker run --rm -i -v "$DESTINO_VOLUMEN":/destino alpine tar -xzf - -C /destino < "$RESPALDO/archivos.tar.gz"
    echo "Archivos en el volumen «$DESTINO_VOLUMEN»"
  else
    mkdir -p "$DESTINO_DIR"
    tar -xzf "$RESPALDO/archivos.tar.gz" -C "$DESTINO_DIR"
    echo "Archivos en $DESTINO_DIR"
  fi
fi

tablas="$(sql_en "$BD_DESTINO" "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")"
migraciones="$(sql_en "$BD_DESTINO" "SELECT count(*) FROM flyway_schema_history WHERE success")"
echo "[$(date +%F' '%T)] Listo en $((SECONDS - inicio)) s: base «$BD_DESTINO» con $tablas tablas y $migraciones migraciones aplicadas."
