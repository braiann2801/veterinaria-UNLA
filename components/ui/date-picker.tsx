"use client"

import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from "react"
import { createPortal } from "react-dom"
import { CalendarDays, ChevronDown } from "lucide-react"
import { Calendar } from "@heroui/react"
import { getLocalTimeZone, parseDate, today, type CalendarDate } from "@internationalized/date"
import { I18nProvider } from "react-aria-components"

import { hoyLocal } from "@/lib/api"
import { cn } from "@/lib/utils"
import { TOQUE_MINIMO } from "@/lib/vista"

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

/**
 * `YYYY-MM-DD` a la fecha sin hora de HeroUI. `parseDate` tira con un texto
 * mal formado: se devuelve `undefined` para que el calendario arranque vacio.
 */
function aFechaCalendario(iso: string | undefined): CalendarDate | undefined {
  if (!iso) return undefined
  try {
    return parseDate(iso)
  } catch {
    return undefined
  }
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

/** La fecha dentro de [min, max]: cambiar el año puede sacarla del rango. */
function acotar(fecha: CalendarDate, min?: CalendarDate, max?: CalendarDate): CalendarDate {
  if (min && fecha.compare(min) < 0) return min
  if (max && fecha.compare(max) > 0) return max
  return fecha
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
 * El calendario es el `Calendar` de HeroUI con selector de año (el mismo que
 * usa el rango de la guarderia): tocando el encabezado se elige el año sin ir
 * mes por mes, util para fechas de nacimiento. Los colores salen del tema con
 * la clase `calendario-tema` (ver `app/globals.css`).
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
  // Foco del calendario (el mes que se ve) y selector de año, controlados: ver
  // `alCambiarFoco`.
  const [foco, setFoco] = useState<CalendarDate>(() => today(getLocalTimeZone()))
  const [eligiendoAnio, setEligiendoAnio] = useState(false)
  const contenedorRef = useRef<HTMLDivElement>(null)
  const disparadorRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const fondoRef = useRef<HTMLDivElement>(null)

  const fechaSeleccionada = value ? aFecha(value) : undefined
  const minimo = aFechaCalendario(min)
  const maximo = aFechaCalendario(max)

  /**
   * Al elegir un año, el selector de HeroUI mueve el foco al 31 de diciembre de
   * ese año: le pasa a react-aria todo el rango 1900-2099 y react-aria arma la
   * lista de años desde ese borde. Se conserva el mes y el dia que se estaban
   * viendo y solo cambia el año.
   */
  function alCambiarFoco(nuevo: CalendarDate) {
    setFoco(eligiendoAnio && nuevo.year !== foco.year ? acotar(foco.set({ year: nuevo.year }), minimo, maximo) : nuevo)
  }

  function alternar() {
    if (abierto) {
      setAbierto(false)
      return
    }
    setEsMovil(window.matchMedia(CONSULTA_MOVIL).matches)
    setPosicion(null)
    setFoco(acotar(aFechaCalendario(value) ?? today(getLocalTimeZone()), minimo, maximo))
    setEligiendoAnio(false)
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

  const texto = fechaSeleccionada ? FORMATO_FECHA.format(fechaSeleccionada) : placeholder

  const contenido = (
    <>
      {/* es-AR: meses y dias en español y la semana arranca el lunes. */}
      <I18nProvider locale="es-AR">
        <Calendar
          aria-label={label ?? "Fecha"}
          value={aFechaCalendario(value) ?? null}
          minValue={minimo}
          maxValue={maximo}
          focusedValue={foco}
          onFocusChange={alCambiarFoco}
          isYearPickerOpen={eligiendoAnio}
          onYearPickerOpenChange={setEligiendoAnio}
          onChange={(dia: CalendarDate) => {
            // CalendarDate.toString() ya es `YYYY-MM-DD`, el formato del backend.
            onChange(dia.toString())
            setAbierto(false)
          }}
          className={cn("calendario-tema w-full p-3", esMovil && "mx-auto max-w-sm")}
        >
          <Calendar.Header>
            <Calendar.YearPickerTrigger className={TOQUE_MINIMO}>
              <Calendar.YearPickerTriggerHeading className="whitespace-nowrap text-sm font-medium first-letter:uppercase" />
              <Calendar.YearPickerTriggerIndicator />
            </Calendar.YearPickerTrigger>
            <Calendar.NavButton slot="previous" className="size-11" />
            <Calendar.NavButton slot="next" className="size-11" />
          </Calendar.Header>
          <Calendar.Grid className="w-full">
            <Calendar.GridHeader>
              {(dia) => (
                <Calendar.HeaderCell className="text-xs font-normal text-muted-foreground">{dia}</Calendar.HeaderCell>
              )}
            </Calendar.GridHeader>
            {/* Celdas de 44 px: area de toque minima (regla 3.4 de AGENTS.md). */}
            <Calendar.GridBody>{(dia) => <Calendar.Cell date={dia} className="size-11 text-sm" />}</Calendar.GridBody>
          </Calendar.Grid>
          <Calendar.YearPickerGrid>
            <Calendar.YearPickerGridBody>
              {({ year }) => <Calendar.YearPickerCell year={year} className="min-h-[44px] text-sm" />}
            </Calendar.YearPickerGridBody>
          </Calendar.YearPickerGrid>
        </Calendar>
      </I18nProvider>
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
