"use client"

/**
 * TASK-006: padrón de tutores y mascotas contra la API real.
 *
 * El switch "Autorizado a retirar" (regla 2.4 de AGENTS.md) viaja al backend
 * como parte del vínculo, no como un campo suelto de la mascota: la autorización
 * pertenece a la tupla Tutor-Mascota y una misma mascota puede tener un tutor
 * que retira y un cotutor que no.
 *
 * <p>Incluye el ABM: editar y eliminar tutores y mascotas. La edición de la
 * mascota manda sólo sus datos propios y reenvía los vínculos tal como estaban,
 * porque el backend sincroniza la lista completa del N:M: omitirla borraría
 * todos los tutores. El borrado de un tutor puede arrastrar a las mascotas que
 * queden huérfanas (regla inviolable 2.4); el backend responde 409 cuando esa
 * operación arrastraría una ficha con historial clínico o contable.</p>
 */

import { useRef, useState } from "react"
import { Link2, PawPrint, Pencil, ShieldCheck, ShieldOff, Trash2, UserPlus } from "lucide-react"

import { hoyLocal, mascotas as apiMascotas, tutores as apiTutores } from "@/lib/api"
import type { ConductaMascota, EspecieMascota, Mascota, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, Select, TextArea, TextInput } from "@/components/ui/field"
import { DatePicker } from "@/components/ui/date-picker"
import { Modal } from "@/components/ui/modal"
import { Recurso, TOQUE_MINIMO, filtrarPorModo, formatearDni } from "@/lib/vista"
import { Paginado } from "@/components/ui/paginado"
import { AltaMascota, CONDUCTAS, ESPECIES } from "@/components/modules/alta-mascota"

/** Botón de acción de fila (editar/eliminar): cuadrado táctil de 44 px (regla 3.4). */
const BOTON_ACCION =
  "inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md border transition-colors"

export function Padron() {
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const [seleccion, setSeleccion] = useState<Tutor | null>(null)
  const [openAlta, setOpenAlta] = useState(false)
  const [editandoTutor, setEditandoTutor] = useState<Tutor | null>(null)
  const [eliminandoTutor, setEliminandoTutor] = useState<Tutor | null>(null)
  const [editandoMascota, setEditandoMascota] = useState<Mascota | null>(null)
  const [eliminandoMascota, setEliminandoMascota] = useState<Mascota | null>(null)
  const [agregandoCotutor, setAgregandoCotutor] = useState<Mascota | null>(null)
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
              className={`inline-flex items-center justify-center gap-2 rounded-md border border-border px-3 text-xs font-medium transition-colors hover:bg-muted ${TOQUE_MINIMO}`}
            >
              <PawPrint className="size-3.5" aria-hidden />
              Alta de mascota
            </button>
            <button
              onClick={() => setOpenAlta(true)}
              className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
            >
              <UserPlus className="size-4" aria-hidden />
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
            <Paginado items={lista} clave="padron-tutores">
            {(pagina) => (
            <ul className="flex flex-col divide-y divide-border">
              {pagina.map((t) => (
                <li key={t.id} className="flex items-center gap-2 py-2">
                  <button
                    onClick={() => setSeleccion(t)}
                    className="flex min-w-0 flex-1 flex-col gap-1 rounded-md py-1 text-left transition-colors hover:bg-muted/50"
                  >
                    <span className="truncate text-sm font-medium">{t.nombreCompleto}</span>
                    <span className="truncate text-xs text-muted-foreground">
                      DNI {formatearDni(t.dni)}
                      {t.telefono ? ` · ${t.telefono}` : ""} · {t.mascotas.length} mascota
                      {t.mascotas.length === 1 ? "" : "s"}
                    </span>
                  </button>
                  <div className="flex shrink-0 items-center gap-1">
                    <button
                      onClick={() => setEditandoTutor(t)}
                      aria-label={`Editar ${t.nombreCompleto}`}
                      title="Editar tutor"
                      className={`${BOTON_ACCION} border-border text-muted-foreground hover:bg-muted hover:text-foreground`}
                    >
                      <Pencil className="size-4" aria-hidden />
                    </button>
                    <button
                      onClick={() => setEliminandoTutor(t)}
                      aria-label={`Eliminar ${t.nombreCompleto}`}
                      title="Eliminar tutor"
                      className={`${BOTON_ACCION} border-destructive/40 text-destructive hover:bg-destructive/10`}
                    >
                      <Trash2 className="size-4" aria-hidden />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
            )}
            </Paginado>
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
            <Paginado items={lista} clave="padron-mascotas">
            {(pagina) => (
            <ul className="flex flex-col divide-y divide-border">
              {pagina.map((m) => (
                <li key={m.id} className="flex flex-col gap-2 py-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{m.nombre}</p>
                      <p className="text-xs text-muted-foreground">
                        {[m.especie, m.raza].filter(Boolean).join(" · ")}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      {m.conducta && m.conducta !== "normal" && (
                        <span className="mr-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-900 dark:bg-amber-500/15 dark:text-amber-300">
                          {m.conducta}
                        </span>
                      )}
                      <button
                        onClick={() => setEditandoMascota(m)}
                        aria-label={`Editar ${m.nombre}`}
                        title="Editar mascota"
                        className={`${BOTON_ACCION} border-border text-muted-foreground hover:bg-muted hover:text-foreground`}
                      >
                        <Pencil className="size-4" aria-hidden />
                      </button>
                      <button
                        onClick={() => setEliminandoMascota(m)}
                        aria-label={`Eliminar ${m.nombre}`}
                        title="Eliminar mascota"
                        className={`${BOTON_ACCION} border-destructive/40 text-destructive hover:bg-destructive/10`}
                      >
                        <Trash2 className="size-4" aria-hidden />
                      </button>
                    </div>
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
                    <li>
                      {/* Sumar un tutor desde la mascota, sin ir a buscar la ficha del tutor. */}
                      <button
                        onClick={() => setAgregandoCotutor(m)}
                        aria-label={`Agregar cotutor a ${m.nombre}`}
                        className={`inline-flex items-center gap-1 rounded-full border border-dashed border-border px-3 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground ${TOQUE_MINIMO}`}
                      >
                        <Link2 className="size-3" aria-hidden />
                        Agregar cotutor
                      </button>
                    </li>
                  </ul>
                </li>
              ))}
            </ul>
            )}
            </Paginado>
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

      {editandoTutor && (
        <ModalEditarTutor
          tutor={editandoTutor}
          onCerrar={() => setEditandoTutor(null)}
          onGuardado={async () => {
            setEditandoTutor(null)
            await refrescar()
          }}
        />
      )}

      {eliminandoTutor && (
        <ModalEliminar
          titulo={`Eliminar a ${eliminandoTutor.nombreCompleto}`}
          advertencia={
            <>
              <p>
                ¿Seguro que querés eliminar a <strong>{eliminandoTutor.nombreCompleto}</strong> del
                padrón?
              </p>
              <p className="text-muted-foreground">
                Si alguna de sus mascotas queda sin ningún otro tutor, también se elimina. Si esa
                mascota tiene historial clínico o contable, el sistema bloquea el borrado. Esta
                acción no se puede deshacer.
              </p>
            </>
          }
          onCerrar={() => setEliminandoTutor(null)}
          onConfirmar={async () => {
            await apiTutores.eliminar(eliminandoTutor.id)
            setEliminandoTutor(null)
            await refrescar()
          }}
        />
      )}

      {editandoMascota && (
        <ModalEditarMascota
          mascota={editandoMascota}
          onCerrar={() => setEditandoMascota(null)}
          onGuardado={async () => {
            setEditandoMascota(null)
            await refrescar()
          }}
        />
      )}

      {agregandoCotutor && (
        <ModalAgregarCotutor
          mascota={agregandoCotutor}
          tutores={tutores.datos ?? []}
          onCerrar={() => setAgregandoCotutor(null)}
          onVinculado={async () => {
            setAgregandoCotutor(null)
            await refrescar()
          }}
        />
      )}

      {eliminandoMascota && (
        <ModalEliminar
          titulo={`Eliminar a ${eliminandoMascota.nombre}`}
          advertencia={
            <>
              <p>
                ¿Seguro que querés eliminar a <strong>{eliminandoMascota.nombre}</strong> del padrón?
              </p>
              <p className="text-muted-foreground">
                Se borran también sus vínculos con los tutores. Si la mascota tiene historial clínico
                o contable, el sistema no permitirá eliminarla. Esta acción no se puede deshacer.
              </p>
            </>
          }
          onCerrar={() => setEliminandoMascota(null)}
          onConfirmar={async () => {
            await apiMascotas.eliminar(eliminandoMascota.id)
            setEliminandoMascota(null)
            await refrescar()
          }}
        />
      )}
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
  const [email, setEmail] = useState("")
  const [direccion, setDireccion] = useState("")
  const [mascotaSeleccionada, setMascotaSeleccionada] = useState<number | null>(null)
  const [autorizadoRetiro, setAutorizadoRetiro] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar(), abierto)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      const creado = await apiTutores.crear({
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        dni: dni.trim(),
        // Los opcionales van como `undefined` y no como "": el backend los valida
        // con @Pattern y un string vacío no pasa la validación.
        telefono: telefono.trim() || undefined,
        email: email.trim() || undefined,
        direccion: direccion.trim() || undefined,
      })
      if (mascotaSeleccionada) {
        await apiTutores.vincular(creado.id, mascotaSeleccionada, autorizadoRetiro)
      }
      setNombre("")
      setApellido("")
      setDni("")
      setTelefono("")
      setEmail("")
      setDireccion("")
      setMascotaSeleccionada(null)
      setAutorizadoRetiro(false)
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
          <Campo etiqueta="DNI" valor={dni} onChange={setDni} inputMode="numeric" />
          <Campo etiqueta="Teléfono" valor={telefono} onChange={setTelefono} inputMode="tel" />
          <Campo etiqueta="Email" valor={email} onChange={setEmail} inputMode="email" />
          <Campo etiqueta="Dirección" valor={direccion} onChange={setDireccion} />
        </div>

        <Recurso estado={mascotas} textoCarga="Cargando mascotas">
          {(lista) => (
            <section className="rounded-md border p-3">
              <h3 className="mb-2 text-xs font-medium text-muted-foreground">
                Asociar con mascota existente (opcional)
              </h3>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <select
                  value={mascotaSeleccionada ?? ""}
                  onChange={(e) =>
                    setMascotaSeleccionada(e.target.value ? Number(e.target.value) : null)
                  }
                  className={`w-full rounded-md border bg-background px-2 py-2 text-sm ${TOQUE_MINIMO}`}
                >
                  <option value="">Sin asociar</option>
                  {lista.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.nombre} — {m.especie}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => setAutorizadoRetiro((v) => !v)}
                  disabled={!mascotaSeleccionada}
                  role="switch"
                  aria-checked={autorizadoRetiro}
                  className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors disabled:opacity-60 ${TOQUE_MINIMO} ${
                    autorizadoRetiro
                      ? "border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-300"
                      : "border-border bg-muted text-muted-foreground"
                  }`}
                >
                  {autorizadoRetiro ? "Autorizado a retirar" : "Solo cotutor"}
                </button>
              </div>
            </section>
          )}
        </Recurso>

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

/** Edición de los datos escalares del tutor. Los vínculos se manejan aparte. */
function ModalEditarTutor({
  tutor,
  onCerrar,
  onGuardado,
}: {
  tutor: Tutor
  onCerrar: () => void
  onGuardado: () => void | Promise<void>
}) {
  const [nombre, setNombre] = useState(tutor.nombre)
  const [apellido, setApellido] = useState(tutor.apellido)
  const [dni, setDni] = useState(tutor.dni)
  const [telefono, setTelefono] = useState(tutor.telefono ?? "")
  const [email, setEmail] = useState(tutor.email ?? "")
  const [direccion, setDireccion] = useState(tutor.direccion ?? "")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await apiTutores.actualizar(tutor.id, {
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        dni: dni.trim(),
        // Los opcionales van como `undefined` y no como "": el backend los valida
        // con @Pattern y un string vacío no pasa la validación.
        telefono: telefono.trim() || undefined,
        email: email.trim() || undefined,
        direccion: direccion.trim() || undefined,
      })
      await onGuardado()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo actualizar el tutor")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open onClose={onCerrar} title={`Editar ${tutor.nombreCompleto}`}>
      <div className="flex flex-col gap-3 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Nombre" valor={nombre} onChange={setNombre} />
          <Campo etiqueta="Apellido" valor={apellido} onChange={setApellido} />
        </div>
        <Campo etiqueta="DNI" valor={dni} onChange={setDni} inputMode="numeric" />
        <Campo etiqueta="Teléfono" valor={telefono} onChange={setTelefono} inputMode="tel" />
        <Campo etiqueta="Email" valor={email} onChange={setEmail} inputMode="email" />
        <Campo etiqueta="Dirección" valor={direccion} onChange={setDireccion} />
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
          {guardando ? "Guardando…" : "Guardar cambios"}
        </button>
      </div>
    </Modal>
  )
}

/**
 * Edición de la mascota.
 *
 * <p>Manda sólo los campos propios: el N:M se gobierna desde el modal del tutor.
 * Aun así reenvía `tutores` con la lista actual, porque el backend sincroniza la
 * lista completa y omitirla borraría todos los vínculos.</p>
 */
function ModalEditarMascota({
  mascota,
  onCerrar,
  onGuardado,
}: {
  mascota: Mascota
  onCerrar: () => void
  onGuardado: () => void | Promise<void>
}) {
  const [nombre, setNombre] = useState(mascota.nombre)
  const [especie, setEspecie] = useState<EspecieMascota>(mascota.especie)
  const [raza, setRaza] = useState(mascota.raza ?? "")
  const [sexo, setSexo] = useState<"" | "Macho" | "Hembra">(
    mascota.sexo === "Macho" || mascota.sexo === "Hembra" ? mascota.sexo : "",
  )
  const [fechaNacimiento, setFechaNacimiento] = useState(mascota.fechaNacimiento ?? "")
  const [peso, setPeso] = useState(mascota.pesoKg != null ? String(mascota.pesoKg) : "")
  const [conducta, setConducta] = useState<"" | ConductaMascota>(mascota.conducta ?? "")
  const [chip, setChip] = useState(mascota.chip ?? "")
  const [observaciones, setObservaciones] = useState(mascota.observaciones ?? "")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    const pesoNum = peso.trim() === "" ? undefined : Number(peso)
    if (pesoNum !== undefined && (Number.isNaN(pesoNum) || pesoNum <= 0)) {
      setError("El peso tiene que ser un número mayor a 0.")
      return
    }
    setGuardando(true)
    setError(null)
    try {
      await apiMascotas.actualizar(mascota.id, {
        nombre: nombre.trim(),
        especie,
        raza: raza.trim() || undefined,
        sexo: sexo || undefined,
        fechaNacimiento: fechaNacimiento || undefined,
        pesoKg: pesoNum,
        conducta: conducta || undefined,
        chip: chip.trim() || undefined,
        observaciones: observaciones.trim() || undefined,
        tutores: mascota.tutores.map((t) => ({
          tutorId: t.id,
          autorizadoRetiro: t.autorizadoRetiro,
        })),
      })
      await onGuardado()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo actualizar la mascota")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open onClose={onCerrar} title={`Editar ${mascota.nombre}`}>
      <div className="flex flex-col gap-3 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Nombre" valor={nombre} onChange={setNombre} />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Especie</span>
            <Select
              value={especie}
              onChange={(e) => setEspecie(e.target.value as EspecieMascota)}
              className={TOQUE_MINIMO}
            >
              {ESPECIES.map((e) => (
                <option key={e.valor} value={e.valor}>
                  {e.etiqueta}
                </option>
              ))}
            </Select>
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Raza" valor={raza} onChange={setRaza} />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Sexo</span>
            <Select
              value={sexo}
              onChange={(e) => setSexo(e.target.value as "" | "Macho" | "Hembra")}
              className={TOQUE_MINIMO}
            >
              <option value="">Sin especificar</option>
              <option value="Macho">Macho</option>
              <option value="Hembra">Hembra</option>
            </Select>
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <DatePicker
            label="Fecha de nacimiento"
            value={fechaNacimiento}
            max={hoyLocal()}
            onChange={setFechaNacimiento}
            limpiable
          />
          <Campo
            etiqueta="Peso (kg)"
            valor={peso}
            onChange={setPeso}
            inputMode="decimal"
            placeholder="Ej: 12.5"
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Conducta</span>
            <Select
              value={conducta}
              onChange={(e) => setConducta(e.target.value as "" | ConductaMascota)}
              className={TOQUE_MINIMO}
            >
              <option value="">Sin especificar</option>
              {CONDUCTAS.map((c) => (
                <option key={c.valor} value={c.valor}>
                  {c.etiqueta}
                </option>
              ))}
            </Select>
          </label>
          <Campo etiqueta="Chip" valor={chip} onChange={setChip} placeholder="Opcional" />
        </div>

        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Observaciones</span>
          <TextArea
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            placeholder="Opcional"
          />
        </label>

        <p className="rounded-md border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
          Los tutores vinculados ({mascota.tutores.length}) no se editan acá: se agregan o quitan
          desde el modal del tutor, en el listado de tutores.
        </p>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <button
          onClick={guardar}
          disabled={guardando || !nombre.trim()}
          className={`rounded-md bg-primary text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
        >
          {guardando ? "Guardando…" : "Guardar cambios"}
        </button>
      </div>
    </Modal>
  )
}

/** Confirmación de borrado. Muestra el 409 del backend si la regla lo bloquea. */
function ModalEliminar({
  titulo,
  advertencia,
  onCerrar,
  onConfirmar,
}: {
  titulo: string
  advertencia: React.ReactNode
  onCerrar: () => void
  onConfirmar: () => void | Promise<void>
}) {
  const [error, setError] = useState<string | null>(null)
  const [borrando, setBorrando] = useState(false)

  async function confirmar() {
    setBorrando(true)
    setError(null)
    try {
      await onConfirmar()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo eliminar")
    } finally {
      setBorrando(false)
    }
  }

  return (
    <Modal open onClose={borrando ? () => {} : onCerrar} title={titulo}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2 text-sm">{advertencia}</div>
        {error && (
          <p
            className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive"
            role="alert"
          >
            {error}
          </p>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            onClick={onCerrar}
            disabled={borrando}
            className={`rounded-md border border-border px-4 text-sm font-medium transition-colors hover:bg-muted disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            Cancelar
          </button>
          <button
            onClick={confirmar}
            disabled={borrando}
            className={`rounded-md border border-destructive/40 bg-destructive/10 px-4 text-sm font-medium text-destructive transition-colors hover:bg-destructive/20 disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            {borrando ? "Eliminando…" : "Eliminar"}
          </button>
        </div>
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
      await apiTutores.actualizarAutorizacion(tutor.id, mascotaId, !actual)
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
                                    ? "border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-300"
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

/**
 * Vincula un tutor más a una mascota ya registrada, desde la tarjeta de la
 * mascota. Es el mismo vínculo que "Vincular otra mascota" en la ficha del
 * tutor (`POST /tutores/{id}/mascotas/{mascotaId}`): solo cambia el punto de
 * entrada. Por defecto queda como cotutor, sin autorización de retiro.
 */
function ModalAgregarCotutor({
  mascota,
  tutores,
  onCerrar,
  onVinculado,
}: {
  mascota: Mascota
  tutores: Tutor[]
  onCerrar: () => void
  onVinculado: () => void | Promise<void>
}) {
  const [tutorId, setTutorId] = useState<number | null>(null)
  const [autorizado, setAutorizado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const disponibles = tutores.filter((t) => !mascota.tutores.some((v) => v.id === t.id))

  async function vincular() {
    if (!tutorId) {
      setError("Elegí un tutor para vincular.")
      return
    }
    setGuardando(true)
    setError(null)
    try {
      await apiTutores.vincular(tutorId, mascota.id, autorizado)
      await onVinculado()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo vincular")
      setGuardando(false)
    }
  }

  return (
    <Modal open onClose={onCerrar} title={`Agregar cotutor a ${mascota.nombre}`}>
      <div className="flex flex-col gap-3">
        {disponibles.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Todos los tutores del padrón ya están vinculados a {mascota.nombre}. Para sumar a alguien
            nuevo, primero cargalo con &quot;Nuevo tutor&quot;.
          </p>
        ) : (
          <>
            <Select
              value={tutorId ?? ""}
              onChange={(e) => setTutorId(e.target.value ? Number(e.target.value) : null)}
              className={TOQUE_MINIMO}
              aria-label="Tutor a vincular"
            >
              <option value="">Elegí un tutor…</option>
              {disponibles.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombre} {t.apellido} · DNI {formatearDni(t.dni)}
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
                  Si está apagado, queda como cotutor: puede consultar y pagar, pero no llevarse a la mascota.
                </span>
              </span>
            </label>
          </>
        )}

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            onClick={onCerrar}
            className={`rounded-md border border-border px-4 text-sm hover:bg-muted ${TOQUE_MINIMO}`}
          >
            Cancelar
          </button>
          {disponibles.length > 0 && (
            <button
              onClick={vincular}
              disabled={guardando || !tutorId}
              className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
            >
              <Link2 className="size-4" aria-hidden />
              {guardando ? "Vinculando…" : "Vincular tutor"}
            </button>
          )}
        </div>
      </div>
    </Modal>
  )
}

function Campo({
  etiqueta,
  valor,
  onChange,
  inputMode,
  placeholder,
}: {
  etiqueta: string
  valor: string
  onChange: (v: string) => void
  inputMode?: "numeric" | "tel" | "email" | "decimal"
  placeholder?: string
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="text-xs font-medium text-muted-foreground">{etiqueta}</span>
      <TextInput
        value={valor}
        onChange={(e) => onChange(filtrarPorModo(inputMode, e.target.value))}
        inputMode={inputMode}
        placeholder={placeholder}
        className={TOQUE_MINIMO}
      />
    </label>
  )
}
