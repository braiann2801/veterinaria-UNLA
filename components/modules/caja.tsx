"use client"

/**
 * TASK-009: Caja del Día.
 *
 * Antes de esta fase el módulo no tenía día: `GET /comandas` exigía un tutor, así
 * que para cobrar había que acordarse de a quién se le cobra y elegirlo de entre
 * todo el padrón. Ahora arranca en hoy, propone a los tutores que efectivamente
 * pasaron por el consultorio o dejaron un perro en la guardería, y deja cambiar
 * de fecha para el corte de un día anterior.
 *
 * Dos cosas que esta pantalla no hace, a propósito:
 *
 * - **No calcula saldos.** El saldo que se muestra es el `saldoPendiente` del
 *   backend. Si el frontend restara por su cuenta, cualquier diferencia de
 *   redondeo haría que el mostrador cobre de más o de menos.
 * - **No decide quién puede pagar.** El selector ofrece a los tutores vinculados
 *   a la mascota de esa comanda, y el backend es el que acepta o rechaza. Si la
 *   UI filtrara, el operador vería un 400 sin explicación; si aceptara de más,
 *   un desconocido saldría con una comanda ajena saldada.
 */

import { useMemo, useState } from "react"
import { AlertTriangle, Ban, Receipt, Wallet } from "lucide-react"

import {
  caja as apiCaja,
  comandas as apiComandas,
  hoyLocal,
  mascotas as apiMascotas,
  tutores as apiTutores,
} from "@/lib/api"
import type {
  ComandaCobro,
  DeudaTutor,
  EstadoComanda,
  Mascota,
  MetodoPago,
  TutorDelDia,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, Select } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { DatePicker } from "@/components/ui/date-picker"
import { Recurso, Semforo, TOQUE_MINIMO, formatearDni, money } from "@/lib/vista"

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

const ETIQUETA_CONCEPTO: Record<string, string> = {
  CONSULTA_CLINICA: "Consulta clínica",
  GUARDERIA: "Guardería",
  OTRO: "Otros",
}

export function Caja() {
  // El backend también usa hoy por defecto cuando no le mandan fecha, pero el
  // input tiene que mostrar un valor: un select de fecha vacío no es una fecha.
  const [fecha, setFecha] = useState(hoyLocal())
  const [filtro, setFiltro] = useState<EstadoComanda | "TODAS">("TODAS")
  const [tutorId, setTutorId] = useState<number | null>(null)
  const [aCobrar, setACobrar] = useState<ComandaCobro | null>(null)

  // La fecha viaja como clave: sin esto el hook pediria una sola vez y la pantalla
  // seguiria mostrando las comandas del dia con el que se abrio.
  const tutoresDelDia = useApi<TutorDelDia[]>(
    () => apiCaja.tutoresDelDia(fecha),
    true,
    fecha,
  )
  const comandas = useApi<ComandaCobro[]>(
    () => apiComandas.listarDelDia(fecha, tutorId ?? undefined, filtro === "TODAS" ? undefined : filtro),
    true,
    `${fecha}|${tutorId ?? ""}|${filtro}`,
  )
  // La deuda se consulta solo con un tutor elegido: sin seleccion no hay a quien
  // alertarle, y pedirla "para todos" no existe en la API.
  const deuda = useApi<DeudaTutor>(
    () => apiTutores.deuda(tutorId as number),
    tutorId !== null,
    tutorId ?? undefined,
  )

  const tutorElegido = (tutoresDelDia.datos ?? []).find((t) => t.tutorId === tutorId) ?? null

  async function refrescar() {
    await Promise.all([comandas.recargar(), tutoresDelDia.recargar()])
    if (tutorId !== null) await deuda.recargar()
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Caja del día" bodyClassName="p-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
          <DatePicker
            className="flex-1"
            label="Jornada"
            value={fecha}
            onChange={(nueva) => {
              // Cambiar de dia descarta la seleccion de tutor: el de ayer no
              // tiene por que ser el de hoy, y dejarlo puesto mandaria un
              // filtro que el operador no eligio.
              setFecha(nueva)
              setTutorId(null)
            }}
          />

          <label className="flex flex-1 flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">
              Tutor con actividad
            </span>
            <Select
              value={tutorId ?? ""}
              onChange={(e) => setTutorId(e.target.value ? Number(e.target.value) : null)}
              className={TOQUE_MINIMO}
            >
              <option value="">Todos los tutores de la jornada</option>
              {(tutoresDelDia.datos ?? []).map((t) => (
                <option key={t.tutorId} value={t.tutorId}>
                  {t.nombreCompleto} · {t.mascotas.map((m) => m.nombreMascota).join(", ")}
                </option>
              ))}
            </Select>
            {/* Un dropdown vacío no dice si no hubo actividad o si falló la
                llamada. Sin estas tres líneas el operador solo veía el placeholder
                y tenía que adivinar, mientras que la lista de comandas de abajo sí
                informaba su error: dos controles de la misma pantalla con criterios
                distintos. */}
            {tutoresDelDia.cargando && (tutoresDelDia.datos ?? []).length === 0 && (
              <span className="text-xs text-muted-foreground" role="status">
                Cargando tutores de la jornada…
              </span>
            )}
            {tutoresDelDia.error && (
              <span className="text-xs text-destructive" role="alert">
                No se pudieron cargar los tutores de la jornada.{" "}
                <button
                  type="button"
                  onClick={() => void tutoresDelDia.recargar()}
                  className="underline underline-offset-2"
                >
                  Reintentar
                </button>
              </span>
            )}
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

        {tutorElegido && (
          <p className="mt-3 text-xs text-muted-foreground">
            {tutorElegido.nombreCompleto} · DNI {formatearDni(tutorElegido.dni)} ·{" "}
            {tutorElegido.mascotas.length} mascota{tutorElegido.mascotas.length === 1 ? "" : "s"} en la
            jornada.
          </p>
        )}
      </Panel>

      {/* Silencio ya solo puede significar "consultó y no debe". Mientras la
          llamada estaba en vuelo, o si fallaba, el banner no se veía y la
          pantalla mostraba un "nadie debe" que nadie había verificado. Es el
          mismo criterio que el de morosidad del tablero del Mostrador (TANDA 2):
          un falso "todo en orden" informado antes de tiempo es peor que dejar
          un hueco visible. */}
      {tutorId !== null && deuda.cargando && deuda.datos === null && (
        <p
          className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground"
          role="status"
        >
          Consultando la deuda de {tutorElegido?.nombreCompleto ?? "este tutor"}…
        </p>
      )}

      {tutorId !== null && deuda.error && deuda.datos === null && (
        <div
          className="flex items-start gap-3 rounded-lg border border-amber-400 bg-amber-50 p-4 dark:border-amber-500/40 dark:bg-amber-500/10"
          role="alert"
        >
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-amber-700 dark:text-amber-400" aria-hidden />
          <div>
            <p className="text-sm font-semibold text-amber-900 dark:text-amber-300">DEUDA SIN VERIFICAR</p>
            <p className="mt-1 text-sm text-amber-800 dark:text-amber-300">
              No se pudo consultar si {tutorElegido?.nombreCompleto ?? "este tutor"} debe.
              No asumás que está al día: el egreso se valida recién en el checkout.
            </p>
            <button
              type="button"
              onClick={() => void deuda.recargar()}
              className={`mt-2 rounded-md border border-amber-400 px-3 text-xs font-medium text-amber-900 transition-colors hover:bg-amber-100 dark:border-amber-500/40 dark:text-amber-300 dark:hover:bg-amber-500/15 ${TOQUE_MINIMO}`}
            >
              Reintentar
            </button>
          </div>
        </div>
      )}

      {tutorId !== null && deuda.datos && deuda.datos.tieneDeuda && (
        <div
          className="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-500/40 dark:bg-red-500/10"
          role="alert"
        >
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-red-700 dark:text-red-400" aria-hidden />
          <div>
            <p className="text-sm font-semibold text-red-900 dark:text-red-300">TUTOR MOROSO</p>
            <p className="mt-1 text-sm text-red-800 dark:text-red-300">
              {deuda.datos.nombreTutor} debe {money(deuda.datos.totalDeuda)} en{" "}
              {deuda.datos.cantidadComandas} comanda
              {deuda.datos.cantidadComandas === 1 ? "" : "s"} pendiente
              {deuda.datos.cantidadComandas === 1 ? "" : "s"}. Cada mascota queda retenida
              hasta saldar sus propias comandas.
            </p>
            <ul className="mt-2 flex flex-col gap-0.5">
              {deuda.datos.comandasPendientes.map((c) => (
                <li key={c.id} className="text-xs text-red-800 dark:text-red-300">
                  #{c.id} {ETIQUETA_CONCEPTO[c.concepto] ?? c.concepto}
                  {c.nombreMascota ? ` de ${c.nombreMascota}` : ""} ·{" "}
                  {money(c.saldoPendiente)} · {c.diasDeAntiguedad} día
                  {c.diasDeAntiguedad === 1 ? "" : "s"} de atraso
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <Panel title={`Comandas del ${fecha.split("-").reverse().join("/")}`}>
        <Recurso
          estado={comandas}
          textoCarga="Cargando comandas"
          textoVacio="No se emitió ninguna comanda en esa jornada"
          onReintentar={comandas.recargar}
        >
          {(lista) => (
            <ul className="flex flex-col divide-y divide-border">
              {lista.map((c) => (
                <li key={c.id} className="flex flex-col gap-2 py-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">
                        {ETIQUETA_CONCEPTO[c.concepto] ?? c.concepto}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        #{c.id} · {c.fechaEmision.slice(11, 16)} · {c.nombreTutor}
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
                    {/* Una comanda cancelada conserva su saldo en el backend, pero ya no
                        se cobra ni cuenta como deuda: mostrarlo como "pendiente" confunde. */}
                    {c.estado === "CANCELADA" ? (
                      <p className="text-sm text-muted-foreground">
                        Anulada · no se cobra{" "}
                        <span className="text-xs">({money(c.montoTotal)})</span>
                      </p>
                    ) : (
                      <p className="text-sm">
                        <span className="font-medium">{money(c.saldoPendiente)}</span>
                        <span className="text-xs text-muted-foreground">
                          {" "}
                          pendientes de {money(c.montoTotal)}
                        </span>
                      </p>
                    )}
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
                          {/* El pagador se muestra solo cuando se informó: los pagos
                              anteriores a TASK-009 no lo tienen y no se inventa. */}
                          {p.nombreTutorPagador ? ` · pagó ${p.nombreTutorPagador}` : ""}
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

      {aCobrar && (
        <ModalCobro
          comanda={aCobrar}
          abierto
          onCerrar={() => setACobrar(null)}
          onCobrado={async () => {
            setACobrar(null)
            await refrescar()
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
 *
 * El selector de "quién paga" se arma con los tutores **vinculados a la mascota
 * de esa comanda**, no con el padrón entero: la lista corta es la que el backend
 * va a aceptar, y ofrecer 300 personas para que 2 pasen el filtro es peor que no
 * ofrecer nada. El backend sigue siendo el que decide.
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
  const [pagadorId, setPagadorId] = useState<string>("")
  const [error, setError] = useState<string | null>(null)
  const [cobrando, setCobrando] = useState(false)

  // Los vinculos salen de la ficha de la mascota y no de la jornada del dia: la
  // comanda que se esta cobrando puede ser de una jornada vieja, y con "los
  // tutores de hoy" un cliente que volvio a pagar una deuda anterior veria la
  // lista de pagadores de otra familia.
  const mascota = useApi<Mascota>(
    () => apiMascotas.buscarPorId(comanda.mascotaId as number),
    comanda.mascotaId !== null,
    comanda.mascotaId ?? undefined,
  )

  const vinculados = useMemo(() => mascota.datos?.tutores ?? [], [mascota.datos])

  const total = lineas.reduce((acc, l) => acc + (Number(l.monto) || 0), 0)
  const restante = Math.max(0, comanda.saldoPendiente - total)
  const excede = total > comanda.saldoPendiente

  // Sin selección se manda undefined y el backend asume al deudor. Es el mismo
  // camino que el de siempre, no una excepcion: la mayoria de los cobros la paga
  // el titular.
  const pagador = pagadorId ? Number(pagadorId) : undefined

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
      await apiCaja.registrarPago(comanda.id, pagos, pagador)
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

        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Quién paga</span>
          <Select
            value={pagadorId}
            onChange={(e) => setPagadorId(e.target.value)}
            aria-label="Tutor que realiza el pago"
            className={TOQUE_MINIMO}
          >
            <option value="">
              {comanda.nombreTutor} (titular de la comanda)
            </option>
            {vinculados
              .filter((t) => t.id !== comanda.tutorId)
              .map((t) => (
                <option key={t.id} value={t.id}>
                  {`${t.nombre} ${t.apellido}`}
                  {t.autorizadoRetiro ? "" : " · no autorizado a retirar"}
                </option>
              ))}
          </Select>
          <span className="text-xs text-muted-foreground">
            Cualquier tutor vinculado a la mascota puede pagar. Que esté autorizado a
            retirarla es otro tema: el retiro se valida por separado, en el egreso.
          </span>
        </label>

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
            <span className={excede ? "font-medium text-red-700 dark:text-red-300" : "font-medium"}>
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
