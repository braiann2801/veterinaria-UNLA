"use client"

import { AlertTriangle } from "lucide-react"

import { Panel } from "@/components/ui/field"
import type { Medicamento, MascotaDelDia } from "@/lib/api"
import type { EstadoApi } from "@/lib/use-api"
import { ErrorBox, money } from "@/lib/vista"

import type { DeudasDelDia } from "./use-deudas-del-dia"

/**
 * Conductas que encienden el aviso de manejo.
 *
 * "Miedoso" y "normal" no avisan: no son un riesgo para el manejo. El backend
 * manda la grafía con eñe (`EntityMapper.conductaToApi`), que es la que comparan
 * estas constantes.
 */
const CONDUCTAS_DE_RIESGO = new Set(["muerde", "gruñe"])

/**
 * Panel "Avisos importantes": manejo (conducta), morosidad de la jornada y
 * medicamentos bajo el stock mínimo.
 *
 * <p>Cada fuente tiene su propio estado: un fallo parcial se dice y no tapa a
 * las demás. Los avisos de manejo y de morosidad salen de
 * `GET /egreso/pacientes-hoy`; el de stock, de
 * `GET /medicamentos/bajo-stock` (que ya excluye inactivos y mínimo 0).</p>
 *
 * <p>El mensaje de "sin avisos" solo aparece cuando ninguna consulta sigue en
 * vuelo y ninguna falló. Decir "no hay avisos" mientras el stock todavía se está
 * leyendo dejaría al operador sin enterarse de un medicamento agotado.</p>
 */
export function AvisosImportantes({
  jornada,
  deudas,
  stockBajo,
  fecha,
}: {
  jornada: EstadoApi<MascotaDelDia[]>
  deudas: DeudasDelDia
  stockBajo: EstadoApi<Medicamento[]>
  fecha: string
}) {
  const avisosConducta = (jornada.datos ?? []).filter(
    (m) => m.conducta && CONDUCTAS_DE_RIESGO.has(m.conducta),
  )
  const avisosDeuda = (jornada.datos ?? [])
    .flatMap((m) => m.tutores.map((t) => ({ tutorId: t.tutorId, nombreCompleto: t.nombreCompleto })))
    .filter((t, i, todos) => todos.findIndex((o) => o.tutorId === t.tutorId) === i)
    .map((t) => ({ ...t, deuda: deudas.porTutor[t.tutorId] }))
    .filter((t) => t.deuda?.tieneDeuda)
  const avisosStock = stockBajo.datos ?? []

  const consultando = deudas.cargando || stockBajo.cargando
  const sinAvisos =
    avisosConducta.length === 0 &&
    avisosDeuda.length === 0 &&
    avisosStock.length === 0 &&
    !consultando &&
    !jornada.error &&
    !stockBajo.error

  return (
    <Panel title="Avisos importantes" className="lg:col-span-2">
      {jornada.error && (
        <div className="mb-2">
          <ErrorBox error={jornada.error} onReintentar={jornada.recargar} />
        </div>
      )}
      {stockBajo.error && (
        <div className="mb-2">
          <ErrorBox error={stockBajo.error} onReintentar={stockBajo.recargar} />
        </div>
      )}

      {deudas.cargando && (
        <p className="mb-2 text-sm text-muted-foreground">
          Consultando la deuda de los tutores del {fecha}…
        </p>
      )}
      {stockBajo.cargando && (
        <p className="mb-2 text-sm text-muted-foreground">
          Consultando el stock de medicamentos…
        </p>
      )}

      {sinAvisos ? (
        <p className="text-sm text-muted-foreground">
          Sin avisos de manejo ni de morosidad para el {fecha}, ni medicamentos bajo el
          stock mínimo.
        </p>
      ) : (
        (avisosStock.length > 0 || avisosConducta.length > 0 || avisosDeuda.length > 0) && (
          <ul className="flex flex-col gap-2">
            {avisosStock.map((m) => {
              const nombre = m.presentacion ? `${m.nombre} (${m.presentacion})` : m.nombre
              const sinStock = m.stockActual === 0
              return (
                <li
                  key={`s-${m.id}`}
                  role="alert"
                  className={`flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${
                    sinStock
                      ? "border-red-500/40 bg-red-500/10"
                      : "border-amber-500/40 bg-amber-500/10"
                  }`}
                >
                  <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
                  <span>
                    <strong>{nombre}</strong>{" "}
                    {sinStock
                      ? `sin stock (mínimo ${m.stockMinimo}).`
                      : `quedan ${m.stockActual} unidades (mínimo ${m.stockMinimo}).`}
                  </span>
                </li>
              )
            })}
            {avisosConducta.map((m) => (
              <li
                key={`c-${m.mascotaId}`}
                className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
              >
                <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
                <span>
                  <strong>{m.nombreMascota}</strong>{" "}
                  {m.conducta === "muerde" ? "muerde" : "gruñe"} — manejar con cuidado.
                </span>
              </li>
            ))}
            {avisosDeuda.map((t) => (
              <li
                key={`d-${t.tutorId}`}
                className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
              >
                <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
                <span>
                  <strong>{t.nombreCompleto}</strong> viene con deuda previa de{" "}
                  {money(t.deuda?.totalDeuda ?? 0)}.
                </span>
              </li>
            ))}
          </ul>
        )
      )}

      {/*
        Un fallo parcial en la consulta de deudas se dice. La alternativa
        —mostrar "sin avisos" como si nada— convertiría un error de red en
        una afirmación falsa de que nadie debe nada, que es el peor
        resultado posible en la pantalla de morosidad (regla 2.5).
      */}
      {deudas.fallidos > 0 && (
        <p className="mt-2 rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-xs text-amber-800">
          No se pudo consultar la deuda de {deudas.fallidos}{" "}
          {deudas.fallidos === 1 ? "tutor" : "tutores"}: puede faltar algún aviso de
          morosidad.{" "}
          <button type="button" className="underline" onClick={deudas.recargar}>
            Reintentar
          </button>
        </p>
      )}
    </Panel>
  )
}
