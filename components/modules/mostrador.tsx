"use client"

/**
 * Mostrador: movimiento de la jornada y alta de clientes y turnos.
 *
 * <p>TANDA 2: el tablero ya no lee nada de `lib/store.tsx`. Todo lo que se ve acá
 * sale de la base:</p>
 *
 * <ul>
 *   <li><code>GET /api/v1/turnos?fecha=</code> — columna "Turnos".</li>
 *   <li><code>GET /api/v1/guarderia?fecha=</code> — columnas "Ingresan" y
 *       "Se retiran". Es la misma lista partida en dos: una reserva entra por su
 *       hora de ingreso y sale por su hora de salida.</li>
 *   <li><code>GET /api/v1/guarderia/aforo?fecha=</code> — panel de ocupación, en
 *       vez de `ocupadosPorDia()` del store.</li>
 *   <li><code>GET /api/v1/egreso/pacientes-hoy?fecha=</code> — una sola llamada
 *       que trae la conducta de cada mascota presente y sus tutores: de ahí salen
 *       los avisos de manejo y la lista de tutores del día. No se agregó ningún
 *       endpoint.</li>
 * </ul>
 *
 * <p><b>Por qué el componente ya no importa el store.</b> Antes depended de
 * `turnos`, `reservas`, `dogs`, `owners`, `cupoMax` y `ocupadosPorDia`, todos
 * semillas en memoria. Consecuencia concreta que motivó el cambio: un turno
 * creado desde el botón "Agregar turno" aparecía en Agenda (que sí lee la base) y
 * <b>no</b> en el tablero de arriba, y al recargar la página el tablero mostraba
 * los datos de demostración de nuevo.</p>
 *
 * <p><b>Dos modales, contra la API real (HOTFIX-UI).</b> "Cargar cliente" crea
 * tutor + mascota y los vincula; "Agregar turno" hace `POST /api/v1/turnos`. Los
 * dos avisan con `onGuardado` para que el tablero se vuelva a pedir.</p>
 *
 * <p>La fecha de la jornada sale de `hoyLocal()`, en hora local y no con
 * `toISOString()`, que entre las 21:00 y las 24:00 devolvería mañana.</p>
 *
 * <p><b>Orquestador.</b> Este archivo conserva las cuatro llamadas con fecha y el
 * `refrescar` común; el resto quedó en `components/modules/mostrador/`. El reparto
 * deja acá el único dueño de la fecha: los componentes hijos son presentacionales
 * y reciben el estado ya pedido, así que la clave de recarga y la invalidación
 * post-alta siguen siendo una sola.</p>
 */

import { useCallback, useState } from "react"
import { UserPlus, CalendarPlus, CalendarDays } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Field, TextInput } from "@/components/ui/field"
import {
  egreso as apiEgreso,
  guarderia as apiGuarderia,
  turnos as apiTurnos,
  hoyLocal,
} from "@/lib/api"
import type {
  AforoGuarderia,
  MascotaDelDia,
  ReservaGuarderia,
  TurnoClinico,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { TOQUE_MINIMO } from "@/lib/vista"

import { AgregarTurnoModal } from "./mostrador/agregar-turno-modal"
import { AvisosImportantes } from "./mostrador/avisos-importantes"
import { CargarClienteModal } from "./mostrador/cargar-cliente-modal"
import { TableroJornada } from "./mostrador/tablero-jornada"
import { WidgetAforo } from "./mostrador/widget-aforo"
import { useDeudasDelDia } from "./mostrador/use-deudas-del-dia"

export function Mostrador() {
  /**
   * Día del tablero.
   *
   * <p>Es estado propio y no el `today` del store: el store quedó con la fecha de
   * "hoy" y este tablero necesita poder mirar otro día. El selector va arriba, no
   * escondido, porque el mostrador también necesita ver la guardia de mañana para
   * preparar los ingresos.</p>
   */
  const [fecha, setFecha] = useState(hoyLocal())
  const [openCliente, setOpenCliente] = useState(false)
  const [openTurno, setOpenTurno] = useState(false)

  // Las cuatro llamadas llevan la fecha como `clave` y no solo como closure:
  // `useApi` guarda la fn en un ref, así que sin la clave cambiar de día no
  // dispararía nada y la grilla mostraría el día anterior como si fuera el
  // pedido. Ver la nota de `clave` en `lib/use-api.ts`.
  const turnos = useApi<TurnoClinico[]>(() => apiTurnos.listar(fecha), true, fecha)
  const reservas = useApi<ReservaGuarderia[]>(() => apiGuarderia.listar(fecha), true, fecha)
  const aforo = useApi<AforoGuarderia>(() => apiGuarderia.aforo(fecha), true, fecha)
  const jornada = useApi<MascotaDelDia[]>(() => apiEgreso.pacientesDelDia(fecha), true, fecha)

  const deudas = useDeudasDelDia(jornada.datos, fecha)

  /**
   * Relee el tablero después de una mutación hecha desde esta misma pantalla.
   *
   * <p>Es la unica invalidacion necesaria: los modulos se montan y desmontan al
   * cambiar de pestaña (`app/page.tsx`), así que un turno creado desde Agenda se ve
   * solo al volver al Mostrador. Acá, en cambio, el componente no se desmonta nunca,
   * y sin este `recargar` el tablero quedaría mostrando la jornada de antes del
   * alta.</p>
   */
  const refrescar = useCallback(async () => {
    await Promise.all([turnos.recargar(), reservas.recargar(), aforo.recargar(), jornada.recargar()])
  }, [turnos, reservas, aforo, jornada])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">Mostrador</h1>
          <p className="text-sm text-muted-foreground">
            Movimiento del {fecha === hoyLocal() ? "día de hoy" : fecha} · {fecha}
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <Field label="Día del tablero">
            <TextInput
              type="date"
              value={fecha}
              onChange={(e) => setFecha(e.target.value || hoyLocal())}
              className={TOQUE_MINIMO}
              aria-label="Día del tablero"
            />
          </Field>
          {fecha !== hoyLocal() && (
            <Button variant="outline" onClick={() => setFecha(hoyLocal())} type="button">
              <CalendarDays className="h-4 w-4" /> Hoy
            </Button>
          )}
          <Button variant="outline" onClick={() => setOpenCliente(true)}>
            <UserPlus className="h-4 w-4" /> Cargar cliente
          </Button>
          <Button onClick={() => setOpenTurno(true)}>
            <CalendarPlus className="h-4 w-4" /> Agregar turno
          </Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <WidgetAforo aforo={aforo} fecha={fecha} />
        <AvisosImportantes jornada={jornada} deudas={deudas} fecha={fecha} />
      </div>

      <TableroJornada turnos={turnos} reservas={reservas} fecha={fecha} onReintentar={refrescar} />

      <CargarClienteModal open={openCliente} onClose={() => setOpenCliente(false)} onGuardado={refrescar} />
      <AgregarTurnoModal open={openTurno} onClose={() => setOpenTurno(false)} onGuardado={refrescar} />
    </div>
  )
}
