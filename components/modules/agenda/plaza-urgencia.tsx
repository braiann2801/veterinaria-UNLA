"use client"

/**
 * Plaza extra de guardería para urgencia clínica (excepción de la regla 1 de
 * AGENTS.md): una sola por día, aparte de las 10.
 *
 * <p>Cualquier empleado ve si está habilitada y quién la ocupa; solo el
 * Veterinario la habilita, la deshabilita o interna una mascota. El backend lo
 * valida igual (403): acá solo se ocultan los controles.</p>
 */

import { useState } from "react"
import { Siren } from "lucide-react"

import { guarderia as apiGuarderia, mascotas as apiMascotas } from "@/lib/api"
import type { Mascota, PlazaUrgencia } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Button } from "@/components/ui/button"
import { Field, Select } from "@/components/ui/field"
import { Recurso, TOQUE_MINIMO } from "@/lib/vista"

export function PlazaUrgenciaGuarderia({
  fecha,
  esVeterinario,
  onCambio,
}: {
  fecha: string
  esVeterinario: boolean
  /** El padre relee estadías y aforo. */
  onCambio: () => void | Promise<void>
}) {
  const plaza = useApi<PlazaUrgencia>(() => apiGuarderia.plazaUrgencia(fecha), true, fecha)
  const [internando, setInternando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function ejecutar(accion: () => Promise<unknown>) {
    setGuardando(true)
    setError(null)
    try {
      await accion()
      setInternando(false)
      await Promise.all([plaza.recargar(), onCambio()])
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo actualizar la plaza de urgencia")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Recurso estado={plaza} textoCarga="Consultando plaza de urgencia">
      {(p) => {
        // Sin habilitar no hay nada que mostrar a quien no la puede habilitar.
        if (!p.habilitada && !esVeterinario) return null
        return (
          <div className="flex flex-col gap-2 rounded-xl border border-red-500/30 bg-red-500/5 p-3">
            <p className="inline-flex items-center gap-2 text-sm font-medium text-red-700">
              <Siren className="size-4" aria-hidden />
              Plaza de urgencia clínica
            </p>
            <p className="text-xs text-muted-foreground">
              {!p.habilitada
                ? "No habilitada. Es una plaza extra, aparte de los 10 cupos, solo para urgencias clínicas."
                : p.ocupada
                  ? `Ocupada por ${p.nombreMascota}.`
                  : "Habilitada y libre. No la usan las reservas normales."}
            </p>

            {esVeterinario && (
              <>
                {!p.habilitada && (
                  <Button
                    type="button"
                    variant="outline"
                    disabled={guardando}
                    onClick={() => ejecutar(() => apiGuarderia.habilitarPlazaUrgencia(fecha))}
                    className={TOQUE_MINIMO}
                  >
                    Habilitar plaza de urgencia
                  </Button>
                )}
                {p.habilitada && !p.ocupada && !internando && (
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Button
                      type="button"
                      disabled={guardando}
                      onClick={() => setInternando(true)}
                      className={TOQUE_MINIMO}
                    >
                      Internar mascota
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={guardando}
                      onClick={() => ejecutar(() => apiGuarderia.deshabilitarPlazaUrgencia(fecha))}
                      className={TOQUE_MINIMO}
                    >
                      Deshabilitar
                    </Button>
                  </div>
                )}
                {p.habilitada && !p.ocupada && internando && (
                  <InternarMascota
                    guardando={guardando}
                    onCancelar={() => setInternando(false)}
                    onInternar={(mascotaId) => ejecutar(() => apiGuarderia.ocuparPlazaUrgencia(fecha, mascotaId))}
                  />
                )}
                {p.ocupada && (
                  <p className="text-xs text-muted-foreground">
                    Para deshabilitarla, primero liberá la plaza (egreso o cancelación de la estadía).
                  </p>
                )}
              </>
            )}

            {error && (
              <p className="rounded-md border border-destructive/40 bg-destructive/10 p-2 text-xs text-destructive" role="alert">
                {error}
              </p>
            )}
          </div>
        )
      }}
    </Recurso>
  )
}

/** Se monta solo al tocar "Internar mascota": recién ahí se piden las mascotas. */
function InternarMascota({
  guardando,
  onCancelar,
  onInternar,
}: {
  guardando: boolean
  onCancelar: () => void
  onInternar: (mascotaId: number) => void
}) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const [mascotaId, setMascotaId] = useState("")

  return (
    <div className="flex flex-col gap-2">
      <Field label="Mascota a internar">
        <Select value={mascotaId} onChange={(e) => setMascotaId(e.target.value)} className={TOQUE_MINIMO}>
          <option value="">{mascotas.cargando ? "Cargando mascotas…" : "Elegí una mascota…"}</option>
          {(mascotas.datos ?? [])
            .filter((m) => m.activo)
            .map((m) => (
              <option key={m.id} value={m.id}>
                {m.nombre}
              </option>
            ))}
        </Select>
      </Field>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button type="button" variant="outline" onClick={onCancelar} className={TOQUE_MINIMO}>
          Cancelar
        </Button>
        <Button
          type="button"
          disabled={guardando || mascotaId === ""}
          onClick={() => onInternar(Number(mascotaId))}
          className={TOQUE_MINIMO}
        >
          {guardando ? "Internando…" : "Internar"}
        </Button>
      </div>
    </div>
  )
}
