"use client"

/**
 * Mostrador: movimiento de la jornada y alta de clientes y turnos.
 *
 * <p>El tablero de arriba lee del store en memoria (`lib/store.tsx`) y es una
 * demo; lo que se corrigió en HOTFIX-UI son los dos modales, que antes escribían
 * también en el store y por lo tanto no persistían nada:</p>
 *
 * <ul>
 *   <li><b>Cargar cliente</b> crea tutor + mascota contra la API real, en una sola
 *       operación, y ya no usa `addOwnerWithDog`.</li>
 *   <li><b>Agregar turno</b> crea el turno con `POST /api/v1/turnos`, así que
 *       aparece en Agenda porque Agenda lee la base.</li>
 * </ul>
 *
 * <p>La fecha de la jornada sale de `useStore().today`, que se calcula con
 * `hoyLocal()`: en hora local y no con `toISOString()`, que entre las 21:00 y las
 * 24:00 devolvería mañana.</p>
 */

import { useEffect, useState } from "react"
import { AlertTriangle, UserPlus, CalendarPlus, LogIn, LogOut } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Panel, Field, TextInput, Select, TextArea } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { useStore, ownerName, money } from "@/lib/store"
import {
  mascotas as apiMascotas,
  profesionales as apiProfesionales,
  tutores as apiTutores,
  turnos as apiTurnos,
  hoyLocal,
} from "@/lib/api"
import type { EspecieMascota, Mascota, Profesional, Tutor } from "@/lib/api"
import { TOQUE_MINIMO } from "@/lib/vista"

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

export function Mostrador() {
  const { turnos, reservas, dogs, owners, today, cupoMax, ocupadosPorDia } = useStore()
  const [openCliente, setOpenCliente] = useState(false)
  const [openTurno, setOpenTurno] = useState(false)

  const dog = (id: string) => dogs.find((d) => d.id === id)
  const owner = (id: string) => owners.find((o) => o.id === id)

  const turnosHoy = turnos.filter((t) => t.fecha === today)
  const ingresosHoy = reservas.filter((r) => r.ingreso === today)
  const egresosHoy = reservas.filter((r) => r.egreso === today)

  const ocupados = ocupadosPorDia(today)

  // Avisos: perros de hoy con conducta de riesgo + dueños con deuda
  const dogsHoy = new Set<string>([
    ...turnosHoy.map((t) => t.dogId),
    ...ingresosHoy.map((r) => r.dogId),
    ...egresosHoy.map((r) => r.dogId),
  ])
  const avisosConducta = [...dogsHoy]
    .map((id) => dog(id))
    .filter((d) => d && (d.conducta === "muerde" || d.conducta === "gruñe"))
  const ownersHoy = new Set<string>([
    ...turnosHoy.map((t) => t.ownerId),
    ...ingresosHoy.map((r) => r.ownerId),
    ...egresosHoy.map((r) => r.ownerId),
  ])
  const avisosDeuda = [...ownersHoy]
    .map((id) => owner(id))
    .filter((o) => o && o.deuda > 0)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">Mostrador</h1>
          <p className="text-sm text-muted-foreground">Movimiento de hoy · {today}</p>
        </div>
        <div className="flex gap-2">
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
          <p className="text-3xl font-bold">
            {ocupados} <span className="text-base font-normal text-muted-foreground">de {cupoMax} ocupados</span>
          </p>
          <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-foreground transition-all"
              style={{ width: `${Math.min(100, (ocupados / cupoMax) * 100)}%` }}
            />
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            {cupoMax - ocupados} lugares disponibles hoy
          </p>
        </Panel>

        <Panel title="Avisos importantes" className="lg:col-span-2">
          {avisosConducta.length === 0 && avisosDeuda.length === 0 ? (
            <p className="text-sm text-muted-foreground">Sin avisos para hoy.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {avisosConducta.map((d) => (
                <li key={`c-${d!.id}`} className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span>
                    <strong>{d!.nombre}</strong> {d!.conducta === "muerde" ? "muerde" : "gruñe"} — manejar con cuidado.
                  </span>
                </li>
              ))}
              {avisosDeuda.map((o) => (
                <li key={`d-${o!.id}`} className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span>
                    <strong>{ownerName(o)}</strong> viene con deuda previa de {money(o!.deuda)}.
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>

      <Panel title="Turnos y estadías de hoy">
        <div className="grid gap-4 md:grid-cols-3">
          <TurnoCol title="Turnos" items={turnosHoy.map((t) => ({
            id: t.id, top: `${dog(t.dogId)?.nombre ?? "—"} · ${t.hora}`, sub: `${ownerName(owner(t.ownerId))} · ${t.motivo}`,
          }))} icon="turno" />
          <TurnoCol title="Ingresan (guardería)" items={ingresosHoy.map((r) => ({
            id: r.id, top: dog(r.dogId)?.nombre ?? "—", sub: `Hasta ${r.egreso}`,
          }))} icon="in" />
          <TurnoCol title="Se retiran" items={egresosHoy.map((r) => ({
            id: r.id, top: dog(r.dogId)?.nombre ?? "—", sub: `Retira: ${r.autorizadoNombre || ownerName(owner(r.ownerId))}`,
          }))} icon="out" />
        </div>
      </Panel>

      <CargarClienteModal open={openCliente} onClose={() => setOpenCliente(false)} />
      <AgregarTurnoModal open={openTurno} onClose={() => setOpenTurno(false)} />
    </div>
  )
}

function TurnoCol({
  title,
  items,
  icon,
}: {
  title: string
  items: { id: string; top: string; sub: string }[]
  icon: "turno" | "in" | "out"
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
    </div>
  )
}

function CargarClienteModal({ open, onClose }: { open: boolean; onClose: () => void }) {
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
function AgregarTurnoModal({ open, onClose }: { open: boolean; onClose: () => void }) {
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
      setExito("Turno guardado. Ya está en Agenda.")
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
