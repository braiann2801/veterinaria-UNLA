"use client"

import { useEffect, useRef, useState } from "react"
import { CalendarDays, ChevronDown } from "lucide-react"
import type { Matcher } from "react-day-picker"

import { Calendar } from "@/components/ui/calendar"
import { hoyLocal } from "@/lib/api"
import { cn } from "@/lib/utils"

const FORMATO_FECHA = new Intl.DateTimeFormat("es-AR", {
  weekday: "short",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
})

/** `YYYY-MM-DD` local a `Date`, sin pasar por `new Date(iso)` (que asume UTC). */
function aFecha(iso: string): Date | undefined {
  const [anio, mes, dia] = iso.split("-").map(Number)
  if (!anio || !mes || !dia) return undefined
  return new Date(anio, mes - 1, dia)
}

/** `Date` a `YYYY-MM-DD` local, que es lo que espera el backend. */
function aIso(fecha: Date): string {
  const anio = fecha.getFullYear()
  const mes = String(fecha.getMonth() + 1).padStart(2, "0")
  const dia = String(fecha.getDate()).padStart(2, "0")
  return `${anio}-${mes}-${dia}`
}

export interface DatePickerProps {
  /** Fecha seleccionada en `YYYY-MM-DD`. */
  value: string
  onChange: (fecha: string) => void
  label?: string
  id?: string
  /** Extremos opcionales en `YYYY-MM-DD`: deshabilitan los dias fuera del rango. */
  min?: string
  max?: string
  disabled?: boolean
  className?: string
  placeholder?: string
}

/**
 * Selector de fecha desplegable.
 *
 * Es un boton estilizado que muestra la fecha elegida y despliega un calendario
 * en un popover; al elegir un dia el popover se repliega solo y el boton vuelve
 * a quedar como disparador. Reemplaza al `<input type="date">` nativo, que en
 * movil ocupa toda la pantalla con el control del sistema y rompe la estetica.
 *
 * Reutilizable en cualquier modulo: recibe y devuelve la fecha como
 * `YYYY-MM-DD` (el formato del backend) y no sabe nada del dato que filtra.
 * El atajo "Hoy" usa `hoyLocal()` para no caer en el corrimiento de UTC.
 */
export function DatePicker({
  value,
  onChange,
  label,
  id,
  min,
  max,
  disabled = false,
  className,
  placeholder = "Elegir fecha",
}: DatePickerProps) {
  const [abierto, setAbierto] = useState(false)
  const contenedorRef = useRef<HTMLDivElement>(null)
  const disparadorRef = useRef<HTMLButtonElement>(null)

  const fechaSeleccionada = value ? aFecha(value) : undefined

  // Cierre por click afuera y por Escape. Escape devuelve el foco al boton para
  // que la navegacion por teclado no se pierda al cerrar.
  useEffect(() => {
    if (!abierto) return

    function alPresionarFuera(evento: PointerEvent) {
      if (!contenedorRef.current?.contains(evento.target as Node)) {
        setAbierto(false)
      }
    }
    function alPresionarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") {
        setAbierto(false)
        disparadorRef.current?.focus()
      }
    }

    document.addEventListener("pointerdown", alPresionarFuera)
    document.addEventListener("keydown", alPresionarTecla)
    return () => {
      document.removeEventListener("pointerdown", alPresionarFuera)
      document.removeEventListener("keydown", alPresionarTecla)
    }
  }, [abierto])

  const noSeleccionables: Matcher[] = []
  const desde = min ? aFecha(min) : undefined
  const hasta = max ? aFecha(max) : undefined
  if (desde) noSeleccionables.push({ before: desde })
  if (hasta) noSeleccionables.push({ after: hasta })

  const texto = fechaSeleccionada ? FORMATO_FECHA.format(fechaSeleccionada) : placeholder

  return (
    <div ref={contenedorRef} className={cn("relative flex flex-col gap-1.5", className)}>
      {label && (
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
      )}
      <button
        ref={disparadorRef}
        type="button"
        id={id}
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-label={`${label ?? "Fecha"}: ${texto}`}
        onClick={() => setAbierto((previo) => !previo)}
        className={cn(
          "inline-flex min-h-[44px] w-full items-center justify-between gap-2 rounded-xl border border-input bg-background px-3 text-sm transition-colors",
          "hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          "active:scale-[.99] disabled:pointer-events-none disabled:opacity-50",
          abierto && "border-foreground",
        )}
      >
        <span className="inline-flex min-w-0 items-center gap-2">
          <CalendarDays className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <span className={cn("truncate font-medium", !fechaSeleccionada && "text-muted-foreground")}>
            {texto}
          </span>
        </span>
        <ChevronDown
          className={cn(
            "size-4 shrink-0 text-muted-foreground transition-transform",
            abierto && "rotate-180",
          )}
          aria-hidden
        />
      </button>

      {abierto && (
        <div
          role="dialog"
          aria-label="Selector de fecha"
          className="absolute left-0 top-full z-50 mt-2 max-h-[min(28rem,75vh)] w-[min(22rem,calc(100vw-1rem))] origin-top overflow-y-auto rounded-2xl border border-border bg-card shadow-lg animate-in fade-in-0 zoom-in-95"
        >
          <Calendar
            mode="single"
            selected={fechaSeleccionada}
            defaultMonth={fechaSeleccionada ?? new Date()}
            disabled={noSeleccionables.length > 0 ? noSeleccionables : undefined}
            onSelect={(dia) => {
              if (!dia) return
              onChange(aIso(dia))
              setAbierto(false)
            }}
          />
          <div className="flex items-center justify-between gap-2 border-t border-border px-3 py-2">
            <button
              type="button"
              onClick={() => {
                onChange(hoyLocal())
                setAbierto(false)
              }}
              className="rounded-lg px-2 py-1 text-xs font-medium text-primary transition-colors hover:bg-muted"
            >
              Hoy
            </button>
            <button
              type="button"
              onClick={() => setAbierto(false)}
              className="rounded-lg px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted"
            >
              Cerrar
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
