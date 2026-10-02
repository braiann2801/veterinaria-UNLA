"use client"

/**
 * Piezas visuales compartidas por los modulos conectados a la API.
 *
 * Los estados de carga y error estan centralizados para que la UI sea
 * consistente: si cada pantalla inventara su propio spinner, el operador
 * aprenderia a no mirar ninguno de ellos.
 */

import type { ReactNode } from "react"
import { AlertCircle, Loader2, RefreshCw, Inbox } from "lucide-react"

import { ApiError } from "@/lib/api"
import { mensajeDeError, type EstadoApi } from "@/lib/use-api"
import { cn } from "@/lib/utils"

/** Ancho de la zona de toque tactil (regla 3.4 de AGENTS.md). */
export const TOQUE_MINIMO = "min-h-[44px]"

export function Cargando({ texto = "Cargando" }: { texto?: string }) {
  return (
    <div
      className="flex items-center gap-2 py-8 text-sm text-muted-foreground"
      role="status"
      aria-live="polite"
    >
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {texto}
    </div>
  )
}

export function ErrorBox({
  error,
  onReintentar,
}: {
  error: ApiError | null
  onReintentar?: () => void
}) {
  if (!error) return null
  return (
    <div
      className="flex flex-col gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm"
      role="alert"
    >
      <div className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden />
        <p className="leading-relaxed">{mensajeDeError(error)}</p>
      </div>
      {onReintentar && (
        <div>
          <button
            onClick={onReintentar}
            className={cn(
              "inline-flex items-center gap-2 rounded-md border border-destructive/40 px-3 text-xs font-medium text-destructive transition-colors hover:bg-destructive/10",
              TOQUE_MINIMO,
            )}
          >
            <RefreshCw className="size-3.5" aria-hidden />
            Reintentar
          </button>
        </div>
      )}
    </div>
  )
}

export function Vacio({ texto }: { texto: string }) {
  return (
    <div className="flex flex-col items-center gap-2 py-10 text-center text-sm text-muted-foreground">
      <Inbox className="size-6 opacity-60" aria-hidden />
      <p>{texto}</p>
    </div>
  )
}

/**
 * Envoltura estandar de un recurso.
 *
 * Concentra el orden carga -> error -> vacio -> contenido, que es el que todos
 * los modulos necesitan y que cada uno terminaba haciendo distinto.
 */
export function Recurso<T>({
  estado,
  children,
  textoCarga,
  textoVacio,
  onReintentar,
}: {
  estado: EstadoApi<T>
  children: (datos: T) => ReactNode
  textoCarga?: string
  textoVacio?: string
  onReintentar?: () => void
}) {
  if (estado.cargando && estado.datos === null) return <Cargando texto={textoCarga} />
  if (estado.error && estado.datos === null) {
    return <ErrorBox error={estado.error} onReintentar={onReintentar ?? estado.recargar} />
  }
  if (estado.datos === null) return null
  if (textoVacio && esVacio(estado.datos)) return <Vacio texto={textoVacio} />
  return <>{children(estado.datos)}</>
}

function esVacio(datos: unknown) {
  if (Array.isArray(datos)) return datos.length === 0
  if (datos instanceof Map) return datos.size === 0
  return false
}

/**
 * Semáforo de egreso.
 *
 * Un solo lugar decide el color, para que "verde" signifique exactamente lo
 * mismo en el mostrador, en la lista de mascotas y en el modal de retiro.
 */
export function Semforo({
  tono,
  children,
}: {
  tono: "verde" | "ambar" | "rojo"
  children: ReactNode
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium",
        tono === "verde" && "bg-emerald-100 text-emerald-900",
        tono === "ambar" && "bg-amber-100 text-amber-900",
        tono === "rojo" && "bg-red-100 text-red-900",
      )}
    >
      {children}
    </span>
  )
}

/** Formato de dinero en pesos argentinos, sin depender de Intl. */
export function money(n: number | null | undefined) {
  const valor = n ?? 0
  const entero = Math.trunc(Math.abs(valor))
  const miles = entero.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".")
  return `${valor < 0 ? "-" : ""}$${miles}${Math.round(valor) !== entero ? ",00" : ""}`
}