"use client"

/**
 * Navegación del MVP 1.
 *
 * Mobile-first (AGENTS.md 3.2): en `< md` la barra lateral se oculta y la
 * navegación vive en una barra inferior con la fila de módulos más usada; en
 * `>= md` aparece la lateral fija. Se elige barra inferior y no hamburguesa
 * porque el mostrador usa el pulgar: abrir un menú lateral con una sola mano
 * mientras se sostiene un perro no es realista.
 */

import { useState } from "react"
import {
  CalendarDays,
  ClipboardList,
  Dog,
  FileText,
  LayoutDashboard,
  LogOut,
  Menu,
  Stethoscope,
  Users,
  Wallet,
  X,
  type LucideIcon,
} from "lucide-react"

import { cn } from "@/lib/utils"
import { type Role } from "@/lib/store"

export type TabId =
  | "mostrador"
  | "agenda"
  | "padron"
  | "consultas"
  | "cobros"
  | "balance"
  | "egreso"
  | "fichas"

type TabDef = {
  id: TabId
  label: string
  icon: LucideIcon
  roles: Role[]
}

export const TABS: TabDef[] = [
  { id: "mostrador", label: "Mostrador", icon: LayoutDashboard, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "agenda", label: "Agenda", icon: CalendarDays, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "padron", label: "Padrón", icon: Users, roles: ["recepcion", "duenio"] },
  { id: "consultas", label: "Ficha médica", icon: Stethoscope, roles: ["veterinario", "recepcion"] },
  { id: "cobros", label: "Caja", icon: Wallet, roles: ["recepcion", "duenio"] },
  { id: "egreso", label: "Egreso", icon: LogOut, roles: ["recepcion", "veterinario", "duenio"] },
  { id: "balance", label: "Balance", icon: ClipboardList, roles: ["duenio"] },
  { id: "fichas", label: "Historial", icon: FileText, roles: ["veterinario", "duenio"] },
]

/** Módulos visibles en la barra inferior del móvil. */
const ABAJO = new Set<TabId>(["mostrador", "egreso", "cobros", "agenda"])

export function Sidebar({
  active,
  onChange,
  role,
}: {
  active: TabId
  onChange: (id: TabId) => void
  role: Role
}) {
  const [abierto, setAbierto] = useState(false)
  const visible = TABS.filter((t) => t.roles.includes(role))

  function elegir(id: TabId) {
    onChange(id)
    setAbierto(false)
  }

  return (
    <>
      {/* Lateral de escritorio */}
      <aside className="hidden w-56 shrink-0 flex-col border-r border-border bg-sidebar md:flex">
        <Marca />
        <nav className="flex flex-1 flex-col gap-1 p-2">
          {visible.map((t) => (
            <BotonNav key={t.id} tab={t} activo={t.id === active} onClick={() => elegir(t.id)} />
          ))}
        </nav>
        <PieDeMarca />
      </aside>

      {/* Barra inferior de móvil */}
      <nav
        className="fixed inset-x-0 bottom-0 z-40 flex border-t border-border bg-background md:hidden"
        aria-label="Navegación principal"
      >
        {visible
          .filter((t) => ABAJO.has(t.id))
          .map((t) => {
            const Icon = t.icon
            const activo = t.id === active
            return (
              <button
                key={t.id}
                onClick={() => elegir(t.id)}
                aria-current={activo ? "page" : undefined}
                className={cn(
                  "flex min-h-[56px] flex-1 flex-col items-center justify-center gap-1 text-[11px] transition-colors",
                  activo ? "text-foreground" : "text-muted-foreground",
                )}
              >
                <Icon className="size-5" aria-hidden />
                <span>{t.label}</span>
              </button>
            )
          })}
        <button
          onClick={() => setAbierto(true)}
          aria-label="Ver todos los módulos"
          aria-expanded={abierto}
          className="flex min-h-[56px] flex-1 flex-col items-center justify-center gap-1 text-[11px] text-muted-foreground"
        >
          <Menu className="size-5" aria-hidden />
          <span>Más</span>
        </button>
      </nav>

      {/* Cajón de módulos en móvil */}
      {abierto && (
        <div
          className="fixed inset-0 z-50 flex items-end bg-black/40 md:hidden"
          onClick={() => setAbierto(false)}
        >
          <div
            className="w-full rounded-t-xl border-t border-border bg-background p-4"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-label="Módulos"
          >
            <div className="mb-3 flex items-center justify-between">
              <p className="text-sm font-semibold">Módulos</p>
              <button
                onClick={() => setAbierto(false)}
                aria-label="Cerrar"
                className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md text-muted-foreground"
              >
                <X className="size-5" aria-hidden />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {visible.map((t) => (
                <BotonNav
                  key={t.id}
                  tab={t}
                  activo={t.id === active}
                  onClick={() => elegir(t.id)}
                />
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function BotonNav({
  tab,
  activo,
  onClick,
}: {
  tab: TabDef
  activo: boolean
  onClick: () => void
}) {
  const Icon = tab.icon
  return (
    <button
      onClick={onClick}
      aria-current={activo ? "page" : undefined}
      className={cn(
        "flex min-h-[44px] items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
        activo
          ? "bg-foreground font-medium text-background"
          : "text-foreground/80 hover:bg-muted",
      )}
    >
      <Icon className="size-4 shrink-0" aria-hidden />
      <span>{tab.label}</span>
    </button>
  )
}

function Marca() {
  return (
    <div className="flex items-center gap-2 border-b border-border px-4 py-4">
      <div className="flex size-8 items-center justify-center rounded-md border border-foreground/20">
        <Dog className="size-4" />
      </div>
      <div className="leading-tight">
        <p className="text-sm font-semibold">Veterinaria</p>
        <p className="text-xs text-muted-foreground">Guardería canina</p>
      </div>
    </div>
  )
}

function PieDeMarca() {
  return (
    <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">
      MVP 1 · Sistema interno
    </div>
  )
}