"use client"

/**
 * Alta de turno clínico, única para Agenda y Mostrador.
 *
 * <p>Con el desplegable cerrado funciona como siempre: se elige una mascota ya
 * cargada. El botón "Cliente o mascota nuevos" abre dos opciones para el
 * cliente que llega por primera vez: cliente nuevo o existente, y mascota nueva
 * o existente. Las altas combinadas van a `POST /turnos/con-alta`, que hace todo
 * en una transacción: si el turno choca con la ventana de 45+15 (409), no queda
 * ni el cliente ni la mascota creados.</p>
 *
 * <p>Con el interruptor de <b>urgencia</b> no se pide ningún dato: el turno se
 * registra para ahora, sin cliente, mascota ni profesional, y puede pisar turnos
 * agendados (excepción de la regla 2 de AGENTS.md). Los datos se completan desde
 * la Agenda antes de cobrar.</p>
 *
 * <p>Se monta solo mientras está abierto, para que el formulario arranque vacío.
 * La fecha no lleva `min`: una atención ya ocurrida se documenta igual.</p>
 */

import { useState } from "react"
import { ChevronDown, Siren, UserPlus } from "lucide-react"

import {
  mascotas as apiMascotas,
  profesionales as apiProfesionales,
  turnos as apiTurnos,
  tutores as apiTutores,
} from "@/lib/api"
import type { Mascota, Profesional, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Button } from "@/components/ui/button"
import { DatePicker } from "@/components/ui/date-picker"
import { Field, Select, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { cn } from "@/lib/utils"
import { TOQUE_MINIMO } from "@/lib/vista"
import {
  ClienteMascotaForm,
  SELECCION_INICIAL,
  aPedido,
  seleccionLista,
  type SeleccionClienteMascota,
} from "@/components/modules/agenda/cliente-mascota-form"

function etiquetaMascota(m: Mascota): string {
  const tutor = m.tutores?.[0]
  return tutor ? `${m.nombre} · ${tutor.nombre} ${tutor.apellido}` : `${m.nombre} · sin tutor`
}

function etiquetaProfesional(p: Profesional): string {
  return p.matricula ? `${p.nombreCompleto} · ${p.matricula}` : p.nombreCompleto
}

/** "13:07": la hora local de ahora, para anticipar a qué hora queda la urgencia. */
function horaActual(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

export function AgendarTurnoModal({
  fechaPorDefecto,
  onCerrar,
  onGuardado,
}: {
  fechaPorDefecto: string
  onCerrar: () => void
  /** El padre relee sus listados antes de que el modal se cierre. */
  onGuardado: () => void | Promise<void>
}) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())
  const profesionales = useApi<Profesional[]>(() => apiProfesionales.listar())

  const [urgencia, setUrgencia] = useState(false)
  const [desplegado, setDesplegado] = useState(false)
  const [seleccion, setSeleccion] = useState<SeleccionClienteMascota>(SELECCION_INICIAL)
  const [mascotaId, setMascotaId] = useState("")

  const [profesionalId, setProfesionalId] = useState("")
  const [fecha, setFecha] = useState(fechaPorDefecto)
  const [hora, setHora] = useState("09:00")
  const [motivo, setMotivo] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const profesionalesActivos = (profesionales.datos ?? []).filter((p) => p.activo)
  const mascotaLista = desplegado ? seleccionLista(seleccion) : mascotaId !== ""
  const listo = urgencia || (mascotaLista && profesionalId !== "" && hora !== "" && motivo.trim() !== "")

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      if (urgencia) {
        await apiTurnos.crearUrgencia()
      } else {
        // Fecha y hora se arman a mano en hora local: un `toISOString()` correría el día en UTC-3.
        const turno = {
          fechaHoraInicio: `${fecha}T${hora}:00`,
          motivo: motivo.trim(),
          profesionalId: Number(profesionalId),
        }
        if (desplegado) {
          await apiTurnos.crearConAlta({ ...turno, ...aPedido(seleccion) })
        } else {
          await apiTurnos.crear({ ...turno, mascotaId: Number(mascotaId) })
        }
      }
      await onGuardado()
      onCerrar()
    } catch (e) {
      // El 409 de solapamiento trae las horas en conflicto: se muestra tal cual.
      setError(e instanceof Error ? e.message : "No se pudo agendar el turno")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open onClose={onCerrar} title="Agendar turno">
      <div className="flex flex-col gap-3">
        <label
          className={cn(
            "flex cursor-pointer items-center justify-between gap-3 rounded-xl border px-3 text-sm font-medium",
            TOQUE_MINIMO,
            urgencia ? "border-red-500/50 bg-red-500/10 text-red-700 dark:text-red-300" : "border-border",
          )}
        >
          <span className="inline-flex items-center gap-2">
            <Siren className="size-4" aria-hidden />
            Turno de urgencia
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={urgencia}
            aria-label="Turno de urgencia"
            onClick={() => setUrgencia((u) => !u)}
            className={cn(
              "relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors",
              urgencia ? "bg-red-600" : "bg-muted",
            )}
          >
            <span
              className={cn(
                "inline-block size-5 rounded-full bg-background shadow transition-transform",
                urgencia ? "translate-x-5" : "translate-x-0.5",
              )}
            />
          </button>
        </label>

        {urgencia ? (
          <p className="rounded-xl border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
            Se registra para hoy a las <strong className="text-foreground">{horaActual()}</strong>, sin cliente, mascota
            ni profesional. Quien la atienda se asigna al tocar <strong>Atender</strong>, y los datos del cliente y la
            mascota se completan antes de cobrar.
          </p>
        ) : (
          <>
            <button
              type="button"
              onClick={() => setDesplegado((d) => !d)}
              aria-expanded={desplegado}
              className={cn(
                "inline-flex items-center justify-between gap-2 rounded-xl border border-dashed border-border px-3 text-sm font-medium text-primary transition-colors hover:bg-muted",
                TOQUE_MINIMO,
              )}
            >
              <span className="inline-flex items-center gap-2">
                <UserPlus className="size-4" aria-hidden />
                Cliente o mascota nuevos
              </span>
              <ChevronDown className={cn("size-4 transition-transform", desplegado && "rotate-180")} aria-hidden />
            </button>

            {!desplegado ? (
              <Field label="Mascota">
                <Select value={mascotaId} onChange={(e) => setMascotaId(e.target.value)} className={TOQUE_MINIMO}>
                  <option value="">Elegí una mascota…</option>
                  {(mascotas.datos ?? [])
                    .filter((m) => m.activo)
                    .map((m) => (
                      <option key={m.id} value={m.id}>
                        {etiquetaMascota(m)}
                      </option>
                    ))}
                </Select>
                {mascotas.datos !== null && mascotas.datos.length === 0 && (
                  <span className="text-xs text-muted-foreground">
                    No hay mascotas cargadas: usá "Cliente o mascota nuevos".
                  </span>
                )}
              </Field>
            ) : (
              <ClienteMascotaForm valor={seleccion} onChange={setSeleccion} tutores={tutores.datos ?? []} />
            )}

            <Field label="Profesional">
              <Select
                value={profesionalId}
                onChange={(e) => setProfesionalId(e.target.value)}
                className={TOQUE_MINIMO}
              >
                <option value="">Elegí un profesional…</option>
                {profesionalesActivos.map((p) => (
                  <option key={p.id} value={p.id}>
                    {etiquetaProfesional(p)}
                  </option>
                ))}
              </Select>
              {profesionales.datos !== null && profesionalesActivos.length === 0 && (
                <span className="text-xs text-muted-foreground">
                  No hay profesionales activos. Cargalos desde el Mostrador.
                </span>
              )}
            </Field>

            <div className="grid gap-3 sm:grid-cols-2">
              <DatePicker label="Fecha" value={fecha} onChange={setFecha} />
              <Field label="Hora">
                <TextInput
                  type="time"
                  step={900}
                  value={hora}
                  onChange={(e) => setHora(e.target.value)}
                  className={TOQUE_MINIMO}
                />
              </Field>
            </div>

            <Field label="Motivo de consulta">
              <TextInput
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Ej: Vacuna, control…"
                maxLength={300}
                className={TOQUE_MINIMO}
              />
            </Field>
          </>
        )}

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onCerrar} type="button" className={TOQUE_MINIMO}>
            Cancelar
          </Button>
          <Button onClick={guardar} disabled={guardando || !listo} type="button" className={TOQUE_MINIMO}>
            {guardando ? "Guardando…" : urgencia ? "Registrar urgencia" : "Agendar turno"}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
