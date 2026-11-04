# Feature: urgencias

Turnos de urgencia sin datos (se completan antes de cobrar) y plaza extra de guardería para urgencia clínica, habilitada por el Veterinario.
Rama: `feat/urgencias` · Tests: `./mvnw test` contra el contenedor del 3307, `npx tsc --noEmit` + `next build --webpack`.

## Specs

- **S1** — "Agendar un turno debe admitir turnos de urgencia, donde no se pidan ningún tipo de datos. Esto lo haremos con un toogle al momento de agendar los turnos, si toogle true, se reserva turno para la fecha y la hora actual."
- **S2** — "Los datos deben cargarse antes de abonar el servicio."
- **S3** — "si y solo si, el Veterinario lo habilita, puede expandir la capacidad máxima de la guardería a 1 plaza extra destinada a la urgencia clinica. Dicho cupo debe permanecer bloqueado para el flujo de reservas. esta plaza extra no afecta a las 10 disponibles, es totalmente independiente"

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S1, S3 | inline | hecho: excepciones en AGENTS.md (confirmadas) | d8c945d |
| T2 | S1, S2 | inline | hecho: backend turno de urgencia, atención sin datos, comanda sin titular que no se cobra, completar datos + tests (RED 7/8, 303 verdes) | d6cca4d |
| T3 | S1, S2 | inline | hecho: frontend toggle de urgencia, Atender con profesional, Completar datos, Caja bloquea el cobro sin titular (tsc + build) | a8270e1 |
| T4 | S3 | inline | hecho: backend plaza de urgencia por día (solo Veterinario) + tests (RED 7/7, 310 verdes) | 41a6653 |
| T5 | S3 | inline | hecho: frontend plaza de urgencia en Agenda guardería (tsc + build) | 61da861 |
| T6 | — | inline + verify (e2e) | hecho: README, PROGRESS.md, e2e S1-S3 en `veterinaria_e2e` (descartada) | 78274d7 + este |

## Log

- **L1** (2026-11-03, pedido original): "Agendar un turno debe admitir turnos de urgencia, donde no se pidan ningún tipo de datos. Esto lo haremos con un toogle al momento de agendar los turnos, si toogle true, se reserva turno para la fecha y la hora actual. Los datos deben cargarse antes de abonar el servicio. Por otro lado, si y solo si, el Veterinario lo habilita, puede expandir la capacidad máxima de la guardería a 1 plaza extra destinada a la urgencia clinica. Dicho cupo debe permanecer bloqueado para el flujo de reservas. esta plaza extra no afecta a las 10 disponibles, es totalmente independiente"
- **L2** (decisiones del usuario): urgencia sin profesional (se define al atender); puede superponerse con turnos agendados; se puede atender y cargar la ficha sin datos, que se completan antes de cobrar; la plaza extra se habilita por día y solo la habilita el Veterinario.
- **L3** (gobernanza): las dos funcionalidades contradicen las reglas 1 y 2 de AGENTS.md. Se propuso un diff con dos excepciones y el usuario respondió "si" a "¿braiann2801 confirma este cambio en AGENTS.md?". Aplicado en T1.
- **L4** (supuestos de diseño):
  - Turno de urgencia: `urgencia=true`, mascota y profesional nulos, inicio = ahora, motivo "Urgencia". Sin chequeo de solapamiento.
  - Atender una urgencia pide el profesional. Si todavía no hay mascota, la ficha se guarda sin mascota y la comanda (consulta + extras) se emite sin titular: aparece en Caja pero cobrarla da 400 hasta completar los datos.
  - Completar datos: cliente nuevo o existente + mascota nueva o existente (mismo modelo que el alta combinada). Asigna la mascota al turno, a la ficha y a la comanda, y le pone de titular al tutor autorizado. Todo en una transacción.
  - Esquema: Hibernate `ddl-auto=update` no relaja `NOT NULL` en tablas existentes; un componente al arrancar hace `ALTER ... NULL` solo si la columna sigue siendo `NOT NULL` (sin reescribir datos).
  - Plaza de urgencia: entidad por día (`plaza_urgencia_guarderia`, fecha única). Habilitar, deshabilitar y ocupar: solo rol VETERINARIO (403 al resto, incluido el Dueño). La estadía que la ocupa es de un día, se marca `urgenciaClinica=true` y no cuenta en `cuposOcupados`. Deshabilitar exige que esté libre.
- **L5** (implementación): el pago de una comanda sin titular responde 409 "Completá los datos del cliente y la mascota de la urgencia antes de cobrar" (no 400 como decía L4). La estadía en la plaza de urgencia no emite comanda de guardería (se cobra por la atención clínica). Corte de commits: T2 (backend S1-S2), T3 (frontend S1-S2), T4 (backend S3, incluye `SecurityConfig`: riesgo de permisos, commit propio), T5 (frontend S3).
- **L6** (e2e parcial en `veterinaria_e2e`): como Dueño, el interruptor registró "Urgencia sin datos · 16:53 · Sin profesional asignado" en Mostrador y Agenda (rojo, con Atender y Completar datos); Atender pide "Profesional que atiende" y avisa que se puede atender sin datos.
- **L7** (e2e completo, verify): S1, S2 y S3 pasan. Atender eligió profesional; Caja mostró "Urgencia sin datos" sin botón Cobrar y el pago por API dio 409 con el mensaje de L5; Completar datos (Rita Gómez + Rayo) habilitó el cobro. Como Dueño la plaza no se ve y `PUT /guarderia/urgencia/{fecha}` da 403; como Veterinario se habilitó, se internó a Toby ("Ocupada por Toby", aforo 0 de 10) y una reserva normal posterior quedó 1 de 10. A 390 px no hay scroll horizontal y los controles miden 44 px (el switch, 24 px, queda dentro de una fila clickeable de 44 px). La base `veterinaria_e2e` se borró al terminar.
