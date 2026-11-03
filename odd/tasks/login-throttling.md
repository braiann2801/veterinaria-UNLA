# Feature: login-throttling

Límite de intentos fallidos en `POST /api/v1/auth/login` contra fuerza bruta.
Rama: `feat/login-throttling` · Tests: `./mvnw test` contra el contenedor del 3307, `npx tsc --noEmit` + `next build --webpack`.

## Specs

- **S1** — "POST /api/v1/auth/login no tiene throttling: permite probar contraseñas por fuerza bruta. Bloqueo temporal por usuario/IP tras N intentos fallidos. Definir N y ventana; responder 429 con el contrato ApiErrorResponse."
- **S2** — "Tras N intentos fallidos, el login responde 429 con el contrato ApiErrorResponse"
- **S3** — "El bloqueo expira automáticamente al cumplirse la ventana definida"
- **S4** — "Un login exitoso resetea el contador de intentos"
- **S5** — "Tests de backend cubren el bloqueo y el desbloqueo"

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S1-S5 | inline | hecho (RED 4/6 observado; 293 tests en verde) | fa282f5 |
| T2 | S2 | inline | hecho sin cambios de código: el cliente ya muestra `mensaje` (e2e verificado) | — |
| T3 | — | inline | hecho | este commit |

## Log

- **L1** (2026-11-03, ticket del usuario, verbatim): "POST /api/v1/auth/login no tiene throttling: permite probar contraseñas por fuerza bruta / Bloqueo temporal por usuario/IP tras N intentos fallidos. Definir N y ventana; responder 429 con el contrato ApiErrorResponse. / Tras N intentos fallidos, el login responde 429 con el contrato ApiErrorResponse / El bloqueo expira automáticamente al cumplirse la ventana definida / Un login exitoso resetea el contador de intentos / Tests de backend cubren el bloqueo y el desbloqueo"
- **L2** (decisiones, el ticket pide definirlas):
  - N = 5 intentos fallidos; ventana = 15 minutos. Configurables con `veterinaria.auth.login-intentos-maximos` y `veterinaria.auth.login-bloqueo-minutos` (variables `LOGIN_INTENTOS_MAXIMOS`, `LOGIN_BLOQUEO_MINUTOS`).
  - La clave es el par (usuario normalizado, IP). Un bloqueo por usuario solo permitiría que cualquiera deje sin acceso a un empleado desde otra IP; por IP sola bloquearía a todo un local detrás de la misma conexión.
  - Los fallos cuentan dentro de una ventana que arranca en el primer fallo; si pasan 15 minutos sin llegar a 5, el contador vuelve a cero. Al quinto fallo el par queda bloqueado 15 minutos; mientras dura, el login responde 429 sin verificar la contraseña (ni siquiera la correcta pasa).
  - La IP es `request.getRemoteAddr()`; no se confía en `X-Forwarded-For` porque el cliente lo puede falsificar.
  - El bloqueo vive en memoria del backend: un reinicio lo limpia y con varias instancias cada una cuenta por separado. Aceptable para una sola instancia.
  - El 429 aplica igual exista o no la cuenta, así que no revela qué usuarios existen.
- **L3** (e2e con ego-browser, base descartable `veterinaria_e2e` borrada al terminar): 5 intentos con clave mala muestran "Usuario o contraseña incorrectos"; el sexto, con la clave correcta, muestra "Demasiados intentos de ingreso fallidos. Probá de nuevo en 15 minutos." y no entra.
- **L4** (riesgo): ítem 2 (seguridad: login) e ítem 4 (concurrencia: contador compartido, `ConcurrentHashMap.compute` atómico por clave).
