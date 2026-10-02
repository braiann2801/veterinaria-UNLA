"use client"

/**
 * Hooks de datos contra la API.
 *
 * Cada hook expone el triplete `{ datos, cargando, error, recargar }`. La UI
 * nunca maneja el estado de una request a mano: si cada pantalla lo hiciera,
 * la mitad olvidaria el `cargando` y el usuario veria una tabla vacia sin
 * ningun indicio de que la consulta sigue en vuelo.
 *
 * `refrescar()` es explicito en vez de un auto-refresh periodico: el mostrador
 * necesita que el saldo que ve sea el del instante en que lo miro.
 */

import { useCallback, useEffect, useRef, useState } from "react"

import { ApiError, ApiConnectionError } from "@/lib/api"

export type EstadoApi<T> = {
  datos: T | null
  cargando: boolean
  error: ApiError | null
  recargar: () => Promise<void>
  /** Para mutaciones: deja el estado en carga y vuelve a pedir los datos. */
  mutando: boolean
  ejecutar: (accion: () => Promise<T>) => Promise<T | null>
}

/**
 * Carga un recurso y lo mantiene fresco bajo demanda.
 *
 * @param fn     la llamada a la API. Debe ser estable (memoizada o un metodo de
 *               objeto de `lib/api.ts`) o el efecto se dispara en cada render.
 * @param activo si es false no se llama a la API. Sirve para no pedir datos
 *               hasta que el usuario haya elegido algo (por ejemplo, una
 *               mascota en el modulo de egreso).
 * @param clave  valor que, al cambiar, fuerza una recarga aunque `fn` y `activo
 *               sigan igual. Es lo que permite que un hook dependa de un
 *               parametro sin que el consumidor tenga que armar la fn a mano:
 *               `useApi(() => turnos.listar(fecha), true, fecha)`.
 *
 *               Sin esto, pasar la fecha como closure no recarga nada. El
 *               `fnRef` de mas abajo existe justamente para que `fn` no sea
 *               dependencia del efecto, y el precio de esa decision es que un
 *               parametro capturado en la fn deja de disparar la carga. `clave`
 *               es el escape explicito: el consumidor declara que hay un input
 *               del que el resultado depende.
 */
export function useApi<T>(fn: () => Promise<T>, activo = true, clave?: string | number): EstadoApi<T> {
  const [datos, setDatos] = useState<T | null>(null)
  const [cargando, setCargando] = useState(activo)
  const [error, setError] = useState<ApiError | null>(null)
  const [mutando, setMutando] = useState(false)

  // Guarda la fn sin ponerla en las dependencias del efecto: si el consumidor
  // pasa una arrow function inline, sin este ref el useEffect entraria en
  // loop infinito de requests.
  const fnRef = useRef(fn)
  fnRef.current = fn

  const montado = useRef(true)
  useEffect(() => {
    montado.current = true
    return () => {
      montado.current = false
    }
  }, [])

  /**
   * Descarta los datos anteriores cuando cambia `clave`.
   *
   * <p>Sin esto, cambiar la fecha dejaria los turnos del dia viejo en pantalla
   * mientras se pide el nuevo: la lista se veria exacta y estaria mal, que es
   * peor que un spinner. `Recurso` ya dibuja el estado de carga cuando
   * {@code datos} es null, asi que basta con limpiar.</p>
   */
  useEffect(() => {
    if (activo) {
      setDatos(null)
      setError(null)
    }
  }, [activo, clave])

  // `clave` entra en las dependencias para que cambiar un parametro de la
  // consulta recargue. Sin esta dependencia el efecto solo corre al montar.
  const recargar = useCallback(async () => {
    if (!activo) return
    setCargando(true)
    try {
      const resultado = await fnRef.current()
      // Sin este guarda, un filtro rapido deja la respuesta vieja ganar la
      // carrera y la lista muestra resultados que ya no corresponden.
      if (montado.current) setDatos(resultado)
      if (montado.current) setError(null)
    } catch (e) {
      if (montado.current) setError(normalizar(e))
    } finally {
      if (montado.current) setCargando(false)
    }
  }, [activo, clave])

  useEffect(() => {
    if (activo) void recargar()
  }, [activo, recargar])

  const ejecutar = useCallback(
    async (accion: () => Promise<T>) => {
      setMutando(true)
      try {
        const resultado = await accion()
        if (montado.current) setError(null)
        await recargar()
        return resultado
      } catch (e) {
        if (montado.current) setError(normalizar(e))
        return null
      } finally {
        if (montado.current) setMutando(false)
      }
    },
    [recargar],
  )

  return { datos, cargando, error, recargar, mutando, ejecutar }
}

function normalizar(e: unknown): ApiError {
  if (e instanceof ApiError) return e
  return new ApiError(0, e instanceof Error ? e.message : "Error desconocido", "/")
}

/**
 * Mensaje listo para mostrar al usuario.
 *
 * `ApiError` ya trae el texto del backend, que es el que conoce el dominio.
 * Para un error de red se arma un mensaje propio, porque el backend no puede
 * decir nada: nunca llego a responder.
 */
export function mensajeDeError(e: ApiError | null) {
  if (!e) return null
  if (e instanceof ApiConnectionError) return e.message
  return [e.message, ...e.detalles].filter(Boolean).join(" ")
}

/** El boton que dispara una mutacion debe explicar el error que estrago. */
export function debeReintentar(e: ApiError | null) {
  return e !== null && !e.esValidacion && !e.esNoEncontrado && !e.esConflicto
}