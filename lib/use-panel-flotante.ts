"use client"

/**
 * Comportamiento de un panel flotante anclado a un boton.
 *
 * <p>Lo comparten los selectores de fecha del sistema: el de un dia
 * (`DatePicker`) y el de un periodo (`RangoFechasPicker`). El panel se dibuja en
 * un portal sobre `body`, se ubica junto al disparador en escritorio (arriba si
 * abajo no entra) y es una hoja inferior en el celular (regla 3.4 de
 * AGENTS.md).</p>
 *
 * <p>Estaba dentro de `DatePicker`. Al aparecer el selector de periodo, copiar
 * el portal, el `ResizeObserver`, el cierre por click afuera y el Escape habria
 * dejado dos implementaciones que se desincronizan: una arregla un borde y la
 * otra sigue abriendose fuera de pantalla. Vive aca una sola vez.</p>
 *
 * <p>Es un hook de UI puro: no sabe nada de fechas, solo de abrir, cerrar y
 * ubicar. Quien lo usa aporta el contenido y el estado del valor.</p>
 */

import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type RefObject,
} from "react"

/** Separacion entre el panel y el boton, y entre el panel y el borde de la pantalla. */
export const MARGEN_PANEL = 8
/** Debajo de `sm` (640 px) el panel es una hoja inferior (regla 3.4 de AGENTS.md). */
const CONSULTA_MOVIL = "(max-width: 639px)"

/**
 * Donde va el panel en escritorio: debajo del boton si entra, si no arriba, y
 * si no entra en ningun lado, pegado al borde inferior. Nunca sale de la
 * pantalla por los costados.
 */
function ubicarPanel(disparador: DOMRect, altoPanel: number, anchoPanel: number): CSSProperties {
  const ancho = Math.min(anchoPanel, window.innerWidth - MARGEN_PANEL * 2)
  const libreAbajo = window.innerHeight - disparador.bottom - MARGEN_PANEL * 2
  const libreArriba = disparador.top - MARGEN_PANEL * 2
  const top =
    altoPanel <= libreAbajo
      ? disparador.bottom + MARGEN_PANEL
      : altoPanel <= libreArriba
        ? disparador.top - MARGEN_PANEL - altoPanel
        : Math.max(MARGEN_PANEL, window.innerHeight - altoPanel - MARGEN_PANEL)
  const left = Math.min(
    Math.max(disparador.left, MARGEN_PANEL),
    window.innerWidth - ancho - MARGEN_PANEL,
  )
  return { top, left, width: ancho }
}

export interface EstadoPanelFlotante {
  abierto: boolean
  esMovil: boolean
  /** `null` mientras se mide el panel: se monta invisible para no saltar. */
  posicion: CSSProperties | null
  /** Envuelve al disparador: un click ahi no cuenta como "afuera". */
  contenedorRef: RefObject<HTMLDivElement | null>
  disparadorRef: RefObject<HTMLButtonElement | null>
  panelRef: RefObject<HTMLDivElement | null>
  /** Fondo oscuro de la hoja inferior; su click cierra con su propio handler. */
  fondoRef: RefObject<HTMLDivElement | null>
  alternar: () => void
  cerrar: () => void
}

/**
 * @param anchoPanel ancho del panel en escritorio, en px
 * @param alAbrir    se ejecuta cada vez que el panel se abre, antes de mostrarlo:
 *                   sirve para reiniciar el foco del calendario o el borrador
 */
export function usePanelFlotante(anchoPanel: number, alAbrir?: () => void): EstadoPanelFlotante {
  const [abierto, setAbierto] = useState(false)
  const [esMovil, setEsMovil] = useState(false)
  const [posicion, setPosicion] = useState<CSSProperties | null>(null)
  const contenedorRef = useRef<HTMLDivElement>(null)
  const disparadorRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const fondoRef = useRef<HTMLDivElement>(null)

  const alAbrirRef = useRef(alAbrir)
  alAbrirRef.current = alAbrir

  function cerrar() {
    setAbierto(false)
  }

  function alternar() {
    if (abierto) {
      setAbierto(false)
      return
    }
    setEsMovil(window.matchMedia(CONSULTA_MOVIL).matches)
    setPosicion(null)
    alAbrirRef.current?.()
    setAbierto(true)
  }

  // Ubicacion en escritorio. Se recalcula al scrollear (cualquier contenedor,
  // por eso en captura), al cambiar el tamaño de la ventana y cuando el panel
  // cambia de alto (un mes de seis semanas o el selector de año).
  useLayoutEffect(() => {
    if (!abierto || esMovil) return

    function ubicar() {
      const disparador = disparadorRef.current
      const panel = panelRef.current
      if (!disparador || !panel) return
      setPosicion(ubicarPanel(disparador.getBoundingClientRect(), panel.offsetHeight, anchoPanel))
    }

    ubicar()
    const observador = new ResizeObserver(ubicar)
    if (panelRef.current) observador.observe(panelRef.current)
    window.addEventListener("resize", ubicar)
    window.addEventListener("scroll", ubicar, true)
    return () => {
      observador.disconnect()
      window.removeEventListener("resize", ubicar)
      window.removeEventListener("scroll", ubicar, true)
    }
  }, [abierto, esMovil, anchoPanel])

  // Cierre por click afuera y por Escape. Escape devuelve el foco al boton para
  // que la navegacion por teclado no se pierda al cerrar. El keydown se escucha
  // en captura y se marca con preventDefault: dentro de un `Modal`, ese Escape
  // cierra solo el calendario y no el dialogo entero.
  useEffect(() => {
    if (!abierto) return

    function alPresionarFuera(evento: PointerEvent) {
      const destino = evento.target as Node
      // El fondo de la hoja inferior cierra con su propio click: si cerrara en
      // el pointerdown, el click terminaria en lo que quedo debajo.
      if (fondoRef.current?.contains(destino)) return
      if (!contenedorRef.current?.contains(destino) && !panelRef.current?.contains(destino)) {
        setAbierto(false)
      }
    }
    function alPresionarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") {
        evento.preventDefault()
        setAbierto(false)
        disparadorRef.current?.focus()
      }
    }

    document.addEventListener("pointerdown", alPresionarFuera)
    document.addEventListener("keydown", alPresionarTecla, true)
    return () => {
      document.removeEventListener("pointerdown", alPresionarFuera)
      document.removeEventListener("keydown", alPresionarTecla, true)
    }
  }, [abierto])

  return {
    abierto,
    esMovil,
    posicion,
    contenedorRef,
    disparadorRef,
    panelRef,
    fondoRef,
    alternar,
    cerrar,
  }
}
