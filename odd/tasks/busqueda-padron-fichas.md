# Feature: buscadores paginados en el Padrón + botón "Fichas"

Pedido del usuario (2026-11-20):

> "quiero que cada parte dentro del padron tenga su buscador, en tutores quiero
> que haya un buscador para los tutores y dentro de mascotas me gustaria poder
> buscar el nombre de la mascota, y que abajo me aparezca su duenio/cotutor esto
> aliviana la lista para no tener que scrollear cuando haya mucha cantidad de
> perros/tutores".

## Specs

- **S1** — El panel Tutores tiene su propio buscador por **nombre, apellido, DNI
  y teléfono** (DNI con puntos o guiones se normaliza a dígitos). El panel
  Mascotas busca por **nombre** y muestra debajo el tutor/cotutor de cada fila.
- **S2** — Paridad con Medicamentos: `debounce` de 350 ms, `AbortController`
  para descartar respuestas viejas y paginación en servidor con
  `PaginadoServidor`. Cambiar la página o el tamaño no recarga todo el padrón.
- **S3** — Se conservan edición, desactivación y reactivación. Las filas
  inactivas siguen gobernadas por el toggle "Mostrar desactivados" existente:
  el flag viaja al backend como `incluirInactivos`.
- **S4** — En el Padrón, cada mascota tiene un botón **"Fichas"** con texto
  visible, `min-h-[44px]` y `aria-label`, que abre el historial de atenciones
  de esa mascota reutilizando `verFichas` del Shell. La barra superior queda
  intacta (fila completa clickeable, sin botón), según la tarea anterior.
- **S5** — Los endpoints nuevos no rompen los actuales: `GET /tutores` y
  `GET /mascotas` siguen alimentando selects, contadores y la ficha del tutor
  abierta.
- **S6** — Sin cambios a `AGENTS.md`. Sin commit: queda a la espera de revisión.

## Resultado de la implementación

### Backend

- `GET /api/v1/tutores/buscar?q=&page=&size=&incluirInactivos=` y
  `GET /api/v1/mascotas/buscar?...`, ambos con `PaginaResponseDTO`. Parámetros
  acotados: `page >= 0`, `1 <= size <= 200`, filtro hasta 120 caracteres.
- Repositorios: `buscarIds` pagina sobre la tabla (JPQL + `countQuery`) y
  `findCon...PorIds` carga la página con `@EntityGraph` de la relación N:M;
  así se evita paginar en memoria un `join` de colección y el problema N+1.
- Servicios: orden estable por apellido/nombre/id (tutores) y nombre/id
  (mascotas), reconstrucción del orden de la página y mapeo a DTO dentro de la
  transacción de solo lectura de la clase.
- `PadronBusquedaTest.java`: 5 tests de integración (filtro y paginación de
  mascotas, toggle de inactivos, búsqueda de tutores por cuatro campos, orden +
  paginación + acote, y no colisión de `/buscar` con `/{id}`).

### Frontend

- `lib/api.ts`: `BuscarPadron` y `tutores.buscar` / `mascotas.buscar`.
- `lib/use-busqueda-paginada.ts` (nuevo): estado de consulta con debounce,
  abort, reintento y paginación; referencias de búsqueda estables a nivel de
  módulo para no disparar recargas en cada render.
- `components/modules/padron.tsx`: dos `TextInput` con ícono `Search`,
  `Recurso` con estado de carga/error/vacío, `PaginadoServidor` por panel,
  botón "Fichas" en la lista de mascotas y prop `onVerFichas`.
- `app/app/page.tsx`: pasa `verFichas` al Padrón.
- Se mantienen `useApi` de las listas completas solo para los contadores de
  inactivos, los `<Select>` de cotutores y la ficha del tutor abierto.

## Limitación conocida

El modelo no distingue "dueño" de "cotutor": solo existe el flag
`autorizado_retiro` en cada tupla `TutorMascota`. La UI muestra a todas las
personas vinculadas con el ícono de escudo según ese flag.

## Hallazgo de infraestructura

El primer `test-harness.ps1` falló con 364 errores de `ApplicationContext`. El
primer `Caused by` en `backend/target/surefire-reports` era
`Unable to determine Dialect without JDBC metadata`: no había MySQL escuchando
en 3306 (la variante habitual es `Communications link failure`, ver riesgo 7 de
`PROGRESS.md`). Se levantó `C:\xampp\mysql\bin\mysqld.exe` con
`--defaults-file=C:\xampp\mysql\bin\my.ini --console` y el harness pasó sin
cambios de código.

## Verificación

- `npx tsc --noEmit` — limpio.
- `.\mvnw.cmd -q compile` — limpio.
- `.\mvnw.cmd -q -Dtest=PadronBusquedaTest test` — 5/5 tests.
- `.\test-harness.ps1` — **PASÓ**: 414 tests, 0 fallos, 0 errores, 0 omitidos;
  `BUILD SUCCESS` y "Todos los controles pasaron exitosamente" (incluye chequeo
  de tipos, suite backend y contrato de la base).

## Archivos tocados

- `backend/src/main/java/com/veterinaria/veterinaria_backend/controller/TutorController.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/controller/MascotaController.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/TutorService.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/impl/TutorServiceImpl.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/MascotaService.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/service/impl/MascotaServiceImpl.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/repository/TutorRepository.java`
- `backend/src/main/java/com/veterinaria/veterinaria_backend/repository/MascotaRepository.java`
- `backend/src/test/java/com/veterinaria/veterinaria_backend/PadronBusquedaTest.java`
- `lib/api.ts`
- `lib/use-busqueda-paginada.ts`
- `components/modules/padron.tsx`
- `app/app/page.tsx`
- `PROGRESS.md`
