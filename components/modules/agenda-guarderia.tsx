"use client"

/**
 * Agenda de guardería: el mismo calendario mensual que la agenda clínica, pero
 * con las estadías. Cada mascota tiene su color, así una estadía de varios días
 * se sigue a lo largo del mes. Al elegir un día, al costado (debajo en celular)
 * se ven el aforo y las estadías de ese día.
 *
 * <p>El tope de 10 perros por día lo valida el backend (regla 1 de AGENTS.md);
 * acá solo se muestra. La plaza extra de urgencia clínica va aparte y solo la
 * maneja el Veterinario.</p>
 */

import { useMemo, useState } from "react"
import { CalendarDays, Clock, Plus, Users } from "lucide-react"

import { guarderia as apiGuarderia } from "@/lib/api"
import type { AforoGuarderia, ReservaGuarderia } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { useAuth } from "@/lib/auth"
import { PlazaUrgenciaGuarderia } from "@/components/modules/agenda/plaza-urgencia"
import { Panel } from "@/components/ui/field"
import { Recurso, Semforo, TOQUE_MINIMO } from "@/lib/vista"
import { NuevaReservaGuarderia } from "@/components/modules/nueva-reserva"
import {
  CalendarioMensual,
  aClave,
  deClave,
  diasVisibles,
  type EventoCalendario,
} from "@/components/modules/agenda/calendario-mensual"

/**
 * Color estable por mascota: el mismo id da siempre el mismo tono. Se reparte
 * con el ángulo áureo para que mascotas con ids seguidos no queden parecidas.
 */
export function colorDeMascota(mascotaId: number, alfa = 0.3): string {
  const tono = Math.round((mascotaId * 137.508) % 360)
  return `hsl(${tono} 75% 55% / ${alfa})`
}

function colorDeAforo(aforo: AforoGuarderia) {
  if (aforo.completo) return "rojo" as const
  if (aforo.cuposDisponibles <= 2) return "ambar" as const
  return "verde" as const
}

/** `09:00 – 18:00`, o `null` si la reserva no tiene horario. Jackson emite "08:00:00": se recorta. */
function rangoHorario(reserva: ReservaGuarderia) {
  if (!reserva.horaEntrada || !reserva.horaSalida) return null
  return `${reserva.horaEntrada.slice(0, 5)} – ${reserva.horaSalida.slice(0, 5)}`
}

/** Las canceladas no ocupan cupo: no se pintan en el calendario. */
const OCUPA_CUPO = (r: ReservaGuarderia) => r.estado !== "CANCELADO"

function fechaLegible(clave: string) {
  return new Intl.DateTimeFormat("es-AR", { weekday: "long", day: "numeric", month: "long" }).format(deClave(clave))
}

export function AgendaGuarderia() {
  const hoy = aClave(new Date())
  const [fecha, setFecha] = useState(hoy)
  const [vista, setVista] = useState(() => ({ anio: new Date().getFullYear(), mes: new Date().getMonth() }))
  const [reservaAbierta, setReservaAbierta] = useState(false)
  const { role } = useAuth()

  const dias = diasVisibles(vista.anio, vista.mes)
  const desde = aClave(dias[0])
  const hasta = aClave(dias[dias.length - 1])

  const estadias = useApi<ReservaGuarderia[]>(() => apiGuarderia.listarRango(desde, hasta), true, `${desde}_${hasta}`)
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha), true, fecha)

  /** Cada estadía aparece en todos los días que ocupa dentro de la grilla. */
  const estadiasPorDia = useMemo(() => {
    const mapa = new Map<string, ReservaGuarderia[]>()
    for (const r of estadias.datos ?? []) {
      const ultimo = r.fechaSalida ?? r.fecha
      for (let d = deClave(r.fecha); aClave(d) <= ultimo; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
        const clave = aClave(d)
        if (clave < desde || clave > hasta) continue
        mapa.set(clave, [...(mapa.get(clave) ?? []), r])
      }
    }
    return mapa
  }, [estadias.datos, desde, hasta])

  const eventosPorDia = useMemo(() => {
    const mapa = new Map<string, EventoCalendario[]>()
    for (const [clave, lista] of estadiasPorDia) {
      mapa.set(
        clave,
        lista.filter(OCUPA_CUPO).map((r) => ({
          id: `${r.id}-${clave}`,
          hora: clave === r.fecha && r.horaEntrada ? r.horaEntrada.slice(0, 5) : undefined,
          texto: r.nombreMascota,
          titulo: `${r.nombreMascota} · ${r.fecha}${r.fechaSalida ? ` al ${r.fechaSalida}` : ""}${r.urgenciaClinica ? " · urgencia clínica" : ""}`,
          // La plaza de urgencia va en rojo, como las urgencias de la agenda clínica.
          color: r.urgenciaClinica ? "hsl(0 85% 55% / 0.35)" : colorDeMascota(r.mascotaId),
        })),
      )
    }
    return mapa
  }, [estadiasPorDia])

  function seleccionar(clave: string) {
    setFecha(clave)
    const dia = deClave(clave)
    if (dia.getMonth() !== vista.mes || dia.getFullYear() !== vista.anio) {
      setVista({ anio: dia.getFullYear(), mes: dia.getMonth() })
    }
  }

  function cambiarMes(delta: number) {
    const destino = new Date(vista.anio, vista.mes + delta, 1)
    setVista({ anio: destino.getFullYear(), mes: destino.getMonth() })
  }

  async function refrescar() {
    await Promise.all([estadias.recargar(), aforo.recargar()])
  }

  const delDia = (estadiasPorDia.get(fecha) ?? []).filter(OCUPA_CUPO)

  return (
    <div className="flex flex-col gap-4">
      <Panel
        title="Agenda guardería"
        action={
          <div className="flex items-center gap-2">
            {fecha !== hoy && (
              <button
                type="button"
                onClick={() => seleccionar(hoy)}
                className={`inline-flex items-center rounded-md border border-input px-3 text-xs font-medium hover:bg-muted ${TOQUE_MINIMO}`}
              >
                Hoy
              </button>
            )}
            <button
              type="button"
              onClick={() => setReservaAbierta(true)}
              className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
            >
              <Plus className="size-3.5" aria-hidden />
              Nueva reserva
            </button>
          </div>
        }
      >
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
          <CalendarioMensual
            anio={vista.anio}
            mes={vista.mes}
            eventosPorDia={eventosPorDia}
            unidad={{ singular: "estadía", plural: "estadías" }}
            seleccionado={fecha}
            hoy={hoy}
            cargando={estadias.cargando}
            onSeleccionar={seleccionar}
            onCambiarMes={cambiarMes}
          />

          <section className="flex min-w-0 flex-col gap-3 lg:border-l lg:border-border lg:pl-4" aria-live="polite">
            <h3 className="text-sm font-semibold first-letter:uppercase">Guardería del {fechaLegible(fecha)}</h3>

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

            <PlazaUrgenciaGuarderia fecha={fecha} esVeterinario={role === "veterinario"} onCambio={refrescar} />

            <Recurso estado={estadias} textoCarga="Cargando estadías" onReintentar={estadias.recargar}>
              {() =>
                delDia.length === 0 ? (
                  <p className="py-6 text-center text-sm text-muted-foreground">No hay estadías este día.</p>
                ) : (
                  <ul className="flex flex-col divide-y divide-border">
                    {delDia.map((r) => {
                      const horario = rangoHorario(r)
                      return (
                        <li key={r.id} className="flex items-center justify-between gap-2 py-3">
                          <div className="flex min-w-0 items-center gap-2">
                            <span
                              className="size-3 shrink-0 rounded-full"
                              style={{ backgroundColor: colorDeMascota(r.mascotaId, 1) }}
                              aria-hidden
                            />
                            <div className="min-w-0">
                              <p className="truncate text-sm font-medium">
                                {r.nombreMascota}
                                {r.urgenciaClinica && (
                                  <span className="ml-2 rounded-full bg-red-500/15 px-2 py-0.5 text-[11px] font-semibold text-red-700">
                                    Urgencia clínica
                                  </span>
                                )}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                <CalendarDays className="mr-1 inline size-3" aria-hidden />
                                {r.fechaSalida ? `${r.fecha.slice(8, 10)}/${r.fecha.slice(5, 7)} al ${r.fechaSalida.slice(8, 10)}/${r.fechaSalida.slice(5, 7)}` : r.tipoEstadia.replace("_", " ")}
                              </p>
                              {horario && (
                                <p className="text-xs text-muted-foreground">
                                  <Clock className="mr-1 inline size-3" aria-hidden />
                                  {horario}
                                </p>
                              )}
                            </div>
                          </div>
                          <Semforo tono="verde">{r.estado}</Semforo>
                        </li>
                      )
                    })}
                  </ul>
                )
              }
            </Recurso>
          </section>
        </div>
      </Panel>

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