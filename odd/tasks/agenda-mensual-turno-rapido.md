# Feature: agenda-mensual-turno-rapido

Vista mensual de la Agenda con los turnos de cada día y alta de turno con cliente y mascota nuevos en un solo paso.
Rama: `feat/agenda-mensual-turno-rapido` · Tests: `./mvnw test` contra el contenedor del 3307 (ver README), `npx tsc --noEmit` + `next build --webpack`.

## Specs

- **S1** — "Quiero que sea un Calendario con los días del mes. El cual tenga cada día de forma reducidos los turnos del día. Y al seleccionar el día en concreto a la derecha muestre Los turnos del día."
- **S2** — "reducir la fricción al momento de agendar un turno clinico. En situaciones donde venga un cliente nuevo y quiera agendar un turno, en vez de tener que primero agendar el cliente, después agendar el perro y luego agendar el turno. Quiero que dentro del modal A agendar turno haya un botón que despliegue: - Nuevo cliente o cliente existente - Mascota existente o mascota nueva"

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S1 | inline | hecho: `GET /api/v1/turnos?desde=&hasta=` (RED 4/5 observado; el 5.º fija que `?fecha=` no cambia) | 8d04657 |
| T2 | S1 | inline | hecho: Agenda mensual (tsc, build, e2e escritorio y 375 px) | 582f195 |
| T3 | S2 | inline | hecho: `POST /api/v1/turnos/con-alta` (RED 7/7 observado, 256 tests en verde) | e3eb5bc |
| T4 | S2 | inline | hecho: modal único con el desplegable (e2e: alta con cliente nuevo y 409 sin huérfanos) | 582f195 |
| T5 | S1, S2 | inline | hecho: README y PROGRESS.md; el Kaban no tenía ticket para esto | este commit |

## Log

- **L1** (2026-10-29, pedido original): "Ahora quiero que hagas dos cosas. Lo primero es cambiar la forma en que vemos los calendarios. Quiero que sea un Calendario con los días del mes. El cual tenga cada día de forma reducidos los turnos del día. Y al seleccionar el día en concreto a la derecha muestre Los turnos del día. Lo segundo que quiero es que hagas es reducir la fricción al momento de agendar un turno clinico. En situaciones donde venga un cliente nuevo y quiera agendar un turno, en vez de tener que primero agendar el cliente, después agendar el perro y luego agendar el turno. Quiero que dentro del modal A agendar turno haya un botón que despliegue: - Nuevo cliente o cliente existente - Mascota existente o mascota nueva"
- **L2** (supuestos):
  - S1 aplica a la pestaña Agenda. El calendario muestra los turnos clínicos; el aforo y las reservas de guardería del día elegido siguen debajo, como hoy. En celular el panel del día va debajo del calendario (mobile-first); en escritorio va a la derecha.
  - En cada celda se ven hasta 3 turnos ("09:00 Firulais") y un "+N" con el resto; en celular, solo la cantidad.
  - S2: los dos modales de turno (`NuevoTurnoModal` de Agenda y `AgregarTurnoModal` de Mostrador) se unifican en uno.
  - El alta combinada es un solo endpoint transaccional: si el turno choca (409) o algún dato es inválido (400), no queda ni el tutor ni la mascota creados.
  - Cliente nuevo implica mascota nueva (una mascota existente ya tiene tutor; sumarle un cotutor se hace desde el Padrón). La mascota nueva queda vinculada con `autorizadoRetiro=true`.
  - Con el desplegable cerrado, el modal funciona como hoy (mascota existente de toda la base).
- **L3** (verificación e2e con ego-browser, base descartable `veterinaria_e2e` en el contenedor del 3307, borrada al terminar; la base de desarrollo no se tocó porque sus cuentas tienen claves del usuario):
  - Escritorio 1440 px: el 29/10 muestra 3 turnos y "+2 más"; el panel derecho lista los 5. Elegir el 15 cambia el panel.
  - Modal: con cliente nuevo se escribió "30.4abc56.789" en el DNI y quedó "30456789". El turno se agendó y apareció en la celda y en el panel. En la base quedó "Juan Carlos De la Fuente", teléfono 1155554444, con Pupi vinculada.
  - 409: un cliente nuevo a las 13:30 sobre el turno de las 13:00 muestra el mensaje del backend y el DNI 29111222 no queda creado.
  - Celular 375 px: celdas de 43×48 con la cantidad de turnos, lista del día debajo, sin scroll horizontal. El ancho de la celda queda 1 px debajo de los 44 de AGENTS.md (7 columnas en 343 px).
  - Bugs encontrados y corregidos en la prueba: el título salía "Octubre De 2026" (`capitalize`) y los días de meses vecinos tenían el mes equivocado en su `aria-label`.
- **L4** (corte de entrega): la rama suma ~2.000 líneas en 4 commits, por encima del corte de ~400. Cada commit es una unidad revisable: 8d04657 (rango, backend), e3eb5bc (alta combinada, backend), 582f195 (frontend: calendario y modal único; incluye borrar los dos modales viejos, unas 580 líneas eliminadas) y este commit de documentación.
- **L5** (aviso de la revision `review-b805858b7d955dcc`, aprobada): con mascota existente, el `tutorId` se ignoraba. Ahora se valida el vinculo y responde 400 "La mascota elegida no pertenece al cliente indicado" (RED observado; 258 tests en verde).
