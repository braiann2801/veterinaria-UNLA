"use client"

/**
 * Balance: cierre de caja de un período.
 *
 * Antes esta pantalla sumaba los pagos semilla del store en memoria: mostraba
 * cifras que no eran las de la base. Ahora todo sale de `GET /caja/cierre`, que
 * agrega en MySQL y devuelve el arqueo por medio de pago, los saldos pendientes,
 * la serie diaria y el extracto de los pagos.
 *
 * El período arranca en hoy y se elige con un calendario de dos fechas. Al
 * cambiar, el hook descarta los datos viejos: la tabla muestra esqueletos en vez
 * de las cifras del período anterior, que se verían exactas y estarían mal.
 *
 * <b>Cobrado y facturado no son lo mismo.</b> El recaudado se fecha por el
 * momento del cobro; lo facturado, por la emisión de la comanda. Un pago de hoy
 * puede saldar una deuda de la semana pasada, y por eso los dos números pueden
 * diferir sin que haya un error: es justamente lo que el Balance tiene que
 * mostrar para saber cómo viene la caja.
 */

import { useEffect, useState, type ReactNode } from "react"
import type { LucideIcon } from "lucide-react"
import {
  AlertTriangle,
  ArrowLeftRight,
  Banknote,
  CreditCard,
  HandCoins,
  History,
  ListChecks,
  ReceiptText,
  TrendingUp,
  Wallet,
} from "lucide-react"

import { caja as apiCaja, hoyLocal } from "@/lib/api"
import type {
  CierreCaja,
  ConceptoCobro,
  MetodoPago,
  MovimientoCaja,
  SerieDiaCaja,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel } from "@/components/ui/field"
import { RangoFechasPicker, type Periodo } from "@/components/ui/rango-fechas"
import { Skeleton } from "@/components/ui/skeleton"
import { ErrorBox, money } from "@/lib/vista"
import { cn } from "@/lib/utils"

const METODOS: { clave: MetodoPago; etiqueta: string; icono: LucideIcon }[] = [
  { clave: "EFECTIVO", etiqueta: "Efectivo", icono: Banknote },
  { clave: "TRANSFERENCIA", etiqueta: "Transferencias", icono: ArrowLeftRight },
  { clave: "DEBITO", etiqueta: "Débito", icono: CreditCard },
  { clave: "CREDITO", etiqueta: "Crédito", icono: CreditCard },
  { clave: "SENA", etiqueta: "Seña", icono: HandCoins },
]

const ETIQUETA_METODO: Record<string, string> = {
  EFECTIVO: "Efectivo",
  TRANSFERENCIA: "Transferencia",
  DEBITO: "Débito",
  CREDITO: "Crédito",
  SENA: "Seña",
}

const ETIQUETA_CONCEPTO: Record<ConceptoCobro, string> = {
  CONSULTA_CLINICA: "Consulta clínica",
  GUARDERIA: "Guardería",
  OTRO: "Otros",
}

const ETIQUETA_ESTADO: Record<string, string> = {
  PENDIENTE: "Pendiente",
  PARCIAL: "Parcial",
  PAGADA: "Pagada",
  CANCELADA: "Cancelada",
}

const MESES_CORTOS = [
  "ene",
  "feb",
  "mar",
  "abr",
  "may",
  "jun",
  "jul",
  "ago",
  "sep",
  "oct",
  "nov",
  "dic",
]

/** Cada cuántos movimientos se agrega otro lote con "Ver más". */
const LOTE_MOVIMIENTOS = 50

/** Los pagos se guardan en hora local, así que la hora se corta del ISO sin pasar por `Date`. */
function horaDe(iso: string) {
  return iso.slice(11, 16)
}

/** `dd/mm` local del ISO, sin pasar por `Date` (la fecha ya viene en hora local). */
function fechaCorta(iso: string) {
  const [, mes, dia] = iso.slice(0, 10).split("-")
  return `${dia}/${mes}`
}

/** `dd/mm/aaaa` del ISO. */
function fechaLarga(iso: string) {
  const [anio, mes, dia] = iso.slice(0, 10).split("-")
  return `${dia}/${mes}/${anio}`
}

/** Cantidad de días del período, contando los dos extremos. */
function diasDelPeriodo(desde: string, hasta: string) {
  const [anioDesde, mesDesde, diaDesde] = desde.slice(0, 10).split("-").map(Number)
  const [anioHasta, mesHasta, diaHasta] = hasta.slice(0, 10).split("-").map(Number)
  const inicio = Date.UTC(anioDesde, mesDesde - 1, diaDesde)
  const fin = Date.UTC(anioHasta, mesHasta - 1, diaHasta)
  return Math.round((fin - inicio) / 86_400_000) + 1
}

/** Movimiento del extracto enriquecido con el criterio del período mirado. */
type MovimientoVista = MovimientoCaja & { deudaAnterior: boolean; fechaDeuda: string }

/**
 * Día al que pertenece lo cobrado: el del servicio (estadía o atención), que
 * puede ser anterior a la emisión cuando se carga después de ocurrido. Sin
 * servicio (comanda manual) vale la emisión; el último fallback, `fechaHora`,
 * cubre a un backend desalineado.
 */
function fechaDeLaDeuda(m: MovimientoCaja): string {
  return m.fechaServicio ?? (m.fechaEmision ?? m.fechaHora).slice(0, 10)
}

interface PuntoSerie {
  etiqueta: string
  total: number
  detalle: string
}

/**
 * Convierte la serie diaria en los puntos que se dibujan.
 *
 * Hasta dos meses, una barra por día. Más allá, una barra por mes: trescientos
 * sesenta y cinco bastones en un celular no comunican nada, y el Balance de un
 * año se lee por mes.
 */
function puntosDelGrafico(porDia: SerieDiaCaja[]): PuntoSerie[] {
  if (porDia.length <= 62) {
    return porDia.map((dia) => ({
      etiqueta: dia.fecha.slice(8, 10),
      total: dia.totalCobrado,
      detalle: `${fechaLarga(dia.fecha)} · ${money(dia.totalCobrado)} · ${dia.cantidadPagos} ${
        dia.cantidadPagos === 1 ? "pago" : "pagos"
      }`,
    }))
  }

  const porMes = new Map<string, { total: number; pagos: number }>()
  for (const dia of porDia) {
    const mes = dia.fecha.slice(0, 7)
    const acumulado = porMes.get(mes) ?? { total: 0, pagos: 0 }
    porMes.set(mes, {
      total: acumulado.total + dia.totalCobrado,
      pagos: acumulado.pagos + dia.cantidadPagos,
    })
  }
  return [...porMes].map(([mes, { total, pagos }]) => ({
    etiqueta: MESES_CORTOS[Number(mes.slice(5, 7)) - 1] ?? mes,
    total,
    detalle: `${mes} · ${money(total)} · ${pagos} pagos`,
  }))
}

export function Balance() {
  const [periodo, setPeriodo] = useState<Periodo>(() => {
    const hoy = hoyLocal()
    return { desde: hoy, hasta: hoy }
  })
  const esUnDia = periodo.desde === periodo.hasta
  const cierre = useApi<CierreCaja>(
    () => apiCaja.cierre(periodo.desde, periodo.hasta),
    true,
    `${periodo.desde}|${periodo.hasta}`,
  )

  // El extracto de un año puede traer miles de filas: se dibujan por lotes para
  // que el celular no intente armar toda la lista de una vez.
  const [limite, setLimite] = useState(LOTE_MOVIMIENTOS)
  useEffect(() => {
    setLimite(LOTE_MOVIMIENTOS)
  }, [periodo.desde, periodo.hasta])

  // Normaliza la respuesta antes de renderizar. `CierreCaja` declara los campos
  // como obligatorios, pero un backend desactualizado (o un deploy a medio
  // actualizar) puede no mandarlos todavía: sin este piso, `movimientos.length`
  // sobre `undefined` blanquea la pantalla. Un faltante se ve como cero/vacío en
  // vez de tirar la vista.
  const crudo = cierre.datos
  const datos = crudo
    ? {
        totalFacturado: crudo.totalFacturado ?? 0,
        totalCobrado: crudo.totalCobrado ?? 0,
        saldosPendientes: crudo.saldosPendientes ?? 0,
        cantidadPagos: crudo.cantidadPagos ?? 0,
        cantidadComandas: crudo.cantidadComandas ?? 0,
        totalesPorMetodo: crudo.totalesPorMetodo ?? {},
        porDia: crudo.porDia ?? [],
        movimientos: crudo.movimientos ?? [],
      }
    : null

  // Un servicio prestado antes del período que se está mirando significa que
  // este pago salda una deuda vieja. Se calcula acá, contra el inicio del
  // período, y no se guarda en el backend, para que siga siendo válido al
  // cambiar de rango.
  const movimientos: MovimientoVista[] = datos
    ? datos.movimientos.map((m) => {
        const fechaDeuda = fechaDeLaDeuda(m)
        return { ...m, fechaDeuda, deudaAnterior: fechaDeuda < periodo.desde }
      })
    : []
  const visibles = movimientos.slice(0, limite)

  const porcentajeCobranza =
    datos && datos.totalFacturado > 0
      ? Math.round((datos.totalCobrado / datos.totalFacturado) * 100)
      : null

  const puntos = datos && !esUnDia ? puntosDelGrafico(datos.porDia) : []
  const maximo = Math.max(1, ...puntos.map((punto) => punto.total))

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-lg font-semibold">
            {esUnDia ? "Balance del día" : "Balance del período"}
          </h1>
          <p className="text-sm text-muted-foreground">
            {esUnDia
              ? "Cierre de caja de la jornada elegida"
              : `${fechaLarga(periodo.desde)} → ${fechaLarga(periodo.hasta)} · ${diasDelPeriodo(
                  periodo.desde,
                  periodo.hasta,
                )} días`}
          </p>
        </div>
        <RangoFechasPicker
          className="w-full sm:w-80"
          label="Período"
          value={periodo}
          onChange={setPeriodo}
        />
      </div>

      {cierre.error && !datos ? (
        <ErrorBox error={cierre.error} onReintentar={cierre.recargar} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {datos ? (
              <>
                <Tarjeta
                  resaltada
                  icono={Wallet}
                  etiqueta="Total recaudado"
                  valor={money(datos.totalCobrado)}
                />
                <Tarjeta
                  icono={ReceiptText}
                  etiqueta="Facturado"
                  valor={money(datos.totalFacturado)}
                  detalle={
                    porcentajeCobranza === null ? "Sin facturación" : `${porcentajeCobranza}% cobrado`
                  }
                />
                <Tarjeta
                  icono={AlertTriangle}
                  etiqueta={esUnDia ? "Saldos pendientes del día" : "Por cobrar del período"}
                  valor={money(datos.saldosPendientes)}
                />
                <Tarjeta
                  icono={ListChecks}
                  etiqueta="Movimientos"
                  valor={String(datos.cantidadPagos)}
                  detalle={`${datos.cantidadComandas} ${
                    datos.cantidadComandas === 1 ? "comanda" : "comandas"
                  }`}
                />
              </>
            ) : (
              <>
                <TarjetaCargando />
                <TarjetaCargando />
                <TarjetaCargando />
                <TarjetaCargando />
              </>
            )}
          </div>

          {!esUnDia && (
            <Panel title="Cómo venimos">
              {datos ? (
                <>
                  <div className="flex items-end justify-between gap-3">
                    <p className="text-sm text-muted-foreground">
                      Recaudación día por día del período
                    </p>
                    <p className="text-sm text-muted-foreground">
                      Promedio:{" "}
                      <span className="font-medium text-foreground">
                        {money(datos.totalCobrado / Math.max(1, datos.porDia.length))}
                      </span>
                    </p>
                  </div>
                  {puntos.every((punto) => punto.total === 0) ? (
                    <p className="py-8 text-center text-sm text-muted-foreground">
                      No hubo cobros en este período.
                    </p>
                  ) : (
                    <div
                      role="img"
                      aria-label="Recaudación por día del período"
                      className="mt-4 flex items-end gap-1 overflow-x-auto pb-1"
                    >
                      {puntos.map((punto, i) => (
                        <div
                          key={`${punto.etiqueta}-${i}`}
                          className="flex min-w-[18px] flex-1 flex-col items-center gap-1.5"
                          title={punto.detalle}
                        >
                          <div className="flex h-28 w-full items-end">
                            <div
                              className="w-full rounded-t bg-primary/80 transition-all duration-200 hover:bg-primary"
                              style={{
                                height: `${Math.max(2, (punto.total / maximo) * 100)}%`,
                              }}
                            />
                          </div>
                          <span className="text-[10px] tabular-nums text-muted-foreground">
                            {punto.etiqueta}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                  <p className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
                    <TrendingUp className="size-3.5 shrink-0" aria-hidden />
                    Facturado y recaudado se fechan distinto: un cobro de hoy puede saldar una
                    comanda de otro día.
                  </p>
                </>
              ) : (
                <EsqueletoSerie />
              )}
            </Panel>
          )}

          <Panel title="Desglose por medio de pago">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {METODOS.map(({ clave, etiqueta, icono }) =>
                datos ? (
                  <Tarjeta
                    key={clave}
                    className="p-3"
                    icono={icono}
                    etiqueta={etiqueta}
                    valor={money(datos.totalesPorMetodo[clave] ?? 0)}
                  />
                ) : (
                  <TarjetaCargando key={clave} className="p-3" />
                ),
              )}
            </div>
            {datos && (
              <p className="mt-3 text-xs text-muted-foreground">
                {datos.cantidadComandas}{" "}
                {datos.cantidadComandas === 1 ? "comanda cobrada" : "comandas cobradas"} en{" "}
                {esUnDia ? "la jornada" : "el período"}.
              </p>
            )}
          </Panel>

          <Panel
            title={`Movimientos ${esUnDia ? "de la jornada" : "del período"}${
              datos ? ` (${datos.cantidadPagos})` : ""
            }`}
            bodyClassName="p-0"
          >
            {cierre.cargando && !datos ? (
              <EsqueletoMovimientos />
            ) : movimientos.length === 0 ? (
              <p className="px-4 py-10 text-center text-sm text-muted-foreground">
                No hubo cobros registrados {esUnDia ? "en esta jornada" : "en este período"}.
              </p>
            ) : (
              <>
                <ListaMovimientos movimientos={visibles} mostrarFecha={!esUnDia} />
                {movimientos.length > limite && (
                  <div className="border-t border-border/60 p-3">
                    <button
                      type="button"
                      onClick={() => setLimite((actual) => actual + LOTE_MOVIMIENTOS)}
                      className="min-h-[44px] w-full rounded-xl border border-border px-3 text-sm font-medium transition-colors hover:bg-muted"
                    >
                      Ver {Math.min(LOTE_MOVIMIENTOS, movimientos.length - limite)} más
                      <span className="ml-1 text-muted-foreground">
                        ({movimientos.length - limite} restantes)
                      </span>
                    </button>
                  </div>
                )}
              </>
            )}
          </Panel>
        </>
      )}
    </div>
  )
}

function Tarjeta({
  etiqueta,
  valor,
  detalle,
  icono: Icono,
  resaltada = false,
  className,
}: {
  etiqueta: string
  valor: string
  detalle?: string
  icono: LucideIcon
  resaltada?: boolean
  className?: string
}) {
  return (
    <div
      className={cn(
        "rounded-xl border border-border bg-card p-4 shadow-sm",
        resaltada && "border-primary/30 bg-primary/5",
        className,
      )}
    >
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <Icono className="size-4 shrink-0" aria-hidden />
        <span className="truncate">{etiqueta}</span>
      </div>
      <p className="mt-2 text-2xl font-semibold tabular-nums">{valor}</p>
      {detalle && <p className="mt-1 text-xs text-muted-foreground">{detalle}</p>}
    </div>
  )
}

function TarjetaCargando({ className }: { className?: string }) {
  return (
    <div className={cn("rounded-xl border border-border bg-card p-4 shadow-sm", className)}>
      <Skeleton className="h-4 w-24" />
      <Skeleton className="mt-3 h-7 w-28" />
    </div>
  )
}

function EsqueletoSerie() {
  return (
    <div role="status" aria-live="polite" className="mt-4 flex items-end gap-1">
      <span className="sr-only">Cargando recaudación del período</span>
      {Array.from({ length: 12 }).map((_, i) => (
        <Skeleton key={i} className="h-20 flex-1" />
      ))}
    </div>
  )
}

/**
 * Quién pagó, cuando no es el titular de la comanda: un cotutor puede saldar la
 * deuda de otro, y el arqueo tiene que poder decir quién entregó la plata.
 */
function PagadoPor({ m }: { m: MovimientoVista }) {
  if (!m.nombrePagador || m.nombrePagador === m.nombreTutor) return null
  return <span className="block text-xs text-muted-foreground">Pagó {m.nombrePagador}</span>
}

/**
 * Tabla del extracto.
 *
 * En celular se apilan tarjetas en vez de forzar el scroll horizontal: con siete
 * columnas la tabla no entra y el operador termina leyendo una fila partida. En
 * escritorio la tabla ordenada es más útil para el arqueo.
 */
function ListaMovimientos({
  movimientos,
  mostrarFecha,
}: {
  movimientos: MovimientoVista[]
  mostrarFecha: boolean
}) {
  return (
    <>
      <ul className="divide-y divide-border/60 md:hidden">
        {movimientos.map((m) => (
          <li key={m.id} className="flex flex-col gap-2 px-4 py-3">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{m.nombreTutor}</p>
                <PagadoPor m={m} />
                <p className="truncate text-xs text-muted-foreground">
                  {mostrarFecha && `${fechaCorta(m.fechaHora)} · `}
                  {m.nombreMascota ?? "Sin mascota"} · {horaDe(m.fechaHora)}
                </p>
              </div>
              <p className="shrink-0 font-semibold tabular-nums">{money(m.monto)}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <Etiqueta>{ETIQUETA_METODO[m.metodoPago] ?? m.metodoPago}</Etiqueta>
              <span className="text-muted-foreground">
                {ETIQUETA_CONCEPTO[m.concepto] ?? m.concepto}
              </span>
              <EstadoComanda estado={m.estadoComanda} />
              {m.deudaAnterior && <DeudaAnterior fecha={m.fechaDeuda} />}
            </div>
          </li>
        ))}
      </ul>

      <div className="hidden overflow-x-auto md:block">
        <table className="w-full min-w-[720px] text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              {mostrarFecha && <th className="px-4 py-3 font-medium">Fecha</th>}
              <th className="px-4 py-3 font-medium">Hora</th>
              <th className="px-4 py-3 font-medium">Tutor</th>
              <th className="px-4 py-3 font-medium">Mascota</th>
              <th className="px-4 py-3 font-medium">Concepto</th>
              <th className="px-4 py-3 font-medium">Medio</th>
              <th className="px-4 py-3 text-right font-medium">Monto</th>
              <th className="px-4 py-3 font-medium">Estado</th>
            </tr>
          </thead>
          <tbody>
            {movimientos.map((m) => (
              <tr key={m.id} className="border-b border-border/60 last:border-0">
                {mostrarFecha && (
                  <td className="px-4 py-3 tabular-nums text-muted-foreground">
                    {fechaCorta(m.fechaHora)}
                  </td>
                )}
                <td className="px-4 py-3 tabular-nums text-muted-foreground">
                  {horaDe(m.fechaHora)}
                </td>
                <td className="px-4 py-3">
                  <span className="font-medium">{m.nombreTutor}</span>
                  <PagadoPor m={m} />
                </td>
                <td className="px-4 py-3 text-muted-foreground">{m.nombreMascota ?? "—"}</td>
                <td className="px-4 py-3 text-muted-foreground">
                  {ETIQUETA_CONCEPTO[m.concepto] ?? m.concepto}
                </td>
                <td className="px-4 py-3">
                  <Etiqueta>{ETIQUETA_METODO[m.metodoPago] ?? m.metodoPago}</Etiqueta>
                </td>
                <td className="px-4 py-3 text-right font-semibold tabular-nums">{money(m.monto)}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-col items-start gap-1.5">
                    <EstadoComanda estado={m.estadoComanda} />
                    {m.deudaAnterior && <DeudaAnterior fecha={m.fechaDeuda} />}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

function Etiqueta({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
      {children}
    </span>
  )
}

/**
 * Marca de un pago que salda una deuda de otro día.
 *
 * Lleva el día de la deuda (el del servicio): "deuda anterior" sin el día obliga a
 * abrir la comanda para saber de cuándo venía; con la fecha, el arqueo se lee de
 * un vistazo. Color ámbar y no rojo: no es una alerta de morosidad viva, es un
 * cobro que se está regularizando.
 */
function DeudaAnterior({ fecha }: { fecha: string }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900">
      <History className="size-3" aria-hidden />
      Deuda anterior · {fechaCorta(fecha)}
    </span>
  )
}

function EstadoComanda({ estado }: { estado: string }) {
  const tono =
    estado === "PAGADA"
      ? "bg-emerald-100 text-emerald-900"
      : estado === "PARCIAL"
        ? "bg-amber-100 text-amber-900"
        : estado === "CANCELADA"
          ? "bg-muted text-muted-foreground"
          : "bg-red-100 text-red-900"

  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium", tono)}>
      {ETIQUETA_ESTADO[estado] ?? estado}
    </span>
  )
}

function EsqueletoMovimientos() {
  return (
    <div role="status" aria-live="polite" className="divide-y divide-border/60">
      <span className="sr-only">Cargando movimientos del período</span>
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 px-4 py-3">
          <Skeleton className="h-4 w-12" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-20" />
        </div>
      ))}
    </div>
  )
}
