import { MAX_PASOS, aEditable, aEnvio, huella, nuevaFase, porcentajeFase, validarPlan, type FaseEditable } from './plan';

/** Plan válido de `n` fases con un paso cada una; la primera la aprueba el cliente. */
function plan(n: number, pasos = 1): FaseEditable[] {
  return Array.from({ length: n }, (_, i) => {
    const fase = nuevaFase();
    fase.nombre = `Fase ${i + 1}`;
    fase.apruebaCliente = i === 0;
    fase.pasos = Array.from({ length: pasos }, (__, p) => ({ ...fase.pasos[0]!, clave: `p${i}-${p}`, titulo: `Paso ${p + 1}` }));
    return fase;
  });
}

describe('Validación del plan (RN-28, HU-27)', () => {
  it('acepta de 2 a 8 fases', () => {
    for (const n of [2, 8]) {
      const errores = validarPlan(plan(n));
      expect(errores.generales).toEqual([]);
      expect(errores.porCampo).toEqual({});
    }
  });

  it('con 1 fase pide al menos 2', () => {
    expect(validarPlan(plan(1)).generales).toEqual(['El plan necesita al menos 2 fases']);
  });

  it('con 9 fases permite máximo 8', () => {
    expect(validarPlan(plan(9)).generales).toEqual(['El plan puede tener máximo 8 fases']);
  });

  it('acepta 12 pasos en una fase y rechaza 13', () => {
    expect(validarPlan(plan(2, MAX_PASOS)).porCampo).toEqual({});
    expect(validarPlan(plan(2, MAX_PASOS + 1)).porCampo['fases[0].pasos']).toBe('Cada fase puede tener máximo 12 pasos.');
  });

  it('un nombre repetido, sin importar mayúsculas ni espacios, se marca en la segunda fase', () => {
    const fases = plan(2);
    fases[1]!.nombre = '  FASE 1 ';
    expect(validarPlan(fases).porCampo).toEqual({ 'fases[1].nombre': 'Ya hay una fase con ese nombre en este servicio' });
  });

  it('pide marcar al menos una fase que apruebe el cliente', () => {
    const fases = plan(3).map((f) => ({ ...f, apruebaCliente: false }));
    expect(validarPlan(fases).generales).toEqual(['Marca al menos una fase que apruebe el cliente']);
  });

  it('una fase sin nombre o con un paso sin título se lista como incompleta', () => {
    const fases = plan(3);
    fases[1]!.nombre = ' ';
    fases[2]!.pasos[0]!.titulo = 'ab';
    const errores = validarPlan(fases);
    expect(errores.generales).toEqual(['Fase 2: completa el nombre y los pasos', 'Fase 3: completa el nombre y los pasos']);
    expect(errores.porCampo['fases[1].nombre']).toBe('Escribe el nombre de la fase (al menos 3 caracteres).');
    expect(errores.porCampo['fases[2].pasos[0].titulo']).toBe('Escribe qué se hace en este paso.');
  });

  it('una fase sin pasos pide agregar uno', () => {
    const fases = plan(2);
    fases[0]!.pasos = [];
    expect(validarPlan(fases).porCampo['fases[0].pasos']).toBe('Agrega al menos un paso.');
  });

  it('revisa el largo del nombre, de "qué es" y de la explicación', () => {
    const fases = plan(2);
    fases[0]!.nombre = 'x'.repeat(61);
    fases[0]!.queEs = 'x'.repeat(141);
    fases[1]!.pasos[0]!.titulo = 'x'.repeat(81);
    fases[1]!.pasos[0]!.explicacion = 'x'.repeat(161);
    const { porCampo } = validarPlan(fases);
    expect(Object.keys(porCampo)).toEqual([
      'fases[0].nombre', 'fases[0].queEs', 'fases[1].pasos[0].titulo', 'fases[1].pasos[0].explicacion',
    ]);
  });
});

describe('Conversión del plan', () => {
  it('cada fase vale 100 % entre el número de fases', () => {
    expect(porcentajeFase(5)).toBe('20 %');
    expect(porcentajeFase(6)).toBe('16,7 %');
    expect(porcentajeFase(8)).toBe('12,5 %');
  });

  it('envía los textos recortados y los opcionales vacíos como nulos, sin las claves locales', () => {
    const [fase] = aEditable([{ orden: 1, nombre: ' Diseño ', queEs: null, apruebaCliente: true, pasos: [{ orden: 1, titulo: ' Bocetos ', explicacion: null }] }]);
    expect(aEnvio([fase!])).toEqual({
      fases: [{ nombre: 'Diseño', queEs: null, apruebaCliente: true, pasos: [{ titulo: 'Bocetos', explicacion: null }] }],
    });
  });

  it('la huella no cambia por los espacios ni por las claves, y sí por el contenido', () => {
    const fases = plan(2);
    const copia = aEditable(aEnvio(fases).fases.map((f, i) => ({ ...f, orden: i + 1, pasos: f.pasos.map((p, j) => ({ ...p, orden: j + 1 })) })));
    expect(huella(copia)).toBe(huella(fases));
    fases[0]!.nombre = 'Otro nombre';
    expect(huella(copia)).not.toBe(huella(fases));
  });
});
