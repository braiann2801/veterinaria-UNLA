"use client"

import { useState } from "react"
import { AlertTriangle, UserPlus, CalendarPlus, LogIn, LogOut } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Panel, Field, TextInput, Select } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { useStore, ownerName, money, type Tutor } from "@/lib/store"

export function Mostrador() {
  const { turnos, reservas, dogs, owners, today, cupoMax, ocupadosPorDia } = useStore()
  const [openCliente, setOpenCliente] = useState(false)
  const [openTurno, setOpenTurno] = useState(false)

  const dog = (id: string) => dogs.find((d) => d.id === id)
  const owner = (id: string) => owners.find((o) => o.id === id)

  const turnosHoy = turnos.filter((t) => t.fecha === today)
  const ingresosHoy = reservas.filter((r) => r.ingreso === today)
  const egresosHoy = reservas.filter((r) => r.egreso === today)

  const ocupados = ocupadosPorDia(today)

  // Avisos: perros de hoy con conducta de riesgo + dueños con deuda
  const dogsHoy = new Set<string>([
    ...turnosHoy.map((t) => t.dogId),
    ...ingresosHoy.map((r) => r.dogId),
    ...egresosHoy.map((r) => r.dogId),
  ])
  const avisosConducta = [...dogsHoy]
    .map((id) => dog(id))
    .filter((d) => d && (d.conducta === "muerde" || d.conducta === "gruñe"))
  const ownersHoy = new Set<string>([
    ...turnosHoy.map((t) => t.ownerId),
    ...ingresosHoy.map((r) => r.ownerId),
    ...egresosHoy.map((r) => r.ownerId),
  ])
  const avisosDeuda = [...ownersHoy]
    .map((id) => owner(id))
    .filter((o) => o && o.deuda > 0)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">Mostrador</h1>
          <p className="text-sm text-muted-foreground">Movimiento de hoy · {today}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setOpenCliente(true)}>
            <UserPlus className="h-4 w-4" /> Cargar cliente
          </Button>
          <Button onClick={() => setOpenTurno(true)}>
            <CalendarPlus className="h-4 w-4" /> Agregar turno
          </Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="Ocupación guardería" className="lg:col-span-1">
          <p className="text-3xl font-bold">
            {ocupados} <span className="text-base font-normal text-muted-foreground">de {cupoMax} ocupados</span>
          </p>
          <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-foreground transition-all"
              style={{ width: `${Math.min(100, (ocupados / cupoMax) * 100)}%` }}
            />
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            {cupoMax - ocupados} lugares disponibles hoy
          </p>
        </Panel>

        <Panel title="Avisos importantes" className="lg:col-span-2">
          {avisosConducta.length === 0 && avisosDeuda.length === 0 ? (
            <p className="text-sm text-muted-foreground">Sin avisos para hoy.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {avisosConducta.map((d) => (
                <li key={`c-${d!.id}`} className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span>
                    <strong>{d!.nombre}</strong> {d!.conducta === "muerde" ? "muerde" : "gruñe"} — manejar con cuidado.
                  </span>
                </li>
              ))}
              {avisosDeuda.map((o) => (
                <li key={`d-${o!.id}`} className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span>
                    <strong>{ownerName(o)}</strong> viene con deuda previa de {money(o!.deuda)}.
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>

      <Panel title="Turnos y estadías de hoy">
        <div className="grid gap-4 md:grid-cols-3">
          <TurnoCol title="Turnos" items={turnosHoy.map((t) => ({
            id: t.id, top: `${dog(t.dogId)?.nombre ?? "—"} · ${t.hora}`, sub: `${ownerName(owner(t.ownerId))} · ${t.motivo}`,
          }))} icon="turno" />
          <TurnoCol title="Ingresan (guardería)" items={ingresosHoy.map((r) => ({
            id: r.id, top: dog(r.dogId)?.nombre ?? "—", sub: `Hasta ${r.egreso}`,
          }))} icon="in" />
          <TurnoCol title="Se retiran" items={egresosHoy.map((r) => ({
            id: r.id, top: dog(r.dogId)?.nombre ?? "—", sub: `Retira: ${r.autorizadoNombre || ownerName(owner(r.ownerId))}`,
          }))} icon="out" />
        </div>
      </Panel>

      <CargarClienteModal open={openCliente} onClose={() => setOpenCliente(false)} />
      <AgregarTurnoModal open={openTurno} onClose={() => setOpenTurno(false)} />
    </div>
  )
}

function TurnoCol({
  title,
  items,
  icon,
}: {
  title: string
  items: { id: string; top: string; sub: string }[]
  icon: "turno" | "in" | "out"
}) {
  return (
    <div className="rounded-md border border-border">
      <div className="flex items-center gap-2 border-b border-border bg-muted/40 px-3 py-2 text-xs font-medium">
        {icon === "in" && <LogIn className="h-3.5 w-3.5" />}
        {icon === "out" && <LogOut className="h-3.5 w-3.5" />}
        {title} <span className="ml-auto text-muted-foreground">{items.length}</span>
      </div>
      <ul className="divide-y divide-border">
        {items.length === 0 ? (
          <li className="px-3 py-3 text-sm text-muted-foreground">Sin registros.</li>
        ) : (
          items.map((it) => (
            <li key={it.id} className="px-3 py-2">
              <p className="text-sm font-medium">{it.top}</p>
              <p className="text-xs text-muted-foreground">{it.sub}</p>
            </li>
          ))
        )}
      </ul>
    </div>
  )
}

function CargarClienteModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { addOwnerWithDog } = useStore()
  const [form, setForm] = useState({ dogNombre: "", dni: "", telefono: "", nombre: "", apellido: "" })
  const [tutores, setTutores] = useState<Tutor[]>([])

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }))

  const submit = () => {
    if (!form.dogNombre || !form.nombre || !form.apellido) return
    addOwnerWithDog({ ...form, tutores: tutores.filter((t) => t.nombre) })
    setForm({ dogNombre: "", dni: "", telefono: "", nombre: "", apellido: "" })
    setTutores([])
    onClose()
  }

  return (
    <Modal open={open} onClose={onClose} title="Cargar cliente">
      <div className="flex flex-col gap-3">
        <Field label="Nombre del perro">
          <TextInput value={form.dogNombre} onChange={set("dogNombre")} placeholder="Ej: Rocky" />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Nombre del dueño">
            <TextInput value={form.nombre} onChange={set("nombre")} />
          </Field>
          <Field label="Apellido del dueño">
            <TextInput value={form.apellido} onChange={set("apellido")} />
          </Field>
          <Field label="DNI">
            <TextInput value={form.dni} onChange={set("dni")} inputMode="numeric" />
          </Field>
          <Field label="Teléfono">
            <TextInput value={form.telefono} onChange={set("telefono")} inputMode="tel" />
          </Field>
        </div>

        <div className="rounded-md border border-border p-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-muted-foreground">Otros tutores (opcional)</p>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setTutores((t) => [...t, { nombre: "", telefono: "" }])}
            >
              Agregar tutor
            </Button>
          </div>
          {tutores.map((t, i) => (
            <div key={i} className="mt-2 grid gap-2 sm:grid-cols-2">
              <TextInput
                placeholder="Nombre"
                value={t.nombre}
                onChange={(e) =>
                  setTutores((arr) => arr.map((x, j) => (j === i ? { ...x, nombre: e.target.value } : x)))
                }
              />
              <TextInput
                placeholder="Teléfono"
                value={t.telefono}
                onChange={(e) =>
                  setTutores((arr) => arr.map((x, j) => (j === i ? { ...x, telefono: e.target.value } : x)))
                }
              />
            </div>
          ))}
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button onClick={submit}>Guardar cliente</Button>
        </div>
      </div>
    </Modal>
  )
}

function AgregarTurnoModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { dogs, owners, addTurno, today } = useStore()
  const [dogId, setDogId] = useState("")
  const [hora, setHora] = useState("09:00")
  const [motivo, setMotivo] = useState("")
  const [fecha, setFecha] = useState(today)

  const submit = () => {
    const d = dogs.find((x) => x.id === dogId)
    if (!d || !motivo) return
    addTurno({ dogId, ownerId: d.ownerId, hora, motivo, fecha })
    setDogId("")
    setMotivo("")
    onClose()
  }

  return (
    <Modal open={open} onClose={onClose} title="Agregar turno">
      <div className="flex flex-col gap-3">
        <Field label="Perro">
          <Select value={dogId} onChange={(e) => setDogId(e.target.value)}>
            <option value="">Seleccionar…</option>
            {dogs.map((d) => (
              <option key={d.id} value={d.id}>
                {d.nombre} — {ownerName(owners.find((o) => o.id === d.ownerId))}
              </option>
            ))}
          </Select>
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Fecha">
            <TextInput type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
          </Field>
          <Field label="Hora">
            <TextInput type="time" value={hora} onChange={(e) => setHora(e.target.value)} />
          </Field>
        </div>
        <Field label="Motivo de consulta">
          <TextInput value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej: Vacuna, control…" />
        </Field>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button onClick={submit}>Guardar turno</Button>
        </div>
      </div>
    </Modal>
  )
}
