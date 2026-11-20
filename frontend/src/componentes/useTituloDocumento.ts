import { useEffect } from 'react';

/** Título de la pestaña: "Mis citas · CoreTech Solutions". Ayuda a los lectores de pantalla al cambiar de vista. */
export function useTituloDocumento(titulo: string) {
  useEffect(() => {
    document.title = titulo ? `${titulo} · CoreTech Solutions` : 'CoreTech Solutions';
  }, [titulo]);
}
