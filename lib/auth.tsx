"use client"

/**
 * Sesion del empleado para los componentes.
 *
 * El rol que gobierna la navegacion sale del usuario logueado, no de un selector:
 * antes cualquiera elegia "Dueño" en la barra superior. Aun asi, esconder
 * pantallas es solo comodidad; los permisos reales los aplica el backend (403).
 */

import { useCallback, useSyncExternalStore } from "react"

import { auth as apiAuth } from "@/lib/api"
import {
  borrarSesion,
  guardarSesion,
  obtenerSesion,
  suscribirSesion,
  type RolApi,
  type Sesion,
} from "@/lib/sesion"
import type { Role } from "@/lib/store"

const ROL_UI: Record<RolApi, Role> = {
  DUENIO: "duenio",
  VETERINARIO: "veterinario",
  RECEPCION: "recepcion",
  GUARDERIA: "guarderia",
}

export function rolDeUi(rol: RolApi): Role {
  return ROL_UI[rol]
}

/** Durante el render del servidor no hay storage: se arranca "sin resolver". */
const SIN_RESOLVER = undefined

export function useAuth() {
  const sesion = useSyncExternalStore<Sesion | null | undefined>(
    suscribirSesion,
    obtenerSesion,
    () => SIN_RESOLVER,
  )

  const login = useCallback(async (username: string, password: string) => {
    const nueva = await apiAuth.login(username, password)
    guardarSesion(nueva)
    return nueva
  }, [])

  const cambiarPassword = useCallback(async (actual: string, nueva: string) => {
    const renovada = await apiAuth.cambiarPassword(actual, nueva)
    guardarSesion(renovada)
    return renovada
  }, [])

  const logout = useCallback(() => {
    borrarSesion()
  }, [])

  return {
    /** `false` mientras no se pudo leer el storage (primer render en el servidor). */
    listo: sesion !== SIN_RESOLVER,
    sesion: sesion ?? null,
    usuario: sesion?.usuario ?? null,
    role: sesion ? rolDeUi(sesion.usuario.rol) : null,
    login,
    cambiarPassword,
    logout,
  }
}
