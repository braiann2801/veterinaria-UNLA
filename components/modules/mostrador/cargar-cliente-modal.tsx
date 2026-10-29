"use client"

import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { Field, TextInput, Select, TextArea } from "@/components/ui/field"
import { DatePicker } from "@/components/ui/date-picker"
import { Modal } from "@/components/ui/modal"
import { mascotas as apiMascotas, tutores as apiTutores, hoyLocal } from "@/lib/api"
import type { EspecieMascota, Tutor } from "@/lib/api"
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
export function CargarClienteModal({
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
    email: "",
    direccion: "",
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
  // La lista vacía sola no distingue "todavía no hay tutores" de "falló el GET".
  const [estadoPadron, setEstadoPadron] = useState<"cargando" | "listo" | "error">("cargando")
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
    setEstadoPadron("cargando")
    apiTutores
      .listar()
      .then((lista) => {
        if (!vigente) return
        setPadron(lista)
        setEstadoPadron("listo")
      })
      .catch(() => {
        // El cotutor es opcional: si el padrón no carga, el alta igual tiene que
        // poder hacerse con el tutor principal. Se avisa abajo.
        if (!vigente) return
        setPadron([])
        setEstadoPadron("error")
      })
    return () => {
      vigente = false
    }
  }, [open])

  function limpiar() {
    setTutor({ nombre: "", apellido: "", dni: "", telefono: "", email: "", direccion: "" })
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
        // Los opcionales van como `undefined` y no como "": el backend los valida
        // con @Pattern y un string vacío no pasa la validación.
        telefono: tutor.telefono.trim() || undefined,
        email: tutor.email.trim() || undefined,
        direccion: tutor.direccion.trim() || undefined,
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

  // El tutor que se está cargando no puede ser su propio cotutor.
  const candidatosCotutor = padron.filter((t) => t.dni.trim() !== tutor.dni.trim())

  const listo =
    tutor.nombre.trim().length > 0 &&
    tutor.apellido.trim().length > 0 &&
    tutor.dni.trim().length > 0 &&
    mascota.nombre.trim().length > 0

  return (
    <Modal open={open} onClose={onClose} title="Cargar cliente">
      <div className="flex flex-col gap-4">
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
            <Field label="Email">
              <TextInput
                value={tutor.email}
                onChange={(e) => setTutor((f) => ({ ...f, email: e.target.value }))}
                inputMode="email"
                className={TOQUE_MINIMO}
              />
            </Field>
            <Field label="Dirección">
              <TextInput
                value={tutor.direccion}
                onChange={(e) => setTutor((f) => ({ ...f, direccion: e.target.value }))}
                maxLength={200}
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
            <DatePicker
              label="Fecha de nacimiento"
              value={mascota.fechaNacimiento}
              max={hoyLocal()}
              onChange={(nueva) => setMascota((f) => ({ ...f, fechaNacimiento: nueva }))}
              limpiable
            />
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
              {candidatosCotutor.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombreCompleto} · DNI {t.dni}
                </option>
              ))}
            </Select>
            <span className="text-xs text-muted-foreground">
              {estadoPadron === "cargando"
                ? "Cargando el padrón…"
                : estadoPadron === "error"
                  ? "No se pudo leer el padrón, se puede guardar igual sin cotutor."
                  : candidatosCotutor.length === 0
                    ? "Todavía no hay otros tutores en el padrón."
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
          <p className="rounded-md border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-300" role="status">
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
