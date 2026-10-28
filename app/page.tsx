/**
 * Landing pública del negocio (`/`).
 *
 * Es lo que ve cualquiera que tenga el link. No usa la sesión ni la API: el
 * sistema interno vive en `/app` y exige login. Todo el contenido sale de
 * `lib/negocio.ts`, así que personalizarla no requiere tocar este diseño.
 */

import Link from "next/link"
import {
  Clock,
  Dog,
  Mail,
  MapPin,
  MessageCircle,
  PawPrint,
  Phone,
  ShieldCheck,
  Stethoscope,
  UserRound,
  type LucideIcon,
} from "lucide-react"

import { NEGOCIO, type IconoServicio } from "@/lib/negocio"

const ICONOS: Record<IconoServicio, LucideIcon> = {
  stethoscope: Stethoscope,
  dog: Dog,
  shield: ShieldCheck,
}

const BOTON =
  "inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl px-4 text-sm font-medium transition-colors"

export default function Landing() {
  const { contacto } = NEGOCIO
  const whatsapp = `https://wa.me/${contacto.whatsapp}`

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="sticky top-0 z-10 border-b border-border bg-background/90 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <Link href="/" className="flex min-h-[44px] min-w-0 items-center gap-2 font-semibold">
            <PawPrint className="h-5 w-5 shrink-0 text-primary" aria-hidden />
            <span className="truncate">{NEGOCIO.nombre}</span>
          </Link>
          <Link href="/login" className={`${BOTON} shrink-0 border border-input hover:bg-muted`}>
            <UserRound className="h-4 w-4" aria-hidden />
            <span className="hidden sm:inline">Ingreso empleados</span>
            <span className="sm:hidden">Ingresar</span>
          </Link>
        </div>
      </header>

      <main>
        <section className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-12 sm:py-20">
          <h1 className="text-3xl font-bold leading-tight text-balance sm:text-5xl">{NEGOCIO.eslogan}</h1>
          <p className="max-w-2xl text-base text-muted-foreground sm:text-lg">{NEGOCIO.descripcion}</p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <a href={whatsapp} target="_blank" rel="noopener noreferrer" className={`${BOTON} bg-primary text-primary-foreground hover:bg-primary/90`}>
              <MessageCircle className="h-4 w-4" aria-hidden />
              Pedir turno por WhatsApp
            </a>
            <a href={`tel:${contacto.telefono.replace(/[^+\d]/g, "")}`} className={`${BOTON} border border-input hover:bg-muted`}>
              <Phone className="h-4 w-4" aria-hidden />
              Llamar
            </a>
          </div>
        </section>

        <section aria-labelledby="titulo-servicios" className="border-t border-border bg-card/40">
          <div className="mx-auto max-w-5xl px-4 py-12">
            <h2 id="titulo-servicios" className="mb-6 text-xl font-semibold sm:text-2xl">
              Servicios
            </h2>
            <ul className="grid gap-4 sm:grid-cols-3">
              {NEGOCIO.servicios.map((s) => {
                const Icono = ICONOS[s.icono]
                return (
                  <li key={s.titulo} className="flex flex-col gap-2 rounded-xl border border-border bg-card p-5">
                    <Icono className="h-6 w-6 text-primary" aria-hidden />
                    <h3 className="font-semibold">{s.titulo}</h3>
                    <p className="text-sm text-muted-foreground">{s.detalle}</p>
                  </li>
                )
              })}
            </ul>
          </div>
        </section>

        <section className="mx-auto grid max-w-5xl gap-8 px-4 py-12 sm:grid-cols-2">
          <div aria-labelledby="titulo-horarios">
            <h2 id="titulo-horarios" className="mb-4 flex items-center gap-2 text-xl font-semibold">
              <Clock className="h-5 w-5 text-primary" aria-hidden />
              Horarios
            </h2>
            <dl className="divide-y divide-border rounded-xl border border-border">
              {NEGOCIO.horarios.map((h) => (
                <div key={h.dias} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
                  <dt className="text-muted-foreground">{h.dias}</dt>
                  <dd className="font-medium">{h.horas}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div aria-labelledby="titulo-contacto">
            <h2 id="titulo-contacto" className="mb-4 flex items-center gap-2 text-xl font-semibold">
              <MapPin className="h-5 w-5 text-primary" aria-hidden />
              Contacto
            </h2>
            <ul className="flex flex-col gap-1 text-sm">
              <li>
                <a href={`tel:${contacto.telefono.replace(/[^+\d]/g, "")}`} className="flex min-h-[44px] items-center gap-3 hover:text-primary">
                  <Phone className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                  {contacto.telefono}
                </a>
              </li>
              <li>
                <a href={whatsapp} target="_blank" rel="noopener noreferrer" className="flex min-h-[44px] items-center gap-3 hover:text-primary">
                  <MessageCircle className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                  WhatsApp
                </a>
              </li>
              <li>
                <a href={`mailto:${contacto.email}`} className="flex min-h-[44px] items-center gap-3 break-all hover:text-primary">
                  <Mail className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                  {contacto.email}
                </a>
              </li>
              <li className="flex min-h-[44px] items-center gap-3">
                <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                {contacto.direccion}
              </li>
            </ul>
            {contacto.mapa && (
              <a href={contacto.mapa} target="_blank" rel="noopener noreferrer" className={`${BOTON} mt-3 border border-input hover:bg-muted`}>
                <MapPin className="h-4 w-4" aria-hidden />
                Cómo llegar
              </a>
            )}
          </div>
        </section>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <span>
            © {new Date().getFullYear()} {NEGOCIO.nombre}
          </span>
          <Link href="/login" className="inline-flex min-h-[44px] items-center hover:text-foreground">
            Ingreso empleados
          </Link>
        </div>
      </footer>
    </div>
  )
}
