"use client"

import {
  LayoutDashboard,
  Dog,
  Stethoscope,
  Wallet,
  ClipboardList,
  FileText,
  type LucideIcon,
} from "lucide-react"
import { cn } from "@/lib/utils"
import { type Role } from "@/lib/store"

export type TabId =
  | "mostrador"
  | "guarderia"
  | "consultas"
  | "cobros"
  | "balance"
  | "fichas"

type TabDef = {
  id: TabId
  label: string
  icon: LucideIcon
  roles: Role[]
}

export const TABS: TabDef[] = [
  { id: "mostrador", label: "Mostrador", icon: LayoutDashboard, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "guarderia", label: "Guardería", icon: Dog, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "consultas", label: "Consultas", icon: Stethoscope, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "cobros", label: "Cobros", icon: Wallet, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "balance", label: "Balance del día", icon: ClipboardList, roles: ["duenio"] },
  { id: "fichas", label: "Fichas médicas", icon: FileText, roles: ["veterinario", "duenio"] },
]

export function Sidebar({
  active,
  onChange,
  role,
}: {
  active: TabId
  onChange: (id: TabId) => void
  role: Role
}) {
  const visible = TABS.filter((t) => t.roles.includes(role))
  return (
    <aside className="flex w-56 shrink-0 flex-col border-r border-border bg-sidebar">
      <div className="flex items-center gap-2 border-b border-border px-4 py-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-md border border-foreground/20">
          <Dog className="h-4 w-4" />
        </div>
        <div className="leading-tight">
          <p className="text-sm font-semibold">Veterinaria</p>
          <p className="text-xs text-muted-foreground">Guardería canina</p>
        </div>
      </div>
      <nav className="flex flex-1 flex-col gap-1 p-2">
        {visible.map((t) => {
          const Icon = t.icon
          const isActive = t.id === active
          return (
            <button
              key={t.id}
              onClick={() => onChange(t.id)}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                isActive
                  ? "bg-foreground text-background font-medium"
                  : "text-foreground/80 hover:bg-muted",
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              <span>{t.label}</span>
            </button>
          )
        })}
      </nav>
      <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">
        MVP 1 · Sistema interno
      </div>
    </aside>
  )
}
