"use client"

import { useMemo, useState } from "react"
import { AlertTriangle, FileText, Plus, Search } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Panel, TextInput } from "@/components/ui/field"
import { useStore, ownerName, type Conducta } from "@/lib/store"
import { cn } from "@/lib/utils"

const conductaLabel: Record<Conducta, string> = {
  normal: "Sociable / normal",
  gruñe: "Gruñe",
  muerde: "Muerde — manejar con cuidado",
  miedoso: "Miedoso",
}

const conductaAlerta = (c: Conducta) => c === "muerde" || c === "gruñe"

export function Fichas({ initialDogId }: { initialDogId?: string | null }) {
  const { dogs, owners, fichas } = useStore()
  const [q, setQ] = useState("")
  const [selectedId, setSelectedId] = useState<string | null>(
    initialDogId ?? dogs[0]?.id ?? null,
  )

  const owner = (id: string) => owners.find((o) => o.id === id)

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase()
    if (!term) return dogs
    return dogs.filter((d) => {
      const o = owner(d.ownerId)
      return (
        d.nombre.toLowerCase().includes(term) ||
        (o && ownerName(o).toLowerCase().includes(term))
      )
    })
  }, [q, dogs, owners])

  const selected = dogs.find((d) => d.id === selectedId)
  const selectedOwner = selected ? owner(selected.ownerId) : undefined
  const historia = fichas
    .filter((f) => f.dogId === selectedId)
    .sort((a, b) => (a.fecha < b.fecha ? 1 : -1))

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">Fichas médicas</h1>
          <p className="text-sm text-muted-foreground">Historia clínica y conducta de cada animal</p>
        </div>
        <Button type="button" variant="outline" className="shrink-0">
          <Plus />
          Agregar ficha médica
        </Button>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,320px)_1fr]">
        <Panel title="Animales">
          <div className="relative mb-3">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <TextInput
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar por perro o dueño"
              className="pl-9"
            />
          </div>
          <ul className="flex max-h-[420px] flex-col gap-1 overflow-y-auto">
            {filtered.map((d) => {
              const o = owner(d.ownerId)
              const active = d.id === selectedId
              return (
                <li key={d.id}>
                  <button
                    onClick={() => setSelectedId(d.id)}
                    className={cn(
                      "flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm transition-colors",
                      active
                        ? "border-foreground bg-muted"
                        : "border-transparent hover:bg-muted",
                    )}
                  >
                    <span>
                      <span className="font-medium">{d.nombre}</span>
                      <span className="block text-xs text-muted-foreground">
                        {ownerName(o)}
                      </span>
                    </span>
                    {conductaAlerta(d.conducta) && (
                      <AlertTriangle className="h-4 w-4 shrink-0" />
                    )}
                  </button>
                </li>
              )
            })}
            {filtered.length === 0 && (
              <li className="px-1 py-2 text-sm text-muted-foreground">Sin resultados.</li>
            )}
          </ul>
        </Panel>

        <div className="flex flex-col gap-4">
          {selected ? (
            <>
              <Panel>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-base font-semibold">{selected.nombre}</h2>
                    <p className="text-sm text-muted-foreground">
                      Dueño: {ownerName(selectedOwner)}
                    </p>
                  </div>
                </div>
                <div
                  className={cn(
                    "mt-3 flex items-center gap-2 rounded-md border px-3 py-2 text-sm",
                    conductaAlerta(selected.conducta)
                      ? "border-foreground/40 bg-muted/50 font-medium"
                      : "border-border text-muted-foreground",
                  )}
                >
                  {conductaAlerta(selected.conducta) && (
                    <AlertTriangle className="h-4 w-4 shrink-0" />
                  )}
                  Conducta: {conductaLabel[selected.conducta]}
                </div>
              </Panel>

              <Panel title={`Historia clínica (${historia.length})`}>
                {historia.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    Sin consultas registradas todavía.
                  </p>
                ) : (
                  <ul className="flex flex-col gap-3">
                    {historia.map((f) => (
                      <li key={f.id} className="rounded-md border border-border p-3">
                        <div className="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
                          <FileText className="h-3.5 w-3.5" />
                          {f.fecha}
                          {f.peso && <span>· {f.peso}</span>}
                        </div>
                        <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-2">
                          <Item label="Motivo" value={f.motivo} />
                          <Item label="Diagnóstico" value={f.diagnostico} />
                          <Item label="Tratamiento / vacuna" value={f.tratamiento} />
                          <Item label="Conducta observada" value={f.conducta} />
                        </dl>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </>
          ) : (
            <Panel>
              <p className="text-sm text-muted-foreground">
                Seleccioná un animal para ver su ficha.
              </p>
            </Panel>
          )}
        </div>
      </div>
    </div>
  )
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd>{value || "—"}</dd>
    </div>
  )
}
