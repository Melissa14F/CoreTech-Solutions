#!/usr/bin/env bash
# Genera un certificado AUTOFIRMADO para probar HTTPS en https://localhost (el navegador avisará que no es de
# confianza: es normal). En un servidor real, pon aquí el certificado de verdad (Let's Encrypt u otro) con los mismos
# nombres: nginx/certs/fullchain.pem y nginx/certs/privkey.pem.
# Uso (desde prototipo/):  bash scripts/certificado-local.sh [dominio]      (por defecto: localhost)
set -euo pipefail
DOMINIO="${1:-localhost}"
DESTINO="$(cd "$(dirname "$0")/.." && pwd)/nginx/certs"
mkdir -p "$DESTINO"
# Se usa OpenSSL dentro de un contenedor para no depender de lo que tenga instalado el equipo.
MSYS_NO_PATHCONV=1 docker run --rm -v "$DESTINO:/certs" alpine/openssl req -x509 -nodes -newkey rsa:2048 \
  -days 365 -keyout /certs/privkey.pem -out /certs/fullchain.pem \
  -subj "/CN=$DOMINIO" -addext "subjectAltName=DNS:$DOMINIO,DNS:localhost,IP:127.0.0.1"
echo "Certificado autofirmado para $DOMINIO creado en $DESTINO (vale 365 días)."
