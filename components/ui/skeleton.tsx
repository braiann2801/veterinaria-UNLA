import { cn } from "@/lib/utils"

/**
 * Bloque de carga con la forma del contenido.
 *
 * Se prefiere al spinner cuando ya se sabe que se viene (una grilla, una lista):
 * reserva el alto y evita el salto de layout cuando llegan los datos, y el
 * operador ve de un vistazo cuantas filas va a haber. `aria-hidden` porque no
 * aporta nada a un lector de pantalla: el estado de carga se anuncia aparte.
 */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn("animate-pulse rounded-xl bg-muted", className)} />
}
