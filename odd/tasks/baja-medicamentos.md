# Feature: baja de medicamentos sin perder la trazabilidad clínica

Pedido del usuario (2026-11-20):

> "seria si nunca se usaron en ninguna ficha, osea si se usaron, cuando se
> intente borrarlo, deberia saltar una excepcion que diga que estan usados por
> lo que debe desactivarse, no se puede borrarlo".

## Specs

- **S1** — Un medicamento **nunca usado en una ficha** se puede borrar
  físicamente. Se van con él sus movimientos de stock (que solo documentan la
  carga que se quiere corregir) en la misma transacción.
- **S2** — Un medicamento **usado en una ficha** no se puede borrar:
  `DELETE /{id}` responde **409** con el mensaje
  *"El medicamento '<nombre>' fue usado en fichas clínicas: no se puede borrar.
  Desactivalo con el toggle para que deje de estar disponible sin perder la
  trazabilidad."*
- **S3** — "Usado" significa **consumo en ficha** (`EGRESO_FICHA` o snapshot
  clínico), no el `INGRESO` de carga. Un medicamento cargado por error se
  puede borrar aunque tenga movimientos de alta.
- **S4** — La baja lógica sigue el patrón de tutores/mascotas: toggle de
  desactivados, reactivación, desaparición del catálogo activo y del selector
  de atención (`SelectorMedicamentos` ya filtraba `activo && stockActual > 0`).
  Un inactivo no admite nuevas cargas de stock ni consumos (400).
- **S5** — Cada ficha muestra **qué medicamento se aplicó**, aunque el ítem se
  desactive. Snapshot con nombre, presentación y cantidad; **sin precio** en la
  ficha ni en su payload (regla 2.3 de `AGENTS.md`). El monto sigue congelado
  en la comanda.
- **S6** — Fichas históricas sin snapshot se reconstruyen en lectura desde los
  movimientos `EGRESO_FICHA` con `referenciaId` del id de la ficha.
- **S7** — Confirmación visual para borrar/desactivar, feedback inline tras la
  mutación y recarga del listado. Sin commit: a la espera de revisión.

## Resultado de la implementación

### Backend

- Nueva entidad `MedicamentoUsadoFicha` y repositorio
  `MedicamentoUsadoFichaRepository`. `medicamentoId` es un `Long` **sin FK**;
  guarda `nombre`, `presentacion` y `cantidad`. Sin precio.
- `ConsultaMedicaServiceImpl.registrar` llama a `guardarSnapshot(...)` en la
  misma transacción que ficha, descuento de stock y comanda. `buscarPorId` y
  `listarPorMascota` leen el snapshot con fallback a movimientos para fichas
  anteriores a esta tabla.
- `MedicamentoServiceImpl` incorpora `listar(..., incluirInactivos, ...)`,
  `contarInactivos`, `estaUsado` / `idsEnUso`, `eliminar`, `desactivar`,
  `reactivar` y `verificarActivo` en cargas y consumos.
- `MedicamentoController`: `DELETE /{id}`, `PUT /{id}/desactivar`,
  `PUT /{id}/reactivar`, `GET /inactivos/cantidad` y parámetro
  `incluirInactivos` en `GET`. `SecurityConfig` ya cubría los cuatro roles; no
  se tocó.
- DTOs: `MedicamentoResponseDTO.enUso` y
  `ConsultaMedicaResponseDTO.MedicamentoAplicadoDTO`.

### Frontend

- `lib/api.ts`: tipos `MedicamentoAplicado` y `enUso`; métodos `eliminar`,
  `desactivar`, `reactivar`, `contarInactivos` y `incluirInactivos` en `listar`.
- `lib/use-medicamentos.ts`: `verInactivos` / `cantidadInactivos`, con recarga
  del conteo junto al `version` de la lista.
- `components/modules/medicamentos.tsx`: `ToggleInactivos`, badge
  "Usado en fichas", acciones Desactivar / Borrar / Reactivar,
  `ConfirmarModal` y avisos inline. La elección entre borrar y desactivar se
  refleja ya en el mensaje de confirmación; el `409` del DELETE cubre la
  carrera en que otro operador usó el medicamento entre el listado y el clic.
- `components/modules/fichas.tsx`: bloque "Medicamentos aplicados" con
  nombre, presentación y cantidad.

## Decisión: borrado entidad por entidad, no bulk JPQL

El primer intento usó una bulk delete (`DELETE FROM MovimientoStockMedicamento
WHERE medicamento.id = ...`). Falló con
`org.hibernate.TransientPropertyValueException`: la *persistence context*
conservaba movimientos que apuntaban a un medicamento ya borrado. Se reemplazó
por `movimientoRepository.deleteByMedicamentoId(id)` (derivado de Spring Data),
que gestiona las entidades una por una. No revertir.

## Verificación

- `.\mvnw.cmd -q -DskipTests compile` y `-DskipTests test-compile` — limpios.
- `npx tsc --noEmit` — limpio.
- Suites focales: `MedicamentoBajaTest`, `AtencionConMedicamentosTest` y
  `MedicamentoControllerTest` — 39 pruebas, 0 fallos.
- `.\test-harness.ps1` — **PASÓ**: `tsc --noEmit` limpio, `next build --webpack`
  OK, **421 tests, 0 fallos, 0 errores, 0 omitidos**, `BUILD SUCCESS` y
  "Todos los controles pasaron exitosamente" (incluye el contrato de la base).

## Archivos tocados

- `backend/src/main/java/com/veterinaria/veterinaria_backend/entity/MedicamentoUsadoFicha.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/repository/MedicamentoUsadoFichaRepository.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/repository/MovimientoStockMedicamentoRepository.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/repository/MedicamentoRepository.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/MedicamentoService.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/impl/MedicamentoServiceImpl.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/impl/ConsultaMedicaServiceImpl.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/controller/MedicamentoController.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/dto/MedicamentoResponseDTO.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/dto/ConsultaMedicaResponseDTO.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/mapper/ConsultaMapper.java`
- `backend/src/test/java/com/veterinaria/veterinaria_backend/MedicamentoBajaTest.java`
- `backend/src/test/java/com/veterinaria/veterinaria_backend/AtencionConMedicamentosTest.java`
- `lib/api.ts`
- `lib/use-medicamentos.ts`
- `components/modules/medicamentos.tsx`
- `components/modules/fichas.tsx`
