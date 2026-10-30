"use client"

/**
 * Extras usados en la atención: el profesional elige del catálogo del Dueño y
 * la cantidad. No muestra precios (regla 3 de AGENTS.md): el monto lo suma el
 * backend en la comanda que cobra el mostrador.
 */

import { useState } from "react"
import { Minus, Plus, X } from "lucide-react"

import { extras as apiExtras, type Extra } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Select } from "@/components/ui/field"
import { TOQUE_MINIMO } from "@/lib/vista"
import { cn } from "@/lib/utils"

export type ExtraElegido = { extraId: number; cantidad: number }

const BOTON_ICONO = cn(
  "inline-flex w-11 items-center justify-center rounded-md border border-input hover:bg-muted disabled:opacity-50",
  TOQUE_MINIMO,
)

export function SelectorExtras({
  valor,
  onChange,
}: {
  valor: ExtraElegido[]
  onChange: (valor: ExtraElegido[]) => void
}) {
  const catalogo = useApi<Extra[]>(() => apiExtras.listar(true))
  const [elegido, setElegido] = useState("")

  const porId = new Map((catalogo.datos ?? []).map((e) => [e.id, e]))
  const disponibles = (catalogo.datos ?? []).filter((e) => !valor.some((v) => v.extraId === e.id))

  function agregar(id: string) {
    if (!id) return
    onChange([...valor, { extraId: Number(id), cantidad: 1 }])
    setElegido("")
  }

  function cambiarCantidad(extraId: number, delta: number) {
    onChange(
      valor.map((v) => (v.extraId === extraId ? { ...v, cantidad: Math.max(1, Math.min(99, v.cantidad + delta)) } : v)),
    )
  }

  function quitar(extraId: number) {
    onChange(valor.filter((v) => v.extraId !== extraId))
  }

  return (
    <div className="flex flex-col gap-2 text-sm">
      <span className="text-xs font-medium text-muted-foreground">Extras utilizados (opcional)</span>

      {catalogo.error ? (
        <p className="text-xs text-destructive">No se pudo leer el catálogo de extras.</p>
      ) : catalogo.datos !== null && catalogo.datos.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          No hay extras en el catálogo. El Dueño los carga en Tarifas.
        </p>
      ) : (
        <Select
          value={elegido}
          onChange={(e) => agregar(e.target.value)}
          disabled={disponibles.length === 0}
          aria-label="Agregar un extra"
          className={TOQUE_MINIMO}
        >
          <option value="">
            {catalogo.cargando && catalogo.datos === null
              ? "Cargando extras…"
              : disponibles.length === 0
                ? "Ya agregaste todos los extras"
                : "Agregar vacuna, urgencia o insumo…"}
          </option>
          {disponibles.map((e) => (
            <option key={e.id} value={e.id}>
              {e.nombre}
            </option>
          ))}
        </Select>
      )}

      {valor.length > 0 && (
        <ul className="flex flex-col gap-2">
          {valor.map((v) => (
            <li key={v.extraId} className="flex items-center gap-2 rounded-md border border-border p-1.5 pl-3">
              <span className="min-w-0 flex-1 truncate">{porId.get(v.extraId)?.nombre ?? `Extra #${v.extraId}`}</span>
              <button
                type="button"
                onClick={() => cambiarCantidad(v.extraId, -1)}
                disabled={v.cantidad <= 1}
                className={BOTON_ICONO}
                aria-label="Restar uno"
              >
                <Minus className="size-4" aria-hidden />
              </button>
              <span className="w-6 text-center tabular-nums" aria-label="Cantidad">
                {v.cantidad}
              </span>
              <button
                type="button"
                onClick={() => cambiarCantidad(v.extraId, 1)}
                disabled={v.cantidad >= 99}
                className={BOTON_ICONO}
                aria-label="Sumar uno"
              >
                <Plus className="size-4" aria-hidden />
              </button>
              <button type="button" onClick={() => quitar(v.extraId)} className={BOTON_ICONO} aria-label="Quitar extra">
                <X className="size-4" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
