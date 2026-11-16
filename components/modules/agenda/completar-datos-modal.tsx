"use client"

/**
 * Completar cliente y mascota de una urgencia que se registró sin datos.
 *
 * <p>Se puede hacer antes o después de atenderla. Si ya se atendió, la ficha y
 * la comanda (que nació sin titular) pasan a ser de esa mascota y de su tutor
 * autorizado, y recién entonces la comanda se puede cobrar en Caja.</p>
 */

import { useState } from "react"
import { UserCheck } from "lucide-react"

import { tutores as apiTutores, turnos as apiTurnos } from "@/lib/api"
import type { TurnoClinico, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Button } from "@/components/ui/button"
import { Modal } from "@/components/ui/modal"
import { TOQUE_MINIMO } from "@/lib/vista"
import {
  ClienteMascotaForm,
  SELECCION_INICIAL,
  aPedido,
  seleccionLista,
  type SeleccionClienteMascota,
} from "@/components/modules/agenda/cliente-mascota-form"

export function CompletarDatosModal({
  turno,
  onCerrar,
  onGuardado,
}: {
  turno: TurnoClinico
  onCerrar: () => void
  onGuardado: (mensaje: string) => void | Promise<void>
}) {
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())
  const [seleccion, setSeleccion] = useState<SeleccionClienteMascota>(SELECCION_INICIAL)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    let actualizado: TurnoClinico
    try {
      actualizado = await apiTurnos.completarDatos(turno.id, aPedido(seleccion))
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudieron guardar los datos")
      setGuardando(false)
      return
    }
    // Los datos ya quedaron guardados: si falla releer la agenda, no se muestra como error.
    try {
      await onGuardado(
        turno.estado === "ATENDIDO"
          ? `Datos de ${actualizado.mascota?.nombre ?? "la urgencia"} completos: su comanda ya se puede cobrar en Caja.`
          : `Datos de ${actualizado.mascota?.nombre ?? "la urgencia"} completos.`,
      )
    } catch {
      // El padre muestra la agenda desactualizada con su propio estado de error.
    }
    setGuardando(false)
    onCerrar()
  }

  return (
    <Modal open onClose={onCerrar} title="Completar datos de la urgencia">
      <div className="flex flex-col gap-3">
        <p className="text-xs text-muted-foreground">
          Urgencia de las {turno.fechaHoraInicio.slice(11, 16)}. Los datos son obligatorios para poder cobrarla.
        </p>

        <ClienteMascotaForm valor={seleccion} onChange={setSeleccion} tutores={tutores.datos ?? []} />

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onCerrar} type="button" className={TOQUE_MINIMO}>
            Cancelar
          </Button>
          <Button
            onClick={guardar}
            disabled={guardando || !seleccionLista(seleccion)}
            type="button"
            className={TOQUE_MINIMO}
          >
            <UserCheck className="size-4" aria-hidden />
            {guardando ? "Guardando…" : "Guardar datos"}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
