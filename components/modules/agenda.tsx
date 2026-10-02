"use client"

/**
 * TASK-006: agenda de turnos y guardería con semáforo de aforo.
 *
 * El aforo lo calcula el backend (regla 2.1 de AGENTS.md: tope de 10 perros
 * por día). El semáforo es presentación; la regla vive en `ReservaGuarderiaService`.
 */

import { useState } from "react"
import { CalendarDays, Dog, Users } from "lucide-react"

import { guarderia as apiGuarderia, turnos as apiTurnos } from "@/lib/api"
import type { AforoGuarderia, ReservaGuarderia, TurnoClinico } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel } from "@/components/ui/field"
import { Recurso, Semforo, TOQUE_MINIMO } from "@/lib/vista"

/** El tope lo expone el backend en cada respuesta; no se hardcodea aca. */
function colorDeAforo(aforo: AforoGuarderia) {
  if (aforo.completo) return "rojo" as const
  if (aforo.cuposDisponibles <= 2) return "ambar" as const
  return "verde" as const
}

export function Agenda() {
  const hoy = new Date().toISOString().slice(0, 10)
  const [fecha, setFecha] = useState(hoy)

  const turnos = useApi<TurnoClinico[]>(() => apiTurnos.listar(fecha))
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha))
  const reservas = useApi<ReservaGuarderia[]>(() => apiGuarderia.listar(fecha))

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Agenda y guardería">
        <label className="flex flex-col gap-1.5 text-sm sm:max-w-xs">
          <span className="text-xs font-medium text-muted-foreground">Fecha</span>
          <input
            type="date"
            value={fecha}
            onChange={(e) => setFecha(e.target.value)}
            className={`rounded-md border border-input bg-background px-3 text-sm ${TOQUE_MINIMO}`}
          />
        </label>
      </Panel>

      <Panel title="Aforo de guardería">
        <Recurso estado={aforo} textoCarga="Consultando aforo">
          {(a) => (
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <Semforo tono={colorDeAforo(a)}>
                  <Users className="size-3.5" aria-hidden />
                  {a.cuposOcupados} de {a.aforoMaximo} cupos
                </Semforo>
                {a.completo && <span className="text-xs text-red-700">Aforo completo</span>}
              </div>
              <MeterAforo aforo={a} />
            </div>
          )}
        </Recurso>
      </Panel>

      <Panel title="Turnos clínicos">
        <Recurso
          estado={turnos}
          textoCarga="Cargando turnos"
          textoVacio="No hay turnos para esta fecha"
        >
          {(lista) => (
            <ul className="flex flex-col divide-y divide-border">
              {lista.map((t) => (
                <li key={t.id} className="flex flex-col gap-1 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-medium">
                      {t.fechaHoraInicio.slice(11, 16)} · {t.mascota.nombre}
                    </p>
                    <Semforo
                      tono={
                        t.estado === "ATENDIDO"
                          ? "verde"
                          : t.estado === "CANCELADO"
                            ? "ambar"
                            : "verde"
                      }
                    >
                      {t.estado}
                    </Semforo>
                  </div>
                  <p className="text-xs text-muted-foreground">{t.motivo}</p>
                  <p className="text-xs text-muted-foreground">
                    {t.profesional.nombreCompleto} · {t.profesional.matricula} · bloque hasta{" "}
                    {t.fechaFinBloque.slice(11, 16)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Recurso>
      </Panel>

      <Panel title="Reservas de guardería">
        <Recurso
          estado={reservas}
          textoCarga="Cargando reservas"
          textoVacio="No hay reservas para esta fecha"
        >
          {(lista) => (
            <ul className="flex flex-col divide-y divide-border">
              {lista.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-2 py-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <Dog className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{r.nombreMascota}</p>
                      <p className="text-xs text-muted-foreground">
                        <CalendarDays className="mr-1 inline size-3" aria-hidden />
                        {r.tipoEstadia} · {r.estado}
                      </p>
                    </div>
                  </div>
                  <Semforo tono={r.estado === "CANCELADA" ? "ambar" : "verde"}>{r.estado}</Semforo>
                </li>
              ))}
            </ul>
          )}
        </Recurso>
      </Panel>
    </div>
  )
}

/**
 * Barra de ocupación.
 *
 * Se construye con divs en vez de `<progress>` para poder poner el color del
 * semáforo: el elemento nativo no acepta color por estado de forma portable.
 */
function MeterAforo({ aforo }: { aforo: AforoGuarderia }) {
  const pct =
    aforo.aforoMaximo > 0 ? Math.min(100, (aforo.cuposOcupados / aforo.aforoMaximo) * 100) : 0
  const color =
    aforo.completo ? "bg-red-500" : aforo.cuposDisponibles <= 2 ? "bg-amber-500" : "bg-emerald-500"

  return (
    <div className="flex flex-col gap-1">
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuenow={aforo.cuposOcupados}
        aria-valuemin={0}
        aria-valuemax={aforo.aforoMaximo}
        aria-label="Ocupación de guardería"
      >
        <div className={`h-full rounded-full ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <p className="text-xs text-muted-foreground">
        Quedan {aforo.cuposDisponibles} cupos disponibles.
      </p>
    </div>
  )
}