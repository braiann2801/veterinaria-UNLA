"use client"

import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { Field, TextInput, Select } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import {
  turnos as apiTurnos,
  mascotas as apiMascotas,
  profesionales as apiProfesionales,
  hoyLocal,
} from "@/lib/api"
import type { Mascota, Profesional } from "@/lib/api"
import { TOQUE_MINIMO } from "@/lib/vista"

/**
 * Alta de turno contra `POST /api/v1/turnos`.
 *
 * <p>Antes escribía en `addTurno`, o sea en el store en memoria, y el turno no
 * llegaba a la Agenda: la grilla de Agenda lee la base. Ahora el turno se guarda
 * de verdad y aparece ahí al cambiar de pestaña.</p>
 *
 * <p>El profesional es obligatorio en el contrato (`@NotNull profesionalId`), así
 * que el selector no es opcional: un turno sin profesional bloquearía el
 * consultorio entero en la franja de 45+15 y el backend lo rechaza con 400.</p>
 *
 * <p>La fecha arranca en `hoyLocal()` y no lleva `min`: los turnos retroactivos se
 * registran a propósito, que es la atención que ya ocurrió y hay que documentar.</p>
 */
export function AgregarTurnoModal({
  open,
  onClose,
  onGuardado,
}: {
  open: boolean
  onClose: () => void
  /**
   * Avisa al tablero que relea la jornada.
   *
   * <p>Es el caso que sí hace falta: el modal vive dentro del Mostrador, así que
   * el componente no se desmonta entre el alta y la lectura, y sin esto el turno
   * recién creado no aparecería en la grilla de arriba.</p>
   */
  onGuardado: () => void | Promise<void>
}) {
  const [mascotas, setMascotas] = useState<Mascota[]>([])
  const [profesionales, setProfesionales] = useState<Profesional[]>([])
  const [mascotaId, setMascotaId] = useState("")
  const [profesionalId, setProfesionalId] = useState("")
  const [fecha, setFecha] = useState(hoyLocal())
  const [hora, setHora] = useState("09:00")
  const [motivo, setMotivo] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    if (!open) return
    let vigente = true
    setExito(null)
    setError(null)
    // Ambos listados son necesarios para guardar: sin mascota o sin profesional
    // el contrato no se cumple, así que la pantalla avisa en vez de fallar al enviar.
    Promise.all([apiMascotas.listar(), apiProfesionales.listar()])
      .then(([m, p]) => {
        if (!vigente) return
        setMascotas(m)
        setProfesionales(p.filter((x) => x.activo))
        setMascotaId((prev) => prev || (m[0] ? String(m[0].id) : ""))
        setProfesionalId((prev) => {
          const activos = p.filter((x) => x.activo)
          return prev || (activos[0] ? String(activos[0].id) : "")
        })
      })
      .catch((e: unknown) => {
        if (vigente) setError(e instanceof Error ? e.message : "No se pudo leer el padrón")
      })
    return () => {
      vigente = false
    }
  }, [open])

  async function submit() {
    setGuardando(true)
    setError(null)
    setExito(null)
    try {
      // `fecha` + `hora` se unen en un LocalDateTime. El backend deriva solo el
      // fin de atención (+45) y el de bloque (+60): mandarlos sería saltarse la
      // ventana de desinfección.
      await apiTurnos.crear({
        fechaHoraInicio: `${fecha}T${hora}:00`,
        motivo: motivo.trim(),
        mascotaId: Number(mascotaId),
        profesionalId: Number(profesionalId),
      })
      // Antes del aviso, nunca después: el turno tiene que verse en la grilla
      // cuando el operador lee el "guardado".
      await onGuardado()
      setExito("Turno guardado. Ya está en Agenda y en el tablero de arriba.")
      setMotivo("")
    } catch (e) {
      // El 409 de solapamiento lo produce el dominio: el mensaje del servidor
      // explica qué profesional está ocupado y en qué ventana. Se muestra tal cual
      // en vez de reimprimir la regla acá.
      setError(e instanceof Error ? e.message : "No se pudo guardar el turno")
    } finally {
      setGuardando(false)
    }
  }

  const listo = mascotaId !== "" && profesionalId !== "" && motivo.trim().length > 0

  return (
    <Modal open={open} onClose={onClose} title="Agregar turno">
      <div className="flex flex-col gap-3">
        <Field label="Mascota">
          {mascotas.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No hay mascotas cargadas. Cargá al cliente desde el botón "Cargar
              cliente" o desde el Padrón.
            </p>
          ) : (
            <Select
              value={mascotaId}
              onChange={(e) => setMascotaId(e.target.value)}
              className={TOQUE_MINIMO}
              aria-label="Mascota del turno"
            >
              {mascotas.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nombre} — {m.especie.toLowerCase()}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Profesional">
          {profesionales.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No hay profesionales activos. Cargalos desde el padrón de profesionales.
            </p>
          ) : (
            <Select
              value={profesionalId}
              onChange={(e) => setProfesionalId(e.target.value)}
              className={TOQUE_MINIMO}
              aria-label="Profesional del turno"
            >
              {profesionales.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre} {p.apellido}
                  {p.matricula ? ` · ${p.matricula}` : ""}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Fecha">
            <TextInput
              type="date"
              value={fecha}
              onChange={(e) => setFecha(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Hora">
            <TextInput
              type="time"
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

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        {exito && (
          <p className="rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" role="status">
            {exito}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} type="button">
            Cerrar
          </Button>
          <Button onClick={submit} disabled={guardando || !listo} type="button">
            {guardando ? "Guardando…" : "Guardar turno"}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
