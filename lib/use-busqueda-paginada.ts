"use client"

/**
 * Búsqueda paginada reutilizable (Padrón).
 *
 * <p>Mismo contrato que `useMedicamentos`: el filtro espera 350 ms y aborta la
 * consulta anterior, así escribir rápido no deja en pantalla una respuesta
 * vieja. Acá además vive el flag `incluirInactivos` del toggle del Padrón;
 * cambiarlo vuelve a la primera página.</p>
 *
 * <p>La función `buscar` debe ser estable (definida a nivel de módulo) para no
 * disparar recargas en cada render.</p>
 */

import { useCallback, useEffect, useRef, useState } from "react"

import { ApiError, type Pagina } from "@/lib/api"

const ESPERA_MS = 350

export interface ArgumentosBusqueda {
  q: string
  page: number
  size: number
  incluirInactivos: boolean
  signal: AbortSignal
}

export type BuscarPaginado<T> = (args: ArgumentosBusqueda) => Promise<Pagina<T>>

export interface EstadoBusquedaPaginada<T> {
  datos: Pagina<T> | null
  cargando: boolean
  error: ApiError | null
  /** Fuerza una recarga explícita (tras editar, desactivar o reactivar). */
  recargar: () => Promise<void>
  q: string
  cambiarQ: (valor: string) => void
  pagina: number
  cambiarPagina: (pagina: number) => void
  tamanio: number
  cambiarTamanio: (tamanio: number) => void
}

function esAbort(error: unknown): boolean {
  return (
    (typeof DOMException !== "undefined" && error instanceof DOMException && error.name === "AbortError") ||
    (typeof error === "object" && error !== null && "name" in error && error.name === "AbortError")
  )
}

export function useBusquedaPaginada<T>(
  buscar: BuscarPaginado<T>,
  incluirInactivos: boolean,
  mensajeError: string,
): EstadoBusquedaPaginada<T> {
  const [q, setQ] = useState("")
  const [qAplicada, setQAplicada] = useState("")
  const [pagina, setPagina] = useState(0)
  const [tamanio, setTamanio] = useState(50)
  const [datos, setDatos] = useState<Pagina<T> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [version, setVersion] = useState(0)

  const montado = useRef(true)
  const controlador = useRef<AbortController | null>(null)

  useEffect(() => {
    montado.current = true
    return () => {
      montado.current = false
      controlador.current?.abort()
    }
  }, [])

  useEffect(() => {
    const temporizador = window.setTimeout(() => {
      setPagina(0)
      setQAplicada(q.trim())
    }, ESPERA_MS)
    return () => window.clearTimeout(temporizador)
  }, [q])

  useEffect(() => {
    // El toggle cambia el conjunto de filas: la página anterior ya no aplica.
    setPagina(0)
    setDatos(null)
  }, [incluirInactivos])

  useEffect(() => {
    setDatos(null)
  }, [qAplicada, pagina, tamanio])

  const recargarDatos = useCallback(async () => {
    controlador.current?.abort()
    const actual = new AbortController()
    controlador.current = actual
    setCargando(true)
    try {
      const respuesta = await buscar({
        q: qAplicada,
        page: pagina,
        size: tamanio,
        incluirInactivos,
        signal: actual.signal,
      })
      if (!montado.current || actual.signal.aborted) return
      setDatos(respuesta)
      setError(null)
    } catch (e) {
      if (!montado.current || actual.signal.aborted || esAbort(e)) return
      setError(e instanceof ApiError ? e : new ApiError(0, mensajeError, "/padron"))
    } finally {
      if (montado.current && !actual.signal.aborted) setCargando(false)
    }
  }, [buscar, qAplicada, pagina, tamanio, incluirInactivos, mensajeError, version])

  useEffect(() => {
    void recargarDatos()
  }, [recargarDatos])

  const cambiarTamanio = useCallback((nuevo: number) => {
    setTamanio(nuevo)
    setPagina(0)
  }, [])

  const recargar = useCallback(async () => {
    setVersion((v) => v + 1)
  }, [])

  return {
    datos,
    cargando,
    error,
    recargar,
    q,
    cambiarQ: setQ,
    pagina,
    cambiarPagina: setPagina,
    tamanio,
    cambiarTamanio,
  }
}
