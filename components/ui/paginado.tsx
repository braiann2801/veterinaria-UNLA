"use client"

/**
 * Paginación del lado del cliente para las listas que crecen sin límite
 * (Padrón, historiales, Profesionales, Usuarios, movimientos del Balance).
 *
 * <p>Las listas ya llegan completas de la API y el volumen de un consultorio es
 * chico, así que se pagina en memoria y el backend no cambia. Si el padrón
 * llegara a miles de registros, convendría paginar en el servidor.</p>
 *
 * <p>Se usa como render-prop porque las listas viven dentro de `Recurso`, donde
 * no se puede llamar a un hook:</p>
 *
 * <pre>
 * &lt;Paginado items={lista} clave="padron-tutores"&gt;
 *   {(visibles) =&gt; &lt;ul&gt;{visibles.map(...)}&lt;/ul&gt;}
 * &lt;/Paginado&gt;
 * </pre>
 *
 * <p>La cantidad por página se recuerda por lista (`clave`) en este navegador.
 * Es una comodidad: si el storage no está disponible, se usa el valor por
 * defecto.</p>
 */

import { useEffect, useState, type ReactNode } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"

import { TOQUE_MINIMO } from "@/lib/vista"
import { cn } from "@/lib/utils"

export const OPCIONES_POR_PAGINA = [10, 25, 50, 100] as const
const POR_DEFECTO = 10
const PREFIJO = "veterinaria.porPagina."

function leerPorPagina(clave: string): number {
  try {
    const valor = Number(window.localStorage.getItem(PREFIJO + clave))
    return (OPCIONES_POR_PAGINA as readonly number[]).includes(valor) ? valor : POR_DEFECTO
  } catch {
    return POR_DEFECTO
  }
}

function guardarPorPagina(clave: string, valor: number) {
  try {
    window.localStorage.setItem(PREFIJO + clave, String(valor))
  } catch {
    // Sin storage la preferencia dura hasta recargar.
  }
}

export function Paginado<T>({
  items,
  clave,
  reiniciarCon,
  children,
}: {
  items: T[]
  /** Identifica la lista para recordar su cantidad por página. */
  clave: string
  /** Al cambiar este valor (por ejemplo, el texto de un filtro) se vuelve a la página 1. */
  reiniciarCon?: unknown
  children: (visibles: T[]) => ReactNode
}) {
  const [porPagina, setPorPagina] = useState(() =>
    typeof window === "undefined" ? POR_DEFECTO : leerPorPagina(clave),
  )
  const [pagina, setPagina] = useState(1)

  useEffect(() => {
    setPagina(1)
  }, [reiniciarCon])

  const total = items.length
  const totalPaginas = Math.max(1, Math.ceil(total / porPagina))
  // Si se borra el último registro de la última página, no quedarse en una página vacía.
  const actual = Math.min(pagina, totalPaginas)
  const desde = (actual - 1) * porPagina
  const visibles = items.slice(desde, desde + porPagina)

  const cambiarPorPagina = (valor: number) => {
    setPorPagina(valor)
    setPagina(1)
    guardarPorPagina(clave, valor)
  }

  return (
    <>
      {children(visibles)}
      {total > OPCIONES_POR_PAGINA[0] && (
        // @container: el diseño depende del ancho de la lista, no de la ventana. En
        // una columna angosta (los Pacientes del Historial en pantalla completa)
        // un breakpoint de viewport la ponía en una fila que desbordaba la tarjeta.
        <div className="@container mt-3 border-t border-border pt-3">
        <nav
          aria-label="Paginación"
          className="flex flex-col gap-3 text-sm @lg:flex-row @lg:items-center @lg:justify-between"
        >
          <label className="flex items-center gap-2 whitespace-nowrap text-muted-foreground">
            Mostrar
            <select
              value={porPagina}
              onChange={(e) => cambiarPorPagina(Number(e.target.value))}
              className={cn("rounded-md border border-input bg-background px-2 text-foreground", TOQUE_MINIMO)}
              aria-label="Cantidad por página"
            >
              {OPCIONES_POR_PAGINA.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
            por página
          </label>

          <div className="flex items-center justify-between gap-2 @lg:justify-end">
            <span className="whitespace-nowrap text-muted-foreground" aria-live="polite">
              {desde + 1}–{Math.min(desde + porPagina, total)} de {total}
            </span>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => setPagina(actual - 1)}
                disabled={actual <= 1}
                aria-label="Página anterior"
                className={cn(
                  "inline-flex min-w-[44px] items-center justify-center rounded-md border border-input transition-colors hover:bg-muted disabled:opacity-40",
                  TOQUE_MINIMO,
                )}
              >
                <ChevronLeft className="h-4 w-4" aria-hidden />
              </button>
              <span className="inline-flex min-w-[4.5rem] items-center justify-center whitespace-nowrap text-xs text-muted-foreground">
                Pág. {actual} de {totalPaginas}
              </span>
              <button
                type="button"
                onClick={() => setPagina(actual + 1)}
                disabled={actual >= totalPaginas}
                aria-label="Página siguiente"
                className={cn(
                  "inline-flex min-w-[44px] items-center justify-center rounded-md border border-input transition-colors hover:bg-muted disabled:opacity-40",
                  TOQUE_MINIMO,
                )}
              >
                <ChevronRight className="h-4 w-4" aria-hidden />
              </button>
            </div>
          </div>
        </nav>
        </div>
      )}
    </>
  )
}
