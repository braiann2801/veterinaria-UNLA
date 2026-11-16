# Feature: balance-periodo

Balance por período: el cierre de caja deja de mirar solo un día y permite elegir
un rango de fechas (dos fechas en el calendario o un atajo: hoy, últimos 7 días,
este mes, mes anterior, este año).

Rama: `featur-cambios` · Verificación: `test-harness.ps1` (tsc + Next build + tests
Spring contra MySQL local).

## Specs

- **S1** — "en la parte del balance, me gustaría tener un botón que diga
  seleccionar período". El botón abre un calendario de dos fechas.
- **S2** — "dentro del calendario me debería dejar marcar dos fechas". Rango
  inclusivo; el día máximo es hoy (el Balance no muestra datos futuros).
- **S3** — "esto debería efectuar una consulta en la base de datos, que va a
  consultar el período que yo seleccione": el backend agrega en MySQL por rango
  semiabierto, no filtra filas en el navegador.
- **S4** — "desde el 12 de octubre hasta el 19, o directamente un mes completo, o
  un año". Atajos además del rango manual; tope de un año (366 días).
- **S5** — "para poder ver cómo venimos en balance": serie diaria de recaudación
  y comparación facturado vs. cobrado del período.

## Decisiones

- **Período inicial: hoy.** Se conserva el cierre diario al entrar; el botón
  cambia a rango. (Elegido por el usuario.)
- **Cobrado ≠ facturado.** Cobrado se fecha por `pago.fechaHora`; facturado por
  `comanda.fechaEmision`. La UI lo aclara para que la diferencia no se lea como
  un error.
- **Por cobrar** es el saldo actual de las comandas emitidas en el período, no
  una foto histórica: el sistema no guarda snapshots de deuda.
- **Serie completa.** El backend completa con cero los días sin cobros; hasta dos
  meses se dibuja una barra por día, más allá se agrupa por mes.
- **Extracto por lotes.** "Ver más" agrega 50 movimientos por click: un año puede
  traer miles de filas y el celular no debe armar la lista entera.
- **`?fecha=` se conserva** como alias de un día, para no romper clientes ni los
  tests existentes.

## Tasks

| ID | Specs | Ruta | Estado |
|---|---|---|---|
| T1 | S3, S4 | `PagoRepository`, `ComandaCobroRepository`, `CajaServiceImpl`, `CajaController` | hecho |
| T2 | S1, S2 | `lib/use-panel-flotante.ts`, `components/ui/rango-fechas.tsx` | hecho |
| T3 | S1, S5 | `components/modules/balance.tsx`, `lib/api.ts` | hecho |
| T4 | — | tests `BalancePorPeriodoTest`, docs | hecho |

## Log

- **L1** (2026-11-16, pedido): "en la parte del balance, me gustaría tener un
  botón que diga seleccionar período... me debería dejar marcar dos fechas...
  consultar el período que yo seleccione... desde el 12 de octubre hasta el 19, o
  directamente un mes completo, o un año... las finanzas son de lo más
  importante."
- **L2**: período inicial = hoy; métricas = serie diaria + facturado vs cobrado;
  tope = 1 año / 366 días.
- **L3**: `DatePicker` y el selector de período comparten `usePanelFlotante`
  (portal, posicionamiento, click afuera, Escape) para no duplicar la mecánica
  móvil de hoja inferior.
