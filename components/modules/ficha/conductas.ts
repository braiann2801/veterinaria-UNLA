import type { ConductaObservada } from "@/lib/api"

/** Conductas que el profesional registra al atender (`ConsultaMedica.conductaObservada`). */
export const CONDUCTAS: { valor: ConductaObservada; etiqueta: string }[] = [
  { valor: "TRANQUILO", etiqueta: "Tranquilo" },
  { valor: "NERVIOSO", etiqueta: "Nervioso" },
  { valor: "REACTIVO", etiqueta: "Reactivo" },
  { valor: "AGRESIVO", etiqueta: "Agresivo" },
]

/**
 * Conductas que el backend convierte en alerta.
 *
 * Se replica aca solo para el texto del badge. Quien decide es el servidor:
 * aunque esta lista se olvidara de agregar un valor, la alerta seguiria
 * encendiéndose y el badge solo dejaria de mostrarse. Es degradacion
 * cosmetica, no de logica.
 */
export const CONDUCTAS_CON_ALERTA = new Set<ConductaObservada>(["REACTIVO", "AGRESIVO"])
