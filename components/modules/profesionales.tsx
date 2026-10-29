"use client"

/**
 * TASK-007 (FASE 1): padron de profesionales del mostrador.
 *
 * <p>Antes de este componente no existia ninguna pantalla que creara un
 * profesional. `POST /api/v1/profesionales` solo se ejercitaba desde los tests del
 * backend como precondicio de los turnos, y el unico consumidor en la UI era el
 * `<select>` de `ficha.tsx`, que solo lo leia. Para dar de alta a un veterinario
 * habia que pegar un `curl`.</p>
 *
 * <p>El listado se relee de la API despues de cada alta, no se agrega al estado
 * local. Asi lo que ve el mostrador es lo que quedo en MySQL, y no una copia que
 * puede divergir si el alta fallo a medias.</p>
 */

import { useState } from "react"
import { Pencil, Stethoscope, Trash2, UserPlus } from "lucide-react"

import { profesionales as apiProfesionales } from "@/lib/api"
import type { Profesional } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { Recurso, TOQUE_MINIMO, formatearDni } from "@/lib/vista"
import { Paginado } from "@/components/ui/paginado"

/** Botón de acción de fila (editar/eliminar): cuadrado táctil de 44 px, igual que en el Padrón. */
const BOTON_ACCION =
  "inline-flex min-h-[44px] min-w-[44px] items-center justify-center rounded-md border transition-colors"

export function Profesionales() {
  const lista = useApi<Profesional[]>(() => apiProfesionales.listar())
  const [abierto, setAbierto] = useState(false)
  const [editando, setEditando] = useState<Profesional | null>(null)
  const [eliminando, setEliminando] = useState<Profesional | null>(null)

  return (
    <Panel
      title="Profesionales"
      action={
        <button
          onClick={() => setAbierto(true)}
          className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
        >
          <UserPlus className="size-3.5" aria-hidden />
          Cargar veterinario
        </button>
      }
    >
      <Recurso
        estado={lista}
        textoCarga="Cargando profesionales"
        textoVacio="Todavía no hay profesionales cargados"
        onReintentar={lista.recargar}
      >
        {(datos) => (
          <Paginado items={datos} clave="profesionales">
          {(pagina) => (
          <ul className="flex flex-col divide-y divide-border">
            {pagina.map((p) => (
              <li key={p.id} className="flex flex-col gap-1 py-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="truncate text-sm font-medium">{p.nombreCompleto}</p>
                  <div className="flex shrink-0 items-center gap-1">
                    {!p.activo && (
                      <span className="mr-1 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        Inactivo
                      </span>
                    )}
                    <button
                      onClick={() => setEditando(p)}
                      aria-label={`Editar ${p.nombreCompleto}`}
                      title="Editar profesional"
                      className={`${BOTON_ACCION} border-border text-muted-foreground hover:bg-muted hover:text-foreground`}
                    >
                      <Pencil className="size-4" aria-hidden />
                    </button>
                    <button
                      onClick={() => setEliminando(p)}
                      aria-label={`Eliminar ${p.nombreCompleto}`}
                      title="Eliminar profesional"
                      className={`${BOTON_ACCION} border-destructive/40 text-destructive hover:bg-destructive/10`}
                    >
                      <Trash2 className="size-4" aria-hidden />
                    </button>
                  </div>
                </div>
                <p className="text-xs text-muted-foreground">
                  {[
                    p.matricula ? `Mat. ${p.matricula}` : null,
                    p.dni ? `DNI ${formatearDni(p.dni)}` : null,
                    p.telefono,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "Sin matricula, DNI ni teléfono"}
                </p>
              </li>
            ))}
          </ul>
          )}
          </Paginado>
        )}
      </Recurso>

      {abierto && (
        <ModalCargarProfesional
          abierto={abierto}
          onCerrar={() => setAbierto(false)}
          titulo="Cargar veterinario"
          onCreado={lista.recargar}
        />
      )}
      {editando && (
        <ModalCargarProfesional
          abierto
          profesional={editando}
          onCerrar={() => setEditando(null)}
          titulo={`Editar ${editando.nombreCompleto}`}
          onCreado={lista.recargar}
        />
      )}
      {eliminando && (
        <ModalEliminarProfesional
          profesional={eliminando}
          onCerrar={() => setEliminando(null)}
          onEliminado={async () => {
            setEliminando(null)
            await lista.recargar()
          }}
        />
      )}
    </Panel>
  )
}

/**
 * Formulario de alta.
 *
 * <p>Se monta solo cuando el modal esta abierto, para que el estado del
 * formulario muera al cerrarlo. Si viviera siempre montado, un alta fallida
 * dejaria escrito el DNI rechazado y el operador tendria que limpiarlo a mano
 * para reintentar.</p>
 */
/** Alta o, si recibe `profesional`, edición: mismos campos y mismas reglas del backend. */
function ModalCargarProfesional({
  abierto,
  onCerrar,
  titulo,
  onCreado,
  profesional,
}: {
  abierto: boolean
  onCerrar: () => void
  titulo: string
  onCreado: () => void | Promise<void>
  profesional?: Profesional
}) {
  const [nombre, setNombre] = useState(profesional?.nombre ?? "")
  const [apellido, setApellido] = useState(profesional?.apellido ?? "")
  const [dni, setDni] = useState(profesional?.dni ?? "")
  const [matricula, setMatricula] = useState(profesional?.matricula ?? "")
  const [telefono, setTelefono] = useState(profesional?.telefono ?? "")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      const datos = {
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        dni: dni.trim() || undefined,
        matricula: matricula.trim() || undefined,
        telefono: telefono.trim() || undefined,
      }
      if (profesional) await apiProfesionales.actualizar(profesional.id, datos)
      else await apiProfesionales.crear(datos)
      setNombre("")
      setApellido("")
      setDni("")
      setMatricula("")
      setTelefono("")
      await onCreado()
      onCerrar()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo cargar el profesional")
    } finally {
      setGuardando(false)
    }
  }

  const listo = nombre.trim().length > 0 && apellido.trim().length > 0

  return (
    <Modal open={abierto} onClose={onCerrar} title={titulo}>
      <div className="flex flex-col gap-3 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Nombre" valor={nombre} onChange={setNombre} placeholder="Ej: Elena" />
          <Campo etiqueta="Apellido" valor={apellido} onChange={setApellido} placeholder="Ej: Vidal" />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="DNI" valor={dni} onChange={setDni} inputMode="numeric" />
          <Campo
            etiqueta="Matrícula"
            valor={matricula}
            onChange={setMatricula}
            placeholder="Ej: MAT-1234"
          />
        </div>
        <Campo etiqueta="Teléfono" valor={telefono} onChange={setTelefono} inputMode="tel" />

        <p className="text-xs text-muted-foreground">
          Solo el nombre y el apellido son obligatorios. La matrícula y el DNI se
          rechazan si ya pertenecen a otro profesional.
        </p>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button
            onClick={onCerrar}
            disabled={guardando}
            className={`rounded-md border border-border px-3 text-sm transition-colors hover:bg-muted disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            Cancelar
          </button>
          <button
            onClick={guardar}
            disabled={guardando || !listo}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <Stethoscope className="size-4" aria-hidden />
            {guardando ? "Guardando…" : "Guardar"}
          </button>
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
  inputMode?: "numeric" | "tel"
  placeholder?: string
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="text-xs font-medium text-muted-foreground">{etiqueta}</span>
      <TextInput
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        inputMode={inputMode}
        placeholder={placeholder}
        className={TOQUE_MINIMO}
      />
    </label>
  )
}

function ModalEliminarProfesional({
  profesional,
  onCerrar,
  onEliminado,
}: {
  profesional: Profesional
  onCerrar: () => void
  onEliminado: () => void | Promise<void>
}) {
  const [error, setError] = useState<string | null>(null)
  const [borrando, setBorrando] = useState(false)

  async function eliminar() {
    setBorrando(true)
    setError(null)
    try {
      await apiProfesionales.eliminar(profesional.id)
      await onEliminado()
    } catch (e) {
      // 409: tiene turnos o consultas. El motivo viene del backend.
      setError(e instanceof Error ? e.message : "No se pudo eliminar el profesional")
      setBorrando(false)
    }
  }

  return (
    <Modal open onClose={borrando ? () => {} : onCerrar} title={`Eliminar a ${profesional.nombreCompleto}`}>
      <div className="flex flex-col gap-3 p-4 text-sm">
        <p>
          ¿Seguro que querés eliminar a <strong>{profesional.nombreCompleto}</strong>?
        </p>
        <p className="text-muted-foreground">
          Si ya tiene turnos o consultas registrados, el sistema no va a permitir eliminarlo. Esta acción no se
          puede deshacer.
        </p>
        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-destructive" role="alert">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <button
            onClick={onCerrar}
            disabled={borrando}
            className={`rounded-md border border-border px-3 text-sm transition-colors hover:bg-muted disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            Cancelar
          </button>
          <button
            onClick={eliminar}
            disabled={borrando}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-destructive px-3 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <Trash2 className="size-4" aria-hidden />
            {borrando ? "Eliminando…" : "Eliminar"}
          </button>
        </div>
      </div>
    </Modal>
  )
}
