"use client"

import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react"

export type Role = "recepcion" | "veterinario" | "duenio"

export type Tutor = {
  nombre: string
  telefono: string
}

export type Owner = {
  id: string
  nombre: string
  apellido: string
  dni: string
  telefono: string
  tutores: Tutor[]
  deuda: number
}

export type Conducta = "normal" | "gruñe" | "muerde" | "miedoso"

export type Dog = {
  id: string
  nombre: string
  ownerId: string
  conducta: Conducta
}

export type Turno = {
  id: string
  dogId: string
  ownerId: string
  hora: string
  motivo: string
  fecha: string // YYYY-MM-DD
  estado: "pendiente" | "atendido"
}

export type Reserva = {
  id: string
  dogId: string
  ownerId: string
  autorizadoNombre: string
  autorizadoTelefono: string
  ingreso: string // YYYY-MM-DD
  egreso: string // YYYY-MM-DD
  sena: number
}

export type Ficha = {
  id: string
  dogId: string
  fecha: string
  motivo: string
  diagnostico: string
  tratamiento: string
  peso: string
  conducta: string
}

export type Pago = {
  id: string
  sourceId: string // id del turno o reserva cobrado
  ownerId: string
  dogId: string
  detalle: string
  total: number
  sena: number
  efectivo: number
  transferencia: number
  saldoPendiente: number
  retiraPor: string
  fecha: string
}

type Store = {
  role: Role
  setRole: (r: Role) => void
  query: string
  setQuery: (q: string) => void
  owners: Owner[]
  dogs: Dog[]
  turnos: Turno[]
  reservas: Reserva[]
  fichas: Ficha[]
  pagos: Pago[]
  enCaja: string[] // ids de turnos enviados a caja
  today: string
  cupoMax: number
  addOwnerWithDog: (data: {
    dogNombre: string
    dni: string
    telefono: string
    nombre: string
    apellido: string
    tutores: Tutor[]
  }) => void
  addTurno: (t: Omit<Turno, "id" | "estado">) => void
  marcarAtendido: (id: string) => void
  addReserva: (r: Omit<Reserva, "id">) => void
  addFicha: (f: Omit<Ficha, "id">) => void
  enviarACaja: (turnoId: string) => void
  addPago: (p: Omit<Pago, "id">) => void
  ocupadosPorDia: (fecha: string) => number
}

const StoreContext = createContext<Store | null>(null)

const TODAY = "2026-09-23"

export const PRECIO_CONSULTA = 8000
export const PRECIO_DIA_GUARDERIA = 4000

function uid(prefix: string) {
  return `${prefix}_${Math.random().toString(36).slice(2, 9)}`
}

const seedOwners: Owner[] = [
  { id: "o1", nombre: "María", apellido: "Gómez", dni: "30111222", telefono: "1145678901", tutores: [{ nombre: "Juan Gómez", telefono: "1145678902" }], deuda: 0 },
  { id: "o2", nombre: "Carlos", apellido: "Pérez", dni: "27888444", telefono: "1156781234", tutores: [], deuda: 3500 },
  { id: "o3", nombre: "Lucía", apellido: "Fernández", dni: "33444555", telefono: "1167894567", tutores: [{ nombre: "Ana Fernández", telefono: "1167894500" }], deuda: 0 },
  { id: "o4", nombre: "Diego", apellido: "Rossi", dni: "29555666", telefono: "1178945612", tutores: [], deuda: 0 },
]

const seedDogs: Dog[] = [
  { id: "d1", nombre: "Rocky", ownerId: "o1", conducta: "normal" },
  { id: "d2", nombre: "Luna", ownerId: "o2", conducta: "muerde" },
  { id: "d3", nombre: "Toby", ownerId: "o3", conducta: "gruñe" },
  { id: "d4", nombre: "Kira", ownerId: "o4", conducta: "miedoso" },
  { id: "d5", nombre: "Simón", ownerId: "o1", conducta: "normal" },
]

const seedTurnos: Turno[] = [
  { id: "t1", dogId: "d1", ownerId: "o1", hora: "09:00", motivo: "Vacuna anual", fecha: TODAY, estado: "pendiente" },
  { id: "t2", dogId: "d3", ownerId: "o3", hora: "10:00", motivo: "Control general", fecha: TODAY, estado: "pendiente" },
  { id: "t3", dogId: "d4", ownerId: "o4", hora: "11:00", motivo: "Otitis", fecha: TODAY, estado: "pendiente" },
]

const seedReservas: Reserva[] = [
  { id: "r1", dogId: "d2", ownerId: "o2", autorizadoNombre: "Sofía Pérez", autorizadoTelefono: "1156780000", ingreso: TODAY, egreso: "2026-09-25", sena: 5000 },
  { id: "r2", dogId: "d5", ownerId: "o1", autorizadoNombre: "", autorizadoTelefono: "", ingreso: TODAY, egreso: "2026-09-24", sena: 3000 },
]

const seedFichas: Ficha[] = [
  { id: "f1", dogId: "d1", fecha: "2026-08-10", motivo: "Vacuna", diagnostico: "Sano", tratamiento: "Vacuna séxtuple", peso: "24 kg", conducta: "Tranquilo" },
  { id: "f2", dogId: "d3", fecha: "2026-07-02", motivo: "Cojera", diagnostico: "Esguince leve", tratamiento: "Antiinflamatorio 5 días", peso: "12 kg", conducta: "Reactivo con extraños" },
]

const seedPagos: Pago[] = [
  { id: "p1", sourceId: "seed1", ownerId: "o3", dogId: "d3", detalle: "Consulta", total: 8000, sena: 0, efectivo: 8000, transferencia: 0, saldoPendiente: 0, retiraPor: "Lucía Fernández", fecha: TODAY },
]

export function StoreProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<Role>("recepcion")
  const [query, setQuery] = useState("")
  const [owners, setOwners] = useState<Owner[]>(seedOwners)
  const [dogs, setDogs] = useState<Dog[]>(seedDogs)
  const [turnos, setTurnos] = useState<Turno[]>(seedTurnos)
  const [reservas, setReservas] = useState<Reserva[]>(seedReservas)
  const [fichas, setFichas] = useState<Ficha[]>(seedFichas)
  const [pagos, setPagos] = useState<Pago[]>(seedPagos)
  const [enCaja, setEnCaja] = useState<string[]>([])

  const value = useMemo<Store>(() => {
    const ocupadosPorDia = (fecha: string) =>
      reservas.filter((r) => fecha >= r.ingreso && fecha <= r.egreso).length

    return {
      role,
      setRole,
      query,
      setQuery,
      owners,
      dogs,
      turnos,
      reservas,
      fichas,
      pagos,
      enCaja,
      today: TODAY,
      cupoMax: 10,
      ocupadosPorDia,
      addOwnerWithDog: (data) => {
        const ownerId = uid("o")
        const dogId = uid("d")
        setOwners((prev) => [
          ...prev,
          {
            id: ownerId,
            nombre: data.nombre,
            apellido: data.apellido,
            dni: data.dni,
            telefono: data.telefono,
            tutores: data.tutores,
            deuda: 0,
          },
        ])
        setDogs((prev) => [
          ...prev,
          { id: dogId, nombre: data.dogNombre, ownerId, conducta: "normal" },
        ])
      },
      addTurno: (t) =>
        setTurnos((prev) => [...prev, { ...t, id: uid("t"), estado: "pendiente" }]),
      marcarAtendido: (id) =>
        setTurnos((prev) =>
          prev.map((t) => (t.id === id ? { ...t, estado: "atendido" } : t)),
        ),
      addReserva: (r) => setReservas((prev) => [...prev, { ...r, id: uid("r") }]),
      addFicha: (f) => setFichas((prev) => [...prev, { ...f, id: uid("f") }]),
      enviarACaja: (turnoId) =>
        setEnCaja((prev) => (prev.includes(turnoId) ? prev : [...prev, turnoId])),
      addPago: (p) => setPagos((prev) => [...prev, { ...p, id: uid("p") }]),
    }
  }, [role, query, owners, dogs, turnos, reservas, fichas, pagos, enCaja])

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error("useStore debe usarse dentro de StoreProvider")
  return ctx
}

// Helpers compartidos
export function ownerName(o: Owner | undefined) {
  return o ? `${o.nombre} ${o.apellido}` : "—"
}

export function money(n: number) {
  return "$" + n.toLocaleString("es-AR")
}

// Días de guardería (mínimo 1) entre ingreso y egreso
export function daysBetween(ingreso: string, egreso: string) {
  const a = new Date(ingreso + "T00:00:00")
  const b = new Date(egreso + "T00:00:00")
  const diff = Math.round((b.getTime() - a.getTime()) / 86400000)
  return Math.max(1, diff)
}

export const roleLabels: Record<Role, string> = {
  recepcion: "Recepción",
  veterinario: "Veterinario",
  duenio: "Dueño",
}
