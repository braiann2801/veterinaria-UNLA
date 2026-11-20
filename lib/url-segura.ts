/**
 * Validación preventiva de URLs que vienen de configuración o datos externos.
 *
 * La landing usa valores estáticos de `lib/negocio.ts`, pero si en el futuro
 * pasan a un CMS o a la API, un `javascript:` o un `data:` en un `href` sería
 * XSS directo. Este helper deja pasar solo los protocolos esperados y rutas
 * relativas; todo lo demás devuelve `null` para que la UI no lo renderice.
 */

const PROTOCOLOS_PERMITIDOS = new Set(["http:", "https:", "mailto:", "tel:"])

export function urlSegura(valor: string | null | undefined): string | null {
  if (!valor) return null

  const limpio = valor.trim()
  if (!limpio) return null

  // Caracteres de control y barras invertidas: los navegadores las normalizan
  // de formas sorprendentes ("java\nscript:", "\\evil.com", "//evil.com").
  if (/[\u0000-\u001F\u007F\\]/.test(limpio)) return null
  if (limpio.startsWith("//")) return null

  // Rutas relativas, anclas y query strings: no tienen esquema y se aceptan.
  if (limpio.startsWith("/") || limpio.startsWith("#") || limpio.startsWith("?")) {
    return limpio
  }

  try {
    const url = new URL(limpio)
    return PROTOCOLOS_PERMITIDOS.has(url.protocol) ? limpio : null
  } catch {
    // Sin esquema ni base: no es una URL absoluta válida.
    return null
  }
}
