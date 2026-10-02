"use client"

/**
 * Shell del MVP 1.
 *
 * Las vistas nuevas (Padrón, Agenda, Ficha médica, Caja, Egreso) consumen la
 * API real de `lib/api.ts`. Las que quedaron en modo demo durante TASK-001 a
 * TASK-004 (Balance y el Historial genérico) siguen montadas para no romper el
 * recorrido, pero se avisa en pantalla de que no leen la base.
 */

import { useEffect, useState } from "react"

import { StoreProvider, useStore } from "@/lib/store"
import { Sidebar, TABS, type TabId } from "@/components/sidebar"
import { TopBar } from "@/components/top-bar"
import { Agenda } from "@/components/modules/agenda"
import { Balance } from "@/components/modules/balance"
import { Caja } from "@/components/modules/caja"
import { Egreso } from "@/components/modules/egreso"
import { FichaMedica } from "@/components/modules/ficha"
import { Fichas } from "@/components/modules/fichas"
import { Mostrador } from "@/components/modules/mostrador"
import { Padron } from "@/components/modules/padron"
import { Panel } from "@/components/ui/field"

function Shell() {
  const { role } = useStore()
  const [active, setActive] = useState<TabId>("mostrador")
  const [fichaDogId, setFichaDogId] = useState<string | null>(null)

  // Si el rol actual no puede ver la pestaña activa, volver a Mostrador.
  useEffect(() => {
    const tab = TABS.find((t) => t.id === active)
    if (tab && !tab.roles.includes(role)) setActive("mostrador")
  }, [role, active])

  const verFichas = (dogId: string) => {
    setFichaDogId(dogId)
    setActive("fichas")
  }

  return (
    <div className="flex h-screen overflow-hidden bg-background text-foreground">
      <Sidebar active={active} onChange={setActive} role={role} />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar onVerFichas={verFichas} />
        {/* pb-20 deja aire para la barra inferior de navegación en móvil. */}
        <main className="flex-1 overflow-y-auto p-4 pb-24 md:pb-4 lg:p-6">
          <div className="mx-auto max-w-5xl">
            {active === "mostrador" && (
              <AvisoDemo titulo="Mostrador" texto="Pantalla de demostración de TASK-001. Los módulos conectados a la API real están en Agenda, Padrón, Ficha médica, Caja y Egreso." />
            )}
            {active === "mostrador" && <Mostrador />}
            {active === "agenda" && <Agenda />}
            {active === "padron" && <Padron />}
            {active === "consultas" && <FichaMedica />}
            {active === "cobros" && <Caja />}
            {active === "egreso" && <Egreso />}
            {active === "balance" && (
              <AvisoDemo titulo="Balance del día" texto="Todavía usa datos en memoria. El cierre de caja real se expone en /api/v1/caja/cierre." />
            )}
            {active === "balance" && <Balance />}
            {active === "fichas" && <Fichas key={fichaDogId} initialDogId={fichaDogId} />}
          </div>
        </main>
      </div>
    </div>
  )
}

function AvisoDemo({ titulo, texto }: { titulo: string; texto: string }) {
  return (
    <Panel title={titulo} className="mb-4">
      <p className="text-sm text-muted-foreground">{texto}</p>
    </Panel>
  )
}

export default function Page() {
  return (
    <StoreProvider>
      <Shell />
    </StoreProvider>
  )
}