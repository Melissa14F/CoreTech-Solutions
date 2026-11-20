// Matchers de Testing Library (toBeInTheDocument, toHaveAttribute…) y limpieza después de cada prueba.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// jsdom no implementa el desplazamiento; ScrollRestoration lo usa en cada navegación.
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
// Tampoco scrollIntoView, que se usa al abrir una dirección con #ancla (por ejemplo /legal#datos).
Element.prototype.scrollIntoView = vi.fn();

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});
