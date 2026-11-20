# Feature: medicamentos-precio-atencion

Los medicamentos del consultorio tienen precio propio, se buscan desde la ficha
con la misma barra del módulo Medicamentos y se descuentan del stock al guardar
la atención. Reemplaza al catálogo de extras, que se elimina por completo.

## Specs

- **S1** — "Quitar la parte de extras de atención: en Medicamentos se le puede poner el precio que le va a salir al cliente pagarlo. Si agrego amoxicilina a 5000, es lo que luego se cobra al cliente con la consulta."
- **S2** — "Dentro de la agenda clínica, al tocar Atender, quiero la misma barra de búsqueda que para medicamentos, para clickear el que yo quiera, siempre que esté en la lista de medicamentos."
- **S3** — Regla 3 de `AGENTS.md` (diff aprobado por `braiann2801`): anamnesis, diagnóstico, tratamiento y conducta; ni montos ni medios de pago en la ficha. El precio de catálogo es informativo y se congela en la comanda que gestiona Caja.
- **S4** — Implícito en S1/S2: al guardar la ficha se descuenta el stock de cada medicamento en la misma transacción; sin stock suficiente, 409 y rollback de ficha, comanda y movimientos.
- **S5** — Alta con precio y edición de precio. Cualquier empleado puede fijarlo (igual que el stock). Cambiar el precio no altera comandas emitidas.

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S1, S3, S4, S5 | inline | hecho (suite backend 402/402) | `b84892c` |
| T2 | S2, S3 | inline | hecho (`tsc --noEmit` limpio) | `b84892c` |
| T3 | S5 | inline | hecho (alta, edición y columna Precio) | `b84892c` |
| T4 | S1 | inline | hecho (extras fuera de código y UI; comandas históricas intactas) | `b84892c` |
| T5 | — | inline | hecho (README, docs de tareas, PROGRESS.md) | `b84892c` |

## Log

- **L1** (2026-11-20, pedido original): "quiero quitar la parte de extras de atencion, quiero que en medicamentos el medicamento se le pueda poner el precio que le va a salir al cliente pagarlo, es decir si quiero agregar amoxicilina a 5000 es lo que luego se le va a cobrar al cliente con la consulta tambien, luego dentro de la agenda clinica cuando toco atender, me gustaria tener la misma barra de busquedas que para medicamentos, para clikear el que yo quiera agregar, siempre que este en la lista de medicamentos."
- **L2** (decisiones del usuario): "Borrar código, conservar historial (Recomendado)"; "Sí, mostrar precio"; "Alta + edición (Recomendado)"; "Cualquier empleado"; "Sí, automático (Recomendado)".
- **L3** (regla 3): se presentó el diff que reemplaza extras por medicamentos con precio de catálogo y descuento de stock; `braiann2801` lo aprobó. `AGENTS.md` quedó actualizado.
- **L4** (esquema): no hay Flyway; `ddl-auto=update` agrega `medicamento.precio` (`DECIMAL(12,2)`, default 0, anulable) sin tocar tablas existentes. La tabla `extra` y la FK `comanda_item.extra_id` permanecen en la BD por compatibilidad con comandas antiguas; solo se quitó el mapeo JPA y el código.
- **L5** (contrato): `ConsultaMedicaRequestDTO.medicamentos` con `medicamentoId` + `cantidad` (1..99), sin montos. Jackson ignora la clave `extras` de clientes viejos: la acepta, pero ya no suma nada. `PUT /api/v1/medicamentos/{id}/precio` con `precio >= 0`.
- **L6** (dominio): `ConsultaMedicaServiceImpl.registrar` resuelve medicamentos activos, descuenta stock con `EGRESO_FICHA` y `referenciaId = ficha.id`, y copia nombre y `precio` vigente a cada `ComandaItem`. `ComandaItem` no tiene FK a medicamento ni a extra: guarda descripción, cantidad, precio unitario y subtotal como escalares.
- **L7** (tests nuevos): `AtencionConMedicamentosTest` (9) cubre precio congelado, sin medicamentos, turno atendido/cancelado/ajeno, ids inválidos, inactivo, cantidades fuera de rango, stock insuficiente con rollback y movimientos. `MedicamentoControllerTest` suma alta con precio, default 0, precio negativo y cambio de precio.
- **L8** (infra de tests, actualizado 2026-11-20): el datadir original de XAMPP tenía una página InnoDB corrupta (`space=118, page=1`, tabla `veterinaria_test.comanda_cobro`). Los primeros tests corrieron contra un datadir temporal limpio en `C:\Users\Administrator\AppData\Local\Temp\opencode\mysql-test-data`. Después se recuperó XAMPP: copia en frío → `innodb_force_recovery=1` → dump de `veterinaria_db` → import en datadir limpio → swap. Original en cuarentena (`C:\xampp\mysql\data-corrupt-20261120`), activo el recuperado en 3306 sin force recovery. No se usó Docker: `docker ps` falla por el pipe del motor.
- **L9** (verificación): `.\mvnw.cmd test` → 402 tests, 0 fallos; `npx tsc --noEmit` limpio. El test de stock insuficiente cuenta las fichas desde una transacción `REQUIRES_NEW`: con `@Transactional` en el test, la fila del rollback sigue visible en la transacción compartida.
- **L10** (frontend): `SelectorMedicamentos` reutiliza `useMedicamentos` (debounce de 350 ms + `AbortController`), muestra nombre, presentación, laboratorio, stock y precio, deshabilita los que tienen stock 0 y conserva un snapshot de los elegidos para no perder nombre/precio al cambiar de página de búsqueda. El precio es informativo; el payload de la ficha no lo incluye.
- **L11** (verificación final post-recuperación): `mysqlcheck --check --all-databases` sin errores; 19 tablas con los mismos conteos que el origen (22 comandas, 12 consultas, 13 mascotas, 12 tutores, 13 vínculos, 11 reservas, 3 tarifas…); harness `.\test-harness.ps1` completo en verde (402/402) contra el datadir recuperado. `datos/backup-recuperado-20261120.sql` queda como constancia (gitignored). `next-env.d.ts` regenerado por el build fue revertido.
