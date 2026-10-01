# PROGRESS.md

## Estado Actual
- Tarea en curso: Ninguna
- Agente asignado: Ninguno
- Bloqueantes: Ninguno

## Tareas Completadas
- [x] 2026-09-29 - Setup inicial del repositorio y frontend base v0 en Next.js.
- [x] 2026-09-29 - Definición y aprobación de AGENTS.md con protocolo de gobernanza.
- [x] 2026-09-30 - TASK-001: Modelado relacional N:M de Tutor, Mascota y TutorMascota (entidades, clave compuesta y repositorios). Harness en verde.
- [x] 2026-09-30 - TASK-002: Capa DTO + `EntityMapper` que rompe el ciclo de serialización Tutor-Mascota. 10 tests en verde.
- [x] 2026-09-30 - TASK-003: Servicios transaccionales + Controllers REST `/api/v1/tutores` y `/api/v1/mascotas`, `GlobalExceptionHandler` y CORS. 17 tests en verde.
- [x] 2026-09-30 - TASK-004: Turnos Clínicos (45+15) y Guardería con aforo estricto de 10. 38 tests en verde.
- [x] 2026-09-30 - TASK-004-HOTFIX: Blindaje de concurrencia en aforo de guardería con PESSIMISTIC_WRITE (semaforo por fecha). 42 tests en verde.

## Registro de Cambios Técnicos
- Commit `b5b0633`: Inicialización del proyecto con archivos v0 y configuración base.
- `backend/.../entity/Tutor.java`, `Mascota.java`, `TutorMascota.java`, `TutorMascotaId.java`: modelado N:M con `@MapsId`, `FetchType.LAZY` en ambos lados y flag `autorizado_retiro` no nulo (regla 2.4). Sin DTOs ni referencias bidireccionales serializadas.
- `Mascota.pesoKg`: cambió de `Double` a `BigDecimal`. Hibernate lanza `IllegalArgumentException: scale has no meaning for SQL floating point types` en tipos flotantes; el harness quedó en rojo hasta aplicar el cambio.
- `backend/.../repository/*Repository.java`: interfaces `JpaRepository` con consultas derivadas por dni, chip, tutorId y mascotaId (incluido `findByMascotaIdAndAutorizadoRetiroTrue` para validar retiro por cotutor).
- `application.properties`: sin cambios; se mantiene `spring.jpa.hibernate.ddl-auto=update` y MySQL generó `tutor`, `mascota` y `tutor_mascota` con sus FKs.
- `backend/.../dto/`: 8 records (`TutorRequestDTO`, `TutorResponseDTO`, `TutorResumenDTO`, `MascotaRequestDTO`, `MascotaResponseDTO`, `MascotaResumenDTO`, `TutorMascotaResponseDTO`). El corte de ciclo es por diseño: las listas anidadas usan solo DTOs resumen, que no tienen referencia de vuelta.
- `TutorRequestDTO` / `MascotaRequestDTO`: validación Jakarta Bean Validation. `autorizadoRetiro` es `@NotNull` en ambos DTOs de vinculo para que el flag sea explícito (regla 2.4).
- `backend/.../mapper/EntityMapper.java`: mapeo entidad <-> DTO. Colecciones nulas normalizadas a `List.of()` para no propagar nulls al frontend. `applyRequest` proyecta datos pero no toca la relación N:M (eso es del servicio).
- `EntityMapper`: los métodos de lista se renombraron a `toTutorResponseList` / `toMascotaResponseList` / `toVinculoResponseList`. Sobrecargar `toResponseList(List<T>)` provoca *name clash* por erasure.
- `application.properties`: se agregó `spring.jpa.open-in-view=false` (silencia el warning de sesión abierta y fuerza que las relaciones se resuelvan dentro de la transacción del servicio).
- `TutorMascotaDtoMappingTest` (nuevo): construye un grafo con ciclo real Tutor -> vinculo -> Mascota -> vinculo -> Tutor y verifica que Jackson falla con la entidad cruda pero serializa bien el DTO.
- Stack del proyecto: Spring Boot 4.1.1 usa Jackson 3, paquete `tools.jackson.databind`. No existe `com.fasterxml.jackson.databind`. Además `commons-lang3` no está en el classpath.
- `@Valid` sobre el campo `List<...>` está deprecado en Hibernate Validator 9 (warning HV000271). Se aplica solo sobre el argumento de tipo `List<@Valid VinculoDTO>`.

### TASK-003 - Servicios y Controllers REST

**Endpoints implementados**
- `GET /api/v1/tutores` (200), `GET /api/v1/tutores/{id}` (200/404), `POST /api/v1/tutores` (201/400), `PUT /api/v1/tutores/{id}` (200/400/404), `DELETE /api/v1/tutores/{id}` (204/404).
- `GET /api/v1/mascotas` (200), `GET /api/v1/mascotas/{id}` (200/404), `POST /api/v1/mascotas` (201/400), `PUT /api/v1/mascotas/{id}` (200/400/404), `DELETE /api/v1/mascotas/{id}` (204/404).
- `POST /api/v1/tutores/{tutorId}/mascotas/{mascotaId}` (201/400/404), `DELETE /api/v1/tutores/{tutorId}/mascotas/{mascotaId}` (204/404).
- Extras de apoyo: `GET /api/v1/tutores/{id}/mascotas`, `GET /api/v1/mascotas/{id}/tutores`.

**Archivos nuevos**
- `service/TutorService.java`, `service/MascotaService.java` (interfaces) + `service/impl/*ServiceImpl.java` con `@Transactional(readOnly = true)` a nivel de clase y `@Transactional` por método en escritura.
- `controller/TutorController.java`, `controller/MascotaController.java`, `controller/GlobalExceptionHandler.java` (`@RestControllerAdvice`).
- `exception/BadRequestException.java`, `exception/ResourceNotFoundException.java`, `exception/ApiErrorResponse.java`.
- `config/CorsConfig.java`: orígenes `localhost:3000`, `127.0.0.1:3000` y `https://*.vercel.app`.
- `dto/VinculoRequestDTO.java`: flag `autorizadoRetiro` por body o query param. Si llega con valores distintos en ambos, 400. Si no llega ninguno, 400 (regla 2.4 exige que sea explícito).
- `TutorControllerTest.java` (nuevo, 7 tests): ciclo completo de vinculación, 404, 400 de validación, DNI/chip duplicados, N:M real con dos tutores sobre una mascota, y CORS con `Origin: http://localhost:3000`.

**Bug crítico de persistencia (resuelto) — `NonUniqueObjectException` / `DuplicateKeyException`**
- Síntoma: `PUT /api/v1/tutores/{id}` devolvía 500 al re-vincular una mascota ya vinculada.
- Causa raíz 1: `orphanRemoval = true` en **ambos** lados de la relación N:M. La tupla `TutorMascota` tiene dos dueños (`Tutor.vinculos` y `Mascota.vinculos`) y el borrado en cascada desde ambos provocaba conflicto. **Solución:** quitar `orphanRemoval` de ambas colecciones; las bajas las resuelve el servicio con `TutorMascotaRepository#delete` explícito.
- Causa raíz 2: el constructor `TutorMascota(tutor, mascota, autorizadoRetiro)` construía `TutorMascotaId` a mano. Con `@MapsId` el id lo deriva Hibernate de `tutor.id`/`mascota.id`; asignarlo manualmente generaba una tupla paralela que el contexto de persistencia rechazaba. **Solución:** no asignar el id en el constructor.
- Causa raíz 3: llamar `repository.save()` sobre una entidad ya gestionada dispara `merge()`. Con `CascadeType.ALL` + id compuesto asignado, merge clona la tupla y choca con la instancia viva del contexto. **Solución:** no llamar `save()` en `actualizar`/`vincularMascota`/`desvincularMascota`; el dirty checking persiste al cerrar la transacción.
- Nota: los tests corren contra MySQL real, así que estos bugs eran invisibles con `@DataJpaTest` + H2.

**Charset: enum `conducta` corrupto (resuelto)**
- `Mascota.Conducta.GRUÑE` se persistía como `'GRU?E'` porque Hibernate mapea enums STRING a un `enum` de MySQL y la conexión no declaraba charset.
- **Solución:** el enum usa `GRUNE` en ASCII; `EntityMapper.conductaToApi()` traduce a `"gruñe"` en la respuesta. Se agregó `characterEncoding=UTF-8` a la URL JDBC.
- Se corrigió la columna existente: `ALTER TABLE mascota MODIFY COLUMN conducta VARCHAR(20)` (MySQL no puede cambiar el tipo de un enum con valores distintos; se recreó la columna).

**Verificaciones del Revisor**
- Ningún controller importa repositorios ni el paquete `entity`: verificado con grep. Todos consumen Services.
- `@AutoConfigureMockMvc` en Spring Boot 4.1 está en `org.springframework.boot.webmvc.test.autoconfigure`, no en `org.springframework.boot.test.autoconfigure.web.servlet`.
- Surefire solo corre clases `*Test`/`*Tests`/`Test*`. El test se llamó inicialmente `TutorControllerIT` y no se ejecutaba; renombrado a `TutorControllerTest`.
- `GlobalExceptionHandler` loguea la excepción 500 al servidor y devuelve un mensaje genérico al cliente (no filtra traza).

**Pendiente / no resuelto**
- El warning `HHH000511` (MySQLDialect 5.5.5 no soportada) **sigue apareciendo**. Se quitó `spring.jpa.database-platform` como se pidió, pero Hibernate autodetecta y vuelve a reportar la versión. Se intentó forzar `MySQL8Dialect` y **esa clase no existe en Hibernate 7**, lo que rompió el arranque. El warning es cosmético; queda documentado en `application.properties`.
- Base de datos: se ejecutaron `DELETE FROM tutor_mascota/tutor/mascota` durante la depuración. La base está vacía salvo datos previos a TASK-003.

### TASK-004 - Turnos Clínicos y Guardería con Aforo Estricto

**Reglas implementadas**
- **2.2 Turnos:** bloques de 45 min de atención + 15 de desinfección. `TurnoClinico.recalcularVentana()` deriva `fechaHoraFin` (+45) y `fechaFinBloque` (+60) desde el inicio. El RequestDTO **solo acepta `fechaHoraInicio`**: aceptarlos por API permitiría saltarse la ventana de desinfección.
- **2.1 Aforo:** `ReservaGuarderia.AFORO_MAXIMO = 10`. Cuentan solo reservas `RESERVADO` o `INGRESADO`; `FINALIZADO` y `CANCELADO` liberan cupo.

**Entidades nuevas**
- `entity/TurnoClinico.java` + `entity/Profesional.java` + `enum EstadoTurno` (PENDIENTE/ATENDIDO/CANCELADO).
- `entity/ReservaGuarderia.java` + `enum TipoEstadia` (DIURNA/NOCTURNA/COMPLETA_24H) y `enum EstadoReserva` (RESERVADO/INGRESADO/FINALIZADO/CANCELADO), con `ocupaCupo()`.
- `entity/ControlAforoDiario.java` (único por fecha, semáforo para serializar reservas concurrentes).
- `Profesional` existe como entidad propia (no embebida) porque el solapamiento se valida por profesional con consulta JPQL.

**Repositorios**
- `TurnoClinicoRepository.findSolapamientos(profesionalId, inicio, finBloque)`: JPQL. Detecta solapamiento del bloque completo (`fecha_hora_inicio < finBloque AND inicio < fecha_fin_bloque`) y excluye CANCELADOS. Es lo que hace que un turno al minuto 45 se rechace: la consulta ya terminó pero el consultorio sigue en desinfección.
- `ReservaGuarderiaRepository.contarCuposOcupados(fecha)`: JPQL `COUNT` filtrando por los dos estados que ocupan. Única fuente de verdad del aforo.
- `ControlAforoDiarioRepository.bloquearPorFecha(fecha)`: native query `SELECT ... FOR UPDATE` (sin `OF` alias) para compatibilidad con MariaDB 10.4. El lock es sobre la fila del día, no sobre la tabla.

**Decisión de concurrencia** — resuelta en TASK-004-HOTFIX (ver abajo). Era un check-then-act: leer el conteo y después insertar, dos peticiones simultáneas lo completan las dos.

**Excepciones y HTTP**
- `exception/BusinessConflictException.java` (nueva) → HTTP 409 vía `GlobalExceptionHandler.handleConflict`.
- `exception/SolapamientoTurnoMessages.java`: centraliza el texto del conflicto con las 5 marcas de tiempo involucradas.
- `BusinessConflictException.aforoLleno(fecha, ocupados, maximo)`: mensaje "No hay cupo disponible en guarderia para el {fecha}: aforo completo ({n} de 10 perros). No se pueden admitir mas reservas ese dia."

**Endpoints nuevos**
- `GET /api/v1/turnos?fecha=`, `GET /api/v1/turnos/profesional/{id}?fecha=`, `GET /api/v1/turnos/disponibilidad?profesionalId=&inicio=`, `POST /api/v1/turnos` (201/409), `PUT /api/v1/turnos/{id}` (200/409), `PUT /api/v1/turnos/{id}/cancelar`, `DELETE /api/v1/turnos/{id}` (204).
- `GET /api/v1/guarderia?fecha=`, `GET /api/v1/guarderia/aforo?fecha=`, `POST /api/v1/guarderia` (201/409), `PUT /api/v1/guarderia/{id}`, `PUT /api/v1/guarderia/{id}/cancelar`, `DELETE /api/v1/guarderia/{id}` (204).
- `GET/POST /api/v1/profesionales`.

**Tests nuevos (21)**
- `AforoGuarderiaTest` (6): **prueba unitaria obligatoria del 409**. 10 reservas entran, la 11 recibe `BusinessConflictException` con "aforo completo" y "10 de 10"; comprueba que la rechazada no se persistió; cancelar libera cupo; el aforo es por día; no se duplica mascota el mismo día.
- `VentanaDesinfeccionTest` (9): **prueba unitaria obligatoria de la ventana de desinfección**. Rechaza minuto a minuto del 45 al 59; permite el 60; rechaza solapamiento total; distintos profesionales no chocan; cancelar libera; reprogramar a hora ocupada da 409; `disponible()` coincide con el POST.
- `TurnosYGuarderiaControllerTest` (6): verifica que el 409 y el 400 llegan por HTTP con mensaje descriptivo.

**Correcciones durante la tarea**
- `permiteAlTerminarLaDesinfeccion` fallaba con `expected 10:45 but was 11:00`: el assertion mío estaba mal (turno a las 10:00 + 60 = 11:00), no el servicio. Se corrigió el test.
- `ProfesionalController` llegaba al repositorio directo. El Revisor lo marcó como violación del criterio "controller → service → repository". **Corregido**: se creó `ProfesionalService`/`ProfesionalServiceImpl`. Verificado por grep: cero referencias a `Repository` o `.entity.` en todo el paquete `controller`.

**Pendientes**
- El desacople clínico-contable (AGENTS.md 2.3) no se tocó: `ReservaGuarderia.sena` es solo un campo informativo, no hay comanda de cobro ni medios de pago. Eso corresponde al módulo de caja/TASK posterior.

### TASK-004-HOTFIX - Blindaje de Concurrencia del Aforo (PESSIMISTIC_WRITE)

**El problema**
`ReservaGuarderiaServiceImpl.crear` hacía un check-then-act clásico: `COUNT` de ocupados, comparar contra 10, después `INSERT`. Dos peticiones que llegan juntas ejecutan las dos entre el COUNT y el INSERT y las dos ven 9. El día queda con 11.

**La solución: semáforo por día**
- `entity/ControlAforoDiario.java` (nueva): `id`, `fecha` (UNIQUE), `cuposOcupados`. Una fila por día contra la que hacer `SELECT ... FOR UPDATE`. Sin una fila por día, MySQL no tiene nada que serializar.
- **`cuposOcupados` NO es la fuente de verdad.** Es una instantánea informativa. La fuente de verdad sigue siendo el `COUNT` sobre `reserva_guarderia`. Si el contador decidiera el cupo, cualquier actualización olvidada (un borrado manual, un estado mal seteado) desalinearía el aforo de forma permanente. Como se recalcula bajo el lock, no puede desviarse.
- `ControlAforoDiarioRepository.bloquearPorFecha(fecha)`: native query `SELECT * FROM control_aforo_diario WHERE fecha = ? FOR UPDATE`.

**Creación atómica de la fila del día**
- `ControlAforoInicializador` (bean aparte): `@Transactional(REQUIRES_NEW)`, inserta la fila y **deja propagar** el `DataIntegrityViolationException` si otra transacción ganó la carrera por la constraint UNIQUE.
  - **Por qué una bean separada:** `REQUIRES_NEW` solo surte efecto si la llamada atraviesa el proxy de Spring. Una llamada interna (`this.metodo()`) lo esquiva por completo y el `@Transactional` sería decorativo.
  - **Por qué la excepción se propaga y no se atrapa adentro:** si se atrapara dentro del método `REQUIRES_NEW`, esa transacción quedaría marcada rollback-only y el commit final lanzaría `UnexpectedRollbackException` en lugar de un error de clave duplicada limpio. La captura en `ControlAforoService`, que ya está en otra transacción, es lo único que mantiene el resultado correcto.
  - **Por qué `REQUIRES_NEW` y no la transacción del llamador:** si la fila se creara dentro de la transacción que agenda la reserva y esta hiciera rollback, el semáforo desaparecería aunque las reservas ya se hubieran contado.
- `ControlAforoService.bloquearDia(fecha)`: `@Transactional(MANDATORY)`. Llama al inicializador, captura la violación de unicidad (es la carrera perdida esperada) y toma el lock.

**Aislamiento READ_COMMITTED — obligatorio, no cosmético**
`ReservaGuarderiaServiceImpl.crear` quedó en `@Transactional(isolation = READ_COMMITTED)`. MariaDB 10.4 corre en REPEATABLE-READ por defecto, donde un SELECT plano lee el snapshot fijado al inicio de la transacción. El `COUNT` posterior al `FOR UPDATE` habría visto el estado viejo y el filtro de aforo se habría evaluado contra información obsoleta: el lock existiría pero no serviría. En READ_COMMITTED cada sentencia ve lo último confirmado, que es lo que necesita el patrón leer-contar-escribir protegido por el lock.

**El chequeo de mascota duplicada se movió dentro del lock**
Estaba antes del semáforo, así que dos peticiones concurrentes de la MISMA mascota podían pasar ambas el `EXISTS`. Ahora corre bajo el lock.

**Timeout de lock → 409**
`CannotAcquireLockException` (agotamiento de `innodb_lock_wait_timeout`) se traduce a 409 vía `BusinessConflictException.semaforoOcupado(fecha)`, con mensaje accionable de reintento. **No quedan locks huérfanos:** en InnoDB los bloqueos de fila se liberan al confirmar o revertir la transacción que los tomó, nunca cuando expira la espera. El que retiene el semáforo es otra transacción viva. El motivo original se loguea al servidor (`log.warn`) sin exponerlo al cliente.

**El pool de Hikari subió a 20** (`spring.datasource.hikari.maximum-pool-size`). El default (10) es menor que la concurrencia del test: con 15 peticiones, 5 hilos esperarían una conexión y el test mediría el pool, no el lock.

**Tests nuevos (4) — `AforoConcurrenteTest`**
15 hilos con `ExecutorService` + `CountDownLatch` de salida sobre el mismo día. **Este test NO lleva `@Transactional`:** con la anotación los 15 hilos comparten la transacción del principal (una sola conexión, un único contexto, ningún commit intermedio), los locks no se tomarían y el test pasaría sin probar nada. Por eso la limpieza es manual en `@AfterEach`.
1. `quinceReservasSimultaneasRespetanElAforo` — exactamente 10 exitos, 5 `BusinessConflictException`, y **`contarCuposOcupados(DIA) == 10` en la base**: lo que importa es lo persistido, no lo que respondieron los hilos.
2. `semaforoQuedaAlineadoConLaRealidad` — el contador desnormalizado coincide con la realidad.
3. `diaSinFilaPreviaSeInicializaBajoCarrera` — un día sin semáforo se inicializa solo y admite sus 10 primeros.
4. `oleadasSuccessivasNoAcumulanCupos` — segunda tanda de 15 perros nuevos: las 15 rebotan y el día sigue en 10.

**Verificación de que el test sirve (control negativo)**
Un test de concurrencia que pasa siempre no prueba nada. Se corrió con el lock desactivado: la base quedó con **11 y 12** reservas activas y el test falló. Con el lock activo, exactamente 10. El blindaje es lo que hace la diferencia.

**Errores encontrados durante la tarea**
- **`FOR UPDATE OF alias` — Hibernate genera SQL que MariaDB no entiende.** La solución pedía `@Lock(PESSIMISTIC_WRITE)` + `@Query` JPQL. Hibernate 7 compone `... FOR UPDATE OF cad1_0`; la cláusula `OF` es sintaxis de Oracle/PostgreSQL y MariaDB responde error 1064. **El `@Query` era el disparador**: quitar solo el `@QueryHints` no cambió nada. Se probó también la variante derivada (`findByFechaForUpdate`), que falló antes de llegar a SQL porque Spring parsea `ForUpdate` como nombre de propiedad. **Resolución:** SQL nativo con el `FOR UPDATE` escrito a mano. El SQL exacto queda a la vista y desaparece la capa de traducción.
- **`REQUIRES_NEW` con self-invocation** (detectado antes de compilar): `ControlAforoService.asegurarFila()` llamándose a sí mismo no pasa por el proxy, así que la propagación era inerte. Separado en `ControlAforoInicializador`.
- **Fallo del test propio, no del código:** `oleadasSuccessivasNoAcumulanCupos` reutilizaba los mismos 15 perros y esperaba 15 rechazos; llegaron 5 porque 10 chocaron antes con la regla "mascota ya tiene reserva activa" (400). Corregido usando 15 perros nuevos: así la única razón posible para rebotar es el aforo.

**Pendiente**
- `UPDATE` de disponibilidad de turno (`@Version` o `SELECT ... FOR UPDATE`) si se detectan doble-reservas en producción. No bloquea la agenda actual.