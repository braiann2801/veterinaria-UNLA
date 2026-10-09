"use client"

import type { TurnoClinico } from "@/lib/api"
import type { EstadoApi } from "@/lib/use-api"

import { ColumnaLista } from "./columna-lista"

/**
 * Columna "Turnos" del tablero: los turnos clínicos del día elegido.
 *
 * <p>Los cancelados se sacan de la lista y se resumen en la nota. Un turno
 * cancelado libera la franja (mismo criterio que el backend en
 * `JornadaDelDiaService`), así que no debe contarse como atención del día; pero
 * se informa cuántos son, porque esconderlos en silencio haría creer que nunca
 * existieron.</p>
 */
export function TablaTurnos({ turnos }: { turnos: EstadoApi<TurnoClinico[]> }) {
  const listaTurnos = turnos.datos ?? []
  const cancelados = listaTurnos.filter((t) => t.estado === "CANCELADO")
  const turnosVigentes = listaTurnos.filter((t) => t.estado !== "CANCELADO")

  return (
    <ColumnaLista
      title="Turnos"
      nota={
        cancelados.length > 0
          ? `${cancelados.length} cancelado${cancelados.length === 1 ? "" : "s"} fuera de la lista`
          : undefined
      }
      items={turnosVigentes.map((t) => ({
        id: String(t.id),
        top: `${t.mascota.nombre} · ${t.fechaHoraInicio.slice(11, 16)}`,
        sub: `${t.profesional.nombreCompleto} · ${t.motivo}`,
      }))}
      icon="turno"
    />
  )
}
