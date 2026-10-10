"use client"

/**
 * Barra superior: busqueda global y selector de rol.
 *
 * <p>La busqueda leia del store en memoria y por eso solo encontraba los datos
 * semilla. Con el alta unificada del mostrador, un cliente recien cargado no
 * aparecia en el buscador: la pantalla aceptaba el alta y despues no se
 * encontraba, que es la misma trampa que se acaba de corregir en el modal. Ahora
 * consulta la API, asi que lo que se cargo se encuentra.</p>
 *
 * <p>Se busca sobre `GET /tutores`, que ya trae las mascotas de cada tutor en el
 * mismo payload. Es una sola request para los dos casos que interesan — buscar
 * por el animal o por el dueno — y el filtrado es de texto parcial sobre un
 * padron chico (un consultorio, no un padrón nacional), asi que no se dispara una
 * request por tecla.</p>
 */

import { useMemo } from "react"
import { FileText, Loader2, Search } from "lucide-react"

import { tutores as apiTutores } from "@/lib/api"
import type { MascotaVinculada, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { useStore, roleLabels, type Role } from "@/lib/store"

export function TopBar({
  onVerFichas,
}: {
  onVerFichas: (mascotaId: number) => void
}) {
  const { query, setQuery, role, setRole } = useStore()

  // El texto sigue siendo del store para no duplicar el filtro del Historial.
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())

  const resultados = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    const salida: { mascota: MascotaVinculada; tutor: Tutor }[] = []
    for (const t of tutores.datos ?? []) {
      const nombreTutor = `${t.nombre} ${t.apellido}`.toLowerCase()
      const coincideTutor =
        t.dni.toLowerCase().includes(q) ||
        (t.telefono ?? "").toLowerCase().includes(q) ||
        nombreTutor.includes(q)
      for (const m of t.mascotas) {
        if (coincideTutor || m.nombre.toLowerCase().includes(q)) {
          salida.push({ mascota: m, tutor: t })
          // Corta aca: seis resultados alcanzan para una busqueda auxiliar y
          // evita recorrer todo el padron en cada tecla.
          if (salida.length >= 6) return salida
        }
      }
    }
    return salida
  }, [query, tutores.datos])

  return (
    <header className="flex items-center gap-3 border-b border-border bg-background px-4 py-3 md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,56rem)_minmax(0,1fr)]">
      <div className="relative flex-1 max-w-2xl md:col-start-2 md:row-start-1 md:mx-auto md:max-w-none">
        <Search
          className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Buscar por DNI, teléfono o nombre"
          aria-label="Buscar por DNI, teléfono o nombre"
          className="min-h-[52px] w-full rounded-xl border border-input bg-background pl-12 pr-4 text-base outline-none transition-colors focus:border-ring focus:ring-1 focus:ring-ring"
        />
        {resultados.length > 0 && (
          <ul className="absolute left-0 right-0 top-[calc(100%+0.5rem)] z-40 overflow-hidden rounded-xl border border-border bg-popover shadow-lg">
            {resultados.map(({ mascota, tutor }) => (
              <li
                key={`${tutor.id}-${mascota.id}`}
                className="flex items-center justify-between gap-2 border-b border-border px-3 py-2 text-sm last:border-b-0"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium">{mascota.nombre}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {tutor.nombre} {tutor.apellido} · DNI {tutor.dni}
                    {tutor.telefono ? ` · ${tutor.telefono}` : ""}
                  </p>
                </div>
                {/*
                  El acceso rápido al historial se muestra siempre: los tres roles
                  operativos tienen el Historial (ver `TABS` en `sidebar.tsx`), así
                  que ocultarlo para alguno dejaría un resultado de búsqueda sin
                  salida para un rol que sí puede leer la ficha.
                */}
                <button
                  onClick={() => {
                    onVerFichas(mascota.id)
                    setQuery("")
                  }}
                  className="flex min-h-[44px] shrink-0 items-center gap-1 rounded-xl border border-border px-3 text-xs transition-colors hover:bg-muted"
                >
                  <FileText className="h-3 w-3" aria-hidden />
                  Fichas
                </button>
              </li>
            ))}
          </ul>
        )}
        {tutores.cargando && tutores.datos === null && (
          <span className="absolute right-4 top-1/2 -translate-y-1/2 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            <span className="sr-only">Buscando</span>
          </span>
        )}
      </div>

      <div className="ml-auto flex items-center gap-2 md:col-start-3 md:row-start-1 md:ml-0 md:justify-self-end">
        <label htmlFor="selector-rol" className="hidden text-xs text-muted-foreground sm:inline">
          Usuario
        </label>
        <select
          id="selector-rol"
          value={role}
          onChange={(e) => setRole(e.target.value as Role)}
          className="min-h-[44px] rounded-xl border border-input bg-background px-3 text-sm outline-none transition-colors focus:border-ring focus:ring-1 focus:ring-ring"
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