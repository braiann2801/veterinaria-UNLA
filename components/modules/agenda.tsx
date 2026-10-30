"use client"

/**
 * Agenda: calendario del mes con los turnos de cada día y, al elegir un día, el
 * detalle de sus turnos al costado (debajo en celular).
 *
 * <p>El mes se pide en una sola llamada (`GET /turnos?desde=&hasta=`) con todas
 * las semanas visibles, incluidos los días de los meses vecinos que completan la
 * grilla. El aforo y las reservas de guardería siguen siendo del día elegido.</p>
 *
 * <p>Las fechas viajan como `YYYY-MM-DD` armadas en hora local: `toISOString()`
 * convierte a UTC y entre las 21:00 y las 24:00 de Argentina devolvería mañana.</p>
 */

import { useMemo, useState } from "react"
import { CalendarDays, Clock, Dog, Plus, Stethoscope, Users } from "lucide-react"

import { guarderia as apiGuarderia, turnos as apiTurnos } from "@/lib/api"
import type { AforoGuarderia, ReservaGuarderia, TurnoClinico } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel } from "@/components/ui/field"
import { Recurso, Semforo, TOQUE_MINIMO } from "@/lib/vista"
import { AgendarTurnoModal } from "@/components/modules/agendar-turno-modal"
import { NuevaReservaGuarderia } from "@/components/modules/nueva-reserva"
import { CalendarioMensual, aClave, deClave, diasVisibles } from "@/components/modules/agenda/calendario-mensual"
import { AtenderTurnoModal } from "@/components/modules/agenda/atender-turno-modal"

/** El tope lo expone el backend en cada respuesta; no se hardcodea aca. */
function colorDeAforo(aforo: AforoGuarderia) {
  if (aforo.completo) return "rojo" as const
  if (aforo.cuposDisponibles <= 2) return "ambar" as const
  return "verde" as const
}

/**
 * `09:00 – 18:00`, o `null` si la reserva no tiene horario.
 *
 * <p>Se recorta a cinco caracteres porque Jackson emite el `LocalTime` completo
 * ("08:00:00"): sin el `slice` la columna de la agenda mostraría segundos.</p>
 */
function rangoHorario(reserva: ReservaGuarderia) {
  if (!reserva.horaEntrada || !reserva.horaSalida) return null
  return `${reserva.horaEntrada.slice(0, 5)} – ${reserva.horaSalida.slice(0, 5)}`
}

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
  const [reservaAbierta, setReservaAbierta] = useState(false)
  const [atendiendo, setAtendiendo] = useState<TurnoClinico | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const dias = diasVisibles(vista.anio, vista.mes)
  const desde = aClave(dias[0])
  const hasta = aClave(dias[dias.length - 1])

  const turnosMes = useApi<TurnoClinico[]>(() => apiTurnos.listarRango(desde, hasta), true, `${desde}_${hasta}`)
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha), true, fecha)
  const reservas = useApi<ReservaGuarderia[]>(() => apiGuarderia.listar(fecha), true, fecha)

  const turnosPorDia = useMemo(() => {
    const mapa = new Map<string, TurnoClinico[]>()
    for (const t of turnosMes.datos ?? []) {
      const clave = t.fechaHoraInicio.slice(0, 10)
      mapa.set(clave, [...(mapa.get(clave) ?? []), t])
    }
    return mapa
  }, [turnosMes.datos])

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
    await Promise.all([turnosMes.recargar(), aforo.recargar(), reservas.recargar()])
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel
        title="Agenda"
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
            turnosPorDia={turnosPorDia}
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
                            {t.fechaHoraInicio.slice(11, 16)} · {t.mascota.nombre}
                          </p>
                          <Semforo tono={TONO_ESTADO[t.estado]}>{ETIQUETA_ESTADO[t.estado]}</Semforo>
                        </div>
                        <p className="text-xs text-muted-foreground">{t.motivo}</p>
                        <p className="text-xs text-muted-foreground">
                          {t.profesional.nombreCompleto}
                          {t.profesional.matricula ? ` · ${t.profesional.matricula}` : ""} · bloque hasta{" "}
                          {t.fechaFinBloque.slice(11, 16)}
                        </p>
                        {t.estado === "PENDIENTE" && (
                          <button
                            type="button"
                            onClick={() => setAtendiendo(t)}
                            className={`mt-1 inline-flex items-center justify-center gap-2 self-start rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
                          >
                            <Stethoscope className="size-3.5" aria-hidden />
                            Atender
                          </button>
                        )}
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

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel title="Aforo de guardería">
          <Recurso estado={aforo} textoCarga="Consultando aforo">
            {(a) => (
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <Semforo tono={colorDeAforo(a)}>
                    <Users className="size-3.5" aria-hidden />
                    {a.cuposOcupados} de {a.aforoMaximo} cupos
                  </Semforo>
                  {a.completo && <span className="text-xs text-red-700 dark:text-red-300">Aforo completo</span>}
                </div>
                <MeterAforo aforo={a} />
              </div>
            )}
          </Recurso>
        </Panel>

        <Panel
          title="Reservas de guardería"
          action={
            <button
              onClick={() => setReservaAbierta(true)}
              className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
            >
              <Plus className="size-3.5" aria-hidden />
              Nueva reserva
            </button>
          }
        >
          <Recurso
            estado={reservas}
            textoCarga="Cargando reservas"
            textoVacio="No hay reservas para esta fecha"
            onReintentar={reservas.recargar}
          >
            {(lista) => (
              <ul className="flex flex-col divide-y divide-border">
                {lista.map((r) => {
                  const horario = rangoHorario(r)
                  return (
                    <li key={r.id} className="flex items-center justify-between gap-2 py-3">
                      <div className="flex min-w-0 items-center gap-2">
                        <Dog className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{r.nombreMascota}</p>
                          <p className="text-xs text-muted-foreground">
                            <CalendarDays className="mr-1 inline size-3" aria-hidden />
                            {r.tipoEstadia.replace("_", " ")} · {r.estado}
                          </p>
                          {horario && (
                            <p className="text-xs text-muted-foreground">
                              <Clock className="mr-1 inline size-3" aria-hidden />
                              {horario}
                            </p>
                          )}
                        </div>
                      </div>
                      <Semforo tono={r.estado === "CANCELADO" ? "ambar" : "verde"}>{r.estado}</Semforo>
                    </li>
                  )
                })}
              </ul>
            )}
          </Recurso>
        </Panel>
      </div>

      {turnoAbierto && (
        <AgendarTurnoModal
          fechaPorDefecto={fecha}
          onCerrar={() => setTurnoAbierto(false)}
          onGuardado={refrescar}
        />
      )}

      {atendiendo && (
        <AtenderTurnoModal
          turno={atendiendo}
          onCerrar={() => setAtendiendo(null)}
          onGuardado={async (mensaje) => {
            await turnosMes.recargar()
            setAviso(mensaje)
          }}
        />
      )}

      {reservaAbierta && (
        <NuevaReservaGuarderia
          fechaPorDefecto={fecha}
          onCerrar={() => setReservaAbierta(false)}
          onCreada={refrescar}
        />
      )}
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