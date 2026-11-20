import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { contenidoSitio } from './api';

/**
 * Textos de la página principal (HU-07, HU-19), los mismos de la migración V5. Se usan solo si la API no
 * responde, para que el sitio público nunca quede en blanco; los que valen son los de GET /contenido.
 */
export const CONTENIDO_DE_RESPALDO: Record<string, string> = {
  'portada-etiqueta': 'Software a la medida',
  'portada-titulo-1': 'Tu idea, hecha software.',
  'portada-titulo-2': 'Y cada avance, a la vista.',
  'portada-texto': 'De la primera reunión al despliegue, con un equipo a cargo y tu proyecto visible fase por fase desde tu panel.',
  'portada-boton': 'Agendar una cita',
  'portada-boton-2': 'Ver servicios',
  'servicios-titulo': 'Servicios',
  'servicios-texto': 'Elige el servicio que necesitas y agenda una cita con nuestro equipo.',
  'proceso-titulo': 'Cómo funciona',
  'proceso-texto': 'Desde la primera reunión hasta el despliegue, siempre sabes en qué va tu proyecto.',
  'proceso-paso-1': 'Agenda tu cita',
  'proceso-paso-1-texto': 'Elige el servicio, la fecha y si prefieres la reunión presencial o virtual.',
  'proceso-paso-2': 'Te asignamos un equipo',
  'proceso-paso-2-texto': 'Creamos tu proyecto y un equipo lo construye; su Product Owner es tu contacto de principio a fin.',
  'proceso-paso-3': 'Sigue cada fase',
  'proceso-paso-3-texto': 'Revisa cada fase, apruébala o pide cambios, y habla con tu Product Owner cuando lo necesites.',
  'contacto-titulo': 'Hablemos de tu proyecto',
  'contacto-texto': 'Atendemos en nuestra oficina de Cúcuta o por videollamada, en el horario que elijas al agendar.',
  'contacto-direccion': 'Calle 10 # 4-25, oficina 302, Cúcuta',
  'contacto-horario': 'Lunes a viernes, 8:00 a. m. a 12:00 m. y 2:00 p. m. a 6:00 p. m.',
  'contacto-telefono': '300 123 4567',
  'contacto-correo': 'contacto@coretechsolutions.co',
  'llamado-titulo': '¿Listo para empezar tu proyecto?',
  'llamado-texto': 'Agenda tu primera reunión en menos de dos minutos.',
  'pie-descripcion': 'Empresa de desarrollo de software a la medida en Cúcuta. Creamos tiendas en línea, sitios web, aplicaciones y sistemas de gestión, y te mostramos cada avance de tu proyecto.',
  'pie-mision': 'Desarrollar software a la medida que resuelva necesidades reales de las empresas de la región, con un proceso transparente en el que cada cliente sigue el avance de su proyecto de principio a fin.',
  'pie-vision': 'Para 2030, ser la empresa de desarrollo de software de referencia en el oriente colombiano, reconocida por la calidad de sus soluciones y la confianza que genera en sus clientes.',
};

/** Texto de una clave: el publicado o, si falta, el de respaldo. */
export type Textos = (clave: string) => string;

const Contexto = createContext<{ cargado: boolean; texto: Textos }>({
  cargado: false,
  texto: (clave) => CONTENIDO_DE_RESPALDO[clave] ?? '',
});

/** Lee GET /contenido una vez para todo el sitio público (página principal y pie de página). */
export function ProveedorContenido({ children }: { children: ReactNode }) {
  const [textos, setTextos] = useState<Record<string, string> | null>(null);

  useEffect(() => {
    let vigente = true;
    contenidoSitio.publico()
      .then((r) => vigente && setTextos(r.textos))
      .catch(() => vigente && setTextos({}));
    return () => {
      vigente = false;
    };
  }, []);

  const texto: Textos = (clave) => textos?.[clave] ?? CONTENIDO_DE_RESPALDO[clave] ?? '';
  return <Contexto.Provider value={{ cargado: textos !== null, texto }}>{children}</Contexto.Provider>;
}

export function useContenido() {
  return useContext(Contexto);
}

/** "300 123 4567" → "tel:+573001234567" (número colombiano de 10 dígitos). */
export function enlaceTelefono(telefono: string): string {
  const digitos = telefono.replace(/\D/g, '');
  return `tel:${digitos.length === 10 ? `+57${digitos}` : digitos}`;
}
