"use client"

/**
 * TASK-006/TASK-008: agenda de turnos y guardería con semáforo de aforo.
 *
 * El aforo lo calcula el backend (regla 2.1 de AGENTS.md: tope de 10 perros
 * por día). El semáforo es presentación; la regla vive en `ReservaGuarderiaService`.
 *
 * <p>TASK-008 cambió el cableado de tres formas:</p>
 * <ul>
 *   <li>La fecha viaja como <b>clave</b> de los tres hooks. `useApi` guarda la
 *       fn en un ref y no la tiene en las dependencias del efecto, asi que
 *       cambiar la fecha re-renderizaba la pantalla sin volver a pedir nada: la
 *       grilla seguía mostrando los turnos del primer día cargado. Ahora la fecha
 *       se pasa como tercer argumento del hook.</li>
 *   <li>El modal de alta de turno <b>acepta fechas pasadas</b>. El backend nunca
 *       las prohibió, pero la UI no tenía forma de cargarlas; una atención del
 *       martes que se documenta el jueves quedaba sin registrar.</li>
 *   <li>La lista de guardería muestra el horario de entrada y salida de cada
 *       reserva, para que el mostrador ordene la jornada.</li>
 * </ul>
 */

import { useState } from "react"
import { CalendarDays, Clock, Dog, Plus, Users } from "lucide-react"

import { guarderia as apiGuarderia, turnos as apiTurnos } from "@/lib/api"
import type { AforoGuarderia, ReservaGuarderia, TurnoClinico } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel } from "@/components/ui/field"
import { Recurso, Semforo, TOQUE_MINIMO } from "@/lib/vista"
import { NuevoTurnoModal } from "@/components/modules/nuevo-turno"
import { NuevaReservaGuarderia } from "@/components/modules/nueva-reserva"

/** El tope lo expone el backend en cada respuesta; no se hardcodea aca. */
function colorDeAforo(aforo: AforoGuarderia) {
  if (aforo.completo) return "rojo" as const
  if (aforo.cuposDisponibles <= 2) return "ambar" as const
  return "verde" as const
}

/**
 * Fecha local en `YYYY-MM-DD`, sin pasar por `toISOString()`.
 *
 * Ese helper convierte a UTC, asi que entre las 21:00 y las 24:00 de Argentina
 * devuelve mañana: la agenda arrancaría en un día que todavía no llegó.
 */
function hoy(): string {
  const d = new Date()
  const mes = String(d.getMonth() + 1).padStart(2, "0")
  const dia = String(d.getDate()).padStart(2, "0")
  return `${d.getFullYear()}-${mes}-${dia}`
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

export function Agenda() {
  const [fecha, setFecha] = useState(hoy())
  const [turnoAbierto, setTurnoAbierto] = useState(false)
  const [reservaAbierta, setReservaAbierta] = useState(false)

  const turnos = useApi<TurnoClinico[]>(() => apiTurnos.listar(fecha), true, fecha)
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha), true, fecha)
  const reservas = useApi<ReservaGuarderia[]>(() => apiGuarderia.listar(fecha), true, fecha)

  /** Tras agendar, los tres listados quedaron desactualizados. */
  async function refrescar() {
    await Promise.all([turnos.recargar(), aforo.recargar(), reservas.recargar()])
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Agenda y guardería">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <label className="flex flex-col gap-1.5 text-sm sm:max-w-xs">
            <span className="text-xs font-medium text-muted-foreground">Fecha</span>
            <input
              type="date"
              value={fecha}
              onChange={(e) => setFecha(e.target.value || hoy())}
              className={`rounded-md border border-input bg-background px-3 text-sm ${TOQUE_MINIMO}`}
            />
          </label>
          <button
            onClick={() => setTurnoAbierto(true)}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
          >
            <Plus className="size-4" aria-hidden />
            Agendar turno
          </button>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          Se puede agendar en fechas pasadas para documentar atenciones ya
          ocurridas.
        </p>
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
          onReintentar={turnos.recargar}
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
                    {t.profesional.nombreCompleto}
                    {t.profesional.matricula ? ` · ${t.profesional.matricula}` : ""} ·{" "}
                    bloque hasta {t.fechaFinBloque.slice(11, 16)}
                  </p>
                </li>
              ))}
            </ul>
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

      {turnoAbierto && (
        <NuevoTurnoModal
          fechaPorDefecto={fecha}
          onCerrar={() => setTurnoAbierto(false)}
          onCreado={refrescar}
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