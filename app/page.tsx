/**
 * Landing pública del negocio (`/`).
 *
 * Es lo que ve cualquiera que tenga el link. No usa la sesión ni la API: el
 * sistema interno vive en `/app` y exige login. Todo el contenido sale de
 * `lib/negocio.ts`, así que personalizarla no requiere tocar este diseño.
 *
 * El diseño es claro (crema/azul marino) aunque el sistema interno sea oscuro:
 * el contenedor `.landing` aísla tipografía y paleta (ver `app/globals.css`).
 */

import type { Metadata } from "next"
import Image from "next/image"
import Link from "next/link"
import {
  CalendarClock,
  ClipboardList,
  Clock,
  Dog,
  Mail,
  MapPin,
  MessageCircle,
  PawPrint,
  Phone,
  ShieldCheck,
  Stethoscope,
  type LucideIcon,
} from "lucide-react"

import { Paw } from "@/components/paw"
import { LandingNav } from "@/components/landing/landing-nav"
import {
  NEGOCIO,
  type ColorEstandar,
  type IconoMotivo,
  type IconoServicio,
} from "@/lib/negocio"
import { cn } from "@/lib/utils"

export const metadata: Metadata = {
  title: `${NEGOCIO.nombreCorto} · Clínica y guardería canina`,
  description: NEGOCIO.descripcion,
}

const ICONOS_SERVICIO: Record<IconoServicio, LucideIcon> = {
  stethoscope: Stethoscope,
  dog: Dog,
  shield: ShieldCheck,
}

const ICONOS_MOTIVO: Record<IconoMotivo, LucideIcon> = {
  stethoscope: Stethoscope,
  dog: Dog,
  clipboard: ClipboardList,
  shield: ShieldCheck,
  calendar: CalendarClock,
  map: MapPin,
}

/** Fondos pastel, en el orden en que se muestran las tarjetas. */
const PASTELES = ["bg-lilac", "bg-blush", "bg-mint"] as const

const FONDOS_ESTANDAR: Record<ColorEstandar, string> = {
  blush: "bg-blush",
  lilac: "bg-lilac",
  mint: "bg-mint",
}

const BOTON_BASE =
  "inline-flex min-h-[44px] items-center justify-center gap-2 rounded-full px-6 text-sm font-semibold transition duration-200 hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2"
const BOTON_PRIMARIO = cn(BOTON_BASE, "bg-navy text-white hover:bg-navy/90 focus-visible:outline-navy")
const BOTON_CLARO = cn(BOTON_BASE, "bg-blush text-navy hover:bg-blush/90 focus-visible:outline-blush")
const BOTON_CONTORNO_CLARO = cn(
  BOTON_BASE,
  "border-2 border-white/60 text-white hover:bg-white hover:text-navy focus-visible:outline-white",
)

export default function Landing() {
  const { contacto } = NEGOCIO
  const whatsapp = `https://wa.me/${contacto.whatsapp}`
  const telefono = `tel:${contacto.telefono.replace(/[^+\d]/g, "")}`

  return (
    <div className="landing min-h-screen bg-paper text-navy">
      <section id="inicio" className="relative overflow-hidden bg-cream pb-20 sm:pb-32">
        <Paw className="absolute left-[52%] top-5 w-[120px] rotate-[-10deg] text-blush/80 sm:w-[200px]" />
        <Paw className="absolute right-[4%] top-[52%] hidden w-[110px] rotate-[20deg] text-lilac sm:block" />
        <Paw className="absolute -bottom-6 left-[36%] w-[100px] rotate-[-15deg] text-mint sm:w-[150px]" />

        <LandingNav />

        <div className="relative z-[2] mx-auto grid w-full max-w-[1120px] items-center gap-6 px-6 md:grid-cols-[1.05fr_1fr]">
          <div className="text-center md:text-left">
            <h1 className="mb-5 font-display text-4xl font-bold leading-[1.05] sm:text-6xl lg:text-7xl">
              Clínica y guardería
              <br />
              en un solo lugar
            </h1>
            <p className="mx-auto mb-8 max-w-[420px] text-base text-ink-muted sm:text-lg md:mx-0">
              {NEGOCIO.descripcion}
            </p>
            <a href={whatsapp} target="_blank" rel="noopener noreferrer" className={BOTON_PRIMARIO}>
              <MessageCircle className="h-4 w-4" aria-hidden />
              Reservar turno
            </a>
          </div>
          {/* WebP con fondo transparente (PNG equivalente pesaba 882 KB y este
              114 KB). Se apoya directo sobre el crema, sin marco ni blend. */}
          <Image
            src="/landing/hero.webp"
            alt="Perro en la veterinaria y guardería canina"
            width={717}
            height={1033}
            priority
            className="mx-auto w-full max-w-[300px] md:ml-auto md:max-w-[380px]"
          />
        </div>
      </section>

      {/* Tarjeta que monta sobre el hero (mismo recurso que el diseño original). */}
      <div
        id="servicios"
        className="relative z-[4] mx-4 -mt-16 max-w-[880px] scroll-mt-24 rounded-2xl bg-white px-6 py-7 shadow-[0_20px_50px_-20px_rgba(35,42,92,0.25)] sm:mx-6 sm:px-8 md:mx-auto md:px-9"
      >
        <ul className="grid gap-6 md:grid-cols-3">
          {NEGOCIO.servicios.map((servicio, indice) => {
            const Icono = ICONOS_SERVICIO[servicio.icono]
            return (
              <li key={servicio.titulo} className="flex items-center gap-4">
                <span
                  className={cn(
                    "flex h-14 w-14 shrink-0 items-center justify-center rounded-full",
                    PASTELES[indice % PASTELES.length],
                  )}
                >
                  <Icono className="h-7 w-7 text-navy" aria-hidden />
                </span>
                <div>
                  <h2 className="font-display text-lg font-semibold leading-tight sm:text-xl">{servicio.titulo}</h2>
                  <p className="text-sm leading-snug text-ink-muted">{servicio.detalle}</p>
                </div>
              </li>
            )
          })}
        </ul>
      </div>

      <section id="estandares" className="scroll-mt-20 py-16 sm:py-24">
        <div className="mx-auto w-full max-w-[1120px] px-6">
          <h2 className="mb-12 text-center font-display text-3xl font-semibold sm:mb-16 sm:text-4xl lg:text-[2.75rem]">
            Nuestros estándares de cuidado
          </h2>


          <div className="mx-auto flex max-w-[900px] flex-col gap-12 sm:gap-14">
            {NEGOCIO.estandares.map((estandar, indice) => (
              <article
                key={estandar.titulo}
                className="grid items-center gap-8 md:grid-cols-[1fr_1.2fr] md:gap-16"
              >
                <div
                  className={cn(
                    "mx-auto aspect-square w-full max-w-[320px] overflow-hidden rounded-full",
                    FONDOS_ESTANDAR[estandar.color],
                    indice % 2 === 1 && "md:order-2",
                  )}
                >
                  <Image
                    src={estandar.imagen}
                    alt={estandar.alt}
                    width={1024}
                    height={1024}
                    className="h-full w-full object-cover"
                  />
                </div>
                <div className="text-center md:text-left">
                  <h3 className="mb-3 font-display text-2xl font-semibold sm:text-3xl">{estandar.titulo}</h3>
                  <p className="mx-auto max-w-[420px] text-sm text-ink-muted sm:text-base md:mx-0">
                    {estandar.detalle}
                  </p>
                </div>
              </article>
            ))}
          </div>

          <div className="mt-12 text-center">
            <a href={whatsapp} target="_blank" rel="noopener noreferrer" className={BOTON_PRIMARIO}>
              Reservar turno
            </a>
          </div>
        </div>
      </section>

      <section id="motivos" className="scroll-mt-20 bg-cream py-16 sm:py-24">
        <div className="mx-auto w-full max-w-[1120px] px-6">
          <h2 className="mb-12 text-center font-display text-3xl font-semibold sm:mb-16 sm:text-4xl lg:text-[2.75rem]">
            ¿Por qué elegirnos?
          </h2>

          <ul className="mx-auto mb-12 grid max-w-[820px] grid-cols-2 gap-8 md:grid-cols-3 md:gap-x-8 md:gap-y-12">
            {NEGOCIO.motivos.map((motivo, indice) => {
              const Icono = ICONOS_MOTIVO[motivo.icono]
              return (
                <li key={motivo.titulo} className="text-center">
                  <span
                    className={cn(
                      "mx-auto mb-3 flex h-16 w-16 items-center justify-center rounded-full",
                      PASTELES[indice % PASTELES.length],
                    )}
                  >
                    <Icono className="h-7 w-7 text-navy" aria-hidden />
                  </span>
                  <h3 className="mb-1 font-landing text-base font-semibold">{motivo.titulo}</h3>
                  <p className="mx-auto max-w-[210px] text-sm text-ink-muted">{motivo.detalle}</p>
                </li>
              )
            })}
          </ul>

          <div className="text-center">
            <a href={whatsapp} target="_blank" rel="noopener noreferrer" className={BOTON_PRIMARIO}>
              Reservar turno
            </a>
          </div>
        </div>
      </section>

      <section id="contacto" className="relative scroll-mt-20 overflow-hidden bg-navy py-16 text-white sm:py-20">
        <Paw className="absolute left-[4%] top-5 w-[100px] rotate-[25deg] text-white/10 sm:w-[160px]" />
        <Paw className="absolute bottom-3 right-[6%] w-[80px] rotate-[-20deg] text-white/10 sm:w-[120px]" />
        <Paw className="absolute right-[20%] top-2 hidden w-[60px] text-white/10 sm:block" />

        <div className="relative z-10 mx-auto w-full max-w-[1120px] px-6 text-center">
          <h2 className="mb-3 font-display text-2xl font-semibold sm:text-3xl">Reservá el lugar de tu perro</h2>
          <p className="mx-auto mb-8 max-w-[480px] text-sm text-white/75 sm:text-base">
            Escribinos y coordinamos la consulta o la estadía en guardería. Los cupos son limitados:
            hasta 10 perros por día.
          </p>

          <div className="flex flex-col items-center justify-center gap-3 sm:flex-row">
            <a href={whatsapp} target="_blank" rel="noopener noreferrer" className={BOTON_CLARO}>
              <MessageCircle className="h-4 w-4" aria-hidden />
              Pedir turno por WhatsApp
            </a>
            <a href={telefono} className={BOTON_CONTORNO_CLARO}>
              <Phone className="h-4 w-4" aria-hidden />
              Llamar
            </a>
          </div>

          <div className="mx-auto mt-12 grid max-w-[920px] gap-8 text-left sm:grid-cols-3">
            <div>
              <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-white">
                <Clock className="h-4 w-4 shrink-0 text-blush" aria-hidden />
                Horarios
              </h3>
              <ul className="space-y-1 text-sm text-white/70">
                {NEGOCIO.horarios.map((horario) => (
                  <li key={horario.dias} className="flex justify-between gap-3">
                    <span>{horario.dias}</span>
                    <span className="shrink-0 text-white/90">{horario.horas}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-white">
                <MapPin className="h-4 w-4 shrink-0 text-mint" aria-hidden />
                Dirección
              </h3>
              <address className="text-sm not-italic text-white/70">{contacto.direccion}</address>
              {contacto.mapa && (
                <a
                  href={contacto.mapa}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-2 inline-flex min-h-[44px] items-center gap-2 text-sm text-white underline decoration-white/40 underline-offset-4 hover:decoration-white"
                >
                  Cómo llegar
                </a>
              )}
            </div>

            <div>
              <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-white">
                <Mail className="h-4 w-4 shrink-0 text-lilac" aria-hidden />
                Contacto
              </h3>
              <ul className="flex flex-col text-sm text-white/70">
                <li>
                  <a href={`mailto:${contacto.email}`} className="inline-flex min-h-[44px] items-center break-all hover:text-white">
                    {contacto.email}
                  </a>
                </li>
                <li>
                  <a href={telefono} className="inline-flex min-h-[44px] items-center hover:text-white">
                    {contacto.telefono}
                  </a>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      <footer className="bg-paper">
        <div className="mx-auto flex w-full max-w-[1120px] flex-col gap-3 px-6 py-8 sm:flex-row sm:items-center sm:justify-between">
          <a href="#inicio" className="flex min-h-[44px] items-center gap-2 font-display text-sm font-semibold">
            <PawPrint className="h-5 w-5 shrink-0 text-navy" aria-hidden />
            {NEGOCIO.nombreCorto}
          </a>

          <nav aria-label="Pie de página">
            <ul className="flex flex-wrap items-center gap-x-7 gap-y-1 text-xs text-ink-muted">
              <li>
                <a href="#servicios" className="inline-flex min-h-[44px] items-center hover:text-navy">
                  Servicios
                </a>
              </li>
              <li>
                <a href="#estandares" className="inline-flex min-h-[44px] items-center hover:text-navy">
                  Cuidado
                </a>
              </li>
              <li>
                <a href="#contacto" className="inline-flex min-h-[44px] items-center hover:text-navy">
                  Contacto
                </a>
              </li>
            </ul>
          </nav>

          <Link href="/login" className="inline-flex min-h-[44px] items-center text-xs text-ink-muted hover:text-navy">
            Ingreso empleados
          </Link>
        </div>
      </footer>
    </div>
  )
}
