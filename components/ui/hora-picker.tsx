"use client"

import { Field, Select } from "@/components/ui/field"
import { TOQUE_MINIMO } from "@/lib/vista"

const HORAS = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, "0"))

/**
 * Selector de hora en formato 24 h (`HH:mm`).
 *
 * Reemplaza al `<input type="time">`, que muestra "09:00 a.m." o "09:00" según
 * el idioma del sistema operativo y no se puede forzar desde la página. Dos
 * selects (hora y minutos) se ven igual en cualquier navegador y son cómodos
 * para el dedo.
 *
 * Los minutos van de a `paso` (15 por defecto, la grilla de los turnos). Si el
 * valor trae un minuto fuera de la grilla, se agrega como opción para no
 * perderlo al editar.
 */
export function HoraPicker({
  label,
  value,
  onChange,
  paso = 15,
}: {
  label: string
  /** `HH:mm`. */
  value: string
  onChange: (hora: string) => void
  paso?: number
}) {
  const [hora = "09", minutos = "00"] = value.split(":")
  const opcionesMinutos = Array.from({ length: Math.ceil(60 / paso) }, (_, i) => String(i * paso).padStart(2, "0"))
  if (!opcionesMinutos.includes(minutos)) opcionesMinutos.push(minutos)
  opcionesMinutos.sort()

  return (
    <Field label={label}>
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-1">
        <Select
          value={hora}
          onChange={(e) => onChange(`${e.target.value}:${minutos}`)}
          aria-label={`${label}: hora`}
          className={TOQUE_MINIMO}
        >
          {HORAS.map((h) => (
            <option key={h} value={h}>
              {h}
            </option>
          ))}
        </Select>
        <span className="text-sm font-medium text-muted-foreground" aria-hidden>
          :
        </span>
        <Select
          value={minutos}
          onChange={(e) => onChange(`${hora}:${e.target.value}`)}
          aria-label={`${label}: minutos`}
          className={TOQUE_MINIMO}
        >
          {opcionesMinutos.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </Select>
      </div>
    </Field>
  )
}
