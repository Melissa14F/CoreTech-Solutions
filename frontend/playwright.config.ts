import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de punta a punta (P23). Levantan SOLAS la API (puerto 8090, base `coretech_e2e` nueva con los datos demo)
 * y la SPA (puerto 5174). Solo hace falta tener encendido `docker compose up -d` (PostgreSQL y Mailpit) en prototipo/.
 * Correr: `npx playwright test` (desde prototipo/frontend).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  // Los flujos comparten una sola base: se ejecutan uno tras otro y cada archivo crea sus propios datos
  workers: 1,
  fullyParallel: false,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5174',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'node e2e/arrancar-api.mjs',
      url: 'http://localhost:8090/api/v1/servicios',
      timeout: 300_000,
      reuseExistingServer: false,
      stdout: 'pipe',
    },
    {
      command: 'npx vite --port 5174 --strictPort',
      url: 'http://localhost:5174',
      timeout: 60_000,
      reuseExistingServer: false,
      env: { CORETECH_API_DESTINO: 'http://localhost:8090' },
    },
  ],
});
