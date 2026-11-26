# Prueba de carga (RNF-04)

200 usuarios simultáneos con k6 (en Docker, no hay que instalar nada): 120 agendan citas, 60 abren el detalle de su proyecto y 20 Product Owners registran pasos. Meta: P95 < 500 ms en cada una y sin errores.

La prueba usa **la API de pruebas** (puerto 8090, base `coretech_e2e`, con los límites de intentos por IP muy altos), nunca la base de desarrollo.

Desde `prototipo/` con `docker compose up -d` encendido:

```powershell
# 1. Base limpia y API desde el jar (una JVM normal; spring-boot:run arranca más lenta)
cd backend
.\mvnw.cmd -q -DskipTests package
docker compose -f ..\docker-compose.yml exec -T db psql -U coretech -d postgres -c "DROP DATABASE IF EXISTS coretech_e2e WITH (FORCE)" -c "CREATE DATABASE coretech_e2e OWNER coretech"
java -jar target\portal-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev,e2e

# 2. Datos (en otra terminal): 10 proyectos aprobados con 120 pasos pendientes y 190 clientes. Escribe carga/datos.json
cd ..\carga
node sembrar.mjs

# 3. La prueba (dura unos 2 minutos)
docker run --rm -v "${PWD}:/carga" -e BASE_URL=http://host.docker.internal:8090 grafana/k6 run --summary-export=/carga/resultado.json /carga/carga.js
```

- **Cada paso se marca una sola vez** (lo publicado no se edita): para repetir el paso 3 hay que volver a empezar desde el 1. Para repetir solo agendar y el detalle: `-e VUS_REGISTRAR=0`.
- **Peor caso, sin pausas** (≈200 peticiones en vuelo a la vez): `-e SIN_PAUSAS=1 -e VUS_AGENDAR=120 -e VUS_DETALLE=80 -e VUS_REGISTRAR=0 -e MANTENER=40s`.
- Los resultados se miden en la misma máquina que corre la API, PostgreSQL y k6: en un servidor aparte serán mejores. Los números de la última corrida están en `docs/PROGRESO.md`, «Verificación de RNF».
