"use client"

/**
 * Shell del MVP 1.
 *
 * Todo lo que se ve aca lee la base real salvo dos cosas, y las dos se avisan en
 * pantalla con `AvisoDemo` para que el operador no tome por real un dato que no
 * lo es:
 *
 * - el Balance del día, que sigue sumando pagos semilla del store (el cierre real
 *   está en `GET /api/v1/caja/cierre`), y que además es exclusivo del Dueño;
 * - el tablero de movimiento del Mostrador, que muestra los turnos, reservas y
 *   deudas semilla del store.
 *
 * Los dos modales del Mostrador (Cargar cliente y Agregar turno) sí persisten:
 * van contra la API. El Historial se migro de lectura en memoria a lectura de la
 * API en HOTFIX-UI, asi que ya no es una de las pantallas de demo.
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
import { Panel } from "@/components/ui/field"

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
                <AvisoDemo
                  titulo="Mostrador"
                  texto="«Cargar cliente» y «Agregar turno» guardan en la base: el tutor, la mascota y su vínculo, y el turno que después aparece en Agenda. El padrón de profesionales también lee la base. Lo que NO es real es el tablero de movimiento de abajo (turnos, ingresos, egresan y deudas): muestra datos de demostración."
                />
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
                la URL. La tarjeta "Total en caja" vive dentro de este modulo, no
                en Caja: un veterinario que cobra no esta mirando el balance del
                dia, solo las comandas que tiene delante. */}
            {active === "balance" && esDuenio && (
              <AvisoDemo titulo="Balance del día" texto="Todavía usa datos en memoria. El cierre de caja real se expone en /api/v1/caja/cierre." />
            )}
            {active === "balance" && esDuenio && <Balance />}
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