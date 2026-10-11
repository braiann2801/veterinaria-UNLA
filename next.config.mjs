/** @type {import('next').NextConfig} */
const nextConfig = {
  // Output standalone: genera .next/standalone con un servidor Node mínimo,
  // requerido por el Dockerfile de producción (etapa runner).
  output: 'standalone',
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
}

export default nextConfig
