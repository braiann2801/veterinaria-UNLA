"use client"

/**
 * Tarifas: precios que ajusta el Dueño.
 *
 * <p>Guardería: precio por hora (estadías que entran y salen el mismo día) y por
 * noche (si pasa al menos una). Con esos dos valores se calcula el monto de cada
 * reserva y su comanda. Consulta clínica: el precio de la comanda que emite la
 * Ficha médica.</p>
 *
 * <p>Un cambio rige para lo que se emita desde ahora: las comandas ya emitidas
 * conservan su monto.</p>
 */

import { useEffect, useState } from "react"
import { Save } from "lucide-react"

import {
  ApiError,
  comandas as apiComandas,
  tarifasGuarderia as apiTarifasGuarderia,
  type TarifaGuarderia,
} from "@/lib/api"
import { useApi, mensajeDeError } from "@/lib/use-api"
import { Field, Panel, TextInput } from "@/components/ui/field"
import { Recurso, TOQUE_MINIMO, money, soloDecimal } from "@/lib/vista"
import { cn } from "@/lib/utils"

const BOTON = cn(
  "inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60",
  TOQUE_MINIMO,
)

function textoError(e: unknown) {
  return e instanceof ApiError ? mensajeDeError(e) ?? "No se pudo guardar" : "No se pudo guardar"
}

/** Acepta coma o punto decimal; null si no es un número válido y no negativo. */
function parsear(valor: string): number | null {
  const n = Number(valor.replace(",", ".").trim())
  return valor.trim() !== "" && Number.isFinite(n) && n >= 0 ? n : null
}

export function Tarifas() {
  return (
    <div className="flex flex-col gap-4">
      <TarifasGuarderia />
      <TarifaConsulta />
    </div>
  )
}

function TarifasGuarderia() {
  const tarifas = useApi<TarifaGuarderia>(() => apiTarifasGuarderia.obtener())
  const [hora, setHora] = useState("")
  const [noche, setNoche] = useState("")
  const [estado, setEstado] = useState<{ tipo: "ok" | "error"; texto: string } | null>(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    if (tarifas.datos) {
      setHora(String(tarifas.datos.precioHora))
      setNoche(String(tarifas.datos.precioNoche))
    }
  }, [tarifas.datos])

  const precioHora = parsear(hora)
  const precioNoche = parsear(noche)

  async function guardar() {
    if (precioHora === null || precioNoche === null) return
    setGuardando(true)
    setEstado(null)
    try {
      await apiTarifasGuarderia.actualizar({ precioHora, precioNoche })
      await tarifas.recargar()
      setEstado({ tipo: "ok", texto: "Precios de guardería actualizados. Rigen para las reservas nuevas." })
    } catch (e) {
      setEstado({ tipo: "error", texto: textoError(e) })
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Panel title="Guardería">
      <Recurso estado={tarifas} textoCarga="Cargando tarifas" onReintentar={tarifas.recargar}>
        {() => (
          <div className="flex flex-col gap-3">
            <p className="text-sm text-muted-foreground">
              Si el perro entra y sale el mismo día se cobra por hora (la fracción de hora cuenta como hora
              entera). Si pasa al menos una noche, se cobra por noche.
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Precio por hora">
                <TextInput inputMode="decimal" value={hora} onChange={(e) => setHora(soloDecimal(e.target.value))} className={TOQUE_MINIMO} />
              </Field>
              <Field label="Precio por noche">
                <TextInput inputMode="decimal" value={noche} onChange={(e) => setNoche(soloDecimal(e.target.value))} className={TOQUE_MINIMO} />
              </Field>
            </div>
            {precioHora !== null && precioNoche !== null && (
              <p className="text-xs text-muted-foreground">
                Ejemplos: 4 horas en el día = {money(precioHora * 4)} · 2 noches = {money(precioNoche * 2)}
              </p>
            )}
            {estado && (
              <p
                className={cn(
                  "rounded-md border p-3 text-sm",
                  estado.tipo === "ok"
                    ? "border-emerald-500/40 bg-emerald-500/10"
                    : "border-destructive/40 bg-destructive/10 text-destructive",
                )}
                role={estado.tipo === "ok" ? "status" : "alert"}
              >
                {estado.texto}
              </p>
            )}
            <div>
              <button
                onClick={guardar}
                disabled={guardando || precioHora === null || precioNoche === null}
                className={BOTON}
              >
                <Save className="size-4" aria-hidden />
                {guardando ? "Guardando…" : "Guardar precios de guardería"}
              </button>
            </div>
          </div>
        )}
      </Recurso>
    </Panel>
  )
}

function TarifaConsulta() {
  const tarifas = useApi<Record<string, number>>(() => apiComandas.tarifas())
  const [consulta, setConsulta] = useState("")
  const [estado, setEstado] = useState<{ tipo: "ok" | "error"; texto: string } | null>(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    if (tarifas.datos?.CONSULTA_CLINICA !== undefined) setConsulta(String(tarifas.datos.CONSULTA_CLINICA))
  }, [tarifas.datos])

  const monto = parsear(consulta)

  async function guardar() {
    if (monto === null) return
    setGuardando(true)
    setEstado(null)
    try {
      await apiComandas.actualizarTarifa("CONSULTA_CLINICA", monto)
      await tarifas.recargar()
      setEstado({ tipo: "ok", texto: "Precio de consulta actualizado. Rige para las fichas nuevas." })
    } catch (e) {
      setEstado({ tipo: "error", texto: textoError(e) })
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Panel title="Consulta clínica">
      <Recurso estado={tarifas} textoCarga="Cargando tarifas" onReintentar={tarifas.recargar}>
        {() => (
          <div className="flex flex-col gap-3">
            <p className="text-sm text-muted-foreground">
              Monto de la comanda que se emite al guardar una ficha en Ficha médica.
            </p>
            <Field label="Precio de la consulta">
              <TextInput inputMode="decimal" value={consulta} onChange={(e) => setConsulta(soloDecimal(e.target.value))} className={TOQUE_MINIMO} />
            </Field>
            {estado && (
              <p
                className={cn(
                  "rounded-md border p-3 text-sm",
                  estado.tipo === "ok"
                    ? "border-emerald-500/40 bg-emerald-500/10"
                    : "border-destructive/40 bg-destructive/10 text-destructive",
                )}
                role={estado.tipo === "ok" ? "status" : "alert"}
              >
                {estado.texto}
              </p>
            )}
            <div>
              <button onClick={guardar} disabled={guardando || monto === null} className={BOTON}>
                <Save className="size-4" aria-hidden />
                {guardando ? "Guardando…" : "Guardar precio de consulta"}
              </button>
            </div>
          </div>
        )}
      </Recurso>
    </Panel>
  )
}
