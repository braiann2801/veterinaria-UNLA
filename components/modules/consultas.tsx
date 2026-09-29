"use client"

import { useState } from "react"
import { CheckCircle2, Clock, Sparkles } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Panel, Field, TextInput, TextArea } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { useStore, ownerName, type Turno } from "@/lib/store"

// Bloques de 45 min de consulta + 15 min de limpieza, desde las 09:00
function buildSlots(start = 9, count = 8) {
  const slots: { hora: string; fin: string }[] = []
  let mins = start * 60
  for (let i = 0; i < count; i++) {
    const h = Math.floor(mins / 60)
    const m = mins % 60
    const finMins = mins + 45
    const fh = Math.floor(finMins / 60)
    const fm = finMins % 60
    const fmt = (a: number, b: number) => `${String(a).padStart(2, "0")}:${String(b).padStart(2, "0")}`
    slots.push({ hora: fmt(h, m), fin: fmt(fh, fm) })
    mins += 60 // 45 consulta + 15 limpieza
  }
  return slots
}

export function Consultas() {
  const { turnos, dogs, owners, today } = useStore()
  const [selected, setSelected] = useState<Turno | null>(null)

  const slots = buildSlots()
  const turnosHoy = turnos.filter((t) => t.fecha === today)
  const turnoAt = (hora: string) => turnosHoy.find((t) => t.hora === hora)
  const dog = (id: string) => dogs.find((d) => d.id === id)
  const owner = (id: string) => owners.find((o) => o.id === id)

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-lg font-semibold">Consultas</h1>
        <p className="text-sm text-muted-foreground">
          Agenda de hoy · bloques de 45 min con 15 min de limpieza
        </p>
      </div>

      <Panel title="Agenda del día">
        <ul className="flex flex-col gap-2">
          {slots.map((s) => {
            const t = turnoAt(s.hora)
            const d = t ? dog(t.dogId) : undefined
            return (
              <li key={s.hora}>
                <div className="flex items-stretch gap-3">
                  <div className="w-28 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    {s.hora}–{s.fin}
                  </div>
                  {t ? (
                    <button
                      onClick={() => setSelected(t)}
                      className="flex flex-1 items-center justify-between rounded-md border border-border px-3 py-2 text-left transition-colors hover:bg-muted"
                    >
                      <div>
                        <p className="text-sm font-medium">
                          {d?.nombre} <span className="font-normal text-muted-foreground">· {ownerName(owner(t.ownerId))}</span>
                        </p>
                        <p className="text-xs text-muted-foreground">{t.motivo}</p>
                      </div>
                      {t.estado === "atendido" ? (
                        <span className="flex items-center gap-1 text-xs">
                          <CheckCircle2 className="h-3.5 w-3.5" /> Atendido
                        </span>
                      ) : (
                        <span className="flex items-center gap-1 text-xs text-muted-foreground">
                          <Clock className="h-3.5 w-3.5" /> Pendiente
                        </span>
                      )}
                    </button>
                  ) : (
                    <div className="flex-1 rounded-md border border-dashed border-border px-3 py-2 text-sm text-muted-foreground">
                      Libre
                    </div>
                  )}
                </div>
                <div className="ml-28 flex items-center gap-1 pl-3 pt-1 text-[11px] text-muted-foreground">
                  <Sparkles className="h-3 w-3" /> {s.fin}–{addMin(s.fin, 15)} limpieza/desinfección
                </div>
              </li>
            )
          })}
        </ul>
      </Panel>

      {selected && (
        <FichaConsultaModal turno={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  )
}

function addMin(hhmm: string, add: number) {
  const [h, m] = hhmm.split(":").map(Number)
  const total = h * 60 + m + add
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`
}

function FichaConsultaModal({ turno, onClose }: { turno: Turno; onClose: () => void }) {
  const { dogs, addFicha, marcarAtendido, enviarACaja, owners, today } = useStore()
  const dog = dogs.find((d) => d.id === turno.dogId)
  const [form, setForm] = useState({
    motivo: turno.motivo,
    diagnostico: "",
    tratamiento: "",
    peso: "",
    conducta: "",
  })

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }))

  const guardarYEnviar = () => {
    addFicha({ dogId: turno.dogId, fecha: today, ...form })
    marcarAtendido(turno.id)
    enviarACaja(turno.id)
    onClose()
  }

  return (
    <Modal open onClose={onClose} title={`Ficha médica · ${dog?.nombre ?? ""}`}>
      <div className="flex flex-col gap-3">
        <p className="text-xs text-muted-foreground">
          Dueño: {ownerName(owners.find((o) => o.id === turno.ownerId))} · {turno.hora}
        </p>
        <Field label="Motivo de consulta">
          <TextInput value={form.motivo} onChange={set("motivo")} />
        </Field>
        <Field label="Diagnóstico">
          <TextInput value={form.diagnostico} onChange={set("diagnostico")} />
        </Field>
        <Field label="Tratamiento o vacuna dada">
          <TextInput value={form.tratamiento} onChange={set("tratamiento")} />
        </Field>
        <Field label="Peso del animal">
          <TextInput value={form.peso} onChange={set("peso")} placeholder="Ej: 22 kg" />
        </Field>
        <Field label="Conducta (reactivo, miedoso, etc.)">
          <TextArea value={form.conducta} onChange={set("conducta")} />
        </Field>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button onClick={guardarYEnviar}>Guardar consulta y enviar a caja</Button>
        </div>
      </div>
    </Modal>
  )
}
