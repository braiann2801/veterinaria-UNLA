"use client"

/**
 * TASK-006 / HOTFIX-UI: historial clinico por paciente.
 *
 * <p>Este modulo leia del store en memoria de `lib/store.tsx`, y eso lo dejaba sin
 * nada real que mostrar de dos maneras: las consultas clinicas no aparecian (las
 * escribe `FichaMedica` contra la API), y un animal recien cargado desde el
 * mostrador tampoco aparecia aca, porque nunca paso por el store. Ese segundo
 * punto era el que rompia el alta unificada: se cargaba el cliente, se decia que
 * ya estaba en el Historial, y no estaba.
 *
 * <p>Ahora el listado de pacientes y su historial salen de la API: el animal que
 * se carga en el mostrador aparece en esta pantalla sin ningun paso extra, porque
 * la pantalla lee la base y no una copia.</p>
 *
 * <p>El historial sale de `GET /consultas/mascota/{id}`, o sea de las fichas
 * reales. No hay forma de mostrar una consulta que se esta por escribir y no se
 * inventa ninguna: un paciente sin consultas dice que no tiene.</p>
 *
 * <p>El modulo se monta y desmonta al cambiar de pestana (ver `app/page.tsx`), asi
 * que el listado se vuelve a pedir cada vez que se entra. No hace falta ningun
 * evento global de "refrescar": no hay estado compartido que invalidar.</p>
 */

import { useEffect, useMemo, useState } from "react"
import { AlertTriangle, FileText, Search } from "lucide-react"

import { consultas as apiConsultas, mascotas as apiMascotas } from "@/lib/api"
import type { ConsultaMedica, ConductaMascota, ConductaObservada, Mascota } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, TextInput } from "@/components/ui/field"
import { Recurso, TOQUE_MINIMO } from "@/lib/vista"
import { Paginado } from "@/components/ui/paginado"
import { cn } from "@/lib/utils"

/**
 * Como leer la conducta de la mascota.
 *
 * <p>La columna es `Mascota.Conducta` (normal / grune / muerde / miedoso) y es
 * distinta de `ConsultaMedica.conductaObservada` (TRANQUILO / NERVIOSO /
 * REACTIVO / AGRESIVO). Esta segunda describe como se porto el animal en una
 * atencion concreta y se muestra mas abajo, junto a la ficha que la genero.</p>
 */
const CONDUCTA: Record<ConductaMascota, string> = {
  normal: "Sociable / normal",
  gruñe: "Gruñe",
  muerde: "Muerde — manejar con cuidado",
  miedoso: "Miedoso",
}

const CONDUCTA_OBSERVADA: Record<ConductaObservada, string> = {
  TRANQUILO: "Tranquilo",
  NERVIOSO: "Nervioso",
  REACTIVO: "Reactivo",
  AGRESIVO: "Agresivo",
}

function esAlerta(conducta: ConductaMascota | null) {
  return conducta === "muerde" || conducta === "gruñe"
}

export function Fichas({ initialMascotaId }: { initialMascotaId?: number | null }) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const [q, setQ] = useState("")
  const [seleccionId, setSeleccionId] = useState<number | null>(initialMascotaId ?? null)

  /**
   * El animal puede venir por deep-link desde la barra superior. Cuando cambia se
   * selecciona: sin esto, entrar por "ver ficha" desde la busqueda abriria el
   * historial del primer paciente de la lista, que no es el que se pidio.
   */
  useEffect(() => {
    if (initialMascotaId != null) setSeleccionId(initialMascotaId)
  }, [initialMascotaId])

  /**
   * El historial se pide solo con un paciente elegido: antes de eso no hay contra
   * que consultar, y `GET /consultas/mascota/{id}` no tiene listado global.
   */
  const historial = useApi<ConsultaMedica[]>(
    () => apiConsultas.porMascota(seleccionId!),
    seleccionId !== null,
    seleccionId ?? undefined,
  )

  const seleccionada = useMemo(
    () => (mascotas.datos ?? []).find((m) => m.id === seleccionId) ?? null,
    [mascotas.datos, seleccionId],
  )

  /**
   * Filtra por animal o por tutor. En recepcion el cliente se llama por el dueno,
   * no por la mascota, asi que buscar solo por nombre de animal deja fuera justo
   * al caso mas comun de la pantalla.
   */
  function filtrar(lista: Mascota[]): Mascota[] {
    const term = q.trim().toLowerCase()
    if (!term) return lista
    return lista.filter(
      (m) =>
        m.nombre.toLowerCase().includes(term) ||
        m.tutores.some((t) => `${t.nombre} ${t.apellido}`.toLowerCase().includes(term)),
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-lg font-semibold">Historial clínico</h1>
        <p className="text-sm text-muted-foreground">
          Consultas registradas de cada paciente
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,320px)_1fr]">
        <Panel title="Pacientes">
          <div className="relative mb-3">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden
            />
            <TextInput
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar por mascota o tutor"
              aria-label="Buscar paciente por mascota o tutor"
              className={`pl-9 ${TOQUE_MINIMO}`}
            />
          </div>

          <Recurso estado={mascotas} textoVacio="No hay pacientes cargados.">
            {(lista) => {
              const filtradas = filtrar(lista)
              return (
                <Paginado items={filtradas} clave="historial-pacientes" reiniciarCon={q}>
                {(pagina) => (
                <ul className="flex max-h-[420px] flex-col gap-1 overflow-y-auto">
                  {pagina.map((m) => {
                    const activo = m.id === seleccionId
                    return (
                      <li key={m.id}>
                        <button
                          onClick={() => setSeleccionId(m.id)}
                          aria-current={activo ? "true" : undefined}
                          className={cn(
                            "flex w-full items-center justify-between gap-2 rounded-md border px-3 py-2 text-left text-sm transition-colors",
                            TOQUE_MINIMO,
                            activo
                              ? "border-foreground bg-muted"
                              : "border-transparent hover:bg-muted",
                          )}
                        >
                          <span className="min-w-0">
                            <span className="block truncate font-medium">{m.nombre}</span>
                            <span className="block truncate text-xs text-muted-foreground">
                              {m.tutores.length > 0
                                ? m.tutores
                                    .map((t) => `${t.nombre} ${t.apellido}`)
                                    .join(" · ")
                                : "Sin tutor vinculado"}
                            </span>
                          </span>
                          {esAlerta(m.conducta) && (
                            <AlertTriangle className="size-4 shrink-0" aria-hidden />
                          )}
                        </button>
                      </li>
                    )
                  })}
                  {filtradas.length === 0 && (
                    <li className="px-1 py-2 text-sm text-muted-foreground">
                      Sin resultados para «{q.trim()}».
                    </li>
                  )}
                </ul>
                )}
                </Paginado>
              )
            }}
          </Recurso>
        </Panel>

        {seleccionada ? (
          <div className="flex flex-col gap-4">
            <Panel>
              <h2 className="text-base font-semibold">{seleccionada.nombre}</h2>
              <p className="text-sm text-muted-foreground">
                {seleccionada.especie.toLowerCase()}
                {seleccionada.raza ? ` · ${seleccionada.raza}` : ""}
                {seleccionada.sexo ? ` · ${seleccionada.sexo}` : ""}
                {seleccionada.pesoKg != null ? ` · ${seleccionada.pesoKg} kg` : ""}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">
                {seleccionada.tutores.length > 0
                  ? `Tutor${seleccionada.tutores.length > 1 ? "es" : ""}: ${seleccionada.tutores
                      .map(
                        (t) =>
                          `${t.nombre} ${t.apellido}${t.autorizadoRetiro ? "" : " (sin retiro)"}`,
                      )
                      .join(" · ")}`
                  : "Sin tutor vinculado"}
              </p>

              {/*
                La conducta de la mascota se muestra siempre, con alerta cuando es
                de manejo. Es el dato que el veterinario necesita antes de tocar al
                animal, asi que no se esconde cuando no hay consulta que lo
                respaldare.
              */}
              <div
                className={cn(
                  "mt-3 flex items-center gap-2 rounded-md border px-3 py-2 text-sm",
                  esAlerta(seleccionada.conducta)
                    ? "border-amber-400 bg-amber-50 font-medium text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-300"
                    : "border-border text-muted-foreground",
                )}
              >
                {esAlerta(seleccionada.conducta) && (
                  <AlertTriangle className="size-4 shrink-0" aria-hidden />
                )}
                Conducta:{" "}
                {seleccionada.conducta
                  ? CONDUCTA[seleccionada.conducta]
                  : "sin especificar"}
              </div>
            </Panel>

            <Panel title="Consultas">
              <Recurso
                estado={historial}
                textoVacio="Sin consultas registradas todavía."
              >
                {(consultas) => (
                  <Paginado items={consultas} clave="historial-consultas" reiniciarCon={seleccionId}>
                  {(pagina) => (
                  <ul className="flex flex-col gap-3">
                    {pagina.map((c) => (
                      <li key={c.id} className="rounded-md border border-border p-3">
                        <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                          <FileText className="size-3.5" aria-hidden />
                          {c.fechaAtencion}
                          <span>· {c.profesional.nombreCompleto}</span>
                          {c.alertaConducta && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 font-medium text-amber-900 dark:bg-amber-500/15 dark:text-amber-300">
                              <AlertTriangle className="size-3" aria-hidden />
                              Conducta en la atención:{" "}
                              {CONDUCTA_OBSERVADA[c.conductaObservada]}
                            </span>
                          )}
                        </div>
                        <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-2">
                          <Campo label="Motivo de consulta" valor={c.anamnesis} />
                          <Campo label="Diagnóstico" valor={c.diagnostico} />
                          <Campo label="Tratamiento" valor={c.tratamiento} />
                          <Campo
                            label="Conducta en la atención"
                            valor={CONDUCTA_OBSERVADA[c.conductaObservada]}
                          />
                        </dl>
                      </li>
                    ))}
                  </ul>
                  )}
                  </Paginado>
                )}
              </Recurso>
            </Panel>
          </div>
        ) : (
          <Panel>
            <p className="text-sm text-muted-foreground">
              Elegí un paciente de la lista para ver su historial.
            </p>
          </Panel>
        )}
      </div>
    </div>
  )
}

function Campo({ label, valor }: { label: string; valor: string | null }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd>{valor || "—"}</dd>
    </div>
  )
}