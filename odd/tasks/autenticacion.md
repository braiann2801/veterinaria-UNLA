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
| T1 | S2, S3, S5 | inline | hecho (178 tests verdes, RED observado; revisión RDD aprobada) | 7a48f45, b6f4eb5 |
| T2 | S3, S4 | inline | hecho (187 tests verdes, RED 8/9 observado; revisión RDD declinada) | 182c940 |
| T3 | S2, S3, S5 | inline | hecho (tsc + next build + e2e ego-browser; sin runner de tests en frontend) | ab06425, a0cfbb5 |
| T4 | S4 | inline | hecho (e2e: alta, clave temporal, baja revoca token) | ab06425, a0cfbb5 |
| T5 | S1 | inline | hecho (e2e desktop y 375 px) | 4470498 |
| T6 | S2, S5 | inline | hecho (compose exige JWT_SECRET y OWNER_PASSWORD; .env.example bloqueado por permisos) | ver git log |

## Log

- **L1** (2026-10-28, pedido original): "La idea es que esta aplicación esté desplegada en la red. Y que tanto el dueño como el veterinario, la recepción o la guardería, pueda acceder a través de una página web desde sus celulares. El problema que tenemos ahora es que, al acceder al link, cualquier persona puede ver todo el sistema. Lo cual está mal. La persona que tenga el link del negocio debería poder ver una landing page del negocio. Y los empleados deberían acceder a través de un usuario y contraseña para ingresar al sistema."
- **L2** (2026-10-28): "Las cuentas de los empleados las crea el dueño del negocio con una pantalla para dar de alta, cambiar el rol, desactivar cuentas y resetear contraseñas. La primera cuenta, la del dueño, la crea en el backend. Con una contraseña defaul, La primera tarea del dueño al ingresar al sistema es cambiar su propia contraseña."
- **L3** (2026-10-28): rol Guardería = "Igual que Recepción"; landing = "Plantilla editable".
- **L4**: ruta inline en todas las tareas (sin razón de writer: las unidades dependen del mismo contrato de auth). Verificación independiente al cierre por ítem 2 (seguridad).
- **L5** (T1): tests de backend corren contra el contenedor MySQL del 3307 con `-Dspring.datasource.url=jdbc:mysql://localhost:3307/veterinaria_test?...` (el 3306 local es otro MySQL con contraseña).
- **L6** (T1, revisión RDD `review-b94f726e9d50a514`, aprobada y confirmada): bloqueante R1-default-jwt-secret (secreto JWT versionado) corregido en `b6f4eb5` — sin `JWT_SECRET` se genera una clave aleatoria por arranque. Avisos no bloqueantes a resolver: R1-default-owner-takeover → T6 (OWNER_PASSWORD obligatorio en compose); R1-login-no-throttle → ticket Kaban; R3-bcrypt-byte-limit (72 chars ≠ 72 bytes) → T2; R3-seed-hijacks-existing-account (la semilla promueve una cuenta no-Dueño con el mismo username) → T2; R3-untested-deactivation-revocation → tests de T2.
- **L7** (T2): revisión RDD declinada por el usuario. Avisos de T1 resueltos: semilla no promueve cuentas no-Dueño, límite de 72 bytes en la clave nueva, test de revocación por baja.
- **L8** (T3-T5, revisión RDD `review-50b038e343b5bc95`, aprobada y confirmada): avisos resueltos en `a0cfbb5` (expiraEn inválido = vencido, evento `storage` entre pestañas, sin reset de la propia cuenta y error de recarga separado del reset). Quedan: token en localStorage (tradeoff aceptado) y falta de tests de frontend (ticket Kaban existente). R3-401-cambiar-password no aplica: el backend responde 400 a la clave actual incorrecta.
- **L9** (verificación e2e ego-browser): landing sin sistema; /app→/login; clave incorrecta muestra error; Dueño default forzado a cambiar; alta de Recepción con clave temporal; Recepción sin Balance/Usuarios y API 403 en cierre/usuarios; baja → token viejo 401; 375 px sin scroll horizontal.
- **L10** (T6): `.env.example` no se pudo editar (permisos); el usuario debe agregar `JWT_SECRET=` y `OWNER_PASSWORD=`.
