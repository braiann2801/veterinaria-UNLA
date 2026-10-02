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
import { Stethoscope, UserPlus } from "lucide-react"

import { profesionales as apiProfesionales } from "@/lib/api"
import type { Profesional } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { Recurso, TOQUE_MINIMO } from "@/lib/vista"

export function Profesionales() {
  const lista = useApi<Profesional[]>(() => apiProfesionales.listar())
  const [abierto, setAbierto] = useState(false)

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
          <ul className="flex flex-col divide-y divide-border">
            {datos.map((p) => (
              <li key={p.id} className="flex flex-col gap-1 py-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="truncate text-sm font-medium">{p.nombreCompleto}</p>
                  {!p.activo && (
                    <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      Inactivo
                    </span>
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  {[
                    p.matricula ? `Mat. ${p.matricula}` : null,
                    p.dni ? `DNI ${p.dni}` : null,
                    p.telefono,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "Sin matricula, DNI ni teléfono"}
                </p>
              </li>
            ))}
          </ul>
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
function ModalCargarProfesional({
  abierto,
  onCerrar,
  titulo,
  onCreado,
}: {
  abierto: boolean
  onCerrar: () => void
  titulo: string
  onCreado: () => void | Promise<void>
}) {
  const [nombre, setNombre] = useState("")
  const [apellido, setApellido] = useState("")
  const [dni, setDni] = useState("")
  const [matricula, setMatricula] = useState("")
  const [telefono, setTelefono] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await apiProfesionales.crear({
        nombre: nombre.trim(),
        apellido: apellido.trim(),
        dni: dni.trim() || undefined,
        matricula: matricula.trim() || undefined,
        telefono: telefono.trim() || undefined,
      })
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
