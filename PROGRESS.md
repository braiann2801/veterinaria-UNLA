# PROGRESS.md

## Estado Actual
- Tarea en curso: [Ninguna] — **TASK-006 FINALIZADA, MVP 1 completo**
- Agente asignado: [Implementador/Revisor/Harness] — OK
- Bloqueantes: [Ninguno]

## Tareas Completadas
- [x] TASK-001: Setup inicial repositorio y frontend base v0.
- [x] TASK-002: Base de datos (MySQL) y entidades core con migraciones lógicas.
- [x] TASK-003: CRUDs básicos (Tutor/Mascota/Vínculo N:M) + validaciones.
- [x] TASK-004: Turnos clínicos (bloques 45+15), guardería (aforo 10), tests.
- [x] TASK-004-HOTFIX: Ajustes de esquema y tests.
- [x] TASK-005: Ficha Médica (alertas conducta), Comanda de Cobro (desacouple clínico-contable) y Caja Multipago.
- [x] TASK-006: Circuito de Egreso Seguro y conexión end-to-end del frontend. **MVP 1 cerrado.**

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

## Riesgos Conocidos (sin resolver)
- `@Setter` global en `ComandaCobro` expone `setSaldoPendiente`/`setEstado`, pese a
  que el comentario de la entidad advierte que no debería haber setter público de
  saldo. No hay ruta HTTP que los use, pero la puerta queda abierta.
- La seña de guardería (`ReservaGuarderia.sena`) sigue siendo informativa: no
  genera comanda ni imputa pago (regla 2.5 la contempla, TASK-004 no la conectó).
- `Balance` y el `Historial` demo (`components/modules/balance.tsx`,
  `components/modules/fichas.tsx`) siguen con datos en memoria.
- No hay autenticación: el selector de rol es una pantalla de prueba, no control
  de acceso. `/api/v1/caja/cierre` ya está disponible pero no tiene vista.
- Los IDs del backend son `number` en JS: la base es autoincremental, así que es
  seguro en la práctica, pero no para datos cargados masivamente.