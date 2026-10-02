"use client"

/**
 * SIN MONTAR. Esta pantalla escribe en el store en memoria de `lib/store.tsx`,
 * asi que las reservas no llegan a MySQL.
 *
 * <p>Reemplazada para el alta por `components/modules/nueva-reserva.tsx`, que
 * sí persiste contra `POST /api/v1/guarderia`, y para el aforo por la seccion
 * de guarderia de `components/modules/agenda.tsx`. Ningun modulo la importa.</p>
 *
 * <p>Se conserva como referencia del flujo de seña y persona autorizada a
 * retirar, pero montarla devolveria al mostrador una pantalla que acepta datos
 * y los pierde. Si hay que recuperarla, hay que rehacerla contra `lib/api.ts`.</p>
 */

import { useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { Panel, Field, TextInput, Select } from "@/components/ui/field"
import { useStore, ownerName, money } from "@/lib/store"

function daysBetween(a: string, b: string): string[] {
  if (!a || !b || a > b) return []
  const res: string[] = []
  const d = new Date(a + "T00:00:00")
  const end = new Date(b + "T00:00:00")
  while (d <= end) {
    res.push(d.toISOString().slice(0, 10))
    d.setDate(d.getDate() + 1)
  }
  return res
}

export function Guarderia() {
  const { dogs, owners, reservas, addReserva, cupoMax, ocupadosPorDia, today } = useStore()
  const [dogId, setDogId] = useState("")
  const [autorizadoNombre, setAutorizadoNombre] = useState("")
  const [autorizadoTelefono, setAutorizadoTelefono] = useState("")
  const [ingreso, setIngreso] = useState(today)
  const [egreso, setEgreso] = useState(today)
  const [sena, setSena] = useState("")

  const dias = useMemo(() => daysBetween(ingreso, egreso), [ingreso, egreso])

  // Cupo teniendo en cuenta la reserva que se está por crear (aún no guardada)
  const diasCompletos = dias.filter((d) => ocupadosPorDia(d) >= cupoMax)
  const hayCupoLleno = diasCompletos.length > 0
  const puedeGuardar = Boolean(dogId) && dias.length > 0 && !hayCupoLleno

  const submit = () => {
    const d = dogs.find((x) => x.id === dogId)
    if (!d || !puedeGuardar) return
    addReserva({
      dogId,
      ownerId: d.ownerId,
      autorizadoNombre,
      autorizadoTelefono,
      ingreso,
      egreso,
      sena: Number(sena) || 0,
    })
    setDogId("")
    setAutorizadoNombre("")
    setAutorizadoTelefono("")
    setSena("")
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-lg font-semibold">Guardería</h1>
        <p className="text-sm text-muted-foreground">Reservas de estadía · cupo de {cupoMax} perros por día</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <Panel title="Nueva reserva" className="lg:col-span-3">
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
              <Field label="Fecha de ingreso">
                <TextInput type="date" value={ingreso} onChange={(e) => setIngreso(e.target.value)} />
              </Field>
              <Field label="Fecha de egreso">
                <TextInput type="date" value={egreso} onChange={(e) => setEgreso(e.target.value)} />
              </Field>
            </div>

            <div className="rounded-md border border-border p-3">
              <p className="text-xs font-medium text-muted-foreground">Persona autorizada a retirar</p>
              <div className="mt-2 grid gap-3 sm:grid-cols-2">
                <Field label="Nombre">
                  <TextInput value={autorizadoNombre} onChange={(e) => setAutorizadoNombre(e.target.value)} />
                </Field>
                <Field label="Teléfono">
                  <TextInput value={autorizadoTelefono} onChange={(e) => setAutorizadoTelefono(e.target.value)} inputMode="tel" />
                </Field>
              </div>
            </div>

            <Field label="Seña inicial (pesos)">
              <TextInput value={sena} onChange={(e) => setSena(e.target.value)} inputMode="numeric" placeholder="0" />
            </Field>

            {hayCupoLleno && (
              <p className="rounded-md border border-foreground/30 bg-muted/50 px-3 py-2 text-sm font-bold">
                Cupo de {cupoMax} perros completo en esta fecha
              </p>
            )}

            <div className="flex justify-end">
              <Button onClick={submit} disabled={!puedeGuardar}>
                Guardar reserva
              </Button>
            </div>
          </div>
        </Panel>

        <Panel title="Disponibilidad por día" className="lg:col-span-2">
          {dias.length === 0 ? (
            <p className="text-sm text-muted-foreground">Elegí fechas de ingreso y egreso válidas.</p>
          ) : (
            <ul className="flex flex-col gap-1.5">
              {dias.map((d) => {
                const n = ocupadosPorDia(d)
                const lleno = n >= cupoMax
                return (
                  <li
                    key={d}
                    className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm"
                  >
                    <span>{d}</span>
                    <span className={lleno ? "font-bold" : "text-muted-foreground"}>
                      {lleno ? `Completo (${cupoMax}/${cupoMax})` : `${n}/${cupoMax} anotados`}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </Panel>
      </div>

      <Panel title="Reservas registradas">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs text-muted-foreground">
                <th className="py-2 pr-4 font-medium">Perro</th>
                <th className="py-2 pr-4 font-medium">Dueño</th>
                <th className="py-2 pr-4 font-medium">Ingreso</th>
                <th className="py-2 pr-4 font-medium">Egreso</th>
                <th className="py-2 pr-4 font-medium">Autorizado</th>
                <th className="py-2 pr-4 font-medium">Seña</th>
              </tr>
            </thead>
            <tbody>
              {reservas.map((r) => {
                const d = dogs.find((x) => x.id === r.dogId)
                const o = owners.find((x) => x.id === r.ownerId)
                return (
                  <tr key={r.id} className="border-b border-border last:border-0">
                    <td className="py-2 pr-4 font-medium">{d?.nombre}</td>
                    <td className="py-2 pr-4">{ownerName(o)}</td>
                    <td className="py-2 pr-4">{r.ingreso}</td>
                    <td className="py-2 pr-4">{r.egreso}</td>
                    <td className="py-2 pr-4">{r.autorizadoNombre || "—"}</td>
                    <td className="py-2 pr-4">{money(r.sena)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  )
}
