"use client"

/**
 * Paginación cuando el backend recorta la lista (`PaginaResponseDTO`).
 *
 * <p>A diferencia de `ui/paginado.tsx`, acá no se calcula el total con
 * `items.length`: ese número es el de la página actual, no el del catálogo. El
 * total y la cantidad de páginas vienen en la respuesta y los botones se
 * deshabilitan con esa información.</p>
 */

import { ChevronLeft, ChevronRight } from "lucide-react"

import { Select } from "@/components/ui/field"
import { TOQUE_MINIMO } from "@/lib/vista"
import { cn } from "@/lib/utils"

const OPCIONES = [10, 25, 50, 100] as const

export function PaginadoServidor({
  pagina,
  tamanio,
  totalElementos,
  totalPaginas,
  onChange,
  deshabilitado = false,
}: {
  pagina: number
  tamanio: number
  totalElementos: number
  totalPaginas: number
  onChange: (pagina: number, tamanio: number) => void
  deshabilitado?: boolean
}) {
  const desde = totalElementos === 0 ? 0 : pagina * tamanio + 1
  const hasta = Math.min((pagina + 1) * tamanio, totalElementos)

  return (
    <div className="@container mt-3 border-t border-border pt-3">
      <nav
        aria-label="Paginación"
        className="flex flex-col gap-3 text-sm @lg:flex-row @lg:items-center @lg:justify-between"
      >
        <label className="flex items-center gap-2 whitespace-nowrap text-muted-foreground">
          Mostrar
          <Select
            aria-label="Cantidad por página"
            value={tamanio}
            disabled={deshabilitado}
            onChange={(e) => onChange(0, Number(e.target.value))}
            className={cn("w-20 bg-background px-2 text-foreground", TOQUE_MINIMO)}
          >
            {OPCIONES.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </Select>
          por página
        </label>

        <div className="flex items-center justify-between gap-2 @lg:justify-end">
          <span className="whitespace-nowrap text-muted-foreground" aria-live="polite">
            {desde}–{hasta} de {totalElementos}
          </span>
          <div className="flex gap-1">
            <button
              type="button"
              onClick={() => onChange(pagina - 1, tamanio)}
              disabled={deshabilitado || pagina <= 0}
              aria-label="Página anterior"
              className={cn(
                "inline-flex min-w-[44px] items-center justify-center rounded-md border border-input transition-colors hover:bg-muted disabled:opacity-40",
                TOQUE_MINIMO,
              )}
            >
              <ChevronLeft className="size-4" aria-hidden />
            </button>
            <span className="inline-flex min-w-[4.5rem] items-center justify-center whitespace-nowrap text-xs text-muted-foreground">
              Pág. {pagina + 1} de {Math.max(1, totalPaginas)}
            </span>
            <button
              type="button"
              onClick={() => onChange(pagina + 1, tamanio)}
              disabled={deshabilitado || pagina + 1 >= totalPaginas}
              aria-label="Página siguiente"
              className={cn(
                "inline-flex min-w-[44px] items-center justify-center rounded-md border border-input transition-colors hover:bg-muted disabled:opacity-40",
                TOQUE_MINIMO,
              )}
            >
              <ChevronRight className="size-4" aria-hidden />
            </button>
          </div>
        </div>
      </nav>
    </div>
  )
}
