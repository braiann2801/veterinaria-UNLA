# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Sistema de gestión veterinaria y guardería canina (UNLa). `AGENTS.md` es el protocolo autoritativo del proyecto y `PROGRESS.md` es el registro de estado vigente: leé ambos antes de empezar. El código, los comentarios y los textos de la UI están en español; mantené ese idioma al extender el proyecto.

## Comandos

Frontend (raíz del repo, Next.js en :3000). pnpm es el único lockfile autoritativo (`pnpm-lock.yaml`):

```bash
pnpm install
pnpm dev                 # next dev --webpack
pnpm build               # next build --webpack
npx tsc --noEmit         # chequeo de tipos — OBLIGATORIO: en este proyecto `next build` no valida tipos
```

Backend (`backend/`, Spring Boot 4.x / Java 17 en :8080):

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=AforoGuarderiaTest              # una sola clase
./mvnw test -Dtest=AforoGuarderiaTest#nombreMetodo # un solo método
```

Los tests del backend requieren un **MySQL real en localhost:3306, usuario `root`, sin contraseña**. Usan su propio esquema `veterinaria_test` (`backend/src/test/resources/application.properties`, que *reemplaza* al archivo de propiedades principal: todas las claves deben estar ahí). No se usa H2 a propósito: el aforo de guardería y el cobro dependen de `SELECT ... FOR UPDATE` nativo y de los timeouts de lock de InnoDB, y `AforoConcurrenteTest` mide el lock real.

Barrera de calidad: `test-harness.ps1` (PowerShell; en macOS usar `pwsh` o correr sus 3 pasos a mano) = `tsc --noEmit` → `next build --webpack` → `mvnw clean test`. Si algo falla, no se commitea.

Stack completo en Docker: copiar `.env.example` a `.env`, definir `MYSQL_ROOT_PASSWORD`, `JWT_SECRET` (mínimo 32 caracteres, `openssl rand -base64 48`) y `OWNER_PASSWORD` (clave inicial del Dueño), y correr `docker compose up --build`. Si un MySQL/XAMPP local ya ocupa el 3306, poner `DB_PORT=3307` en `.env`. `NEXT_PUBLIC_API_URL` se inlina al compilar el frontend (por defecto `http://localhost:8080/api/v1`).

## Arquitectura

**Backend** (`backend/src/main/java/com/veterinaria/veterinaria_backend/`): capas estrictas `controller` → `service` (interfaces, implementaciones en `service/impl`) → `repository`. Los controllers solo intercambian DTOs planos (`dto/`, mapeados en `mapper/`); las entidades nunca se serializan (evita ciclos de Jackson en la relación N:M Tutor–Mascota, modelada con `TutorMascota` + `TutorMascotaId`). Con `spring.jpa.open-in-view=false`, las relaciones deben resolverse dentro de la transacción del servicio. Los errores pasan por los tipos de `exception/` (`BadRequestException`, `ResourceNotFoundException`, `BusinessConflictException`) y por `GlobalExceptionHandler`, que devuelve un `ApiErrorResponse` uniforme con 400/404/409/500. El esquema lo maneja `ddl-auto=update`; `DataInitializer` siembra las tarifas de forma idempotente desde las propiedades `veterinaria.tarifas.*`. La conexión a la base se configura por variables de entorno (`DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD`) con defaults compatibles con XAMPP. El prefijo de la API es `/api/v1`; la whitelist de CORS está en `config/CorsConfig.java`.

**Autenticación** (`security/`): Spring Security stateless con JWT HS256 (`spring-boot-starter-security-oauth2-resource-server`). `POST /api/v1/auth/login` y `GET /` son públicos; el resto exige `Authorization: Bearer`. La matriz de permisos está en `SecurityConfig`: `/usuarios/**`, `GET /caja/cierre` y `PUT /comandas/tarifas/**` son solo `DUENIO`; el resto, cualquier empleado (DUENIO/VETERINARIO/RECEPCION/GUARDERIA). `UsuarioJwtConverter` lee el `Usuario` de la base en cada request: el rol sale de la base, y una cuenta inactiva o con `tokenVersion` distinto al claim `ver` da 401 (así desactivar, resetear o cambiar el rol corta la sesión al instante). Con `debeCambiarPassword` la única autoridad es `PASSWORD_CHANGE` (solo `/auth/me` y `/auth/cambiar-password`). `UsuarioInitializer` siembra el Dueño (`OWNER_USERNAME`/`OWNER_PASSWORD`) si no hay ninguno activo; el ABM impide dejar el sistema sin un Dueño activo (409). Sin `JWT_SECRET` se genera una clave aleatoria por arranque: nunca versionar un secreto por defecto. Los tests que no prueban permisos usan `@WithMockUser(roles = "DUENIO")`; los permisos se prueban con tokens reales en `AutenticacionTest` y `UsuarioAbmTest`.

**Frontend**: tres rutas. `/` (`app/page.tsx`) es la landing pública; su contenido sale de `lib/negocio.ts`. `/login` es el ingreso de empleados. `/app` (`app/app/page.tsx`) es el sistema: sin sesión redirige a `/login` y, con `debeCambiarPassword`, muestra solo `components/cambiar-password.tsx`. Adentro, un shell de página única alterna entre los módulos de `components/modules/` (Mostrador, Agenda, Caja, Egreso, Padrón, Fichas, Balance, Usuarios, Profesionales), con visibilidad por rol en `components/sidebar.tsx` (Balance y Usuarios son exclusivos del Dueño). Sesión: `lib/sesion.ts` guarda `{token, expiraEn, usuario}` en `localStorage`; `lib/auth.tsx` (`useAuth()`) da el rol a la UI. Flujo de datos:
- `lib/api.ts` es el único módulo que llama a `fetch`: define la URL base, adjunta el token, ante un 401 (salvo en el login) borra la sesión y manda a `/login?motivo=sesion`, define `ApiError` (con `status` para distinguir 400/401/403/404/409/500) y los tipos TS, que replican exactamente los nombres de campo de los DTOs de Jackson.
- `lib/use-api.ts` expone hooks que devuelven `{datos, cargando, error, recargar}`; los componentes nunca hacen fetch directo.
- `lib/vista.tsx` concentra la UI compartida de carga/error/vacío (`Cargando`, `ErrorBox`, `TOQUE_MINIMO`).
- `lib/store.tsx` solo guarda la búsqueda del TopBar y el tipo `Role`; el rol activo sale del login, nunca de un selector.

## Reglas de dominio inviolables

- Aforo de guardería: máximo 10 perros por día; se garantiza con lock pesimista sobre `ControlAforoDiario`.
- Turnos clínicos: 45 min de atención + 15 min obligatorios de desinfección antes del siguiente turno.
- La ficha clínica (`ConsultaMedica`) nunca maneja dinero; la facturación se deriva a una `ComandaCobro` diferida con precio tomado de `Tarifa`.
- Tutor–Mascota es N:M; cada vínculo tiene `autorizado_retiro`. El retiro exige `autorizado_retiro=true` y no tener deuda; cualquier tutor vinculado puede pagar. Una mascota no puede quedar sin tutor (409 si tiene historial).
- Hay dos enums de conducta distintos: `Mascota.conducta` (normal/gruñe/muerde/miedoso) y `ConsultaMedica.conductaObservada` (TRANQUILO/NERVIOSO/REACTIVO/AGRESIVO).

## Convenciones

- Fechas en el frontend: nunca `new Date().toISOString().split('T')[0]` (salta de día por UTC en UTC-3); usar `hoyLocal()` de `lib/api.ts`. Los campos de fecha usan el `DatePicker` compartido, no `<input type="date">`.
- UI mobile-first: áreas de toque de al menos 44×44 px (`min-h-[44px]`), sin scroll horizontal a nivel página (las tablas densas pasan a tarjetas o a un `overflow-x-auto` local), navegación inferior y paneles tipo sheet en pantallas chicas. Preferir componentes shadcn/ui antes que elementos HTML crudos.
- Java: solo inyección por constructor (`@RequiredArgsConstructor` + `private final`), nunca `@Autowired` en campos.
- Las reglas detalladas por stack están en `.opencode/instructions/*.mdc` (Spring Boot, Next.js/TS, Tailwind/shadcn, UX).
- No editar `AGENTS.md` por cuenta propia: proponer el cambio como diff y esperar la aprobación explícita del dueño. Actualizar `PROGRESS.md` al inicio y al cierre de cada tarea.
