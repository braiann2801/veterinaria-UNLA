"use client"

import type { ReservaGuarderia } from "@/lib/api"
import type { EstadoApi } from "@/lib/use-api"

import { ColumnaLista } from "./columna-lista"

/**
 * Reservas que ocupan plaza ese día.
 *
 * Es el mismo criterio que usa el backend en `JornadaDelDiaService`: FINALIZADO y
 * CANCELADO liberan el cupo, así que no pueden aparecer como "ingresa hoy" ni como
 * "se retira hoy". Copiarlo acá evita que el tablero y el aforo discrepen sobre la
 * misma fecha.
 */
const RESERVAS_QUE_OCUPAN = new Set(["RESERVADO", "INGRESADO"])

/**
 * `"08:00:00"` → `"08:00"`.
 *
 * Jackson emite las columnas `time` de MySQL con segundos. Mostrar `08:00:00` en
 * la grilla del mostrador hace ruido y todos los horarios de la pantalla están en
 * `HH:mm`.
 */
function hhmm(hora: string | null): string {
  return hora ? hora.slice(0, 5) : "sin horario"
}

/**
 * Columnas de guardería del tablero: "Ingresan" y "Se retiran".
 *
 * <p>Es la misma lista partida en dos: una reserva entra por su hora de ingreso y
 * sale por su hora de salida. Devuelve un fragmento con las dos columnas para que
 * queden como dos casillas más de la grilla del tablero, sin un `div` extra que
 * rompa el `md:grid-cols-3`.</p>
 */
export function TablaGuarderia({ reservas }: { reservas: EstadoApi<ReservaGuarderia[]> }) {
  const reservasQueOcupan = (reservas.datos ?? []).filter((r) => RESERVAS_QUE_OCUPAN.has(r.estado))
  const ingresan = reservasQueOcupan.filter((r) => r.horaEntrada !== null)
  const seRetiran = reservasQueOcupan.filter((r) => r.horaSalida !== null)

  return (
    <>
      <ColumnaLista
        title="Ingresan (guardería)"
        items={ingresan.map((r) => ({
          id: `in-${r.id}`,
          top: r.nombreMascota,
          sub: `Entra ${hhmm(r.horaEntrada)} · sale ${hhmm(r.horaSalida)}`,
        }))}
        icon="in"
      />
      <ColumnaLista
        title="Se retiran"
        items={seRetiran.map((r) => ({
          id: `out-${r.id}`,
          top: r.nombreMascota,
          sub: `Sale ${hhmm(r.horaSalida)}`,
        }))}
        icon="out"
      />
    </>
  )
}
