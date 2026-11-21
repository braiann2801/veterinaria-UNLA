"use client"

/**
 * Estado del catálogo de medicamentos con búsqueda debounced.
 *
 * <p>Dos decisiones que importan:</p>
 * <ul>
 *   <li>El temporizador de 350 ms se reinicia en cada tecla, así que escribir
 *       "amoxi" dispara UNA consulta y no cinco.</li>
 *   <li>Cada consulta nueva aborta la anterior. Sin el {@code AbortController},
 *       una respuesta lenta de "a" podía llegar después de la de "amoxi" y
 *       dejar en pantalla una lista que ya no corresponde.</li>
 * </ul>
 */

import { useCallback, useEffect, useRef, useState } from "react"

import { ApiError, medicamentos as apiMedicamentos, type Medicamento, type Pagina } from "@/lib/api"

const ESPERA_MS = 350

export type EstadoMedicamentos = {
  datos: Pagina<Medicamento> | null
  cargando: boolean
  error: ApiError | null
  recargar: () => Promise<void>
  /** Texto escrito por el operador (se aplica recién después del debounce). */
  q: string
  cambiarQ: (valor: string) => void
  pagina: number
  cambiarPagina: (pagina: number) => void
  tamanio: number
  cambiarTamanio: (tamanio: number) => void
  /** Baja lógica: al activarlo aparecen también los medicamentos desactivados. */
  verInactivos: boolean
  cambiarVerInactivos: (valor: boolean) => void
  /** Total de desactivados, para la etiqueta del toggle. */
  cantidadInactivos: number
}

function esAbort(error: unknown): boolean {
  return (
    (typeof DOMException !== "undefined" && error instanceof DOMException && error.name === "AbortError") ||
    (typeof error === "object" && error !== null && "name" in error && error.name === "AbortError")
  )
}

export function useMedicamentos(): EstadoMedicamentos {
  const [q, setQ] = useState("")
  const [qAplicada, setQAplicada] = useState("")
  const [pagina, setPagina] = useState(0)
  const [tamanio, setTamanio] = useState(50)
  const [verInactivos, setVerInactivos] = useState(false)
  const [cantidadInactivos, setCantidadInactivos] = useState(0)
  const [datos, setDatos] = useState<Pagina<Medicamento> | null>(null)
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
      // Cambiar el filtro vuelve a la primera página en el mismo update: si se
      // hiciera en un efecto aparte, se pediría primero la página vieja.
      setPagina(0)
      setQAplicada(q.trim())
    }, ESPERA_MS)
    return () => window.clearTimeout(temporizador)
  }, [q])

  useEffect(() => {
    // Mientras cambia el filtro, la página o el toggle, la tabla anterior no
    // debe seguir pareciendo válida: Recurso muestra el estado de carga.
    setDatos(null)
  }, [qAplicada, pagina, tamanio, verInactivos])

  /** El contador se refresca con el mismo `version` que la lista tras una baja. */
  useEffect(() => {
    apiMedicamentos
      .contarInactivos()
      .then((respuesta) => {
        if (montado.current) setCantidadInactivos(respuesta.cantidad)
      })
      .catch(() => {
        // Si falla el conteo, el toggle sigue funcionando con el número previo:
        // un aviso optativo no debe dejar la lista completa sin mostrar.
      })
  }, [version])

  const recargar = useCallback(async () => {
    controlador.current?.abort()
    const actual = new AbortController()
    controlador.current = actual
    setCargando(true)
    try {
      const respuesta = await apiMedicamentos.listar({
        q: qAplicada,
        incluirInactivos: verInactivos,
        page: pagina,
        size: tamanio,
        signal: actual.signal,
      })
      if (!montado.current || actual.signal.aborted) return
      setDatos(respuesta)
      setError(null)
    } catch (e) {
      if (!montado.current || actual.signal.aborted || esAbort(e)) return
      setError(
        e instanceof ApiError
          ? e
          : new ApiError(0, "No se pudo cargar el listado de medicamentos.", "/medicamentos"),
      )
    } finally {
      if (montado.current && !actual.signal.aborted) setCargando(false)
    }
  }, [qAplicada, pagina, tamanio, verInactivos, version])

  useEffect(() => {
    void recargar()
  }, [recargar])

  const cambiarTamanio = useCallback((nuevo: number) => {
    setTamanio(nuevo)
    setPagina(0)
  }, [])

  /** Fuerza una recarga explícita (después de crear o de cargar stock). */
  const recargarDatos = useCallback(async () => {
    setVersion((v) => v + 1)
  }, [])

  return {
    datos,
    cargando,
    error,
    recargar: recargarDatos,
    q,
    cambiarQ: setQ,
    pagina,
    cambiarPagina: setPagina,
    tamanio,
    cambiarTamanio,
    verInactivos,
    cambiarVerInactivos: setVerInactivos,
    cantidadInactivos,
  }
}
