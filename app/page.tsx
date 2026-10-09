"use client"

/**
 * Shell del MVP 1.
 *
 * Todas las pantallas leen la base real. El tablero de movimiento del Mostrador
 * es la ultima excepcion: sigue mostrando turnos, reservas y deudas semilla del
 * store mientras se termina su migracion a la API.
 *
 * Los dos modales del Mostrador (Cargar cliente y Agregar turno) sí persisten:
 * van contra la API. El Historial y el Balance del día ya no son pantallas de
 * demo: el Balance lee el cierre real en `GET /api/v1/caja/cierre`.
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
import { Profesionales } from "@/components/modules/profesionales"

function Shell() {
  const { role } = useStore()
  const [active, setActive] = useState<TabId>("mostrador")
  const [fichaMascotaId, setFichaMascotaId] = useState<number | null>(null)

  // El Balance (recaudacion del dia) es exclusivo del Dueno. El sidebar ya lo
  // oculta para los otros roles, pero se repite la guarda acá porque este modulo
  // no debe depender de que la navegacion se mantenga sincronizada.
  const esDuenio = role === "duenio"

  // Si el rol actual no puede ver la pestaña activa, volver a Mostrador.
  useEffect(() => {
    const tab = TABS.find((t) => t.id === active)
    if (tab && !tab.roles.includes(role)) setActive("mostrador")
  }, [role, active])

  const verFichas = (mascotaId: number) => {
    setFichaMascotaId(mascotaId)
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
              <div className="flex flex-col gap-4">
                <Mostrador />
                {/* El padron de profesionales va en el mostrador porque es la
                    pantalla donde se da de alta a quien atiende, no un modulo
                    de consulta. */}
                <Profesionales />
              </div>
            )}
            {active === "agenda" && <Agenda />}
            {active === "padron" && <Padron />}
            {active === "consultas" && <FichaMedica />}
            {active === "cobros" && <Caja />}
            {active === "egreso" && <Egreso />}
            {active === "fichas" && (
              <Fichas key={fichaMascotaId} initialMascotaId={fichaMascotaId} />
            )}
            {/* El Balance va condicional por rol y no solo por pestana: es la
                unica metrica de recaudacion de la app y tiene que quedarle fuera
                a Recepcion y Veterinario aunque lleguen con el rol cambiado en
                la URL. El resumen de recaudacion vive dentro de este modulo, no
                en Caja: un veterinario que cobra no esta mirando el balance del
                dia, solo las comandas que tiene delante. */}
            {active === "balance" && esDuenio && <Balance />}
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