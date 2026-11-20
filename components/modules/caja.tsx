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
  DeudaMascota,
  DeudaTutor,
  EstadoComanda,
  Mascota,
  MetodoPago,
  TutorDelDia,
} from "@/lib/api"
import { useApi, type EstadoApi } from "@/lib/use-api"
import { Panel, Select } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { DatePicker } from "@/components/ui/date-picker"
import {
  Recurso,
  Semforo,
  TOQUE_MINIMO,
  fechaCorta,
  formatearDni,
  money,
} from "@/lib/vista"

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
  const [mascotaId, setMascotaId] = useState<number | null>(null)
  const [aCobrar, setACobrar] = useState<ComandaCobro | null>(null)

  // Con una mascota elegida, la fecha deja de mandar: se listan todas sus
  // comandas, de cualquier día. Es el atajo para encontrar su deuda sin
  // recorrer las jornadas una por una.
  const modoMascota = mascotaId !== null

  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())

  // La fecha viaja como clave: sin esto el hook pediria una sola vez y la pantalla
  // seguiria mostrando las comandas del dia con el que se abrio.
  const tutoresDelDia = useApi<TutorDelDia[]>(
    () => apiCaja.tutoresDelDia(fecha),
    true,
    fecha,
  )
  const comandas = useApi<ComandaCobro[]>(
    () =>
      mascotaId !== null
        ? apiComandas.listarPorMascota(
            mascotaId,
            filtro === "TODAS" ? undefined : filtro,
          )
        : apiComandas.listarDelDia(fecha, tutorId ?? undefined, filtro === "TODAS" ? undefined : filtro),
    true,
    mascotaId !== null ? `mascota|${mascotaId}|${filtro}` : `dia|${fecha}|${tutorId ?? ""}|${filtro}`,
  )
  // La deuda se consulta solo con un tutor o una mascota elegidos: sin selección
  // no hay a quién alertarle, y pedirla "para todos" no existe en la API.
  const deudaTutor = useApi<DeudaTutor>(
    () => apiTutores.deuda(tutorId as number),
    !modoMascota && tutorId !== null,
    tutorId ?? undefined,
  )
  const deudaMascota = useApi<DeudaMascota>(
    () => apiMascotas.deuda(mascotaId as number),
    modoMascota,
    mascotaId ?? undefined,
  )

  const tutorElegido = (tutoresDelDia.datos ?? []).find((t) => t.tutorId === tutorId) ?? null
  const mascotaElegida = (mascotas.datos ?? []).find((m) => m.id === mascotaId) ?? null

  async function refrescar() {
    await Promise.all([comandas.recargar(), tutoresDelDia.recargar()])
    if (modoMascota) await deudaMascota.recargar()
    else if (tutorId !== null) await deudaTutor.recargar()
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Caja" bodyClassName="p-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <DatePicker
            label="Jornada"
            value={fecha}
            disabled={modoMascota}
            onChange={(nueva) => {
              // Cambiar de dia descarta la seleccion de tutor: el de ayer no
              // tiene por que ser el de hoy, y dejarlo puesto mandaria un
              // filtro que el operador no eligio.
              setFecha(nueva)
              setTutorId(null)
            }}
          />

          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Mascota</span>
            <Select
              value={mascotaId ?? ""}
              onChange={(e) => {
                const nueva = e.target.value ? Number(e.target.value) : null
                setMascotaId(nueva)
                // Mascota y tutor son dos formas de acotar lo mismo: se elige
                // una u otra para que el filtro visible sea el que se aplica.
                if (nueva !== null) setTutorId(null)
              }}
              className={TOQUE_MINIMO}
            >
              <option value="">Todas las mascotas</option>
              {(mascotas.datos ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nombre}
                  {/* El nombre del tutor desambigua dos mascotas homónimas:
                      con el listado por mascota, elegir la equivocada muestra
                      la deuda de otra persona. */}
                  {m.tutores.length > 0
                    ? ` · ${m.tutores[0].nombre} ${m.tutores[0].apellido}`
                    : ""}
                  {m.activo ? "" : " · inactiva"}
                </option>
              ))}
            </Select>
            {mascotas.cargando && (mascotas.datos ?? []).length === 0 && (
              <span className="text-xs text-muted-foreground" role="status">
                Cargando mascotas…
              </span>
            )}
            {mascotas.error && (
              <span className="text-xs text-destructive" role="alert">
                No se pudieron cargar las mascotas.{" "}
                <button
                  type="button"
                  onClick={() => void mascotas.recargar()}
                  className="underline underline-offset-2"
                >
                  Reintentar
                </button>
              </span>
            )}
          </label>

          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">
              Tutor con actividad
            </span>
            <Select
              value={tutorId ?? ""}
              onChange={(e) => setTutorId(e.target.value ? Number(e.target.value) : null)}
              disabled={modoMascota}
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
            {!modoMascota && tutoresDelDia.cargando && (tutoresDelDia.datos ?? []).length === 0 && (
              <span className="text-xs text-muted-foreground" role="status">
                Cargando tutores de la jornada…
              </span>
            )}
            {!modoMascota && tutoresDelDia.error && (
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

          <label className="flex flex-col gap-1.5 text-sm">
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

        {modoMascota ? (
          <p className="mt-3 rounded-lg border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
            Mostrando todas las comandas de{" "}
            <strong className="text-foreground">
              {mascotaElegida?.nombre ?? "la mascota"}
            </strong>{" "}
            sin importar la fecha. La jornada y el tutor no aplican mientras haya una
            mascota elegida.
          </p>
        ) : (
          tutorElegido && (
            <p className="mt-3 text-xs text-muted-foreground">
              {tutorElegido.nombreCompleto} · DNI {formatearDni(tutorElegido.dni)} ·{" "}
              {tutorElegido.mascotas.length} mascota{tutorElegido.mascotas.length === 1 ? "" : "s"} en la
              jornada.
            </p>
          )
        )}
      </Panel>

      {/* Silencio ya solo puede significar "consultó y no debe". Mientras la
          llamada estaba en vuelo, o si fallaba, el banner no se veía y la
          pantalla mostraba un "nadie debe" que nadie había verificado. Es el
          mismo criterio que el de morosidad del tablero del Mostrador (TANDA 2):
          un falso "todo en orden" informado antes de tiempo es peor que dejar
          un hueco visible. */}
      {modoMascota ? (
        <AlertaDeuda
          titulo="MASCOTA CON DEUDA"
          nombre={deudaMascota.datos?.nombreMascota ?? mascotaElegida?.nombre ?? "la mascota"}
          detalle="La mascota no puede retirarse hasta saldar sus comandas."
          estado={deudaMascota}
          mostrarMascota={false}
        />
      ) : (
        tutorId !== null && (
          <AlertaDeuda
            titulo="TUTOR MOROSO"
            nombre={deudaTutor.datos?.nombreTutor ?? tutorElegido?.nombreCompleto ?? "este tutor"}
            detalle="Cada mascota queda retenida hasta saldar sus propias comandas."
            estado={deudaTutor}
          />
        )
      )}

      <Panel
        title={
          modoMascota
            ? `Comandas de ${mascotaElegida?.nombre ?? "la mascota"}`
            : `Comandas del ${fecha.split("-").reverse().join("/")}`
        }
      >
        <Recurso
          estado={comandas}
          textoCarga="Cargando comandas"
          textoVacio={
            modoMascota
              ? "La mascota no tiene comandas con ese filtro"
              : "No se emitió ninguna comanda en esa jornada"
          }
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
                        #{c.id} · {fechaCorta(c.fechaEmision)} {c.fechaEmision.slice(11, 16)} ·{" "}
                        {c.nombreTutor ?? "Urgencia sin datos"}
                        {c.nombreMascota ? ` · ${c.nombreMascota}` : ""}
                      </p>
                      <DetalleComanda comanda={c} />
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
                    {/* Urgencia atendida sin datos: el backend rechaza el cobro hasta completarlos. */}
                    {c.tutorId === null && c.estado !== "PAGADA" && c.estado !== "CANCELADA" && (
                      <p className="text-xs text-red-700">
                        Completá los datos del cliente y la mascota desde la Agenda para poder cobrar.
                      </p>
                    )}
                    {c.tutorId !== null && c.estado !== "PAGADA" && c.estado !== "CANCELADA" && (
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
 * Aviso de deuda compartido por tutor y mascota.
 *
 * El cargando y el error forman parte del aviso a proposito: un banner que solo
 * aparece cuando hay deuda convierte "todavia no consulté" en un "está al día"
 * falso. El detalle muestra la fecha de cada comanda para que el mostrador
 * sepa desde cuándo se debe.
 */
function AlertaDeuda({
  titulo,
  nombre,
  detalle,
  estado,
  mostrarMascota = true,
}: {
  titulo: string
  nombre: string
  detalle: string
  estado: Pick<EstadoApi<DeudaTutor | DeudaMascota>, "datos" | "cargando" | "error" | "recargar">
  mostrarMascota?: boolean
}) {
  if (estado.cargando && estado.datos === null) {
    return (
      <p
        className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground"
        role="status"
      >
        Consultando la deuda de {nombre}…
      </p>
    )
  }

  if (estado.error && estado.datos === null) {
    return (
      <div
        className="flex items-start gap-3 rounded-lg border border-amber-400 bg-amber-50 p-4"
        role="alert"
      >
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-amber-700" aria-hidden />
        <div>
          <p className="text-sm font-semibold text-amber-900">DEUDA SIN VERIFICAR</p>
          <p className="mt-1 text-sm text-amber-800">
            No se pudo consultar si {nombre} debe. No asumás que está al día: el egreso
            se valida recién en el checkout.
          </p>
          <button
            type="button"
            onClick={() => void estado.recargar()}
            className={`mt-2 rounded-md border border-amber-400 px-3 text-xs font-medium text-amber-900 transition-colors hover:bg-amber-100 ${TOQUE_MINIMO}`}
          >
            Reintentar
          </button>
        </div>
      </div>
    )
  }

  if (!estado.datos || !estado.datos.tieneDeuda) return null
  const deuda = estado.datos

  return (
    <div
      className="flex items-start gap-3 rounded-lg border border-red-300 bg-red-50 p-4"
      role="alert"
    >
      <AlertTriangle className="mt-0.5 size-5 shrink-0 text-red-700" aria-hidden />
      <div>
        <p className="text-sm font-semibold text-red-900">{titulo}</p>
        <p className="mt-1 text-sm text-red-800">
          {deuda.cantidadComandas} comanda
          {deuda.cantidadComandas === 1 ? "" : "s"} pendiente
          {deuda.cantidadComandas === 1 ? "" : "s"} por {money(deuda.totalDeuda)}. {detalle}
        </p>
        <ul className="mt-2 flex flex-col gap-0.5">
          {deuda.comandasPendientes.map((c) => (
            <li key={c.id} className="text-xs text-red-800">
              #{c.id} {ETIQUETA_CONCEPTO[c.concepto] ?? c.concepto}
              {mostrarMascota && c.nombreMascota ? ` de ${c.nombreMascota}` : ""} ·{" "}
              {money(c.saldoPendiente)} · {fechaCorta(c.fechaEmision)} · {c.diasDeAntiguedad} día
              {c.diasDeAntiguedad === 1 ? "" : "s"} de atraso
            </li>
          ))}
        </ul>
      </div>
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
  // La comanda puede ser de una jornada anterior. Quien cobra tiene que verlo
  // al lado del saldo, no descubrirlo después revisando el cierre.
  const esDeudaAnterior = comanda.fechaEmision.slice(0, 10) < hoyLocal()

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
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
            <span className="text-xs font-medium text-muted-foreground">Deuda del</span>
            <span className="inline-flex items-center gap-2 text-sm font-medium">
              {fechaCorta(comanda.fechaEmision)}
              {esDeudaAnterior && (
                <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-700">
                  de otro día
                </span>
              )}
            </span>
          </div>
          <DetalleComanda comanda={comanda} />
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground">Total</span>
            <span>{money(comanda.montoTotal)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground">Pagado</span>
            <span>{money(comanda.totalPagado)}</span>
          </div>
          {senaAplicada(comanda) > 0 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>Incluye seña</span>
              <span>{money(senaAplicada(comanda))}</span>
            </div>
          )}
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
            <span className={excede ?"font-medium text-red-700" :"font-medium"}>
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

/**
 * Detalle de la comanda (consulta y medicamentos usados) con el precio congelado al
 * emitir. Las comandas de guardería y las anteriores al detalle no tienen
 * líneas: ahí no se muestra nada y el total habla solo.
 */
function DetalleComanda({ comanda }: { comanda: ComandaCobro }) {
  if (!comanda.items || comanda.items.length <= 1) return null
  return (
    <ul className="mt-1 mb-2 flex flex-col gap-0.5 text-xs text-muted-foreground">
      {comanda.items.map((item, i) => (
        <li key={i} className="flex justify-between gap-3">
          <span className="min-w-0 truncate">
            {item.cantidad > 1 ? `${item.cantidad} × ` : ""}
            {item.descripcion}
          </span>
          <span className="shrink-0 tabular-nums">{money(item.subtotal)}</span>
        </li>
      ))}
    </ul>
  )
}

/** Seña ya imputada a la comanda (se registra como pago SENA al reservar). */
function senaAplicada(comanda: ComandaCobro): number {
  return (comanda.pagos ?? []).filter((p) => p.metodoPago === "SENA").reduce((total, p) => total + p.monto, 0)
}
