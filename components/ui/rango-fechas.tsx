"use client"

/**
 * Selector de período: dos fechas, con atajos a los rangos que se miran de
 * verdad (hoy, la semana, el mes, el mes pasado, el año).
 *
 * <p>El Balance solo dejaba elegir una jornada. Las finanzas se miran por
 * período: del 12 al 19, el mes completo, el año. Con un solo día había que
 * consultar treinta veces y sumar a mano.</p>
 *
 * <p>Es un botón con calendario desplegable, igual que `DatePicker`: flotante
 * en escritorio y hoja inferior en el celular (regla 3.4 de AGENTS.md). La
 * mecánica del panel vive en `usePanelFlotante`, compartida por los dos, para
 * que el cierre por Escape y la ubicación no se comporten distinto según el
 * selector.</p>
 *
 * <p>Recibe y devuelve `YYYY-MM-DD`, el formato del backend, y no sabe qué dato
 * filtra. El día máximo es hoy: el Balance no muestra datos futuros.</p>
 */

import { useState, type ComponentProps } from "react"
import { createPortal } from "react-dom"
import { CalendarRange, ChevronDown } from "lucide-react"
import { RangeCalendar } from "@heroui/react"
import { getLocalTimeZone, parseDate, today } from "@internationalized/date"
import { I18nProvider } from "react-aria-components"

import { cn } from "@/lib/utils"
import { usePanelFlotante } from "@/lib/use-panel-flotante"
import { TOQUE_MINIMO } from "@/lib/vista"

/** Período inclusivo por los dos extremos, en `YYYY-MM-DD`. */
export interface Periodo {
  desde: string
  hasta: string
}

/** Ancho del panel en escritorio: el calendario de 7 celdas de 44 px más su padding. */
const ANCHO_PANEL = 352

const FORMATO_CORTO = new Intl.DateTimeFormat("es-AR", {
  day: "2-digit",
  month: "2-digit",
  year: "2-digit",
})

const FORMATO_LARGO = new Intl.DateTimeFormat("es-AR", {
  weekday: "short",
  day: "numeric",
  month: "short",
  year: "numeric",
  // Los valores se pasan a Date como UTC solo para formatearlos: con la zona del
  // navegador (UTC-3) los días se mostrarían corridos.
  timeZone: "UTC",
})

/** El tipo de rango que acepta el calendario de HeroUI, sin importar paquetes internos. */
type RangoCalendario = NonNullable<ComponentProps<typeof RangeCalendar>["value"]>

/** `YYYY-MM-DD` a `CalendarDate`; `undefined` si viene mal formado. */
function aCalendario(iso: string | undefined) {
  if (!iso) return undefined
  try {
    return parseDate(iso)
  } catch {
    return undefined
  }
}

function aRango(valor: Periodo | null): RangoCalendario | null {
  if (!valor) return null
  const start = aCalendario(valor.desde)
  const end = aCalendario(valor.hasta)
  return start && end ? { start, end } : null
}

/** Atajos a los períodos que el Dueño mira en el Balance. */
function atajos(hoy: ReturnType<typeof today>) {
  const inicioMesAnterior = hoy.set({ day: 1 }).subtract({ months: 1 })
  return [
    { etiqueta: "Hoy", rango: { start: hoy, end: hoy } },
    {
      etiqueta: "Últimos 7 días",
      rango: { start: hoy.subtract({ days: 6 }), end: hoy },
    },
    {
      etiqueta: "Este mes",
      rango: { start: hoy.set({ day: 1 }), end: hoy },
    },
    {
      etiqueta: "Mes anterior",
      // Primer día al último día del mes pasado: el add/subtract evita tener que
      // saber si el mes tiene 28, 30 o 31 días.
      rango: {
        start: inicioMesAnterior,
        end: inicioMesAnterior.add({ months: 1 }).subtract({ days: 1 }),
      },
    },
    {
      etiqueta: "Este año",
      rango: { start: hoy.set({ month: 1, day: 1 }), end: hoy },
    },
  ]
}

function EtiquetaAtajo({
  children,
  activo,
  onClick,
}: {
  children: string
  activo: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={activo}
      className={cn(
        "min-h-[44px] rounded-lg border px-3 text-xs font-medium transition-colors",
        activo
          ? "border-primary/40 bg-primary/10 text-foreground"
          : "border-border bg-background text-muted-foreground hover:bg-muted hover:text-foreground",
      )}
    >
      {children}
    </button>
  )
}

export interface RangoFechasPickerProps {
  value: Periodo | null
  onChange: (periodo: Periodo) => void
  label?: string
  id?: string
  disabled?: boolean
  className?: string
  /** Tope superior en `YYYY-MM-DD`; por defecto, hoy. */
  max?: string
}

export function RangoFechasPicker({
  value,
  onChange,
  label,
  id,
  disabled = false,
  className,
  max,
}: RangoFechasPickerProps) {
  const hoyCalendario = today(getLocalTimeZone())
  const maximo = aCalendario(max) ?? hoyCalendario
  const [borrador, setBorrador] = useState<RangoCalendario | null>(() => aRango(value))

  const {
    abierto,
    esMovil,
    posicion,
    contenedorRef,
    disparadorRef,
    panelRef,
    fondoRef,
    alternar,
    cerrar,
  } = usePanelFlotante(ANCHO_PANEL, () => {
    // Cada apertura arranca desde lo aplicado: lo que se tocó y no se aplicó no
    // queda a medio camino la próxima vez.
    setBorrador(aRango(value))
  })

  const texto = value
    ? `${FORMATO_CORTO.format(aCalendario(value.desde)?.toDate("UTC") ?? new Date())} → ${FORMATO_CORTO.format(
        aCalendario(value.hasta)?.toDate("UTC") ?? new Date(),
      )}`
    : "Seleccionar período"

  const borradorCompleto = Boolean(borrador?.start && borrador?.end)

  function aplicar(rango: RangoCalendario) {
    if (!rango.start || !rango.end) return
    onChange({ desde: rango.start.toString(), hasta: rango.end.toString() })
    cerrar()
  }

  function esElAtajo(rango: { start: unknown; end: unknown }) {
    if (!value) return false
    return value.desde === String(rango.start) && value.hasta === String(rango.end)
  }

  const contenido = (
    <>
      {/* es-AR: meses y días en español y la semana arranca el lunes. */}
      <I18nProvider locale="es-AR">
        <div className="flex flex-wrap gap-2 px-3 pt-3">
          {atajos(hoyCalendario).map(({ etiqueta, rango }) => (
            <EtiquetaAtajo
              key={etiqueta}
              activo={esElAtajo(rango)}
              onClick={() => aplicar(rango as RangoCalendario)}
            >
              {etiqueta}
            </EtiquetaAtajo>
          ))}
        </div>
        <RangeCalendar
          aria-label={label ?? "Período"}
          value={borrador}
          maxValue={maximo}
          onChange={setBorrador}
          className={cn("calendario-tema w-full p-3", esMovil && "mx-auto max-w-sm")}
        >
          <RangeCalendar.Header>
            <RangeCalendar.YearPickerTrigger className={TOQUE_MINIMO}>
              <RangeCalendar.YearPickerTriggerHeading className="whitespace-nowrap text-sm font-medium first-letter:uppercase" />
              <RangeCalendar.YearPickerTriggerIndicator />
            </RangeCalendar.YearPickerTrigger>
            <RangeCalendar.NavButton slot="previous" className="size-11" />
            <RangeCalendar.NavButton slot="next" className="size-11" />
          </RangeCalendar.Header>
          <RangeCalendar.Grid className="w-full">
            <RangeCalendar.GridHeader>
              {(dia) => (
                <RangeCalendar.HeaderCell className="text-xs font-normal text-muted-foreground">
                  {dia}
                </RangeCalendar.HeaderCell>
              )}
            </RangeCalendar.GridHeader>
            {/* Celdas de 44 px: área de toque mínima (regla 3.4 de AGENTS.md). */}
            <RangeCalendar.GridBody>
              {(dia) => <RangeCalendar.Cell date={dia} className="size-11 text-sm" />}
            </RangeCalendar.GridBody>
          </RangeCalendar.Grid>
          <RangeCalendar.YearPickerGrid>
            <RangeCalendar.YearPickerGridBody>
              {({ year }) => (
                <RangeCalendar.YearPickerCell year={year} className="min-h-[44px] text-sm" />
              )}
            </RangeCalendar.YearPickerGridBody>
          </RangeCalendar.YearPickerGrid>
        </RangeCalendar>
      </I18nProvider>

      <p className="px-3 text-center text-xs text-muted-foreground">
        {borrador?.start && borrador?.end
          ? `${FORMATO_LARGO.format(borrador.start.toDate("UTC"))} → ${FORMATO_LARGO.format(
              borrador.end.toDate("UTC"),
            )}`
          : "Marcá la primera y la última fecha del período"}
      </p>

      <div className="mt-2 flex items-center justify-between gap-2 border-t border-border px-3 py-2">
        <button
          type="button"
          onClick={() => setBorrador(null)}
          className="min-h-[44px] rounded-lg px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted"
        >
          Limpiar
        </button>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={cerrar}
            className="min-h-[44px] rounded-lg px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted"
          >
            Cerrar
          </button>
          <button
            type="button"
            disabled={!borradorCompleto}
            onClick={() => borrador && aplicar(borrador)}
            className="min-h-[44px] rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
          >
            Aplicar
          </button>
        </div>
      </div>
    </>
  )

  return (
    <div ref={contenedorRef} className={cn("relative flex flex-col gap-1.5", className)}>
      {label && <span className="text-xs font-medium text-muted-foreground">{label}</span>}
      <button
        ref={disparadorRef}
        type="button"
        id={id}
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-label={`${label ?? "Período"}: ${texto}`}
        onClick={alternar}
        className={cn(
          "inline-flex min-h-[44px] w-full items-center justify-between gap-2 rounded-xl border border-input bg-background px-3 text-sm transition-colors",
          "hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          "active:scale-[.99] disabled:pointer-events-none disabled:opacity-50",
          abierto && "border-foreground",
        )}
      >
        <span className="inline-flex min-w-0 items-center gap-2">
          <CalendarRange className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <span className={cn("truncate font-medium", !value && "text-muted-foreground")}>
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

      {/* z-[70]: por encima del Modal (z-50), que es donde más se usa. */}
      {abierto &&
        createPortal(
          esMovil ? (
            <>
              <div
                ref={fondoRef}
                className="fixed inset-0 z-[70] bg-black/30"
                onClick={cerrar}
                aria-hidden
              />
              <div
                ref={panelRef}
                role="dialog"
                aria-label="Selector de período"
                className="fixed inset-x-0 bottom-0 z-[71] max-h-[85vh] overflow-y-auto rounded-t-2xl border border-border bg-card pb-[env(safe-area-inset-bottom)] shadow-lg animate-in slide-in-from-bottom"
              >
                {contenido}
              </div>
            </>
          ) : (
            <div
              ref={panelRef}
              role="dialog"
              aria-label="Selector de período"
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
