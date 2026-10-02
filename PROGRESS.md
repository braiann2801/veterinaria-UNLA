# PROGRESS.md

## Estado Actual
- Tarea en curso: [Ninguna] — **TASK-007 (FASE 1) FINALIZADA**
- Agente asignado: Orquestador -> Implementador -> Revisor/Harness — OK
- Bloqueantes: [Ninguno]
- Harness: `tsc --noEmit` limpio, `next build` OK, **104 tests backend en verde**
  (89 previos + 15 nuevos).

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