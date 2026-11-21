// Levanta la API para las pruebas E2E sobre una base limpia: borra y crea `coretech_e2e` en el PostgreSQL de
// docker-compose (servicio "db", que debe estar encendido) y arranca Spring Boot con los perfiles dev + e2e,
// que cargan los datos demo en esa base (puerto 8090).
import { spawn, spawnSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const prototipo = resolve(aqui, '..', '..');
const esWindows = process.platform === 'win32';

function psql(sql) {
  const r = spawnSync(
    'docker',
    ['compose', '-f', resolve(prototipo, 'docker-compose.yml'), 'exec', '-T', 'db', 'psql', '-U', 'coretech', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-c', sql],
    { stdio: 'inherit' },
  );
  if (r.status !== 0) {
    console.error('No pude preparar la base de las pruebas. ¿Está encendido docker compose (servicio db)?');
    process.exit(1);
  }
}

psql('DROP DATABASE IF EXISTS coretech_e2e WITH (FORCE)');
psql('CREATE DATABASE coretech_e2e OWNER coretech');

// En Windows se lanza por cmd con una sola cadena (mvnw.cmd no se encuentra sin ".\\" y los argumentos sueltos con shell están en desuso)
const api = esWindows
  ? spawn('.\\mvnw.cmd -q spring-boot:run -Dspring-boot.run.profiles=dev,e2e', { cwd: resolve(prototipo, 'backend'), stdio: 'inherit', shell: true })
  : spawn('./mvnw', ['-q', 'spring-boot:run', '-Dspring-boot.run.profiles=dev,e2e'], { cwd: resolve(prototipo, 'backend'), stdio: 'inherit' });
api.on('exit', (codigo) => process.exit(codigo ?? 0));
for (const senal of ['SIGINT', 'SIGTERM']) process.on(senal, () => api.kill());
