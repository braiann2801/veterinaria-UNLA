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
| T1 | S1, S3 | inline | pendiente: excepciones en AGENTS.md (confirmadas) | |
| T2 | S1, S2 | inline | pendiente: backend turno de urgencia, atención sin datos, comanda sin titular que no se cobra, completar datos + tests | |
| T3 | S1, S2 | inline | pendiente: frontend toggle de urgencia, Atender con profesional, Completar datos | |
| T4 | S3 | inline | pendiente: backend plaza de urgencia por día (solo Veterinario) + tests | |
| T5 | S3 | inline | pendiente: frontend plaza de urgencia en Agenda guardería | |
| T6 | — | inline | pendiente: e2e, README, PROGRESS.md | |

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
