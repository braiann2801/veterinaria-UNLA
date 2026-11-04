"use client"

/**
 * Agenda: calendario del mes con los turnos de cada día y, al elegir un día, el
 * detalle de sus turnos al costado (debajo en celular).
 *
 * <p>El mes se pide en una sola llamada (`GET /turnos?desde=&hasta=`) con todas
 * las semanas visibles, incluidos los días de los meses vecinos que completan la
 * grilla. La guardería tiene su propia agenda (`agenda-guarderia.tsx`).</p>
 *
 * <p>Las fechas viajan como `YYYY-MM-DD` armadas en hora local: `toISOString()`
 * convierte a UTC y entre las 21:00 y las 24:00 de Argentina devolvería mañana.</p>
 */

import { useMemo, useState } from "react"
import { Plus, Stethoscope, UserCheck } from "lucide-react"

import { nombreDelTurno, turnos as apiTurnos } from "@/lib/api"
import type { TurnoClinico } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel } from "@/components/ui/field"
import { Recurso, Semforo, TOQUE_MINIMO } from "@/lib/vista"
import { AgendarTurnoModal } from "@/components/modules/agendar-turno-modal"
import {
  CalendarioMensual,
  aClave,
  deClave,
  diasVisibles,
  type EventoCalendario,
} from "@/components/modules/agenda/calendario-mensual"
import { AtenderTurnoModal } from "@/components/modules/agenda/atender-turno-modal"
import { CompletarDatosModal } from "@/components/modules/agenda/completar-datos-modal"

const ETIQUETA_ESTADO: Record<TurnoClinico["estado"], string> = {
  PENDIENTE: "Pendiente",
  ATENDIDO: "Atendido",
  CANCELADO: "Cancelado",
}

const TONO_ESTADO: Record<TurnoClinico["estado"], "verde" | "ambar" | "rojo"> = {
  PENDIENTE: "ambar",
  ATENDIDO: "verde",
  CANCELADO: "rojo",
}

/** "martes 29 de octubre". */
function fechaLegible(clave: string) {
  return new Intl.DateTimeFormat("es-AR", { weekday: "long", day: "numeric", month: "long" }).format(deClave(clave))
}

export function Agenda() {
  const hoy = aClave(new Date())
  const [fecha, setFecha] = useState(hoy)
  const [vista, setVista] = useState(() => ({ anio: new Date().getFullYear(), mes: new Date().getMonth() }))
  const [turnoAbierto, setTurnoAbierto] = useState(false)
  const [atendiendo, setAtendiendo] = useState<TurnoClinico | null>(null)
  const [completando, setCompletando] = useState<TurnoClinico | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const dias = diasVisibles(vista.anio, vista.mes)
  const desde = aClave(dias[0])
  const hasta = aClave(dias[dias.length - 1])

  const turnosMes = useApi<TurnoClinico[]>(() => apiTurnos.listarRango(desde, hasta), true, `${desde}_${hasta}`)

  const turnosPorDia = useMemo(() => {
    const mapa = new Map<string, TurnoClinico[]>()
    for (const t of turnosMes.datos ?? []) {
      const clave = t.fechaHoraInicio.slice(0, 10)
      mapa.set(clave, [...(mapa.get(clave) ?? []), t])
    }
    return mapa
  }, [turnosMes.datos])

  /** Lo que se ve en cada celda: los turnos no cancelados, en verde los atendidos. */
  const eventosPorDia = useMemo(() => {
    const mapa = new Map<string, EventoCalendario[]>()
    for (const [clave, turnos] of turnosPorDia) {
      mapa.set(
        clave,
        turnos
          .filter((t) => t.estado !== "CANCELADO")
          .map((t) => ({
            id: t.id,
            hora: t.fechaHoraInicio.slice(11, 16),
            texto: nombreDelTurno(t),
            titulo: `${t.fechaHoraInicio.slice(11, 16)} ${nombreDelTurno(t)} · ${t.motivo}${t.estado === "ATENDIDO" ? " · atendido" : ""}`,
            // Urgencias en rojo; atendidos en verde; el resto, el color del sistema.
            clase: t.urgencia ? "bg-red-500/25" : t.estado === "ATENDIDO" ? "bg-emerald-500/20" : "bg-primary/15",
          })),
      )
    }
    return mapa
  }, [turnosPorDia])

  /** Elegir un día de un mes vecino también mueve el calendario a ese mes. */
  function seleccionar(clave: string) {
    setFecha(clave)
    setAviso(null)
    const dia = deClave(clave)
    if (dia.getMonth() !== vista.mes || dia.getFullYear() !== vista.anio) {
      setVista({ anio: dia.getFullYear(), mes: dia.getMonth() })
    }
  }

  function cambiarMes(delta: number) {
    const destino = new Date(vista.anio, vista.mes + delta, 1)
    setVista({ anio: destino.getFullYear(), mes: destino.getMonth() })
  }

  function irAHoy() {
    seleccionar(hoy)
  }

  /** Tras agendar, el mes y los listados del día quedaron desactualizados. */
  async function refrescar() {
    await turnosMes.recargar()
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel
        title="Agenda clínica"
        action={
          <div className="flex items-center gap-2">
            {fecha !== hoy && (
              <button
                type="button"
                onClick={irAHoy}
                className={`inline-flex items-center rounded-md border border-input px-3 text-xs font-medium hover:bg-muted ${TOQUE_MINIMO}`}
              >
                Hoy
              </button>
            )}
            <button
              type="button"
              onClick={() => setTurnoAbierto(true)}
              className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
            >
              <Plus className="size-3.5" aria-hidden />
              Agendar turno
            </button>
          </div>
        }
      >
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
          <CalendarioMensual
            anio={vista.anio}
            mes={vista.mes}
            eventosPorDia={eventosPorDia}
            seleccionado={fecha}
            hoy={hoy}
            cargando={turnosMes.cargando}
            onSeleccionar={seleccionar}
            onCambiarMes={cambiarMes}
          />

          <section className="flex min-w-0 flex-col gap-2 lg:border-l lg:border-border lg:pl-4" aria-live="polite">
            <h3 className="text-sm font-semibold first-letter:uppercase">Turnos del {fechaLegible(fecha)}</h3>
            <Recurso estado={turnosMes} textoCarga="Cargando turnos" onReintentar={turnosMes.recargar}>
              {() => {
                const delDia = turnosPorDia.get(fecha) ?? []
                if (delDia.length === 0) {
                  return <p className="py-6 text-center text-sm text-muted-foreground">No hay turnos este día.</p>
                }
                return (
                  <ul className="flex flex-col divide-y divide-border">
                    {delDia.map((t) => (
                      <li key={t.id} className="flex flex-col gap-1 py-3">
                        <div className="flex items-center justify-between gap-2">
                          <p className={`text-sm font-medium ${t.estado === "CANCELADO" ? "line-through opacity-60" : ""}`}>
                            {t.fechaHoraInicio.slice(11, 16)} · {nombreDelTurno(t)}
                            {t.urgencia && (
                              <span className="ml-2 rounded-full bg-red-500/15 px-2 py-0.5 text-[11px] font-semibold text-red-700 dark:text-red-300">
                                Urgencia
                              </span>
                            )}
                          </p>
                          <Semforo tono={TONO_ESTADO[t.estado]}>{ETIQUETA_ESTADO[t.estado]}</Semforo>
                        </div>
                        <p className="text-xs text-muted-foreground">{t.motivo}</p>
                        <p className="text-xs text-muted-foreground">
                          {t.profesional
                            ? `${t.profesional.nombreCompleto}${t.profesional.matricula ? ` · ${t.profesional.matricula}` : ""}`
                            : "Sin profesional asignado"}{" "}
                          · bloque hasta{" "}
                          {t.fechaFinBloque.slice(11, 16)}
                        </p>
                        <div className="mt-1 flex flex-wrap gap-2">
                          {t.estado === "PENDIENTE" && (
                            <button
                              type="button"
                              onClick={() => setAtendiendo(t)}
                              className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
                            >
                              <Stethoscope className="size-3.5" aria-hidden />
                              Atender
                            </button>
                          )}
                          {/* Una urgencia sin datos no se puede cobrar hasta completarlos. */}
                          {t.urgencia && !t.mascota && t.estado !== "CANCELADO" && (
                            <button
                              type="button"
                              onClick={() => setCompletando(t)}
                              className={`inline-flex items-center justify-center gap-2 rounded-md border border-red-500/40 px-3 text-xs font-medium text-red-700 transition-colors hover:bg-red-500/10 dark:text-red-300 ${TOQUE_MINIMO}`}
                            >
                              <UserCheck className="size-3.5" aria-hidden />
                              Completar datos
                            </button>
                          )}
                        </div>
                      </li>
                    ))}
                  </ul>
                )
              }}
            </Recurso>
            {aviso && (
              <p
                className="rounded-md border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm text-emerald-900 dark:text-emerald-300"
                role="status"
              >
                {aviso}
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              Se puede agendar en fechas pasadas para documentar atenciones ya ocurridas.
            </p>
          </section>
        </div>
      </Panel>

      {turnoAbierto && (
        <AgendarTurnoModal
          fechaPorDefecto={fecha}
          onCerrar={() => setTurnoAbierto(false)}
          onGuardado={refrescar}
        />
      )}

      {completando && (
        <CompletarDatosModal
          turno={completando}
          onCerrar={() => setCompletando(null)}
          onGuardado={async (mensaje) => {
            setAviso(mensaje)
            await turnosMes.recargar()
          }}
        />
      )}

      {atendiendo && (
        <AtenderTurnoModal
          turno={atendiendo}
          onCerrar={() => setAtendiendo(null)}
          onGuardado={async (mensaje) => {
            setAviso(mensaje)
            await turnosMes.recargar()
          }}
        />
      )}

    </div>
  )
}
