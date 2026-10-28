# Feature: autenticacion

Landing publica + login de empleados con roles, con permisos aplicados en el backend.
Rama: `feat/autenticacion` · Entrega: ask-on-risk · Tests: `./mvnw test` (backend), `npx tsc --noEmit` + `next build --webpack` (frontend).
Plan aprobado: `~/.claude/plans/shiny-shimmying-nautilus.md`.

## Specs

- **S1** — "La persona que tenga el link del negocio debería poder ver una landing page del negocio." Landing publica en `/`, plantilla editable con los datos en `lib/negocio.ts`.
- **S2** — "los empleados deberían acceder a través de un usuario y contraseña para ingresar al sistema." El sistema vive en `/app`; sin sesion redirige a `/login`. La API responde 401 sin token valido.
- **S3** — Roles DUENIO, VETERINARIO, RECEPCION, GUARDERIA. Guarderia: "Igual que Recepción". Balance, Usuarios, cierre de caja y edicion de tarifas: solo DUENIO (403 al resto).
- **S4** — "Las cuentas de los empleados las crea el dueño del negocio con una pantalla para dar de alta, cambiar el rol, desactivar cuentas y resetear contraseñas." Desactivar, resetear o cambiar el rol invalida los tokens vigentes. No se puede dejar el sistema sin un DUENIO activo (409).
- **S5** — "La primera cuenta, la del dueño, la crea en el backend. Con una contraseña defaul, La primera tarea del dueño al ingresar al sistema es cambiar su propia contraseña." Mientras `debeCambiarPassword=true`, solo `/auth/me` y `/auth/cambiar-password` (403 el resto).

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S2, S3, S5 | inline | hecho (175 tests verdes, RED por compilación observado) | ver git log |
| T2 | S3, S4 | inline | pendiente | |
| T3 | S2, S3, S5 | inline | pendiente | |
| T4 | S4 | inline | pendiente | |
| T5 | S1 | inline | pendiente | |
| T6 | S2, S5 | inline | pendiente | |

## Log

- **L1** (2026-10-28, pedido original): "La idea es que esta aplicación esté desplegada en la red. Y que tanto el dueño como el veterinario, la recepción o la guardería, pueda acceder a través de una página web desde sus celulares. El problema que tenemos ahora es que, al acceder al link, cualquier persona puede ver todo el sistema. Lo cual está mal. La persona que tenga el link del negocio debería poder ver una landing page del negocio. Y los empleados deberían acceder a través de un usuario y contraseña para ingresar al sistema."
- **L2** (2026-10-28): "Las cuentas de los empleados las crea el dueño del negocio con una pantalla para dar de alta, cambiar el rol, desactivar cuentas y resetear contraseñas. La primera cuenta, la del dueño, la crea en el backend. Con una contraseña defaul, La primera tarea del dueño al ingresar al sistema es cambiar su propia contraseña."
- **L3** (2026-10-28): rol Guardería = "Igual que Recepción"; landing = "Plantilla editable".
- **L4**: ruta inline en todas las tareas (sin razón de writer: las unidades dependen del mismo contrato de auth). Verificación independiente al cierre por ítem 2 (seguridad).
