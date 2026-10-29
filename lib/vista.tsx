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

import { ApiError, type EspecieMascota } from "@/lib/api"
import { mensajeDeError, type EstadoApi } from "@/lib/use-api"
import { cn } from "@/lib/utils"

/** Especies del enum `Mascota.Especie` del backend, con la etiqueta que ve el operador. */
export const ESPECIES: { valor: EspecieMascota; etiqueta: string }[] = [
  { valor: "CANINO", etiqueta: "Canino" },
  { valor: "FELINO", etiqueta: "Felino" },
  { valor: "AVE", etiqueta: "Ave" },
  { valor: "ROEDOR", etiqueta: "Roedor" },
  { valor: "REPTIL", etiqueta: "Reptil" },
  { valor: "OTRO", etiqueta: "Otro" },
]

/** Ancho de la zona de toque tactil (regla 3.4 de AGENTS.md). */
export const TOQUE_MINIMO = "min-h-[44px]"

/**
 * DNI con puntos de miles para mostrar ("30123456" -> "30.123.456"). El backend
 * lo guarda solo con digitos; esto es solo presentacion, nunca va a la API.
 */
export function formatearDni(dni: string): string {
  return /^\d+$/.test(dni) ? dni.replace(/\B(?=(\d{3})+(?!\d))/g, ".") : dni
}

/**
 * Filtros de entrada: se aplican en el `onChange` para que una letra nunca
 * llegue a escribirse. El backend sigue validando, pero esta es la primera
 * barrera. Al pegar "30.123.456" los puntos se descartan y queda "30123456".
 */

/** Solo dígitos, con tope opcional (el DNI argentino tiene 7 u 8). */
export function soloDigitos(valor: string, max?: number): string {
  const limpio = valor.replace(/\D/g, "")
  return max === undefined ? limpio : limpio.slice(0, max)
}

/** DNI: solo dígitos, hasta 8. */
export function soloDni(valor: string): string {
  return soloDigitos(valor, 8)
}

/** Teléfono: dígitos y los separadores que se usan al escribirlo (+, espacio, guion, paréntesis, punto). */
export function soloTelefono(valor: string): string {
  return valor.replace(/[^\d+\s().-]/g, "").slice(0, 20)
}

/** Número con decimales: dígitos y un único separador decimal (la coma se convierte en punto). */
export function soloDecimal(valor: string): string {
  const limpio = valor.replace(/,/g, ".").replace(/[^\d.]/g, "")
  const punto = limpio.indexOf(".")
  return punto === -1 ? limpio : limpio.slice(0, punto + 1) + limpio.slice(punto + 1).replace(/\./g, "")
}

/** Filtro que corresponde a un `inputMode`; el resto de los modos no se filtra. */
export function filtrarPorModo(modo: string | undefined, valor: string): string {
  if (modo === "numeric") return soloDni(valor)
  if (modo === "tel") return soloTelefono(valor)
  if (modo === "decimal") return soloDecimal(valor)
  return valor
}

/** Quita puntos, espacios y guiones de un DNI, igual que el backend antes de validarlo. */
export function limpiarDni(dni: string): string {
  return dni.replace(/[.\s-]/g, "")
}

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
      className="flex flex-col gap-3 rounded-xl border border-destructive/40 bg-destructive/10 p-4 text-sm"
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
        tono === "verde" && "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300",
        tono === "ambar" && "bg-amber-500/15 text-amber-700 dark:text-amber-300",
        tono === "rojo" && "bg-red-500/15 text-red-700 dark:text-red-300",
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
/**
 * Fecha legible `dd/mm/aaaa` a partir de un ISO del backend (`2026-10-28` o
 * `2026-10-28T00:00:00`).
 *
 * Se toma el texto tal cual y no se pasa por `Date`: `new Date("2026-10-28")` es
 * medianoche UTC, y en UTC-3 se mostraría el día anterior.
 */
export function fechaCorta(iso: string | null | undefined) {
  if (!iso) return ""
  const [anio, mes, dia] = iso.slice(0, 10).split("-")
  return anio && mes && dia ? `${dia}/${mes}/${anio}` : iso
}
