# PROGRESS.md

Memoria operativa del proyecto **veterinaria-UNLA**. Se mantiene corta a
propósito: solo lo que un agente necesita para retomar el trabajo sin repetir
exploración. Los detalles finos de cada fase quedaron en los commits; acá no.

## Estado Actual

- **Fase en curso:** rama `feat/urgencias` (detalle en
  `odd/tasks/urgencias.md`): turno de urgencia sin datos y plaza extra de
  guardería para urgencia clínica (solo Veterinario). Implementada; e2e y
  revisión en curso; pendiente de push/PR.
- **Base Git:** `main` en `e2edc3b` (merge del PR #2, deuda por mascota y
  normalización).
- **Next.js:** `agentRules: false` en `next.config.mjs` para que `next dev` no
  reescriba `AGENTS.md`.
- **`backend/mvnw`:** versionado con permiso de ejecución (`+x`), así
  `./mvnw` corre en macOS/Linux sin `chmod`.
- **Fase en curso (2026-11-20):** **medicamentos con precio en la atención**.
  Reemplaza al módulo de extras (código, API y UI borrados; comandas históricas
  intactas). Backend, frontend y docs terminados, todavía **sin commitear** en
  el working tree. Detalle en `odd/tasks/medicamentos-precio-atencion.md`.
- **Harness:** `tsc --noEmit` limpio, `next build --webpack` OK, 402 tests de
  Spring Boot en verde contra el datadir temporal de MySQL, `docker compose
  config --quiet` OK. Todo sin commitear.
- **Endurecimiento agnóstico al hosting (2026-11-19):** CSP en
  `Report-Only` + `Permissions-Policy` en `next.config.mjs`; `iss` del JWT
  validado y configurable (`JWT_ISSUER`); `Cache-Control: no-store` en login,
  logout y cambio de contraseña; revocación inmediata de sesiones en
  `POST /api/v1/auth/logout` (`tokenVersion++`); TTL de JWT configurable en 4 h
  (`JWT_HORAS`); rate limiting extensible (`AlmacenRateLimit`, memoria por
  defecto y soporte Redis vía `REDIS_URL` / `RATE_LIMIT_STORAGE=memory|redis`);
  reglas agregadas por par, por IP (anti-spraying) y por usuario
  (anti-distribuido); throttle en `/auth/cambiar-password`; `SanitizadorLog`
  anti-log injection y enmascaramiento de PII; `show-sql` apagado por defecto
  (`JPA_SHOW_SQL`); CORS sin comodín de Vercel por defecto (`CORS_ORIGENES` exacto);
  `PORT` configurable; helper `lib/url-segura.ts` aplicado a la landing.
  Verificado: compile backend, 48 tests (unitarios e integración: AutenticacionTest, LoginThrottlingTest, LogoutRevocacionTest, RateLimitingExtendidoTest), `tsc --noEmit`, `next build --webpack` y `docker compose config`. MySQL de XAMPP reiniciado para las suites `@SpringBootTest`. Pendiente: quitar `typescript.ignoreBuildErrors`.
- **Importación histórica de Huellitas:** aplicada sobre `veterinaria_db`
  local y sobre **producción (Aiven + Render)** el 2026-11-04. Antes de cargar
  se respaldó Aiven en `datos/backup-aiven-20261104-223149.sql` (gitignored) y
  se limpiaron solo las tablas de datos; se conservaron `usuario`, `tarifa`,
  `tarifa_guarderia` y `extra`. Resultado en Aiven: 9 tutores, 11 mascotas, 11
  vínculos, 2 profesionales placeholder, 10 consultas, 10 estadías, 20 comandas
  y 18 pagos. Verificado por JDBC y por la API de Render; el frontend de Vercel
  ya apunta a `https://veterinaria-unla.onrender.com/api/v1` y CORS responde OK.
  Reportes en `datos/` (gitignored): `*-simulacion.md` (revisión previa) y
  `*-aplicado.md` (constancia).

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
- [x] **Atención con extras y menú** (hito histórico, reemplazado el
      2026-11-20) — `/api/v1/extras` (escritura solo Dueño); la ficha recibe
      extras, pasa el turno a ATENDIDO y emite una comanda con líneas
      (`comanda_item`, precio congelado). Botón "Atender" en la Agenda, catálogo
      en Tarifas, detalle en Caja, barra lateral agrupada. 271 tests backend.
      El código y la UI de extras se borraron después; las comandas quedaron.
- [x] **Correcciones de flujo y registros** — seña como pago SENA en la
      comanda; DELETE desactiva (tutor/mascota/profesional) con reactivar y
      toggle de desactivados; Agenda guardería con calendario y color por
      mascota (`GET /guarderia?desde=&hasta=`); peso en la ficha; Cargar
      mascota en Mostrador; sin pestaña Ficha médica; confirmaciones en modal.
      287 tests backend.
- [x] **Límite de intentos de login** — `LimitadorIntentosLogin` (en memoria,
      clave usuario+IP): 5 fallos en 15 min bloquean el par 15 min con 429
      `ApiErrorResponse` + `Retry-After`; un login exitoso resetea. `Clock`
      inyectable (`RelojConfig`) para testear el paso del tiempo. 293 tests.
- [x] **Importación única de datos históricos** — `CommandLineRunner`
      deshabilitado por defecto (`veterinaria.importacion.habilitada=false`)
      lee el Excel de Huellitas con POI, construye un plan conciliado y escribe
      todo en una sola transacción. Sin endpoint ni pantalla: el frontend no
      cambia. La hoja 1 deduplica tutores/mascotas con DNI provisional; las
      hojas 2 y 3 conservan fechas, importes y medios de pago históricos.
      Reporte Markdown en `datos/` (simulación y aplicado). 311 tests.
- [x] **Urgencias** (`feat/urgencias`) — excepciones confirmadas a las
      reglas 1 y 2 de AGENTS.md. Turno de urgencia (`POST /turnos/urgencia`):
      sin mascota ni profesional, a la hora actual, sin chequeo de
      solapamiento; se atiende eligiendo profesional y la comanda queda sin
      titular (cobro → 409) hasta `POST /turnos/{id}/completar-datos`.
      `EsquemaUrgencias` relaja los NOT NULL al arrancar. Plaza de urgencia de
      guardería por día (`plaza_urgencia_guarderia`, `/guarderia/urgencia`):
      escrituras solo rol VETERINARIO en `SecurityConfig`, fuera del
      `contarCuposOcupados`, sin comanda, bajo el mismo semáforo del día.
      310 tests.
- [x] **Landing pública rediseñada** — se integró el diseño claro
      (crema/azul marino, Fredoka + Poppins) en `/`: hero con huellas,
      tarjeta de servicios montada sobre el hero, estándares con fotos
      circulares, "¿Por qué elegirnos?" y CTA de contacto. El contenido sigue
      saliendo de `lib/negocio.ts` (nuevos `nombreCorto`, `estandares` y
      `motivos`); imágenes optimizadas en `public/landing/`; nav móvil con menú
      hamburguesa y "Ingreso empleados" siempre visible.
- [x] **Identidad de la landing aplicada al login y al sistema** — el tema
      oscuro se retiró: `app/layout.tsx` ya no monta la clase `dark` y los
      tokens de `app/globals.css` pasan a papel cálido `#faf7f1`, tarjetas
      blancas, azul marino `#232a5c` como primario y crema `#f6efe3` en el
      sidebar. Como las pantallas son token-driven (solo 3 colores literales en
      `components/`), no hubo que reescribir módulos: se quitaron los 81
      overrides `dark:` de alertas (`caja`, `padron`, `egreso`, `balance`,
      `fichas`, `agenda`, shadcn `button`) y quedan los valores claros
      (`text-amber-700`, `bg-amber-50`, ...). Poppins pasa a ser la tipografía
      del producto y Fredoka se aplica a `h1`/`h2`/`h3` desde `@layer base`.
      `/login` se rediseñó con el lenguaje de la landing (fondo crema, huellas
      decorativas, tarjeta blanca y marca). Verificado con `tsc --noEmit`,
      `next build --webpack`, y auditoría en navegador headless: contraste
      ≥ 3.5:1 en texto visible y `overflowX = 0` en 1440 px y 390 px, en
      `/app` y `/login`.

- [x] **Balance por período** — `GET /api/v1/caja/cierre?desde=&hasta=`
      (rango inclusivo, máx. 366 días; `?fecha=` sigue siendo un día) con
      total facturado, cobrado, saldos pendientes, arqueo por medio, serie
      diaria completa y extracto. En el frontend, botón "Seleccionar período"
      con calendario de dos fechas y atajos (hoy, 7 días, este mes, mes
      anterior, este año); tarjetas de cobranza, gráfico de barras por día y
      extracto con "Ver más". Detalle: `odd/tasks/balance-periodo.md`.

- [x] **Ver contraseña en los formularios** — `PasswordInput`
      (`components/ui/password-input.tsx`) envuelve `TextInput` y agrega el
      botón de ojo (`Eye`/`EyeOff`, `aria-pressed`) sin robar el foco al
      destapar. Se usa en el login y en los tres campos del cambio de contraseña
      (pantalla obligatoria y modal): escribir a ciegas terminaba en "usuario o
      contraseña incorrectos" y, tras 5 intentos, en el bloqueo de 15 min.

- [x] **Verificación de entradas y permisos (seguridad)** — auditoría completa
      de DTOs, repositorios y matriz de roles. Sin inyección SQL: las dos únicas
      consultas nativas usan parámetros. Topes en el login (usuario ≤ 40, clave
      ≤ 72 caracteres y ≤ 72 bytes UTF-8, porque BCrypt corta ahí), body
      ilegible sin detalle interno, CORS configurable y sin credenciales,
      tarifa con validación declarativa y `maxLength` en el frontend.
      Los campos sensibles (`rol`, `activo`, `debeCambiarPassword`,
      `passwordHash`) se ignoran si el cliente los manda; el rol siempre sale
      de la base. Tests en `EntradaSeguraTest`; detalle en
      `odd/tasks/seguridad-entradas.md`. Verificado además contra el backend
      en vivo: cargas SQLi → `401`, cuerpos gigantes → `400`, CORS deja pasar
      solo los orígenes configurados.

- [x] **Caja: filtro por mascota y fecha de la deuda** — `GET
      /comandas/mascota/{id}` lista las comandas de una mascota de cualquier
      fecha (estado opcional) y `GET /mascotas/{id}/deuda` expone el total con
      la fecha de cada comanda. En la UI, elegir una mascota reemplaza al
      filtro por jornada y el modal de cobro muestra `Deuda del dd/mm/aaaa`
      con aviso cuando es de otro día. 361 tests backend
      (`ComandasPorMascotaTest`).

- [x] **CORS estricto y cabeceras de seguridad** — al definir `CORS_ORIGENES`,
      la lista reemplaza al default y los comodines amplios
      (`https://*.vercel.app`) se descartan con aviso en el log: se exige la
      URL exacta de cada deploy. Sin la variable se conservan 3000 local +
      comodín (AGENTS.md §1). En `next.config.mjs`, `headers()` agrega
      `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` y
      `Referrer-Policy: strict-origin-when-cross-origin` a todas las rutas.
      Test unitario `CorsConfigTest`; verificado con `tsc --noEmit`,
      `next build --webpack` y headers reales con `next start`.

- [x] **Endurecimiento de seguridad agnóstico al hosting** — `next.config.mjs`
      suma `Permissions-Policy` restrictiva (cámara, micrófono, geolocalización,
      pagos, USB y sensores) y `Content-Security-Policy-Report-Only` con
      orígenes locales, el backend de `NEXT_PUBLIC_API_URL` y los dominios de
      `@vercel/analytics`; se mantiene `'unsafe-inline'` en scripts/estilos
      hasta el refactor a nonce y no se fuerza HSTS. Backend: `iss` validado
      con `JwtValidators.createDefaultWithIssuer` y emitido desde
      `JWT_ISSUER` (default `veterinaria-unla`), `Cache-Control: no-store` en
      `/auth/login` y `/auth/cambiar-password`, `JPA_SHOW_SQL`/`JPA_FORMAT_SQL`
      con default `false`, `PORT` configurable y CORS por defecto solo local
      (sin `*.vercel.app`). Frontend: `lib/url-segura.ts` permite solo
      `http/https/mailto/tel` y rutas relativas, y la landing lo usa para
      `whatsapp`, `telefono`, `mapa` y `email`. `.env.example`,
      `docker-compose.yml` y README actualizados.

- [x] **Revocación de sesión en logout y Rate Limiting extensible (Seguridad)** —
      TTL del JWT reducido a 4 h por defecto (configurable con `JWT_HORAS`);
      endpoint `POST /api/v1/auth/logout` con `Cache-Control: no-store` e
      invalidación inmediata (`tokenVersion++` persistente); frontend
      (`lib/auth.tsx` y `lib/api.ts`) con logout asíncrono y tolerancia a fallos de
      red. Rate limiting refactorizado con abstracción `AlmacenRateLimit` /
      `RateLimiterService`: implementación en memoria por defecto
      (`AlmacenRateLimitMemoria`) para dev local sin dependencias y soporte
      opcional para Redis (`AlmacenRateLimitRedis`, `REDIS_URL`,
      `RATE_LIMIT_STORAGE=memory|redis`); reglas agregadas en login por par
      `(usuario, IP)`, por `IP` (anti-password spraying) y por `usuario`
      (anti-ataque distribuido) sin revelar existencia de cuentas; límite de
      intentos en `POST /api/v1/auth/cambiar-password` con `429` y `Retry-After`;
      `SanitizadorLog` previene log injection (CRLF) y enmascara PII. Se reforzó
      `sanitizarUrl` (credenciales ofuscadas aun con CRLF inyectado), Redis con
      scripts Lua atómicos (`INCR`+`EXPIRE` y `GET`+`SET`) y `rediss://` en puerto
      6379 por defecto. Nuevos tests: `SanitizadorLogTest`,
      `AlmacenRateLimitRedisTest`, `LogoutRevocacionTest` y
      `RateLimitingExtendidoTest`. Sin DB pasan compile, tests unitarios, tsc,
      build, docker compose y suites de integración (48 tests, 0 fallos).

- [x] **Medicamentos e insumos (2026-11-20)** — catálogo con stock atómico para
      los cuatro roles. Backend: entidades `Medicamento` y
      `MovimientoStockMedicamento` (historial inmutable), repositorios, DTOs
      `record`, `MedicamentoService`/`Impl` y `MedicamentoController`. Endpoints
      bajo `/api/v1/medicamentos`: `GET` (búsqueda case-insensitive por nombre,
      principio activo y laboratorio; paginado server-side; solo activos; orden
      alfabético), `GET /{id}`, `POST` (alta), `POST /{id}/stock` (suma) y
      `GET /{id}/movimientos` (historial paginado). Unicidad por
      `(nombre, presentación)` normalizados con `NormalizadorAr.clave` → 409;
      `clave` separa letras de dígitos para que `500mg` y `500 mg` colisionen.
      Stock con `SELECT ... FOR UPDATE` en SQL nativo (mismo motivo que el
      aforo: Hibernate 7 emite `FOR UPDATE OF` y MariaDB lo rechaza); `cantidad
      > 0` validada; cada alta con stock inicial y cada carga dejan un
      `INGRESO`. `MovimientoStockMedicamento.usuario` es snapshot del username
      (sin FK: las cuentas se borran) y `referenciaId` es un `Long` sin FK
      listo para el id de ficha. `registrarConsumo(...)` ya descuenta con lock,
      valida stock y escribe `EGRESO_FICHA` — **todavía no lo invoca
      `ConsultaMedicaService`**; hoy lo invoca al guardar la ficha (hito
      siguiente). `SecurityConfig` da acceso a los cuatro roles.
      Frontend: pestaña "Medicamentos" (sección Operación; no en la barra
      inferior del móvil), tabla en desktop / cards en celular, buscador con
      debounce de 350 ms + `AbortController`, alta, carga de stock con
      historial compacto y paginación con el total del backend. Sin sistema de
      toasts: el feedback es inline (`role="status"` / `role="alert"`).
      Verificado: 19 tests nuevos (normalización, endpoints, permisos por rol,
      12 cargas concurrentes), suite completa 404 tests, `tsc --noEmit`,
      `next build --webpack` y `docker compose config --quiet`.

- [x] **Medicamentos con precio en la atención (2026-11-20)** — se eliminó
      el módulo de extras (entidad, DTOs, repositorio, service, controller,
      reglas de `SecurityConfig`, tests y UI) y se reemplazó por medicamentos con
      precio propio. Regla 3 de `AGENTS.md` actualizada con el diff aprobado.
      `Medicamento.precio` (`DECIMAL(12,2)`, default 0) y
      `PUT /api/v1/medicamentos/{id}/precio` para cualquier empleado.
      `ConsultaMedicaRequestDTO` recibe `medicamentos` (`medicamentoId` +
      `cantidad` 1..99) sin montos; `ConsultaMedicaServiceImpl` descuenta stock
      (`EGRESO_FICHA` con `referenciaId` de la ficha) y copia nombre y precio
      vigente a la comanda. `ComandaItem` guarda escalares, sin FK: las comandas
      con extras históricas siguen enteras y la tabla `extra` no se tocó.
      Frontend: `SelectorMedicamentos` (misma barra de búsqueda con debounce y
      `AbortController`, precio informativo) en Atender; alta y edición de
      precio en Medicamentos; Tarifas ya no muestra extras. Verificado: 402
      tests (9 nuevos de atención, 4 de precio), `tsc --noEmit`,
      `next build --webpack` y `docker compose config --quiet`. Detalle en
      `odd/tasks/medicamentos-precio-atencion.md`.

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
5. **Nada se borra (reemplaza a la regla del huérfano):** DELETE de tutor,
   mascota y profesional desactiva (`activo=false`). Al desactivar un tutor se
   desactivan sus mascotas sin otro tutor activo. Los inactivos no toman turnos
   ni reservas (400). `PUT .../{id}/reactivar` los vuelve a dar de alta; una
   mascota necesita un tutor activo.
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
9. **[RESUELTO] Rate limiting extensible:** refactorizado detrás de
   `AlmacenRateLimit` / `RateLimiterService`. Memoria por defecto en dev local
   (`AlmacenRateLimitMemoria`) y soporte para Redis (`AlmacenRateLimitRedis`,
   `REDIS_URL`, `RATE_LIMIT_STORAGE=memory|redis`). Reglas multi-dimensión: por
   par `(usuario, IP)`, por `IP` (anti-spraying) y por `usuario`
   (anti-distribuido), más throttle en `/auth/cambiar-password` y sanitización
   de logs (`SanitizadorLog`).
10. **[RESUELTO] Revocación de sesión en logout:** endpoint
    `POST /api/v1/auth/logout` con `tokenVersion++` persistente y
    `Cache-Control: no-store`; frontend invoca endpoint antes de limpiar storage
    con tolerancia a fallos; TTL de JWT reducido a 4 h por defecto (`JWT_HORAS`).
11. **`typescript.ignoreBuildErrors: true`:** el build de Next no falla por
    errores de tipos; el harness lo compensa con `tsc --noEmit`. Siguiente
    bloque: quitarlo y dejar que `next build` también valide.
12. **CSP en `Report-Only`:** no bloquea todavía. Siguiente bloque: nonce vía
    `middleware.ts`, revisar reportes y pasar a `enforce`, quitando
    `'unsafe-inline'` cuando los estilos inline se migren a variables CSS.
13. **Descuento de stock desde ficha clínica (resuelto 2026-11-20):**
    `ConsultaMedicaServiceImpl.registrar` invoca `registrarConsumo(...)` dentro
    de la misma transacción: resuelve medicamentos activos, valida stock y
    escribe `EGRESO_FICHA` con `referenciaId` de la ficha; sin stock, 409 con
    rollback de ficha, comanda y movimientos. Los extras (conceptos de cobro sin
    vínculo con `Medicamento`) se eliminaron y reemplazaron por esta lista.
    Sigue sin haber edición ni desactivación de medicamentos: el campo `activo`
    existe, el listado solo muestra activos y no hay endpoint para cambiarlo.
14. **Precio congelado:** `ComandaItem` guarda descripción, cantidad, precio
    unitario y subtotal como escalares, sin FK a `Medicamento` ni a `Extra`.
    Así las comandas históricas (incluidas las de extras) sobreviven a cambios
    de catálogo. La tabla `extra` y su FK quedan en la BD: `ddl-auto=update`
    no las dropea y no se quiso reescribir datos históricos.

## Registro Técnico Clave (solo lo que condiciona trabajo futuro)

- **Endpoints vivos que el frontend consume** (todos bajo `/api/v1`): tutores,
  mascotas, profesionales, turnos, guarderia, egreso (`/egreso/validar`,
  `/egreso/pacientes-hoy`), caja (`/comandas/dia`, `/caja/pagos/{id}`,
  `/caja/tutores-del-dia`, `/caja/cierre` (día con `fecha` o período con
  `desde`/`hasta`), `/comandas/tarifas/{concepto}`, `/comandas/mascota/{id}`
  (todas las fechas, estado opcional) y `/mascotas/{id}/deuda`),
  consultas, `/turnos?desde=&hasta=` (vista mensual), `/turnos/con-alta`,
  `/medicamentos` (catálogo + `POST` con precio + `PUT /{id}/precio` +
  `POST /{id}/stock` + `GET /{id}/movimientos`; los cuatro roles en
  `SecurityConfig`). Ya no existe `/extras`.
- **Tests backend:** 402 en verde. Cobertura clave: aforo concurrente (lock
  real en MySQL), ventana de desinfección 45+15, cotutor puede pagar, egreso por
  DNI sin vínculo, huérfano con historial → 409, aislamiento en
  `veterinaria_test` (no pisa `veterinaria_db`), balance por período (rango
  inclusivo, serie diaria completa, facturado ≠ cobrado, límite de 366 días),
  12 cargas de stock simultáneas sobre la misma fila (lock real en MySQL:
  `medicamento` con `SELECT ... FOR UPDATE`, sin `@Transactional` en la clase
  de test para no enmascarar el lock), precio congelado en comanda, stock
  insuficiente con rollback y movimientos `EGRESO_FICHA` con `referenciaId` de
  la ficha, y parsers/conciliación de la importación histórica.
- **Importación histórica:** vive en `importacion/` (`ImportacionRunner`,
  `ConstructorPlan`, `ConciliacionHistorica`, `ImportacionService`,
  `ReporteImportacion`). Es una vía única de consola: no hay endpoint, no hay
  botón y el runner no hace nada con `veterinaria.importacion.habilitada=false`.
  Para re-ejecutar: `habilitada=true` + `aplicar=false` genera el reporte de
  simulación; `aplicar=true` escribe y es idempotente por DNI, mascota+fecha de
  consulta y mascota+fecha de ingreso. El Excel y los reportes están en
  `datos/` (gitignored) porque contienen datos personales.
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
- **CORS:** `CorsConfig` (`WebMvcConfigurer`) aplica sobre `/api/**`. Sin
  `CORS_ORIGENES` solo acepta `http://localhost:3000` y
  `http://127.0.0.1:3000` (sin hosting atado); si la variable está definida, su
  lista manda y los comodines `*.vercel.app` se descartan. Métodos
  GET/POST/PUT/DELETE/OPTIONS/PATCH; `allowCredentials=false`. Spring Security
  (JWT) toma esta misma configuración CORS.
- **JWT:** `iss` configurable por `JWT_ISSUER` (default `veterinaria-unla`) y
  validado en el decoder; `exp` obligatorio; `ver` contra `tokenVersion`.
  Cambiar `JWT_ISSUER` invalida las sesiones abiertas.