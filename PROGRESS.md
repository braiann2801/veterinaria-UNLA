# PROGRESS.md

Memoria operativa del proyecto **veterinaria-UNLA**. Se mantiene corta a
propósito: solo lo que un agente necesita para retomar el trabajo sin repetir
exploración. Los detalles finos de cada fase quedaron en los commits; acá no.

## Estado Actual

- **Fase en curso:** rama `feat/atencion-extras-menu` (detalle en
  `odd/tasks/atencion-extras-menu.md`), apilada sobre
  `feat/agenda-mensual-turno-rapido` (PR #3, abierto): atender turnos desde la
  Agenda, catálogo de extras en Tarifas, detalle de comanda en Caja y barra
  lateral en secciones. Implementada y verificada; pendiente de push/PR.
- **Base Git:** `main` en `e2edc3b` (merge del PR #2, deuda por mascota y
  normalización).
- **Next.js:** `agentRules: false` en `next.config.mjs` para que `next dev` no
  reescriba `AGENTS.md`.
- **`backend/mvnw`:** versionado con permiso de ejecución (`+x`), así
  `./mvnw` corre en macOS/Linux sin `chmod`.
- **Harness:** tsc limpio, Next.js build OK, 271 tests de Spring Boot en verde
  (en macOS contra el contenedor `veterinaria-db-local` del 3307; ver README).

## Hitos completados

- [x] **Setup base** — Next.js (port 3000, webpack) + Spring Boot 3.x (8080) +
      MySQL 8 (3306, `veterinaria_db`), CORS local/vercel.
- [x] **Dominio core** — Tutor-Mascota $N:M$ con `autorizado_retiro`; turnos
      45+15 min; aforo guardería tope 10 con lock pesimista; ficha clínica
      desacoplada de cobro (comanda diferida a mostrador); caja multipago;
      egreso validado por identidad + deuda + conducta.
- [x] **TASK-007 (FASE 1)** — ABM `Profesional`, alta real de mascotas, Padrón.
- [x] **TASK-008 (FASE 2)** — Guardería con horarios, Agenda reactiva por fecha.
- [x] **TASK-009 (FASE 3)** — Caja del día (`/comandas/dia`, `/caja/tutores-del-dia`),
      checkout de egreso filtrable; fix "camino por DNI" y estados de carga en caja.
- [x] **Cierre de Riesgos 6 y 7** — `DataInitializer` siembra tarifas
      idempotente (3 conceptos); tests aislados en `veterinaria_test`.
- [x] **Refactor Mostrador** — monolito de 1105 líneas → orquestador de 150 +
      componentes atómicos en `components/modules/mostrador/`.
- [x] **Balance real** — `GET /api/v1/caja/cierre` (arqueo, saldos, movimientos);
      `DatePicker` reutilizable sobre `react-day-picker` (celdas 44×44).
- [x] **Migración de fechas** — los 8 `<input type="date">` operativos → `DatePicker`
      (con selector mes/año).
- [x] **ABM del Padrón** — editar/eliminar tutores y mascotas; regla del huérfano
      (una mascota no queda sin tutor; con historial bloquea 409 atómico).
- [x] **Alta de tutor unificada** — mismos campos (email/dirección) en Mostrador y Padrón.
- [x] **Reestilo visual** — tema oscuro azul/negro por defecto (primario #2563EB,
      sin `prefers-color-scheme`), `rounded-xl`, navegación activa y aforo azul.
- [x] **Optimización (poda de código muerto)** — backend sin cambios. Frontend:
      se eliminaron 3 módulos legacy montados en el store mock
      (`consultas.tsx`, `cobros.tsx`, `guarderia.tsx`) y el store quedó solo con
      estado de sesión (rol + búsqueda); helpers sin uso de `lib/api.ts`
      (`aTexto`, `aFechaLocal`, `aHoraLocal`, `esFechaHoy`) eliminados.
- [x] **Dockerización** — `docker-compose.yml` (db/backend/frontend, red
      `veterinaria-net`, volumen `mysql_data`); Dockerfiles multi-stage
      (Maven→JRE y pnpm→standalone); `next.config.mjs` con `output: 'standalone'`;
      DB parametrizada por env (`DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD`) con
      defaults locales; secretos en `.env` (gitignored) + `.env.example`
      versionado; `package-lock.json` eliminado (pnpm único lockfile
      autoritativo).
- [x] **Health check raíz + trazabilidad de errores** — `HomeController` responde
      `GET /` (`{status: "UP", ...}`) sin tocar la DB. Decisión revisada: NO se
      agrega `ex.printStackTrace()` porque `log.error(..., ex)` ya vuelca la
      traza completa al log (duplicar iría a stderr).
- [x] **Handler 404 (rutas inexistentes)** — `NoResourceFoundException` se
      atrapa en `GlobalExceptionHandler` y responde **404** con el mismo
      contrato `ApiErrorResponse` que el resto de los errores. Sin este
      handler caía en el catch-all `Exception.class` y devolvía 500.
      Verificado con smoke HTTP real: `GET /api/v1/no-existe` → 404.
- [x] **CORS: PATCH habilitado** — `CorsConfig` ahora permite también el método
      `PATCH` sobre `/api/**`. Se mantiene la whitelist de orígenes
      (localhost:3000, 127.0.0.1:3000, `*.vercel.app`), `allowCredentials`,
      `exposedHeaders("Location")` y `maxAge(3600)`. Alineado a AGENTS.md §1.
- [x] **Guardería por rango y tarifas** — estadía con entrada/salida (fecha y
      hora) en el DateRangePicker de HeroUI; aforo validado cada día del rango;
      monto por horas o por noches con precios del Dueño (pestaña Tarifas); la
      comanda GUARDERIA se emite al reservar; el egreso registra la entrega y
      finaliza la estadía. Detalle: `odd/tasks/guarderia-tarifas-rango.md`.
- [x] **Autenticación y landing** — `/` landing pública (`lib/negocio.ts`),
      `/login`, `/app` protegido. Spring Security + JWT HS256; roles
      DUENIO/VETERINARIO/RECEPCION/GUARDERIA (Guardería = Recepción); Balance,
      Usuarios, cierre y tarifas solo Dueño (403). Dueño sembrado con clave
      default y cambio obligatorio; ABM de usuarios con clave temporal y
      revocación inmediata de sesiones. 187 tests backend.
- [x] **Deuda por mascota y normalización** — el egreso se bloquea por la
      deuda de la mascota (`consultarDeudaDeMascota`), no la del tutor;
      `NormalizadorAr` normaliza nombres, DNI (7-8 dígitos), teléfono (10
      dígitos nacionales), matrícula y email en los DTO de entrada. README
      completo. 244 tests backend.
- [x] **Agenda mensual y turno rápido** — `GET /turnos?desde=&hasta=` (máx. 62
      días) alimenta un calendario del mes con panel del día;
      `POST /turnos/con-alta` agenda con cliente y/o mascota nuevos en una sola
      transacción. Un único modal de turno (`agendar-turno-modal.tsx`) para
      Agenda y Mostrador. 256 tests backend.
- [x] **Atención con extras y menú** — `/api/v1/extras` (escritura solo
      Dueño); la ficha recibe extras, pasa el turno a ATENDIDO y emite una
      comanda con líneas (`comanda_item`, precio congelado). Botón "Atender" en
      la Agenda, catálogo en Tarifas, detalle en Caja, barra lateral agrupada.
      271 tests backend.

## Decisiones Críticas de Arquitectura

1. **Rutas:** frontend en `app/` + `components/` + `lib/` (sin `frontend/src/`);
   backend en `backend/src/main/java/com/veterinaria/veterinaria_backend/`.
2. **Fechas:** prohibido `new Date().toISOString().split('T')[0]` en frontend
   (salto de día UTC en UTC-3). Siempre `hoyLocal()` de `lib/api.ts`.
3. **Desacople clínico-contable:** la ficha jamás maneja dinero; la facturación
   deriva a `ComandaCobro` con precio desde `Tarifa`.
4. **Conducta:** `Mascota.conducta` = normal/gruñe/muerde/miedoso;
   `ConsultaMedica.conductaObservada` = TRANQUILO/NERVIOSO/REACTIVO/AGRESIVO.
5. **Egreso vs. Cobro:** retiro exige `autorizado_retiro=true` + que la
   mascota no tenga comandas impagas (la deuda es de la mascota, no del tutor;
   una comanda sin mascota no retiene a nadie). Cualquier tutor vinculado
   puede pagar la comanda.
10. **Normalización de datos:** vive en `normalizacion/NormalizadorAr` y corre
   en los constructores de los DTO de entrada, antes de Bean Validation. Solo
   afecta altas y ediciones: las filas viejas se normalizan al editarlas.
6. **Arquitectura en capas estricta:** Controller → Service → Repository,
   DTOs planos (sin entidades serializadas), errores HTTP semánticos
   (400/404/409/500) vía `GlobalExceptionHandler`.
7. **Store global:** `lib/store.tsx` es SOLO la query del TopBar. El rol sale
   del login (`lib/auth.tsx`), nunca de un selector. Todo dato operativo sale
   de `lib/api.ts` + `lib/use-api.ts` (triplete `{datos, cargando, error, recargar}`).
9. **Permisos en el backend:** la matriz vive en `security/SecurityConfig.java`;
   esconder pestañas es solo UI. El rol y el estado de la cuenta se leen de la
   base en cada request (`UsuarioJwtConverter`). Nunca versionar un
   `JWT_SECRET` por defecto; en Docker `JWT_SECRET` y `OWNER_PASSWORD` son
   obligatorios.
8. **Config por entorno:** `application.properties` lee `DB_HOST/DB_PORT/
   DB_USERNAME/DB_PASSWORD` con defaults que preservan el dev local (XAMPP:
   localhost/3306/root/vacío); docker-compose los sobreescribe y el secreto
   vive en `.env` (nunca versionado).

## Riesgos y Pendientes Abiertos

1. **[RESUELTO] Módulos legacy** — `consultas/cobros/guarderia.tsx` eliminados;
   el Riesgo 2 histórico ("guardería legado sin montar") quedó cerrado.
2. **`vinculado_desde` en `TutorMascota`** sigue sin consumirse en el frontend.
3. **Sin migraciones versionadas** (Flyway/Liquibase): la base depende de
   `hibernate.ddl-auto=update`.
4. **Colisión VS Code/Maven:** el Language Server genera clases rotas en
   `backend/target/classes`; si aparecen `BeanCreationException` anómalos,
   compilar siempre con `mvnw clean`.
5. **Regla del huérfano (nueva, inviolable):** al borrar un tutor, una mascota
   sin otros tutores se elimina con él; si tiene historial clínico/contable, el
   borrado completo aborta con 409 (única transacción).
6. **`docker compose up` sin verificar end-to-end:** el YAML quedó validado
   estáticamente. En macOS ya se probó un MySQL 8 en contenedor (puerto 3307)
   con backend y frontend locales, pero falta el `compose up --build`
   completo. Antes del `up`: detener el MySQL local o subir `DB_PORT=3307` en
   `.env` (colisión del puerto 3306).
7. **MySQL de XAMPP es frágil:** si el harness falla con ~160 errores de
   `ApplicationContext` (Caused by: `Communications link failure` /
   `Connection refused` en 3306), no es el código: MySQL se cayó. Reiniciar
   `C:\xampp\mysql\bin\mysqld.exe`, esperar el puerto y relanzar el harness.
8. **Datos previos sin normalizar:** tutores con teléfono que no tenga 10
   dígitos o DNI de 9-10 dígitos dan 400 al editarlos hasta corregir el dato.
   No se reescribió la base existente.

## Registro Técnico Clave (solo lo que condiciona trabajo futuro)

- **Endpoints vivos que el frontend consume** (todos bajo `/api/v1`): tutores,
  mascotas, profesionales, turnos, guarderia, egreso (`/egreso/validar`,
  `/egreso/pacientes-hoy`), caja (`/comandas/dia`, `/caja/pagos/{id}`,
  `/caja/tutores-del-dia`, `/caja/cierre`, `/comandas/tarifas/{concepto}`),
  consultas, `/turnos?desde=&hasta=` (vista mensual), `/turnos/con-alta` y
  `/extras` (catálogo; POST/PUT solo Dueño en `SecurityConfig`).
- **Tests backend:** 271 en verde. Cobertura clave: aforo concurrente (lock
  real en MySQL), ventana de desinfección 45+15, cotutor puede pagar, egreso por
  DNI sin vínculo, huérfano con historial → 409, aislamiento en
  `veterinaria_test` (no pisa `veterinaria_db`).
- **Frontend sin test runner:** la calidad se sostiene con `tsc --noEmit` +
  `next build --webpack` (el harness 1/3 y 2/3).
- **Refactor de referencia:** `components/modules/mostrador/` es el patrón a
  seguir (orquestador con estado/fetch + hijos presentacionales tipados con
  `EstadoApi<T>`).
- **Docker:** `docker compose up --build` levanta db(3306)/backend(8080)/
  frontend(3000). El browser pega a la API vía `NEXT_PUBLIC_API_URL` (build-time,
  default `http://localhost:8080/api/v1`); el backend resuelve `db` por red
  Docker y el compose falla si `.env` no define `MYSQL_ROOT_PASSWORD`.
- **Health check:** `GET http://localhost:8080/` responde `{"status":"UP",...}`
  sin conexión a DB; útil para sondear el backend (p. ej. en Docker).
- **Contrato de error uniforme:** todas las respuestas de error usan
  `ApiErrorResponse` (`{timestamp,status,error,mensaje,ruta,detalles}`), con
  `status` **numérico**. Rutas inexistentes → **404** (handler dedicado), no 500.
- **CORS:** `CorsConfig` (`WebMvcConfigurer`) aplica sobre `/api/**` para
  localhost:3000, 127.0.0.1:3000 y `*.vercel.app`; métodos
  GET/POST/PUT/DELETE/OPTIONS/**PATCH**; `allowCredentials=true`. Spring Security
  (JWT) toma esta misma configuración CORS.