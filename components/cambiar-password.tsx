"use client"

/**
 * Cambio de contraseña.
 *
 * Dos usos del mismo formulario:
 * - `CambiarPassword`: pantalla obligatoria, la única del sistema mientras la
 *   cuenta tenga `debeCambiarPassword` (el Dueño recién sembrado y cualquier
 *   empleado con clave temporal). El backend responde 403 a todo lo demás.
 * - `ModalCambiarPassword`: cambio voluntario desde la barra superior.
 *
 * En los dos casos el backend emite un token nuevo y cierra las demás sesiones
 * de la cuenta (`tokenVersion`); `useAuth` guarda la sesión renovada.
 */

import { useState, type FormEvent, type ReactNode } from "react"
import { useRouter } from "next/navigation"
import { KeyRound, Loader2, LogOut } from "lucide-react"

import { ApiError } from "@/lib/api"
import { useAuth } from "@/lib/auth"
import { ErrorBox, TOQUE_MINIMO } from "@/lib/vista"
import { Field, TextInput } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { Modal } from "@/components/ui/modal"
import { cn } from "@/lib/utils"

const MINIMO = 8

function FormCambiarPassword({
  onCambiada,
  encabezado,
  pie,
}: {
  onCambiada?: () => void
  encabezado?: ReactNode
  pie?: ReactNode
}) {
  const { cambiarPassword } = useAuth()
  const [actual, setActual] = useState("")
  const [nueva, setNueva] = useState("")
  const [repetida, setRepetida] = useState("")
  const [error, setError] = useState<ApiError | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setAviso(null)
    if (nueva.length < MINIMO) return setAviso(`La contraseña nueva debe tener al menos ${MINIMO} caracteres.`)
    if (nueva !== repetida) return setAviso("Las dos contraseñas nuevas no coinciden.")
    if (nueva === actual) return setAviso("La contraseña nueva tiene que ser distinta de la actual.")
    setGuardando(true)
    try {
      await cambiarPassword(actual, nueva)
      onCambiada?.()
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, "No se pudo cambiar la contraseña", "/auth/cambiar-password"))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4">
      {encabezado}
      <Field label="Contraseña actual">
        <TextInput
          type="password"
          autoComplete="current-password"
          value={actual}
          onChange={(e) => setActual(e.target.value)}
          className={TOQUE_MINIMO}
          required
        />
      </Field>
      <Field label={`Contraseña nueva (mínimo ${MINIMO} caracteres)`}>
        <TextInput
          type="password"
          autoComplete="new-password"
          value={nueva}
          onChange={(e) => setNueva(e.target.value)}
          className={TOQUE_MINIMO}
          required
        />
      </Field>
      <Field label="Repetir contraseña nueva">
        <TextInput
          type="password"
          autoComplete="new-password"
          value={repetida}
          onChange={(e) => setRepetida(e.target.value)}
          className={TOQUE_MINIMO}
          required
        />
      </Field>

      {aviso && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
          {aviso}
        </p>
      )}
      <ErrorBox error={error} />

      <Button type="submit" disabled={guardando} className={cn("w-full", TOQUE_MINIMO)}>
        {guardando && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
        Guardar contraseña
      </Button>
      {pie}
    </form>
  )
}

/** Pantalla obligatoria del primer ingreso (o tras un reset). */
export function CambiarPassword() {
  const { usuario, logout } = useAuth()
  const router = useRouter()

  const salir = () => {
    logout()
    router.replace("/login")
  }

  return (
    <main className="flex min-h-screen items-start justify-center bg-background p-4 text-foreground sm:items-center">
      <div
        className="w-full max-w-sm rounded-xl border border-border bg-card p-5 shadow-lg"
        role="region"
        aria-labelledby="titulo-cambio"
      >
        <FormCambiarPassword
          encabezado={
            <header className="flex flex-col gap-1">
              <KeyRound className="h-6 w-6 text-primary" aria-hidden />
              <h1 id="titulo-cambio" className="text-lg font-semibold">
                Elegí una contraseña nueva
              </h1>
              <p className="text-sm text-muted-foreground">
                {usuario?.nombreCompleto ? `${usuario.nombreCompleto}, la` : "La"} contraseña con la que
                ingresaste es temporal. Cambiala para empezar a usar el sistema.
              </p>
            </header>
          }
          pie={
            <Button type="button" variant="ghost" onClick={salir} className={cn("w-full", TOQUE_MINIMO)}>
              <LogOut className="h-4 w-4" aria-hidden />
              Salir
            </Button>
          }
        />
      </div>
    </main>
  )
}

/** Cambio voluntario desde la barra superior. */
export function ModalCambiarPassword({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [listo, setListo] = useState(false)

  const cerrar = () => {
    setListo(false)
    onClose()
  }

  return (
    <Modal open={open} onClose={cerrar} title="Cambiar contraseña">
      {listo ? (
        <div className="flex flex-col gap-4">
          <p className="text-sm" role="status">
            Listo, tu contraseña cambió. Si tenías la sesión abierta en otro dispositivo, ahí vas a tener que volver a
            ingresar.
          </p>
          <Button type="button" onClick={cerrar} className={cn("w-full", TOQUE_MINIMO)}>
            Cerrar
          </Button>
        </div>
      ) : (
        <FormCambiarPassword onCambiada={() => setListo(true)} />
      )}
    </Modal>
  )
}
