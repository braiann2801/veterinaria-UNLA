"use client"

/**
 * TASK-006: Caja y Recepción contra la API real.
 *
 * Lista las comandas pendientes del tutor, permite el cobro mixto (varios medios
 * en una sola operación) y muestra la alerta visual de "TUTOR MOROSO" (regla
 * 2.5 de AGENTS.md).
 *
 * El modal de cobro no calcula el saldo: usa `saldoPendiente` del backend. Si el
 * frontend restara por su cuenta, cualquier diferencia de redondeo mostraría un
 * saldo que no existe y el mostrador cobraría de más o de menos.
 */

import { useState } from "react"
import { AlertTriangle, Ban, Receipt, Wallet } from "lucide-react"

import { caja as apiCaja, comandas as apiComandas, tutores as apiTutores } from "@/lib/api"
import type { ComandaCobro, DeudaTutor, EstadoComanda, MetodoPago, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, Select } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { Recurso, Semforo, TOQUE_MINIMO, money } from "@/lib/vista"

const METODOS: { valor: MetodoPago; etiqueta: string }[] = [
  { valor: "EFECTIVO", etiqueta: "Efectivo" },
  { valor: "TRANSFERENCIA", etiqueta: "Transferencia" },
  { valor: "DEBITO", etiqueta: "Débito" },
  { valor: "CREDITO", etiqueta: "Crédito" },
  { valor: "SENA", etiqueta: "Seña previa" },
]

const ETIQUETA_ESTADO: Record<EstadoComanda, string> = {
  PENDIENTE: "Pendiente",
  PARCIAL: "Parcial",
  PAGADA: "Pagada",
  CANCELADA: "Cancelada",
}

export function Caja() {
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())
  const [tutorId, setTutorId] = useState<number | null>(null)
  const [filtro, setFiltro] = useState<EstadoComanda | "TODAS">("TODAS")
  const [aCobrar, setACobrar] = useState<ComandaCobro | null>(null)

  // El endpoint de comandas exige un tutorId: no existe un listado global por
  // diseño, así que la vista arranca sin selección en vez de traer todo.
  const comandas = useApi<ComandaCobro[]>(
    () => apiComandas.listarPorTutor(tutorId as number, filtro === "TODAS" ? undefined : filtro),
    tutorId !== null,
  )
  const deuda = useApi<DeudaTutor>(
    () => apiTutores.deuda(tutorId as number),
    tutorId !== null,
  )

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Recepción">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="flex flex-1 flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Tutor</span>
            <Select
              value={tutorId ?? ""}
              onChange={(e) => setTutorId(e.target.value ? Number(e.target.value) : null)}
              className={TOQUE_MINIMO}
            >
              <option value="">Elegí un tutor…</option>
              {(tutores.datos ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombreCompleto} · DNI {t.dni}
                </option>
              ))}
            </Select>
          </label>

          <label className="flex flex-1 flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Estado</span>
            <Select
              value={filtro}
              onChange={(e) => setFiltro(e.target.value as EstadoComanda | "TODAS")}
              className={TOQUE_MINIMO}
            >
              <option value="TODAS">Todas</option>
              <option value="PENDIENTE">Pendientes</option>
              <option value="PARCIAL">Parciales</option>
              <option value="PAGADA">Pagadas</option>
              <option value="CANCELADA">Canceladas</option>
            </Select>
          </label>
        </div>
      </Panel>

      {tutorId !== null && deuda.datos && deuda.datos.tieneDeuda && (
        <div
          className="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4"
          role="alert"
        >
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-red-700" aria-hidden />
          <div>
            <p className="text-sm font-semibold text-red-900">TUTOR MOROSO</p>
            <p className="mt-1 text-sm text-red-800">
              {deuda.datos.nombreTutor} debe {money(deuda.datos.totalDeuda)} en{" "}
              {deuda.datos.cantidadComandas} comanda
              {deuda.datos.cantidadComandas === 1 ? "" : "s"} pendiente
              {deuda.datos.cantidadComandas === 1 ? "" : "s"}. El egreso queda bloqueado
              hasta regularizar.
            </p>
            <ul className="mt-2 flex flex-col gap-0.5">
              {deuda.datos.comandasPendientes.map((c) => (
                <li key={c.id} className="text-xs text-red-800">
                  #{c.id} {c.concepto} · {money(c.saldoPendiente)} ·{" "}
                  {c.diasDeAntiguedad} día{c.diasDeAntiguedad === 1 ? "" : "s"} de atraso
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {tutorId !== null ? (
        <Panel title="Comandas">
          <Recurso
            estado={comandas}
            textoCarga="Cargando comandas"
            textoVacio="Este tutor no tiene comandas con ese estado"
          >
            {(lista) => (
              <ul className="flex flex-col divide-y divide-border">
                {lista.map((c) => (
                  <li key={c.id} className="flex flex-col gap-2 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium">
                          {c.concepto === "CONSULTA_CLINICA"
                            ? "Consulta clínica"
                            : c.concepto === "GUARDERIA"
                              ? "Guardería"
                              : "Otros"}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          #{c.id} · {c.fechaEmision.slice(0, 10)}
                          {c.nombreMascota ? ` · ${c.nombreMascota}` : ""}
                        </p>
                      </div>
                      <Semforo
                        tono={
                          c.estado === "PAGADA"
                            ? "verde"
                            : c.estado === "CANCELADA"
                              ? "ambar"
                              : "rojo"
                        }
                      >
                        {ETIQUETA_ESTADO[c.estado]}
                      </Semforo>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm">
                        <span className="font-medium">{money(c.saldoPendiente)}</span>
                        <span className="text-xs text-muted-foreground">
                          {" "}
                          pendientes de {money(c.montoTotal)}
                        </span>
                      </p>
                      {c.estado !== "PAGADA" && c.estado !== "CANCELADA" && (
                        <button
                          onClick={() => setACobrar(c)}
                          className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
                        >
                          <Wallet className="size-3.5" aria-hidden />
                          Cobrar
                        </button>
                      )}
                    </div>

                    {c.pagos.length > 0 && (
                      <ul className="flex flex-col gap-0.5 border-t border-border pt-2">
                        {c.pagos.map((p) => (
                          <li key={p.id} className="text-xs text-muted-foreground">
                            {METODOS.find((m) => m.valor === p.metodoPago)?.etiqueta ?? p.metodoPago} ·{" "}
                            {money(p.monto)} · {p.fechaHora.slice(0, 10)}
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Recurso>
        </Panel>
      ) : (
        <Panel title="Comandas">
          <p className="text-sm text-muted-foreground">
            Elegí un tutor para ver sus comandas y registrar el cobro.
          </p>
        </Panel>
      )}

      {aCobrar && (
        <ModalCobro
          comanda={aCobrar}
          abierto
          onCerrar={() => setACobrar(null)}
          onCobrado={async () => {
            setACobrar(null)
            await Promise.all([comandas.recargar(), deuda.recargar()])
          }}
        />
      )}
    </div>
  )
}

/**
 * Modal de cobro mixto.
 *
 * Varios medios en una misma operación: es el caso real del mostrador (parte en
 * efectivo y el resto por transferencia) y lo que el backend soporta con un
 * array de pagos. El saldo restante se muestra en vivo para que quien cobra vea
 * si llegó a cero antes de confirmar.
 */
function ModalCobro({
  comanda,
  abierto,
  onCerrar,
  onCobrado,
}: {
  comanda: ComandaCobro
  abierto: boolean
  onCerrar: () => void
  onCobrado: () => void | Promise<void>
}) {
  const [lineas, setLineas] = useState<{ metodoPago: MetodoPago; monto: string }[]>([
    { metodoPago: "EFECTIVO", monto: String(comanda.saldoPendiente) },
  ])
  const [error, setError] = useState<string | null>(null)
  const [cobrando, setCobrando] = useState(false)

  const total = lineas.reduce((acc, l) => acc + (Number(l.monto) || 0), 0)
  const restante = Math.max(0, comanda.saldoPendiente - total)
  const excede = total > comanda.saldoPendiente

  async function cobrar() {
    const pagos = lineas
      .map((l) => ({ metodoPago: l.metodoPago, monto: Number(l.monto) || 0 }))
      .filter((p) => p.monto > 0)

    if (pagos.length === 0) {
      setError("Cargá al menos un monto mayor a cero.")
      return
    }
    if (excede) {
      setError(
        `El total (${money(total)}) supera el saldo pendiente (${money(comanda.saldoPendiente)}).`,
      )
      return
    }

    setCobrando(true)
    setError(null)
    try {
      await apiCaja.registrarPago(comanda.id, pagos)
      await onCobrado()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo registrar el cobro")
    } finally {
      setCobrando(false)
    }
  }

  function agregarLinea() {
    setLineas((prev) => [...prev, { metodoPago: "TRANSFERENCIA", monto: String(restante) }])
  }

  return (
    <Modal open={abierto} onClose={onCerrar} title={`Cobrar comanda #${comanda.id}`}>
      <div className="flex flex-col gap-4 p-4">
        <div className="rounded-lg border border-border p-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground">Total</span>
            <span>{money(comanda.montoTotal)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground">Pagado</span>
            <span>{money(comanda.totalPagado)}</span>
          </div>
          <div className="flex items-center justify-between font-medium">
            <span>Saldo pendiente</span>
            <span>{money(comanda.saldoPendiente)}</span>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          {lineas.map((linea, i) => (
            <div key={i} className="flex gap-2">
              <Select
                value={linea.metodoPago}
                onChange={(e) =>
                  setLineas((prev) =>
                    prev.map((l, j) =>
                      j === i ? { ...l, metodoPago: e.target.value as MetodoPago } : l,
                    ),
                  )
                }
                className={TOQUE_MINIMO}
                aria-label="Medio de pago"
              >
                {METODOS.map((m) => (
                  <option key={m.valor} value={m.valor}>
                    {m.etiqueta}
                  </option>
                ))}
              </Select>
              <input
                type="number"
                inputMode="numeric"
                min={0}
                step={0.01}
                value={linea.monto}
                onChange={(e) =>
                  setLineas((prev) =>
                    prev.map((l, j) => (j === i ? { ...l, monto: e.target.value } : l)),
                  )
                }
                aria-label={`Monto del pago ${i + 1}`}
                className={`w-full rounded-md border border-input bg-background px-3 text-sm ${TOQUE_MINIMO}`}
              />
              {lineas.length > 1 && (
                <button
                  onClick={() => setLineas((prev) => prev.filter((_, j) => j !== i))}
                  aria-label={`Quitar pago ${i + 1}`}
                  className={`shrink-0 rounded-md px-3 text-xs text-destructive transition-colors hover:bg-destructive/10 ${TOQUE_MINIMO}`}
                >
                  <Ban className="size-4" aria-hidden />
                </button>
              )}
            </div>
          ))}
        </div>

        <button
          onClick={agregarLinea}
          className={`rounded-md border border-border text-sm font-medium transition-colors hover:bg-muted ${TOQUE_MINIMO}`}
        >
          Agregar medio de pago
        </button>

        <div className="rounded-lg border border-border p-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground">Total a cobrar</span>
            <span className={excede ? "font-medium text-red-700" : "font-medium"}>
              {money(total)}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground">Quedaría debiendo</span>
            <span className="font-medium">{money(restante)}</span>
          </div>
        </div>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <button
          onClick={cobrar}
          disabled={cobrando || total <= 0 || excede}
          className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
        >
          <Receipt className="size-4" aria-hidden />
          {cobrando ? "Registrando…" : "Confirmar cobro"}
        </button>
      </div>
    </Modal>
  )
}