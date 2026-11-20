import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';

import { crearEnrutador } from './rutas/enrutador';
import './styles/index.css';

const raiz = document.getElementById('raiz');
if (!raiz) throw new Error('Falta el elemento #raiz en index.html');

createRoot(raiz).render(
  <StrictMode>
    <RouterProvider router={crearEnrutador()} future={{ v7_startTransition: true }} />
  </StrictMode>,
);
