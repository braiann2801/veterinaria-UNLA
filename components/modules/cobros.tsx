"use client"

import { useMemo, useState } from "react"
import { AlertTriangle, Dog as DogIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Panel, Field, TextInput, Select } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import {
  useStore,
  ownerName,
  money,
  daysBetween,
  PRECIO_CONSULTA,
  PRECIO_DIA_GUARDERIA,
  type Reserva,
  type Turno,
} from "@/lib/store"

type Pendiente = {
  sourceId: string
  ownerId: string
  dogId: string
  detalle: string
  total: number
  sena: number
  retiraOpciones: string[]
}

export function Cobros() {
  const { turnos, reservas, enCaja, pagos, dogs, owners, today } = useStore()
  const [selected, setSelected] = useState<Pendiente | null>(null)

  const dog = (id: string) => dogs.find((d) => d.id === id)
  const owner = (id: string) => owners.find((o) => o.id === id)
  const yaCobrado = (sourceId: string) => pagos.some((p) => p.sourceId === sourceId)

  const pendientes = useMemo<Pendiente[]>(() => {
    const list: Pendiente[] = []

    // Consultas enviadas a caja
    turnos
      .filter((t: Turno) => enCaja.includes(t.id) && !yaCobrado(t.id))
      .forEach((t) => {
        const o = owner(t.ownerId)
        list.push({
          sourceId: t.id,
          ownerId: t.ownerId,
          dogId: t.dogId,
          detalle: `Consulta · ${t.motivo}`,
          total: PRECIO_CONSULTA,
          sena: 0,
          retiraOpciones: [ownerName(o)],
        })
      })

    // Guardería lista para retirar
    reservas
      .filter((r: Reserva) => !yaCobrado(r.id))
      .forEach((r) => {
        const o = owner(r.ownerId)
        const dias = daysBetween(r.ingreso, r.egreso)
        list.push({
          sourceId: r.id,
          ownerId: r.ownerId,
          dogId: r.dogId,
          detalle: `Guardería · ${dias} día${dias > 1 ? "s" : ""} (${r.ingreso} → ${r.egreso})`,
          total: dias * PRECIO_DIA_GUARDERIA,
          sena: r.sena,
          retiraOpciones: [ownerName(o), r.autorizadoNombre].filter(Boolean),
        })
      })

    return list
  }, [turnos, reservas, enCaja, pagos, dogs, owners])

  const cobradosHoy = pagos.filter((p) => p.fecha === today)

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-lg font-semibold">Cobros / Caja</h1>
        <p className="text-sm text-muted-foreground">Cobrar al retirar al animal</p>
      </div>

      <Panel title={`Pendientes de cobro (${pendientes.length})`}>
        {pendientes.length === 0 ? (
          <p className="text-sm text-muted-foreground">No hay cobros pendientes.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {pendientes.map((p) => (
              <li
                key={p.sourceId}
                className="flex items-center justify-between rounded-md border border-border px-3 py-2"
              >
                <div className="flex items-center gap-3">
                  <DogIcon className="h-4 w-4 text-muted-foreground" />
                  <div>
                    <p className="text-sm font-medium">
                      {dog(p.dogId)?.nombre}{" "}
                      <span className="font-normal text-muted-foreground">
                        · {ownerName(owner(p.ownerId))}
                      </span>
                    </p>
                    <p className="text-xs text-muted-foreground">{p.detalle}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-medium tabular-nums">{money(p.total)}</span>
                  <Button size="sm" onClick={() => setSelected(p)}>
                    Cobrar
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Panel title={`Cobrado hoy (${cobradosHoy.length})`}>
        {cobradosHoy.length === 0 ? (
          <p className="text-sm text-muted-foreground">Todavía no se registraron cobros.</p>
        ) : (
          <ul className="flex flex-col divide-y divide-border">
            {cobradosHoy.map((p) => (
              <li key={p.id} className="flex items-center justify-between py-2 text-sm">
                <div>
                  <p className="font-medium">
                    {dog(p.dogId)?.nombre}{" "}
                    <span className="font-normal text-muted-foreground">· {p.detalle}</span>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Retiró: {p.retiraPor || "—"}
                  </p>
                </div>
                <div className="text-right tabular-nums">
                  <p className="font-medium">{money(p.total)}</p>
                  {p.saldoPendiente > 0 && (
                    <p className="text-xs text-muted-foreground">
                      debe {money(p.saldoPendiente)}
                    </p>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {selected && <CobrarModal item={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}

function CobrarModal({ item, onClose }: { item: Pendiente; onClose: () => void }) {
  const { addPago, today } = useStore()
  const [efectivo, setEfectivo] = useState("")
  const [transferencia, setTransferencia] = useState("")
  const [retiraPor, setRetiraPor] = useState(item.retiraOpciones[0] ?? "")

  const saldoBase = item.total - item.sena
  const pagado = (Number(efectivo) || 0) + (Number(transferencia) || 0)
  const saldoPendiente = Math.max(0, saldoBase - pagado)

  const cobrar = () => {
    addPago({
      sourceId: item.sourceId,
      ownerId: item.ownerId,
      dogId: item.dogId,
      detalle: item.detalle,
      total: item.total,
      sena: item.sena,
      efectivo: Number(efectivo) || 0,
      transferencia: Number(transferencia) || 0,
      saldoPendiente,
      retiraPor,
      fecha: today,
    })
    onClose()
  }

  return (
    <Modal open onClose={onClose} title="Cobrar">
      <div className="flex flex-col gap-3">
        <div className="rounded-md border border-border bg-muted/40 p-3 text-sm">
          <Row label="Total servicios" value={money(item.total)} />
          <Row label="Seña entregada" value={"− " + money(item.sena)} />
          <div className="mt-1 border-t border-border pt-1">
            <Row label="Saldo a cobrar" value={money(saldoBase)} strong />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Efectivo">
            <TextInput
              type="number"
              inputMode="numeric"
              value={efectivo}
              onChange={(e) => setEfectivo(e.target.value)}
              placeholder="0"
            />
          </Field>
          <Field label="Transferencia">
            <TextInput
              type="number"
              inputMode="numeric"
              value={transferencia}
              onChange={(e) => setTransferencia(e.target.value)}
              placeholder="0"
            />
          </Field>
        </div>

        <Field label="Retira">
          <Select value={retiraPor} onChange={(e) => setRetiraPor(e.target.value)}>
            {item.retiraOpciones.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </Select>
        </Field>

        <div className="rounded-md border border-border p-3 text-sm">
          <Row label="Saldo final" value={money(saldoPendiente)} strong />
        </div>

        {saldoPendiente > 0 && (
          <div className="flex items-start gap-2 rounded-md border border-foreground/30 bg-muted/40 p-3 text-sm">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              Queda un saldo pendiente de <strong>{money(saldoPendiente)}</strong>. Se registrará
              como deuda del cliente.
            </span>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={cobrar}>Confirmar cobro</Button>
        </div>
      </div>
    </Modal>
  )
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-center justify-between py-0.5">
      <span className="text-muted-foreground">{label}</span>
      <span className={strong ? "font-semibold tabular-nums" : "tabular-nums"}>{value}</span>
    </div>
  )
}
