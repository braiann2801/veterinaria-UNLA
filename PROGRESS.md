# PROGRESS.md

## Estado Actual
- Tarea en curso: [Ninguna] — TASK-005 FINALIZADA
- Agente asignado: [Implementador/Revisor/Harness] — OK
- Bloqueantes: [Ninguno]

## Tareas Completadas
- [x] TASK-001: Setup inicial repositorio y frontend base v0.
- [x] TASK-002: Base de datos (MySQL) y entidades core con migraciones lógicas.
- [x] TASK-003: CRUDs básicos (Tutor/Mascota/Vínculo N:M) + validaciones.
- [x] TASK-004: Turnos clínicos (bloques 45+15), guardería (aforo 10), tests.
- [x] TASK-004-HOTFIX: Ajustes de esquema y tests.
- [x] TASK-005: Ficha Médica (alertas conducta), Comanda de Cobro (desacople clínico-contable) y Caja Multipago.

## Registro de Cambios Técnicos (TASK-005)
- Entidades: Tarifa, ConsultaMedica, ComandaCobro, Pago (conducta -> alerta derivada; comanda emite PENDIENTE al persistir ficha).
- Repositorios: TarifaRepository, ConsultaMedicaRepository, ComandaCobroRepository.bloquearPorId (SELECT ... FOR UPDATE nativo), PagoRepository (totales por método + contarComandasCobradas).
- DTOs: ConsultaMedicaRequestDTO/ResponseDTO sin campos contables; ComandaCobro*, Pago*, DeudaTutorResponseDTO, CierreCajaResponseDTO (totalPagado derivado + @JsonProperty).
- Servicios/mappers: ConsultaMedicaServiceImpl (idempotencia turno, tutor autorizado a retirar), ComandaCobroServiceImpl, CajaServiceImpl (pagos acumulativos atómicos, cierre con mapa completo por método).
- Controllers: /api/v1/consultas, /api/v1/comandas, /api/v1/caja, deuda en TutorController.
- Tests: desacople/alertas, caja multipago, morosidad, rollback transaccional (tests sin @Transactional para observar atomicidad). 71 tests OK.
- Fix: serialización 	otalPagado, assertions robustas con Jackson.

## Notas
- Desacople clínico-contable aplicado (ficha sin montos; cobro diferido).
- Aforo guardería 10 y ventanas 45+15 intactos.
- Harness .\test-harness.ps1 → verde (frontend build + backend tests).

