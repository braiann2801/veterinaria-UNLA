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
 * <p><b>Sobre la validación de las horas:</b> el formulario permite cualquier
 * par y deja que el backend lo rechace con 400. Podría duplicar la regla acá
 * para deshabilitar el botón, pero entonces habría dos verdades sobre la misma
 * condición, y ya se rompió una vez: la validación ingenua de "salida posterior a
 * entrada" rechaza el caso más común de la estadía de 24 horas, donde el perro
 * entra a las 08:00 y sale a las 08:00 <i>del día siguiente</i>. Esa excepción
 * depende del tipo de estadía y vive en una sola tabla de la decisión, en el
 * servicio.</p>
 */

import { useEffect, useState } from "react"
import { Clock, Loader2 } from "lucide-react"

import { guarderia as apiGuarderia, mascotas as apiMascotas } from "@/lib/api"
import { ApiError } from "@/lib/api"
import type { AforoGuarderia, Mascota, TipoEstadiaGuarderia } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Modal } from "@/components/ui/modal"
import { Select, TextArea, TextInput } from "@/components/ui/field"
import { Semforo, TOQUE_MINIMO } from "@/lib/vista"

const TIPOS: { valor: TipoEstadiaGuarderia; etiqueta: string }[] = [
  { valor: "DIURNA", etiqueta: "Diurna (dentro del mismo día)" },
  { valor: "NOCTURNA", etiqueta: "Nocturna" },
  { valor: "COMPLETA_24H", etiqueta: "Completa 24 horas" },
]

/**
 * Horario de apertura y cierre del canil.
 *
 * Solo determinan el `min` y el `max` del input de hora: acotan lo que el
 * operador puede elegir sin saltarse la jornada, pero no son una regla de
 * negocio. El backend acepta cualquier hora; estos límites son una ayuda para
 * no cargar, por error, un retiro a las 3:00.
 */
const APERTURA = "06:00"
const CIERRE = "23:00"

/** Par de horas que el formulario propone al cambiar de tipo de estadía. */
const HORARIOS_SUGERIDOS: Record<TipoEstadiaGuarderia, { entrada: string; salida: string }> = {
  DIURNA: { entrada: "08:00", salida: "18:00" },
  NOCTURNA: { entrada: "19:00", salida: "08:00" },
  COMPLETA_24H: { entrada: "08:00", salida: "08:00" },
}

function etiquetaMascota(m: Mascota): string {
  const tutor = m.tutores?.[0]
  return tutor ? `${m.nombre} · ${tutor.nombre} ${tutor.apellido}` : `${m.nombre} · sin tutor`
}

/** Fecha local en `YYYY-MM-DD`; `toISOString()` devolvería mañana de noche. */
function hoy(): string {
  const d = new Date()
  const mes = String(d.getMonth() + 1).padStart(2, "0")
  const dia = String(d.getDate()).padStart(2, "0")
  return `${d.getFullYear()}-${mes}-${dia}`
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

  const [fecha, setFecha] = useState(fechaPorDefecto)
  const [tipo, setTipo] = useState<TipoEstadiaGuarderia>("COMPLETA_24H")
  const [mascotaId, setMascotaId] = useState<number | null>(null)
  const [entrada, setEntrada] = useState(HORARIOS_SUGERIDOS.COMPLETA_24H.entrada)
  const [salida, setSalida] = useState(HORARIOS_SUGERIDOS.COMPLETA_24H.salida)
  const [sena, setSena] = useState("0")
  const [observaciones, setObservaciones] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  // El aforo de la fecha elegida, que es distinto del de la fecha abierta en la
  // agenda: el operador puede reservar para mañana y el semáforo que le
  // importa es el de mañana. Se pide al backend y no se lleva cuenta local.
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha), true, fecha)

  // Al cambiar la fecha de la reserva hay que volver a pedir el aforo: el de
  // la fecha anterior ya no describe nada. El hook se encarga solo vía su clave,
  // esto solo limpia la vista mientras vuela.
  useEffect(() => {
    setError(null)
  }, [fecha, tipo])

  /** Al cambiar de estadía se propone el par de horas que le corresponde. */
  function cambiarTipo(valor: TipoEstadiaGuarderia) {
    setTipo(valor)
    setEntrada(HORARIOS_SUGERIDOS[valor].entrada)
    setSalida(HORARIOS_SUGERIDOS[valor].salida)
  }

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await apiGuarderia.crear({
        fecha,
        tipoEstadia: tipo,
        mascotaId: mascotaId as number,
        // Las dos horas van juntas: el backend rechaza media pareja en vez de
        // completarla, asi que mandarlas siempre es lo que evita el error.
        horaEntrada: entrada,
        horaSalida: salida,
        sena: sena.trim() === "" ? 0 : Number(sena),
        observaciones: observaciones.trim() || undefined,
      })
      await onCreada()
      onCerrar()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo crear la reserva")
      // Un 409 por aforo lleno deja de estar al día lo que muestra el semáforo:
      // si falló por cupo, la respuesta que motivó el intento era desactualizada.
      if (e instanceof ApiError && (e.esConflicto || e.esValidacion)) await aforo.recargar()
    } finally {
      setGuardando(false)
    }
  }

  const listo = mascotaId !== null
  const sinMascotas = mascotas.datos !== null && mascotas.datos.length === 0

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

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Fecha</span>
            <TextInput
              type="date"
              value={fecha}
              onChange={(e) => setFecha(e.target.value || hoy())}
              className={TOQUE_MINIMO}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Tipo de estadía</span>
            <Select
              value={tipo}
              onChange={(e) => cambiarTipo(e.target.value as TipoEstadiaGuarderia)}
              className={TOQUE_MINIMO}
            >
              {TIPOS.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.etiqueta}
                </option>
              ))}
            </Select>
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Hora de entrada</span>
            <TextInput
              type="time"
              step={900}
              min={APERTURA}
              max={CIERRE}
              value={entrada}
              onChange={(e) => setEntrada(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Hora de salida</span>
            <TextInput
              type="time"
              step={900}
              min={APERTURA}
              max={CIERRE}
              value={salida}
              onChange={(e) => setSalida(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </label>
        </div>

        {tipo === "COMPLETA_24H" && (
          <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
            <Clock className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            En la estadía de 24 horas la salida es una hora del día siguiente, así
            que puede ser igual o anterior a la de entrada. Es el caso normal, no
            un error.
          </p>
        )}

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