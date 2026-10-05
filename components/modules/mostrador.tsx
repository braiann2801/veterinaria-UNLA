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
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { AlertTriangle, UserPlus, CalendarPlus, LogIn, LogOut, CalendarDays } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Panel, Field, TextInput, Select, TextArea } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import {
  egreso as apiEgreso,
  guarderia as apiGuarderia,
  mascotas as apiMascotas,
  profesionales as apiProfesionales,
  tutores as apiTutores,
  turnos as apiTurnos,
  hoyLocal,
} from "@/lib/api"
import type {
  AforoGuarderia,
  DeudaTutor,
  EspecieMascota,
  Mascota,
  MascotaDelDia,
  Profesional,
  ReservaGuarderia,
  Tutor,
  TurnoClinico,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Cargando, ErrorBox, Recurso, TOQUE_MINIMO, money } from "@/lib/vista"

/** Especies del enum `Mascota.Especie` del backend, con la etiqueta que ve el operador. */
const ESPECIES: { valor: EspecieMascota; etiqueta: string }[] = [
  { valor: "CANINO", etiqueta: "Canino" },
  { valor: "FELINO", etiqueta: "Felino" },
  { valor: "AVE", etiqueta: "Ave" },
  { valor: "ROEDOR", etiqueta: "Roedor" },
  { valor: "REPTIL", etiqueta: "Reptil" },
  { valor: "OTRO", etiqueta: "Otro" },
]

/**
 * Conductas de la **mascota** (`MascotaRequestDTO.conducta`).
 *
 * No son las de `ConsultaMedica.conductaObservada` (TRANQUILO / NERVIOSO /
 * REACTIVO / AGRESIVO), que describen cómo se portó el animal en una atención
 * concreta. La columna de la mascota es NORMAL, GRUNE, MUERDE, MIEDOSO: mandar
 * un valor del otro enum devuelve un 400.
 */
const CONDUCTAS: { valor: "normal" | "gruñe" | "muerde" | "miedoso"; etiqueta: string }[] = [
  { valor: "normal", etiqueta: "Normal" },
  { valor: "gruñe", etiqueta: "Gruñe" },
  { valor: "muerde", etiqueta: "Muerde" },
  { valor: "miedoso", etiqueta: "Miedoso" },
]

/**
 * Conductas que encienden el aviso de manejo.
 *
 * "Miedoso" y "normal" no avisan: no son un riesgo para el manejo. El backend
 * manda la grafía con eñe (`EntityMapper.conductaToApi`), que es la que comparan
 * estas constantes.
 */
const CONDUCTAS_DE_RIESGO = new Set(["muerde", "gruñe"])

/**
 * Reservas que ocupan plaza ese día.
 *
 * Es el mismo criterio que usa el backend en `JornadaDelDiaService`: FINALIZADO y
 * CANCELADO liberan el cupo, así que no pueden aparecer como "ingresa hoy" ni como
 * "se retira hoy". Copiarlo acá evita que el tablero y el aforo discrepen sobre la
 * misma fecha.
 */
const RESERVAS_QUE_OCUPAN = new Set(["RESERVADO", "INGRESADO"])

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

  const listaTurnos = turnos.datos ?? []
  const cancelados = listaTurnos.filter((t) => t.estado === "CANCELADO")
  const turnosVigentes = listaTurnos.filter((t) => t.estado !== "CANCELADO")

  const reservasQueOcupan = (reservas.datos ?? []).filter((r) => RESERVAS_QUE_OCUPAN.has(r.estado))
  const ingresan = reservasQueOcupan.filter((r) => r.horaEntrada !== null)
  const seRetiran = reservasQueOcupan.filter((r) => r.horaSalida !== null)

  // Avisos de manejo: la conducta viene en la jornada, ya filtrada por los tutores
  // que realmente pasaron. Antes se cruzaban las semillas del store.
  const avisosConducta = (jornada.datos ?? []).filter((m) => m.conducta && CONDUCTAS_DE_RIESGO.has(m.conducta))
  const avisosDeuda = (jornada.datos ?? [])
    .flatMap((m) => m.tutores.map((t) => ({ tutorId: t.tutorId, nombreCompleto: t.nombreCompleto })))
    .filter((t, i, todos) => todos.findIndex((o) => o.tutorId === t.tutorId) === i)
    .map((t) => ({ ...t, deuda: deudas.porTutor[t.tutorId] }))
    .filter((t) => t.deuda?.tieneDeuda)

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
        <Panel title="Ocupación guardería" className="lg:col-span-1">
          <Recurso
            estado={aforo}
            textoCarga="Consultando el aforo"
            onReintentar={aforo.recargar}
          >
            {(datos) => (
              <>
                <p className="text-3xl font-bold">
                  {datos.cuposOcupados}{" "}
                  <span className="text-base font-normal text-muted-foreground">
                    de {datos.aforoMaximo} ocupados
                  </span>
                </p>
                <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className={`h-full rounded-full transition-all ${datos.completo ? "bg-destructive" : "bg-foreground"}`}
                    style={{
                      width: `${Math.min(100, (datos.cuposOcupados / datos.aforoMaximo) * 100)}%`,
                    }}
                  />
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  {datos.cuposDisponibles} lugares disponibles el {fecha}
                </p>
              </>
            )}
          </Recurso>
        </Panel>

        <Panel title="Avisos importantes" className="lg:col-span-2">
          {/*
            La condicion mira solo `jornada.error` a proposito. Los dos avisos de
            esta panel salen de `GET /egreso/pacientes-hoy`: el de manejo, de la
            columna `conducta`; el de morosidad, de los tutores que vienen ahi. Si
            `turnos` o `reservas` fallan, la grilla de abajo ya lo avisa por su
            cuenta y tapar estos avisos por eso dejaria al operador sin ver un
            perro que muerde.
          */}
          {jornada.error ? (
            <ErrorBox error={jornada.error} onReintentar={jornada.recargar} />
          ) : (
            <>
              {/* While the debt query is in flight, it must not say "no debt". */}
              {deudas.cargando && (
                <p className="mb-2 text-sm text-muted-foreground">
                  Consultando la deuda de los tutores del {fecha}…
                </p>
              )}
              {avisosConducta.length === 0 && avisosDeuda.length === 0 && !deudas.cargando ? (
                <p className="text-sm text-muted-foreground">
                  Sin avisos de manejo ni de morosidad para el {fecha}.
                </p>
              ) : (
                (avisosConducta.length > 0 || avisosDeuda.length > 0) && (
                <ul className="flex flex-col gap-2">
                  {avisosConducta.map((m) => (
                    <li
                      key={`c-${m.mascotaId}`}
                      className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
                    >
                      <AlertTriangle className="h-4 w-4 shrink-0" />
                      <span>
                        <strong>{m.nombreMascota}</strong>{" "}
                        {m.conducta === "muerde" ? "muerde" : "gruñe"} — manejar con cuidado.
                      </span>
                    </li>
                  ))}
                  {avisosDeuda.map((t) => (
                    <li
                      key={`d-${t.tutorId}`}
                      className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
                    >
                      <AlertTriangle className="h-4 w-4 shrink-0" />
                      <span>
                        <strong>{t.nombreCompleto}</strong> viene con deuda previa de{" "}
                        {money(t.deuda?.totalDeuda ?? 0)}.
                      </span>
                    </li>
                  ))}
                </ul>
                )
              )}

              {/*
                Un fallo parcial en la consulta de deudas se dice. La alternativa
                —mostrar "sin avisos" como si nada— convertiría un error de red en
                una afirmación falsa de que nadie debe nada, que es el peor
                resultado posible en la pantalla de morosidad (regla 2.5).
              */}
              {deudas.fallidos > 0 && (
                <p className="mt-2 rounded-md border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900">
                  No se pudo consultar la deuda de {deudas.fallidos}{" "}
                  {deudas.fallidos === 1 ? "tutor" : "tutores"}: puede faltar algún aviso de
                  morosidad.{" "}
                  <button type="button" className="underline" onClick={deudas.recargar}>
                    Reintentar
                  </button>
                </p>
              )}
            </>
          )}
        </Panel>
      </div>

      <Panel
        title={
          turnos.cargando || reservas.cargando
            ? "Turnos y estadías"
            : `Turnos y estadías del ${fecha}`
        }
      >
        {turnos.cargando && reservas.cargando && turnos.datos === null && reservas.datos === null ? (
          <Cargando texto="Consultando la jornada" />
        ) : turnos.error || reservas.error ? (
          <ErrorBox error={turnos.error ?? reservas.error ?? null} onReintentar={refrescar} />
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            <TurnoCol
              title="Turnos"
              nota={
                cancelados.length > 0
                  ? `${cancelados.length} cancelado${cancelados.length === 1 ? "" : "s"} fuera de la lista`
                  : undefined
              }
              items={turnosVigentes.map((t) => ({
                id: String(t.id),
                top: `${t.mascota.nombre} · ${t.fechaHoraInicio.slice(11, 16)}`,
                sub: `${t.profesional.nombreCompleto} · ${t.motivo}`,
              }))}
              icon="turno"
            />
            <TurnoCol
              title="Ingresan (guardería)"
              items={ingresan.map((r) => ({
                id: `in-${r.id}`,
                top: r.nombreMascota,
                sub: `Entra ${hhmm(r.horaEntrada)} · sale ${hhmm(r.horaSalida)}`,
              }))}
              icon="in"
            />
            <TurnoCol
              title="Se retiran"
              items={seRetiran.map((r) => ({
                id: `out-${r.id}`,
                top: r.nombreMascota,
                sub: `Sale ${hhmm(r.horaSalida)}`,
              }))}
              icon="out"
            />
          </div>
        )}

        <p className="mt-3 text-xs text-muted-foreground">
          Las dos columnas de guardería son las reservas <strong>de esa fecha</strong>: una
          reserva que empezó ayer y termina hoy no aparece acá, porque el endpoint
          filtra por la fecha de la reserva y no por la hora de salida.
        </p>
      </Panel>

      <CargarClienteModal open={openCliente} onClose={() => setOpenCliente(false)} onGuardado={refrescar} />
      <AgregarTurnoModal open={openTurno} onClose={() => setOpenTurno(false)} onGuardado={refrescar} />
    </div>
  )
}

/**
 * Deuda consolidada de los tutores que estuvieron en la jornada.
 *
 * <p><b>Por qué es N+1.</b> No existe consulta masiva de deuda: lo único que hay
 * es `GET /api/v1/tutores/{id}/deuda`, una por tutor. Se deduplica por
 * `tutorId` y se piden en paralelo con `Promise.allSettled`, así que el costo es
 * una request por tutor presente y todas vuelan a la vez.</p>
 *
 * <p><b>Por qué `allSettled` y no `all`.</b> Con `all`, un tutor al que se le cae
 * la red tumba el aviso de morosidad de los demás. Con `allSettled` se informa cuántos
 * quedaron sin consultar y el operador sabe que le puede faltar un aviso; lo
 * contrario sería mostrar "nadie debe nada" cuando en realidad no se consultó.</p>
 *
 * <p><b>Por qué un hook y no un `useApi`.</b> `useApi` resuelve el caso de "una
 * llamada a un recurso". Esto es "N llamadas derivadas de los datos de otra
 * llamada", que ya es un segundo nivel. Encerrarlo aparte deja explícito que es un
 * costo conocido y no un descuido.</p>
 */
function useDeudasDelDia(jornada: MascotaDelDia[] | null, fecha: string) {
  const [estado, setEstado] = useState<{
    clave: string
    porTutor: Record<number, DeudaTutor>
    fallidos: number
    cargando: boolean
  }>({ clave: "", porTutor: {}, fallidos: 0, cargando: false })

  const [tique, setTique] = useState(0)

  const ids = useMemo(() => {
    const unicos = new Set<number>()
    for (const mascota of jornada ?? []) {
      for (const tutor of mascota.tutores) unicos.add(tutor.tutorId)
    }
    return [...unicos].sort((a, b) => a - b)
  }, [jornada])

  /**
   * Los ids se leen de un ref y no se ponen en las dependencias.
   *
   * <p>El arreglo cambia de largo según quantos tutores vinieron, y React exige
   * que el arreglo de dependencias mantenga su tamaño entre renders. Encerrarlo en
   * una clave de texto y guardar los ids aparte cumple las dos cosas: la clave
   * dispara el efecto, el ref aporta los valores.</p>
   */
  const idsRef = useRef(ids)
  idsRef.current = ids
  const clave = `${fecha}:${ids.join(",")}`

  useEffect(() => {
    const aConsultar = idsRef.current
    let vigente = true

    if (aConsultar.length === 0) {
      setEstado({ clave, porTutor: {}, fallidos: 0, cargando: false })
      return
    }

    setEstado({ clave, porTutor: {}, fallidos: 0, cargando: true })
    Promise.allSettled(aConsultar.map((id) => apiTutores.deuda(id))).then((resultados) => {
      if (!vigente) return
      const porTutor: Record<number, DeudaTutor> = {}
      let fallidos = 0
      resultados.forEach((resultado, i) => {
        if (resultado.status === "fulfilled") porTutor[aConsultar[i]] = resultado.value
        else fallidos += 1
      })
      setEstado({ clave, porTutor, fallidos, cargando: false })
    })

    return () => {
      vigente = false
    }
  }, [clave, tique])

  const recargar = useCallback(() => setTique((t) => t + 1), [])

  // Se expone el `cargando` aunque la vista todavia no lo pinte: el dato existe,
  // y omitirlo obligaria al proximo consumidor a reinventarlo.
  return { ...estado, recargar }
}

function TurnoCol({
  title,
  items,
  icon,
  nota,
}: {
  title: string
  items: { id: string; top: string; sub: string }[]
  icon: "turno" | "in" | "out"
  nota?: string
}) {
  return (
    <div className="rounded-md border border-border">
      <div className="flex items-center gap-2 border-b border-border bg-muted/40 px-3 py-2 text-xs font-medium">
        {icon === "in" && <LogIn className="h-3.5 w-3.5" />}
        {icon === "out" && <LogOut className="h-3.5 w-3.5" />}
        {title} <span className="ml-auto text-muted-foreground">{items.length}</span>
      </div>
      <ul className="divide-y divide-border">
        {items.length === 0 ? (
          <li className="px-3 py-3 text-sm text-muted-foreground">Sin registros.</li>
        ) : (
          items.map((it) => (
            <li key={it.id} className="px-3 py-2">
              <p className="text-sm font-medium">{it.top}</p>
              <p className="text-xs text-muted-foreground">{it.sub}</p>
            </li>
          ))
        )}
      </ul>
      {nota && <p className="border-t border-border px-3 py-1.5 text-xs text-muted-foreground">{nota}</p>}
    </div>
  )
}

/**
 * `"08:00:00"` → `"08:00"`.
 *
 * Jackson emite las columnas `time` de MySQL con segundos. Mostrar `08:00:00` en
 * la grilla del mostrador hace ruido y todos los horarios de la pantalla están en
 * `HH:mm`.
 */
function hhmm(hora: string | null): string {
  return hora ? hora.slice(0, 5) : "sin horario"
}

/**
 * Alta unificada de cliente: tutor + mascota en una sola acción.
 *
 * <p>Antes escribía en `addOwnerWithDog`, o sea en el `useState` del store: la
 * pantalla aceptaba el alta, el operador la veía aparecer y al recargar no había
 * nada. Ahora va contra la API real.</p>
 *
 * <p><b>El orden de las tres llamadas importa y no es negociable:</b></p>
 * <ol>
 *   <li>`POST /tutores` — el DNI es `@NotBlank` con 7 a 10 dígitos, así que sin él
 *       no hay alta posible. Es el primero porque de su id sale el vínculo.</li>
 *   <li>`POST /mascotas` — se crea sola, sin tutor.</li>
 *   <li>`POST /tutores/{id}/mascotas/{mascotaId}` — el vínculo, que es la tupla
 *       `$N:M` con su flag de retiro.</li>
 * </ol>
 * <p>El paso 3 podría ser un solo `POST /mascotas` con el tutor anidado
 * en la lista `tutores` (una sola transacción, ver `alta-mascota.tsx`), pero esa
 * ruta **sincroniza** la lista completa de tutores de la mascota: lo que no venga
 * en el `tutores` queda desvinculado. Con el cotutor opcional eso obliga a armar
 * la lista con los dos de entrada. La ruta por tutor agrega de a una
 * (`TutorServiceImpl.vincularMascota` solo toca los vínculos de ese tutor), así
 * que sumar el cotutor al final no puede pisar el vínculo del titular.</p>
 *
 * <p>El precio de esa decisión es que si el paso 2 falla queda un tutor creado sin
 * animal. Por eso el `catch` distingue los dos casos y, cuando el tutor ya se
 * creó, informa el DNI para que el operador pueda limpiarlo en el Padrón.</p>
 *
 * <p>El `autorizadoRetiro` viaja explícito en cada tupla (regla 2.4): el titular
 * arranca prendido —quien carga al cliente en mostrador es el dueño y es quien va
 * a retirar al animal— y el cotutor se crea con el flag apagado.</p>
 */
function CargarClienteModal({
  open,
  onClose,
  onGuardado,
}: {
  open: boolean
  onClose: () => void
  /** Avisa al tablero que relea la jornada: el cliente recien cargado ya es real. */
  onGuardado: () => void | Promise<void>
}) {
  const [tutor, setTutor] = useState({
    nombre: "",
    apellido: "",
    dni: "",
    telefono: "",
  })
  const [mascota, setMascota] = useState({
    nombre: "",
    especie: "CANINO" as EspecieMascota,
    raza: "",
    sexo: "" as "" | "Macho" | "Hembra",
    fechaNacimiento: "",
    peso: "",
    conducta: "" as "" | "normal" | "gruñe" | "muerde" | "miedoso",
    observaciones: "",
  })
  const [autorizado, setAutorizado] = useState(true)
  const [cotutorId, setCotutorId] = useState<string>("")
  const [padron, setPadron] = useState<Tutor[]>([])
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  /**
   * El padrón se pide solo con el modal abierto, y se vuelve a pedir en cada
   * apertura para que el cotutor nuevo de otro mostrador esté en la lista.
   *
   * <p>También se limpian los avisos: el modal queda abierto después de guardar,
   * así que sin esto, reabrirlo mostría el "se cargó" del alta anterior y el
   * botón de guardar seguía oculto, con el formulario vacío.</p>
   */
  useEffect(() => {
    if (!open) return
    let vigente = true
    setError(null)
    setExito(null)
    apiTutores
      .listar()
      .then((lista) => {
        if (vigente) setPadron(lista)
      })
      .catch(() => {
        // El cotutor es opcional: si el padrón no carga, el alta igual tiene que
        // poder hacerse con el tutor principal. Se avisa abajo.
        if (vigente) setPadron([])
      })
    return () => {
      vigente = false
    }
  }, [open])

  function limpiar() {
    setTutor({ nombre: "", apellido: "", dni: "", telefono: "" })
    setMascota({
      nombre: "",
      especie: "CANINO",
      raza: "",
      sexo: "",
      fechaNacimiento: "",
      peso: "",
      conducta: "",
      observaciones: "",
    })
    setAutorizado(true)
    setCotutorId("")
    setError(null)
  }

  async function guardar() {
    setGuardando(true)
    setError(null)
    setExito(null)

    // El DNI se valida acá para no gastar un viaje con un 400 previsible: el
    // backend exige 7 a 10 dígitos y el mensaje no dice cuántos.
    const dni = tutor.dni.trim()
    if (!/^\d{7,10}$/.test(dni)) {
      setError("El DNI debe tener entre 7 y 10 dígitos, solo números.")
      setGuardando(false)
      return
    }
    const pesoNum = mascota.peso.trim() === "" ? undefined : Number(mascota.peso)
    if (pesoNum !== undefined && (Number.isNaN(pesoNum) || pesoNum <= 0)) {
      setError("El peso tiene que ser un número mayor a 0.")
      setGuardando(false)
      return
    }

    let tutorId: number | null = null
    try {
      // 1) Tutor primero: el vínculo sale de su id.
      const creado = await apiTutores.crear({
        nombre: tutor.nombre.trim(),
        apellido: tutor.apellido.trim(),
        dni,
        telefono: tutor.telefono.trim() || undefined,
      })
      tutorId = creado.id

      // 2) Mascota sola.
      const mascotaCreada = await apiMascotas.crear({
        nombre: mascota.nombre.trim(),
        especie: mascota.especie,
        raza: mascota.raza.trim() || undefined,
        sexo: mascota.sexo || undefined,
        // El backend rechaza fechas futuras con @Past: mandar "" no es "sin
        // fecha", es una fecha inválida.
        fechaNacimiento: mascota.fechaNacimiento || undefined,
        pesoKg: pesoNum,
        conducta: mascota.conducta || undefined,
        observaciones: mascota.observaciones.trim() || undefined,
      })

      // 3) Vínculos. El titular va con su flag; el cotutor se agrega después con
      // el suyo, porque `sincronizarTutores` reemplaza la lista completa y el
      // segundo POST debe volver a listar al titular o lo borraría.
      await apiTutores.vincular(tutorId, mascotaCreada.id, autorizado)

      if (cotutorId) {
        await apiTutores.vincular(Number(cotutorId), mascotaCreada.id, false)
      }

      // El modal queda abierto con el aviso: cerrarlo en el acto dejaría al
      // operador sin confirmación de qué se cargó, y este formulario pide tres
      // pasos. El botón de cerrar queda como "Listo".
      //
      // El tablero se relee antes de avisar: si la recarga fallara, el operador
      // tiene que enterarse en vez de leer un "se cargó" sobre una pantalla que
      // sigue mostrando la jornada anterior.
      await onGuardado()
      setExito(
        `${mascotaCreada.nombre} quedó cargada con ${creado.nombreCompleto}. Ya está en Ficha médica, Agenda e Historial.`,
      )
      limpiar()
    } catch (e) {
      // El caso que hay que reportar bien: el tutor se creó y la mascota no. Sin
      // esto el operador queda con un tutor cargado que no usa y no sabe de qué.
      setError(
        tutorId === null
          ? e instanceof Error
            ? e.message
            : "No se pudo cargar el cliente"
          : `Se cargó el tutor ${dni} pero no se pudo completar la mascota. Revisá el padrón: el tutor quedó dado de alta. ${
              e instanceof Error ? e.message : ""
            }`,
      )
    } finally {
      setGuardando(false)
    }
  }

  const listo =
    tutor.nombre.trim().length > 0 &&
    tutor.apellido.trim().length > 0 &&
    tutor.dni.trim().length > 0 &&
    mascota.nombre.trim().length > 0

  return (
    <Modal open={open} onClose={onClose} title="Cargar cliente">
      <div className="flex flex-col gap-4">
        <p className="text-xs text-muted-foreground">
          Carga al tutor y a su mascota en una sola operación. Quedan guardados en la
          base y disponibles al instante en Ficha médica, Agenda e Historial.
        </p>

        <div className="flex flex-col gap-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Datos del tutor
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Nombre">
              <TextInput
                value={tutor.nombre}
                onChange={(e) => setTutor((f) => ({ ...f, nombre: e.target.value }))}
                maxLength={80}
                className={TOQUE_MINIMO}
              />
            </Field>
            <Field label="Apellido">
              <TextInput
                value={tutor.apellido}
                onChange={(e) => setTutor((f) => ({ ...f, apellido: e.target.value }))}
                maxLength={80}
                className={TOQUE_MINIMO}
              />
            </Field>
            <Field label="DNI">
              <TextInput
                value={tutor.dni}
                onChange={(e) => setTutor((f) => ({ ...f, dni: e.target.value }))}
                inputMode="numeric"
                className={TOQUE_MINIMO}
              />
            </Field>
            <Field label="Teléfono">
              <TextInput
                value={tutor.telefono}
                onChange={(e) => setTutor((f) => ({ ...f, telefono: e.target.value }))}
                inputMode="tel"
                className={TOQUE_MINIMO}
              />
            </Field>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Datos de la mascota
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Nombre">
              <TextInput
                value={mascota.nombre}
                onChange={(e) => setMascota((f) => ({ ...f, nombre: e.target.value }))}
                placeholder="Ej: Firulais"
                maxLength={80}
                className={TOQUE_MINIMO}
              />
            </Field>
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">Especie</span>
              <Select
                value={mascota.especie}
                onChange={(e) => setMascota((f) => ({ ...f, especie: e.target.value as EspecieMascota }))}
                className={TOQUE_MINIMO}
              >
                {ESPECIES.map((e) => (
                  <option key={e.valor} value={e.valor}>
                    {e.etiqueta}
                  </option>
                ))}
              </Select>
            </label>
            <Field label="Raza">
              <TextInput
                value={mascota.raza}
                onChange={(e) => setMascota((f) => ({ ...f, raza: e.target.value }))}
                placeholder="Ej: Mestizo"
                maxLength={80}
                className={TOQUE_MINIMO}
              />
            </Field>
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">Sexo</span>
              <Select
                value={mascota.sexo}
                onChange={(e) =>
                  setMascota((f) => ({ ...f, sexo: e.target.value as "" | "Macho" | "Hembra" }))
                }
                className={TOQUE_MINIMO}
              >
                <option value="">Sin especificar</option>
                <option value="Macho">Macho</option>
                <option value="Hembra">Hembra</option>
              </Select>
            </label>
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">
                Fecha de nacimiento
              </span>
              <TextInput
                type="date"
                value={mascota.fechaNacimiento}
                max={hoyLocal()}
                onChange={(e) => setMascota((f) => ({ ...f, fechaNacimiento: e.target.value }))}
                className={TOQUE_MINIMO}
              />
            </label>
            <Field label="Peso (kg)">
              <TextInput
                value={mascota.peso}
                onChange={(e) => setMascota((f) => ({ ...f, peso: e.target.value }))}
                inputMode="decimal"
                placeholder="Ej: 12.5"
                className={TOQUE_MINIMO}
              />
            </Field>
          </div>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Conducta</span>
            <Select
              value={mascota.conducta}
              onChange={(e) =>
                setMascota(
                  (f) => ({
                    ...f,
                    conducta: e.target.value as "" | "normal" | "gruñe" | "muerde" | "miedoso",
                  }),
                )
              }
              className={TOQUE_MINIMO}
            >
              <option value="">Sin especificar</option>
              {CONDUCTAS.map((c) => (
                <option key={c.valor} value={c.valor}>
                  {c.etiqueta}
                </option>
              ))}
            </Select>
            <span className="text-xs text-muted-foreground">
              Cómo es el animal por manejo. Cómo se portó en una atención se anota en
              la ficha médica.
            </span>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Observaciones</span>
            <TextArea
              value={mascota.observaciones}
              onChange={(e) => setMascota((f) => ({ ...f, observaciones: e.target.value }))}
              placeholder="Opcional"
              maxLength={500}
            />
          </label>
        </div>

        <div className="rounded-md border border-border p-3">
          <p className="mb-2 text-xs font-medium text-muted-foreground">Vínculos</p>
          <label className="flex min-h-[44px] items-center gap-3 rounded-md border border-border px-3">
            <input
              type="checkbox"
              checked={autorizado}
              onChange={(e) => setAutorizado(e.target.checked)}
              className="size-4"
            />
            <span className="text-sm">
              El tutor está autorizado a retirar
              <span className="block text-xs text-muted-foreground">
                Si queda apagado, puede hacer consultas y pagos pero no llevarse a la
                mascota.
              </span>
            </span>
          </label>

          <label className="mt-3 flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">
              Sumar un cotutor (opcional)
            </span>
            <Select
              value={cotutorId}
              onChange={(e) => setCotutorId(e.target.value)}
              className={TOQUE_MINIMO}
              aria-label="Cotutor a vincular"
            >
              <option value="">Sin cotutor</option>
              {padron
                .filter((t) => t.dni.trim() !== tutor.dni.trim())
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombreCompleto} · DNI {t.dni}
                  </option>
                ))}
            </Select>
            <span className="text-xs text-muted-foreground">
              {padron.length === 0
                ? "No se pudo leer el padrón, se puede guardar igual sin cotutor."
                : "El cotutor se vincula sin autorización de retiro."}
            </span>
          </label>
        </div>

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        {exito && (
          <p className="rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" role="status">
            {exito}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} type="button">
            {exito ? "Listo" : "Cancelar"}
          </Button>
          <Button
            onClick={guardar}
            disabled={guardando || !listo}
            type="button"
            className={exito ? "hidden" : undefined}
          >
            {guardando ? "Guardando…" : "Guardar cliente"}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/**
 * Alta de turno contra `POST /api/v1/turnos`.
 *
 * <p>Antes escribía en `addTurno`, o sea en el store en memoria, y el turno no
 * llegaba a la Agenda: la grilla de Agenda lee la base. Ahora el turno se guarda
 * de verdad y aparece ahí al cambiar de pestaña.</p>
 *
 * <p>El profesional es obligatorio en el contrato (`@NotNull profesionalId`), así
 * que el selector no es opcional: un turno sin profesional bloquearía el
 * consultorio entero en la franja de 45+15 y el backend lo rechaza con 400.</p>
 *
 * <p>La fecha arranca en `hoyLocal()` y no lleva `min`: los turnos retroactivos se
 * registran a propósito, que es la atención que ya ocurrió y hay que documentar.</p>
 */
function AgregarTurnoModal({
  open,
  onClose,
  onGuardado,
}: {
  open: boolean
  onClose: () => void
  /**
   * Avisa al tablero que relea la jornada.
   *
   * <p>Es el caso que sí hace falta: el modal vive dentro del Mostrador, así que
   * el componente no se desmonta entre el alta y la lectura, y sin esto el turno
   * recién creado no aparecería en la grilla de arriba.</p>
   */
  onGuardado: () => void | Promise<void>
}) {
  const [mascotas, setMascotas] = useState<Mascota[]>([])
  const [profesionales, setProfesionales] = useState<Profesional[]>([])
  const [mascotaId, setMascotaId] = useState("")
  const [profesionalId, setProfesionalId] = useState("")
  const [fecha, setFecha] = useState(hoyLocal())
  const [hora, setHora] = useState("09:00")
  const [motivo, setMotivo] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    if (!open) return
    let vigente = true
    setExito(null)
    setError(null)
    // Ambos listados son necesarios para guardar: sin mascota o sin profesional
    // el contrato no se cumple, así que la pantalla avisa en vez de fallar al enviar.
    Promise.all([apiMascotas.listar(), apiProfesionales.listar()])
      .then(([m, p]) => {
        if (!vigente) return
        setMascotas(m)
        setProfesionales(p.filter((x) => x.activo))
        setMascotaId((prev) => prev || (m[0] ? String(m[0].id) : ""))
        setProfesionalId((prev) => {
          const activos = p.filter((x) => x.activo)
          return prev || (activos[0] ? String(activos[0].id) : "")
        })
      })
      .catch((e: unknown) => {
        if (vigente) setError(e instanceof Error ? e.message : "No se pudo leer el padrón")
      })
    return () => {
      vigente = false
    }
  }, [open])

  async function submit() {
    setGuardando(true)
    setError(null)
    setExito(null)
    try {
      // `fecha` + `hora` se unen en un LocalDateTime. El backend deriva solo el
      // fin de atención (+45) y el de bloque (+60): mandarlos sería saltarse la
      // ventana de desinfección.
      await apiTurnos.crear({
        fechaHoraInicio: `${fecha}T${hora}:00`,
        motivo: motivo.trim(),
        mascotaId: Number(mascotaId),
        profesionalId: Number(profesionalId),
      })
      // Antes del aviso, nunca después: el turno tiene que verse en la grilla
      // cuando el operador lee el "guardado".
      await onGuardado()
      setExito("Turno guardado. Ya está en Agenda y en el tablero de arriba.")
      setMotivo("")
    } catch (e) {
      // El 409 de solapamiento lo produce el dominio: el mensaje del servidor
      // explica qué profesional está ocupado y en qué ventana. Se muestra tal cual
      // en vez de reimprimir la regla acá.
      setError(e instanceof Error ? e.message : "No se pudo guardar el turno")
    } finally {
      setGuardando(false)
    }
  }

  const listo = mascotaId !== "" && profesionalId !== "" && motivo.trim().length > 0

  return (
    <Modal open={open} onClose={onClose} title="Agregar turno">
      <div className="flex flex-col gap-3">
        <Field label="Mascota">
          {mascotas.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No hay mascotas cargadas. Cargá al cliente desde el botón "Cargar
              cliente" o desde el Padrón.
            </p>
          ) : (
            <Select
              value={mascotaId}
              onChange={(e) => setMascotaId(e.target.value)}
              className={TOQUE_MINIMO}
              aria-label="Mascota del turno"
            >
              {mascotas.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nombre} — {m.especie.toLowerCase()}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Profesional">
          {profesionales.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No hay profesionales activos. Cargalos desde el padrón de profesionales.
            </p>
          ) : (
            <Select
              value={profesionalId}
              onChange={(e) => setProfesionalId(e.target.value)}
              className={TOQUE_MINIMO}
              aria-label="Profesional del turno"
            >
              {profesionales.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre} {p.apellido}
                  {p.matricula ? ` · ${p.matricula}` : ""}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Fecha">
            <TextInput
              type="date"
              value={fecha}
              onChange={(e) => setFecha(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Hora">
            <TextInput
              type="time"
              value={hora}
              onChange={(e) => setHora(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>
        <Field label="Motivo de consulta">
          <TextInput
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Ej: Vacuna, control…"
            maxLength={300}
            className={TOQUE_MINIMO}
          />
        </Field>

        {error && (
          <p className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        {exito && (
          <p className="rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" role="status">
            {exito}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} type="button">
            Cerrar
          </Button>
          <Button onClick={submit} disabled={guardando || !listo} type="button">
            {guardando ? "Guardando…" : "Guardar turno"}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
