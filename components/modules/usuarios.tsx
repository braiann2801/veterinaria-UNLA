"use client"

/**
 * Usuarios: ABM de cuentas de empleados. Solo lo ve el Dueño.
 *
 * <p>El alta y el reset no piden contraseña: el backend genera una temporal que
 * se muestra <b>una sola vez</b> para dictársela al empleado, y la cuenta queda
 * con cambio obligatorio en el primer ingreso.</p>
 *
 * <p>Cambiar el rol, desactivar o resetear cierra al instante la sesión abierta
 * de ese empleado. El backend rechaza (409) desactivarse a uno mismo y dejar el
 * sistema sin un Dueño activo; la UI esconde esas acciones, pero la regla es la
 * del servidor.</p>
 */

import { useState } from "react"
import { KeyRound, Pencil, Power, UserPlus } from "lucide-react"

import { ApiError, usuarios as apiUsuarios, type UsuarioConClaveTemporal } from "@/lib/api"
import { useAuth, rolDeUi } from "@/lib/auth"
import type { RolApi, UsuarioSesion } from "@/lib/sesion"
import { roleLabels } from "@/lib/store"
import { useApi, mensajeDeError } from "@/lib/use-api"
import { Field, Panel, Select, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { Recurso, TOQUE_MINIMO } from "@/lib/vista"
import { cn } from "@/lib/utils"

const ROLES: RolApi[] = ["DUENIO", "VETERINARIO", "RECEPCION", "GUARDERIA"]

const BOTON_FILA = cn(
  "inline-flex items-center gap-1.5 rounded-md border border-input px-3 text-xs font-medium transition-colors hover:bg-muted disabled:opacity-50",
  TOQUE_MINIMO,
)

function textoError(e: unknown, porDefecto: string) {
  return e instanceof ApiError ? mensajeDeError(e) ?? porDefecto : porDefecto
}

export function Usuarios() {
  const { usuario: yo } = useAuth()
  const lista = useApi<UsuarioSesion[]>(() => apiUsuarios.listar())
  const [alta, setAlta] = useState(false)
  const [editando, setEditando] = useState<UsuarioSesion | null>(null)
  const [clave, setClave] = useState<UsuarioConClaveTemporal | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState<number | null>(null)

  async function alternarActivo(u: UsuarioSesion) {
    const accion = u.activo ? "desactivar" : "reactivar"
    if (!window.confirm(`¿Seguro que querés ${accion} la cuenta de ${u.nombreCompleto}?`)) return
    setOcupado(u.id)
    setError(null)
    try {
      await apiUsuarios.cambiarActivo(u.id, !u.activo)
      await lista.recargar()
    } catch (e) {
      setError(textoError(e, `No se pudo ${accion} la cuenta`))
    } finally {
      setOcupado(null)
    }
  }

  async function resetear(u: UsuarioSesion) {
    if (!window.confirm(`Se va a generar una contraseña temporal para ${u.nombreCompleto} y se cerrará su sesión. ¿Continuar?`)) return
    setOcupado(u.id)
    setError(null)
    try {
      setClave(await apiUsuarios.resetearPassword(u.id))
    } catch (e) {
      setError(textoError(e, "No se pudo resetear la contraseña"))
      setOcupado(null)
      return
    }
    // La clave ya se mostró: si la recarga de la lista falla, no es un error del reset.
    try {
      await lista.recargar()
    } finally {
      setOcupado(null)
    }
  }

  return (
    <Panel
      title="Usuarios"
      action={
        <button
          onClick={() => setAlta(true)}
          className={`inline-flex items-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
        >
          <UserPlus className="size-3.5" aria-hidden />
          Nuevo usuario
        </button>
      }
    >
      {error && (
        <p className="mb-3 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
      <Recurso
        estado={lista}
        textoCarga="Cargando usuarios"
        textoVacio="Todavía no hay usuarios"
        onReintentar={lista.recargar}
      >
        {(datos) => (
          <ul className="flex flex-col divide-y divide-border">
            {datos.map((u) => {
              const soyYo = u.id === yo?.id
              return (
                <li key={u.id} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                      <span className="truncate">{u.nombreCompleto}</span>
                      {soyYo && <span className="text-xs text-muted-foreground">(vos)</span>}
                      <span className="rounded-full bg-primary/15 px-2 py-0.5 text-xs text-primary">
                        {roleLabels[rolDeUi(u.rol)]}
                      </span>
                      {!u.activo && (
                        <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">Inactivo</span>
                      )}
                      {u.activo && u.debeCambiarPassword && (
                        <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                          Clave temporal
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-muted-foreground">Usuario: {u.username}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button className={BOTON_FILA} onClick={() => setEditando(u)} disabled={ocupado === u.id}>
                      <Pencil className="size-3.5" aria-hidden />
                      Editar
                    </button>
                    {/* Resetear la propia cuenta cerraría esta sesión antes de poder leer la
                        clave temporal: para el Dueño logueado no se ofrece. */}
                    {!soyYo && (
                      <button className={BOTON_FILA} onClick={() => resetear(u)} disabled={ocupado === u.id}>
                        <KeyRound className="size-3.5" aria-hidden />
                        Resetear clave
                      </button>
                    )}
                    {!soyYo && (
                      <button className={BOTON_FILA} onClick={() => alternarActivo(u)} disabled={ocupado === u.id}>
                        <Power className="size-3.5" aria-hidden />
                        {u.activo ? "Desactivar" : "Reactivar"}
                      </button>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </Recurso>

      {alta && (
        <ModalAlta
          onCerrar={() => setAlta(false)}
          onCreado={async (r) => {
            setAlta(false)
            setClave(r)
            await lista.recargar()
          }}
        />
      )}
      {editando && (
        <ModalEditar
          usuario={editando}
          onCerrar={() => setEditando(null)}
          onGuardado={async () => {
            setEditando(null)
            await lista.recargar()
          }}
        />
      )}
      {clave && <ModalClaveTemporal datos={clave} onCerrar={() => setClave(null)} />}
    </Panel>
  )
}

function ModalAlta({
  onCerrar,
  onCreado,
}: {
  onCerrar: () => void
  onCreado: (r: UsuarioConClaveTemporal) => void | Promise<void>
}) {
  const [username, setUsername] = useState("")
  const [nombreCompleto, setNombreCompleto] = useState("")
  const [rol, setRol] = useState<RolApi>("RECEPCION")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await onCreado(await apiUsuarios.crear({ username: username.trim(), nombreCompleto: nombreCompleto.trim(), rol }))
    } catch (e) {
      setError(textoError(e, "No se pudo crear el usuario"))
      setGuardando(false)
    }
  }

  const listo = username.trim().length >= 3 && nombreCompleto.trim().length > 0

  return (
    <Modal open onClose={onCerrar} title="Nuevo usuario">
      <div className="flex flex-col gap-3">
        <Field label="Nombre y apellido">
          <TextInput value={nombreCompleto} onChange={(e) => setNombreCompleto(e.target.value)} className={TOQUE_MINIMO} />
        </Field>
        <Field label="Usuario (para ingresar)">
          <TextInput
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            placeholder="Ej: laura.perez"
            className={TOQUE_MINIMO}
          />
        </Field>
        <Field label="Rol">
          <Select value={rol} onChange={(e) => setRol(e.target.value as RolApi)} className={TOQUE_MINIMO}>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {roleLabels[rolDeUi(r)]}
              </option>
            ))}
          </Select>
        </Field>
        <p className="text-xs text-muted-foreground">
          Se genera una contraseña temporal que vas a ver una sola vez. El empleado la cambia en su primer ingreso.
        </p>
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <Acciones onCancelar={onCerrar} onConfirmar={guardar} deshabilitado={!listo || guardando} texto="Crear usuario" />
      </div>
    </Modal>
  )
}

function ModalEditar({
  usuario,
  onCerrar,
  onGuardado,
}: {
  usuario: UsuarioSesion
  onCerrar: () => void
  onGuardado: () => void | Promise<void>
}) {
  const [nombreCompleto, setNombreCompleto] = useState(usuario.nombreCompleto)
  const [rol, setRol] = useState<RolApi>(usuario.rol)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    setGuardando(true)
    setError(null)
    try {
      await apiUsuarios.actualizar(usuario.id, { nombreCompleto: nombreCompleto.trim(), rol })
      await onGuardado()
    } catch (e) {
      setError(textoError(e, "No se pudo guardar el usuario"))
      setGuardando(false)
    }
  }

  return (
    <Modal open onClose={onCerrar} title={`Editar ${usuario.username}`}>
      <div className="flex flex-col gap-3">
        <Field label="Nombre y apellido">
          <TextInput value={nombreCompleto} onChange={(e) => setNombreCompleto(e.target.value)} className={TOQUE_MINIMO} />
        </Field>
        <Field label="Rol">
          <Select value={rol} onChange={(e) => setRol(e.target.value as RolApi)} className={TOQUE_MINIMO}>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {roleLabels[rolDeUi(r)]}
              </option>
            ))}
          </Select>
        </Field>
        {rol !== usuario.rol && (
          <p className="text-xs text-muted-foreground">Al cambiar el rol se cierra la sesión abierta de este usuario.</p>
        )}
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <Acciones
          onCancelar={onCerrar}
          onConfirmar={guardar}
          deshabilitado={nombreCompleto.trim().length === 0 || guardando}
          texto="Guardar"
        />
      </div>
    </Modal>
  )
}

function ModalClaveTemporal({ datos, onCerrar }: { datos: UsuarioConClaveTemporal; onCerrar: () => void }) {
  return (
    <Modal open onClose={onCerrar} title="Contraseña temporal">
      <div className="flex flex-col gap-3">
        <p className="text-sm">
          Pasale esta contraseña a <b>{datos.usuario.nombreCompleto}</b> (usuario <b>{datos.usuario.username}</b>).
          Va a tener que cambiarla al ingresar.
        </p>
        <p
          className="select-all rounded-xl border border-border bg-muted p-4 text-center font-mono text-2xl tracking-widest"
          aria-label="Contraseña temporal"
        >
          {datos.passwordTemporal}
        </p>
        <p className="text-xs text-muted-foreground">
          No se puede volver a ver: si se pierde, hay que resetearla de nuevo.
        </p>
        <button
          onClick={onCerrar}
          className={cn("rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground", TOQUE_MINIMO)}
        >
          Listo
        </button>
      </div>
    </Modal>
  )
}

function Acciones({
  onCancelar,
  onConfirmar,
  deshabilitado,
  texto,
}: {
  onCancelar: () => void
  onConfirmar: () => void
  deshabilitado: boolean
  texto: string
}) {
  return (
    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
      <button onClick={onCancelar} className={cn("rounded-md border border-input px-4 text-sm hover:bg-muted", TOQUE_MINIMO)}>
        Cancelar
      </button>
      <button
        onClick={onConfirmar}
        disabled={deshabilitado}
        className={cn("rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-50", TOQUE_MINIMO)}
      >
        {texto}
      </button>
    </div>
  )
}
