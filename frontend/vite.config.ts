/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo, /api va a la API de Spring Boot (prototipo/backend, puerto 8080).
// Así la SPA y la API comparten origen y la cookie de sesión (HttpOnly, SameSite=Strict) viaja sola.
// Las pruebas E2E (e2e/) apuntan a otra API con CORETECH_API_DESTINO.
const destinoApi = process.env.CORETECH_API_DESTINO ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: destinoApi, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/pruebas/configuracion.ts'],
    css: false,
  },
});
