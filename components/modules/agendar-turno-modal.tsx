"use client"

/**
 * Alta de turno clínico, única para Agenda y Mostrador.
 *
 * <p>Con el desplegable cerrado funciona como siempre: se elige una mascota ya
 * cargada. El botón "Cliente o mascota nuevos" abre dos opciones para el
 * cliente que llega por primera vez: cliente nuevo o existente, y mascota nueva
 * o existente. Así el mostrador agenda en un solo paso, sin cargar primero el
 * cliente, después la mascota y recién ahí el turno.</p>
 *
 * <p>Las altas combinadas van a `POST /turnos/con-alta`, que hace todo en una
 * transacción: si el turno choca con la ventana de 45+15 (409), no queda ni el
 * cliente ni la mascota creados.</p>
 *
 * <p>Se monta solo mientras está abierto, para que el formulario arranque vacío:
 * si un horario fue rechazado, el operador no quiere encontrarlo escrito al
 * reabrir. La fecha no lleva `min`: una atención ya ocurrida se documenta igual.</p>
 */

import { useState } from "react"
import { ChevronDown, UserPlus } from "lucide-react"

import {
  mascotas as apiMascotas,
  profesionales as apiProfesionales,
  turnos as apiTurnos,
  tutores as apiTutores,
} from "@/lib/api"
import type { EspecieMascota, Mascota, Profesional, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Button } from "@/components/ui/button"
import { DatePicker } from "@/components/ui/date-picker"
import { Field, Select, TextInput } from "@/components/ui/field"
import { Modal } from "@/components/ui/modal"
import { cn } from "@/lib/utils"
import { ESPECIES, TOQUE_MINIMO, formatearDni, soloDni, soloTelefono } from "@/lib/vista"

type ModoCliente = "existente" | "nuevo"
type ModoMascota = "existente" | "nueva"

function etiquetaMascota(m: Mascota): string {
  const tutor = m.tutores?.[0]
  return tutor ? `${m.nombre} · ${tutor.nombre} ${tutor.apellido}` : `${m.nombre} · sin tutor`
}

function etiquetaProfesional(p: Profesional): string {
  return p.matricula ? `${p.nombreCompleto} · ${p.matricula}` : p.nombreCompleto
}

export function AgendarTurnoModal({
  fechaPorDefecto,
  onCerrar,
  onGuardado,
}: {
  fechaPorDefecto: string
  onCerrar: () => void
  /** El padre relee sus listados antes de que el modal se cierre. */
  onGuardado: () => void | Promise<void>
}) {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())
  const profesionales = useApi<Profesional[]>(() => apiProfesionales.listar())

  const [desplegado, setDesplegado] = useState(false)
  const [modoCliente, setModoCliente] = useState<ModoCliente>("nuevo")
  const [modoMascota, setModoMascota] = useState<ModoMascota>("nueva")

  const [mascotaId, setMascotaId] = useState("")
  const [tutorId, setTutorId] = useState("")
  const [tutorNuevo, setTutorNuevo] = useState({ nombre: "", apellido: "", dni: "", telefono: "" })
  const [mascotaNueva, setMascotaNueva] = useState({
    nombre: "",
    especie: "CANINO" as EspecieMascota,
    sexo: "" as "" | "Macho" | "Hembra",
    raza: "",
  })

  const [profesionalId, setProfesionalId] = useState("")
  const [fecha, setFecha] = useState(fechaPorDefecto)
  const [hora, setHora] = useState("09:00")
  const [motivo, setMotivo] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const clienteNuevo = desplegado && modoCliente === "nuevo"
  // Un cliente nuevo no tiene mascotas: la suya se carga nueva en el mismo paso.
  const mascotaEsNueva = desplegado && (clienteNuevo || modoMascota === "nueva")
  const tutorElegido = (tutores.datos ?? []).find((t) => String(t.id) === tutorId) ?? null
  const profesionalesActivos = (profesionales.datos ?? []).filter((p) => p.activo)

  const clienteListo = !desplegado
    ? true
    : clienteNuevo
      ? tutorNuevo.nombre.trim() !== "" && tutorNuevo.apellido.trim() !== "" && tutorNuevo.dni.length >= 7
      : tutorId !== ""
  const mascotaLista = mascotaEsNueva ? mascotaNueva.nombre.trim() !== "" : mascotaId !== ""
  const listo =
    clienteListo && mascotaLista && profesionalId !== "" && hora !== "" && motivo.trim() !== ""

  function cambiarCliente(modo: ModoCliente) {
    setModoCliente(modo)
    setMascotaId("")
  }

  function cambiarTutor(id: string) {
    setTutorId(id)
    // La mascota elegida era de otro cliente.
    setMascotaId("")
  }

  async function guardar() {
    setGuardando(true)
    setError(null)
    // Fecha y hora se arman a mano en hora local: un `toISOString()` correría el día en UTC-3.
    const turno = {
      fechaHoraInicio: `${fecha}T${hora}:00`,
      motivo: motivo.trim(),
      profesionalId: Number(profesionalId),
    }
    try {
      if (!mascotaEsNueva) {
        await apiTurnos.crear({ ...turno, mascotaId: Number(mascotaId) })
      } else {
        await apiTurnos.crearConAlta({
          ...turno,
          tutorId: clienteNuevo ? undefined : Number(tutorId),
          // Los opcionales van como `undefined` y no como "": el backend los valida con @Pattern.
          tutorNuevo: clienteNuevo
            ? {
                nombre: tutorNuevo.nombre.trim(),
                apellido: tutorNuevo.apellido.trim(),
                dni: tutorNuevo.dni,
                telefono: tutorNuevo.telefono.trim() || undefined,
              }
            : undefined,
          mascotaNueva: {
            nombre: mascotaNueva.nombre.trim(),
            especie: mascotaNueva.especie,
            sexo: mascotaNueva.sexo || undefined,
            raza: mascotaNueva.raza.trim() || undefined,
          },
        })
      }
      await onGuardado()
      onCerrar()
    } catch (e) {
      // El 409 de solapamiento trae las horas en conflicto: se muestra tal cual.
      setError(e instanceof Error ? e.message : "No se pudo agendar el turno")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal open onClose={onCerrar} title="Agendar turno">
      <div className="flex flex-col gap-3">
        <button
          type="button"
          onClick={() => setDesplegado((d) => !d)}
          aria-expanded={desplegado}
          className={cn(
            "inline-flex items-center justify-between gap-2 rounded-xl border border-dashed border-border px-3 text-sm font-medium text-primary transition-colors hover:bg-muted",
            TOQUE_MINIMO,
          )}
        >
          <span className="inline-flex items-center gap-2">
            <UserPlus className="size-4" aria-hidden />
            Cliente o mascota nuevos
          </span>
          <ChevronDown className={cn("size-4 transition-transform", desplegado && "rotate-180")} aria-hidden />
        </button>

        {!desplegado ? (
          <Field label="Mascota">
            <Select value={mascotaId} onChange={(e) => setMascotaId(e.target.value)} className={TOQUE_MINIMO}>
              <option value="">Elegí una mascota…</option>
              {(mascotas.datos ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {etiquetaMascota(m)}
                </option>
              ))}
            </Select>
            {mascotas.datos !== null && mascotas.datos.length === 0 && (
              <span className="text-xs text-muted-foreground">
                No hay mascotas cargadas: usá "Cliente o mascota nuevos".
              </span>
            )}
          </Field>
        ) : (
          <div className="flex flex-col gap-3 rounded-xl border border-border bg-muted/30 p-3">
            <Segmentado
              etiqueta="Cliente"
              valor={modoCliente}
              onChange={cambiarCliente}
              opciones={[
                { valor: "nuevo", etiqueta: "Cliente nuevo" },
                { valor: "existente", etiqueta: "Cliente existente" },
              ]}
            />

            {clienteNuevo ? (
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Nombre">
                  <TextInput
                    value={tutorNuevo.nombre}
                    onChange={(e) => setTutorNuevo((t) => ({ ...t, nombre: e.target.value }))}
                    maxLength={80}
                    className={TOQUE_MINIMO}
                  />
                </Field>
                <Field label="Apellido">
                  <TextInput
                    value={tutorNuevo.apellido}
                    onChange={(e) => setTutorNuevo((t) => ({ ...t, apellido: e.target.value }))}
                    maxLength={80}
                    className={TOQUE_MINIMO}
                  />
                </Field>
                <Field label="DNI">
                  <TextInput
                    value={tutorNuevo.dni}
                    onChange={(e) => setTutorNuevo((t) => ({ ...t, dni: soloDni(e.target.value) }))}
                    inputMode="numeric"
                    placeholder="30123456"
                    className={TOQUE_MINIMO}
                  />
                </Field>
                <Field label="Teléfono (opcional)">
                  <TextInput
                    value={tutorNuevo.telefono}
                    onChange={(e) => setTutorNuevo((t) => ({ ...t, telefono: soloTelefono(e.target.value) }))}
                    inputMode="tel"
                    placeholder="11 2345-6789"
                    className={TOQUE_MINIMO}
                  />
                </Field>
              </div>
            ) : (
              <>
                <Field label="Cliente">
                  <Select value={tutorId} onChange={(e) => cambiarTutor(e.target.value)} className={TOQUE_MINIMO}>
                    <option value="">Elegí un cliente…</option>
                    {(tutores.datos ?? []).map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.nombreCompleto} · DNI {formatearDni(t.dni)}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Segmentado
                  etiqueta="Mascota"
                  valor={modoMascota}
                  onChange={(modo) => {
                    setModoMascota(modo)
                    setMascotaId("")
                  }}
                  opciones={[
                    { valor: "existente", etiqueta: "Mascota existente" },
                    { valor: "nueva", etiqueta: "Mascota nueva" },
                  ]}
                />
                {modoMascota === "existente" && (
                  <Field label="Mascota del cliente">
                    <Select
                      value={mascotaId}
                      onChange={(e) => setMascotaId(e.target.value)}
                      disabled={!tutorElegido}
                      className={TOQUE_MINIMO}
                    >
                      <option value="">{tutorElegido ? "Elegí una mascota…" : "Primero elegí el cliente"}</option>
                      {(tutorElegido?.mascotas ?? []).map((m) => (
                        <option key={m.id} value={m.id}>
                          {m.nombre}
                        </option>
                      ))}
                    </Select>
                    {tutorElegido && tutorElegido.mascotas.length === 0 && (
                      <span className="text-xs text-muted-foreground">
                        Este cliente no tiene mascotas: elegí "Mascota nueva".
                      </span>
                    )}
                  </Field>
                )}
              </>
            )}

            {mascotaEsNueva && (
              <div className="grid gap-3 sm:grid-cols-2">
                {clienteNuevo && (
                  <p className="text-xs text-muted-foreground sm:col-span-2">
                    La mascota del cliente nuevo se carga acá mismo y queda autorizada a retirarla.
                  </p>
                )}
                <Field label="Nombre de la mascota">
                  <TextInput
                    value={mascotaNueva.nombre}
                    onChange={(e) => setMascotaNueva((m) => ({ ...m, nombre: e.target.value }))}
                    maxLength={80}
                    className={TOQUE_MINIMO}
                  />
                </Field>
                <Field label="Especie">
                  <Select
                    value={mascotaNueva.especie}
                    onChange={(e) => setMascotaNueva((m) => ({ ...m, especie: e.target.value as EspecieMascota }))}
                    className={TOQUE_MINIMO}
                  >
                    {ESPECIES.map((e) => (
                      <option key={e.valor} value={e.valor}>
                        {e.etiqueta}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field label="Sexo (opcional)">
                  <Select
                    value={mascotaNueva.sexo}
                    onChange={(e) => setMascotaNueva((m) => ({ ...m, sexo: e.target.value as "" | "Macho" | "Hembra" }))}
                    className={TOQUE_MINIMO}
                  >
                    <option value="">Sin indicar</option>
                    <option value="Macho">Macho</option>
                    <option value="Hembra">Hembra</option>
                  </Select>
                </Field>
                <Field label="Raza (opcional)">
                  <TextInput
                    value={mascotaNueva.raza}
                    onChange={(e) => setMascotaNueva((m) => ({ ...m, raza: e.target.value }))}
                    maxLength={80}
                    className={TOQUE_MINIMO}
                  />
                </Field>
              </div>
            )}
          </div>
        )}

        <Field label="Profesional">
          <Select value={profesionalId} onChange={(e) => setProfesionalId(e.target.value)} className={TOQUE_MINIMO}>
            <option value="">Elegí un profesional…</option>
            {profesionalesActivos.map((p) => (
              <option key={p.id} value={p.id}>
                {etiquetaProfesional(p)}
              </option>
            ))}
          </Select>
          {profesionales.datos !== null && profesionalesActivos.length === 0 && (
            <span className="text-xs text-muted-foreground">
              No hay profesionales activos. Cargalos desde el Mostrador.
            </span>
          )}
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <DatePicker label="Fecha" value={fecha} onChange={setFecha} />
          <Field label="Hora">
            <TextInput
              type="time"
              step={900}
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

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onCerrar} type="button" className={TOQUE_MINIMO}>
            Cancelar
          </Button>
          <Button onClick={guardar} disabled={guardando || !listo} type="button" className={TOQUE_MINIMO}>
            {guardando ? "Guardando…" : "Agendar turno"}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/** Dos opciones excluyentes como botones, con el área táctil mínima. */
function Segmentado<T extends string>({
  etiqueta,
  valor,
  onChange,
  opciones,
}: {
  etiqueta: string
  valor: T
  onChange: (valor: T) => void
  opciones: { valor: T; etiqueta: string }[]
}) {
  return (
    <div className="flex flex-col gap-1.5" role="radiogroup" aria-label={etiqueta}>
      <span className="text-xs font-medium text-muted-foreground">{etiqueta}</span>
      <div className="grid grid-cols-2 gap-1 rounded-xl bg-muted p-1">
        {opciones.map((o) => (
          <button
            key={o.valor}
            type="button"
            role="radio"
            aria-checked={valor === o.valor}
            onClick={() => onChange(o.valor)}
            className={cn(
              "rounded-lg px-2 text-sm font-medium transition-colors",
              TOQUE_MINIMO,
              valor === o.valor
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            {o.etiqueta}
          </button>
        ))}
      </div>
    </div>
  )
}
