"use client"

/**
 * TASK-006: padrón de tutores y mascotas contra la API real.
 *
 * El switch "Autorizado a retirar" (regla 2.4 de AGENTS.md) viaja al backend
 * como parte del vínculo, no como un campo suelto de la mascota: la autorización
 * pertenece a la tupla Tutor-Mascota y una misma mascota puede tener un tutor
 * que retira y un cotutor que no.
 */

import { useRef, useState } from "react"
import { Link2, PawPrint, ShieldCheck, ShieldOff, UserPlus } from "lucide-react"

import { mascotas as apiMascotas, tutores as apiTutores } from "@/lib/api"
import type { Mascota, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, Select, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { Recurso, TOQUE_MINIMO } from "@/lib/vista"
import { AltaMascota } from "@/components/modules/alta-mascota"

export function Padron() {
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const [seleccion, setSeleccion] = useState<Tutor | null>(null)
  const [openAlta, setOpenAlta] = useState(false)
  const refAltaMascota = useRef<HTMLDivElement | null>(null)

  /**
   * Refresco unico de ambos listados.
   *
   * <p>Se recarga tambien el tutor abierto, porque los modales muestran la copia
   * del tutor guardada en el estado: sin esto, un vinculo recien hecho no se
   * veria hasta reabrir el modal.</p>
   */
  async function refrescar(tutorId?: number) {
    await Promise.all([tutores.recargar(), mascotas.recargar()])
    if (tutorId !== undefined) {
      setSeleccion(await apiTutores.buscarPorId(tutorId))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel
        title="Tutores"
        action={
          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() =>
                refAltaMascota.current?.scrollIntoView({ behavior: "smooth", block: "start" })
              }
              className={`inline-flex items-center gap-2 rounded-md border border-border px-3 text-xs font-medium transition-colors hover:bg-muted ${TOQUE_MINIMO}`}
            >
              <PawPrint className="size-3.5" aria-hidden />
              Nueva mascota
            </button>
            <button
              onClick={() => setOpenAlta(true)}
              className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
            >
              <UserPlus className="size-3.5" aria-hidden />
              Nuevo tutor
            </button>
          </div>
        }
      >
        <Recurso
          estado={tutores}
          textoCarga="Cargando tutores"
          textoVacio="Todavía no hay tutores cargados"
        >
          {(lista) => (
            <ul className="flex flex-col divide-y divide-border">
              {lista.map((t) => (
                <li key={t.id}>
                  <button
                    onClick={() => setSeleccion(t)}
                    className="flex w-full flex-col gap-1 py-3 text-left transition-colors hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{t.nombreCompleto}</p>
                      <p className="text-xs text-muted-foreground">
                        DNI {t.dni}
                        {t.telefono ? ` · ${t.telefono}` : ""}
                      </p>
                    </div>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {t.mascotas.length} mascota{t.mascotas.length === 1 ? "" : "s"}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Recurso>
      </Panel>

      <Panel title="Mascotas">
        <Recurso
          estado={mascotas}
          textoCarga="Cargando mascotas"
          textoVacio="Todavía no hay mascotas cargadas"
        >
          {(lista) => (
            <ul className="flex flex-col divide-y divide-border">
              {lista.map((m) => (
                <li key={m.id} className="flex flex-col gap-2 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{m.nombre}</p>
                      <p className="text-xs text-muted-foreground">
                        {[m.especie, m.raza].filter(Boolean).join(" · ")}
                      </p>
                    </div>
                    {m.conducta && m.conducta !== "normal" && (
                      <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-900">
                        {m.conducta}
                      </span>
                    )}
                  </div>
                  <ul className="flex flex-wrap gap-1.5">
                    {m.tutores.map((t) => (
                      <li
                        key={t.id}
                        className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-xs"
                      >
                        {t.autorizadoRetiro ? (
                          <ShieldCheck className="size-3 text-emerald-600" aria-hidden />
                        ) : (
                          <ShieldOff className="size-3 text-muted-foreground" aria-hidden />
                        )}
                        {t.nombre} {t.apellido}
                      </li>
                    ))}
                    {m.tutores.length === 0 && (
                      <li className="text-xs text-muted-foreground">Sin tutores vinculados</li>
                    )}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </Recurso>
      </Panel>

      {/* Alta de mascota. El select de tutores se alimenta del listado real, asi
          que si todavia no hay tutores el formulario ofrece guardarla sin vinculo
          en lugar de fallar. */}
      <div ref={refAltaMascota}>
        <AltaMascota tutores={tutores.datos ?? []} onCreada={() => refrescar()} />
      </div>

      {seleccion && (
        <ModalAltaTutor
          abierto={Boolean(seleccion)}
          onCerrar={() => setSeleccion(null)}
          tutor={seleccion}
          onCambio={() => refrescar(seleccion.id)}
        />
      )}

      <ModalAltaTutorNuevo
        abierto={openAlta}
        onCerrar={() => setOpenAlta(false)}
        onCreado={async () => {
          setOpenAlta(false)
          await refrescar()
        }}
      />
    </div>
  )
}

/** Alta simple de tutor. Vincular mascotas es un paso aparte. */
function ModalAltaTutorNuevo({
  abierto,
  onCerrar,
  onCreado,
}: {
  abierto: boolean
  onCerrar: () => void
  onCreado: () => void | Promise<void>
}) {
  const [nombre, setNombre] = useState("")
  const [apellido, setApellido] = useState("")
  const [dni, setDni] = useState("")
  const [telefono, setTelefono] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await apiTutores.crear({
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        dni: dni.trim(),
        telefono: telefono.trim() || undefined,
      })
      setNombre("")
      setApellido("")
      setDni("")
      setTelefono("")
      await onCreado()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo crear el tutor")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open={abierto} onClose={onCerrar} title="Nuevo tutor">
      <div className="flex flex-col gap-3 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Nombre" valor={nombre} onChange={setNombre} />
          <Campo etiqueta="Apellido" valor={apellido} onChange={setApellido} />
        </div>
        <Campo etiqueta="DNI" valor={dni} onChange={setDni} inputMode="numeric" />
        <Campo etiqueta="Teléfono" valor={telefono} onChange={setTelefono} inputMode="tel" />
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <button
          onClick={guardar}
          disabled={guardando || !nombre.trim() || !apellido.trim() || !dni.trim()}
          className={`rounded-md bg-primary text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
        >
          {guardando ? "Guardando…" : "Guardar tutor"}
        </button>
      </div>
    </Modal>
  )
}

/** Alta de tutor con vínculos y switch de autorización de retiro. */
function ModalAltaTutor({
  abierto,
  onCerrar,
  tutor,
  onCambio,
}: {
  abierto: boolean
  onCerrar: () => void
  tutor: Tutor
  onCambio: () => void | Promise<void>
}) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar(), abierto)
  const [seleccion, setSeleccion] = useState<number | null>(null)
  const [autorizado, setAutorizado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function vincular() {
    if (!seleccion) {
      setError("Elegí una mascota para vincular.")
      return
    }
    setGuardando(true)
    setError(null)
    try {
      await apiTutores.vincular(tutor.id, seleccion, autorizado)
      setSeleccion(null)
      setAutorizado(false)
      await onCambio()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo vincular")
    } finally {
      setGuardando(false)
    }
  }

  async function desvincular(mascotaId: number) {
    setGuardando(true)
    setError(null)
    try {
      await apiTutores.desvincular(tutor.id, mascotaId)
      await onCambio()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo desvincular")
    } finally {
      setGuardando(false)
    }
  }

  async function alternar(mascotaId: number, actual: boolean) {
    setGuardando(true)
    setError(null)
    try {
      await apiTutores.vincular(tutor.id, mascotaId, !actual)
      await onCambio()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo cambiar la autorización")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open={abierto} onClose={onCerrar} title={tutor.nombreCompleto}>
      <div className="flex flex-col gap-4 p-4">
        <section>
          <h3 className="mb-2 text-xs font-medium text-muted-foreground">Mascotas vinculadas</h3>
          <Recurso estado={mascotas} textoCarga="Cargando mascotas">
            {(lista) =>
              lista.length === 0 ? (
                <p className="text-sm text-muted-foreground">No hay mascotas cargadas.</p>
              ) : (
                <ul className="flex flex-col divide-y divide-border">
                  {lista.map((m) => {
                    const vinculo = tutor.mascotas.find((v) => v.id === m.id)
                    return (
                      <li key={m.id} className="flex items-center justify-between gap-3 py-2.5">
                        <div className="min-w-0">
                          <p className="truncate text-sm">{m.nombre}</p>
                          <p className="text-xs text-muted-foreground">
                            {vinculo
                              ? vinculo.autorizadoRetiro
                                ? "Autorizado a retirar"
                                : "Cotutor: no puede retirar"
                              : "Sin vincular"}
                          </p>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          {vinculo && (
                            <>
                              <button
                                onClick={() => alternar(m.id, vinculo.autorizadoRetiro)}
                                disabled={guardando}
                                role="switch"
                                aria-checked={vinculo.autorizadoRetiro}
                                aria-label={`Autorizado a retirar a ${m.nombre}`}
                                className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors disabled:opacity-60 ${TOQUE_MINIMO} ${
                                  vinculo.autorizadoRetiro
                                    ? "border-emerald-300 bg-emerald-50 text-emerald-900"
                                    : "border-border bg-muted text-muted-foreground"
                                }`}
                              >
                                {vinculo.autorizadoRetiro ? "Autorizado" : "No autorizado"}
                              </button>
                              <button
                                onClick={() => desvincular(m.id)}
                                disabled={guardando}
                                className={`rounded-md px-2 text-xs text-destructive transition-colors hover:bg-destructive/10 disabled:opacity-60 ${TOQUE_MINIMO}`}
                              >
                                Quitar
                              </button>
                            </>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )
            }
          </Recurso>
        </section>

        <section className="flex flex-col gap-3 border-t border-border pt-4">
          <h3 className="text-xs font-medium text-muted-foreground">Vincular otra mascota</h3>
          <Select
            value={seleccion ?? ""}
            onChange={(e) => setSeleccion(e.target.value ? Number(e.target.value) : null)}
            className={TOQUE_MINIMO}
          >
            <option value="">Elegí una mascota…</option>
            {(mascotas.datos ?? [])
              .filter((m) => !tutor.mascotas.some((v) => v.id === m.id))
              .map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nombre}
                </option>
              ))}
          </Select>

          <label className="flex min-h-[44px] items-center gap-3 rounded-md border border-border px-3">
            <input
              type="checkbox"
              checked={autorizado}
              onChange={(e) => setAutorizado(e.target.checked)}
              className="size-4"
            />
            <span className="text-sm">
              Autorizado a retirar
              <span className="block text-xs text-muted-foreground">
                Si está apagado, el tutor queda como cotutor y no puede llevarse a la mascota.
              </span>
            </span>
          </label>

          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}

          <button
            onClick={vincular}
            disabled={guardando || !seleccion}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <Link2 className="size-4" aria-hidden />
            {guardando ? "Vinculando…" : "Vincular mascota"}
          </button>
        </section>
      </div>
    </Modal>
  )
}

function Campo({
  etiqueta,
  valor,
  onChange,
  inputMode,
}: {
  etiqueta: string
  valor: string
  onChange: (v: string) => void
  inputMode?: "numeric" | "tel"
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="text-xs font-medium text-muted-foreground">{etiqueta}</span>
      <TextInput
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        inputMode={inputMode}
        className={TOQUE_MINIMO}
      />
    </label>
  )
}