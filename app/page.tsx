"use client"

import { useEffect, useState } from "react"
import { StoreProvider, useStore } from "@/lib/store"
import { Sidebar, TABS, type TabId } from "@/components/sidebar"
import { TopBar } from "@/components/top-bar"
import { Mostrador } from "@/components/modules/mostrador"
import { Guarderia } from "@/components/modules/guarderia"
import { Consultas } from "@/components/modules/consultas"
import { Cobros } from "@/components/modules/cobros"
import { Balance } from "@/components/modules/balance"
import { Fichas } from "@/components/modules/fichas"

function Shell() {
  const { role } = useStore()
  const [active, setActive] = useState<TabId>("mostrador")
  const [fichaDogId, setFichaDogId] = useState<string | null>(null)

  // Si el rol actual no puede ver la pestaña activa, volver a Mostrador
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
        <main className="flex-1 overflow-y-auto p-4 lg:p-6">
          <div className="mx-auto max-w-5xl">
            {active === "mostrador" && <Mostrador />}
            {active === "guarderia" && <Guarderia />}
            {active === "consultas" && <Consultas />}
            {active === "cobros" && <Cobros />}
            {active === "balance" && <Balance />}
            {active === "fichas" && <Fichas key={fichaDogId} initialDogId={fichaDogId} />}
          </div>
        </main>
      </div>
    </div>
  )
}

export default function Page() {
  return (
    <StoreProvider>
      <Shell />
    </StoreProvider>
  )
}
