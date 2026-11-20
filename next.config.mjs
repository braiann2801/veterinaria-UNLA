/** @type {import('next').NextConfig} */

// Orígenes locales que usa el proyecto en desarrollo. En producción no se
// hardcodea ningún hosting: el backend real se toma de NEXT_PUBLIC_API_URL.
const ORIGENES_LOCALES = [
  'http://localhost:3000',
  'http://localhost:8080',
  'http://127.0.0.1:3000',
  'http://127.0.0.1:8080',
]

/**
 * Origen del backend para `connect-src`. `NEXT_PUBLIC_API_URL` es la misma
 * variable que usa el navegador para llamar a la API (build-time), así la CSP
 * acompaña al despliegue sin atarse a Vercel, Render ni a un dominio fijo.
 */
function origenDelBackend() {
  const valor = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'
  try {
    return new URL(valor).origin
  } catch {
    return null
  }
}

/**
 * CSP inicial en modo Report-Only: no bloquea nada todavía, solo reporta en la
 * consola del navegador. Se deja `script-src 'unsafe-inline'` y
 * `style-src 'unsafe-inline'` porque Next inyecta scripts de bootstrap y la UI
 * usa estilos inline; el siguiente bloque debe reemplazarlos por nonce/hashes.
 */
function construirCspReportOnly() {
  const backend = origenDelBackend()
  const connectSrc = ["'self'", ...ORIGENES_LOCALES, 'https://vitals.vercel-insights.com']
  if (backend && !connectSrc.includes(backend)) connectSrc.push(backend)

  const scriptSrc = ["'self'", "'unsafe-inline'", 'https://va.vercel-scripts.com']
  // React Refresh de `next dev` necesita eval; en producción no se habilita.
  if (process.env.NODE_ENV !== 'production') scriptSrc.push("'unsafe-eval'")

  return [
    "default-src 'self'",
    `script-src ${scriptSrc.join(' ')}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self' data:",
    `connect-src ${connectSrc.join(' ')}`,
    "worker-src 'self' blob:",
    "frame-src 'none'",
    "frame-ancestors 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "manifest-src 'self'",
    "media-src 'self'",
  ].join('; ')
}

const nextConfig = {
  // Output standalone: genera .next/standalone con un servidor Node mínimo,
  // requerido por el Dockerfile de producción (etapa runner).
  output: 'standalone',
  // Cabeceras de seguridad para todas las rutas. La app no se embebe en
  // iframes, por eso X-Frame-Options es DENY (no SAMEORIGIN).
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          {
            key: 'Permissions-Policy',
            value:
              'camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=(), display-capture=(), midi=(), serial=(), xr-spatial-tracking=()',
          },
          // Report-Only: se observa sin bloquear. No se agrega HSTS forzado
          // para no romper HTTP local ni migraciones de hosting.
          { key: 'Content-Security-Policy-Report-Only', value: construirCspReportOnly() },
        ],
      },
    ]
  },
  // Sin esto, `next dev` agrega un bloque `nextjs-agent-rules` a AGENTS.md en
  // cada arranque. AGENTS.md solo se modifica con aprobación del Dueño (§4.1).
  agentRules: false,
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
}

export default nextConfig
