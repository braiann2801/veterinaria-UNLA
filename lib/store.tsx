"use client"

/**
 * Estado de UI compartido entre pantallas (búsqueda del TopBar).
 *
 * <p>Este store nació como un mock en memoria con perros, tutores, turnos,
 * reservas y pagos de juguete. Ese mundo de datos ya no existe: todo el dato
 * operativo se lee y escribe contra la API Spring Boot (`lib/api.ts`).</p>
 *
 * <p>El rol tampoco vive acá: antes se elegía con un selector y cualquiera podía
 * ponerse "Dueño". Ahora sale del usuario logueado (`lib/auth.tsx`).</p>
 */

import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react"

export type Role = "recepcion" | "veterinario" | "duenio" | "guarderia"

export const roleLabels: Record<Role, string> = {
  recepcion: "Recepción",
  veterinario: "Veterinario",
  duenio: "Dueño",
  guarderia: "Guardería",
}

type Store = {
  query: string
  setQuery: (query: string) => void
}

const StoreContext = createContext<Store | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [query, setQuery] = useState("")

  const value = useMemo<Store>(() => ({ query, setQuery }), [query])

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error("useStore debe usarse dentro de StoreProvider")
  return ctx
}
