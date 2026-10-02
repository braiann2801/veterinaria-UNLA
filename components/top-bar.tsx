"use client"

import { Search, FileText } from "lucide-react"
import { useStore, ownerName, roleLabels, type Role } from "@/lib/store"

export function TopBar({
  onVerFichas,
}: {
  onVerFichas: (dogId: string) => void
}) {
  const { query, setQuery, role, setRole, dogs, owners } = useStore()

  const q = query.trim().toLowerCase()
  const matches = q
    ? dogs
        .map((d) => ({ dog: d, owner: owners.find((o) => o.id === d.ownerId) }))
        .filter(({ dog, owner }) => {
          if (!owner) return false
          return (
            dog.nombre.toLowerCase().includes(q) ||
            owner.dni.includes(q) ||
            owner.telefono.includes(q) ||
            ownerName(owner).toLowerCase().includes(q)
          )
        })
        .slice(0, 6)
    : []

  const canVerFichas = role === "veterinario" || role === "duenio"

  return (
    <header className="flex items-center gap-3 border-b border-border bg-background px-4 py-3">
      <div className="relative flex-1 max-w-xl">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Buscar por DNI, teléfono o nombre"
          className="min-h-[44px] w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm outline-none focus:border-foreground focus:ring-1 focus:ring-ring"
        />
        {matches.length > 0 && (
          <ul className="absolute left-0 right-0 top-11 z-40 overflow-hidden rounded-md border border-border bg-popover shadow-lg">
            {matches.map(({ dog, owner }) => (
              <li
                key={dog.id}
                className="flex items-center justify-between gap-2 border-b border-border px-3 py-2 text-sm last:border-b-0"
              >
                <div>
                  <p className="font-medium">{dog.nombre}</p>
                  <p className="text-xs text-muted-foreground">
                    {ownerName(owner)} · DNI {owner?.dni} · {owner?.telefono}
                  </p>
                </div>
                {canVerFichas && (
                  <button
                    onClick={() => {
                      onVerFichas(dog.id)
                      setQuery("")
                    }}
                    className="flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs transition-colors hover:bg-muted"
                  >
                    <FileText className="h-3 w-3" />
                    Fichas
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="ml-auto flex items-center gap-2">
        <label htmlFor="selector-rol" className="hidden text-xs text-muted-foreground sm:inline">
          Usuario
        </label>
        <select
          id="selector-rol"
          value={role}
          onChange={(e) => setRole(e.target.value as Role)}
          className="min-h-[44px] rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-foreground focus:ring-1 focus:ring-ring"
        >
          {(Object.keys(roleLabels) as Role[]).map((r) => (
            <option key={r} value={r}>
              {roleLabels[r]}
            </option>
          ))}
        </select>
      </div>
    </header>
  )
}
