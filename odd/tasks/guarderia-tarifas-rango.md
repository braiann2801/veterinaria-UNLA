# Feature: guarderia-tarifas-rango

Estadía de guardería por rango (entrada/salida con fecha y hora), con monto calculado por horas o por noches según tarifas que ajusta el Dueño.
Rama: `feat/autenticacion` (continúa sobre el PR #1) · Tests: `./mvnw test` (MySQL 3307) + `npx tsc --noEmit` + e2e ego-browser.

## Specs

- **S1** — "el abono de la guarderia no debe ser fija en $10000. debe variar dependiendo: si es por hora o por noche." Regla elegida ("Por horas o por noches"): si entra y sale el mismo día, horas × precio por hora (fracción = hora entera); si pasa al menos una noche, noches × precio por noche.
- **S2** — "el duenio debe poder ajustar estos valores." Precio por hora y por noche editables solo por el Dueño (403 al resto), desde una pantalla.
- **S3** — "dentro del modal de reserva de guarderia quiero que tenga este componente" (DateRangePicker de HeroUI v3 con hora de entrada y salida). Decisión: se usa HeroUI; sin el bloque de demo "Format Options".
- **S4** — "y que se autocalcule el monto a abonar segun los valores que ponga el duenio". El modal muestra el monto mientras se elige el rango; la comanda de GUARDERIA se emite con ese monto.
- **S5** (implicado por S3) — Una estadía de varias noches ocupa cupo en cada día del rango: el aforo de 10 se valida día por día.

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S1, S2 | inline | hecho (215 tests verdes; RED observado) | ver git log |
| T2 | S1, S4, S5 | inline | pendiente | |
| T3 | S3, S4 | inline | pendiente | |
| T4 | — | inline | pendiente | |

T1: tarifas de guardería (hora/noche) + pestaña Tarifas del Dueño. T2: reserva por rango, aforo por día, cotización y comanda con monto calculado. T3: modal con DateRangePicker de HeroUI + monto en vivo. T4: docs (CLAUDE.md, PROGRESS.md).
Riesgo: ítem 4 (locks de aforo en varios días), ítem 1 (columnas nuevas en reserva), ítem 5 (dependencias HeroUI). Verificación independiente al cierre.

## Log

- **L1** (2026-10-28, pedido): "hay un error de flujo, el abono de la guarderia no debe ser fija en $10000. debe variar dependiendo: si es por hora o por noche. el duenio debe poder ajustar estos valores. dentro del modal de reserva de guarderia quiero que tenga este componente [DateRangePicker de HeroUI v3] y que se autocalcule el monto a abonar segun los valores que ponga el duenio"
- **L2**: cálculo = "Por horas o por noches (Recomendado)"; componente = "El de HeroUI que pegaste".
