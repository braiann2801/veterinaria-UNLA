"use client"

import { Banknote, ArrowLeftRight, Wallet, AlertTriangle } from "lucide-react"
import { Panel } from "@/components/ui/field"
import { useStore, money, ownerName } from "@/lib/store"

export function Balance() {
  const { pagos, owners, dogs, today } = useStore()
  const hoy = pagos.filter((p) => p.fecha === today)

  const efectivo = hoy.reduce((s, p) => s + p.efectivo, 0)
  const transferencia = hoy.reduce((s, p) => s + p.transferencia, 0)
  const senas = hoy.reduce((s, p) => s + p.sena, 0)
  const pendiente = hoy.reduce((s, p) => s + p.saldoPendiente, 0)
  const totalCaja = efectivo + transferencia

  const dog = (id: string) => dogs.find((d) => d.id === id)
  const owner = (id: string) => owners.find((o) => o.id === id)

  const cards = [
    { label: "Efectivo", value: efectivo, icon: Banknote },
    { label: "Transferencias", value: transferencia, icon: ArrowLeftRight },
    { label: "Total en caja", value: totalCaja, icon: Wallet },
    { label: "Saldos pendientes", value: pendiente, icon: AlertTriangle },
  ]

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-lg font-semibold">Balance del día</h1>
        <p className="text-sm text-muted-foreground">{today} · resumen de caja</p>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-lg border border-border bg-card p-4 shadow-sm">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <c.icon className="h-4 w-4" />
              {c.label}
            </div>
            <p className="mt-2 text-2xl font-semibold tabular-nums">{money(c.value)}</p>
          </div>
        ))}
      </div>

      <Panel title={`Movimientos de hoy (${hoy.length})`}>
        {hoy.length === 0 ? (
          <p className="text-sm text-muted-foreground">Sin movimientos registrados.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="py-2 pr-3 font-medium">Cliente</th>
                  <th className="py-2 pr-3 font-medium">Detalle</th>
                  <th className="py-2 pr-3 text-right font-medium">Efectivo</th>
                  <th className="py-2 pr-3 text-right font-medium">Transf.</th>
                  <th className="py-2 pr-3 text-right font-medium">Seña</th>
                  <th className="py-2 text-right font-medium">Pendiente</th>
                </tr>
              </thead>
              <tbody>
                {hoy.map((p) => (
                  <tr key={p.id} className="border-b border-border/60 last:border-0">
                    <td className="py-2 pr-3">
                      <span className="font-medium">{dog(p.dogId)?.nombre}</span>
                      <span className="text-muted-foreground"> · {ownerName(owner(p.ownerId))}</span>
                    </td>
                    <td className="py-2 pr-3 text-muted-foreground">{p.detalle}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{money(p.efectivo)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{money(p.transferencia)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{money(p.sena)}</td>
                    <td className="py-2 text-right tabular-nums">
                      {p.saldoPendiente > 0 ? money(p.saldoPendiente) : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}
