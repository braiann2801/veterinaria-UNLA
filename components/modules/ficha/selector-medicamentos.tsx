"use client"

/**
 * Medicamentos usados en la atención: el profesional busca en el mismo
 * catálogo del módulo Medicamentos y elige con un click.
 *
 * <p>El precio de catálogo se muestra a título informativo (regla 3 de
 * AGENTS.md): no se puede editar acá ni viaja en el payload de la ficha. El
 * backend lo congela en la comanda al guardar. El buscador reutiliza
 * `useMedicamentos`, así que comparte el debounce de 350 ms y el
 * AbortController que evita respuestas desordenadas.</p>
 */

import { useState } from "react"
import { Minus, Plus, Search, X } from "lucide-react"

import type { Medicamento } from "@/lib/api"
import { useMedicamentos } from "@/lib/use-medicamentos"
import { Button } from "@/components/ui/button"
import { TextInput } from "@/components/ui/field"
import { Cargando, ErrorBox, money, TOQUE_MINIMO, Vacio } from "@/lib/vista"
import { cn } from "@/lib/utils"

export type MedicamentoElegido = { medicamentoId: number; cantidad: number }

const BOTON_CANTIDAD = cn("w-11 shrink-0", TOQUE_MINIMO)

function detalle(medicamento: Medicamento): string {
  return [medicamento.presentacion, medicamento.laboratorio]
    .filter((valor): valor is string => Boolean(valor))
    .join(" · ")
}

export function SelectorMedicamentos({
  valor,
  onChange,
}: {
  valor: MedicamentoElegido[]
  onChange: (valor: MedicamentoElegido[]) => void
}) {
  const catalogo = useMedicamentos()
  // Si el profesional sigue buscando, un medicamento ya elegido puede salir de
  // la página actual. Guardamos su snapshot para no perder nombre ni precio.
  const [detalles, setDetalles] = useState<Map<number, Medicamento>>(new Map())

  const porId = new Map(catalogo.datos?.contenido.map((m) => [m.id, m]) ?? [])
  const resultados = (catalogo.datos?.contenido ?? [])
    .filter((m) => m.activo && m.stockActual > 0 && !valor.some((v) => v.medicamentoId === m.id))
    .slice(0, 8)

  function agregar(medicamento: Medicamento) {
    setDetalles((anteriores) => new Map(anteriores).set(medicamento.id, medicamento))
    onChange([...valor, { medicamentoId: medicamento.id, cantidad: 1 }])
  }

  function cambiarCantidad(medicamentoId: number, delta: number) {
    onChange(
      valor.map((v) =>
        v.medicamentoId === medicamentoId
          ? { ...v, cantidad: Math.max(1, Math.min(99, v.cantidad + delta)) }
          : v,
      ),
    )
  }

  function quitar(medicamentoId: number) {
    setDetalles((anteriores) => {
      const siguiente = new Map(anteriores)
      siguiente.delete(medicamentoId)
      return siguiente
    })
    onChange(valor.filter((v) => v.medicamentoId !== medicamentoId))
  }

  return (
    <div className="flex flex-col gap-2 text-sm">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-muted-foreground">Medicamentos usados (opcional)</span>
        {valor.length > 0 && (
          <span className="text-xs text-muted-foreground">
            {valor.reduce((total, v) => {
              const med = detalles.get(v.medicamentoId) ?? porId.get(v.medicamentoId)
              return total + (med ? med.precio * v.cantidad : 0)
            }, 0) > 0
              ? `Total informativo: ${money(valor.reduce((total, v) => {
                  const med = detalles.get(v.medicamentoId) ?? porId.get(v.medicamentoId)
                  return total + (med ? med.precio * v.cantidad : 0)
                }, 0))}`
              : ""}
          </span>
        )}
      </div>

      <div className="relative">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <TextInput
          value={catalogo.q}
          onChange={(e) => catalogo.cambiarQ(e.target.value)}
          placeholder="Buscar amoxicilina, vacuna, jeringa…"
          aria-label="Buscar medicamentos usados en la atención"
          className={cn("pl-10", TOQUE_MINIMO)}
        />
      </div>

      {catalogo.error && catalogo.datos === null ? (
        <ErrorBox error={catalogo.error} onReintentar={catalogo.recargar} />
      ) : catalogo.cargando && catalogo.datos === null ? (
        <Cargando texto="Cargando medicamentos" />
      ) : resultados.length === 0 ? (
        <Vacio
          texto={
            catalogo.q.trim() !== ""
              ? "No hay medicamentos activos con stock que coincidan con la búsqueda."
              : "No hay medicamentos con stock disponible en el catálogo."
          }
        />
      ) : (
        <ul className="flex flex-col gap-1.5 rounded-xl border border-border p-1.5">
          {resultados.map((medicamento) => (
            <li key={medicamento.id}>
              <Button
                type="button"
                variant="ghost"
                onClick={() => agregar(medicamento)}
                className="h-auto min-h-[44px] w-full justify-start whitespace-normal px-3 py-2 text-left"
                aria-label={`Agregar ${medicamento.nombre}, ${money(medicamento.precio)}`}
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{medicamento.nombre}</span>
                  <span className="block truncate text-xs text-muted-foreground">
                    {detalle(medicamento) || "Sin presentación"} · stock {medicamento.stockActual}
                  </span>
                </span>
                <span className="shrink-0 text-xs font-semibold tabular-nums">{money(medicamento.precio)}</span>
                <Plus className="size-4 shrink-0" aria-hidden />
              </Button>
            </li>
          ))}
          {catalogo.datos && catalogo.datos.totalElementos > catalogo.tamanio && (
            <li className="px-2 pb-1 text-xs text-muted-foreground">
              Mostrando los primeros resultados. Afino la búsqueda para ver el resto.
            </li>
          )}
        </ul>
      )}

      {valor.length > 0 && (
        <ul className="flex flex-col gap-2">
          {valor.map((elegido) => {
            const medicamento = detalles.get(elegido.medicamentoId) ?? porId.get(elegido.medicamentoId)
            return (
              <li
                key={elegido.medicamentoId}
                className="flex flex-col gap-2 rounded-xl border border-border p-2 sm:flex-row sm:items-center"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">
                    {medicamento?.nombre ?? `Medicamento #${elegido.medicamentoId}`}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {medicamento ? detalle(medicamento) || "Sin presentación" : ""}
                    {medicamento ? ` · ${money(medicamento.precio)} c/u` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    onClick={() => cambiarCantidad(elegido.medicamentoId, -1)}
                    disabled={elegido.cantidad <= 1}
                    className={BOTON_CANTIDAD}
                    aria-label="Restar uno"
                  >
                    <Minus className="size-4" aria-hidden />
                  </Button>
                  <span className="w-7 text-center tabular-nums" aria-label="Cantidad">
                    {elegido.cantidad}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    onClick={() => cambiarCantidad(elegido.medicamentoId, 1)}
                    disabled={elegido.cantidad >= 99}
                    className={BOTON_CANTIDAD}
                    aria-label="Sumar uno"
                  >
                    <Plus className="size-4" aria-hidden />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => quitar(elegido.medicamentoId)}
                    className={BOTON_CANTIDAD}
                    aria-label={`Quitar ${medicamento?.nombre ?? "medicamento"}`}
                  >
                    <X className="size-4" aria-hidden />
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <p className="text-xs text-muted-foreground">
        Los precios son informativos: se cobran en Caja con la comanda que se emite al guardar.
      </p>
    </div>
  )
}
