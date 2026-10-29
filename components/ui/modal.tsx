"use client"

import { useEffect, useId, useRef, type ReactNode } from "react"
import { X } from "lucide-react"

const FOCUSABLES =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Dialogo modal compartido.
 *
 * <p>El scroll vive en el overlay y el centrado en un envoltorio con
 * `min-h-full`: si el contenido es mas alto que la pantalla, el envoltorio
 * crece en lugar de desbordar hacia arriba. Con `items-center` directo sobre
 * el contenedor que scrollea, el encabezado (titulo y Cerrar) quedaba en
 * coordenadas negativas, fuera del alcance del scroll.</p>
 *
 * <p>Mientras esta abierto: Escape cierra, Tab no sale del panel, el body no
 * scrollea y al cerrar el foco vuelve a quien lo abrio. Un Escape que ya
 * consumio un componente interno (p. ej. el `DatePicker`, que marca el evento
 * con `preventDefault`) no cierra el modal.</p>
 */
export function Modal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}) {
  const tituloId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  // Los padres pasan `onClose` como arrow inline: con un ref el efecto no se
  // re-ejecuta en cada render y el foco no salta al panel mientras se escribe.
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return

    const focoPrevio = document.activeElement as HTMLElement | null
    const overflowPrevio = document.body.style.overflow
    document.body.style.overflow = "hidden"
    panelRef.current?.focus()

    function alPresionarTecla(evento: KeyboardEvent) {
      if (evento.key === "Escape") {
        if (!evento.defaultPrevented) onCloseRef.current()
        return
      }
      if (evento.key !== "Tab" || !panelRef.current) return
      // Los popovers de react-aria/HeroUI (p. ej. el calendario del rango de la
      // guardería) se abren en un portal fuera del panel, como otro role=dialog:
      // el Tab dentro de ellos lo maneja su propia trampa de foco, no esta.
      const otroDialogo = (document.activeElement as HTMLElement | null)?.closest('[role="dialog"]')
      if (otroDialogo && otroDialogo !== panelRef.current) return

      // El `Button` de Base UI deja `tabindex` en botones deshabilitados: sin
      // este filtro el ultimo "focusable" podia ser un Guardar inactivo y el
      // foco no se movia.
      const focusables = [...panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLES)].filter(
        (el) => !el.hasAttribute("disabled") && el.getClientRects().length > 0,
      )
      if (focusables.length === 0) {
        evento.preventDefault()
        return
      }
      const primero = focusables[0]
      const ultimo = focusables[focusables.length - 1]
      const activo = document.activeElement
      const fuera = !panelRef.current.contains(activo)
      if (evento.shiftKey && (activo === primero || activo === panelRef.current || fuera)) {
        evento.preventDefault()
        ultimo.focus()
      } else if (!evento.shiftKey && (activo === ultimo || fuera)) {
        evento.preventDefault()
        primero.focus()
      }
    }

    document.addEventListener("keydown", alPresionarTecla)
    return () => {
      document.removeEventListener("keydown", alPresionarTecla)
      document.body.style.overflow = overflowPrevio
      focoPrevio?.focus?.()
    }
  }, [open])

  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40" onMouseDown={onClose}>
      <div className="flex min-h-full items-start justify-center p-4 sm:items-center">
        <div
          ref={panelRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={tituloId}
          tabIndex={-1}
          className="w-full max-w-lg rounded-xl border border-border bg-card shadow-lg outline-none"
          onMouseDown={(e) => e.stopPropagation()}
        >
          <header className="flex items-center justify-between border-b border-border px-4 py-3">
            <h2 id={tituloId} className="text-sm font-semibold">
              {title}
            </h2>
            <button
              onClick={onClose}
              className="rounded-xl p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              aria-label="Cerrar"
            >
              <X className="h-4 w-4" />
            </button>
          </header>
          <div className="p-4">{children}</div>
        </div>
      </div>
    </div>
  )
}
