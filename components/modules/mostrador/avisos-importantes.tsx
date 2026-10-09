"use client"

import { AlertTriangle } from "lucide-react"

import { Panel } from "@/components/ui/field"
import type { MascotaDelDia } from "@/lib/api"
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
 * Panel "Avisos importantes": manejo (conducta) y morosidad de la jornada.
 *
 * <p>La condición de error mira solo `jornada.error` a propósito. Los dos avisos
 * salen de `GET /egreso/pacientes-hoy`: el de manejo, de la columna `conducta`; el
 * de morosidad, de los tutores que vienen ahí. Si `turnos` o `reservas` fallan,
 * la grilla de abajo ya lo avisa por su cuenta y tapar estos avisos por eso
 * dejaría al operador sin ver un perro que muerde.</p>
 *
 * <p>Avisos de manejo: la conducta viene en la jornada, ya filtrada por los
 * tutores que realmente pasaron. Antes se cruzaban las semillas del store.</p>
 */
export function AvisosImportantes({
  jornada,
  deudas,
  fecha,
}: {
  jornada: EstadoApi<MascotaDelDia[]>
  deudas: DeudasDelDia
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

  return (
    <Panel title="Avisos importantes" className="lg:col-span-2">
      {jornada.error ? (
        <ErrorBox error={jornada.error} onReintentar={jornada.recargar} />
      ) : (
        <>
          {/* While the debt query is in flight, it must not say "no debt". */}
          {deudas.cargando && (
            <p className="mb-2 text-sm text-muted-foreground">
              Consultando la deuda de los tutores del {fecha}…
            </p>
          )}
          {avisosConducta.length === 0 && avisosDeuda.length === 0 && !deudas.cargando ? (
            <p className="text-sm text-muted-foreground">
              Sin avisos de manejo ni de morosidad para el {fecha}.
            </p>
          ) : (
            (avisosConducta.length > 0 || avisosDeuda.length > 0) && (
            <ul className="flex flex-col gap-2">
              {avisosConducta.map((m) => (
                <li
                  key={`c-${m.mascotaId}`}
                  className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
                >
                  <AlertTriangle className="h-4 w-4 shrink-0" />
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
                  <AlertTriangle className="h-4 w-4 shrink-0" />
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
            <p className="mt-2 rounded-md border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900">
              No se pudo consultar la deuda de {deudas.fallidos}{" "}
              {deudas.fallidos === 1 ? "tutor" : "tutores"}: puede faltar algún aviso de
              morosidad.{" "}
              <button type="button" className="underline" onClick={deudas.recargar}>
                Reintentar
              </button>
            </p>
          )}
        </>
      )}
    </Panel>
  )
}
