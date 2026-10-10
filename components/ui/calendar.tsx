"use client"

import { useEffect, useRef, useState, type ComponentProps } from "react"
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp } from "lucide-react"
import {
  DayPicker,
  getDefaultClassNames,
  useDayPicker,
  type DayButtonProps,
  type MonthCaptionProps,
} from "react-day-picker"
import { es } from "react-day-picker/locale/es"

import { cn } from "@/lib/utils"

const FORMATO_MES = new Intl.DateTimeFormat("es-AR", { month: "long" })
const FORMATO_MES_ANIO = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" })

/** `Intl` devuelve los meses en minúscula ("octubre"); la UI los quiere capitalizados. */
function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

const NOMBRES_MES = Array.from({ length: 12 }, (_, indice) =>
  capitalizar(FORMATO_MES.format(new Date(2020, indice, 1))),
)

/**
 * Calendario base sobre `react-day-picker`.
 *
 * No importa el CSS de la libreria: se estiliza con Tailwind usando
 * `getDefaultClassNames()` como piso, que es el patron de shadcn/ui. Asi el
 * calendario hereda los tokens del tema (primary, muted, border) en lugar de
 * traer su propia paleta, y no hay dos sistemas de estilos compitiendo.
 *
 * Locale español y semana arrancando el lunes para el uso local.
 *
 * Cada dia es una celda fija de 44x44 (regla 3.4 de AGENTS.md). Antes el tamano
 * salia de un `aspect-square` sobre el ancho disponible, asi que en el celular
 * quedaban botones de ~38 px: comodos para el mouse, no para el dedo.
 *
 * El encabezado "octubre 2026" es un boton (`CapituloMes`): al tocarlo se abre
 * un selector de mes y año con flechas para moverse sin cerrar el calendario.
 */
export function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  components,
  ...props
}: ComponentProps<typeof DayPicker>) {
  const porDefecto = getDefaultClassNames()

  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      locale={es}
      weekStartsOn={1}
      className={cn("p-3", className)}
      classNames={{
        root: cn("w-fit", porDefecto.root),
        months: cn("relative flex flex-col gap-4", porDefecto.months),
        // `relative` es lo que ancla el panel de mes/año de `CapituloMes`: si no,
        // el popover absoluto se posicionaria contra el popover del DatePicker.
        month: cn("relative flex w-full flex-col gap-4", porDefecto.month),
        // La barra de navegacion queda en una franja de 44px sobre el
        // encabezado. `pointer-events-none` deja pasar el click del centro
        // (el boton mes/año) y los propios botones lo reactivan.
        nav: cn(
          "pointer-events-none absolute inset-x-0 top-0 flex w-full items-center justify-between gap-1",
          porDefecto.nav,
        ),
        button_previous: cn(
          "pointer-events-auto inline-flex size-10 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground aria-disabled:pointer-events-none aria-disabled:opacity-40 sm:size-11",
          porDefecto.button_previous,
        ),
        button_next: cn(
          "pointer-events-auto inline-flex size-10 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground aria-disabled:pointer-events-none aria-disabled:opacity-40 sm:size-11",
          porDefecto.button_next,
        ),
        month_caption: cn(
          "flex h-11 w-full items-center justify-center",
          porDefecto.month_caption,
        ),
        caption_label: cn(
          "select-none text-sm font-medium sm:text-base",
          porDefecto.caption_label,
        ),
        month_grid: cn("w-full border-collapse", porDefecto.month_grid),
        weekdays: cn("flex", porDefecto.weekdays),
        weekday: cn(
          "flex-1 select-none text-xs font-normal text-muted-foreground sm:text-sm",
          porDefecto.weekday,
        ),
        week: cn("mt-1 flex w-full", porDefecto.week),
        day: cn("group/day relative h-11 w-11 p-0 text-center select-none", porDefecto.day),
        outside: cn("text-muted-foreground/50", porDefecto.outside),
        disabled: cn("opacity-40", porDefecto.disabled),
        hidden: cn("invisible", porDefecto.hidden),
        ...classNames,
      }}
      components={{
        Chevron: ({ orientation, className: claseChevron }) =>
          orientation === "left" ? (
            <ChevronLeft className={cn("size-5", claseChevron)} aria-hidden />
          ) : (
            <ChevronRight className={cn("size-5", claseChevron)} aria-hidden />
          ),
        DayButton: DiaCalendario,
        MonthCaption: CapituloMes,
        ...components,
      }}
      {...props}
    />
  )
}

/**
 * Celda clickeable de un dia.
 *
 * El estado (seleccionado, hoy, fuera del mes) se resuelve aca y no con clases
 * globales de `react-day-picker`, para que el resaltado use los tokens del tema.
 * El foco se mueve al dia activo para que la navegacion por teclado siga
 * funcionando cuando se reemplaza el boton por defecto.
 */
function DiaCalendario({ className, day, modifiers, ...props }: DayButtonProps) {
  const ref = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (modifiers.focused) ref.current?.focus()
  }, [modifiers.focused])

  return (
    <button
      ref={ref}
      type="button"
      className={cn(
        "inline-flex size-11 items-center justify-center rounded-lg text-sm font-normal transition-colors",
        "hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        "disabled:pointer-events-none disabled:opacity-40",
        modifiers.selected && "bg-primary font-medium text-primary-foreground hover:bg-primary",
        modifiers.today && !modifiers.selected && "font-semibold text-primary",
        modifiers.outside && !modifiers.selected && "text-muted-foreground/50",
        className,
      )}
      {...props}
    />
  )
}

/**
 * Encabezado del mes convertido en disparador del selector mes/año.
 *
 * Reemplaza al `MonthCaption` por defecto: en vez de un texto fijo, es un boton
 * que abre un panel sobre la grilla con dos columnas desplazables. Se navega con
 * `goToMonth()` de `react-day-picker`, que es la unica via soportada para mover
 * el mes mostrado.
 *
 * El rango de años sale de `startMonth`/`endMonth` del calendario; si no estan
 * definidos cae a un rango amplio para no dejar el selector vacio.
 */
function CapituloMes({ className, calendarMonth }: MonthCaptionProps) {
  const { goToMonth, dayPickerProps } = useDayPicker()
  const [abierto, setAbierto] = useState(false)

  const anio = calendarMonth.date.getFullYear()
  const mes = calendarMonth.date.getMonth()

  const anioActual = new Date().getFullYear()
  const anioMin = dayPickerProps.startMonth?.getFullYear() ?? anioActual - 100
  const anioMax = dayPickerProps.endMonth?.getFullYear() ?? anioActual + 10

  const anios: number[] = []
  for (let valor = anioMax; valor >= anioMin; valor -= 1) anios.push(valor)

  const meses = NOMBRES_MES.map((nombre, indice) => ({ valor: indice, etiqueta: nombre }))

  const etiqueta = capitalizar(FORMATO_MES_ANIO.format(calendarMonth.date))

  return (
    <div className={className}>
      <button
        type="button"
        onClick={() => setAbierto((previo) => !previo)}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        aria-label={`Mes y año: ${etiqueta}`}
        className={cn(
          "inline-flex min-h-[44px] items-center gap-1.5 rounded-lg px-3 text-sm font-medium transition-colors",
          "hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:text-base",
        )}
      >
        {etiqueta}
        <ChevronDown
          className={cn(
            "size-4 text-muted-foreground transition-transform",
            abierto && "rotate-180",
          )}
          aria-hidden
        />
      </button>

      {abierto && (
        <div className="absolute left-0 right-0 top-11 bottom-0 z-20 flex gap-1 rounded-xl border border-border bg-card p-2 shadow-lg">
          <ColumnaSelector
            titulo="Mes"
            opciones={meses}
            seleccionado={mes}
            onElegir={(nuevoMes) => {
              goToMonth(new Date(anio, nuevoMes, 1))
              setAbierto(false)
            }}
          />
          <ColumnaSelector
            titulo="Año"
            opciones={anios.map((valor) => ({ valor, etiqueta: String(valor) }))}
            seleccionado={anio}
            onElegir={(nuevoAnio) => goToMonth(new Date(nuevoAnio, mes, 1))}
          />
        </div>
      )}
    </div>
  )
}

/**
 * Columna desplazable del selector mes/año.
 *
 * Las flechas mueven el scroll de a un item (44 px, la misma medida que una
 * celda de dia) y al abrir se centra el valor activo para no arrancar arriba de
 * todo. `min-h-0` es necesario para que el `overflow-y-auto` funcione dentro de
 * la columna flex.
 */
function ColumnaSelector({
  titulo,
  opciones,
  seleccionado,
  onElegir,
}: {
  titulo: string
  opciones: { valor: number; etiqueta: string }[]
  seleccionado: number
  onElegir: (valor: number) => void
}) {
  const listaRef = useRef<HTMLUListElement>(null)
  const activoRef = useRef<HTMLLIElement>(null)

  useEffect(() => {
    const lista = listaRef.current
    const activo = activoRef.current
    if (!lista || !activo) return
    lista.scrollTop = activo.offsetTop - lista.clientHeight / 2 + activo.clientHeight / 2
  }, [])

  function desplazar(direccion: number) {
    listaRef.current?.scrollBy({ top: direccion * 44, behavior: "smooth" })
  }

  const claseFlecha = cn(
    "inline-flex h-11 w-full items-center justify-center rounded-lg text-muted-foreground transition-colors",
    "hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
  )

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-1">
      <span className="px-1 text-xs font-medium text-muted-foreground">{titulo}</span>
      <button
        type="button"
        onClick={() => desplazar(-1)}
        aria-label={`${titulo}: anterior`}
        className={claseFlecha}
      >
        <ChevronUp className="size-5" aria-hidden />
      </button>
      <ul ref={listaRef} className="min-h-0 flex-1 overflow-y-auto">
        {opciones.map((opcion) => {
          const activa = opcion.valor === seleccionado
          return (
            <li key={opcion.valor} ref={activa ? activoRef : undefined}>
              <button
                type="button"
                onClick={() => onElegir(opcion.valor)}
                aria-pressed={activa}
                className={cn(
                  "min-h-[44px] w-full rounded-lg px-2 text-sm transition-colors",
                  activa
                    ? "bg-primary font-medium text-primary-foreground"
                    : "hover:bg-muted focus-visible:bg-muted",
                )}
              >
                {opcion.etiqueta}
              </button>
            </li>
          )
        })}
      </ul>
      <button
        type="button"
        onClick={() => desplazar(1)}
        aria-label={`${titulo}: siguiente`}
        className={claseFlecha}
      >
        <ChevronDown className="size-5" aria-hidden />
      </button>
    </div>
  )
}
