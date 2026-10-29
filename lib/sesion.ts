/**
 * Sesion del empleado logueado, persistida en `localStorage`.
 *
 * Es un modulo plano (sin React) porque lo leen dos mundos: `lib/api.ts`, que
 * adjunta el token a cada request y limpia la sesion ante un 401, y
 * `lib/auth.tsx`, que la expone a los componentes. Los cambios se notifican a
 * los suscriptores para que la UI reaccione a un logout disparado desde la API.
 *
 * Todo acceso a `localStorage` va en try/catch: en navegacion privada o con el
 * storage bloqueado puede tirar, y eso no debe romper la app (solo obliga a
 * volver a ingresar al recargar).
 */

export type RolApi = "DUENIO" | "VETERINARIO" | "RECEPCION" | "GUARDERIA"

/** Cuenta tal como la emite `UsuarioResponseDTO` (sin hash). */
export interface UsuarioSesion {
  id: number
  username: string
  nombreCompleto: string
  rol: RolApi
  activo: boolean
  debeCambiarPassword: boolean
}

/** Respuesta de `POST /auth/login` y `POST /auth/cambiar-password`. */
export interface Sesion {
  token: string
  expiraEn: string
  usuario: UsuarioSesion
}

const CLAVE = "veterinaria.sesion"

let actual: Sesion | null | undefined
const suscriptores = new Set<() => void>()

/** Un `expiraEn` ilegible cuenta como vencido: `NaN > ahora` es falso. */
function vigente(sesion: Sesion) {
  return Date.parse(sesion.expiraEn) > Date.now()
}

function leer(): Sesion | null {
  if (typeof window === "undefined") return null
  try {
    const crudo = window.localStorage.getItem(CLAVE)
    if (!crudo) return null
    const sesion = JSON.parse(crudo) as Sesion
    // Una sesion vencida se descarta aca: el backend igual la rechazaria con 401.
    if (!sesion?.token || !vigente(sesion)) return null
    return sesion
  } catch {
    return null
  }
}

export function obtenerSesion(): Sesion | null {
  if (actual === undefined) actual = leer()
  else if (actual && !vigente(actual)) actual = null
  return actual
}

// Un logout o un login en otra pestaña cambia el storage: se relee para no seguir
// mandando un token que ya no corresponde.
if (typeof window !== "undefined") {
  window.addEventListener("storage", (e) => {
    if (e.key !== null && e.key !== CLAVE) return
    actual = leer()
    suscriptores.forEach((fn) => fn())
  })
}

export function guardarSesion(sesion: Sesion) {
  actual = sesion
  try {
    window.localStorage.setItem(CLAVE, JSON.stringify(sesion))
  } catch {
    // Sin storage la sesion vive solo en memoria hasta recargar.
  }
  suscriptores.forEach((fn) => fn())
}

export function borrarSesion() {
  actual = null
  try {
    window.localStorage.removeItem(CLAVE)
  } catch {
    // Nada que limpiar si el storage no esta disponible.
  }
  suscriptores.forEach((fn) => fn())
}

export function suscribirSesion(fn: () => void) {
  suscriptores.add(fn)
  return () => {
    suscriptores.delete(fn)
  }
}
