"use client"

/**
 * Rango de una estadía de guardería: fecha y hora de entrada y de salida.
 *
 * <p>Es el DateRangePicker de HeroUI v3 que pidió el Dueño, sin el bloque de
 * demo "Format Options" (granularidad, ciclo horario, zona horaria): acá la
 * granularidad es siempre el minuto y el reloj, de 24 horas. Los valores son
 * {@code CalendarDateTime} (sin zona): la guardería trabaja en hora local.</p>
 */

import type { TimeValue } from "@heroui/react"
import { DateField, DateRangePicker, Label, RangeCalendar, TimeField } from "@heroui/react"
import { CalendarDateTime, parseDateTime } from "@internationalized/date"

export type RangoEstadia = { start: CalendarDateTime; end: CalendarDateTime }

/** "2026-10-28" + "08:00" -> CalendarDateTime. */
export function aFechaHora(fecha: string, hora: string): CalendarDateTime {
  return parseDateTime(`${fecha}T${hora}`)
}

/** Partes que espera la API: fecha `YYYY-MM-DD` y hora `HH:mm`. */
export function partes(valor: CalendarDateTime) {
  const iso = valor.toString() // 2026-10-28T08:00:00
  return { fecha: iso.slice(0, 10), hora: iso.slice(11, 16), iso: iso.slice(0, 19) }
}

const FORMATO = new Intl.DateTimeFormat("es-AR", {
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
  // Los valores se pasan a Date como UTC solo para formatearlos: con la zona del
  // navegador (UTC-3) las horas se mostrarían corridas.
  timeZone: "UTC",
})

export function RangoEstadiaPicker({
  value,
  onChange,
}: {
  value: RangoEstadia | null
  onChange: (valor: RangoEstadia | null) => void
}) {
  return (
    <DateRangePicker
      className="rango-estadia flex w-full flex-col gap-1.5"
      granularity="minute"
      hourCycle={24}
      hideTimeZone
      value={value}
      onChange={(v) => onChange(v ? { start: v.start as CalendarDateTime, end: v.end as CalendarDateTime } : null)}
      startName="entrada"
      endName="salida"
    >
      {({ state }) => (
        <>
          <Label className="text-xs font-medium text-muted-foreground">Entrada y salida</Label>
          <DateField.Group className="h-auto min-h-[44px] rounded-xl border border-input bg-background px-3 py-1 text-sm transition-colors focus-within:border-ring focus-within:ring-1 focus-within:ring-ring">
            <DateField.InputContainer className="flex-wrap gap-y-1">
              <DateField.Input slot="start">{(segment) => <DateField.Segment segment={segment} />}</DateField.Input>
              <DateRangePicker.RangeSeparator />
              <DateField.Input slot="end">{(segment) => <DateField.Segment segment={segment} />}</DateField.Input>
            </DateField.InputContainer>
            <DateField.Suffix>
              <DateRangePicker.Trigger aria-label="Elegir entrada y salida" className="min-h-[36px] min-w-[36px] text-muted-foreground hover:text-foreground">
                <DateRangePicker.TriggerIndicator />
              </DateRangePicker.Trigger>
            </DateField.Suffix>
          </DateField.Group>
          <DateRangePicker.Popover className="rango-estadia flex w-full max-w-80 flex-col gap-3 rounded-xl border border-border bg-popover p-3 text-popover-foreground shadow-lg">
            <RangeCalendar aria-label="Días de la estadía" className="w-full">
              <RangeCalendar.Header>
                <RangeCalendar.YearPickerTrigger>
                  <RangeCalendar.YearPickerTriggerHeading />
                  <RangeCalendar.YearPickerTriggerIndicator />
                </RangeCalendar.YearPickerTrigger>
                <RangeCalendar.NavButton slot="previous" />
                <RangeCalendar.NavButton slot="next" />
              </RangeCalendar.Header>
              <RangeCalendar.Grid>
                <RangeCalendar.GridHeader>
                  {(day) => <RangeCalendar.HeaderCell>{day}</RangeCalendar.HeaderCell>}
                </RangeCalendar.GridHeader>
                <RangeCalendar.GridBody>{(date) => <RangeCalendar.Cell date={date} />}</RangeCalendar.GridBody>
              </RangeCalendar.Grid>
              <RangeCalendar.YearPickerGrid>
                <RangeCalendar.YearPickerGridBody>
                  {({ year }) => <RangeCalendar.YearPickerCell year={year} />}
                </RangeCalendar.YearPickerGridBody>
              </RangeCalendar.YearPickerGrid>
            </RangeCalendar>
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <Label className="text-sm text-muted-foreground">Hora de entrada</Label>
                <TimeField
                  aria-label="Hora de entrada"
                  granularity="minute"
                  hourCycle={24}
                  hideTimeZone
                  name="horaEntrada"
                  value={state.timeRange?.start ?? null}
                  onChange={(v) =>
                    state.setTimeRange({ end: state.timeRange?.end as TimeValue, start: v as TimeValue })
                  }
                >
                  <TimeField.Group variant="secondary" className="min-h-[40px] rounded-lg border border-input bg-background px-3">
                    <TimeField.Input>{(segment) => <TimeField.Segment segment={segment} />}</TimeField.Input>
                  </TimeField.Group>
                </TimeField>
              </div>
              <div className="flex items-center justify-between">
                <Label className="text-sm text-muted-foreground">Hora de salida</Label>
                <TimeField
                  aria-label="Hora de salida"
                  granularity="minute"
                  hourCycle={24}
                  hideTimeZone
                  name="horaSalida"
                  value={state.timeRange?.end ?? null}
                  onChange={(v) =>
                    state.setTimeRange({ end: v as TimeValue, start: state.timeRange?.start as TimeValue })
                  }
                >
                  <TimeField.Group variant="secondary" className="min-h-[40px] rounded-lg border border-input bg-background px-3">
                    <TimeField.Input>{(segment) => <TimeField.Segment segment={segment} />}</TimeField.Input>
                  </TimeField.Group>
                </TimeField>
              </div>
            </div>
            <span className="mt-1 text-xs text-muted-foreground">
              {state.value?.start && state.value?.end
                ? `${FORMATO.format(state.value.start.toDate("UTC"))} → ${FORMATO.format(state.value.end.toDate("UTC"))}`
                : "Elegí los días en el calendario"}
            </span>
          </DateRangePicker.Popover>
          <p className="text-xs text-muted-foreground">
            {state.value?.start && state.value?.end
              ? `Entra ${FORMATO.format(state.value.start.toDate("UTC"))} · sale ${FORMATO.format(state.value.end.toDate("UTC"))}`
              : "Tocá el calendario para elegir los días"}
          </p>
        </>
      )}
    </DateRangePicker>
  )
}
