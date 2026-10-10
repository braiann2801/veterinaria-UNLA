"use client"

/**
 * Balance del día: cierre de caja de una jornada.
 *
 * Antes esta pantalla sumaba los pagos semilla del store en memoria: mostraba
 * cifras que no eran las de la base. Ahora todo sale de `GET /caja/cierre`, que
 * agrega en MySQL y devuelve el arqueo por medio de pago, los saldos pendientes
 * y el extracto de los pagos.
 *
 * La fecha arranca en hoy y se puede cambiar con un calendario desplegable para
 * hacer el corte de un día anterior. Al cambiar, el hook descarta los datos
 * viejos: la tabla muestra esqueletos en vez de las cifras del día anterior, que
 * se verían exactas y estarían mal.
 */

import { useState, type ReactNode } from "react"
import type { LucideIcon } from "lucide-react"
import {
  AlertTriangle,
  ArrowLeftRight,
  Banknote,
  CreditCard,
  HandCoins,
  History,
  ListChecks,
  Wallet,
} from "lucide-react"

import { caja as apiCaja, hoyLocal } from "@/lib/api"
import type {
  CierreCaja,
  ConceptoCobro,
  MetodoPago,
  MovimientoCaja,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { DatePicker } from "@/components/ui/date-picker"
import { Panel } from "@/components/ui/field"
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

/** Los pagos se guardan en hora local, así que la hora se corta del ISO sin pasar por `Date`. */
function horaDe(iso: string) {
  return iso.slice(11, 16)
}

/** `dd/mm` local del ISO, sin pasar por `Date` (la fecha ya viene en hora local). */
function fechaCorta(iso: string) {
  const [, mes, dia] = iso.slice(0, 10).split("-")
  return `${dia}/${mes}`
}

/** Movimiento del extracto enriquecido con el criterio de la jornada mirada. */
type MovimientoVista = MovimientoCaja & { deudaAnterior: boolean }

export function Balance() {
  const [fecha, setFecha] = useState(hoyLocal())
  const cierre = useApi<CierreCaja>(() => apiCaja.cierre(fecha), true, fecha)

  // Normaliza la respuesta antes de renderizar. `CierreCaja` declara los campos
  // como obligatorios, pero un backend desactualizado (o un deploy a medio
  // actualizar) puede no mandarlos todavia: sin este piso, `movimientos.length`
  // sobre `undefined` blanquea la pantalla. Un faltante se ve como cero/vacio en
  // vez de tirar la vista.
  const crudo = cierre.datos
  const datos = crudo
    ? {
        totalCobrado: crudo.totalCobrado ?? 0,
        saldosPendientes: crudo.saldosPendientes ?? 0,
        cantidadPagos: crudo.cantidadPagos ?? 0,
        cantidadComandas: crudo.cantidadComandas ?? 0,
        totalesPorMetodo: crudo.totalesPorMetodo ?? {},
        movimientos: crudo.movimientos ?? [],
      }
    : null

  // Una comanda emitida antes de la jornada que se está mirando significa que
  // este pago salda una deuda vieja. Se calcula acá, contra la fecha elegida,
  // y no se guarda en el backend, para que siga siendo válido al cambiar de
  // jornada. El fallback a `fechaHora` cubre a un backend desalineado.
  const movimientos: MovimientoVista[] = datos
    ? datos.movimientos.map((m) => ({
        ...m,
        deudaAnterior: (m.fechaEmision ?? m.fechaHora).slice(0, 10) < fecha,
      }))
    : []

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-lg font-semibold">Balance del día</h1>
          <p className="text-sm text-muted-foreground">Cierre de caja de la jornada elegida</p>
        </div>
        <DatePicker
          className="w-full sm:w-72"
          label="Jornada"
          value={fecha}
          onChange={setFecha}
        />
      </div>

      {cierre.error && !datos ? (
        <ErrorBox error={cierre.error} onReintentar={cierre.recargar} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
            {datos ? (
              <>
                <Tarjeta
                  resaltada
                  icono={Wallet}
                  etiqueta="Total recaudado"
                  valor={money(datos.totalCobrado)}
                />
                <Tarjeta
                  icono={AlertTriangle}
                  etiqueta="Saldos pendientes del día"
                  valor={money(datos.saldosPendientes)}
                />
                <Tarjeta
                  className="col-span-2 lg:col-span-1"
                  icono={ListChecks}
                  etiqueta="Cantidad de movimientos"
                  valor={String(datos.cantidadPagos)}
                />
              </>
            ) : (
              <>
                <TarjetaCargando />
                <TarjetaCargando />
                <TarjetaCargando className="col-span-2 lg:col-span-1" />
              </>
            )}
          </div>

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
                {datos.cantidadComandas === 1 ? "comanda cobrada" : "comandas cobradas"} en la
                jornada.
              </p>
            )}
          </Panel>

          <Panel
            title={`Movimientos de la jornada${datos ? ` (${datos.cantidadPagos})` : ""}`}
            bodyClassName="p-0"
          >
            {cierre.cargando && !datos ? (
              <EsqueletoMovimientos />
            ) : movimientos.length === 0 ? (
              <p className="px-4 py-10 text-center text-sm text-muted-foreground">
                No hubo cobros registrados en esta jornada.
              </p>
            ) : (
              <ListaMovimientos movimientos={movimientos} />
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
  icono: Icono,
  resaltada = false,
  className,
}: {
  etiqueta: string
  valor: string
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

/**
 * Tabla del extracto.
 *
 * En celular se apilan tarjetas en vez de forzar el scroll horizontal: con siete
 * columnas la tabla no entra y el operador termina leyendo una fila partida. En
 * escritorio la tabla ordenada es más útil para el arqueo.
 */
function ListaMovimientos({ movimientos }: { movimientos: MovimientoVista[] }) {
  return (
    <>
      <ul className="divide-y divide-border/60 md:hidden">
        {movimientos.map((m) => (
          <li key={m.id} className="flex flex-col gap-2 px-4 py-3">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{m.nombreTutor}</p>
                <p className="truncate text-xs text-muted-foreground">
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
              {m.deudaAnterior && (
                <DeudaAnterior fechaEmision={m.fechaEmision ?? m.fechaHora} />
              )}
            </div>
          </li>
        ))}
      </ul>

      <div className="hidden overflow-x-auto md:block">
        <table className="w-full min-w-[720px] text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
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
                <td className="px-4 py-3 tabular-nums text-muted-foreground">
                  {horaDe(m.fechaHora)}
                </td>
                <td className="px-4 py-3 font-medium">{m.nombreTutor}</td>
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
                    {m.deudaAnterior && (
                      <DeudaAnterior fechaEmision={m.fechaEmision ?? m.fechaHora} />
                    )}
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
 * Lleva la fecha de emisión de la comanda: "deuda anterior" sin el día obliga a
 * abrir la comanda para saber de cuándo venía; con la fecha, el arqueo se lee de
 * un vistazo. Color ámbar y no rojo: no es una alerta de morosidad viva, es un
 * cobro que se está regularizando hoy.
 */
function DeudaAnterior({ fechaEmision }: { fechaEmision: string }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900 dark:bg-amber-500/15 dark:text-amber-300">
      <History className="size-3" aria-hidden />
      Deuda anterior · {fechaCorta(fechaEmision)}
    </span>
  )
}

function EstadoComanda({ estado }: { estado: string }) {
  const tono =
    estado === "PAGADA"
      ? "bg-emerald-100 text-emerald-900 dark:bg-emerald-500/15 dark:text-emerald-300"
      : estado === "PARCIAL"
        ? "bg-amber-100 text-amber-900 dark:bg-amber-500/15 dark:text-amber-300"
        : estado === "CANCELADA"
          ? "bg-muted text-muted-foreground"
          : "bg-red-100 text-red-900 dark:bg-red-500/15 dark:text-red-300"

  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium", tono)}>
      {ETIQUETA_ESTADO[estado] ?? estado}
    </span>
  )
}

function EsqueletoMovimientos() {
  return (
    <div role="status" aria-live="polite" className="divide-y divide-border/60">
      <span className="sr-only">Cargando movimientos de la jornada</span>
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
