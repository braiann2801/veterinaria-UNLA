"use client"

/**
 * Catálogo de extras: lo que el profesional puede sumar a una atención (una
 * vacuna, una urgencia, un insumo). El Dueño fija el nombre y el precio; el
 * profesional solo elige cuáles usó, sin ver montos (regla 3 de AGENTS.md).
 *
 * <p>Un extra no se borra: se desactiva. Las comandas ya emitidas guardan su
 * copia del nombre y del precio, así que cambiar o desactivar un extra no toca
 * lo ya cobrado ni lo pendiente.</p>
 */

import { useState } from "react"
import { Pencil, Plus, Save, X } from "lucide-react"

import { ApiError, extras as apiExtras, type Extra } from "@/lib/api"
import { mensajeDeError, useApi } from "@/lib/use-api"
import { Field, Panel, TextInput } from "@/components/ui/field"
import { Recurso, TOQUE_MINIMO, money, soloDecimal } from "@/lib/vista"
import { cn } from "@/lib/utils"

function textoError(e: unknown) {
  return e instanceof ApiError ? mensajeDeError(e) ?? "No se pudo guardar" : "No se pudo guardar"
}

function parsearPrecio(valor: string): number | null {
  const n = Number(valor.trim())
  return valor.trim() !== "" && Number.isFinite(n) && n >= 0 ? n : null
}

const BOTON_PRIMARIO = cn(
  "inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60",
  TOQUE_MINIMO,
)
const BOTON_SECUNDARIO = cn(
  "inline-flex items-center justify-center gap-1.5 rounded-md border border-input px-3 text-sm font-medium hover:bg-muted disabled:opacity-60",
  TOQUE_MINIMO,
)

export function CatalogoExtras() {
  const catalogo = useApi<Extra[]>(() => apiExtras.listar())
  const [nombre, setNombre] = useState("")
  const [precio, setPrecio] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const precioNuevo = parsearPrecio(precio)

  async function agregar() {
    if (nombre.trim() === "" || precioNuevo === null) return
    setGuardando(true)
    setError(null)
    try {
      await apiExtras.crear({ nombre: nombre.trim(), precio: precioNuevo })
      setNombre("")
      setPrecio("")
      await catalogo.recargar()
    } catch (e) {
      setError(textoError(e))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Panel title="Extras de la atención">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">
          Vacunas, urgencias o insumos que el profesional suma al cerrar una atención. El precio se agrega a
          la comanda de la consulta. Cambiar un precio rige para las atenciones nuevas.
        </p>

        <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_10rem_auto] sm:items-end">
          <Field label="Nombre">
            <TextInput
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              placeholder="Ej: Vacuna antirrábica"
              maxLength={80}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Precio">
            <TextInput
              inputMode="decimal"
              value={precio}
              onChange={(e) => setPrecio(soloDecimal(e.target.value))}
              placeholder="0"
              className={TOQUE_MINIMO}
            />
          </Field>
          <button
            type="button"
            onClick={agregar}
            disabled={guardando || nombre.trim() === "" || precioNuevo === null}
            className={BOTON_PRIMARIO}
          >
            <Plus className="size-4" aria-hidden />
            {guardando ? "Agregando…" : "Agregar"}
          </button>
        </div>
        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <Recurso
          estado={catalogo}
          textoCarga="Cargando extras"
          textoVacio="Todavía no hay extras cargados"
          onReintentar={catalogo.recargar}
        >
          {(lista) => (
            <ul className="flex flex-col divide-y divide-border">
              {lista.map((extra) => (
                <FilaExtra key={extra.id} extra={extra} onCambio={catalogo.recargar} />
              ))}
            </ul>
          )}
        </Recurso>
      </div>
    </Panel>
  )
}

function FilaExtra({ extra, onCambio }: { extra: Extra; onCambio: () => Promise<void> | void }) {
  const [editando, setEditando] = useState(false)
  const [nombre, setNombre] = useState(extra.nombre)
  const [precio, setPrecio] = useState(String(extra.precio))
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const precioValido = parsearPrecio(precio)

  async function guardar(cambios: { nombre: string; precio: number; activo: boolean }) {
    setGuardando(true)
    setError(null)
    try {
      await apiExtras.actualizar(extra.id, cambios)
      setEditando(false)
      await onCambio()
    } catch (e) {
      setError(textoError(e))
    } finally {
      setGuardando(false)
    }
  }

  function cancelar() {
    setNombre(extra.nombre)
    setPrecio(String(extra.precio))
    setError(null)
    setEditando(false)
  }

  return (
    <li className="flex flex-col gap-2 py-3">
      {editando ? (
        <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_10rem_auto] sm:items-end">
          <Field label="Nombre">
            <TextInput value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={80} className={TOQUE_MINIMO} />
          </Field>
          <Field label="Precio">
            <TextInput
              inputMode="decimal"
              value={precio}
              onChange={(e) => setPrecio(soloDecimal(e.target.value))}
              className={TOQUE_MINIMO}
            />
          </Field>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() =>
                precioValido !== null && guardar({ nombre: nombre.trim(), precio: precioValido, activo: extra.activo })
              }
              disabled={guardando || nombre.trim() === "" || precioValido === null}
              className={BOTON_PRIMARIO}
            >
              <Save className="size-4" aria-hidden />
              Guardar
            </button>
            <button type="button" onClick={cancelar} className={BOTON_SECUNDARIO} aria-label="Cancelar edición">
              <X className="size-4" aria-hidden />
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className={cn("min-w-0", !extra.activo && "opacity-60")}>
            <p className="truncate text-sm font-medium">{extra.nombre}</p>
            <p className="text-xs text-muted-foreground">
              {money(extra.precio)}
              {!extra.activo && " · desactivado"}
            </p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setEditando(true)}
              className={BOTON_SECUNDARIO}
              aria-label={`Editar ${extra.nombre}`}
            >
              <Pencil className="size-4" aria-hidden />
            </button>
            <button
              type="button"
              onClick={() => guardar({ nombre: extra.nombre, precio: extra.precio, activo: !extra.activo })}
              disabled={guardando}
              className={BOTON_SECUNDARIO}
            >
              {extra.activo ? "Desactivar" : "Activar"}
            </button>
          </div>
        </div>
      )}
      {error && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 p-2 text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}
