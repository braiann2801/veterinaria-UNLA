/** @type {import('next').NextConfig} */
const nextConfig = {
  // Output standalone: genera .next/standalone con un servidor Node mínimo,
  // requerido por el Dockerfile de producción (etapa runner).
  output: 'standalone',
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
