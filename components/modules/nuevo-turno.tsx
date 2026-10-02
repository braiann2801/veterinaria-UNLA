"use client"

/**
 * TASK-008: alta de turno clinico con fecha y hora.
 *
 * <p>Antes de este componente no habia ninguna forma de agendar un turno desde
 * la UI: la grilla de `agenda.tsx` era de solo lectura. El backend ya aceptaba
 * el `POST /api/v1/turnos` (y nunca prohibio fechas pasadas), asi que el trabajo
 * de esta pantalla es cablear los datos del formulario al contrato del
 * endpoint.</p>
 *
 * <p>El campo de fecha <b>no lleva `min`</b>. Es deliberado: una atencion de la
 * semana pasada se documenta hoy, y poner el limite en "hoy" haria imposible
 * cargarla. El backend no impone la restriccion, asi que la UI tampoco.</p>
 */

import { useState } from "react"
import { CalendarPlus } from "lucide-react"

import { mascotas as apiMascotas, profesionales as apiProfesionales, turnos as apiTurnos } from "@/lib/api"
import type { Mascota, Profesional } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Modal } from "@/components/ui/modal"
import { Panel, Select, TextInput } from "@/components/ui/field"
import { TOQUE_MINIMO } from "@/lib/vista"

/** `Matriz DNI 12345678 · Laura Sosa`. */
function etiquetaMascota(m: Mascota): string {
  const tutor = m.tutores?.[0]
  return tutor ? `${m.nombre} · ${tutor.nombre} ${tutor.apellido}` : `${m.nombre} · sin tutor`
}

function etiquetaProfesional(p: Profesional): string {
  return p.matricula ? `${p.nombreCompleto} · ${p.matricula}` : p.nombreCompleto
}

/**
 * Se monta solo mientras esta abierto (lo hace la agenda con `{abierto && …}`),
 * para que el estado del formulario muera al cerrar: si un turno chocó con la
 * ventana de desinfeccion, el operador no quiere encontrar escrito el horario
 * rechazado al reabrir.
 */
export function NuevoTurnoModal({
  fechaPorDefecto,
  onCerrar,
  onCreado,
}: {
  fechaPorDefecto: string
  onCerrar: () => void
  onCreado: () => void | Promise<void>
}) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const profesionales = useApi<Profesional[]>(() => apiProfesionales.listar())

  const [mascotaId, setMascotaId] = useState<number | null>(null)
  const [profesionalId, setProfesionalId] = useState<number | null>(null)
  const [fecha, setFecha] = useState(fechaPorDefecto)
  const [hora, setHora] = useState("09:00")
  const [motivo, setMotivo] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  /**
   * Compacta la fecha y la hora en un solo `LocalDateTime`.
   *
   * Se arma con `T` a mano y no con `new Date(...)`: el constructor interpreta
   * la cadena como hora local, pero un `.toISOString()` posterior correría el
   * riesgo opuesto, devolverla en UTC y correr la fecha tres horas.
   */
  const inicio = `${fecha}T${hora}`

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await apiTurnos.crear({
        fechaHoraInicio: inicio,
        motivo: motivo.trim() || "Consulta",
        mascotaId: mascotaId as number,
        profesionalId: profesionalId as number,
      })
      await onCreado()
      onCerrar()
    } catch (e) {
      // El 409 de solapamiento trae un mensaje largo del backend con las horas
      // en conflicto: se muestra tal cual porque es el unico lugar donde el
      // operador ve que bloque choca con cual.
      setError(e instanceof Error ? e.message : "No se pudo agendar el turno")
    } finally {
      setGuardando(false)
    }
  }

  const listo = mascotaId !== null && profesionalId !== null && hora.length > 0
  const sinMascotas = mascotas.datos !== null && mascotas.datos.length === 0
  const sinProfesionales =
    profesionales.datos !== null && !profesionales.datos.some((p) => p.activo)

  return (
    <Modal open onClose={onCerrar} title="Agendar turno">
      <div className="flex flex-col gap-3">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Mascota</span>
          <Select
            value={mascotaId ?? ""}
            onChange={(e) => setMascotaId(e.target.value ? Number(e.target.value) : null)}
            className={TOQUE_MINIMO}
          >
            <option value="">Elegí una mascota…</option>
            {(mascotas.datos ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {etiquetaMascota(m)}
              </option>
            ))}
          </Select>
          {sinMascotas && (
            <span className="text-xs text-muted-foreground">
              No hay mascotas cargadas. Cargalas primero en el Padrón.
            </span>
          )}
        </label>

        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Profesional</span>
          <Select
            value={profesionalId ?? ""}
            onChange={(e) => setProfesionalId(e.target.value ? Number(e.target.value) : null)}
            className={TOQUE_MINIMO}
          >
            <option value="">Elegí un profesional…</option>
            {(profesionales.datos ?? [])
              .filter((p) => p.activo)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {etiquetaProfesional(p)}
                </option>
              ))}
          </Select>
          {sinProfesionales && (
            <span className="text-xs text-muted-foreground">
              No hay profesionales activos. Cargalos desde el Mostrador.
            </span>
          )}
        </label>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Fecha</span>
            <TextInput
              type="date"
              value={fecha}
              onChange={(e) => setFecha(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Hora</span>
            <TextInput
              type="time"
              step={900}
              value={hora}
              onChange={(e) => setHora(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </label>
        </div>

        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Motivo</span>
          <TextInput
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Ej: Vacunación anual"
            maxLength={300}
            className={TOQUE_MINIMO}
          />
        </label>

        <Panel className="bg-muted/40">
          <p className="text-xs text-muted-foreground">
            Cada turno ocupa 45 minutos de atención más 15 de desinfección. El
            bloque entero queda reservado: agendarlo en la franja de desinfección
            de otro turno se rechaza. La fecha puede ser pasada.
          </p>
        </Panel>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button
            onClick={onCerrar}
            disabled={guardando}
            className={`rounded-md border border-border px-3 text-sm transition-colors hover:bg-muted disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            Cancelar
          </button>
          <button
            onClick={guardar}
            disabled={guardando || !listo}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <CalendarPlus className="size-4" aria-hidden />
            {guardando ? "Agendando…" : "Agendar"}
          </button>
        </div>
      </div>
    </Modal>
  )
}