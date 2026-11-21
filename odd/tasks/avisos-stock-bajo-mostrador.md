# Feature: aviso de stock bajo en "Avisos importantes" del Mostrador

Pedido del usuario (2026-11-20):

> "esta bien, solo que dentro de avisos sigue sin aparecer si tengo poco stock
> del medicamento, osea si baja del minimo stock que tiene que emitir un aviso,
> quiero que aparezca en el mostrador en avisos importantes"

## Specs

- **S1** — El panel "Avisos importantes" muestra un aviso por medicamento
  **activo** con `stockMinimo > 0` y `stockActual < stockMinimo`. El mínimo 0
  significa "sin control de stock", por eso no avisa.
- **S2** — "Sin stock" (`stockActual === 0`) se muestra en rojo y "bajo mínimo"
  en ámbar; ambos llevan `role="alert"` para lectores de pantalla. El texto
  incluye el nombre, la presentación, las unidades restantes y el mínimo.
- **S3** — La condición se calcula en el backend sobre datos frescos. No se
  filtra en el front un `GET /medicamentos?size=...` arbitrario: si el catálogo
  crece, un aviso podría quedar fuera de la página consultada.
- **S4** — Cada fuente (jornada, deudas, stock) informa su propio error y
  reintento. El mensaje "sin avisos" solo aparece si ninguna consulta sigue en
  vuelo ni falló, para no afirmar que todo está bien mientras el stock aún se
  está leyendo.
- **S5** — Sin cambios a `AGENTS.md`. Sin commit: queda a la espera de revisión.

## Resultado de la implementación

### Backend

- `GET /api/v1/medicamentos/bajo-stock` devuelve `List<MedicamentoResponseDTO>`.
  Se declaró antes de `/{id}` para que Spring no intente interpretar
  `bajo-stock` como un `Long`.
- `MedicamentoRepository.bajoStock()`: JPQL con
  `activo = true AND stockMinimo > 0 AND stockActual < stockMinimo`, ordenado
  por `stockActual`, `nombre` e `id` para que primero aparezcan los más
  urgentes y el orden sea estable.
- `MedicamentoServiceImpl.bajoStock()` mapea con el `toResponse` existente,
  que ya calcula `bajoStock`. Capas Controller → Service → Repository, sin DTO
  de entidad expuesta.
- `MedicamentoControllerTest.bajoStockParaAvisos`: cubre sin stock, bajo
  mínimo, stock normal, mínimo 0 e inactivo; verifica el orden y el flag
  `bajoStock`.

### Frontend

- `lib/api.ts`: `medicamentos.bajoStock()`.
- `components/modules/mostrador.tsx`: hook `useApi` de stock bajo (carga al
  montar, porque el aviso no depende de la fecha seleccionada), inclusión en
  `refrescar()` y prop `stockBajo` al panel.
- `components/modules/mostrador/avisos-importantes.tsx`: fuente de stock
  agregada; estados de carga y error propios; avisos de conducta y morosidad
  conservados con sus claves estables (`s-`, `c-`, `d-`).

## Decisión: endpoint propio en lugar de filtrar el catálogo

`MedicamentoResponseDTO` ya traía `bajoStock`, pero el catálogo se consume
paginado. Filtrar en el front habría obligado a pedir un `size` grande y a
confiar en que ningún medicamento quede fuera de esa página. Un endpoint
dedicado mantiene la regla en un solo lugar (el repositorio), devuelve solo lo
que el panel necesita y no acopla el aviso al tamaño de página del catálogo.

## Verificación

- `npx tsc --noEmit` — limpio.
- `.\mvnw.cmd -q compile` — limpio.
- `.\mvnw.cmd -q -Dtest=MedicamentoControllerTest test` — 24/24 tests.
- `.\test-harness.ps1` — **PASÓ**: 415 tests, 0 fallos, 0 errores, 0 omitidos;
  `BUILD SUCCESS` y "Todos los controles pasaron exitosamente" (incluye
  chequeo de tipos, suite backend y contrato de la base).
- `next-env.d.ts` fue regenerado por el build del harness con rutas
  `.next/types/...`; se restauró a `.next/dev/types/...` porque es un artefacto
  generado, no un cambio de esta tarea.

## Archivos tocados

- `backend/src/main/java/com/veterinaria/veterinaria_backend/controller/MedicamentoController.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/repository/MedicamentoRepository.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/MedicamentoService.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/impl/MedicamentoServiceImpl.java`
- `backend/src/test/java/com/veterinaria/veterinaria_backend/MedicamentoControllerTest.java`
- `lib/api.ts`
- `components/modules/mostrador.tsx`
- `components/modules/mostrador/avisos-importantes.tsx`
