"use client"

import { useEffect, useRef, type ComponentProps } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { DayPicker, getDefaultClassNames, type DayButtonProps } from "react-day-picker"
import { es } from "react-day-picker/locale/es"

import { cn } from "@/lib/utils"

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
        month: cn("flex w-full flex-col gap-4", porDefecto.month),
        nav: cn(
          "absolute inset-x-0 top-0 flex w-full items-center justify-between gap-1",
          porDefecto.nav,
        ),
        button_previous: cn(
          "inline-flex size-10 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground aria-disabled:pointer-events-none aria-disabled:opacity-40 sm:size-11",
          porDefecto.button_previous,
        ),
        button_next: cn(
          "inline-flex size-10 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground aria-disabled:pointer-events-none aria-disabled:opacity-40 sm:size-11",
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
