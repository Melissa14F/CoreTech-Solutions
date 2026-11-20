/**
 * Países que el cliente puede elegir al agendar y las zonas IANA de cada uno (HU-01, RN-23).
 * Es la misma lista del backend (`agenda.aplicacion.PaisesYZonas`) y de `prototipo/cliente/agendar-fecha.html`:
 * no hay endpoint. Un país con una sola zona no pide elegirla; con varias, sí.
 */
export const COLOMBIA = 'America/Bogota';
export const PAIS_NEGOCIO = 'Colombia';

export interface ZonaHoraria {
  /** Zona IANA que se envía a la API. */
  id: string;
  /** Nombre que ve el cliente. */
  nombre: string;
}

export const PAISES: Record<string, ZonaHoraria[]> = {
  Colombia: [{ id: COLOMBIA, nombre: 'Colombia' }],
  Argentina: [{ id: 'America/Argentina/Buenos_Aires', nombre: 'Buenos Aires' }],
  Bolivia: [{ id: 'America/La_Paz', nombre: 'La Paz' }],
  Brasil: [
    { id: 'America/Sao_Paulo', nombre: 'Brasilia, São Paulo, Río de Janeiro' },
    { id: 'America/Manaus', nombre: 'Amazonas (Manaos)' },
  ],
  Canadá: [
    { id: 'America/Toronto', nombre: 'Este (Toronto, Montreal)' },
    { id: 'America/Winnipeg', nombre: 'Centro (Winnipeg)' },
    { id: 'America/Edmonton', nombre: 'Montaña (Calgary, Edmonton)' },
    { id: 'America/Vancouver', nombre: 'Pacífico (Vancouver)' },
  ],
  Chile: [{ id: 'America/Santiago', nombre: 'Santiago' }],
  'Costa Rica': [{ id: 'America/Costa_Rica', nombre: 'San José' }],
  Ecuador: [{ id: 'America/Guayaquil', nombre: 'Quito, Guayaquil' }],
  'El Salvador': [{ id: 'America/El_Salvador', nombre: 'San Salvador' }],
  España: [
    { id: 'Europe/Madrid', nombre: 'Península y Baleares (Madrid)' },
    { id: 'Atlantic/Canary', nombre: 'Islas Canarias' },
  ],
  'Estados Unidos': [
    { id: 'America/New_York', nombre: 'Este (Nueva York, Miami)' },
    { id: 'America/Chicago', nombre: 'Centro (Chicago, Houston)' },
    { id: 'America/Denver', nombre: 'Montaña (Denver)' },
    { id: 'America/Phoenix', nombre: 'Arizona (Phoenix)' },
    { id: 'America/Los_Angeles', nombre: 'Pacífico (Los Ángeles, San Francisco)' },
  ],
  Guatemala: [{ id: 'America/Guatemala', nombre: 'Ciudad de Guatemala' }],
  Honduras: [{ id: 'America/Tegucigalpa', nombre: 'Tegucigalpa' }],
  México: [
    { id: 'America/Mexico_City', nombre: 'Centro (Ciudad de México, Guadalajara)' },
    { id: 'America/Cancun', nombre: 'Sureste (Cancún)' },
    { id: 'America/Mazatlan', nombre: 'Pacífico (Mazatlán, La Paz)' },
    { id: 'America/Tijuana', nombre: 'Noroeste (Tijuana)' },
  ],
  Panamá: [{ id: 'America/Panama', nombre: 'Ciudad de Panamá' }],
  Paraguay: [{ id: 'America/Asuncion', nombre: 'Asunción' }],
  Perú: [{ id: 'America/Lima', nombre: 'Lima' }],
  'Puerto Rico': [{ id: 'America/Puerto_Rico', nombre: 'San Juan' }],
  'República Dominicana': [{ id: 'America/Santo_Domingo', nombre: 'Santo Domingo' }],
  Uruguay: [{ id: 'America/Montevideo', nombre: 'Montevideo' }],
  Venezuela: [{ id: 'America/Caracas', nombre: 'Caracas' }],
  Alemania: [{ id: 'Europe/Berlin', nombre: 'Berlín' }],
  Francia: [{ id: 'Europe/Paris', nombre: 'París' }],
  Italia: [{ id: 'Europe/Rome', nombre: 'Roma' }],
  'Países Bajos': [{ id: 'Europe/Amsterdam', nombre: 'Ámsterdam' }],
  Portugal: [{ id: 'Europe/Lisbon', nombre: 'Lisboa' }],
  'Reino Unido': [{ id: 'Europe/London', nombre: 'Londres' }],
  Suiza: [{ id: 'Europe/Zurich', nombre: 'Zúrich' }],
  Australia: [
    { id: 'Australia/Sydney', nombre: 'Este (Sídney, Melbourne)' },
    { id: 'Australia/Perth', nombre: 'Oeste (Perth)' },
  ],
  Japón: [{ id: 'Asia/Tokyo', nombre: 'Tokio' }],
};

/** Texto de "País y zona horaria" en la confirmación: "Colombia" o "España · Península y Baleares (Madrid)". */
export function textoZona(pais: string, zona: string): string {
  if (pais === PAIS_NEGOCIO) return pais;
  const nombre = PAISES[pais]?.find((z) => z.id === zona)?.nombre ?? zona.split('/').pop()?.replace(/_/g, ' ') ?? zona;
  return `${pais} · ${nombre}`;
}
