"use client"

/**
 * Ingreso de empleados (`/login`).
 *
 * Con sesión vigente redirige a `/app`. Después de un 401 en el sistema
 * (`lib/api.ts`) llega con `?motivo=sesion` y avisa que hay que volver a entrar,
 * en lugar de mostrar un formulario vacío sin explicación.
 */

import { useEffect, useState, type FormEvent } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { ArrowLeft, Loader2, LogIn, PawPrint } from "lucide-react"

import { ApiError } from "@/lib/api"
import { useAuth } from "@/lib/auth"
import { ErrorBox, TOQUE_MINIMO } from "@/lib/vista"
import { Field, TextInput } from "@/components/ui/field"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { NEGOCIO } from "@/lib/negocio"

export default function LoginPage() {
  const { listo, sesion, login } = useAuth()
  const router = useRouter()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<ApiError | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [sesionVencida, setSesionVencida] = useState(false)

  useEffect(() => {
    setSesionVencida(new URLSearchParams(window.location.search).get("motivo") === "sesion")
  }, [])

  useEffect(() => {
    if (listo && sesion) router.replace("/app")
  }, [listo, sesion, router])

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await login(username, password)
      router.replace("/app")
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, "No se pudo ingresar", "/auth/login"))
      setEnviando(false)
    }
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-start gap-6 bg-background p-4 pt-12 text-foreground sm:justify-center sm:pt-4">
      <form
        onSubmit={enviar}
        className="flex w-full max-w-sm flex-col gap-4 rounded-xl border border-border bg-card p-5 shadow-lg"
        aria-labelledby="titulo-login"
      >
        <header className="flex flex-col items-center gap-2 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/15 text-primary">
            <PawPrint className="h-6 w-6" aria-hidden />
          </span>
          <h1 id="titulo-login" className="text-lg font-semibold">
            Ingreso de empleados
          </h1>
          <p className="text-sm text-muted-foreground">{NEGOCIO.nombre}</p>
        </header>

        {sesionVencida && !error && (
          <p className="rounded-md border border-border bg-muted p-3 text-sm" role="status">
            Tu sesión terminó. Volvé a ingresar para continuar.
          </p>
        )}

        <Field label="Usuario">
          <TextInput
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className={TOQUE_MINIMO}
            required
          />
        </Field>
        <Field label="Contraseña">
          <TextInput
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={TOQUE_MINIMO}
            required
          />
        </Field>

        <ErrorBox error={error} />

        <Button type="submit" disabled={enviando} className={cn("w-full", TOQUE_MINIMO)}>
          {enviando ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <LogIn className="h-4 w-4" aria-hidden />}
          Ingresar
        </Button>
        <p className="text-center text-xs text-muted-foreground">
          ¿Olvidaste la contraseña? Pedile al Dueño que la resetee.
        </p>
      </form>

      <Link
        href="/"
        className={cn("inline-flex items-center gap-2 rounded-xl px-3 text-sm text-muted-foreground hover:text-foreground", TOQUE_MINIMO)}
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Volver al sitio
      </Link>
    </main>
  )
}
