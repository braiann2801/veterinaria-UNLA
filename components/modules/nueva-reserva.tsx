"use client"

/**
 * TASK-008: alta de reserva de guardería con horario de entrada y salida.
 *
 * <p>Antes de este componente el único camino para reservar una Estadía era
 * `components/modules/guarderia.tsx`, que escribe en el store en memoria de
 * `lib/store.tsx`: la pantalla aceptaba la reserva, el semáforo de aforo se
 * actualizaba contra ese store y al recargar no quedaba nada en MySQL. Además
 * ese módulo no está montado en `app/page.tsx`, así que ni siquiera era
 * alcanzable desde la navegación.</p>
 *
 * <p>Este modal sí persiste: `POST /api/v1/guarderia` y el aforo lo vuelve a
 * calcular el servidor. La UI no lleva la cuenta de cupos, solo refleja el
 * `cuposOcupados` que viene en cada respuesta.</p>
 *
 * <p><b>Rango y monto:</b> la estadía se elige como un rango de entrada y
 * salida (fecha y hora) con el DateRangePicker de HeroUI. El monto lo calcula
 * el backend (`GET /guarderia/cotizar`) con los precios que ajusta el Dueño:
 * por horas si entra y sale el mismo día, por noches si pasa al menos una. La
 * pantalla no repite la fórmula: muestra la misma cuenta con la que se emite la
 * comanda. Las reglas del rango (salida posterior a la entrada, aforo de cada
 * día) también las valida el backend.</p>
 */

import { useEffect, useState } from "react"
import { Clock, Loader2 } from "lucide-react"

import {
  guarderia as apiGuarderia,
  mascotas as apiMascotas,
  tarifasGuarderia as apiTarifasGuarderia,
} from "@/lib/api"
import { ApiError } from "@/lib/api"
import type { AforoGuarderia, CotizacionGuarderia, Mascota, ReservaGuarderia } from "@/lib/api"
import { useApi, mensajeDeError } from "@/lib/use-api"
import { Modal } from "@/components/ui/modal"
import { RangoEstadiaPicker, aFechaHora, partes, type RangoEstadia } from "@/components/ui/rango-estadia"
import { Select, TextArea, TextInput } from "@/components/ui/field"
import { Semforo, TOQUE_MINIMO, money } from "@/lib/vista"

/** Fecha `YYYY-MM-DD` del día siguiente. */
function diaSiguiente(fecha: string): string {
  const [a, m, d] = fecha.split("-").map(Number)
  const siguiente = new Date(Date.UTC(a, m - 1, d + 1))
  return siguiente.toISOString().slice(0, 10)
}

function etiquetaMascota(m: Mascota): string {
  const tutor = m.tutores?.[0]
  return tutor ? `${m.nombre} · ${tutor.nombre} ${tutor.apellido}` : `${m.nombre} · sin tutor`
}

function tonoDeAforo(aforo: AforoGuarderia) {
  if (aforo.completo) return "rojo" as const
  if (aforo.cuposDisponibles <= 2) return "ambar" as const
  return "verde" as const
}

export function NuevaReservaGuarderia({
  fechaPorDefecto,
  onCerrar,
  onCreada,
}: {
  fechaPorDefecto: string
  onCerrar: () => void
  /** Recibe la reserva creada para que el padre recargue su listado. */
  onCreada: () => void | Promise<void>
}) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())

  // Por defecto, una noche: entra el día abierto en la agenda a las 08:00 y sale al día siguiente.
  const [rango, setRango] = useState<RangoEstadia | null>(() => ({
    start: aFechaHora(fechaPorDefecto, "08:00"),
    end: aFechaHora(diaSiguiente(fechaPorDefecto), "08:00"),
  }))
  const [mascotaId, setMascotaId] = useState<number | null>(null)
  const [sena, setSena] = useState("0")
  const [observaciones, setObservaciones] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  // Despues de guardar se muestra a que comanda quedo atada la estadia (o que no
  // se pudo emitir por falta de tutor) en vez de cerrar el modal en silencio.
  const [creada, setCreada] = useState<ReservaGuarderia | null>(null)

  // El aforo de la fecha elegida, que es distinto del de la fecha abierta en la
  // agenda: el operador puede reservar para mañana y el semáforo que le
  // importa es el de mañana. Se pide al backend y no se lleva cuenta local.
  const entrada = rango ? partes(rango.start) : null
  const salida = rango ? partes(rango.end) : null
  const fecha = entrada?.fecha ?? fechaPorDefecto
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha), true, fecha)

  // Monto en vivo: lo calcula el backend con los precios vigentes. La clave
  // vuelve a pedirlo cada vez que cambia el rango.
  const claveRango = entrada && salida ? `${entrada.iso}|${salida.iso}` : ""
  const cotizacion = useApi<CotizacionGuarderia>(
    () => apiTarifasGuarderia.cotizar(entrada!.iso, salida!.iso),
    claveRango !== "",
    claveRango,
  )

  useEffect(() => {
    setError(null)
  }, [claveRango])

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      const reserva = await apiGuarderia.crear({
        fecha: entrada!.fecha,
        fechaSalida: salida!.fecha,
        mascotaId: mascotaId as number,
        horaEntrada: entrada!.hora,
        horaSalida: salida!.hora,
        sena: sena.trim() === "" ? 0 : Number(sena),
        observaciones: observaciones.trim() || undefined,
      })
      await onCreada()
      setCreada(reserva)
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo crear la reserva")
      // Un 409 por aforo lleno deja de estar al día lo que muestra el semáforo:
      // si falló por cupo, la respuesta que motivó el intento era desactualizada.
      if (e instanceof ApiError && (e.esConflicto || e.esValidacion)) await aforo.recargar()
    } finally {
      setGuardando(false)
    }
  }

  const listo = mascotaId !== null && entrada !== null && salida !== null
  const sinMascotas = mascotas.datos !== null && mascotas.datos.length === 0

  if (creada) {
    return (
      <Modal open onClose={onCerrar} title="Reserva creada">
        <div className="flex flex-col gap-3">
          {creada.comandaId !== null ? (
            <p className="text-sm" role="status">
              Se emitió la comanda #{creada.comandaId} de guardería para {creada.nombreMascota}. Se cobra en
              Caja: ahí se puede registrar la seña o el pago completo, y el egreso no se habilita hasta que esté
              saldada.
            </p>
          ) : (
            <p className="rounded-md border border-amber-400 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-300" role="alert">
              {creada.nombreMascota} no tiene tutor vinculado, así que la estadía no generó comanda. Para poder
              cobrarla: cancelá esta reserva, vinculá un tutor en el Padrón y volvé a reservar.
            </p>
          )}
          <button
            onClick={onCerrar}
            className={`rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground ${TOQUE_MINIMO}`}
          >
            Listo
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal open onClose={onCerrar} title="Nueva reserva de guardería">
      <div className="flex flex-col gap-3">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Mascota</span>
          <Select
            value={mascotaId ?? ""}
            onChange={(e) => setMascotaId(e.target.value ? Number(e.target.value) : null)}
            className={TOQUE_MINIMO}
          >
            <option value="">Elegí una mascota…</option>
            {(mascotas.datos ?? []).map((m) => (
              <option key={m.id} value={m.id}>
                {etiquetaMascota(m)}
              </option>
            ))}
          </Select>
          {sinMascotas && (
            <span className="text-xs text-muted-foreground">
              No hay mascotas cargadas. Cargalas primero en el Padrón.
            </span>
          )}
        </label>

        <RangoEstadiaPicker value={rango} onChange={setRango} />

        <div className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2" aria-live="polite">
          <span className="text-xs font-medium text-muted-foreground">Monto a abonar</span>
          {!claveRango ? (
            <span className="text-xs text-muted-foreground">Elegí entrada y salida</span>
          ) : cotizacion.cargando && cotizacion.datos === null ? (
            <Loader2 className="size-4 animate-spin text-muted-foreground" aria-hidden />
          ) : cotizacion.error ? (
            <span className="text-right text-xs text-destructive">{mensajeDeError(cotizacion.error)}</span>
          ) : cotizacion.datos ? (
            <span className="text-right">
              <span className="block text-base font-semibold" data-testid="monto-estadia">
                {money(cotizacion.datos.total)}
              </span>
              <span className="block text-xs text-muted-foreground">
                {cotizacion.datos.cantidad} {cotizacion.datos.modalidad === "NOCHES"
                  ? cotizacion.datos.cantidad === 1 ? "noche" : "noches"
                  : cotizacion.datos.cantidad === 1 ? "hora" : "horas"} × {money(cotizacion.datos.precioUnitario)}
              </span>
            </span>
          ) : null}
        </div>

        <div className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2">
          <span className="text-xs font-medium text-muted-foreground">Aforo del {fecha}</span>
          {aforo.cargando && aforo.datos === null ? (
            <Loader2 className="size-4 animate-spin text-muted-foreground" aria-hidden />
          ) : aforo.datos ? (
            <Semforo tono={tonoDeAforo(aforo.datos)}>
              {aforo.datos.cuposOcupados} de {aforo.datos.aforoMaximo}
            </Semforo>
          ) : (
            <span className="text-xs text-destructive">No se pudo consultar</span>
          )}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Seña</span>
            <TextInput
              value={sena}
              onChange={(e) => setSena(e.target.value)}
              inputMode="numeric"
              placeholder="0"
              className={TOQUE_MINIMO}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Observaciones</span>
            <TextArea
              value={observaciones}
              onChange={(e) => setObservaciones(e.target.value)}
              placeholder="Dieta especial, medicación, etc."
              maxLength={300}
            />
          </label>
        </div>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button
            onClick={onCerrar}
            disabled={guardando}
            className={`rounded-md border border-border px-3 text-sm transition-colors hover:bg-muted disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            Cancelar
          </button>
          <button
            onClick={guardar}
            disabled={guardando || !listo}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <Clock className="size-4" aria-hidden />
            {guardando ? "Reservando…" : "Reservar"}
          </button>
        </div>
      </div>
    </Modal>
  )
}