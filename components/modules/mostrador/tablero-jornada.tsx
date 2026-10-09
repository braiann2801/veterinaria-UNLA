"use client"

import { Panel } from "@/components/ui/field"
import type { ReservaGuarderia, TurnoClinico } from "@/lib/api"
import type { EstadoApi } from "@/lib/use-api"
import { Cargando, ErrorBox } from "@/lib/vista"

import { TablaGuarderia } from "./tabla-guarderia"
import { TablaTurnos } from "./tabla-turnos"

/**
 * Panel "Turnos y estadías": las tres columnas del movimiento diario.
 *
 * <p>Envuelve las dos tablas y concentra el orden carga → error → grilla, que es
 * el mismo que el resto de la app. El error es combinado a propósito: si cualquiera
 * de las dos listas falla, reintentar (`onReintentar`) recarga las dos, así que
 * mostrar un único `ErrorBox` no pierde información.</p>
 */
export function TableroJornada({
  turnos,
  reservas,
  fecha,
  onReintentar,
}: {
  turnos: EstadoApi<TurnoClinico[]>
  reservas: EstadoApi<ReservaGuarderia[]>
  fecha: string
  onReintentar: () => void | Promise<void>
}) {
  return (
    <Panel
      title={
        turnos.cargando || reservas.cargando
          ? "Turnos y estadías"
          : `Turnos y estadías del ${fecha}`
      }
    >
      {turnos.cargando && reservas.cargando && turnos.datos === null && reservas.datos === null ? (
        <Cargando texto="Consultando la jornada" />
      ) : turnos.error || reservas.error ? (
        <ErrorBox error={turnos.error ?? reservas.error ?? null} onReintentar={onReintentar} />
      ) : (
        <div className="grid gap-4 md:grid-cols-3">
          <TablaTurnos turnos={turnos} />
          <TablaGuarderia reservas={reservas} />
        </div>
      )}
    </Panel>
  )
}
