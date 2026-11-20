"use client"

/**
 * Catálogo de medicamentos y carga de stock.
 *
 * <p>La tabla densa se convierte en tarjetas apiladas en el celular (regla
 * 3.3 de AGENTS.md) y en escritorio se pagina en el servidor: el consultorio
 * puede crecer y no queremos traer miles de-insumos al navegador para mostrar
 * cincuenta.</p>
 *
 * <p>El precio de cada medicamento se fija acá (alta o edición) y se copia a
 * la comanda cuando el profesional lo usa en una atención. Cambiarlo no altera
 * comandas ya emitidas. El modal "Editar" corrige todos los datos del catálogo:
 * si se modifica el stock, exige una observación y deja un movimiento AJUSTE
 * (positivo o negativo) en el historial, que se consulta desde "Agregar stock",
 * junto con los egresos por ficha clínica.</p>
 */

import { useEffect, useState } from "react"
import { CheckCircle2, History, PackagePlus, Pencil, Plus, Search } from "lucide-react"

import { ApiError, medicamentos as apiMedicamentos, type Medicamento, type MovimientoStock, type Pagina } from "@/lib/api"
import { useMedicamentos } from "@/lib/use-medicamentos"
import { mensajeDeError } from "@/lib/use-api"
import { Field, Panel, TextArea, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { PaginadoServidor } from "@/components/ui/paginado-servidor"
import { Cargando, fechaCorta, money, Recurso, Semforo, soloDecimal, soloDigitos, TOQUE_MINIMO, Vacio } from "@/lib/vista"
import { cn } from "@/lib/utils"

const BOTON_PRIMARIO = cn(
  "inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:opacity-90 disabled:opacity-60",
  TOQUE_MINIMO,
)
const BOTON_SECUNDARIO = cn(
  "inline-flex items-center justify-center gap-1.5 rounded-md border border-input px-3 text-sm font-medium transition-colors hover:bg-muted disabled:opacity-60",
  TOQUE_MINIMO,
)

function textoError(error: unknown): string {
  return error instanceof ApiError ? mensajeDeError(error) ?? "No se pudo guardar" : "No se pudo guardar"
}

function parsearEntero(valor: string): number | null {
  if (valor.trim() === "") return null
  const numero = Number(valor)
  return Number.isInteger(numero) && numero >= 0 ? numero : null
}

/** Precio opcional: vacío significa 0 (no se cobra). Acepta coma decimal. */
function parsearPrecio(valor: string): number | null {
  if (valor.trim() === "") return 0
  const numero = Number(valor.replace(",", "."))
  return Number.isFinite(numero) && numero >= 0 ? numero : null
}

function EstadoStock({ medicamento }: { medicamento: Medicamento }) {
  if (medicamento.stockActual === 0) return <Semforo tono="rojo">Sin stock</Semforo>
  if (medicamento.bajoStock) {
    return <Semforo tono="ambar">Bajo mínimo · {medicamento.stockActual}/{medicamento.stockMinimo}</Semforo>
  }
  return <Semforo tono="verde">Disponible · {medicamento.stockActual}</Semforo>
}

function detalle(medicamento: Medicamento): string {
  return [medicamento.presentacion, medicamento.laboratorio, medicamento.principioActivo]
    .filter((valor): valor is string => Boolean(valor))
    .join(" · ")
}

export function Medicamentos() {
  const catalogo = useMedicamentos()
  const [modal, setModal] = useState<"nuevo" | "stock" | "editar" | null>(null)
  const [seleccionado, setSeleccionado] = useState<Medicamento | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  function abrirStock(medicamento: Medicamento) {
    setSeleccionado(medicamento)
    setModal("stock")
  }

  function abrirEditar(medicamento: Medicamento) {
    setSeleccionado(medicamento)
    setModal("editar")
  }

  return (
    <Panel
      title="Medicamentos e insumos"
      action={
        <button
          type="button"
          onClick={() => setModal("nuevo")}
          className={BOTON_PRIMARIO}
        >
          <Plus className="size-4" aria-hidden />
          <span className="hidden sm:inline">Nuevo medicamento</span>
          <span className="sm:hidden">Nuevo</span>
        </button>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">
          Catálogo y stock disponible. Cargar stock suma unidades y deja constancia en el historial del
          medicamento.
        </p>

        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <TextInput
            value={catalogo.q}
            onChange={(e) => catalogo.cambiarQ(e.target.value)}
            placeholder="Buscar por nombre, principio activo o laboratorio"
            aria-label="Buscar medicamentos"
            className={cn("pl-10", TOQUE_MINIMO)}
          />
        </div>

        {aviso && (
          <div
            role="status"
            className="flex items-start gap-2 rounded-xl border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm"
          >
            <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-700" aria-hidden />
            {aviso}
          </div>
        )}

        <Recurso
          estado={catalogo}
          textoCarga="Cargando medicamentos"
          onReintentar={catalogo.recargar}
        >
          {(pagina) =>
            pagina.contenido.length === 0 ? (
              <Vacio texto={catalogo.q ? "No hay medicamentos que coincidan con la búsqueda" : "Todavía no hay medicamentos cargados"} />
            ) : (
              <>
                {/* Tabla en desktop: scroll local para no desbordar la página. */}
                <div className="hidden overflow-x-auto rounded-xl border border-border md:block">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-muted text-xs uppercase tracking-wide text-muted-foreground">
                      <tr>
                        <th scope="col" className="px-3 py-2">Medicamento</th>
                        <th scope="col" className="px-3 py-2">Presentación / laboratorio</th>
                        <th scope="col" className="px-3 py-2 text-right">Precio</th>
                        <th scope="col" className="px-3 py-2">Stock</th>
                        <th scope="col" className="px-3 py-2 text-right">Acciones</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {pagina.contenido.map((medicamento) => (
                        <tr key={medicamento.id} className="transition-colors hover:bg-muted/40">
                          <td className="px-3 py-2 font-medium">{medicamento.nombre}</td>
                          <td className="px-3 py-2 text-muted-foreground">{detalle(medicamento) || "—"}</td>
                          <td className="px-3 py-2 text-right tabular-nums">{money(medicamento.precio)}</td>
                          <td className="px-3 py-2"><EstadoStock medicamento={medicamento} /></td>
                          <td className="px-3 py-2">
                            <div className="flex flex-wrap justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => abrirEditar(medicamento)}
                                className={BOTON_SECUNDARIO}
                                aria-label={`Editar ${medicamento.nombre}`}
                              >
                                <Pencil className="size-4" aria-hidden />
                                Editar
                              </button>
                              <button
                                type="button"
                                onClick={() => abrirStock(medicamento)}
                                className={BOTON_SECUNDARIO}
                                aria-label={`Agregar stock a ${medicamento.nombre}`}
                              >
                                <PackagePlus className="size-4" aria-hidden />
                                Agregar stock
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* En el celular, cada fila es una tarjeta apilada. */}
                <ul className="flex flex-col gap-3 md:hidden">
                  {pagina.contenido.map((medicamento) => (
                    <li key={medicamento.id} className="rounded-xl border border-border bg-background p-3">
                      <div className="flex items-start justify-between gap-2">
                        <p className="font-medium">{medicamento.nombre}</p>
                        <span className="shrink-0 font-semibold tabular-nums">{money(medicamento.precio)}</span>
                      </div>
                      <p className="mt-0.5 text-xs text-muted-foreground">{detalle(medicamento) || "Sin datos adicionales"}</p>
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                        <EstadoStock medicamento={medicamento} />
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            onClick={() => abrirEditar(medicamento)}
                            className={BOTON_SECUNDARIO}
                            aria-label={`Editar ${medicamento.nombre}`}
                          >
                            <Pencil className="size-4" aria-hidden />
                            Editar
                          </button>
                          <button
                            type="button"
                            onClick={() => abrirStock(medicamento)}
                            className={BOTON_SECUNDARIO}
                          >
                            <PackagePlus className="size-4" aria-hidden />
                            Agregar stock
                          </button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>

                <PaginadoServidor
                  pagina={catalogo.pagina}
                  tamanio={catalogo.tamanio}
                  totalElementos={pagina.totalElementos}
                  totalPaginas={pagina.totalPaginas}
                  onChange={(paginaNueva, tamanioNuevo) => {
                    catalogo.cambiarTamanio(tamanioNuevo)
                    catalogo.cambiarPagina(paginaNueva)
                  }}
                  deshabilitado={catalogo.cargando}
                />
              </>
            )
          }
        </Recurso>

        {catalogo.error && catalogo.datos !== null && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {mensajeDeError(catalogo.error)}
          </p>
        )}
      </div>

      <NuevoMedicamento
        open={modal === "nuevo"}
        onClose={() => setModal(null)}
        onGuardado={async (nombre) => {
          setModal(null)
          await catalogo.recargar()
          setAviso(`Se agregó "${nombre}" al catálogo.`)
        }}
      />

      {seleccionado && (
        <CargarStock
          medicamento={seleccionado}
          open={modal === "stock"}
          onClose={() => setModal(null)}
          onGuardado={async (cantidad) => {
            const nombre = seleccionado.nombre
            setModal(null)
            await catalogo.recargar()
            setAviso(`Se agregaron ${cantidad} unidades a "${nombre}".`)
          }}
        />
      )}

      {seleccionado && (
        <EditarMedicamento
          medicamento={seleccionado}
          open={modal === "editar"}
          onClose={() => setModal(null)}
          onGuardado={async (actualizado) => {
            const conAjuste = actualizado.stockActual !== seleccionado.stockActual
            setModal(null)
            await catalogo.recargar()
            setAviso(
              conAjuste
                ? `Se guardaron los cambios de "${actualizado.nombre}". Stock corregido a ${actualizado.stockActual} unidades.`
                : `Se guardaron los cambios de "${actualizado.nombre}".`,
            )
          }}
        />
      )}
    </Panel>
  )
}

function NuevoMedicamento({
  open,
  onClose,
  onGuardado,
}: {
  open: boolean
  onClose: () => void
  onGuardado: (nombre: string) => Promise<void>
}) {
  const [nombre, setNombre] = useState("")
  const [presentacion, setPresentacion] = useState("")
  const [principioActivo, setPrincipioActivo] = useState("")
  const [laboratorio, setLaboratorio] = useState("")
  const [stockInicial, setStockInicial] = useState("0")
  const [stockMinimo, setStockMinimo] = useState("0")
  const [precio, setPrecio] = useState("0")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const inicial = parsearEntero(stockInicial)
  const minimo = parsearEntero(stockMinimo)
  const precioUnitario = parsearPrecio(precio)

  async function guardar() {
    if (nombre.trim() === "" || inicial === null || minimo === null || precioUnitario === null) {
      setError("Completá el nombre y revisá los stocks y el precio (números mayores o iguales a cero).")
      return
    }
    setGuardando(true)
    setError(null)
    try {
      await apiMedicamentos.crear({
        nombre: nombre.trim(),
        presentacion: presentacion.trim() || null,
        principioActivo: principioActivo.trim() || null,
        laboratorio: laboratorio.trim() || null,
        precio: precioUnitario,
        stockInicial: inicial,
        stockMinimo: minimo,
      })
      setNombre("")
      setPresentacion("")
      setPrincipioActivo("")
      setLaboratorio("")
      setPrecio("0")
      setStockInicial("0")
      setStockMinimo("0")
      await onGuardado(nombre.trim())
    } catch (e) {
      setError(textoError(e))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Nuevo medicamento">
      <div className="flex flex-col gap-3">
        <Field label="Nombre">
          <TextInput
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            placeholder="Ej: Amoxicilina 500mg"
            maxLength={120}
            className={TOQUE_MINIMO}
          />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Presentación">
            <TextInput
              value={presentacion}
              onChange={(e) => setPresentacion(e.target.value)}
              placeholder="Comprimidos, frasco…"
              maxLength={120}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Laboratorio">
            <TextInput
              value={laboratorio}
              onChange={(e) => setLaboratorio(e.target.value)}
              maxLength={120}
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>
        <Field label="Principio activo">
          <TextInput
            value={principioActivo}
            onChange={(e) => setPrincipioActivo(e.target.value)}
            maxLength={120}
            className={TOQUE_MINIMO}
          />
        </Field>
        <Field label="Precio unitario">
          <TextInput
            inputMode="decimal"
            value={precio}
            onChange={(e) => setPrecio(soloDecimal(e.target.value))}
            placeholder="0"
            className={TOQUE_MINIMO}
          />
          <span className="text-xs text-muted-foreground">
            Se cobra junto con la consulta cuando se use en una atención. Podés cambiarlo después.
          </span>
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Stock inicial">
            <TextInput
              inputMode="numeric"
              value={stockInicial}
              onChange={(e) => setStockInicial(soloDigitos(e.target.value, 9))}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Stock mínimo (alerta)">
            <TextInput
              inputMode="numeric"
              value={stockMinimo}
              onChange={(e) => setStockMinimo(soloDigitos(e.target.value, 9))}
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>
        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button
            type="button"
            onClick={guardar}
            disabled={guardando || nombre.trim() === "" || inicial === null || minimo === null || precioUnitario === null}
            className={BOTON_PRIMARIO}
          >
            {guardando ? "Guardando…" : "Guardar"}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function CargarStock({
  medicamento,
  open,
  onClose,
  onGuardado,
}: {
  medicamento: Medicamento
  open: boolean
  onClose: () => void
  onGuardado: (cantidad: number) => Promise<void>
}) {
  const [cantidad, setCantidad] = useState("")
  const [observacion, setObservacion] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [historial, setHistorial] = useState<Pagina<MovimientoStock> | null>(null)
  const [cargandoHistorial, setCargandoHistorial] = useState(false)
  const [errorHistorial, setErrorHistorial] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    const control = new AbortController()
    setHistorial(null)
    setErrorHistorial(null)
    setCargandoHistorial(true)
    apiMedicamentos
      .movimientos(medicamento.id, 0, 5, control.signal)
      .then(setHistorial)
      .catch((e: unknown) => {
        if (control.signal.aborted) return
        setErrorHistorial(textoError(e))
      })
      .finally(() => {
        if (!control.signal.aborted) setCargandoHistorial(false)
      })
    return () => control.abort()
  }, [open, medicamento.id])

  const unidades = parsearEntero(cantidad)

  async function guardar() {
    if (unidades === null || unidades <= 0) {
      setError("Ingresá una cantidad mayor a cero.")
      return
    }
    setGuardando(true)
    setError(null)
    try {
      await apiMedicamentos.agregarStock(medicamento.id, {
        cantidad: unidades,
        observacion: observacion.trim() || null,
      })
      setCantidad("")
      setObservacion("")
      await onGuardado(unidades)
    } catch (e) {
      setError(textoError(e))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={`Agregar stock · ${medicamento.nombre}`}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">
          {detalle(medicamento) || "Sin datos adicionales"}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">Stock actual</span>
          <EstadoStock medicamento={medicamento} />
        </div>

        <div className="grid gap-3 sm:grid-cols-[10rem_minmax(0,1fr)]">
          <Field label="Cantidad a sumar">
            <TextInput
              inputMode="numeric"
              value={cantidad}
              onChange={(e) => setCantidad(soloDigitos(e.target.value, 9))}
              placeholder="0"
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Observación (opcional)">
            <TextInput
              value={observacion}
              onChange={(e) => setObservacion(e.target.value)}
              placeholder="Ej: reposición de farmacia"
              maxLength={500}
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="border-t border-border pt-3">
          <p className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            <History className="size-4" aria-hidden />
            Últimos movimientos
          </p>
          {cargandoHistorial && <Cargando texto="Cargando movimientos" />}
          {errorHistorial && (
            <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
              {errorHistorial}
            </p>
          )}
          {historial && historial.contenido.length === 0 && (
            <p className="py-2 text-sm text-muted-foreground">Todavía no hay movimientos registrados.</p>
          )}
          {historial && historial.contenido.length > 0 && (
            <ul className="max-h-40 divide-y divide-border overflow-y-auto rounded-xl border border-border">
              {historial.contenido.map((movimiento) => (
                <li key={movimiento.id} className="flex items-start justify-between gap-3 px-3 py-2 text-xs">
                  <div className="min-w-0">
                    <p className="font-medium">
                      {movimiento.tipo === "INGRESO"
                        ? "Ingreso"
                        : movimiento.tipo === "EGRESO_FICHA"
                          ? "Egreso por ficha"
                          : "Ajuste"}{" "}
                      · {movimiento.cantidad} u. · saldo {movimiento.stockResultante}
                    </p>
                    <p className="truncate text-muted-foreground">
                      {movimiento.observacion ?? "Sin observación"} · {movimiento.usuario}
                    </p>
                  </div>
                  <span className="shrink-0 whitespace-nowrap text-muted-foreground">
                    {fechaCorta(movimiento.fechaHora)} {movimiento.fechaHora.slice(11, 16)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button
            type="button"
            onClick={guardar}
            disabled={guardando || unidades === null || unidades <= 0}
            className={BOTON_PRIMARIO}
          >
            {guardando ? "Guardando…" : "Agregar stock"}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function EditarMedicamento({
  medicamento,
  open,
  onClose,
  onGuardado,
}: {
  medicamento: Medicamento
  open: boolean
  onClose: () => void
  onGuardado: (actualizado: Medicamento) => Promise<void>
}) {
  const [nombre, setNombre] = useState("")
  const [presentacion, setPresentacion] = useState("")
  const [principioActivo, setPrincipioActivo] = useState("")
  const [laboratorio, setLaboratorio] = useState("")
  const [precio, setPrecio] = useState("")
  const [stockMinimo, setStockMinimo] = useState("")
  const [stockActual, setStockActual] = useState("")
  const [stockAnterior, setStockAnterior] = useState(medicamento.stockActual)
  const [observacion, setObservacion] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    if (!open) return
    setNombre(medicamento.nombre)
    setPresentacion(medicamento.presentacion ?? "")
    setPrincipioActivo(medicamento.principioActivo ?? "")
    setLaboratorio(medicamento.laboratorio ?? "")
    setPrecio(String(medicamento.precio))
    setStockMinimo(String(medicamento.stockMinimo))
    setStockActual(String(medicamento.stockActual))
    // Se congela lo que vio el operador. Si otro cambia el stock mientras edita,
    // el backend lo detecta y responde 409 en vez de sobrescribirlo.
    setStockAnterior(medicamento.stockActual)
    setObservacion("")
    setError(null)
  }, [
    open,
    medicamento.id,
    medicamento.nombre,
    medicamento.presentacion,
    medicamento.principioActivo,
    medicamento.laboratorio,
    medicamento.precio,
    medicamento.stockActual,
    medicamento.stockMinimo,
  ])

  const minimo = parsearEntero(stockMinimo)
  const actual = parsearEntero(stockActual)
  const precioUnitario = parsearPrecio(precio)
  const hayAjuste = actual !== null && actual !== stockAnterior
  const observacionLista = observacion.trim()
  const puedeGuardar =
    nombre.trim() !== "" &&
    minimo !== null &&
    actual !== null &&
    precioUnitario !== null &&
    (!hayAjuste || observacionLista !== "")

  async function guardar() {
    if (!puedeGuardar || minimo === null || actual === null || precioUnitario === null) return
    setGuardando(true)
    setError(null)
    try {
      const actualizado = await apiMedicamentos.actualizar(medicamento.id, {
        nombre: nombre.trim(),
        presentacion: presentacion.trim() || null,
        principioActivo: principioActivo.trim() || null,
        laboratorio: laboratorio.trim() || null,
        precio: precioUnitario,
        stockMinimo: minimo,
        stockActual: actual,
        stockActualAnterior: stockAnterior,
        observacionStock: hayAjuste ? observacionLista : null,
      })
      await onGuardado(actualizado)
    } catch (e) {
      setError(textoError(e))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={`Editar · ${medicamento.nombre}`}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">
          Corregí los datos del catálogo. Para arreglar el stock, cargá el valor real y explicá el motivo.
        </p>
        <Field label="Nombre">
          <TextInput
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            maxLength={120}
            className={TOQUE_MINIMO}
          />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Presentación">
            <TextInput
              value={presentacion}
              onChange={(e) => setPresentacion(e.target.value)}
              placeholder="Comprimidos, frasco…"
              maxLength={120}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Laboratorio">
            <TextInput
              value={laboratorio}
              onChange={(e) => setLaboratorio(e.target.value)}
              maxLength={120}
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>
        <Field label="Principio activo">
          <TextInput
            value={principioActivo}
            onChange={(e) => setPrincipioActivo(e.target.value)}
            maxLength={120}
            className={TOQUE_MINIMO}
          />
        </Field>
        <Field label="Precio unitario">
          <TextInput
            inputMode="decimal"
            value={precio}
            onChange={(e) => setPrecio(soloDecimal(e.target.value))}
            className={TOQUE_MINIMO}
          />
          <span className="text-xs text-muted-foreground">
            Rige para las atenciones nuevas. Las comandas ya emitidas conservan el precio con el que se cobraron.
          </span>
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Stock mínimo (alerta)">
            <TextInput
              inputMode="numeric"
              value={stockMinimo}
              onChange={(e) => setStockMinimo(soloDigitos(e.target.value, 9))}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Stock actual">
            <TextInput
              inputMode="numeric"
              value={stockActual}
              onChange={(e) => setStockActual(soloDigitos(e.target.value, 9))}
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>
        {hayAjuste ? (
          <Field label="Observación del ajuste (obligatoria)">
            <TextArea
              value={observacion}
              onChange={(e) => setObservacion(e.target.value)}
              placeholder="Ej: faltaban 3 unidades en el conteo real"
              maxLength={500}
            />
            <span className="text-xs text-muted-foreground">
              Queda en el historial como ajuste{" "}
              {actual !== null && actual < stockAnterior ? "negativo" : "positivo"} con tu usuario.
            </span>
          </Field>
        ) : (
          <p className="text-xs text-muted-foreground">
            Stock sin cambios: no se registra ningún movimiento.
          </p>
        )}
        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className={BOTON_SECUNDARIO}>Cancelar</button>
          <button
            type="button"
            onClick={guardar}
            disabled={guardando || !puedeGuardar}
            className={BOTON_PRIMARIO}
          >
            {guardando ? "Guardando…" : "Guardar cambios"}
          </button>
        </div>
      </div>
    </Modal>
  )
}
