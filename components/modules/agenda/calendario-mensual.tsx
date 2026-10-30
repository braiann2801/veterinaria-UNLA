"use client"

/**
 * Grilla del mes con los turnos de cada día en forma reducida.
 *
 * <p>Es presentacional: recibe los turnos ya agrupados por día y avisa qué día
 * se eligió. La semana arranca el lunes, como se usa en Argentina. En celular
 * cada celda muestra solo la cantidad de turnos (no entra más en 1/7 del
 * ancho); desde `md` muestra hasta tres ("09:00 Firulais") y un "+N" con el resto.</p>
 */

import { ChevronLeft, ChevronRight } from "lucide-react"

import type { TurnoClinico } from "@/lib/api"
import { cn } from "@/lib/utils"
import { TOQUE_MINIMO } from "@/lib/vista"

const DIAS_SEMANA = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]
/** "15 de octubre de 2026": la etiqueta de cada celda, con su propio mes (no el que se está viendo). */
const FECHA_CELDA = new Intl.DateTimeFormat("es-AR", { day: "numeric", month: "long", year: "numeric" })
const TURNOS_POR_CELDA = 3

/** `YYYY-MM-DD` en hora local, sin pasar por UTC. */
export function aClave(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, "0")
  const dia = String(fecha.getDate()).padStart(2, "0")
  return `${fecha.getFullYear()}-${mes}-${dia}`
}

/** `YYYY-MM-DD` -> Date local a las 00:00 (el constructor con string lo leería en UTC). */
export function deClave(clave: string): Date {
  const [anio, mes, dia] = clave.split("-").map(Number)
  return new Date(anio, mes - 1, dia)
}

/**
 * Días visibles del mes: semanas completas de lunes a domingo, incluyendo los
 * días de los meses vecinos que completan la primera y la última semana.
 */
export function diasVisibles(anio: number, mes: number): Date[] {
  const primero = new Date(anio, mes, 1)
  // getDay(): 0 = domingo. Corrido para que el lunes sea 0.
  const desfase = (primero.getDay() + 6) % 7
  const inicio = new Date(anio, mes, 1 - desfase)
  const ultimo = new Date(anio, mes + 1, 0)
  const total = Math.ceil((desfase + ultimo.getDate()) / 7) * 7
  return Array.from({ length: total }, (_, i) => new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + i))
}

export function CalendarioMensual({
  anio,
  mes,
  turnosPorDia,
  seleccionado,
  hoy,
  cargando,
  onSeleccionar,
  onCambiarMes,
}: {
  anio: number
  /** 0 = enero, como `Date`. */
  mes: number
  turnosPorDia: Map<string, TurnoClinico[]>
  seleccionado: string
  hoy: string
  cargando: boolean
  onSeleccionar: (clave: string) => void
  onCambiarMes: (delta: number) => void
}) {
  const titulo = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" }).format(new Date(anio, mes, 1))

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => onCambiarMes(-1)}
          aria-label="Mes anterior"
          className={cn("inline-flex w-11 items-center justify-center rounded-xl hover:bg-muted", TOQUE_MINIMO)}
        >
          <ChevronLeft className="size-5" aria-hidden />
        </button>
        <h3 className="text-sm font-semibold first-letter:uppercase" aria-live="polite">
          {titulo}
          {cargando && <span className="ml-2 text-xs font-normal text-muted-foreground">Cargando…</span>}
        </h3>
        <button
          type="button"
          onClick={() => onCambiarMes(1)}
          aria-label="Mes siguiente"
          className={cn("inline-flex w-11 items-center justify-center rounded-xl hover:bg-muted", TOQUE_MINIMO)}
        >
          <ChevronRight className="size-5" aria-hidden />
        </button>
      </div>

      <div className="grid grid-cols-7 gap-px overflow-hidden rounded-xl border border-border bg-border" role="grid">
        {DIAS_SEMANA.map((d) => (
          <div key={d} className="bg-card py-1.5 text-center text-[11px] font-medium text-muted-foreground" role="columnheader">
            {d}
          </div>
        ))}
        {diasVisibles(anio, mes).map((dia) => {
          const clave = aClave(dia)
          const delMes = dia.getMonth() === mes
          const turnos = turnosPorDia.get(clave) ?? []
          const activos = turnos.filter((t) => t.estado !== "CANCELADO")
          const elegido = clave === seleccionado
          return (
            <button
              key={clave}
              type="button"
              role="gridcell"
              aria-selected={elegido}
              aria-label={`${FECHA_CELDA.format(dia)}: ${activos.length} turno${activos.length === 1 ? "" : "s"}`}
              onClick={() => onSeleccionar(clave)}
              className={cn(
                "flex min-h-12 min-w-0 flex-col items-stretch gap-0.5 p-1 text-left transition-colors md:min-h-24 md:p-1.5",
                delMes ? "bg-card hover:bg-muted/60" : "bg-muted/40 text-muted-foreground",
                elegido && "bg-primary/10 ring-2 ring-inset ring-primary",
              )}
            >
              <span
                className={cn(
                  "inline-flex size-6 items-center justify-center self-start rounded-full text-xs font-medium",
                  clave === hoy && "bg-primary text-primary-foreground",
                )}
              >
                {dia.getDate()}
              </span>

              {activos.length > 0 && (
                <>
                  {/* Celular: solo la cantidad. */}
                  <span className="mt-auto self-center rounded-full bg-primary/15 px-1.5 text-[11px] font-semibold text-primary md:hidden">
                    {activos.length}
                  </span>
                  {/* Desde md: los primeros turnos y cuántos quedan. */}
                  <ul className="hidden min-w-0 flex-col gap-0.5 md:flex">
                    {activos.slice(0, TURNOS_POR_CELDA).map((t) => (
                      <li
                        key={t.id}
                        className="truncate rounded bg-primary/15 px-1 text-[11px] leading-5 text-foreground"
                        title={`${t.fechaHoraInicio.slice(11, 16)} ${t.mascota.nombre} · ${t.motivo}`}
                      >
                        <span className="font-medium">{t.fechaHoraInicio.slice(11, 16)}</span> {t.mascota.nombre}
                      </li>
                    ))}
                    {activos.length > TURNOS_POR_CELDA && (
                      <li className="px-1 text-[11px] text-muted-foreground">
                        +{activos.length - TURNOS_POR_CELDA} más
                      </li>
                    )}
                  </ul>
                </>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
