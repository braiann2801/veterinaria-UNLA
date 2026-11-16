"use client"

/**
 * Confirmación en un modal propio, en lugar de `window.confirm`: el diálogo
 * nativo del navegador no respeta el diseño, no se adapta al celular y en
 * algunos navegadores se puede silenciar sin querer.
 */

import { Button } from "@/components/ui/button"
import { Modal } from "@/components/ui/modal"
import { TOQUE_MINIMO } from "@/lib/vista"

export type Confirmacion = {
  titulo: string
  mensaje: string
  textoConfirmar: string
  /** Texto del botón que cierra sin hacer nada. Por defecto "Cancelar"; cambialo cuando la acción también se llama "cancelar". */
  textoDescartar?: string
  /** Pinta el botón en rojo: acciones que cortan el acceso o no se deshacen. */
  peligrosa?: boolean
  alConfirmar: () => void | Promise<void>
}

export function ConfirmarModal({
  confirmacion,
  onCerrar,
}: {
  confirmacion: Confirmacion | null
  onCerrar: () => void
}) {
  if (!confirmacion) return null
  return (
    <Modal open onClose={onCerrar} title={confirmacion.titulo}>
      <div className="flex flex-col gap-4">
        <p className="text-sm">{confirmacion.mensaje}</p>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" type="button" onClick={onCerrar} className={TOQUE_MINIMO}>
            {confirmacion.textoDescartar ?? "Cancelar"}
          </Button>
          <Button
            type="button"
            variant={confirmacion.peligrosa ? "destructive" : "default"}
            className={TOQUE_MINIMO}
            onClick={() => {
              const accion = confirmacion.alConfirmar
              onCerrar()
              void accion()
            }}
          >
            {confirmacion.textoConfirmar}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
