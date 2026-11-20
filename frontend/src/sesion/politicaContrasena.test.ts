import { contrasenaEsSegura, cumpleRegla, errorConfirmacion, errorContrasenaNueva } from './politicaContrasena';

describe('política de contraseña (RN-22, igual al backend)', () => {
  it('revisa cada requisito por separado', () => {
    expect(cumpleRegla('largo', 'Abc1!xy')).toBe(false);
    expect(cumpleRegla('largo', 'Abc1!xyz')).toBe(true);
    expect(cumpleRegla('mayuscula', 'ñandú1!')).toBe(false);
    expect(cumpleRegla('mayuscula', 'Ñandú1!')).toBe(true);
    expect(cumpleRegla('numero', 'Segura!!')).toBe(false);
    expect(cumpleRegla('simbolo', 'Segura2026')).toBe(false);
    expect(cumpleRegla('simbolo', 'Segura2026!')).toBe(true);
  });

  it('un espacio no cuenta como símbolo, como en el backend', () => {
    expect(cumpleRegla('simbolo', 'Segura 2026')).toBe(false);
  });

  it('acepta una contraseña segura y rechaza una de más de 64 caracteres', () => {
    expect(contrasenaEsSegura('Demo2026!')).toBe(true);
    expect(errorContrasenaNueva(`Aa1!${'x'.repeat(61)}`, 'vacío')).toBe('La contraseña es demasiado larga: usa máximo 64 caracteres.');
  });

  it('da los mensajes del prototipo', () => {
    expect(errorContrasenaNueva('', 'Crea una contraseña.')).toBe('Crea una contraseña.');
    expect(errorContrasenaNueva('corta', 'x')).toBe('La contraseña aún no cumple todos los requisitos.');
    expect(errorConfirmacion('Demo2026!', '', 'Escribe de nuevo la contraseña.')).toBe('Escribe de nuevo la contraseña.');
    expect(errorConfirmacion('Demo2026!', 'Demo2026?', 'x')).toBe('Las contraseñas no coinciden.');
    expect(errorConfirmacion('Demo2026!', 'Demo2026!', 'x')).toBeNull();
  });
});
