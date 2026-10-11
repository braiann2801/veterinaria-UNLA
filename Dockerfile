# ============================================================================
# Frontend Next.js — build multi-stage con pnpm + output standalone
#
# STAGE 1: instala dependencias con pnpm (lockfile autoritativo) y compila.
# STAGE 2: imagen mínima de producción que solo corre el servidor standalone
#          generado por Next.js (.next/standalone).
# ============================================================================

# ---------- STAGE 1: dependencias + build ----------
FROM node:22-alpine AS build
WORKDIR /app

# corepack activa la versión de pnpm declarada en package.json (`packageManager`).
COPY package.json pnpm-lock.yaml ./
RUN corepack enable \
  && corepack prepare pnpm@12.3.4 --activate \
  && pnpm install --frozen-lockfile

# Copia el resto del código (node_modules, .next, .env quedan fuera por .dockerignore)
COPY . .

# NEXT_PUBLIC_* se inlina en build-time. El browser llama a la API en el puerto
# 8080 mapeado al host; se puede sobrescribir pasando --build-arg.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

# Paridad con el script de package.json: next build --webpack
RUN pnpm build

# ---------- STAGE 2: runtime standalone ----------
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# El servidor standalone trae su propio node_modules mínimo.
COPY --from=build /app/.next/standalone ./
# Assets estáticos + carpeta public (favicons, placeholders del frontend).
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:3000/ >/dev/null || exit 1

CMD ["node", "server.js"]