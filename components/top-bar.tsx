"use client"

/**
 * Barra superior: busqueda global y usuario logueado.
 *
 * <p>Antes tenia un selector "Usuario" para elegir el rol, que dejaba a
 * cualquiera ver el Balance. Ahora muestra quien esta logueado y permite cerrar
 * la sesion; el rol sale del login (`lib/auth.tsx`).</p>
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

import { useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { FileText, KeyRound, Loader2, LogOut, Search } from "lucide-react"

import { ModalCambiarPassword } from "@/components/cambiar-password"

import { tutores as apiTutores } from "@/lib/api"
import type { MascotaVinculada, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { useAuth } from "@/lib/auth"
import { useStore, roleLabels } from "@/lib/store"
import { formatearDni, limpiarDni } from "@/lib/vista"

export function TopBar({
  onVerFichas,
}: {
  onVerFichas: (mascotaId: number) => void
}) {
  const { query, setQuery } = useStore()
  const { usuario, role, logout } = useAuth()
  const router = useRouter()
  const [cambiandoClave, setCambiandoClave] = useState(false)

  const salir = () => {
    logout()
    router.replace("/login")
  }

  // El texto sigue siendo del store para no duplicar el filtro del Historial.
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())

  const resultados = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    const salida: { mascota: MascotaVinculada; tutor: Tutor }[] = []
    for (const t of tutores.datos ?? []) {
      const nombreTutor = `${t.nombre} ${t.apellido}`.toLowerCase()
      // El DNI se guarda sin puntos: "30.123" tiene que encontrar "30123456".
      const qDni = limpiarDni(q)
      const coincideTutor =
        (qDni !== "" && t.dni.includes(qDni)) ||
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
    <header className="flex items-center gap-3 border-b border-border bg-background px-4 py-3">
      {/* El buscador arranca en el margen izquierdo y ocupa todo el ancho libre. */}
      <div className="relative min-w-0 flex-1">
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
                    {tutor.nombre} {tutor.apellido} · DNI {formatearDni(tutor.dni)}
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

      <div className="flex shrink-0 items-center gap-2">
        {usuario && role && (
          <div className="hidden min-w-0 flex-col items-end leading-tight sm:flex">
            <span className="max-w-[12rem] truncate text-sm font-medium">{usuario.nombreCompleto}</span>
            <span className="text-xs text-muted-foreground">{roleLabels[role]}</span>
          </div>
        )}
        <button
          type="button"
          onClick={() => setCambiandoClave(true)}
          aria-label="Cambiar contraseña"
          title="Cambiar contraseña"
          className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-xl border border-input bg-background px-3 text-sm transition-colors hover:bg-muted"
        >
          <KeyRound className="h-4 w-4" aria-hidden />
        </button>
        <ModalCambiarPassword open={cambiandoClave} onClose={() => setCambiandoClave(false)} />
        <button
          type="button"
          onClick={salir}
          aria-label="Cerrar sesión"
          title="Cerrar sesión"
          className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-2 rounded-xl border border-input bg-background px-3 text-sm transition-colors hover:bg-muted"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          <span className="hidden md:inline">Salir</span>
        </button>
      </div>
    </header>
  )
}