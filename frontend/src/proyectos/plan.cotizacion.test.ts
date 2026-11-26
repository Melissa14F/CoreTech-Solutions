import { fechaLegible } from './admin';
import {
  aEnvioPlan, conPuntos, cotizacionDe, fechaConDia, nombrePila, soloDigitos, sumarDiasIso, ultimaFechaDeEntrega, validarCotizacion,
  type CotizacionEditable,
} from './plan';
import { PLAN_BORRADOR, PLAN_ENVIADO } from '../pruebas/plan';

const VALIDA: CotizacionEditable = { valor: '7800000', entrega: '2027-01-29', condiciones: 'Incluye 2 rondas de ajustes en cada fase.' };
const errores = (c: Partial<CotizacionEditable>) => validarCotizacion({ ...VALIDA, ...c }, '2026-11-16', fechaLegible);

describe('cotización del plan (RN-29)', () => {
  it('una cotización completa no tiene errores', () => {
    expect(errores({})).toEqual({});
  });

  it('el valor va de $1.000.000 a $200.000.000', () => {
    const mensaje = 'El valor debe estar entre $1.000.000 y $200.000.000.';
    expect(errores({ valor: '999999' }).valorCotizado).toBe(mensaje);
    expect(errores({ valor: '200000001' }).valorCotizado).toBe(mensaje);
    expect(errores({ valor: '1000000' }).valorCotizado).toBeUndefined();
    expect(errores({ valor: '200000000' }).valorCotizado).toBeUndefined();
    expect(errores({ valor: '' }).valorCotizado).toBe('Escribe el valor del proyecto.');
  });

  it('la entrega es al menos 14 días después del inicio y no pasa de 5 años desde hoy', () => {
    expect(errores({ entrega: '2026-11-29' }).fechaEstimadaEntrega)
      .toBe('La entrega debe ser al menos 2 semanas después del inicio (16 nov 2026).');
    expect(errores({ entrega: '2026-11-30' }).fechaEstimadaEntrega).toBeUndefined();
    const ultima = ultimaFechaDeEntrega();
    expect(errores({ entrega: ultima }).fechaEstimadaEntrega).toBeUndefined();
    expect(errores({ entrega: sumarDiasIso(ultima, 1) }).fechaEstimadaEntrega).toBe('Revisa el año de la fecha.');
    expect(errores({ entrega: '' }).fechaEstimadaEntrega).toBe('Elige la fecha estimada de entrega.');
  });

  it('qué incluye pide al menos 20 caracteres', () => {
    expect(errores({ condiciones: '' }).condicionesCotizacion).toBe('Escribe qué incluye el valor.');
    expect(errores({ condiciones: 'Muy corto' }).condicionesCotizacion).toBe('Escribe al menos 20 caracteres.');
  });
});

describe('última fecha de entrega', () => {
  it('es 5 años después de hoy, no una fecha fija', () => {
    expect(ultimaFechaDeEntrega(new Date(2026, 10, 21))).toBe('2031-11-21');
    expect(ultimaFechaDeEntrega(new Date(2040, 0, 1))).toBe('2045-01-01');
  });

  it('desde un 29 de febrero cae en el 28 si el año de llegada no es bisiesto, como en el backend', () => {
    expect(ultimaFechaDeEntrega(new Date(2028, 1, 29))).toBe('2033-02-28');
    expect(ultimaFechaDeEntrega(new Date(2031, 1, 28))).toBe('2036-02-28');
  });
});

describe('ayudas del plan', () => {
  it('pone puntos de miles y quita lo que no es dígito', () => {
    expect(conPuntos('7800000')).toBe('7.800.000');
    expect(conPuntos('999')).toBe('999');
    expect(soloDigitos('$ 7.800.000')).toBe('7800000');
  });

  it('suma días a una fecha y la escribe como el prototipo', () => {
    expect(sumarDiasIso('2026-11-16', 14)).toBe('2026-11-30');
    expect(sumarDiasIso('2026-12-25', 10)).toBe('2027-01-04');
    expect(fechaConDia('2026-11-17')).toBe('martes 17 nov');
  });

  it('llama al cliente por su nombre de pila', () => {
    expect(nombrePila('Laura Gómez')).toBe('Laura');
  });

  it('arma el cuerpo que se envía: valor numérico y vacíos como nulos', () => {
    const borrador = cotizacionDe(PLAN_BORRADOR);
    expect(borrador).toEqual({ valor: '', entrega: '2027-01-29', condiciones: '' });
    expect(aEnvioPlan([], borrador)).toMatchObject({ valorCotizado: null, condicionesCotizacion: null, fechaEstimadaEntrega: '2027-01-29' });
    expect(aEnvioPlan([], cotizacionDe(PLAN_ENVIADO))).toMatchObject({ valorCotizado: 7800000 });
  });
});
