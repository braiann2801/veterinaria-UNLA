"use client"

/**
 * Estado de sesión global (rol + búsqueda del TopBar).
 *
 * <p>Este store nació como un mock en memoria con perros, tutores, turnos,
 * reservas y pagos de juguete. Ese mundo de datos ya no existe: todo el dato
 * operativo se lee y escribe contra la API Spring Boot (`lib/api.ts`), y acá
 * solo queda el estado de UI compartido entre pantallas.</p>
 */

import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react"

export type Role = "recepcion" | "veterinario" | "duenio"

export const roleLabels: Record<Role, string> = {
  recepcion: "Recepción",
  veterinario: "Veterinario",
  duenio: "Dueño",
}

type Store = {
  role: Role
  setRole: (role: Role) => void
  query: string
  setQuery: (query: string) => void
}

const StoreContext = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<Role>("recepcion")
  const [query, setQuery] = useState("")

  const value = useMemo<Store>(
    () => ({ role, setRole, query, setQuery }),
    [role, query],
  )

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error("useStore debe usarse dentro de StoreProvider")
  return ctx
}