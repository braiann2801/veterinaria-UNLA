# Seguridad de entradas y permisos

> Tarea: verificar que los datos del login no permitan inyección SQL ni otras
> manipulaciones, y que ningún campo sensible se pueda cambiar sin permiso.
> Fecha: 2026-11-16. Rama: `featur-cambios`.

## Alcance revisado

- Todo el backend (`controller`, `service`, `repository`, `security`, `dto`).
- Los 18 DTOs de entrada.
- La matriz de permisos de `SecurityConfig`.
- El frontend en lo que respecta a datos que viajan al backend.

## Resultado de la auditoría

### Inyección SQL: no hay superficie

- Solo existen dos consultas nativas y las dos usan parámetros nombrados:
  - `ControlAforoDiarioRepository` (bloqueo de aforo).
  - `ComandaCobroRepository#buscarParaActualizar` (`WHERE id = :id FOR UPDATE`).
- El resto son JPQL con `:parametros` o consultas derivadas de Spring Data.
- No hay concatenación de strings en SQL, ni `String.format` con valores de
  usuario, ni `EntityManager.createQuery` con texto armado.
- Verificado en vivo con cargas `' OR 1=1--`, `' UNION SELECT`, `"; DROP TABLE`,
  `' OR SLEEP(5)--` en usuario y clave: todas responden `401` y la tabla
  `usuario` queda intacta (test `EntradaSeguraTest#inyeccionSqlNoEntra`).

### Escalada por manipular el cuerpo o el inspector

- El rol no viaja en el token: `UsuarioJwtConverter` lo lee de la base en cada
  request. Cambiar `rol` en el JSON del login, o editar `usuario.rol` en
  `localStorage`, no cambia ninguna autorización (`403`).
- El token está firmado (HS256) y lleva `ver` = `tokenVersion`; alterar el
  payload invalida la firma, y un cambio de rol/clave/estado sube la versión y
  cierra las sesiones.
- Los DTOs de entrada no tienen campos de estado ni de saldo: `activo`,
  `debeCambiarPassword`, `passwordHash` y `tokenVersion` se ignoran aunque se
  manden (test `EntradaSeguraTest#actualizarUsuarioNoAceptaCamposSensibles`).
- La urgencia de guardería no se puede pedir por API: `ReservaGuarderiaRequestDTO`
  no tiene el campo y el servicio lo fija solo en `ocuparPlazaUrgencia`
  (endpoint exclusivo de Veterinario).
- La alerta de conducta no se recibe: se deriva de `conductaObservada`.
- El fin del turno y la ventana de desinfección no se reciben: los deriva el
  dominio a partir de `fechaHoraInicio`.

### Permisos (revisados en `SecurityConfig`)

| Recurso | Rol |
|---|---|
| `/api/v1/auth/login` | público |
| `/api/v1/auth/me`, `/cambiar-password` | autenticado (aun con cambio pendiente) |
| `/api/v1/usuarios/**` | DUENIO |
| `/api/v1/caja/cierre` | DUENIO (sin restricción de método, para que HEAD no lo saltee) |
| `PUT /comandas/tarifas/**`, `PUT /guarderia/tarifas` | DUENIO |
| `POST/PUT /extras/**` | DUENIO |
| `PUT/DELETE/POST /guarderia/urgencia/**` | VETERINARIO |
| Resto de `/api/**` | cualquier empleado con la clave cambiada |

`anyRequest().authenticated()` cierra lo que no esté listado. Los tests de
permisos usan tokens reales, no `@WithMockUser`.

## Correcciones aplicadas

1. **`LoginRequestDTO` con topes**: `username` ≤ 40 (columna `varchar(40)`) y
   `password` ≤ 72. Antes un cuerpo de miles de caracteres llegaba hasta la
   consulta y hasta BCrypt.
2. **Control de bytes en `AuthServiceImpl.login`**: BCrypt solo considera 72
   bytes. `@Size` cuenta caracteres, y un acento ocupa más de un byte, así que
   una clave de 40 `ñ` (80 bytes) pasaba la validación y se recortaba en
   silencio. Ahora responde `400` antes de consultar y de hashear.
3. **`CambioPasswordRequestDTO.actual` con tope de 72**: evita gastar BCrypt
   sobre una clave gigante en el cambio de contraseña.
4. **Body ilegible sin detalle interno**: `GlobalExceptionHandler` ya no
   devuelve `ex.getMessage()` (traía paquetes, clases y fragmentos del JSON).
   El detalle va al log; el cliente recibe un mensaje fijo. Se separó del error
   de tipo en query param, que ahora responde `400` nombrando solo el parámetro.
5. **CORS**: orígenes configurables con `CORS_ORIGENES` (default cumple
   AGENTS.md: 3000 local + `*.vercel.app`) y `allowCredentials(false)`. El JWT
   viaja en `Authorization`, no en cookies, así que no hacían falta credenciales
   y se elimina la combinación más riesgosa de CORS.
6. **Tarifa**: `@Valid` + `@NotNull` + `@DecimalMin("0.00")` en el body (el
   servicio ya rechazaba nulo/negativo; ahora el contrato lo declara).
7. **Mensajes de error propios**: `MensajePublicoException` permite que el
   deserializador de fechas siga explicando qué formatos acepta, mientras el
   resto de los errores de deserialización queda genérico.
8. **Frontend**: `maxLength` en usuario (40) y contraseñas (72), para que el
   navegador no mande lo que el backend va a rechazar.

## Riesgos que quedan (recomendaciones, no bloqueantes)

- **Token en `localStorage`**: un XSS podría robarlo. Mitigación real: CSP
  estricta y, si se quiere eliminar el vector, pasar a cookie `httpOnly`
  + CSRF token.
- **Sin CSP ni cabeceras de seguridad** en Next.js (`X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy`).
- **`JWT_SECRET` sin definir en local**: clave aleatoria por arranque (sesiones
  caen al reiniciar). En producción es obligatoria (el arranque falla si mide
  menos de 32 bytes).
- **Throttle en memoria y por usuario+IP**: un atacante con muchas IPs puede
  hacer *password spraying* lento; el límite por par no lo frena. Para producción
  conviene un contador compartido (Redis) y un límite por IP.
- **`CORS_ORIGENES`**: fijar la URL exacta del deploy en Vercel en vez de dejar
  el comodín.
- **`/auth/login` acepta números/booleanos donde se espera texto**: Jackson los
  convierte. No es explotable (igual se compara como string), pero se podría
  endurecer con `allow-coercion-of-scalars=false` si se quiere contrato estricto.
- **`PagoRequestDTO.monto`** no tiene tope superior; el servicio ya rechaza
  pagos mayores al saldo, así que no hay impacto contable.
- **`EgresoRegistroRequestDTO.fecha`** acepta fechas futuras. No permite saltar
  controles (la autorización y la deuda se validan para esa misma fecha), pero
  un egreso no debería registrarse antes de ocurrir; los tests usan fechas de
  2031 como fixture, así que restringirlo requiere rehacer esos datos.

## Verificación

- `EntradaSeguraTest` (7 tests): SQLi, tamaños, bytes UTF-8, campos extra,
  `PUT /usuarios` con campos sensibles, tarifa, body ilegible y query param
  inválido.
- `.\test-harness.ps1` completo: `tsc --noEmit`, `next build --webpack` y toda
  la suite de backend.
