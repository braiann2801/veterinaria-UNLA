"use client"

import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from "react"
import { createPortal } from "react-dom"
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

/** Separación entre el panel y el botón, y entre el panel y el borde de la pantalla. */
const MARGEN = 8
/** Ancho del panel en escritorio: el calendario de 7 celdas de 44 px más su padding. */
const ANCHO_PANEL = 352
/** Debajo de `sm` (640 px) el panel es una hoja inferior (regla 3.4 de AGENTS.md). */
const CONSULTA_MOVIL = "(max-width: 639px)"

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

/**
 * Dónde va el panel en escritorio: debajo del botón si entra, si no arriba, y
 * si no entra en ningún lado, pegado al borde inferior. Nunca sale de la
 * pantalla por los costados.
 */
function ubicarPanel(disparador: DOMRect, altoPanel: number): CSSProperties {
  const ancho = Math.min(ANCHO_PANEL, window.innerWidth - MARGEN * 2)
  const libreAbajo = window.innerHeight - disparador.bottom - MARGEN * 2
  const libreArriba = disparador.top - MARGEN * 2
  const top =
    altoPanel <= libreAbajo
      ? disparador.bottom + MARGEN
      : altoPanel <= libreArriba
        ? disparador.top - MARGEN - altoPanel
        : Math.max(MARGEN, window.innerHeight - altoPanel - MARGEN)
  const left = Math.min(Math.max(disparador.left, MARGEN), window.innerWidth - ancho - MARGEN)
  return { top, left, width: ancho }
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
  /** Muestra un boton "Limpiar" para vaciar la fecha (`onChange("")`). */
  limpiable?: boolean
}

/**
 * Selector de fecha desplegable.
 *
 * Es un boton estilizado que muestra la fecha elegida y despliega un calendario;
 * al elegir un dia el panel se repliega solo y el boton vuelve a quedar como
 * disparador. Reemplaza al `<input type="date">` nativo, que en movil ocupa toda
 * la pantalla con el control del sistema y rompe la estetica.
 *
 * El panel se dibuja en un portal sobre `body` con posicion fija. Antes era un
 * `absolute` dentro del formulario: adentro de un `Modal` se abria por debajo de
 * la pantalla, pisaba los campos siguientes y en el celular quedaba detras del
 * panel del dialogo. En escritorio se ubica junto al boton (arriba si abajo no
 * entra) y lo sigue al scrollear; en el celular es una hoja inferior.
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
  limpiable = false,
}: DatePickerProps) {
  const [abierto, setAbierto] = useState(false)
  const [esMovil, setEsMovil] = useState(false)
  // Sin posicion calculada el panel se monta invisible: asi se mide su alto
  // antes de mostrarlo y no aparece un instante en la esquina.
  const [posicion, setPosicion] = useState<CSSProperties | null>(null)
  const contenedorRef = useRef<HTMLDivElement>(null)
  const disparadorRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const fondoRef = useRef<HTMLDivElement>(null)

  const fechaSeleccionada = value ? aFecha(value) : undefined

  function alternar() {
    if (abierto) {
      setAbierto(false)
      return
    }
    setEsMovil(window.matchMedia(CONSULTA_MOVIL).matches)
    setPosicion(null)
    setAbierto(true)
  }

  // Ubicacion en escritorio. Se recalcula al scrollear (cualquier contenedor,
  // por eso en captura), al cambiar el tamaño de la ventana y cuando el panel
  // cambia de alto (un mes de 6 semanas o el selector de mes y año).
  useLayoutEffect(() => {
    if (!abierto || esMovil) return

    function ubicar() {
      const disparador = disparadorRef.current
      const panel = panelRef.current
      if (!disparador || !panel) return
      setPosicion(ubicarPanel(disparador.getBoundingClientRect(), panel.offsetHeight))
    }

    ubicar()
    const observador = new ResizeObserver(ubicar)
    if (panelRef.current) observador.observe(panelRef.current)
    window.addEventListener("resize", ubicar)
    window.addEventListener("scroll", ubicar, true)
    return () => {
      observador.disconnect()
      window.removeEventListener("resize", ubicar)
      window.removeEventListener("scroll", ubicar, true)
    }
  }, [abierto, esMovil])

  // Cierre por click afuera y por Escape. Escape devuelve el foco al boton para
  // que la navegacion por teclado no se pierda al cerrar. El keydown se escucha
  // en captura y se marca con preventDefault: dentro de un `Modal`, ese Escape
  // cierra solo el calendario y no el dialogo entero.
  useEffect(() => {
    if (!abierto) return

    function alPresionarFuera(evento: PointerEvent) {
      const destino = evento.target as Node
      // El fondo de la hoja inferior cierra con su propio click: si cerrara en
      // el pointerdown, el click terminaria en lo que quedo debajo.
      if (fondoRef.current?.contains(destino)) return
      if (!contenedorRef.current?.contains(destino) && !panelRef.current?.contains(destino)) {
        setAbierto(false)
      }
    }
    function alPresionarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") {
        evento.preventDefault()
        setAbierto(false)
        disparadorRef.current?.focus()
      }
    }

    document.addEventListener("pointerdown", alPresionarFuera)
    document.addEventListener("keydown", alPresionarTecla, true)
    return () => {
      document.removeEventListener("pointerdown", alPresionarFuera)
      document.removeEventListener("keydown", alPresionarTecla, true)
    }
  }, [abierto])

  const noSeleccionables: Matcher[] = []
  const desde = min ? aFecha(min) : undefined
  const hasta = max ? aFecha(max) : undefined
  if (desde) noSeleccionables.push({ before: desde })
  if (hasta) noSeleccionables.push({ after: hasta })

  const texto = fechaSeleccionada ? FORMATO_FECHA.format(fechaSeleccionada) : placeholder

  const contenido = (
    <>
      <Calendar
        mode="single"
        selected={fechaSeleccionada}
        defaultMonth={fechaSeleccionada ?? new Date()}
        startMonth={desde}
        endMonth={hasta}
        disabled={noSeleccionables.length > 0 ? noSeleccionables : undefined}
        className={cn(esMovil && "mx-auto")}
        onSelect={(dia) => {
          if (!dia) return
          onChange(aIso(dia))
          setAbierto(false)
        }}
      />
      <div className="flex items-center justify-between gap-2 border-t border-border px-3 py-2">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              onChange(hoyLocal())
              setAbierto(false)
            }}
            className="min-h-[44px] rounded-lg px-3 text-sm font-medium text-primary transition-colors hover:bg-muted"
          >
            Hoy
          </button>
          {limpiable && fechaSeleccionada && (
            <button
              type="button"
              onClick={() => {
                onChange("")
                setAbierto(false)
              }}
              className="min-h-[44px] rounded-lg px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted"
            >
              Limpiar
            </button>
          )}
        </div>
        <button
          type="button"
          onClick={() => setAbierto(false)}
          className="min-h-[44px] rounded-lg px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted"
        >
          Cerrar
        </button>
      </div>
    </>
  )

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
        onClick={alternar}
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

      {/* z-[70]: por encima del Modal (z-50), que es donde mas se usa. */}
      {abierto &&
        createPortal(
          esMovil ? (
            <>
              <div
                ref={fondoRef}
                className="fixed inset-0 z-[70] bg-black/30"
                onClick={() => setAbierto(false)}
                aria-hidden
              />
              <div
                ref={panelRef}
                role="dialog"
                aria-label="Selector de fecha"
                className="fixed inset-x-0 bottom-0 z-[71] max-h-[85vh] overflow-y-auto rounded-t-2xl border border-border bg-card pb-[env(safe-area-inset-bottom)] shadow-lg animate-in slide-in-from-bottom"
              >
                {contenido}
              </div>
            </>
          ) : (
            <div
              ref={panelRef}
              role="dialog"
              aria-label="Selector de fecha"
              style={posicion ?? { top: 0, left: 0, width: ANCHO_PANEL, visibility: "hidden" }}
              className="fixed z-[70] max-h-[calc(100vh-16px)] overflow-y-auto rounded-2xl border border-border bg-card shadow-lg"
            >
              {contenido}
            </div>
          ),
          document.body,
        )}
    </div>
  )
}
