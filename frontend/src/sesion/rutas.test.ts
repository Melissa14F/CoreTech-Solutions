import { destinoAlIngresar, destinoSeguro, panelDeRol } from './rutas';

describe('destinos de la sesión', () => {
  it('cada rol tiene su panel (RF-02)', () => {
    expect(panelDeRol('CLIENTE')).toBe('/cliente/proyectos');
    expect(panelDeRol('PRODUCT_OWNER')).toBe('/equipo/proyectos');
    expect(panelDeRol('ADMINISTRADOR')).toBe('/admin');
  });

  it('solo acepta volver a una ruta de la aplicación', () => {
    expect(destinoSeguro('/cliente/citas')).toBe('/cliente/citas');
    expect(destinoSeguro('//otro-sitio.com')).toBeNull();
    expect(destinoSeguro('/\\otro-sitio.com')).toBeNull();
    expect(destinoSeguro('https://otro-sitio.com')).toBeNull();
    expect(destinoSeguro(null)).toBeNull();
  });

  it('al ingresar vuelve a la página pedida solo si es de su panel', () => {
    expect(destinoAlIngresar('CLIENTE', '/cliente/citas')).toBe('/cliente/citas');
    expect(destinoAlIngresar('CLIENTE', '/admin/usuarios')).toBe('/cliente/proyectos');
    expect(destinoAlIngresar('ADMINISTRADOR', '/admin')).toBe('/admin');
    expect(destinoAlIngresar('ADMINISTRADOR', '/administracion-falsa')).toBe('/admin');
    expect(destinoAlIngresar('PRODUCT_OWNER', null)).toBe('/equipo/proyectos');
  });
});
