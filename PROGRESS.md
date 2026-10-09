# PROGRESS.md

## Estado Actual
- **Fase en curso:** Ninguna — **REFACTOR DE `mostrador.tsx` COMPLETADO** (monolito de 1105 líneas → orquestador de 150 + 9 componentes atómicos, sin cambios de UI ni funcionalidad)
- **Base Git:** `c264145` (FASE 1 + FASE 2 + FASE 3 commiteadas)[cite: 2]
- **Harness:** `.\test-harness.ps1` en verde (TSC limpio, Next.js build OK, **157 tests de Spring Boot pasando**, 0 fallos)[cite: 2]
- **Últimos logros (TANDA 2):**
  - Tablero de movimiento del Mostrador conectado a MySQL vía endpoints reales (`/turnos`, `/guarderia`, `/guarderia/aforo`, `/egreso/pacientes-hoy`)[cite: 2].
  - Bug `DateTimeParseException` resuelto con `FlexibleLocalDateTimeDeserializer` en backend y armado ISO en `components/modules/ficha.tsx`[cite: 2].
  - Fechas dinámicas locales estandarizadas con `hoyLocal()` (evitando desfasaje UTC)[cite: 2].
  - Turnos retroactivos verificados y soportados[cite: 2].

---

## Historial de Tareas y Fases Completadas
- [x] **TASK-001 a TASK-003:** Setup base Next.js/Spring Boot, persistencia MySQL, modelo Tutor-Mascota N:M[cite: 2].
- [x] **TASK-004 + HOTFIX:** Turnos (bloques 45+15 min), aforo de guardería (tope 10 con lock pesimista)[cite: 2].
- [x] **TASK-005:** Ficha Médica (conducta) y Comanda de Cobro desacoplada de precios (catálogo en `Tarifa`) con caja multipago[cite: 2].
- [x] **TASK-006 (MVP 1):** Circuito de egreso seguro consolidado (`GET /api/v1/egreso/validar`), semáforo y navegación mobile-first[cite: 2].
- [x] **TASK-007 (FASE 1):** ABM y persistencia de `Profesional` (DNI/matrícula normalizados), alta real de mascotas asociadas a tutores, Padrón visible para Veterinario[cite: 2].
- [x] **TASK-008 (FASE 2):** Guardería con horarios (`horaEntrada`/`horaSalida`), grilla de Agenda reactiva por fecha (`useApi` con `clave`), `TurnoRetroactivoTest` y modales operativos[cite: 2].
- [x] **HOTFIX-UI:** Navegación unificada idéntica para todos los roles (Egreso último, Balance exclusivo Dueño anteúltimo), alta unificada de Cliente en Mostrador contra la API (`/tutores` + `/mascotas` + vínculo)[cite: 2].
- [x] **TANDA 2:** Deserializador flexible para `LocalDateTime`, eliminación de estado mock en tablero del Mostrador con selector de fechas y refresco automático post-alta[cite: 2].

---

## Decisiones Críticas de Arquitectura y Convenciones
1. **Rutas del Repositorio:**
   - Frontend: `app/`, `components/`, `lib/` (no existe carpeta `frontend/src/`)[cite: 1, 2].
   - Backend: `backend/src/main/java/com/veterinaria/veterinaria_backend/`[cite: 2].
2. **Manejo de Fechas:** Prohibido usar `new Date().toISOString().split('T')[0]` en frontend (genera salto de día UTC en husos UTC-3). Usar siempre helper `hoyLocal()` de `lib/api.ts`[cite: 2].
3. **Desacople Clínico-Contable:** La consulta médica y la ficha jamás manejan dinero ni precios. La facturación se deriva transaccionalmente hacia `ComandaCobro` buscando el valor en `Tarifa`[cite: 2].
4. **Conducta del Paciente:**
   - Paciente base (`Mascota.conducta`): `normal`, `gruñe`, `muerde`, `miedoso`[cite: 2].
   - Consulta clínica puntual (`ConsultaMedica.conductaObservada`): `TRANQUILO`, `NERVIOSO`, `REACTIVO`, `AGRESIVO`[cite: 2].
5. **Egreso Seguro vs. Cobro:**
   - Retiro de paciente: Gobernado por `tutor_mascota.autorizado_retiro = true` y validación de deuda[cite: 2].
   - Cobro en Caja: Cualquier tutor vinculado en `tutor_mascota` puede pagar la comanda, sin requerir autorización de retiro[cite: 2].

---

## Riesgos y Pendientes Abiertos
1. **Módulo Balance:** Sigue operando con datos locales simulados y no consume `GET /api/v1/caja/cierre` (exclusivo para rol Dueño)[cite: 2].
2. **`guarderia.tsx` legado:** Componente desactualizado en memoria sin montar; las reservas operativas se gestionan desde los modales de la Agenda y Mostrador[cite: 2].
3. **Columna sin uso:** `vinculado_desde` en `TutorMascota` sigue sin ser consumida por el frontend[cite: 2].
4. **Falta de migraciones versionadas:** No se cuenta con Flyway o Liquibase; la base depende de `hibernate.ddl-auto=update`[cite: 2].
5. **Colisión de compilación VS Code / Maven:** El Language Server de Java (Eclipse ecj) genera clases con errores en `backend/target/classes`. Se recomienda compilar siempre con `mvnw clean` si surgen `BeanCreationException` anómalos[cite: 2].
6. **[RESUELTO] Tarifa vacía bloqueaba toda la facturación:** ver cierre al final del documento (`DataInitializer`).
7. **[RESUELTO] El harness mutaba la base de desarrollo compartida:** ver cierre al final del documento (`veterinaria_test`).

---

## FASE 3: Vista de Caja del Día (Turnos/Guardería de Hoy) y Checkout de Egreso Filtrado por Fecha

### Diagnóstico del Orquestador (leído, no supuesto)

El encargo tiene tres bloques. **Medio y medio ya estaba implementado y con tests**
antes de arrancar: fue `TASK-009`, commiteado en `c264145`. Reimplementarlo habría
sido ruido, así que el trabajo se concentró en lo que **no** estaba.

**Ya existía y quedó verificado, sin rehacerlo:**

- `GET /api/v1/comandas/dia?fecha=&estado=&tutorId=` con `fecha` opcional por
  defecto a hoy y filtro de estado. `GET /api/v1/comandas` sigue exigiendo
  `tutorId` a propósito: es el historial completo de una persona, y meter la fecha
  en esa ruta mezclaría dos alcances distintos en el mismo endpoint. La caja
  del día es el endpoint `/dia`.
- `GET /api/v1/caja/tutores-del-dia?fecha=`: consolidado sobre
  `JornadaDelDiaService`, agrupado en backend (un tutor con dos mascotas aparece
  una sola vez) y compartiendo la misma definición de "estuvo hoy" que el egreso.
- `POST /api/v1/caja/pagos/{id}` con `tutorId` opcional, validando **vínculo en
  `tutor_mascota`** y sin exigir `autorizado_retiro`. `CajaDelDiaTest` ya cubre
  `cotutorVinculadoPuedePagar`, `elDeudorNoCambia` y `terceroSinVinculoRechazado`.
- `GET /api/v1/egreso/pacientes-hoy?fecha=` con default hoy, y
  `GET /api/v1/egreso/validar` exigiendo estrictamente `autorizado_retiro = true`
  y sin deuda. `EgresoSeguroTest` cubre el semáforo completo (verde/rojo/ámbar),
  la precedencia identidad-antes-que-dinero y que la alerta de conducta **avisa
  pero no bloquea**.
- Frontend: `caja.tsx` con `hoyLocal()`, filtro de estado, alerta TUTOR MOROSO y
  modal multipago con selector de pagador entre tutores vinculados; `egreso.tsx`
  con `hoyLocal()`, listado de la jornada y botón "Validar egreso" por fila.

**Diagnóstico de los tres huecos que sí había:**

1. **`egreso.tsx` — callejón sin salida para un paciente sin tutores vinculados.**
   El `Select` "Quién se presenta" solo se renderiza si hay vínculos, y es ese
   `Select` el que contiene la opción "Otro (buscar por DNI)". Con cero vínculos
   el modal queda con una etiqueta vacía, `tutorId = ""`, el `useApi` deshabilitado
   y **ningún veredicto**: ni botón, ni semáforo, ni motivo. La fila en la lista
   dice "Sin tutores vinculados: no hay a quién validar", que contradice la
   documentación del propio componente, donde se explica que el camino por DNI
   queda "para el caso real de un tutor que no está vinculado en la base". El
   endpoint por DNI sí existe y sí responde.
2. **`caja.tsx` — el selector de tutores del día no tiene estado de carga ni de
   error.** Si `GET /caja/tutores-del-dia` falla o tarda, el `Select` muestra solo
   "Todos los tutores de la jornada" y nada más. El operador no puede distinguir
   "no hubo actividad hoy" de "no se pudo consultar", y la lista de comandas
   debajo sí informa su error: dos controles de la misma pantalla con criterios
   distintos.
3. **`caja.tsx` — el banner TUTOR MOROSO se apaga en la carga y en el error.**
   La condición es `deuda.datos && deuda.datos.tieneDeuda`: mientras la llamada
   está en vuelo o si falla, el banner **no se ve**, y la pantalla muestra un
   "nadie debe" silencioso. Es exactamente el defecto que ya se corrigió en el
   tablero del Mostrador en TANDA 2 ("durante la carga no dice nadie debe, dice
   que está calculando"), presente acá porque este módulo no se tocó en esa tanda.

### Cambios de FASE 3

- `components/modules/egreso.tsx`: el camino por DNI queda siempre a mano, con o
  sin vínculos, para que el semáforo se pinte y el operador vea el motivo del
  bloqueo en vez de una caja vacía.
- `components/modules/caja.tsx`: estado de carga y de error visibles en el
  selector de tutores del día, y aviso de morosidad que distingue "calculando"
  y "no se pudo consultar" de "está al día", con reintento.

Sin cambios en backend, sin endpoints nuevos, sin tocar `AGENTS.md`.

### Verificación E2E (contra la base real, sembrada y luego restaurada)

Backend levantado contra MySQL y datos sembrados **por la API** (no por SQL), para
ejercitar el negocio: un tutor con dos vínculos —uno `autorizado_retiro = true` y
otro `false`— y una segunda mascota **sin tutores**, cada una con un turno en la
jornada.

- `caja.tsx`: `/comandas/dia` con fecha por defecto y con filtro de estado (200), y
  estado inválido → 400; `/caja/tutores-del-dia` agrupa a la tutora en una sola
  fila con `motivos: ["TURNO_CLINICO"]`; `/mascotas/{id}` expone los dos vínculos
  con su `autorizadoRetiro`, que es lo que pinta el selector de pagador.
- `egreso.tsx`: `/egreso/pacientes-hoy` trae a Luna (2 tutores) y a Toby (**0
  tutores**). El punto que arregla la fase: `GET /egreso/validar?mascotaId=<Toby>&
  tutorDni=...` responde **200 con veredicto rojo `TUTOR_NO_AUTORIZADO`** en vez del
  modal en blanco de antes. El cotutor sin autorización da rojo y el tutor
  autorizado sin deuda da verde.
- El cobro por el cotutor **sin** `autorizado_retiro` no pudo cerrarse por el
  bloqueo del Riesgo 6 (no hay tarifa para emitir la comanda), no por la regla de
  negocio: `CajaDelDiaTest.cotutorVinculadoPuedePagar` la cubre con tarifa sembrada.

Base devuelta exactamente al estado inicial (todo en 0 salvo `control_aforo_diario`,
6 contadores por fecha) y archivos temporales eliminados.

### Resultado del Harness

- `.\test-harness.ps1` → **"Todos los controles pasaron"**: `tsc --noEmit` limpio,
  build de Next.js OK, **154 tests de Spring Boot, 0 fallos**.
- Incidente resuelto durante la fase: el primer intento dio 154 errores de contexto
  Spring (`Failed to load ApplicationContext`) y **no era el código**: MySQL no
  estaba levantado en la máquina (puerto 3306 cerrado, sin proceso `mysqld`). Se
  levantó y el harness pasó. Se anota porque el mensaje de error no lo sugiere.

### Archivos modificados

- `components/modules/caja.tsx` (2 bloqueantes de UI: estados de carga/error del
  selector de tutores y del aviso de morosidad).
- `components/modules/egreso.tsx` (1: camino por DNI siempre alcanzable).
- `PROGRESS.md` (registro y cierre de la fase).

Backend sin cambios, `AGENTS.md` sin tocar.

---

## CIERRE DE RIESGOS 6 y 7: Semilla de Tarifas y Aislamiento de la Base de Tests

### Hallazgo previo (verificado, no supuesto)

El encargo pedía sembrar **cinco** conceptos: `CONSULTA_CLINICA`,
`CONSULTA_URGENCIA`, `GUARDERIA_DIURNA`, `GUARDERIA_COMPLETA_24H` y
`CONTROL_POSTOPERATORIO`. **Cuatro de esos cinco no existen en el dominio**: el
único enum que usan `Tarifa.concepto` y `ComandaCobro` es
`ComandaCobro.Concepto` = `{CONSULTA_CLINICA, GUARDERIA, OTRO}`, y los otros
nombres no aparecen en ningún archivo del repo. Como `Tarifa.concepto` es
`@Enumerated(STRING)`, escribir esos strings reventaría en runtime. Se consultó
al Dueño: **decisión = sembrar los 3 conceptos reales** sin inventar conceptos
muertos que ningún flujo emite ni la UI puede seleccionar.

### Riesgo 6 — Semilla de tarifas (`config/DataInitializer.java`)

- `@Component implements CommandLineRunner`; en el arranque llama a
  `sembrar()`, que si `tarifaRepository.count() == 0` inserta las tarifas base
  de `CONSULTA_CLINICA`, `GUARDERIA` y `OTRO` y no hace nada si ya hay precios.
- Idempotente y **no pisa** precios cargados/editados: los montos vigentes siguen
  siendo el dato del negocio (`PUT /api/v1/comandas/tarifas/{concepto}`).
- Los montos salen de `veterinaria.tarifas.*` en `application.properties`
  (defaults 8000 / 10000 / 5000), no de constantes hardcodeadas, respetando la
  razón por la que existe `Tarifa` (AGENTS.md 2.3 / decálogo del propio javadoc).
  Ajustar el precio base no requiere recompilar.
- `run()` es `@Transactional`; `sembrar()` es público para poder testearlo directo.

### Riesgo 7 — Aislamiento de tests (`backend/src/test/resources/application.properties`)

- Nuevo `application.properties` de test apuntando a la base **`veterinaria_test`**
  (`createDatabaseIfNotExist=true`), separada de `veterinaria_db`. El classpath
  de test reemplaza al principal, por eso el archivo re-declara las claves
  (datasource, ddl-auto, pool de 20 conexiones).
- Se eligió **esquema MySQL aislado y no H2** (el encargo permitía ambos):
  `ControlAforoDiarioRepository` y `ComandaCobroRepository` usan `SELECT ... FOR
  UPDATE` nativo y el servicio maneja `innodb_lock_wait_timeout`; `AforoConcurrenteTest`
  mide el lock real. En H2 cambiaría la semántica y los tests dejarían de probar
  lo que dicen probar. Con el esquema aislado el comportamiento es idéntico.
- Verificado en vivo: los tests corren contra `veterinaria_test` (creada, 11
  tablas) y **`veterinaria_db.tarifa` permaneció en 0** tras correr la suite.

### Verificación

- `DataInitializerTest` (nuevo): siembra sobre tabla vacía, **no pisa** una tarifa
  existente con otro monto, e idempotencia (sembrar dos veces deja 3, no 6).
- `.\test-harness.ps1` → **"Todos los controles pasaron"**: `tsc --noEmit` limpio,
  build de Next.js OK, **157 tests de Spring Boot, 0 fallos** (154 previos + 3
  nuevos). El aislamiento no rompió ningún test de concurrencia.
- Sembrador verificado contra la base real: al reiniciar el backend con el código
  nuevo, `veterinaria_db.tarifa` pasa de 0 a las 3 tarifas base y
  `POST /api/v1/consultas` / `POST /api/v1/comandas` ya encuentran tarifa.

### Archivos modificados (esta fase)

- `backend/src/main/java/com/veterinaria/veterinaria_backend/config/DataInitializer.java` (nuevo).
- `backend/src/test/resources/application.properties` (nuevo — aislamiento de tests).
- `backend/src/test/java/com/veterinaria/veterinaria_backend/DataInitializerTest.java` (nuevo).
- `backend/src/main/resources/application.properties` (montos de la semilla `veterinaria.tarifas.*`).
- `PROGRESS.md` (este cierre).

`AGENTS.md` sin tocar. Sin commit: queda pendiente de tu confirmación.

---

## REFACTOR DE `mostrador.tsx`: Extracción a Componentes Atómicos

### Objetivo y contrato

Dividir el monolito `components/modules/mostrador.tsx` (**1105 líneas**) en piezas
atómicas bajo `components/modules/mostrador/`, **sin cambiar UI ni funcionalidad**,
dejando `mostrador.tsx` como orquestador (**150 líneas**). La ruta pública
`app/page.tsx` → `import { Mostrador } from "@/components/modules/mostrador"` se
preserva (no se creó `index.ts` en la carpeta para no competir con el archivo).

### Diseño de la extracción

- **El orquestador conserva el estado y el fetch.** Los 4 `useApi`
  (`turnos`, `reservas`, `aforo`, `jornada`), el hook `useDeudasDelDia` y el
  `refrescar` (`Promise.all` de los 4 `recargar`) siguen en `mostrador.tsx`. Los
  hijos son presentacionales y reciben props tipadas con `EstadoApi<T>`, así que
  el fetch, la `clave=fecha` y la recarga post-mutación son exactamente los mismos.
- **Modales:** `cargar-cliente-modal.tsx` (460) y `agregar-turno-modal.tsx` (212),
  movidos **verbatim** (verificado con `git diff`).
- **Vistas:** `tabla-turnos.tsx` (38), `tabla-guarderia.tsx` (64),
  `widget-aforo.tsx` (47), `avisos-importantes.tsx` (118) y `tablero-jornada.tsx`
  (50), que compone las tablas y conserva la condición de carga/error del panel.
- **Auxiliares:** `columna-lista.tsx` (50, ex `TurnoCol`) y `use-deudas-del-dia.ts`
  (90, incluye `export type DeudasDelDia = ReturnType<typeof useDeudasDelDia>`).
- **Constantes y helpers** (`ESPECIES`, `CONDUCTAS`, `CONDUCTAS_DE_RIESGO`,
  `RESERVAS_QUE_OCUPAN`, `hhmm()`) se colocaron junto a su único consumidor; no se
  creó `constantes.ts`.

### Incidente y corrección

En la primera pasada de `cargar-cliente-modal.tsx` se reescribió JSX y estado en
vez de copiarlos; se detectó con `git diff --no-index` contra el monolito y se
restauró el contenido **verbatim** (incluidos los comentarios). Lo mismo se hizo
para `agregar-turno-modal.tsx`. Los archivos de panel se validaron por diff
normalizado.

### Bloqueo ajeno resuelto (con confirmación del Dueño)

`app/page.tsx` tenía, en el worktree y **fuera del alcance del refactor**, un
`<AvisoDemo titulo="Mostrador" />` sin el prop `texto`, lo que rompía
`npx tsc --noEmit`. Dado que el tablero del Mostrador ya usa datos reales (TANDA 2),
se confirmó con el Dueño y se **eliminó el aviso obsoleto**.

### Verificación

- `npx tsc --noEmit` → limpio.
- `.\test-harness.ps1` → **"Todos los controles pasaron"**: TSC limpio, build de
  Next.js OK, **157 tests de Spring Boot, 0 fallos**.
- Sin tests de frontend: la equivalencia visual se sostiene por construcción (JSX
  y clases copiados del original y verificados por diff).

### Archivos (esta fase)

- `components/modules/mostrador.tsx` (reescrito como orquestador, 150 líneas).
- `components/modules/mostrador/{widget-aforo,avisos-importantes,tablero-jornada,
  tabla-turnos,tabla-guarderia,columna-lista,cargar-cliente-modal,
  agregar-turno-modal}.tsx` y `use-deudas-del-dia.ts` (nuevos).
- `app/page.tsx` (eliminación del `AvisoDemo` de Mostrador sin `texto`).
- `PROGRESS.md` (este registro).

`AGENTS.md` sin tocar. Backend sin cambios. Sin commit: pendiente de tu confirmación.
