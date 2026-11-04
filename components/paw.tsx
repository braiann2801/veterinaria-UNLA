/**
 * Huella decorativa de la marca (hero y CTA de la landing, y login).
 *
 * Es una forma de marca del diseño, no un ícono de interfaz: por eso no sale de
 * `lucide-react`, que solo tiene trazos y acá hace falta relleno.
 */
export function Paw({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 100 100" fill="currentColor" aria-hidden className={className}>
      <ellipse cx="50" cy="66" rx="24" ry="20" />
      <ellipse cx="22" cy="40" rx="10" ry="13" />
      <ellipse cx="40" cy="24" rx="10" ry="13" />
      <ellipse cx="60" cy="24" rx="10" ry="13" />
      <ellipse cx="78" cy="40" rx="10" ry="13" />
    </svg>
  )
}
