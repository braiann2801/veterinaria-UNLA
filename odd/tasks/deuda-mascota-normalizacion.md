# Feature: deuda-mascota-normalizacion

Kanban tickets 1, 2, 3 and 5, debt tied to the pet instead of the tutor, and Argentine normalization of personal data.
Branch: `feat/deuda-mascota-normalizacion` · Tests: `./mvnw clean test` (backend), `npx tsc --noEmit` + `next build --webpack` (frontend).
The user is away studying: product decisions without an answer are taken as documented assumptions (Log L3) and left to review.

## Specs

- **S1** — Kanban ticket 1 "Agrandar botón Cerrar del Modal a 44x44 px" and ticket 3 "Botones del Mostrador con área táctil de 44 px" (AGENTS.md §3.4).
- **S2** — Kanban ticket 2 "Cotutor: distinguir padrón vacío de error de carga": "cargar-cliente-modal.tsx:120-125 usa [] tanto para padrón vacío como para fallo de GET /tutores, y :445 muestra 'No se pudo leer el padrón...' con la base vacía y mientras carga. Separar estados cargando / vacío / error".
- **S3** — "La deuda debe estar atada a la mascota, no al dueño. Es decir, si un dueño tiene dos mascotas y paga la deuda de la primera mascota, esta mascota ya se puede retirar. En cambio, en la otra no."
- **S4** — "quiero que todos los campos estén normalizados, es decir, si una persona escribe su nombre y su apellido todo en minúscula, que se escriba con la primera letra en mayúscula. De igual forma, hay que normalizar: la matrícula, el DNI, el número de teléfono. Según la normativa de Argentina."
- **S5** — Kanban ticket 5 "README completo con instalación y primer ingreso del Dueño": "Agregar: cómo levantar con Docker (.env con MYSQL_ROOT_PASSWORD, JWT_SECRET, OWNER_PASSWORD), primer ingreso del Dueño (usuario OWNER_USERNAME, por defecto 'duenio', con la c[lave inicial y cambio obligatorio)]".

## Tasks

| ID | Specs | Route | Status | Commit |
|---|---|---|---|---|
| T1 | S1 | inline | done (tsc + next build) | 76d3d53 |
| T2 | S2 | inline | done (tsc; no frontend test runner) | 30f759e |
| T3 | S3 | inline | done (RED 5/5 observed, 232 backend tests green) | c59d283 |
| T4 | S4 | inline (worker unavailable) | done (RED 12/12 observed, 244 backend tests green, tsc + next build) | 8b6e3b4 |
| T5 | S5 | inline | done (claims checked against application.properties, docker-compose.yml, SecurityConfig) | e44f9c8 |
| T6 | — | inline | done: PROGRESS.md updated; Kanban tickets 1, 2, 3, 5 moved to Done | this commit |

## Log

- **L1** (2026-10-29, original request): "quiero dejarte trabajando tengo que estudiar hace el tiket 2, 5. Luego necesito que corrijas unas cosas. La deuda debe estar atada a la mascota, no al dueño. Es decir, si un dueño tiene dos mascotas y paga la deuda de la primera mascota, esta mascota ya se puede retirar. En cambio, en la otra no. Luego quiero que todos los campos estén normalizados, es decir, si una persona escribe su nombre y su apellido todo en minúscula, que se escriba con la primera letra en mayúscula. De igual forma, hay que normalizar: - la matrícula - el DNI - el número de teléfono Según la normativa de Argentina."
- **L2**: tickets 1 and 3 had been requested earlier in the session ("ticket 1 y 3") and were already done on `main` uncommitted; they become T1 of this branch.
- **L3** (assumptions, user unavailable):
  - S3: the pet's debt is the pending balance of the comandas emitted for that pet, whoever the tutor is. Comandas without a pet (concept OTRO without `mascotaId`) do not block any pet's checkout; they still show as tutor debt in Caja.
  - S4 normalization applies to new writes (create/update) only. Existing rows are not rewritten (that would be a stored-data rewrite, high risk); they get normalized the next time they are edited.
  - Names (tutor, professional, user, pet, breed): trim, collapse spaces, first letter of each word in uppercase; Spanish particles (de, del, la, las, los, y, e, da, di, van, von) stay lowercase unless first.
  - DNI: digits only, dots and spaces removed ("30.123.456" -> "30123456"), 7 or 8 digits (RENAPER format). The UI shows it as 30.123.456.
  - Phone: stored as the 10-digit national number (area code + subscriber, ENACOM numbering plan), removing +54, the mobile 9 after 54, the trunk 0 and separators. The 15 mobile prefix cannot be removed safely without the area code table, so a 12-digit number is rejected with a message asking to drop it.
  - Matrícula: uppercase, dots removed, spaces collapsed, a space between the letter prefix and the number ("m.p. 1234" -> "MP 1234"). Provincial license formats differ, so no prefix is invented.
  - Email: trim and lowercase.
- **L4** (environment): backend tests run against the Docker container `veterinaria-db-local` on port 3307 (root without password): `./mvnw -q test "-Dspring.datasource.url=jdbc:mysql://localhost:3307/veterinaria_test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&characterEncoding=UTF-8"`. The MySQL on 3306 has a password and rejects the default test config. Baseline: 227 green.
- **L5** (T3): pet debt = `ComandaCobroService.consultarDeudaDeMascota` (new `DeudaMascotaResponseDTO`); egreso uses it; `etiquetaMotivo` is now "Mascota con deuda pendiente" and the message names the pet. `GET /tutores/{id}/deuda` keeps the tutor total and each pending comanda now carries `mascotaId`/`nombreMascota`; Caja shows the pet per comanda.
- **L6** (T4 design): normalization lives in one helper class and runs in the request DTO compact constructors, so the Bean Validation annotations check the normalized value. Separators only are removed from DNI/phone (letters stay, so length/format errors still fire). Matrícula keeps hyphens ("MP-ABM-9" stays); the "PREFIX NUMBER" space is inserted only when the whole value is letters followed by digits.
- **L7** (T4): the gentle-ai-worker launched for T4 failed before any tool call ("assistant reported an error"); not retried, T4 continues inline with the same handoff.
- **L8** (T4): existing tests changed on purpose: `TutorMascotaRequestValidationTest` and `TutorControllerTest` (new DNI/phone messages), `ReservaGuarderiaHorarioTest` (pet "SinHora" now normalizes to "Sinhora"; data renamed to "Sin Hora"), `EgresoSeguroTest` (T3: comandas now carry `mascotaId`; label "Mascota con deuda pendiente").
- **L9** (slice boundary): the branch is ~1,100 authored lines over 5 commits, above the ~400-line slice. Each commit is one reviewable unit: 76d3d53 + 30f759e (tickets 1-3, UI), c59d283 (pet debt), 8b6e3b4 (normalization, ~610 lines with ~300 of tests), e44f9c8 (README). If the reviewer wants smaller PRs, split as chained PRs on those boundaries.
- **L10** (Kanban): no kaban CLI on the machine; `.kaban/board.db` (tracked in git) was updated with one SQL transaction (column_id=done, version+1, completed_at), integrity_check ok. Ticket 4 (pool exhaustion) and 6 (edit/delete turnos) stay in Todo.
- **L11** (close): Risk item 3 named (stricter DNI 7-8 / phone 10-digit validation applies when editing pre-existing rows). The independent gentle-ai-verify run failed before any tool call ("assistant reported an error"); not retried. Native assess: medium, reviewDue false. Needs the user decision: accept with the self-verification (244 backend tests, tsc, next build) or run the verification/review again.
