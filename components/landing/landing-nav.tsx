"use client"

/**
 * Navegación de la landing pública.
 *
 * En escritorio los links están siempre visibles; en móvil se colapsan en un
 * menú hamburguesa (`< md`) para no romper el layout ni dejar opciones fuera de
 * alcance del pulgar. El ingreso de empleados queda visible en ambos casos.
 */

import { useState } from "react"
import Link from "next/link"
import { Menu, PawPrint, X } from "lucide-react"

import { NEGOCIO } from "@/lib/negocio"

const ENLACES = [
  { href: "#servicios", label: "Servicios" },
  { href: "#estandares", label: "Cuidado" },
  { href: "#motivos", label: "Nosotros" },
  { href: "#contacto", label: "Contacto" },
] as const

const BOTON_INGRESO =
  "inline-flex min-h-[44px] shrink-0 items-center justify-center rounded-full border-2 border-navy px-4 text-sm font-medium text-navy transition-colors hover:bg-navy hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy"

export function LandingNav() {
  const [abierto, setAbierto] = useState(false)

  return (
    <nav aria-label="Principal" className="relative z-10">
      <div className="mx-auto flex w-full max-w-[1120px] items-center justify-between gap-3 px-6 py-5 sm:py-7">
        <a href="#inicio" className="flex min-h-[44px] items-center gap-2">
          <PawPrint className="h-6 w-6 shrink-0 text-navy" aria-hidden />
          <span className="font-display text-base font-semibold sm:text-lg">{NEGOCIO.nombreCorto}</span>
        </a>

        <ul className="hidden items-center gap-10 text-sm text-ink-muted md:flex">
          {ENLACES.map((enlace) => (
            <li key={enlace.href}>
              <a
                href={enlace.href}
                className="inline-flex min-h-[44px] items-center transition-colors hover:text-navy focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy"
              >
                {enlace.label}
              </a>
            </li>
          ))}
        </ul>

        <div className="flex items-center gap-2">
          <Link href="/login" className={BOTON_INGRESO}>
            <span className="hidden sm:inline">Ingreso empleados</span>
            <span className="sm:hidden">Ingresar</span>
          </Link>
          <button
            type="button"
            onClick={() => setAbierto((valor) => !valor)}
            aria-expanded={abierto}
            aria-controls="menu-movil"
            aria-label={abierto ? "Cerrar menú" : "Abrir menú"}
            className="flex h-11 w-11 items-center justify-center rounded-full border-2 border-navy text-navy transition-colors hover:bg-navy hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy md:hidden"
          >
            {abierto ? <X className="h-5 w-5" aria-hidden /> : <Menu className="h-5 w-5" aria-hidden />}
          </button>
        </div>
      </div>

      {abierto && (
        <div id="menu-movil" className="border-t border-navy/10 bg-cream md:hidden">
          <ul className="mx-auto flex w-full max-w-[1120px] flex-col px-6 py-2 text-sm">
            {ENLACES.map((enlace) => (
              <li key={enlace.href}>
                <a
                  href={enlace.href}
                  onClick={() => setAbierto(false)}
                  className="flex min-h-[44px] items-center border-b border-navy/10 text-ink-muted last:border-b-0 hover:text-navy"
                >
                  {enlace.label}
                </a>
              </li>
            ))}
          </ul>
        </div>
      )}
    </nav>
  )
}
