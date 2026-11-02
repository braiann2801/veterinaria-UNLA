"use client"

/**
 * Interruptor para mostrar u ocultar los registros dados de baja. Los registros
 * no se borran (se desactivan), así que por defecto se ocultan para no ensuciar
 * las listas, pero siguen a un toque de distancia.
 */

import { cn } from "@/lib/utils"
import { TOQUE_MINIMO } from "@/lib/vista"

export function ToggleInactivos({
  valor,
  onChange,
  cantidad,
}: {
  valor: boolean
  onChange: (valor: boolean) => void
  /** Cuántos inactivos hay, para que se sepa si vale la pena mostrarlos. */
  cantidad: number
}) {
  return (
    <label className={cn("inline-flex cursor-pointer items-center gap-2 text-xs text-muted-foreground", TOQUE_MINIMO)}>
      <button
        type="button"
        role="switch"
        aria-checked={valor}
        onClick={() => onChange(!valor)}
        className={cn(
          "relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors",
          valor ? "bg-primary" : "bg-muted",
        )}
      >
        <span
          className={cn(
            "inline-block size-5 rounded-full bg-background shadow transition-transform",
            valor ? "translate-x-5" : "translate-x-0.5",
          )}
        />
      </button>
      Mostrar desactivados ({cantidad})
    </label>
  )
}
