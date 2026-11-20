/**
 * Política de contraseña (RN-22), igual a la del backend (PoliticaContrasena.java): mínimo 8 caracteres,
 * una mayúscula, un número y un símbolo (un carácter que no es letra, número ni espacio). Máximo 64
 * caracteres, porque BCrypt solo admite 72 bytes. La interfaz valida para ayudar; el backend siempre vuelve a validar.
 */
export const MAXIMO_CONTRASENA = 64;

export type ReglaContrasena = 'largo' | 'mayuscula' | 'numero' | 'simbolo';

export const REQUISITOS: { regla: ReglaContrasena; texto: string }[] = [
  { regla: 'largo', texto: 'Mínimo 8 caracteres' },
  { regla: 'mayuscula', texto: 'Una letra mayúscula' },
  { regla: 'numero', texto: 'Un número' },
  { regla: 'simbolo', texto: 'Un símbolo (!, #, @…)' },
];

const REGLAS: Record<ReglaContrasena, (valor: string) => boolean> = {
  largo: (valor) => valor.length >= 8,
  mayuscula: (valor) => /\p{Lu}/u.test(valor),
  numero: (valor) => /\p{Nd}/u.test(valor),
  simbolo: (valor) => /[^\p{L}\p{N}\s]/u.test(valor),
};

export function cumpleRegla(regla: ReglaContrasena, valor: string): boolean {
  return REGLAS[regla](valor);
}

export function contrasenaEsSegura(valor: string): boolean {
  return REQUISITOS.every(({ regla }) => cumpleRegla(regla, valor)) && valor.length <= MAXIMO_CONTRASENA;
}

/** Mensaje bajo el campo de una contraseña nueva, o null si está bien (textos del prototipo). */
export function errorContrasenaNueva(valor: string, mensajeVacio: string): string | null {
  if (!valor) return mensajeVacio;
  if (valor.length > MAXIMO_CONTRASENA) return 'La contraseña es demasiado larga: usa máximo 64 caracteres.';
  if (!contrasenaEsSegura(valor)) return 'La contraseña aún no cumple todos los requisitos.';
  return null;
}

/** Mensaje bajo la confirmación, o null si coincide. */
export function errorConfirmacion(contrasena: string, confirmacion: string, mensajeVacio: string): string | null {
  if (!confirmacion) return mensajeVacio;
  if (contrasena !== confirmacion) return 'Las contraseñas no coinciden.';
  return null;
}
