"use client"

/**
 * Cambio de contraseña obligatorio.
 *
 * Es la única pantalla del sistema mientras la cuenta tenga
 * `debeCambiarPassword`: la del Dueño recién sembrado y la de cualquier empleado
 * con clave temporal (alta o reset). El backend responde 403 a todo lo demás.
 */

import { useState, type FormEvent } from "react"
import { useRouter } from "next/navigation"
import { KeyRound, Loader2, LogOut } from "lucide-react"

import { ApiError } from "@/lib/api"
import { useAuth } from "@/lib/auth"
import { ErrorBox, TOQUE_MINIMO } from "@/lib/vista"
import { Field, TextInput } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const MINIMO = 8

export function CambiarPassword() {
  const { usuario, cambiarPassword, logout } = useAuth()
  const router = useRouter()
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
      // Guarda la sesión renovada: `useAuth` re-renderiza y /app muestra el sistema.
      await cambiarPassword(actual, nueva)
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, "No se pudo cambiar la contraseña", "/auth/cambiar-password"))
    } finally {
      setGuardando(false)
    }
  }

  const salir = () => {
    logout()
    router.replace("/login")
  }

  return (
    <main className="flex min-h-screen items-start justify-center bg-background p-4 text-foreground sm:items-center">
      <form
        onSubmit={enviar}
        className="flex w-full max-w-sm flex-col gap-4 rounded-xl border border-border bg-card p-5 shadow-lg"
        aria-labelledby="titulo-cambio"
      >
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
        <Button type="button" variant="ghost" onClick={salir} className={cn("w-full", TOQUE_MINIMO)}>
          <LogOut className="h-4 w-4" aria-hidden />
          Salir
        </Button>
      </form>
    </main>
  )
}
