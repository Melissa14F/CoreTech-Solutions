import { Link } from 'react-router-dom';

/** Logo y nombre de CoreTech; lleva al sitio público. */
export function Marca({ etiqueta = 'CoreTech Solutions, ir al inicio' }: { etiqueta?: string }) {
  return (
    <Link className="marca" to="/" aria-label={etiqueta}>
      <img className="marca__logo" src="/logo.svg" alt="" width={30} height={30} />
      <span>CoreTech</span>
    </Link>
  );
}
