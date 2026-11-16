"use client"

/**
 * Campo de contraseña con botón para ver lo que se está escribiendo.
 *
 * <p>Se escribe a ciegas y un error de tipeo no se ve: en el login se traduce en
 * "usuario o contraseña incorrectos" y, tras cinco intentos, en un bloqueo de
 * 15 minutos. El ojo deja revisar la clave antes de enviarla.</p>
 *
 * <p>Es el mismo control que `TextInput` —no un input nuevo— para que el foco,
 * el borde y la altura sean idénticos al resto de los formularios: solo agrega
 * el botón y el relleno de la derecha. Quien lo usa no maneja el estado de
 * visibilidad ni el `type`; sigue recibiendo todas las props del input
 * (`autoComplete`, `required`, `value`, `onChange`).</p>
 *
 * <p>Vuelve a oculto cada vez que se monta: una clave no debe quedar a la vista
 * porque el formulario se reabrió.</p>
 */

import { useState, type InputHTMLAttributes } from "react"
import { Eye, EyeOff } from "lucide-react"

import { cn } from "@/lib/utils"
import { TextInput } from "@/components/ui/field"

export function PasswordInput({
  className,
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, "type">) {
  const [visible, setVisible] = useState(false)
  const etiqueta = visible ? "Ocultar contraseña" : "Mostrar contraseña"

  return (
    <div className="relative">
      <TextInput {...props} type={visible ? "text" : "password"} className={cn("pr-12", className)} />
      <button
        type="button"
        // Sin `preventDefault` el click movería el foco al botón y se cortaría
        // la escritura: destapar la clave no debe interrumpir lo que se tipea.
        onMouseDown={(evento) => evento.preventDefault()}
        onClick={() => setVisible((actual) => !actual)}
        aria-label={etiqueta}
        aria-pressed={visible}
        title={etiqueta}
        className={cn(
          "absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-xl text-muted-foreground",
          "transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none focus-visible:ring-inset",
        )}
      >
        {/* aria-hidden: el botón ya se anuncia por `aria-label`. */}
        {visible ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
      </button>
    </div>
  )
}
