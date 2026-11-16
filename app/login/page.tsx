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
import { Paw } from "@/components/paw"
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
    <main className="relative flex min-h-screen flex-col items-center justify-center gap-6 overflow-hidden bg-cream p-4 py-10 text-navy sm:py-4">
      {/* Huellas decorativas: la marca de la landing, muy tenues para no competir
          con el formulario. */}
      <Paw className="pointer-events-none absolute -left-16 top-4 h-56 w-56 text-navy/5" />
      <Paw className="pointer-events-none absolute -right-20 bottom-0 h-72 w-72 text-navy/[0.04]" />

      <div className="relative flex w-full max-w-sm flex-col gap-5">
        <Link
          href="/"
          className="flex min-h-[44px] items-center justify-center gap-2 rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy"
        >
          <PawPrint className="h-6 w-6 shrink-0 text-navy" aria-hidden />
          <span className="font-display text-lg font-semibold">{NEGOCIO.nombreCorto}</span>
        </Link>

        <form
          onSubmit={enviar}
          className="flex flex-col gap-4 rounded-3xl border border-navy/10 bg-white p-6 shadow-[0_28px_70px_-40px_rgba(35,42,92,0.55)]"
          aria-labelledby="titulo-login"
        >
          <header className="flex flex-col items-center gap-2 text-center">
            <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-lilac/60 text-navy">
              <PawPrint className="h-7 w-7" aria-hidden />
            </span>
            <h1 id="titulo-login" className="text-2xl leading-tight font-semibold">
              Ingreso de empleados
            </h1>
            <p className="text-sm text-ink-muted">Sistema interno · {NEGOCIO.nombre}</p>
          </header>

          {sesionVencida && !error && (
            <p className="rounded-xl border border-navy/10 bg-cream p-3 text-sm" role="status">
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

          <Button type="submit" disabled={enviando} className={cn("w-full rounded-xl", TOQUE_MINIMO)}>
            {enviando ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <LogIn className="h-4 w-4" aria-hidden />}
            Ingresar
          </Button>
          <p className="text-center text-xs text-ink-muted">
            ¿Olvidaste la contraseña? Pedile al Dueño que la resetee.
          </p>
        </form>

        <Link
          href="/"
          className={cn(
            "inline-flex items-center justify-center gap-2 rounded-xl px-3 text-sm text-ink-muted transition-colors hover:text-navy focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy",
            TOQUE_MINIMO,
          )}
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Volver al sitio
        </Link>
      </div>
    </main>
  )
}
