#!/usr/bin/env bash
# Respaldo de CoreTech (RNF-05): la base de datos (pg_dump) y los archivos subidos (imágenes y adjuntos).
#
#   ./scripts/respaldo.sh            (desde prototipo/)
#
# Crea respaldos/AAAA-MM-DD_HHMMSS/ con:  coretech.dump  archivos.tar.gz  SHA256SUMS
# y borra los respaldos con más de CORETECH_RETENCION_DIAS días (14 por omisión).
# Sirve en el servidor (Linux) y en Git Bash de Windows. No guarda contraseñas: pg_dump corre dentro del contenedor de
# PostgreSQL, que ya conoce las suyas.
#
# Variables (todas opcionales):
#   CORETECH_SERVICIO_BD          servicio de PostgreSQL en docker-compose.yml          (db)
#   CORETECH_BD                   nombre de la base                                    (coretech)
#   CORETECH_ARCHIVOS_VOLUMEN     volumen de Docker con los archivos (producción, P25)  (vacío)
#   CORETECH_ARCHIVOS_DIR         carpeta con los archivos, si no hay volumen           (backend/datos/archivos)
#   CORETECH_RESPALDOS            dónde guardar los respaldos                           (respaldos/)
#   CORETECH_RETENCION_DIAS       días que se conservan                                 (14)
#
# Para que sea diario, programa este script una vez al día (pérdida máxima de datos: 24 h):
#   Linux, crontab -e:   30 2 * * *  cd /ruta/a/prototipo && ./scripts/respaldo.sh >> respaldos/respaldo.log 2>&1
#   Windows: Programador de tareas → acción «bash.exe» con el argumento «scripts/respaldo.sh», iniciando en prototipo/.
# Para no perder los respaldos si el disco falla, copia CORETECH_RESPALDOS a otra máquina o a un almacenamiento externo.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash no debe convertir las rutas de Linux que se pasan a Docker

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"   # docker compose toma el docker-compose.yml de esta carpeta (en Windows no entiende rutas /c/…)
COMPOSE=(docker compose)
SERVICIO_BD="${CORETECH_SERVICIO_BD:-db}"
BD="${CORETECH_BD:-coretech}"
VOLUMEN="${CORETECH_ARCHIVOS_VOLUMEN:-}"
ARCHIVOS_DIR="${CORETECH_ARCHIVOS_DIR:-$RAIZ/backend/datos/archivos}"
RESPALDOS="${CORETECH_RESPALDOS:-$RAIZ/respaldos}"
RETENCION="${CORETECH_RETENCION_DIAS:-14}"

sello="$(date +%Y-%m-%d_%H%M%S)"
destino="$RESPALDOS/$sello"
parcial="$RESPALDOS/.parcial-$sello"
trap 'rm -rf "$parcial"' EXIT   # si algo falla, no queda un respaldo a medias que parezca bueno

mkdir -p "$parcial"
inicio=$SECONDS

echo "[$(date +%F' '%T)] Respaldando la base «$BD»…"
"${COMPOSE[@]}" exec -T "$SERVICIO_BD" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$0" --format=custom --no-owner' "$BD" > "$parcial/coretech.dump"

# Un respaldo que no se puede leer no sirve: se comprueba antes de darlo por bueno
[ -s "$parcial/coretech.dump" ] || { echo "ERROR: el respaldo de la base quedó vacío" >&2; exit 1; }
"${COMPOSE[@]}" exec -T "$SERVICIO_BD" pg_restore --list < "$parcial/coretech.dump" > /dev/null \
  || { echo "ERROR: el respaldo de la base no se puede leer" >&2; exit 1; }

echo "[$(date +%F' '%T)] Respaldando los archivos…"
if [ -n "$VOLUMEN" ]; then
  docker run --rm -v "$VOLUMEN":/origen:ro alpine tar -czf - -C /origen . > "$parcial/archivos.tar.gz"
elif [ -d "$ARCHIVOS_DIR" ]; then
  tar -czf "$parcial/archivos.tar.gz" -C "$ARCHIVOS_DIR" .
else
  echo "AVISO: no hay carpeta ni volumen de archivos; el respaldo solo lleva la base de datos" >&2
fi
if [ -f "$parcial/archivos.tar.gz" ]; then
  tar -tzf "$parcial/archivos.tar.gz" > /dev/null || { echo "ERROR: el respaldo de archivos no se puede leer" >&2; exit 1; }
fi

( cd "$parcial" && sha256sum -- * > SHA256SUMS )
mv "$parcial" "$destino"
trap - EXIT

# Retención: solo se borran carpetas con nombre AAAA-MM-DD_HHMMSS dentro de la carpeta de respaldos
find "$RESPALDOS" -maxdepth 1 -type d -name '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]_[0-9][0-9][0-9][0-9][0-9][0-9]' \
  -mtime +"$RETENCION" -exec rm -rf {} + 2>/dev/null || true

echo "[$(date +%F' '%T)] Listo en $((SECONDS - inicio)) s: $destino"
ls -l "$destino"
