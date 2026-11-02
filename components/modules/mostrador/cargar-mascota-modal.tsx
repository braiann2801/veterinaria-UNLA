"use client"

/**
 * Alta de una mascota para un cliente que ya está en el padrón, sin salir del
 * Mostrador. Reusa el formulario del Padrón; solo ofrece clientes activos.
 */

import { tutores as apiTutores, type Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Modal } from "@/components/ui/modal"
import { Recurso } from "@/lib/vista"
import { AltaMascota } from "@/components/modules/alta-mascota"

export function CargarMascotaModal({
  onCerrar,
  onGuardado,
}: {
  onCerrar: () => void
  onGuardado: () => void | Promise<void>
}) {
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())

  return (
    <Modal open onClose={onCerrar} title="Cargar mascota">
      <Recurso estado={tutores} textoCarga="Cargando clientes" onReintentar={tutores.recargar}>
        {(lista) => (
          <AltaMascota
            sinPanel
            tutores={lista.filter((t) => t.activo)}
            onCreada={async () => {
              await onGuardado()
            }}
          />
        )}
      </Recurso>
    </Modal>
  )
}
