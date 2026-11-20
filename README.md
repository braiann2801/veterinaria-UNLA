# veterinaria-UNLA

Sistema de gestión para una veterinaria con guardería canina. Nace de un problema de organización y lo resuelve con agenda de turnos clínicos, control de cupos de guardería, ficha clínica, caja multipago y egreso seguro de mascotas.

| Capa | Tecnología | Puerto |
|---|---|---|
| Frontend | Next.js (React, Tailwind, shadcn/ui), compilado con Webpack | `3000` |
| Backend | Java 17+ con Spring Boot (Web, Data JPA, Security con JWT) | `8080` |
| Base de datos | MySQL 8, base `veterinaria_db` | `3306` |

## Inicio rápido con Docker

Necesitás Docker con Compose.

1. Copiá la plantilla de variables:

   ```bash
   cp .env.example .env
   ```

2. Completá `.env`. Hay tres valores obligatorios: sin ellos `docker compose` no arranca.

   | Variable | Para qué sirve |
   |---|---|
   | `MYSQL_ROOT_PASSWORD` | Clave del root de MySQL dentro del contenedor. Usá solo letras y números, porque el healthcheck la pasa por la shell. |
   | `JWT_SECRET` | Firma de los tokens de sesión. Tiene que tener al menos 32 caracteres; podés generarlo con `openssl rand -base64 48`. |
   | `OWNER_PASSWORD` | Clave inicial de la cuenta del Dueño. El Dueño la cambia en su primer ingreso. |

   Hay cinco variables opcionales:
   - `DB_PORT`: puerto del host donde se publica MySQL. Por defecto es `3306`; usá `3307` si ya tenés otro MySQL en el 3306.
   - `NEXT_PUBLIC_API_URL`: URL de la API que usa el navegador. Por defecto es `http://localhost:8080/api/v1`. También define el origen del backend que la CSP del frontend permite en `connect-src`.
   - `CORS_ORIGENES`: lista separada por comas con los orígenes exactos del frontend. Sin ella solo se aceptan los locales (`http://localhost:3000`, `http://127.0.0.1:3000`); en cualquier despliegue hay que fijar la URL real.
   - `JWT_ISSUER`: emisor (`iss`) de los tokens. Por defecto `veterinaria-unla`. Cambiarlo invalida las sesiones abiertas, porque los tokens viejos dejan de coincidir.
   - `JPA_SHOW_SQL`: logs SQL de Hibernate. Por defecto `false`; poner `true` solo para depurar en local.

   El backend también acepta `PORT` para el puerto HTTP (por defecto `8080`), útil en hostings que lo inyectan.

3. Levantá todo:

   ```bash
   docker compose up --build
   ```

4. Abrí `http://localhost:3000`. Para comprobar el backend, `GET http://localhost:8080/` responde `{"status":"UP", ...}`.

`.env` no se versiona; solo se versiona `.env.example`.

## Primer ingreso del Dueño

1. En el primer arranque, el backend crea la cuenta del Dueño:
   - El usuario es el valor de `OWNER_USERNAME`, que por defecto es `duenio`.
   - La clave es el valor de `OWNER_PASSWORD`. Sin Docker, si no la definís, la clave por defecto es `cambiar1234`.
2. Entrá a `http://localhost:3000/login` con ese usuario y esa clave.
3. El sistema te obliga a cambiar la clave. Hasta que lo hagas, la API rechaza cualquier otra operación con 403.
4. Desde la pestaña **Usuarios**, el Dueño da de alta a los empleados.
   - Roles disponibles: Veterinario, Recepción y Guardería. Guardería tiene los mismos permisos que Recepción.
   - Cada cuenta nueva recibe una clave temporal que se muestra una sola vez.
   - Desactivar una cuenta, resetear su clave o cambiarle el rol cierra al instante las sesiones abiertas de esa persona.

**Límite de intentos y Rate Limiting:** control de tasa extensible detrás de `AlmacenRateLimit` (`RATE_LIMIT_STORAGE=memory|redis`, con `REDIS_URL`).
- **Par (usuario, IP):** tras 5 contraseñas incorrectas para el mismo usuario desde la misma IP, el par queda bloqueado 15 minutos (responde 429, aun con la clave correcta). Se ajusta con `LOGIN_INTENTOS_MAXIMOS` y `LOGIN_BLOQUEO_MINUTOS`.
- **Por IP (anti-password spraying):** tras 20 intentos fallidos desde una misma IP sobre distintos usuarios, la IP queda bloqueada 15 minutos (`LOGIN_IP_INTENTOS_MAXIMOS` y `LOGIN_IP_BLOQUEO_MINUTOS`).
- **Por usuario (anti-ataque distribuido):** tras 15 intentos fallidos sobre la misma cuenta desde IPs distintas, el usuario queda bloqueado 15 minutos (`LOGIN_USUARIO_INTENTOS_MAXIMOS` y `LOGIN_USUARIO_BLOQUEO_MINUTOS`).
- **Cambio de contraseña:** `POST /api/v1/auth/cambiar-password` limita a 5 intentos fallidos de clave actual (`CAMBIO_PASSWORD_INTENTOS_MAXIMOS` y `CAMBIO_PASSWORD_BLOQUEO_MINUTOS`).
- **Sanitización de logs:** `SanitizadorLog` previene log injection neutralizando saltos de línea (`\r`, `\n`) y enmascara identificadores (usuarios, emails, IPs) para no exponer PII en claro.

Balance, Usuarios, cierre de caja y tarifas son solo para el Dueño, y el backend responde 403 al resto. La matriz de permisos vive en `backend/src/main/java/com/veterinaria/veterinaria_backend/security/SecurityConfig.java`.

## Seguridad

- **Cabeceras del frontend** (`next.config.mjs`): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin` y una `Permissions-Policy` que deshabilita cámara, micrófono, geolocalización, pagos, USB y sensores.
- **CSP en modo reporte**: se emite `Content-Security-Policy-Report-Only` con orígenes locales, el backend de `NEXT_PUBLIC_API_URL` y los dominios de `@vercel/analytics`. No bloquea todavía: se deja así mientras la UI usa estilos inline y Next inyecta scripts de arranque. El siguiente paso es pasar a `enforce` con nonce (`middleware.ts`) y quitar `'unsafe-inline'`.
- **HSTS**: no se fuerza `Strict-Transport-Security` desde la app para no romper HTTP local ni migraciones de hosting; conviene configurarlo en el proxy/CDN cuando el dominio ya sea HTTPS.
- **JWT y Revocación de Sesión**: firma HS256 con `JWT_SECRET` de al menos 32 bytes (sin la variable se genera una clave aleatoria por arranque), `exp` obligatorio con vigencia por defecto de 4 horas (configurable con `JWT_HORAS`), `iss` validado contra `JWT_ISSUER` y `ver` comparado con el `tokenVersion` del usuario.
- **Cierre de Sesión (Logout)**: `POST /api/v1/auth/logout` invalida inmediatamente todos los tokens vigentes del usuario incrementando su `tokenVersion` en la base de datos (stateless, persistente entre reinicios y sin dependencias de memoria), respondiendo con `Cache-Control: no-store`. El frontend invoca este endpoint con tolerancia a fallos antes de limpiar el storage local.
- **Respuestas de autenticación**: `/api/v1/auth/login`, `/api/v1/auth/logout` y `/api/v1/auth/cambiar-password` envían `Cache-Control: no-store` para que tokens y respuestas no queden en cachés intermedias.
- **URLs de contacto**: la landing pasa `whatsapp`, `telefono`, `mapa` y `email` por `lib/url-segura.ts`, que solo admite `http:`, `https:`, `mailto:`, `tel:` o rutas relativas y descarta `javascript:`/`data:`.
- **CORS**: por defecto solo orígenes locales; en producción se fija `CORS_ORIGENES` con la URL exacta. Sin credenciales (el token viaja en `Authorization`).

Pendiente para el próximo bloque: pasar CSP a enforce y quitar `typescript.ignoreBuildErrors` de `next.config.mjs`.

## Desarrollo local sin Docker

Necesitás un MySQL 8 en `localhost:3306` con usuario `root` sin clave, o definir `DB_HOST`, `DB_PORT`, `DB_USERNAME` y `DB_PASSWORD`. La base se crea sola.

Las horas del sistema (pagos, comandas, jornada del Balance) son de Argentina aunque el servidor esté en UTC, como en Docker o en la nube: el backend fija la zona `America/Argentina/Buenos_Aires` al arrancar. Se cambia con la variable `ZONA_HORARIA`.

```bash
# Backend (en backend/)
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run

# Frontend (en la raíz)
pnpm install
pnpm dev                      # next dev --webpack, en http://localhost:3000
```

En Windows, `dev.ps1` levanta las dos cosas juntas.

## Tests

`test-harness.ps1` es la barrera de calidad. Si alguno de sus tres pasos falla, no se commitea:

1. `tsc --noEmit`, el chequeo de tipos del frontend.
2. `next build --webpack`, el build del frontend.
3. `mvnw clean test`, la suite del backend.

Los tests del backend usan la base `veterinaria_test`, separada de `veterinaria_db`. Si tu MySQL local tiene clave o no está en el 3306, apuntalos a otro servidor. Por ejemplo, a un contenedor `mysql:8.0` con `MYSQL_ALLOW_EMPTY_PASSWORD=yes` publicado en el 3307:

```bash
cd backend
./mvnw test "-Dspring.datasource.url=jdbc:mysql://localhost:3307/veterinaria_test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&characterEncoding=UTF-8"
```

El frontend todavía no tiene test runner.

## Reglas de negocio

- **Guardería:** hay un máximo de 10 perros por día. Las reservas por rango se validan día por día.
  - La **Agenda guardería** tiene el mismo calendario mensual que la clínica. Cada mascota tiene su propio color, así una estadía de varios días se sigue a lo largo del mes.
  - La **seña** es parte de pago: al reservar se registra como un pago "Seña" en la comanda y se descuenta del total. No puede superar el total de la estadía.
  - **Plaza de urgencia clínica:** el Veterinario puede habilitar, para un día, una plaza extra destinada solo a una urgencia clínica. No suma a los 10 cupos ni la usan las reservas normales (la reserva 11 sigue dando 409). Solo el rol Veterinario la habilita, la deshabilita o interna una mascota (el resto recibe 403, incluido el Dueño); cualquier empleado ve su estado en la Agenda guardería. Para deshabilitarla tiene que estar libre.
- **Turnos clínicos:** cada turno dura 45 minutos de atención y le siguen 15 minutos de desinfección.
  - Los selectores de fecha se abren junto al campo sin salirse de la pantalla (en celular, como panel inferior) y la hora se elige en formato 24 h, de a 15 minutos.
  - La **Agenda clínica** muestra el mes como calendario, con los turnos de cada día. Al elegir un día, sus turnos aparecen al costado (debajo en celular).
  - Para un cliente que llega por primera vez, el modal "Agendar turno" tiene un botón "Cliente o mascota nuevos": permite cargar el cliente y su mascota en el mismo paso. Todo se guarda junto: si el horario choca con otro turno, no queda nada creado a medias.
  - **Turno de urgencia:** el interruptor "Turno de urgencia" del modal registra el turno para ahora, sin pedir cliente, mascota ni profesional, y puede superponerse con turnos agendados. Se ve en rojo en la Agenda. Al tocar **Atender** se elige el profesional y se puede cargar la ficha sin datos; la comanda queda sin titular y Caja no la deja cobrar hasta que se usa **Completar datos** (cliente nuevo o existente + mascota nueva o existente).
- **Ficha clínica sin dinero:**
  - La consulta registra anamnesis, diagnóstico, tratamiento, conducta y el **peso** del día (que pasa a ser el peso actual de la mascota). El alta de la mascota ya no pide el peso.
  - El cobro se genera como comanda para el mostrador.
  - La atención se registra solo desde la Agenda clínica: cada turno pendiente tiene el botón **Atender**, que abre la ficha ya vinculada al turno. Al finalizar, el turno queda como **Atendido**.
  - Un turno pendiente se puede **Cancelar** desde el panel del día de la Agenda clínica, con confirmación. Queda en la agenda tachado y su horario se libera para otro turno. Un turno atendido no se cancela (el backend responde 409): ya tiene ficha y comanda.
  - El profesional puede sumar **medicamentos e insumos** del catálogo del módulo **Medicamentos**: busca por nombre con la misma barra de búsqueda y los agrega con un click. El precio de catálogo se muestra solo como referencia; la comanda suma la consulta más los medicamentos y Caja muestra el detalle de cada línea.
  - El precio se carga al dar de alta el medicamento o se cambia desde la lista ("Precio"). Cualquier empleado puede fijarlo. Al guardar la ficha se descuenta el stock y se congela el precio vigente en la comanda: cambiar el catálogo no altera comandas ya emitidas. Sin stock suficiente, la atención se rechaza.
- **Tutores y mascotas:** la relación es N:M. Cada vínculo indica si ese tutor está autorizado a retirar a la mascota.
- **Los registros no se borran:**
  - Tutores, mascotas y profesionales se **desactivan**: sus fichas, turnos, cobros y deudas quedan guardados.
  - Al desactivar un tutor, se desactivan también sus mascotas que no tengan otro tutor activo.
  - Un registro desactivado no puede tomar turnos ni reservas nuevas. Se puede reactivar desde el Padrón o la lista de Profesionales, con el interruptor "Mostrar desactivados". Una mascota solo vuelve si tiene al menos un tutor activo.
- **Deuda por mascota:**
  - Se puede retirar una mascota cuando sus propias comandas están saldadas.
  - Tampoco se retira si tiene un turno clínico de ese día **sin atender**: hay que atenderlo o cancelarlo antes. La comanda de la consulta nace al atender, así que un turno pendiente no deja deuda que lo frene.
  - Si un tutor tiene dos mascotas y paga la deuda de una, esa sale aunque la otra siga debiendo.
  - Una comanda sin mascota cuenta como deuda del tutor, pero no retiene a ninguna mascota.
  - En el **Balance**, un cobro se marca como **deuda anterior** cuando el servicio es de un día previo a la jornada: el último día de la estadía o el de la atención, aunque se haya cargado hoy. Una comanda manual, sin servicio, usa su fecha de emisión.
- **Datos normalizados:**
  - El backend corrige el formato al guardar, así que el mostrador puede escribir los datos como le resulte cómodo (ver tabla).
  - La normalización se aplica en cada alta y en cada edición.

| Campo | Ejemplo de entrada | Se guarda |
|---|---|---|
| Nombre y apellido | `juan carlos` / `de la fuente` | `Juan Carlos` / `De la Fuente` |
| DNI (7 u 8 dígitos) | `30.123.456` | `30123456`, y la pantalla lo muestra como `30.123.456` |
| Teléfono (10 dígitos con código de área) | `+54 9 11 2345-6789` | `1123456789` |
| Matrícula | `m.p. 1234` | `MP 1234` |
| Email | ` Ana@Mail.COM ` | `ana@mail.com` |

El teléfono no puede llevar el `15` de celular: no se puede quitar sin conocer el largo del código de área. En ese caso el backend pide cargarlo sin el 15, por ejemplo `11 2345-6789`.

## Estructura

```
app/          páginas de Next.js (landing en /, login, sistema en /app)
components/   módulos de la UI (components/modules/*) y piezas compartidas
lib/          cliente de la API, sesión, utilidades de vista
backend/      API Spring Boot: controller -> service -> repository, con DTOs
odd/tasks/    documentos de cada feature (specs, tareas y decisiones)
```
