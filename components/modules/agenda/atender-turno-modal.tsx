"use client"

/**
 * Cierra la atención de un turno desde la Agenda: el profesional carga la ficha
 * clínica y los extras usados sin cambiar de pestaña ni buscar el turno.
 *
 * <p>El turno ya trae la mascota, el profesional y la fecha, así que no se
 * vuelven a pedir. Al guardar, el backend registra la ficha, pasa el turno a
 * ATENDIDO y emite la comanda (consulta + extras) en una sola transacción.
 * No hay montos ni medios de pago acá (regla 3 de AGENTS.md).</p>
 */

import { useState } from "react"
import { Stethoscope, TriangleAlert } from "lucide-react"

import {
  consultas as apiConsultas,
  nombreDelTurno,
  profesionales as apiProfesionales,
  type ConductaObservada,
  type Profesional,
  type TurnoClinico,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Button } from "@/components/ui/button"
import { Field, Select, TextArea, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { TOQUE_MINIMO, soloDecimal } from "@/lib/vista"
import { CONDUCTAS, CONDUCTAS_CON_ALERTA } from "@/components/modules/ficha/conductas"
import { SelectorExtras, type ExtraElegido } from "@/components/modules/ficha/selector-extras"

export function AtenderTurnoModal({
  turno,
  onCerrar,
  onGuardado,
}: {
  turno: TurnoClinico
  onCerrar: () => void
  onGuardado: (mensaje: string) => void | Promise<void>
}) {
  const profesionales = useApi<Profesional[]>(() => apiProfesionales.listar())
  // La urgencia se agenda sin profesional: lo elige quien la atiende.
  const [profesionalId, setProfesionalId] = useState(turno.profesional ? String(turno.profesional.id) : "")
  const [anamnesis, setAnamnesis] = useState("")
  const [diagnostico, setDiagnostico] = useState("")
  const [tratamiento, setTratamiento] = useState("")
  const [peso, setPeso] = useState("")
  const [conducta, setConducta] = useState<ConductaObservada>("TRANQUILO")
  const [extras, setExtras] = useState<ExtraElegido[]>([])
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    let ficha
    try {
      ficha = await apiConsultas.registrar({
        turnoClinicoId: turno.id,
        // Una urgencia sin datos se atiende sin mascota: la comanda queda sin titular hasta completarlos.
        mascotaId: turno.mascota?.id,
        profesionalId: Number(profesionalId),
        fechaAtencion: turno.fechaHoraInicio,
        anamnesis: anamnesis.trim(),
        diagnostico: diagnostico.trim() || undefined,
        tratamiento: tratamiento.trim() || undefined,
        conductaObservada: conducta,
        pesoKg: peso.trim() === "" ? undefined : Number(peso),
        extras,
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo guardar la atención")
      setGuardando(false)
      return
    }
    // La atención ya quedó guardada: si falla releer la agenda, no se muestra
    // como error del guardado (un reintento chocaría con "el turno ya tiene ficha").
    try {
      await onGuardado(
        turno.mascota
          ? `Atención de ${turno.mascota.nombre} cerrada. Se emitió la comanda #${ficha.comandaId} para cobrar en el mostrador.`
          : `Urgencia atendida. La comanda #${ficha.comandaId} se podrá cobrar cuando se completen los datos del cliente y la mascota.`,
      )
    } catch {
      // El padre muestra la agenda desactualizada con su propio estado de error.
    }
    setGuardando(false)
    onCerrar()
  }

  return (
    <Modal open onClose={onCerrar} title={`Atender a ${nombreDelTurno(turno)}`}>
      <div className="flex flex-col gap-3">
        <p className="text-xs text-muted-foreground">
          {turno.fechaHoraInicio.slice(11, 16)} · {turno.motivo}
          {turno.profesional ? ` · ${turno.profesional.nombreCompleto}` : ""}
        </p>

        {!turno.profesional && (
          <Field label="Profesional que atiende">
            <Select value={profesionalId} onChange={(e) => setProfesionalId(e.target.value)} className={TOQUE_MINIMO}>
              <option value="">Elegí un profesional…</option>
              {(profesionales.datos ?? [])
                .filter((p) => p.activo)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.matricula ? `${p.nombreCompleto} · ${p.matricula}` : p.nombreCompleto}
                  </option>
                ))}
            </Select>
          </Field>
        )}
        {!turno.mascota && (
          <p className="rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-900">
            Urgencia sin datos: podés atenderla igual. Antes de cobrar hay que completar el cliente y la mascota desde
            la Agenda.
          </p>
        )}
        <Field label="Anamnesis">
          <TextArea value={anamnesis} onChange={(e) => setAnamnesis(e.target.value)} rows={3} maxLength={2000} />
        </Field>
        <Field label="Peso (kg)">
          <TextInput
            value={peso}
            onChange={(e) => setPeso(soloDecimal(e.target.value))}
            inputMode="decimal"
            placeholder="Ej: 12.5"
            className={TOQUE_MINIMO}
          />
        </Field>
        <Field label="Diagnóstico (opcional)">
          <TextInput
            value={diagnostico}
            onChange={(e) => setDiagnostico(e.target.value)}
            maxLength={2000}
            className={TOQUE_MINIMO}
          />
        </Field>
        <Field label="Tratamiento (opcional)">
          <TextInput
            value={tratamiento}
            onChange={(e) => setTratamiento(e.target.value)}
            maxLength={2000}
            className={TOQUE_MINIMO}
          />
        </Field>
        <Field label="Conducta observada">
          <Select
            value={conducta}
            onChange={(e) => setConducta(e.target.value as ConductaObservada)}
            className={TOQUE_MINIMO}
          >
            {CONDUCTAS.map((c) => (
              <option key={c.valor} value={c.valor}>
                {c.etiqueta}
              </option>
            ))}
          </Select>
          {CONDUCTAS_CON_ALERTA.has(conducta) && (
            <span className="flex items-center gap-1.5 text-xs text-amber-800">
              <TriangleAlert className="size-3.5 shrink-0" aria-hidden />
              Esta conducta va a quedar marcada como alerta en el retiro.
            </span>
          )}
        </Field>

        <SelectorExtras valor={extras} onChange={setExtras} />

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onCerrar} type="button" className={TOQUE_MINIMO}>
            Cancelar
          </Button>
          <Button
            onClick={guardar}
            disabled={
              guardando || profesionalId === "" || anamnesis.trim() === "" || (peso !== "" && !(Number(peso) > 0))
            }
            type="button"
            className={TOQUE_MINIMO}
          >
            <Stethoscope className="size-4" aria-hidden />
            {guardando ? "Guardando…" : "Finalizar atención"}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          Al finalizar, el turno queda como atendido y la comanda (consulta + extras) pasa al mostrador.
        </p>
      </div>
    </Modal>
  )
}
