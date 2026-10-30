# Feature: atencion-extras-menu

Finalizar la atención de un turno desde la Agenda, extras con precio del catálogo del Dueño en la comanda, y barra lateral agrupada en secciones.
Rama: `feat/atencion-extras-menu` (apilada sobre `feat/agenda-mensual-turno-rapido`, PR #3) · Tests: `./mvnw test` contra el contenedor del 3307, `npx tsc --noEmit` + `next build --webpack`.

## Specs

- **S1** — "Todos los turnos quedaron registrados en el calendario Pero no bueno, en ningún lado. En ningún momento el profesional puede dar como finalizada la consulta."
- **S2** — "Tampoco en ningún lado, el profesional puede agregar extras en caso de que haya tenido que usar una vacuna o algo de urgencia" + "El dueño arma el catálogo de extras en la pestaña de tarifas."
- **S3** — "ordenar un poco la barra lateral izquierda de menú de acciones" — orden aprobado ("perfecto"): OPERACIÓN (Mostrador, Agenda, Ficha médica, Egreso); CLIENTES Y CAJA (Padrón, Historial, Caja); ADMINISTRACIÓN, solo el Dueño (Balance, Tarifas, Usuarios).

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S2 | inline | hecho: `/api/v1/extras` (RED 5/6 observado; 403 para Veterinario y Recepción) | 577af5f |
| T2 | S1, S2 | inline | hecho (RED 7/7 observado, 271 tests en verde) | 34b2a25, 0ef2480 |
| T3 | S2 | inline | hecho (e2e: alta con normalización y filtro de letras en el precio) | 5c287cd |
| T4 | S1, S2 | inline | hecho (e2e: Atender → extras sin precios → turno Atendido → comanda $21.100 con detalle en Caja) | 0ef2480 |
| T5 | S3 | inline | hecho (e2e: Dueño ve 3 secciones; Veterinario 2, en escritorio y en el cajón móvil a 375 px) | 745a644 |
| T6 | — | inline | hecho | este commit |

## Log

- **L1** (2026-10-29, pedido original): "Tenemos un problema en el flujo del sistema. Todos los turnos quedaron registrados en el calendario Pero no bueno, en ningún lado. En ningún momento el profesional puede dar como finalizada la consulta. Tampoco en ningún lado, el profesional puede agregar extras en caso de que haya tenido que usar una vacuna o algo de urgencia"
- **L2**: decisión del usuario: "El dueño arma el catálogo de extras en la pestaña de tarifas." Pedido adicional: "Otra cosa que me gustaría que hagas: Es ordenar un poco la barra lateral izquierda de menú de acciones". Orden propuesto y aprobado: "perfecto".
- **L3** (supuestos):
  - Una atención = una comanda (concepto CONSULTA_CLINICA) cuyo total es la consulta más los extras. El detalle queda en líneas de comanda (descripción, cantidad, precio unitario congelado al emitir). Así el cobro, la deuda por mascota y el cierre de caja no cambian de forma.
  - El profesional elige extras y cantidades sin ver precios (regla 3 de AGENTS.md). Leer el catálogo lo puede cualquier empleado; crear, editar o desactivar, solo el Dueño (403 al resto).
  - Un extra no se borra: se desactiva, para no romper el detalle de comandas viejas. Los desactivados no se ofrecen en la ficha.
  - Guardar una ficha asociada a un turno pasa ese turno a ATENDIDO en la misma transacción. Un turno CANCELADO no se puede atender (400). Si el turno es de otra mascota, 400.
  - "Atender" abre la ficha en un modal desde la Agenda, ya vinculada al turno y a su mascota, para no cambiar de pestaña.
- **L4** (e2e con ego-browser sobre la base descartable `veterinaria_e2e`, borrada al terminar): Dueño carga "vacuna antirrábica" (queda "Vacuna antirrábica") y "Jeringa"; Atender el turno de las 09:00 con 1 vacuna y 2 jeringas, el modal no muestra ningún "$"; el turno pasa a Atendido y el de las 11:00 sigue Pendiente con su botón; Caja muestra la comanda #1 de $21.100 con Consulta $8.000, Vacuna $12.500 y 2 × Jeringa $600. Veterinario: GET /extras 200, POST 403; menú sin Administración; 375 px sin scroll horizontal. Corregido en la prueba: "Consulta clinica" sin tilde.
- **L5** (riesgo): ítem 2 (permisos: reglas nuevas en `SecurityConfig` para `/api/v1/extras`, cubiertas por tests de 403) e ítem 1 (tablas nuevas `extra` y `comanda_item`, aditivas por `ddl-auto=update`; no se reescriben datos). `ComandaCobroResponseDTO` suma el campo `items` (opcional, contrato aditivo).
- **L6** (corte de entrega): ~1.000 líneas propias sobre PR #3, en commits revisables: 577af5f (catálogo, con permisos), 34b2a25 (ficha + comanda), 5c287cd (Tarifas), 745a644 (menú), 0ef2480 (Atender + Caja) y docs.
