# Feature: correcciones-flujo-registros

Nueve correcciones del flujo: seña aplicada al cobro, alta de mascota desde Mostrador, atención solo desde la Agenda, peso en la atención, agenda de guardería propia, modales en lugar de diálogos nativos y desactivación en lugar de borrado.
Rama: `fix/correcciones-flujo-registros` · Tests: `./mvnw test` contra el contenedor del 3307, `npx tsc --noEmit` + `next build --webpack`.

## Specs

- **S1** — "la seña no se ve reflejada al momento del cobro." + "la seña debe guardarse como parte de pago y restarse al total."
- **S2** — "falta el boton de Cargar mascota en el mostrador."
- **S3** — "el modal ficha medica esta de mas. Ahora se atiende desde Agenda."
- **S4** — "el peso se debe pedir al momento de atender a la mascota, no al momento de registrarla."
- **S5** — "en el modal agendar turno, al momento de cargar una mascota nueva faltan pedir el dato de fecha de nacimiento."
- **S6** — "hay q separar agenda clinica y agenda guarderia. la agenda de guarderia debe ser igual que la agenda clinica. los turnos de guarderia deben mostrarse de distinto color por mascota."
- **S7** — "cambiar la contraseña muestra un alert nativo del buscador. hay q mostrarlo como modal." + corrección: "aca me confundi, el problema era en resetear contraseña."
- **S8** — "los registros son lo mas importante, el sistema no debe eliminar tutores/clientes/mascotas/profesionales, debe desactivarlas pero guardando sus registros historicos." + decisión: "me parece bien tu recomendacion" (al desactivar un tutor, se desactivan las mascotas que no tengan otro tutor activo; todo se puede reactivar de a uno).
- **S9** — "el panel de padron debe tener un toogle para esconder los tutores/clientes/mascotas desactivadas. al igual que la lista de profesionales"

## Tasks

| ID | Specs | Ruta | Estado | Commit |
|---|---|---|---|---|
| T1 | S8 | inline | hecho (RED 8/8, 279 verdes) | ef74b16 |
| T2 | S1 | inline | hecho (RED 3/4, 283 verdes) | 1334645 |
| T3 | S4 | inline | hecho (RED x2; peso como Double por FichaClinicaDesacopleTest) | eaeea4b |
| T4 | S5 | inline | hecho | dd02376 |
| T5 | S3 | inline | hecho | a33bdf8 |
| T6 | S2 | inline | hecho | b9abd95 |
| T7 | S6 | inline | hecho (RED 2/3, 287 verdes) | 41b3e99, dc7109e, 01467f5 |
| T8 | S7 | inline | hecho | eddab23 |
| T9 | S8, S9 | inline | hecho | d586ccc |
| T10 | — | inline | hecho | este commit |

## Log

- **L1** (2026-11-02, pedido original): "hay varios errores a corregir: 1. la seña no se ve reflejada al momento del cobro. 2. falta el boton de Cargar mascota en el mostrador. 3. el modal ficha medica esta de mas. Ahora se atiende desde Agenda. 4. el peso se debe pedir al momento de atender a la mascota, no al momento de registrarla. 5. en el modal agendar turno, al momento de cargar una mascota nueva faltan pedir el dato de fecha de nacimiento. 6. hay q separar agenda clinica y agenda guarderia. la agenda de guarderia debe ser igual que la agenda clinica. los turnos de guarderia deben mostrarse de distinto color por mascota. 7. cambiar la contraseña muestra un alert nativo del buscador. hay q mostrarlo como modal. 8. los registros son lo mas importante, el sistema no debe eliminar tutores/clientes/mascotas/profesionales, debe desactivarlas pero guardando sus registros historicos. 9. el panel de padron debe tener un toogle para esconder los tutores/clientes/mascotas desactivadas. al igual que la lista de profesionales"
- **L2** (respuestas): "1. la seña debe guardarse como parte de pago y restarse al total. 7. aca me confundi, el problema era en resetear contraseña. 8. me parece bien tu recomendacion"
- **L3** (hallazgos): la seña se guarda en `ReservaGuarderia.sena` pero nunca se aplica como `Pago` a la comanda. Hoy `TutorServiceImpl.eliminar` borra tutor, vínculos y mascotas huérfanas; `MascotaServiceImpl.eliminar` y `ProfesionalServiceImpl.eliminar` borran de verdad. `Profesional` ya tiene `activo`; `Tutor` y `Mascota` no. Los `window.confirm` están en `usuarios.tsx` (activar/desactivar, resetear, eliminar).
- **L4** (supuestos):
  - S8: los endpoints DELETE de tutor, mascota y profesional pasan a desactivar (contrato cambiado, pedido explícito); se agrega reactivar. Un tutor, mascota o profesional inactivo no puede tomar turnos ni reservas nuevas (400) y no se ofrece en los selectores, pero sus fichas, comandas e historial siguen visibles. Los listados devuelven todo con `activo` y el frontend filtra con el toggle. Usuarios (cuentas de empleados) no entra en S8.
  - S1: la seña se registra como un `Pago` con método SENA en la comanda de la reserva, en el momento de reservar; el saldo pendiente queda total − seña. Si la seña supera el total, 400.
  - S4: la ficha registra el peso del día (opcional) y actualiza el peso actual de la mascota; el alta y la edición de la mascota dejan de pedirlo.
  - S6: la pestaña Agenda pasa a "Agenda clínica" y se agrega "Agenda guardería" con el mismo calendario; cada mascota tiene un color estable derivado de su id.
- **L5** (e2e con ego-browser sobre la base descartable `veterinaria_e2e`, borrada al terminar): menú sin Ficha médica y con Agenda clínica / Agenda guardería; guardería muestra a Luna del 3 al 5 y a Rocco el 4 con colores distintos; Caja: comanda de Luna $20.000 con seña $5.000 imputada y saldo $15.000; Mostrador → Cargar mascota (sin campo de peso) crea a Michi; Agendar turno con mascota nueva pide fecha de nacimiento; Atender con peso "14,3kg" guarda 14.3; desactivar a Luis desactiva a Rocco y Michi, toggle "Mostrar desactivados (3)", reactivar Rocco antes que Luis da 400 con mensaje; Profesionales desactivar/toggle/reactivar; Usuarios → Resetear contraseña abre modal propio (sin diálogo nativo); 375 px sin scroll horizontal. Bug encontrado y corregido: al separar la agenda de guardería se había perdido el modal de Agendar turno (01467f5).
- **L6** (riesgo): ítem 1 (columna `activo` nueva en `tutor` y `mascota` con default 1; `peso_kg` nuevo en `consulta_medica`; la edición de mascota ya no pisa el peso con null) e ítem 3 (DELETE de tutor/mascota/profesional cambia de borrar a desactivar: pedido explícito, S8).
