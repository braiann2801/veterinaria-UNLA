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

   Hay dos variables opcionales:
   - `DB_PORT`: puerto del host donde se publica MySQL. Por defecto es `3306`; usá `3307` si ya tenés otro MySQL en el 3306.
   - `NEXT_PUBLIC_API_URL`: URL de la API que usa el navegador. Por defecto es `http://localhost:8080/api/v1`.

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

Balance, Usuarios, cierre de caja y tarifas son solo para el Dueño, y el backend responde 403 al resto. La matriz de permisos vive en `backend/src/main/java/com/veterinaria/veterinaria_backend/security/SecurityConfig.java`.

## Desarrollo local sin Docker

Necesitás un MySQL 8 en `localhost:3306` con usuario `root` sin clave, o definir `DB_HOST`, `DB_PORT`, `DB_USERNAME` y `DB_PASSWORD`. La base se crea sola.

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
- **Turnos clínicos:** cada turno dura 45 minutos de atención y le siguen 15 minutos de desinfección.
  - La Agenda muestra el mes como calendario, con los turnos de cada día. Al elegir un día, sus turnos aparecen al costado (debajo en celular).
  - Para un cliente que llega por primera vez, el modal "Agendar turno" tiene un botón "Cliente o mascota nuevos": permite cargar el cliente y su mascota en el mismo paso. Todo se guarda junto: si el horario choca con otro turno, no queda nada creado a medias.
- **Ficha clínica sin dinero:**
  - La consulta registra anamnesis, diagnóstico, tratamiento y conducta.
  - El cobro se genera como comanda para el mostrador.
  - En la Agenda, cada turno pendiente tiene el botón **Atender**: abre la ficha ya vinculada al turno. Al finalizar, el turno queda como **Atendido**.
  - El profesional puede sumar **extras** (vacunas, urgencias, insumos) del catálogo que arma el Dueño en **Tarifas**. No ve precios: la comanda suma la consulta más los extras, y Caja muestra el detalle de cada línea.
  - Cambiar el precio de un extra rige para las atenciones nuevas; las comandas ya emitidas conservan su precio. Un extra no se borra, se desactiva.
- **Tutores y mascotas:** la relación es N:M. Cada vínculo indica si ese tutor está autorizado a retirar a la mascota.
- **Deuda por mascota:**
  - Se puede retirar una mascota cuando sus propias comandas están saldadas.
  - Si un tutor tiene dos mascotas y paga la deuda de una, esa sale aunque la otra siga debiendo.
  - Una comanda sin mascota cuenta como deuda del tutor, pero no retiene a ninguna mascota.
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
