# PROGRESS.md

## Estado Actual
- Tarea en curso: [Ninguna] — **TANDA 2 / FASE 2 FINALIZADA**
- Agente asignado: Orquestador (diagnóstico) -> Implementador -> Revisor/Harness
- Bloqueantes: [Ninguno]
- Base: `c264145` (FASE 1 + FASE 2 + FASE 3 commiteadas por el dueño)
- Harness: `tsc --noEmit` limpio, `next build` OK, **154 tests backend en verde**
  (145 previos + 9 nuevos de `FechasFlexiblesApiTest`)
- Entregado en TANDA 2: el tablero del Mostrador contra la API real (**cierra el
  riesgo abierto nro 1**), el `DateTimeParseException` de `ficha.tsx` reproducido y
  arreglado, y los turnos retroactivos verificados como ya soportados.
- Sin commitear: 12 archivos modificados + 2 nuevos, entre ellos los 6 de frontend
  de HOTFIX-UI, que siguen pendientes del turno anterior.

> Nota de arranque: el `PROGRESS.md` del working tree había quedado sobrescrito con
> una versión vieja (TASK-005) y perdía el historial de TASK-006 a TASK-009. Se
> restauró con `git checkout -- PROGRESS.md` antes de arrancar; el trabajo de las
> fases previas estaba commiteado y no se perdió nada.

## Diagnóstico de HOTFIX-UI (Orquestador)

El encargo tiene cuatro partes. **Dos se aplican tal como están escritas, una
necesita una corrección que no es negociable, y una tiene un dato de contrato
equivocado.**

### 0. Dos aclaraciones del dueño que fijan el criterio de aceptación

1. **Lo que realmente se pide en la navegación es que Recepción y Veterinario vean
   los mismos módulos, en el mismo orden.** "Balance es el único módulo que no
   tienen que ver ni Recepción ni Veterinario" es la regla, y el orden de 7 ítems
   es la forma de expresarla. El dueño además habilitó que Balance ocupe el
   anteúltimo lugar, así que **Egreso queda último** y **Balance, cuando aparece,
   queda justo antes**. Con esto la lista de Recepción y la de Veterinario son
   idénticas (7 ítems) y la del Dueño es esa misma más Balance.
2. **"Agregar turno" también tiene que persistir.** El dueño pipedó que el botón
   del Mostrador impacte en la base y se vea en Agenda. Se amplía el alcance de la
   parte C a los dos modales del Mostrador, no solo a "Cargar Cliente".

### 1. La navegación no está unificada — CONFIRMADO, dos módulos se exchanging

`components/sidebar.tsx:49-61` declara roles por módulo y hoy los dos roles
operativos **no ven lo mismo**:

| Módulo | roles actuales | ¿Recepción? | ¿Veterinario? |
|---|---|---|---|
| `mostrador` | recepcion, veterinario, duenio | sí | sí |
| `agenda` | recepcion, veterinario, duenio | sí | sí |
| `padron` | recepcion, veterinario, duenio | sí | sí |
| `consultas` | veterinario, recepcion | sí | sí |
| `cobros` | recepcion, duenio | sí | **NO** ← falla |
| `egreso` | recepcion, veterinario, duenio | sí | sí |
| `balance` | duenio | no | no |
| `fichas` | veterinario, duenio | **NO** ← falla | sí |

Son exactamente los dos desvíos que describe el encargo: falta `veterinario` en
Caja y falta `recepcion` en Historial. El **orden** también difiere: `agenda` está
segundo y `padron` tercero, al revés de lo pedido.

### 2. La fecha del Mostrador está congelada en el código — CONFIRMADO

`lib/store.tsx:117` tiene `const TODAY = "2026-09-23"`, y `Mostrador` lee ese
`today` del store en las cincoavas (`movimiento de hoy`, `ocupadosPorDia(today)`,
`turnos.filter(t.fecha === today)`, `reservas.filter(ingreso === today)`, etc.). No
es un defaults que se pueda pisar desde la vista: la fecha no viene del
componente, viene de una constante de módulo. Los `seed*` arrastran la misma
congelación (`2026-09-24`, `2026-09-25`, `2026-08-10`, `2026-07-02`).

**Corrección obligatoria sobre la expresión sugerida.** El encargo propone
`new Date().toISOString().split('T')[0]`. **Eso ya se sabe que está mal en este
repositorio**: `toISOString()` devuelve la fecha en UTC, así que entre las 21:00 y
las 24:00 de una zona UTC-3 devuelve el día de mañana. Ya existe para esto
`hoyLocal()` (`lib/api.ts:674`), creado en FASE 3 exactamente por este motivo, con
el motivo escrito al lado. Se usa `hoyLocal()`, que devuelve el mismo formato
`YYYY-MM-DD` pero en hora local: cumple la intención ("fecha actual del sistema") y
no reintroduce el bug que este proyecto ya documentoó. **Aplicar la expresión
literal sería una regresión.**

### 3. "Cargar Cliente" no persiste — CONFIRMADO, y es el riesgo abierto nº1

`CargarClienteModal` (`components/modules/mostrador.tsx:153-228`) llama a
`addOwnerWithDog`, que escribe en el `useState` del store. La pantalla acepta el
alta, el operador la ve aparecer y al recargar no hay nada. Es literalmente el
riesgo que FASE 1 dejó anotado ("queda como trampa conocida: la forma de hacerlo
bien ya existe en el Padrón").

El `POST /tutores` y el `POST /mascotas` ya están cableados en `lib/api.ts`.

### 4. La "Conducta" del encargo no corresponde a la columna — DATO ERRÓNEO

El encargo pide "Conducta (TRANQUILO, REACTIVO, etc.)". Esos cuatro valores
**no existen** en `Mascota.conducta`:

- `Mascota.Conducta` = `NORMAL, GRUNE, MUERDE, MIEDOSO`
  (`MascotaRequestDTO` lo valida con `@Pattern("^(normal|grune|gruñe|muerde|miedoso)$")`).
- `TRANQUILO, NERVIOSO, REACTIVO, AGRESIVO` son de **`ConsultaMedica.conductaObservada`**,
  el enum de cómo se comportó el animal *durante una atención*, no de cómo es.

Son dos cosas distintas y el formulario de alta pide la segunda. Mandar
`"TRANQUILO"` devuelve un 400 cuyo mensaje ("La conducta debe ser normal, gruñe,
muerde o miedoso") no coincide con lo que el operador tocó. Se implementa el enum
real de la columna (`normal / gruñe / muerde / miedoso`, el mismo que ya usa
`alta-mascota.tsx` y el tipo `ConductaMascota` de `lib/api.ts`) y se deja
constancia. La observación "conducta del paciente" ya tiene su propio lugar, que es
la ficha médica.

### 5. Dato de contrato relevante para el alta unificada

`TutorRequestDTO.dni` es `@NotBlank` con `@Pattern("\\d{7,10}")`, y `telefono`
`@Pattern("\\d{6,15}")`. El modal viejo aceptaba nombre/apellido/DNI/teléfono
libres y además "otros tutores" con **solo nombre y teléfono**, sin DNI. Ese bloque
no es trasladable tal cual: sin DNI no hay alta de tutor válida. Se reemplaza por
un vinculo a un tutor **ya cargado**, que además es el caso real (el cotutor que
acompaña al dueño es alguien que ya está en el padrón).

## Cambios de HOTFIX-UI

**Archivos tocados (7, todos de frontend; el backend no se tocó):**

| Archivo | Qué cambió |
|---|---|
| `components/sidebar.tsx` | Orden único de navegación + roles corregidos |
| `app/page.tsx` | Guarda de rol del Balance, `fichaMascotaId` numérico, avisos honestos |
| `components/top-bar.tsx` | Búsqueda global contra la API (leía el store) |
| `components/modules/fichas.tsx` | HistRIAL pasa del store a la API |
| `components/modules/mostrador.tsx` | Los dos modales pasan del store a la API |
| `lib/store.tsx` | `TODAY` dinámico, semillas relativas, Functions trampas borradas |
| `PROGRESS.md` | Este registro |

### A) Navegación y roles

`TABS` es ahora **una sola lista ordenada** de la que salen las tres superficies
de navegación (lateral de escritorio, barra inferior de móvil y cajón de móvil):
las tres hacen `TABS.filter(por rol)`, así que no pueden divergir. El filtro se
aplica *sobre* la lista y nunca al revés, que es lo que garantiza que cambiar de
rol no mueva nada de su lugar.

Orden final (idéntico para los tres roles):

1. Mostrador · 2. Padrón · 3. Agenda · 4. Ficha médica · 5. Caja ·
6. Historial · 7. **Balance (solo Dueño)** · 8. Egreso

Al filtrar, Recepción y Veterinario ven **exactamente los mismos 7 módulos en el
mismo orden** (Balance desaparece), y el Dueño ve esos 7 más Balance. Egreso
último en los tres casos, y en Recepción/Veterinario el Historial queda
anteúltimo. Los dos desvíos que existían están corregidos: `veterinario` se
sumó a `cobros` y `recepcion` a `fichas`.

**Balance.** Es el único módulo que no ven ni Recepción ni Veterinario, y se
mantiene exclusivo del Dueño. Se agregó una segunda guarda en `app/page.tsx`
(`esDuenio &&`) además de la del sidebar, para que la métrica de recaudación no
dependa de que la navegación se mantenga sincronizada. **La tarjeta "Total en
caja" vive en `balance.tsx`, no en `caja.tsx`**: se verificó que el módulo Caja
no tiene ninguna métrica de recaudación, así que "habilitar Caja al veterinario
sin el balance" se resuelve sin tocar Caja. No se inventó una tarjeta para
ocultar.

### B) Fecha dinámica

`lib/store.tsx` tenía `const TODAY = "2026-09-23"`, y el Mostrador lee ese
`today` del store en las cincoavas. Ahora `TODAY` se calcula con `hoyLocal()`.

**Se apartó de la expresión sugerida en el encargo a propósito.** El encargo pedía
`new Date().toISOString().split('T')[0]`; eso devuelve la fecha **en UTC**, así
que entre las 21:00 y las 24:00 de una zona UTC-3 devuelve el día de mañana y el
tablero abriría en una jornada que todavía no empezó. Es el bug que FASE 3 ya
había encontrado y documentado, y para el que existe `hoyLocal()` en
`lib/api.ts`. Se reutilizó ese helper: mismo formato `YYYY-MM-DD`, hora local.
Aplicar la expresión literal habría reintroducido la regresión.

Las semillas también estaban congeladas (`2026-09-24`, `2026-09-25`, `2026-08-10`,
`2026-07-02`): con `TODAY` dinámico quedaban ancladas a días pasados y el tablero
mostraba cero ocupados y cero turnos. Se pasaron a `relativo(+N)`.

### C) Alta unificada "Cargar cliente" (persistía en memoria)

`CargarClienteModal` llamaba a `addOwnerWithDog`, que escribe en el `useState` del
store: la pantalla aceptaba el alta, el operador la veía aparecer y al recargar no
había nada. Es el riesgo abierto nº1 que venía anotado desde FASE 1.

Ahora va contra la API real, en **tres llamadas y en ese orden**:

1. `POST /tutores` — el DNI es `@NotBlank` con 7 a 10 dígitos, así que es primero
   porque de su id sale el vínculo.
2. `POST /mascotas` — sola, sin tutor.
3. `POST /tutores/{id}/mascotas/{mascotaId}?autorizadoRetiro=` — la tupla `$N:M`
   con su flag explícito (regla 2.4). El cotutor se suma con un segundo POST.

**Por qué tres llamadas y no el `POST /mascotas` anidado que ya usa
`alta-mascota.tsx`.** Ese anidado hace la alta de la mascota **y** el vínculo en
una transacción (más limpio), pero **sincroniza la lista completa** de tutores de
la mascota: lo que no venga en `tutores` queda desvinculado. Con el cotutor
opcional eso obliga a armar la lista con los dos de entrada. La ruta por tutor
(`TutorServiceImpl.vincularMascota`) toca **solo** los vínculos de ese tutor, así
que sumar el cotutor al final no puede pisar el vínculo del titular. Está
verificado contra la base, no supuesto (ver abajo, caso 6).

El precio de esa decisión es que, si falla el paso 2, queda un tutor creado sin
animal. El `catch` distingue los dos casos y, cuando el tutor ya se creó, informa
el DNI para que el operador pueda limpiarlo en el Padrón.

**El bloque "otros tutores" se cambió de contrato.** El modal viejo aceptaba
nombre y teléfono libres, sin DNI, y con `@NotBlank` sobre el DNI **no hay alta
de tutor válida sin él**. Ahora es un selector de un tutor **ya cargado** del
padrón, que además es el caso real (el cotutor que acompaña al dueño ya está en
el padrón). Se crea con `autorizadoRetiro=false`; el titular nace con `true`.

**Conducta.** El encargo pedía `TRANQUILO, REACTIVO, etc.`; se implementó el enum
real de la columna (`normal / gruñe / muerde / miedoso`). Está verificado que
mandar `TRANQUILO` devuelve 400 (ver caso 12b). Los cuatro valores del encargo
pertenecen a `ConsultaMedica.conductaObservada`, que es cómo se portó el animal en
*una* atención y tiene su lugar en la ficha médica, no en el alta.

Se agregaron los `maxLength` que faltaban (`@Size` de nombre 80, raza 80,
observaciones 500, motivo 300) para no gastar un viaje en un 400 previsible, igual
que ya se hacía con el DNI y el peso.

### D) "Agregar turno" ahora impacta en la base

El dueño pidió que este botón también persista y se vea en Agenda. `AgregarTurnoModal`
pasó de `addTurno` (store) a `POST /api/v1/turnos`. Como Agenda lee la base, el
turno aparece ahí. Se agregó el selector de **profesional**, que es
`@NotNull` en el contrato: sin él el backend rechaza el turno, así que no puede
ser opcional. Se manda solo `fechaHoraInicio`: el backend deriva el fin (+45) y el
fin de bloque (+60), y mandarlos sería saltarse la ventana de desinfección. El
409 de solapamiento se muestra tal cual lo devuelve el dominio.

### E) El Historial tenía que dejar de leer el store

`fichas.tsx` leía de `lib/store.tsx`. Eso rompía el requisito literal de que el
animal nuevo aparezca en Historial: la pantalla aceptaba el alta, decía que ya
estaba disponible y el animal no aparecía nunca, porque nunca pasó por el store.
Pasó a `GET /mascotas` + `GET /consultas/mascota/{id}`.

Como consecuencia, `top-bar.tsx` también pasó a la API: su búsqueda era del store
así que solo encontraba los datos semilla, y el deep-link a Historial pasaba un id
de demostración que ya no existe en la base.

**No hizo falta ningún evento global de "refrescar".** Los módulos se montan y
desmontan al cambiar de pestaña (`app/page.tsx`), así que `useApi` vuelve a pedir
al entrar. No hay estado compartido que invalidar, y no se inventó un bus de
eventos para algo que el montaje ya resuelve.

### F) Trampas borradas del store

`addOwnerWithDog` y `addTurno` quedaron sin llamadores y **se eliminaron** de la
interfaz y de la implementación. `owners` y `dogs` perdieron el setter: ya no se
mutan. No alcanza con que el modal no las use — si el próximo que las use vuelve
a construir el mismo truco sin darse cuenta, la falla reaparece sola.

### Revisión del Revisor (HOTFIX-UI)

Cuatro cosas se corrigieron antes de cerrar:

1. **Un comentario que afirmaba algo falso sobre el backend.** Se había escrito
   que el segundo `POST` de vínculo podía borrar el del titular por
   `sincronizarTutores`. Se leyó `TutorServiceImpl.vincularMascota`: agrega un solo
   vínculo y es idempotente por tutor, no sincroniza. El comentario ahora dice la
   verdad, que además es la razón *mejor* de las tres llamadas.
2. **Mensaje de éxito que sobrevivía al cierre.** El modal queda abierto tras
   guardar (para que el operador vea qué se cargó), pero `exito` no se limpiaba al
   reabrir: se veía el "se cargó" del alta anterior con el formulario vacío y el
   botón de guardar oculto. Se limpia en el `useEffect` de apertura.
3. **La acción de éxito que no seonnetía.** `POST /mascotas` acepta
   `@Pattern` para `sexo` (`Macho`/`Hembra`) y `@Past` para `fechaNacimiento`;
   el formulario confía en ambos, con el `max={hoyLocal()}` puesto a propósito y
   el `Select` offering solo valores exactos.
4. **`top-bar.tsx` roto por un hook mal usado.** Una primera versión tenía los
    `useStore()` dentro de una función que no era hook, y con condiciones sobre campos del
   store que no existen. Se rehízo con `useApi` + `useMemo`.

## Verificación end-to-end (base real)

Backend levantado con `mvnw.cmd -o spring-boot:run` contra `veterinaria_db` (vacía
al arranque), replicando **exactamente** lo que envían los modales nuevos:

| # | Caso | Resultado |
|---|---|---|
| 1 | `POST /tutores` (Ana Prueba, DNI 30111222) | 201, id 830 |
| 2 | `POST /tutores` cotutor (Marco Prueba) | 201, id 831 |
| 3 | `POST /mascotas` con los 8 campos del formulario (especie CANINO, raza Mestizo, sexo Macho, fecha 2022-05-10, peso 12.5, conducta `gruñe`, observaciones) | 201, id 3714 |
| 4 | `POST /tutores/830/mascotas/3714?autorizadoRetiro=true` | 201 |
| 5 | `POST /tutores/831/mascotas/3714?autorizadoRetiro=false` (cotutor) | 201 |
| 6 | **`GET /mascotas/3714` → ambos vínculos vivos** | **Ana `autorizadoRetiro=true`, Marco `autorizadoRetiro=false`** |
| 7 | `GET /consultas/mascota/3714` (lo que pide el Historial) | 200, 0 consultas |
| 8 | `GET /mascotas` (Ficha Médica) | el animal aparece |
| 9 | `POST /turnos` 09:00 + profesional activo | 201, `fin=09:45`, `finBloque=10:00` (regla 45+15) |
| 10 | `GET /turnos?fecha=<hoy>` (Agenda) | el turno aparece |
| 11 | `POST /profesionales` | 201, `activo=true` |
| 12a | DNI de 5 dígitos | **400** "El DNI debe contener entre 7 y 10 digitos" |
| 12b | `conducta: "TRANQUILO"` | **400** "La conducta debe ser normal, gruñe, muerde o miedoso" |
| 12c | `fechaNacimiento` futura | **400** "La fecha de nacimiento no puede ser futura" |
| 12d | `sexo: "macho"` en minúscula | **400** "El sexo debe ser Macho o Hembra" |
| 13 | Turno solapado 09:20 con el de 09:00 | **409** con el detalle de la ventana de desinfección |

El caso 12b es la comprobación empírica del dato equivocado del encargo, y el 6 es
la comprobación de que el orden de las tres llamadas no rompe el vínculo del
titular.

Base dejada en cero (8 tablas) y proceso de Java detenido.

## Reporte del harness (HOTFIX-UI)

`.\test-harness.ps1` — **todos los controles pasaron**:

- `tsc --noEmit`: limpio.
- Next.js (webpack build): OK.
- Backend: `BUILD SUCCESS`, **145 tests en verde** (Failures 0, Errors 0, Skipped 0).

Sin tests nuevos: el backend no se modificó, así que no hay contrato nuevo que
cubrir del lado del servidor. La cobertura de este hotfix es de integración
(verificación end-to-end contra la base real, tabla de arriba), no unitaria.

## TANDA 2 / FASE 2 — Conexión real del Tablero de Mostrador, Fix de Deserialización de Fechas y Turnos Retroactivos

### Diagnóstico del Orquestador (con evidencia, no por lectura)

El encargo tiene tres partes. **Dos ya estaban hechas y una apuntaba al archivo
equivocado**: el `DateTimeParseException` existe, pero no donde el encargo supone.

#### 1. `DateTimeParseException` — CONFIRMADO, y es peor de lo que decía el encargo

No es un fallo hipotético: se reprodujo contra `veterinaria_db`.

- **Origen real:** `components/modules/ficha.tsx` manda `fechaAtencion` con el
  valor crudo de un `<input type="date">`, o sea `YYYY-MM-DD`. El campo del
  backend, `ConsultaMedicaRequestDTO.fechaAtencion`, es `java.time.LocalDateTime`.
- **Consecuencia:** `LocalDateTime.parse("2026-10-01")` revienta. **No se puede
  guardar ninguna ficha médica desde la interfaz.** No es un caso borde: es el
  camino único que tiene la pantalla.

Evidencia (POST `/api/v1/consultas`, mascota y profesional reales):

```
fechaAtencion=2026-10-01 -> HTTP 400
  "La peticion no pudo interpretarse: JSON parse error: Cannot deserialize value of
   type `java.time.LocalDateTime` from String \"2026-10-01\":
   (java.time.format.DateTimeParseException) Text '2026-10-01' could not be parsed at index 10"
```

Lo que **ya** funciona y por eso no se toca (verificado en la misma corrida):

| Payload | Resultado |
|---|---|
| `2026-10-01T00:00:00` | deserializa bien |
| `2026-10-01T09:00` (sin segundos) | deserializa bien |
| `2026-10-01T09:00:00.000Z` (`toISOString()`) | deserializa bien |
| `ReservaGuarderia`: `fecha=YYYY-MM-DD`, `horaEntrada=HH:mm` | 201 |

O sea: los otros dos DTO con fecha (`TurnoClinicoRequestDTO`, `ReservaGuarderiaRequestDTO`)
**no tienen el problema**. El arreglo se concentra donde el bug está.

#### 2. `@JsonFormat` con secciones opcionales: NO SIRVE contra un `LocalDateTime`

La expresión sugerida en el encargo,
`@JsonFormat(pattern = "[yyyy-MM-dd'T'HH:mm:ss][yyyy-MM-dd'T'HH:mm][yyyy-MM-dd]")`,
**no puede funcionar** sobre un campo de tipo `LocalDateTime`. Las tres secciones
son opcionales, así que el `DateTimeFormatter` acepta `2026-10-01`, pero después
`LocalDateTime.from(...)` no encuentra componente horario y tira
`DateTimeParseException`: el error se corre de lugar, no se arregla.

Por eso la implementación es un **deserializador propio** (`FlexibleLocalDateTimeDeserializer`)
con una cadena de intentos explícita, en vez de confiar en la semántica de las
secciones opcionales. Hace lo mismo y además deja el mensaje de error util.

#### 3. Turnos retroactivos: YA ESTÁ HECHO (no se vuelve a hacer)

El encargo pide "quitar validaciones `@Future`". Se verificó que **no existe ninguna**:

- `TurnoClinicoRequestDTO.fechaHoraInicio` no lleva `@Future`, y su javadoc dice
  explícitamente que acepta valores pasados a propósito.
- No hay ningún `@Future` ni comparación contra `LocalDateTime.now()` en todo el
  backend (`grep` sobre `src/main/java`).
- Ya existe `TurnoRetroactivoTest` (7 tests) cuya función declarada es justamente
  ser la barrera: *"si alguien agrega un `@Future` o una comparación contra
  `LocalDate.now()` al path de turnos, esta clase se pone roja"*.
- Verificado en vivo: `POST /turnos` con `2026-10-01T09:00` → **201**, y con
  `2026-12-25T10:00` → **201**. El solapamiento sigue devolviendo 409.

**Se mantiene tal cual y se deja constancia.** Lo único que faltaba del punto C
era documentarlo, que es lo que hace esta entrada.

#### 4. Los `@RequestParam` de fecha por día: YA ESTÁN BIEN

El encargo pide tipar `GET /api/v1/turnos` con
`@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fecha`.
**Ya está así**, y en los siete endpoints que filtran por día
(`TurnoClinicoController` x2, `ReservaGuarderiaController` x2, `CajaController`,
`EgresoController`, `ComandaCobroController`). Verificado: `?fecha=2026-10-01` → 200.
No se modifica ninguna firma.

### Decisiones de diseño de la TANDA 2

- **El tablero se arma con 4 llamadas, no con un endpoint nuevo.** El encargo nombra
  `GET /turnos?fecha=` y `GET /guarderia?fecha=`, y son las que se usan:
  - `GET /turnos?fecha=` → columna "Turnos".
  - `GET /guarderia?fecha=` → columnas "Ingresan" y "Se retiran" (misma lista, dos
    cortes por `horaEntrada` / `horaSalida`).
  - `GET /guarderia/aforo?fecha=` → panel de ocupación, en vez de `ocupadosPorDia()`
    del store.
  - `GET /egreso/pacientes-hoy?fecha=` → una sola llamada que trae `conducta` y los
    `tutores` de cada mascota presente: con eso salen los avisos de manejo y la
    lista de tutores del día. No se agregó endpoint.
- **Los avisos de morosidad hacen N+1 a propósito.** No existe consulta masiva de
  deuda: `/tutores/{id}/deuda` es por tutor. Se deduplica por `tutorId`, se piden en
  paralelo con `Promise.allSettled`, y **un fallo parcial se informa en pantalla**
  en vez de droppear la deuda en silencio (ocultar morosidad es peor que mostrarla
  con una advertencia).
- **"Se retiran" significa reservas *de esa fecha*, no de todas las fechas.** El
  endpoint filtra por `r.fecha`, así que una reserva que empezó ayer y termina hoy
  no aparece. Se rotula la columna para no mentir, en vez de agregar un finder de
  `hora_salida` que el encargo no pidió.
- **No hay bus global de refresco.** Los módulos se montan/desmontan al cambiar de
  pestaña (`app/page.tsx`), así que `useApi` vuelve a pedir al volver al Mostrador.
  Lo que sí hace falta —y se implementa— es el `recargar()` explícito después de
  crear un turno *desde* el Mostrador, que es el único caso en que el componente no
  se desmonta. Se verificó end-to-end el camino de la Agenda.
- **`ficha.tsx` manda ISO completo** (`YYYY-MM-DDT00:00:00`), que es lo que el
  encargo pide del lado del cliente, y el backend queda tolerante igual para
  cualquier otro cliente (curl, móvil, tests).

### Cambios de TANDA 2 (Implementador)

#### A) El tablero del Mostrador ahora lee la base

`components/modules/mostrador.tsx` fue reescrito en su parte de tablero. **El
componente ya no importa `lib/store.tsx`**: no queda ni un `useStore()` en el
archivo. Antes tomaba seis cosas del store (`turnos`, `reservas`, `dogs`,
`owners`, `today`, `cupoMax`, `ocupadosPorDia`), todas semillas en memoria.

| Antes (demo) | Ahora (API real) |
|---|---|
| `turnos.filter(t => t.fecha === today)` | `GET /turnos?fecha={fecha}` |
| `reservas.filter(r => r.ingreso === today)` | `GET /guarderia?fecha={fecha}`, corte por `horaEntrada` |
| `reservas.filter(r => r.egreso === today)` | el mismo `GET`, corte por `horaSalida` |
| `ocupadosPorDia(today)` / `cupoMax` | `GET /guarderia/aforo?fecha={fecha}` |
| `dogs` + `owners` cruzados a mano | `GET /egreso/pacientes-hoy?fecha={fecha}` |
| `owners[].deuda` (semilla) | `GET /tutores/{id}/deuda` por tutor presente |

Detalles que quedaron documentados en el código porque son decisiones, no
accidentes:

- **Las cuatro llamadas llevan la fecha como `clave` de `useApi`, no solo como
  closure.** `useApi` guarda la fn en un ref, así que sin la clave, cambiar de día
  no dispararía nada y la grilla mostraría el día anterior. Es el mismo patrón que
  ya usa `ficha.tsx`.
- **Se agrega un selector de día** (`<input type="date">` + botón "Hoy"), porque
  el encargo pide recargar las tablas al cambiar el día y sin selector no había
  forma de cambiarlo. El store conserva su `today` para el resto de las pantallas.
- **Se filtra por `RESERVAS_QUE_OCUPAN = {RESERVADO, INGRESADO}`**, que es el
  criterio del backend en `JornadaDelDiaService`. FINALIZADO y CANCELADO liberan
  el cupo, así que no pueden figurar como "ingresa hoy". Copiarlo evita que el
  tablero y el aforo discrepen sobre la misma fecha.
- **Los turnos CANCELADOS se excluyen de la grilla y se cuentan aparte** en una
  nota al pie de la columna, en vez de desaparecer sin explicación.
- **`hhmm()` recorta las horas** que Jackson emite como `"08:00:00"` a `"08:00"`,
  porque el resto de la pantalla está en `HH:mm`.
- **El aviso de morosidad no dice "nadie debe" mientras la consulta está en
  vuelo.** Se pintea un "Consultando la deuda de los tutores del {fecha}…" hasta
  que `Promise.allSettled` resuelva. Mostrar "sin avisos" durante la carga sería
  una afirmación falsa, y en la pantalla de morosidad (regla 2.5) es el peor
  resultado posible.
- **Un fallo parcial en la deuda se informa** con un contador y un botón de
  reintento. Con `Promise.all` (en vez de `allSettled`), un tutor sin red tumba
  los avisos de los demás.
- **Los dos modales ganan un `onGuardado`.** Es la invalidación que sí hace
  falta: el modal vive dentro del Mostrador, así que el componente no se desmonta
  entre el alta y la lectura, y sin esto el turno recién creado no aparecería en la
  grilla de arriba. El `recargar()` corre **antes** del mensaje de éxito: si la
  recarga fallara, el operador no debe leer un "guardado" sobre una pantalla que
  sigue mostrando la jornada anterior.

**No se inventó bus global de refresco.** Los módulos se montan/desmontan al
cambiar de pestaña (`app/page.tsx`), así que `useApi` vuelve a pedir al volver al
Mostrador. Ese camino se verificó a mano (sección de verificación, punto 5).

#### B) Fechas: toleradas en el backend, ISO completo desde el frontend

- **Backend, nuevo:** `config/FlexibleLocalDateTimeDeserializer.java`. Extiende
  `tools.jackson.databind.ValueDeserializer<LocalDateTime>` (Jackson 3) y encadena
  intentos explícitos: ISO con offset → ISO con espacio → fecha sola a
  `atStartOfDay()`. Si ninguno sirve, lanza un `InvalidFormatException` que **dice
  qué formatos acepta**, en vez del inútil `could not be parsed at index 10`.
- **Backend, aplicado en 2 DTO:** `ConsultaMedicaRequestDTO.fechaAtencion` (el
  que estaba roto) y `TurnoClinicoRequestDTO.fechaHoraInicio` (por tolerancia de
  formato, no por permitir el pasado: el frontend ya mandaba ISO).
- **Frontend:** `ficha.tsx` pasa a mandar `` `${fechaAtencion}T00:00:00` ``, y el
  texto del campo aclara que la hora queda en 00:00 porque el input es solo de día.
- **El mensaje de error mejoró de paso**, sin tocar `GlobalExceptionHandler`: el
  texto nuevo sale del propio `InvalidFormatException`.

#### C) Turnos retroactivos: verificado, no modificado

No se tocó ninguna validación porque no hay ninguna que tocar. Ver la sección de
diagnóstico, punto 3.

#### Dos bugs de fecha UTC de paso, misma clase que el de HOTFIX-UI

- `alta-mascota.tsx`: `max={new Date().toISOString().slice(0, 10)}` → `max={hoyLocal()}`.
  El `max` del campo de nacimiento bloqueaba a un animal nacido hoy entre las 21:00
  y las 24:00 de una zona UTC-3, porque `toISOString()` ya devuelve mañana.
- `guarderia.tsx`: la tira de días del calendario usaba `d.toISOString().slice(0,10)`
  y corría el día completo hacia atrás. Ahora arma la cadena con
  `getFullYear/getMonth/getDate`.

No quedan llamadas a `toISOString()` en el frontend salvo dentro de comentarios que
explican por qué no se usa.

### Verificación end-to-end contra `veterinaria_db` real

Se levantó el backend y se ejercitó por HTTP, no por lectura de código:

1. **El bug, reproducido antes del arreglo:** `POST /api/v1/consultas` con
   `fechaAtencion: "2026-10-01"` → **400** con
   `DateTimeParseException: Text '2026-10-01' could not be parsed at index 10`.
   O sea, la pantalla de ficha médica no podía guardar nada.
2. **El mismo POST después del arreglo** → **201**, persistido como
   `2026-10-04T00:00:00`. Una consulta de 2019 también entra.
3. **Formato ilegible sigue siendo 400**, y ahora el mensaje sirve:
   `"Se esperaba 'YYYY-MM-DDTHH:mm:ss', 'YYYY-MM-DDTHH:mm' o 'YYYY-MM-DD'"`.
4. **Las 4 llamadas del tablero, todas 200** con datos reales: 1 turno; aforo
   `0/10` antes de la reserva y `1/10` después; `GET /egreso/pacientes-hoy` devolvió
   `conducta=muerde` y el tutor con `autorizadoRetiro=True`, que es exactamente lo
   que consume el panel de avisos.
5. **Selector de día:** `?fecha={hoy-3}` → 3 turnos distintos, `?fecha=2099-01-01`
   → lista vacía (no un 500), `?fecha=2026-10-01T00:00:00` → **sigue siendo 400**
   (el contrato de los query params no se ablandó, solo el del body).
6. **Refresco:** turno creado por el camino de Agenda → el `GET /turnos?fecha=hoy`
   lo muestra en la llamada siguiente (`n=1` → `n=2`).
7. **Morosidad:** con dos fichas PENDIENTE, `GET /tutores/{id}/deuda` →
   `tieneDeuda=True total=16000.00`.
8. **Ventana 45+15 intacta:** turno de 10:00 → `fin=10:45`, `finBloque=11:00`.
9. **Base devuelta a cero** (truncate de las 9 tablas de prueba).

### Reporte del harness (TANDA 2)

```
.\test-harness.ps1
  1/3  tsc --noEmit .................. sin errores
  2/3  Next.js build (webpack) ....... OK
  3/3  Spring Boot ................... BUILD SUCCESS
        Tests run: 154, Failures: 0, Errors: 0, Skipped: 0
```

**154 tests, 0 fallos.** Eran 145: los 9 nuevos son de `FechasFlexiblesApiTest`.

### Cierre del riesgo abierto numero 1 — **CERRADO**

> ~~**El tablero de movimiento del Mostrador sigue siendo demo.** Lee
> `turnos`/`reservas`/`deuda` del store. Consecuencia concreta: un turno creado
> desde el mostrador **no aparece en el tablero del mostrador**~~

Cerrado por esta tanda. El tablero consulta MySQL, no importa el store, y se
relee después de cada alta hecha desde el propio Mostrador.

**Corrección al log:** no había ningún aviso en pantalla que dijera "esto es una
demo". Lo que hay en el tablero es el panel "Avisos importantes", que lista perros
con conducta de riesgo y tutores con deuda, y no advierte nada sobre datos en
memoria. El `AvisoDemo` que menciona el registro de FASE 1 más abajo no existe en
el código: `git show c264145 -- components/modules/mostrador.tsx` no lo tiene y no
aparece en ningún otro archivo del frontend. Queda la mención, porque ese registro
ya está commiteado y reescribir historia no es lo que corresponde en esta tanda.

## Archivos tocados en TANDA 2

| Archivo | Qué cambió |
|---|---|
| `backend/.../config/FlexibleLocalDateTimeDeserializer.java` | **nuevo**: deserializador tolerante de `LocalDateTime` |
| `backend/.../dto/ConsultaMedicaRequestDTO.java` | `@JsonDeserialize` en `fechaAtencion` (el campo roto) |
| `backend/.../dto/TurnoClinicoRequestDTO.java` | `@JsonDeserialize` en `fechaHoraInicio` (tolerancia de formato) |
| `backend/src/test/.../FechasFlexiblesApiTest.java` | **nuevo**: 9 tests, barrera de regresión del formato |
| `components/modules/mostrador.tsx` | tablero contra la API, selector de día, `onGuardado`, sin store |
| `components/modules/ficha.tsx` | manda ISO completo + texto del campo |
| `components/modules/alta-mascota.tsx` | `max={hoyLocal()}` en vez de UTC |
| `components/modules/guarderia.tsx` | tira de días sin `toISOString()` |

Sin cambios en firmas de endpoints, sin endpoints nuevos, sin tocar `AGENTS.md`.

### Trampa del entorno que conviene saber (no es un bug del código)

El language server de Java de VS Code compila con Eclipse (ecj) y escribe en
`backend/target/classes`. Como su classpath no ve todo, deja un `.class` con
`java.lang.Error("Unresolved compilation problems")` horneado, y maven lo toma
como actualizado y **no recompila**: `mvn compile` pasa con `exit=0` sobre un
clase roto y el fallo aparece recién en los tests, como un
`BeanCreationException` que no señala el error de verdad.

La trampa de este caso: el `.class` enmascaraba un error real de compilación
(`local variables referenced from a lambda expression must be final or
effectively final`). Si aparece un `BeanCreationException` raro en un
`ValueDeserializer` o similar, **borrar el `.class` y recompilar** antes de
depurar: el error de verdad puede estar tapado.

## Riesgos que quedan abiertos (no tocados, fuera del alcance declarado)

1. ~~El tablero de movimiento del Mostrador sigue siendo demo.~~ **Cerrado en
   TANDA 2** (ver arriba).
2. **El Balance sigue sumando pagos semilla** y no usa `GET /api/v1/caja/cierre`.
   También avisado en pantalla. Sigue siendo exclusivo del Dueño.
3. **`guarderia.tsx` no se monta** y su alta de reservas sigue escribiendo en el
   store. Sin cambios en este hotfix.
4. **`vinculado_desde`** sigue siendo un campo muerto en `entity/TutorMascota.java`:
   lo setea el servicio y nadie lo lee. Queda pendiente de la decisión del dueño
   (borrarlo o documentarlo como dato de prueba).
5. `useStore().query` sigue en el store y **repinta a todos sus consumidores en
   cada tecla** del buscador. Es preexistente y se dejó como estaba para no tocar
   la interfaz del store en este hotfix.

## Revisión de FASE 3 (Revisor)

Tres observaciones que se corrigieron antes de dar por cerrada la fase:

1. **`esFechaHoy` comparaba contra la fecha UTC.** Usaba
   `toISOString().slice(0,10)`, que entre las 21:00 y las 24:00 de una zona
   UTC-3 devuelve el día de mañana. El módulo ya tenía `aFechaLocal` con el
   comentario explicando ese mismo error, pero el helper de "hoy" no lo aplicaba.
   Con la Caja y el Egreso abriendo por defecto en hoy, el mostrador abría la
   jornada equivocada justo en la franja en la que está cerrando el día. Se
   agregó `hoyLocal()`, que arma la fecha con `getFullYear/getMonth/getDate`, y
   `esFechaHoy` pasó a usar ese helper.
2. **El selector de "quién paga" se armaba con la jornada, no con la ficha.** La
   primera versión de `caja.tsx` llamaba a `/egreso/pacientes-hoy` con la fecha de
   emisión de la comanda para sacar los tutores de la mascota. Funciona solo si el
   animal estuvo en el local ese día: una comanda vieja, cobrada hoy con el cliente
  returned de visita, mostraba la lista de pagadores de otra familia. Cambiado a
   `mascotas.buscarPorId(comanda.mascotaId)`, que es la fuente de los vínculos y no
   depende del día.
3. **Dos caminos de entrada al semáforo, un solo veredicto.** El modal de egreso
   ofrece los tutores vinculados (el caso normal, sin escribir un DNI) y deja el
   campo de DNI para quien no figura en la base. No son dos reglas: los dos
   caminos llaman al mismo endpoint y pintan el mismo `EgresoValidacion`. La UI no
   decide habilitación; sólo muestra el `autorizadoRetiro` como dato.

Sobre los criterios de aprobación de la sección 4.3:

- **Capas:** los tres controladores delegan sin lógica; toda decisión vive en
  `CajaServiceImpl`, `ComandaCobroServiceImpl` y `JornadaDelDiaServiceImpl`. No se
  agregaron repositorios fuera de `ComandaCobroRepository`.
- **Ciclos de Jackson:** `PagoResponseDTO` grown con dos campos planos
  (`tutorPagadorId`, `nombreTutorPagador`) en vez de un `Tutor` anidado. El DTO
  sigue siendo una proyección sin retroceso.
- **Semánticas HTTP:** `400` para tutor no vinculado y para estado de comanda
  inválido, `404` para pagador inexistente, `409` para comanda cancelada o
  saldada (comportamiento previo, sin cambios). Ningún endpoint devuelve 409 por
  un retiro bloqueado: sigue siendo una respuesta válida con veredicto.
- **Carga y error:** ambas vistas usan `useApi` con `clave` por fecha, y `Recurso`
  / `ErrorBox` para los tres estados. El reintentar es explícito, no automático.
- **Responsive:** los controles del filtro de caja bajan a una columna bajo `sm`
  y las filas de egreso apilan el botón debajo del dato. Todos los inputs y
  botones llevan `TOQUE_MINIMO` (44 px), y el modal es `max-w-lg` a pantalla
  completa en móvil.

## Cambios de FASE 3

**Backend — nuevo:**
- `service/JornadaDelDiaService` + `service/impl/JornadaDelDiaServiceImpl`: una
  sola definición de "actividad del día" (turno no cancelado o reserva
  RESERVADO/INGRESADO), ordenada por el primer horario y con las mascotas sin hora
  al final, igual que `findByFechaOrdenadasPorHorario`.
- `dto/MascotaDelDiaResponseDTO`, `dto/TutorDelDiaResponseDTO`: la proyección que
  comparten egreso y caja.

**Backend — endpoints:**
- `GET /api/v1/comandas/dia?fecha&tutorId&estado` (Caja del Día; los tres
  filtros opcionales, `fecha` por defecto hoy).
- `GET /api/v1/egreso/pacientes-hoy?fecha` (candidatos del checkout).
- `GET /api/v1/caja/tutores-del-dia?fecha` (a quién cobrarle).
- `POST /api/v1/caja/pagos/{id}` acepta `tutorId` opcional.

**Backend — cobros:**
- `Pago.tutorPagador` (columna `tutor_pagador_id`, nullable) y su proyección en
  `PagoResponseDTO`. La deuda **no** cambia de dueño: se registra quién pagó.
- `resolverPagador` en `CajaServiceImpl`: acepta al deudor o a cualquier tutor
  vinculado a la mascota de la comanda. **Valida la vinculación, no
  `autorizado_retiro`**: el flag gobierna el retiro (regla 2.4), no el cobro. Sin
  mascota en la comanda, solo el titular.
- `findDelDia(inicio, fin)` en `ComandaCobroRepository`, rango semiabierto.
- `mapper/CobroMapper` proyecta el pagador.

**Frontend:**
- `lib/api.ts`: `hoyLocal()`, tipos `MascotaDelDia` / `TutorVinculado` /
  `TutorDelDia` / `MotivoJornada`, `comandas.listarDelDia`,
  `caja.tutoresDelDia`, `caja.registrarPago(…, tutorId?)`, `egreso.pacientesDelDia`,
  y helper `query()` que descarta parámetros vacíos.
- `components/modules/caja.tsx`: reescrito como Caja del Día.
- `components/modules/egreso.tsx`: reescrito como checkout de la jornada, con
  botón "Validar egreso" por fila.

**Tests nuevos (24):** `JornadaDelDiaTest` (9) y `CajaDelDiaTest` (15).

### Decisiones de la revisión que conviene no perder

- **La Caja del Día filtra por fecha de emisión, no por la del turno.** La comanda
  se emite al guardar la ficha y no conoce otra fecha. Filtrar por la actividad
  mezclaría una consulta de la semana pasada en el corte de hoy.
- **Un tercero vinculado puede pagar aunque no esté autorizado a retirar.** Es el
  caso real (el hermano del dueño vino a pagar y no puede llevarse al perro) y es
  la confusión más probable de esta fase: son dos puertas distintas.
- **El deudor de la comanda no se reasigna al pagador.** Mover la deuda haría que
  el deudor real apareciera al día y el que pagó quedara moroso.
- **El turno CANCELADO y la reserva CANCELADA/FINALIZADA no cuentan como
  actividad.** Si aparecieran, la caja ofrecería cobrar una consulta que no se
  atendió.
- **`GET /comandas?tutorId=` no cambió.** Sigue siendo el historial completo de un
  tutor y `tutorId` sigue siendo obligatorio; la caja del día va por `/dia`. El
  único consumidor del endpoint viejo era `caja.tsx`, que se reescribió, y ningún
  test lo golpeaba.

## Diagnóstico de FASE 2 (Orquestador)

## Diagnóstico de FASE 3 (Orquestador)

FASE 3: Vista de Caja del Día (Turnos/Guardería de Hoy) y Checkout de Egreso
Filtrado por Fecha.

**Nota previa sobre el alcance de archivos.** Igual que en FASE 1 y FASE 2, el
encargo menciona `frontend/src/`, que no existe: el frontend vive en la raíz
(`app/`, `components/`, `lib/`). Se toman esos directorios. En el backend se
toca `entity, controller, service, dto, repository` de Comanda, Caja y Egreso,
**más `mapper/CobroMapper`**, que es el archivo que proyecta `Pago` al DTO
público y por lo tanto es inseparable de los dos módulos: sin tocarlo, el
`pago.tutorPagador` no llegaría al mostrador. No se modifica
`TurnoClinicoRepository` ni `ReservaGuarderiaRepository`: los dos métodos que
hacen falta ya existen (`findDelDia`, `findByFechaAndEstadoIn`).

Tres diagnósticos fueron planteados. **Ninguno se sostiene tal como está escrito,
y el que más daño hace es el primero**, porque su corrección literal rompería
cosas que hoy funcionan.

1. **"GET /api/v1/comandas debe filtrar por fecha" — el endpoint no existe como
   caja del día, y `fecha_emision` no es lo que el encargo sugiere.**
   `ComandaCobroController.listar` declara `@RequestParam Long tutorId`
   **obligatorio**: no hay listing global, no hay filtro por fecha y no hay
   forma de obtener "las comandas de hoy". El encarte del endpoint es además
   ambiguo: `ComandaCobro` solo tiene `fecha_emision`, y la comanda de una
   consulta se emite al guardar la ficha, no al cobrar. Filtrar "guardería del
   día" por emisión mezclaría una reserva de hoy facturada mañana con las
   comandas de hoy. **La fecha que se filtra es la de emisión**, y queda
   documentado que "caja del día" significa "lo que se emitió hoy", que es
   además lo único que un corte de caja necesita. Los filtros de `estado` y de
   `tutor` pasan a ser opcionales y combinables.

2. **"El pago puede hacerlo cualquiera de los tutores vinculados" — el
   endpoint citado no existe con esa ruta y no tiene ningún campo de pagador.**
   El cobro entra por `POST /api/v1/caja/pagos/{comandaId}`, no por
   `POST /api/v1/comandas/{id}/pagos`; y `PagoRequestDTO` solo transporta la
   lista de medios de pago. `Pago` no tiene ninguna referencia al tutor que
   paga: hoy **no se sabe quién pagó**. Agregar `tutorId` opcional y validarlo
   contra `tutor_mascota` cierra el hueco.
   El punto no obvio es **qué NO hay que hacer**: el deudor de la comanda no se
   reasigna al que paga. Mover la deuda de un tutor a otro porque un cotutor
   pagó haría que el deudor real apareciera al día y el que pagó quedara
   moroso. Lo que se registra es *quién pagó*, y la deuda sigue donde estaba.

3. **"El flag de retiro" NO aplica al cobro, y es el error más tentador de esta
   fase.** `tutor_mascota.autorizado_retiro` gobierna **quién puede retirar a la
   mascota** (regla 2.4), no quién puede pagar. Un cotutor sin autorización de
   retiro es perfectamente alguien a quien se le acepta pagar: son dos puertas
   distintas. Se valida **la vinculación**, no el flag. Usar el flag acá
   rechazaría el caso real: el hermano del dueño vino a pagar y se lleva al
   perro, y tiene prohibido retirarlo.

4. **"En Egreso, listar las mascotas con estadía del día" — CONFIRMADO, no
   existe.** `EgresoController` expone solo `/validar` y `/validar/tutor`, por
   diseño deliberado (un endpoint, un veredicto). No hay ninguna forma de
   listar la jornada, así que el mostrador no tiene de dónde partir: tiene que
   buscar la mascota entre todas, a mano, y recién ahí discover que puede
   validar. La regla de validación consolidada no se toca.

5. **Riesgo de diseño: dos consultas de "actividad del día" divergiendo.** Caja
   necesita "quién estuvo hoy" y Egreso necesita "qué pacientes están hoy". Si
   cada uno escribe su propia versión de "tiene turno o tiene reserva", el día
   que una de las dos se olvide de excluir los CANCELADOS, la caja muestra a
   alguien que nunca vino. Se crea **un solo servicio de consulta** de jornada
   y los dos módulos delegan, igual que el egreso ya delegaba la morosidad en
   `ComandaCobroService.consultarDeuda`.

6. **Riesgo de regresión en el frontend.** `GET /comandas` pasa a filtrar por
   fecha. `caja.tsx` es su único consumidor y se reescribe en esta fase, y
   **ningún test existente golpea `GET /api/v1/comandas?tutorId=`** (verificado
   por grep: los tests solo usan `POST /comandas` y `PUT /comandas/tarifas`).
   Aun así, el historial completo de un tutor no se pierde: sigue disponible en
   el detalle de `GET /tutores/{id}/deuda`, que lista las pendientes con días de
   atraso. Se deja asentado para que nadie Después lo reporte como bug.

## Diagnóstico de FASE 2 (Orquestador)

**Nota previa sobre el alcance de archivos.** El encargo menciona
`frontend/src/`, pero en este repositorio **no existe**: el frontend de Next.js
vive en la raíz, en `app/`, `components/` y `lib/`. Setomaron esos tres
directorios como las "vistas/componentes correspondientes", junto con `lib/api.ts`
y `lib/use-api.ts`, que son la capa de conexión de los módulos involucrados.
No se tocó ningún otro módulo.

Cuatro diagnósticos fueron planteados. **Dos no se sostienen al leer el código** y
uno tiene la causa en otro archivo del que se suponía:

1. **"El backend bloquea turnos en fechas anteriores a hoy" — FALSO.**
   No existe ninguna restricción que lo impida. `TurnoClinicoRequestDTO` solo
   declara `@NotNull` y `@Size` sobre `fechaHoraInicio`; no hay `@Future`,
   `@PastOrPresent` ni comparación con `LocalDate.now()` en
   `TurnoClinicoServiceImpl`. Un grep de `Future|PastOrPresent|isBefore|isAfter`
   sobre todo `backend/src/main/java` no devuelve ni una coincidencia en el camino
   de turnos. **Los turnos retroactivos ya se pueden guardar hoy.** La
   corrección de FASE 1 en `Profesional.dni` no tiene nada que ver. Este punto se
   verifica con un test en vez de asumirlo.

2. **"La agenda no refleja los turnos de la base ni filtra por fecha" — el
   síntoma es real, la causa está en otro archivo.**
   `components/modules/agenda.tsx:30` ya llama `apiTurnos.listar(fecha)` con la
   fecha del `<input type="date">`, y `TurnoClinicoController.listarPorFecha`
   (`controller/TurnoClinicoController.java:41-46`) ya acepta `?fecha=` con
   `@DateTimeFormat(ISO.DATE)`. Todo el cableado existe.
   El bug está en `lib/use-api.ts`: el hook guarda la función de carga en un
   `fnRef` y su `recargar` tiene dependencias `[activo]`. **Cambiar `fecha` no
   cambia `activo`, así que el efecto no vuelve a dispararse**: la grilla sigue
   mostrando los turnos del primer día que se cargó, para siempre. La fecha
   cambia en el input y la pantalla miente. El arreglo es una clave de recarga
   en el hook, no en la agenda.

3. **"No existe formulario operativo para reservar guardería con horario de
   entrada y salida" — CONFIRMADO, y es peor de lo que decía.**
   No solo faltan `horaEntrada` y `horaSalida`: `components/modules/guarderia.tsx`
   entero es mock (consume `useStore` y llama `addReserva`), y **no está montado
   en `app/page.tsx`** — no hay pestaña que lo muestre. En el backend,
   `ReservaGuarderia` no tiene columnas de horario.

4. **"La ficha médica no lista las mascotas ni los profesionales" — FALSO como
   diagnóstico, pero hay dos defectos reales cerca.**
   `components/modules/ficha.tsx:49-50` ya pide `apiMascotas.listar()` y
   `apiProfesionales.listar()`. Los dos selects leen la base. Lo que sí está mal:
   - El selector de mascotas muestra **solo el nombre**: con dos pacientes
     llamados igual, el veterinario no puede elegir. Falta el tutor.
   - El selector de profesionales imprime `· {p.matricula}` sin guarda. Como
     `matricula` pasó a ser `string | null` en FASE 1, un profesional sin
     matrícula renderiza literalmente "· null".
   - `turnos` se pide con `apiTurnos.listar()` **sin fecha**, o sea solo el día de
     hoy. Junto con el punto 2, esto significa que una ficha de una atención de
     ayer no puede asociar su turno.

## Diagnóstico de FASE 1 (Orquestador)

Síntoma reportado: "el frontend no está persistiendo mascotas en MySQL ni vinculándolas
con tutores, y falta la interfaz para dar de alta profesionales".

Hallazgos, con el archivo y la línea donde se comprueba:

1. **El backend NO tiene la falla de persistencia.** `MascotaServiceImpl.crear`
   (`backend/.../service/impl/MascotaServiceImpl.java:61-75`) guarda la entidad en
   `mascota` y llama a `sincronizarTutores`, que escribe las tuplas en
   `tutor_mascota`. `TutorController.vincular` (`controller/TutorController.java:93-102`)
   hace lo mismo por la vía del tutor. Las dos rutas están implementadas y
   transaccionales.
2. **La falla es de frontend: no existe ningún formulario de alta de mascota.**
   `components/modules/padron.tsx` sólo ofrece "Nuevo tutor" y un modal de vínculos.
   La única pantalla que "da de alta" una mascota es
   `Mostrador` -> `CargarClienteModal` -> `addOwnerWithDog`, que escribe en el
   store **en memoria** de `lib/store.tsx` (datos semilla `seedOwners`/`seedDogs`).
   Eso explica exactamente el síntoma: la pantalla acepta el alta y al recargar no
   hay nada, porque nunca salió del navegador.
3. **Desalineación de tipos en el contrato de mascota.** `lib/api.ts` declara
   `MascotaInput.especie: string`, pero `MascotaRequestDTO.especie` es el enum
   `EspecieRequestDTO` (CANINO, FELINO, AVE, ROEDOR, REPTIL, OTRO). Un `"canino"`
   en minúsculas que llegue desde el formulario produce un 400 confuso.
4. **La entidad `Profesional` está incompleta.** Tiene `id, nombre, apellido,
   matricula, activo`. **Faltan `dni` y `telefono`**, que exige el padrón de
   profesionales, y no hay unicidad de DNI.
5. **`ProfesionalController` sí expone lo pedido** (`GET /api/v1/profesionales` y
   `POST /api/v1/profesionales`, este último con `@Valid` y 201). Verificado, no
   requiere cambio de contrato. Lo que falta es **cobertura de tests directa**:
   ningún test ejercita `GET /profesionales` ni el rechazo de duplicados.
6. **No hay ninguna UI que llame a `POST /api/v1/profesionales`.** El único uso de
   la API de profesionales es `listar()` en `components/modules/ficha.tsx:50` para
   poblar un `<select>`. El botón de alta no existe.
7. **El Padrón no es accesible para el rol Veterinario.** En
   `components/sidebar.tsx:52` la pestaña `padron` declara
   `roles: ["recepcion", "duenio"]`; falta `"veterinario"`. Para un veterinario es
   el módulo que le dice de qué paciente es cada ficha.

## Tareas Completadas
- [x] TASK-001: Setup inicial repositorio y frontend base v0.
- [x] TASK-002: Base de datos (MySQL) y entidades core con migraciones lógicas.
- [x] TASK-003: CRUDs básicos (Tutor/Mascota/Vínculo N:M) + validaciones.
- [x] TASK-004: Turnos clínicos (bloques 45+15), guardería (aforo 10), tests.
- [x] TASK-004-HOTFIX: Ajustes de esquema y tests.
- [x] TASK-005: Ficha Médica (alertas conducta), Comanda de Cobro (desacouple clínico-contable) y Caja Multipago.
- [x] TASK-006: Circuito de Egreso Seguro y conexión end-to-end del frontend. **MVP 1 cerrado.**
- [x] TASK-007 (FASE 1): Persistencia de mascotas en Padrón + módulo de Profesionales.
- [x] TASK-008 (FASE 2): Turnos retroactivos, grilla de Agenda conectada, reserva horaria de Guardería y selects reales en Ficha Médica.

## Registro de Cambios Técnicos (TASK-008 / FASE 2)

### Backend — turnos
- **No se quitó ninguna validación**: no había `@Future` ni comparación con
  `LocalDate.now()` en el camino de turnos. Se dejó constancia en el Javadoc de
  `TurnoClinicoServiceImpl.crear` y de `TurnoClinicoRequestDTO.fechaHoraInicio`,
  y se blindó con `TurnoRetroactivoTest` (7 tests) para que nadie reintroduzca
  el límite por error.
- `TurnoClinicoRepository`: `findByFechaHoraInicioBetween` se reemplazó por
  `findDelDia` y `findDelDiaDelProfesional`, ambos con rango semiabierto
  `[inicio, fin)` y `ORDER BY fecha_hora_inicio, id`. El método anterior era
  cerrado por los dos lados y sin orden: un turno a las 00:00 exactas entraba
  en el listado de dos días, y el orden de la grilla quedaba en manos de MySQL.
- `GET /api/v1/turnos?fecha=` no se tocó: ya existía y ya filtraba.

### Backend — guardería
- `ReservaGuarderia`: columnas `hora_entrada` y `hora_salida` (`LocalTime`,
  nullable). Nullable sin valor por defecto a propósito: las reservas previas no
  tienen hora y rellenarlas sería inventar un dato.
- `ReservaGuarderiaRequestDTO` / `ResponseDTO`: `horaEntrada` y `horaSalida` en
  el contrato.
- `ReservaGuarderiaServiceImpl.aplicarHorario`: **la validación depende del tipo
  de estadía**. Es el punto no obvio de esta fase: las horas son horas de reloj,
  no instantes, así que `COMPLETA_24H` admite `08:00 → 08:00` y también
  `22:00 → 06:00` (salida del día siguiente), mientras que en `DIURNA`/`NOCTURNA`
  la igualdad sí es un error de tipeo. Una regla ingenua de "salida > entrada"
  rechazaba el caso más común de la guardería. El tipo viaja como parámetro en
  vez de leerse de la entidad porque en `actualizar` el tipo del request puede
  ser `null` mientras el horario sí se está cambiando.
- `ReservaGuarderiaRepository.findByFechaOrdenadasPorHorario`: la jornada se
  ordena con `CASE WHEN horaEntrada IS NULL THEN 1 ELSE 0 END` primero, porque
  en MySQL el `NULL` ordena antes en ascendente y las reservas sin horario
  taparían la cabeza de la lista.
- **Sin tocar el semáforo ni el lock pesimista** de TASK-004-HOTFIX: el aforo
  sigue contando una reserva por fila y por día, y `ReservaGuarderiaHorarioTest`
  incluye un caso que llena los 10 cupos con horarios distintos y verifica que el
 reserva número 11 siga recibiendo 409.

### Frontend
- `lib/use-api.ts`: nuevo tercer parámetro `clave`. Es el arreglo de raíz del
  bug de la agenda: el hook guarda la `fn` en un `fnRef` y su efecto solo
  dependía de `activo`, así que cambiar la fecha re-renderizaba sin volver a
  pedir nada. `clave` entra en las dependencias **y** dispara un `setDatos(null)`
  para que no quede la lista del día viejo en pantalla mientras vuela la nueva.
- `components/modules/agenda.tsx`: la fecha viaja como clave en los tres hooks;
  nuevo botón "Agendar turno" (el input de fecha **no lleva `min`**); la reserva
  muestra el horario; se corrigió `CANCELADA` → `CANCELADO` en el semáforo (el
  enum es `CANCELADO`, la condición nunca era verdadera).
- `components/modules/nuevo-turno.tsx` (nuevo): modal de alta contra
  `POST /api/v1/turnos`.
- `components/modules/nueva-reserva.tsx` (nuevo): modal "Nueva reserva de
  guardería" contra `POST /api/v1/guarderia` con mascota, fecha, entrada, salida
  y tipo de estadía. El semáforo muestra el aforo **de la fecha del formulario**,
  no el de la fecha abierta en la agenda.
- `components/modules/ficha.tsx`: el selector de mascotas muestra `nombre · tutor`
  (con dos pacientes homónimos no había forma de elegir); el de profesionales
  filtra por `activo` y no imprime `"· null"` cuando no hay matrícula; se agregó
  el campo **Fecha de la atención** (admite pasado) y el selector de turno se
  filtra por mascota y por esa fecha, en vez de ofrecer los turnos del día de
  cualquier paciente.
- `components/modules/guarderia.tsx`: **queda sin montar** (no lo importaba nadie).
  Se le agregó una nota de encabezado que explica que escribe en el store en
  memoria y que no debe montarse tal cual.
- `lib/api.ts`: `horaEntrada`/`horaSalida` en `ReservaGuarderia*Input`,
  `TipoEstadiaGuarderia` como tipo propio, y `matricula` de los resumenes de
  turno y consulta pasó a `string | null` (ya era nullable en el backend desde
  FASE 1 y el tipo del frontend mentía).

### Verificación end-to-end
Backend levantado contra MySQL real, por HTTP y confirmado en
`veterinaria_db`: turno retroactivo del `2026-02-11` guardado con
`fecha_hora_inicio` correcta y derivado +45/+60; turno solapado en la ventana de
desinfección rejected con 409 **aunque la fecha sea pasada**; `GET /turnos?fecha=`
devolvió 1 turno y `GET /turnos` (hoy) 0; reserva `COMPLETA_24H` 08:00→08:00
persistida con aforo 1/10; `DIURNA` 18:00→09:00 rechazada con 400 y horario a
medias rechazado con 400. Datos de prueba borrados (8 tablas en 0 filas) y puerto
8080 liberado.

## Decisiones de diseño (TASK-008)
1. **La UI no replica las reglas de horario.** El modal deja enviar cualquier par
   y muestra el 400 del backend. Duplicar la condición en el frontend crearía
   dos verdades, y ya se rompió una vez: la versión ingenua rechazaba la
   estadía de 24 horas.
2. **La estadística del semáforo viene del servidor.** No hay conteo local de
   cupos: el backend es la única fuente del aforo (regla 2.1).
3. **El filtro por fecha se hace en el servidor.** La ficha pide
   `?fecha=<fecha de atención>` y acota por mascota en el cliente, porque el
   endpoint por profesional no filtra por paciente.

## Riesgos abiertos (TASK-008)
1. **El `CargarClienteModal` del Mostrador sigue llamando a `addOwnerWithDog`**
   (store en memoria). Es el camino que queda con la falla que se reportó en
   FASE 1: el alta de cliente desde el mostrador no persiste. Corregirlo es
   reescribir ese modal contra `lib/api.ts`, fuera del alcance declarado de esta
   fase.
2. **No hay Flyway ni Liquibase.** `spring.jpa.hibernate.ddl-auto=update`
   agregó `hora_entrada` y `hora_salida` solas. Sin versionado del esquema, una
   base existente y una recién creada pueden divergir sin que nada lo advierta.
3. **Los tests corren contra la base de desarrollo** (`veterinaria_db`), no contra
   una base de test. `RollbackTransaccionalTest` hace `DELETE` sobre 10 tablas:
   si alguien corre los tests con datos reales cargados, los borra.
4. **`turno_clinico.fecha_hora_inicio` es `datetime(6)` sin timezone** y el
   cliente MySQL muestra los valores corridos respecto de lo que devuelve la API
   (la JVM corre en UTC-3 y `serverTimezone=UTC`). Verificado end-to-end que la
   ida y la vuelta por la API son consistentes, así que no afecta la aplicación,
   pero conviene revisar el mapeo antes de que haya reportes o cortes de caja
   que comparen contra la base cruda.

## Registro de Cambios Técnicos (TASK-006)

### Backend — circuito de egreso
- `EgresoValidacionResponseDTO`: veredicto consolidado (`puedeRetirar`, `motivoBloqueo`,
  `tutorAutorizado`, `tieneDeuda`, `saldoDeuda`, `alertaConducta`, `conducta`,
  `conductaUltimaConsulta`, `fechaUltimaConsulta`, `mensaje`) + `MotivoBloqueo` enum
  y `etiquetaMotivo()` expuesto con `@JsonProperty` (Jackson no reconoce getters
  fuera de los componentes de un record).
- `EgresoService` / `EgresoServiceImpl`: `@Transactional(readOnly=true)`, delega la
  morosidad en `ComandaCobroService.consultarDeuda` para que el semáforo y la caja
  no puedan mostrar saldos distintos. Traduce conducta con `EntityMapper.conductaToApi`
  en vez de duplicar el `switch`.
- `EgresoController`: `GET /api/v1/egreso/validar?mascotaId&tutorDni` y
  `GET /api/v1/egreso/validar/tutor?mascotaId&tutorId`.
- `ResourceNotFoundException.tutorPorDni(...)`: factory nuevo.
- `GlobalExceptionHandler`: handler para `MissingServletRequestParameterException`
  (400 con el nombre del parámetro). Antes un query param obligatorio omitido
  devolvía 500.

### Frontend — capa de conexión
- `lib/api.ts`: cliente HTTP tipado contra `http://localhost:8080/api/v1`
  (configurable con `NEXT_PUBLIC_API_URL`). `ApiError` con status y predicados
  `esValidacion` / `esNoEncontrado` / `esConflicto` / `esServidor`, y
  `ApiConnectionError` separado del 500 para distinguir "backend apagado" de
  "backend roto". Tipos alineados con los DTOs exactos del backend.
- `lib/use-api.ts`: hook `useApi` con `{datos, cargando, error, recargar, ejecutar}`
  y guarda de montaje para evitar que una respuesta vieja gane la carrera.
- `lib/vista.tsx`: `Cargando`, `ErrorBox`, `Vacio`, `Recurso`, `Semforo`, `money`.
  Área táctil mínima 44px (regla 3.4).

### Frontend — vistas operativas
- `components/modules/padron.tsx`: tutores y mascotas; switch "Autorizado a retirar"
  por vínculo (regla 2.4).
- `components/modules/agenda.tsx`: turnos, reservas y semáforo de aforo con barra
  de ocupación. El tope lo expone el backend, no está hardcodeado.
- `components/modules/ficha.tsx`: ficha médica sin inputs de precio ni medio de
  pago; muestra el `comandaId` emitido y el historial con badges de conducta.
- `components/modules/caja.tsx`: comandas por tutor, alerta roja "TUTOR MOROSO" y
  modal de cobro mixto (varios medios en una operación).
- `components/modules/egreso.tsx`: módulo de egreso con semáforo + `ModalEgreso`
  de checkout rápido.
- `components/sidebar.tsx`: navegación mobile-first. En `<md` desaparece la lateral
  y aparece barra inferior con los 4 módulos de uso frecuente + cajón "Más";
  en `>=md` vuelve la lateral fija (regla 3.2).
- `app/page.tsx`: shell actualizado; los módulos no conectados (Balance,
  Historial demo) quedan montados con aviso explícito de que no leen la base.

### Decisiones de diseño
- **El bloqueo es una respuesta, no un error.** El endpoint de egreso devuelve 200
  con `puedeRetirar=false`; el frontend pinta el semáforo sin atrapar excepción.
- **Precedencia de motivos: identidad antes que dinero.** Si no está autorizado y
  además debe, manda `TUTOR_NO_AUTORIZADO`, pero `tieneDeuda`/`saldoDeuda` se
  reportan igual: corregir la autorización sola no destraba el egreso y el caso
  volvería al mostrador dos veces.
- **Cada regla se evalúa siempre**, sin short-circuit, por el mismo motivo.
- **La conducta nunca bloquea.** `REACTIVO`/`AGRESIVO` generan aviso de
  precaución, no retención: es información para quien recibe.
- **Solo cuenta la ficha más reciente.** Una ficha vieja con alerta no marca al
  animal para siempre.
- **DNI tolerante a espacios**, porque viene de un lector o de un pegado manual.

### Tests
- `EgresoSeguroTest` (18): retiro no autorizado (sin vínculo y cotutor), morosidad
  (total, parcial, pagada, no mezclada entre tutores), precedencia, alertas de
  conducta (base y ficha), ficha vieja vs reciente, egreso limpio, 404/400,
  espaciado del DNI, equivalencia de saldo egreso↔caja, y el encadenamiento real
  ficha→comanda→bloqueo por deuda.
- Total backend: **89 tests, 0 fallos**.

### Harness
- `test-harness.ps1` pasó de 2 a 3 pasos: ahora corre `tsc --noEmit` antes del
  build. `next build` reporta `Skipping validation of types` en este proyecto, así
  que la barrera anterior no detectaba errores de TypeScript.
- Estado: `tsc` limpio, build OK, 89 tests backend en verde.

## Registro de Cambios Técnicos (TASK-007 / FASE 1)

### Backend — entidad `Profesional` completa
- `Profesional`: agrega `dni` (varchar 20, UNIQUE) y `telefono` (varchar 30). Ya
  tenía `id, nombre, apellido, matricula, activo`.
- `ProfesionalRequestDTO` / `ProfesionalResponseDTO`: los mismos dos campos.
  Solo `nombre` y `apellido` son obligatorios: un auxiliar recién incorporado entra
  sin matrícula, y exigirla dejaría fuera al que todavía no la tramitó. `dni` y
  `matricula` se rechazan cuando ya pertenecen a otro profesional.
- `ProfesionalServiceImpl`: **normaliza** `dni` y `matricula` (trim + mayúsculas,
  y espacios internos colapsados en la matrícula) antes de comparar y de guardar.
  Sin esto, `"MAT-01"`, `"mat-01"` y `" MAT-01 "` pasaban los tres el chequeo de
  duplicados y quedaban tres profesionales con la misma matrícula, que es
  justamente lo que la matrícula tiene que impedir. El teléfono se guarda sin
  mayúsculas: no tiene letras que normalizar y el `+` se lee mejor intacto.
  Mensajes de error por campo ("El DNI X ya esta registrada") para que el
  operador sepa cuál corregir.
- `ProfesionalRepository`: `findByDni` devuelve `List` y no `Optional`. La columna
  es nullable y MySQL admite varios `NULL` en una `UNIQUE`, así que "no hay
  ninguno" y "hay dos" no son el mismo caso.
- `EntityMapper`: `toResponse(Profesional)` y `toProfesionalResponseList`. Se
  delega el mapeo como en el resto de entidades, en vez de duplicarlo en el
  servicio.
- `ProfesionalController`: **verificado, sin cambios de contrato**. Ya exponía
  `GET /api/v1/profesionales` y `POST /api/v1/profesionales` (201, `@Valid`).
  Lo que faltaba era cobertura, no endpoint.
- Esquema: `SHOW COLUMNS FROM profesional` confirma `dni varchar(20) UNI` y
  `telefono varchar(30)`, agregados por `ddl-auto=update`.

### Backend — pruebas nuevas (15)
- `ProfesionalControllerTest` (10): alta con los seis campos **releyendo la fila
  por repositorio** (un 201 con el JSON correcto no prueba persistencia: la
  respuesta se arma de la entidad en memoria); normalización de DNI y matrícula;
  DNI repetido con distinta grafía también rechazado; DNI `NULL` no choca consigo
  mismo; faltantes y campos sobredimensionados devuelven 400 con el detalle por
  campo; `GET` devuelve 200 con el alta visible.
- `MascotaPersistenciaTest` (5): las **dos** rutas de alta desde la UI, con
  aserción sobre `mascotaRepository` y `tutorMascotaRepository` — no sobre el
  JSON de respuesta. Ruta 1 (`POST /mascotas` con el tutor anidado) verifica la
  tupla con `autorizado_retiro=1`; ruta 2 (mascota suelta y después
  `POST /tutores/{id}/mascotas/{id}`) verifica `autorizado_retiro=0`. Además:
  dos tutores sobre la misma mascota con flags distintos, tutor inexistente no
  deja vínculo huérfano, y especie en minúscula devuelve 400.

### Frontend — el alta de mascota que faltaba
- `components/modules/alta-mascota.tsx` (nuevo): formulario contra la API real.
  Nombre, especie, raza, sexo, fecha de nacimiento, peso, conducta, chip y
  observaciones, más el tutor y su flag de retiro.
  Elige entre las dos rutas del backend según haya tutor o no: con tutor, un solo
  `POST /mascotas` con el tutor anidado (una transacción, no puede quedar la
  mascota sin su vínculo); sin tutor, `POST /mascotas` pelado y el vínculo se
  agrega después desde el modal del tutor — el caso real de un animal encontrado
  sin dueño presente.
- `components/modules/padron.tsx`: monta el alta, unifica los refrescos en
  `refrescar(tutorId?)` (recarga ambos listados y vuelve a pedir el tutor
  abierto, que si no mostraría la copia vieja del estado), y el botón "Nueva
  mascota" hace scroll al formulario. El `<select>` de tutores se alimenta del
  listado real, así que sin tutores cargados el formulario lo dice y ofrece
  guardar igual en vez de fallar.
- **El `lib/store.tsx` en memoria no se tocó para el alta de mascotas.** Ese es el
  punto de la corrección: `Mostrador` → `CargarClienteModal` →
  `addOwnerWithDog` seguía escribiendo solo en memoria. El formulario nuevo no pasa
  por el store, así que lo que se ve es lo que quedó en MySQL.

### Frontend — módulo de profesionales
- `components/modules/profesionales.tsx` (nuevo): panel con el listado leído de
  `GET /api/v1/profesionales` y botón "Cargar veterinario" que abre el modal
  `ModalCargarProfesional` con los cinco campos pedidos. Se relee de la API tras
  cada alta en vez de agregar al estado local, para que no haya una copia que
  pueda divergir si el alta falló a medias.
- El modal se monta **solo cuando está abierto** para que el estado del formulario
  muera al cerrar: si viviera siempre montado, un DNI rechazado quedaría escrito y
  el operador tendría que limpiarlo a mano para reintentar.
- Montado en la pestaña Mostrador (`app/page.tsx`), que es donde se da de alta a
  quien atiende. El `AvisoDemo` se ajustó: ya no dice que el Mostrador entero es
  demo.

### Frontend — contrato y navegación
- `lib/api.ts`: `Profesional` tipa `dni`/`matricula`/`telefono` como
  `string | null` (la matrícula es alfanumérica, no `number`), y `crear` toma un
  `ProfesionalInput` en vez de un objeto inline. Nuevo tipo `EspecieMascota` como
  unión de los seis valores del enum del backend, en `MascotaInput.especie` y en
  `Mascota.especie`.
- Ese cambio de tipos es una corrección, no una restricción de estilo: con
  `especie: string` un `"canino"` en minúscula pasaba la compilación y devolvía un
  400 cuyo mensaje no dice nada útil. Ahora el error aparece en `tsc`.
- `components/sidebar.tsx`: la pestaña `padron` suma el rol `"veterinario"`. Era
  el hallazgo 7 del diagnóstico: el veterinario no tenía forma de resolver de qué
  paciente es cada ficha.

### Verificación end-to-end contra MySQL
Backend levantado en 8080 y recorrido por HTTP, con confirmación directa en
`veterinaria_db` por cliente MySQL:

| Llamada | Status | Fila comprobada |
|---|---|---|
| `POST /profesionales` | 201 | `profesional` #300 con los 6 campos |
| `POST /profesionales` DNI repetido | 400 | mensaje "El DNI 30900881 ya esta registrada" |
| `GET /profesionales` | 200 | el alta aparece en el listado |
| `POST /tutores` | 201 | `tutor` #439 |
| `POST /mascotas` con tutor anidado | 201 | `mascota` #2553 + `tutor_mascota` (439, 2553, flag **1**) |
| `POST /mascotas` sola | 201 | `mascota` #2554, `tutor_mascota` vacío |
| `POST /tutores/439/mascotas/2554` | 201 | `tutor_mascota` (439, 2554, flag **0**) |
| `POST /mascotas` especie `canino` | 400 | sin fila en `mascota` |
| `GET /mascotas` | 200 | 2 filas, ambas presentes |

Datos de prueba eliminados al terminar; la base quedó en 0 filas en las cuatro
tablas. Backend detenido y puerto 8080 libre.

### Decisiones de diseño
- **Una sola ruta de verdad, no dos caminos.** El formulario elige entre las dos
  rutas del backend según el contexto, pero no hay una segunda implementación del
  alta. Duplicar el alta garantiza que en algún momento divergen y queda un camino
  que guarda en un lado y no en el otro.
- **El flag de retiro se elige explícitamente en el alta.** El formulario arranca
  con "Autorizado a retirar" apagado cuando el vínculo es de cotutor, y el valor
  viaja en la tupla (regla 2.4). Nunca se deja que el backend lo asuma.
- **El DNI y la matrícula no son obligatorios, pero sí únicos.** La regla de
  negocio que importa es "un profesional por matrícula". Exigir los tres campos
  en el alta impediría cargar a quien todavía no tramitó el título, que es una
  situación real en un consultorio.
- **La normalización va en el servicio, no en la base.** MySQL no compara
  mayúsculas con la collation de la tabla y no colapsa espacios, así que
  normalizar en SQL obligaría a colaciones por columna que este proyecto no usa.
  Normalizar en Java hace que el valor guardado y el comparado sean el mismo.
- **`findByDni` devuelve lista.** Con una columna nullable y `UNIQUE`, varios
  `NULL` son legales; un `Optional` obligaría al llamador a tratar "no hay" y
  "hay dos" como el mismo caso.

### Nota sobre el harness
`test-harness.ps1` corrió los tres pasos en verde. El conteo de tests subió de
**89 a 104** (10 de `ProfesionalControllerTest` + 5 de `MascotaPersistenciaTest`).
Los warnings `HHH000247 Duplicate entry '2027-03-15' for key
'uk_control_aforo_fecha'` que aparecen en la salida son **esperados**: son el
mecanismo de la carrera de `AforoConcurrenteTest`, que gana uno de los 15 hilos por
la constraint `UNIQUE`. No son fallos.

## Riesgos Conocidos (sin resolver)
- `@Setter` global en `ComandaCobro` expone `setSaldoPendiente`/`setEstado`, pese a
  que el comentario de la entidad advierte que no debería haber setter público de
  saldo. No hay ruta HTTP que los use, pero la puerta queda abierta.
- La seña de guardería (`ReservaGuarderia.sena`) sigue siendo informativa: no
  genera comanda ni imputa pago (regla 2.5 la contempla, TASK-004 no la conectó).
- `Balance` y el `Historial` demo (`components/modules/balance.tsx`,
  `components/modules/fichas.tsx`) siguen con datos en memoria. El `Mostrador`
  conserva su resumen de jornada en `lib/store.tsx`: TASK-007 lo conectó con el alta de
  mascotas y el padrón de profesionales, no con el tablero de ocupación.
- El modal `CargarClienteModal` del Mostrador sigue llamando a `addOwnerWithDog`
  (store en memoria). Es un camino de alta de mascota que **no** persiste, y queda
  como trampa conocida: la forma de hacerlo bien ya existe en el Padrón, pero
  retirar el botón del store no se hizo en esta fase.
- El `campo` `especie` de `MascotaInput` ahora es una unión de tipos, pero nada
  impide que un cliente HTTP externo mande minúsculas y reciba 400. La validación
  del enum la hace Jackson, no un `@Pattern` con mensaje propio.
- No hay `Flyway` ni `Liquibase`: el esquema depende de `ddl-auto=update`. Para
  esta fase alcanza (las columnas nuevas se aggregaron solas y se verificaron con
  `SHOW COLUMNS`), pero cualquier entorno que no tolere que Hibernate mute el
  esquema necesita migraciones versionadas.
- No hay autenticación: el selector de rol es una pantalla de prueba, no control
  de acceso. `/api/v1/caja/cierre` ya está disponible pero no tiene vista.
- Los IDs del backend son `number` en JS: la base es autoincremental, así que es
  seguro en la práctica, pero no para datos cargados masivamente.
