"use client"

/**
 * TASK-006: ficha médica contra la API real.
 *
 * Sin inputs de precio ni de medio de pago (regla 2.3 de AGENTS.md). El
 * formulario solo pide lo clínico; el servidor deriva la alerta de conducta y
 * emite la comanda de cobro por su cuenta.
 */

import { useState } from "react"
import { Stethoscope, TriangleAlert } from "lucide-react"

import {
  consultas as apiConsultas,
  mascotas as apiMascotas,
  profesionales as apiProfesionales,
  turnos as apiTurnos,
} from "@/lib/api"
import type {
  ConductaObservada,
  ConsultaMedica,
  Mascota,
  Profesional,
  TurnoClinico,
} from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, Select, TextArea, TextInput } from "@/components/ui/field"
import { Recurso, Semforo, TOQUE_MINIMO } from "@/lib/vista"

const CONDUCTAS: { valor: ConductaObservada; etiqueta: string }[] = [
  { valor: "TRANQUILO", etiqueta: "Tranquilo" },
  { valor: "NERVIOSO", etiqueta: "Nervioso" },
  { valor: "REACTIVO", etiqueta: "Reactivo" },
  { valor: "AGRESIVO", etiqueta: "Agresivo" },
]

/**
 * Conductas que el backend convierte en alerta.
 *
 * Se replica aca solo para el texto del badge. Quien decide es el servidor:
 * aunque esta lista se olvidara de agregar un valor, la alerta seguiria
 * encendiéndose y el badge solo dejaria de mostrarse. Es degradacion
 * cosmetica, no de logica.
 */
const CONDUCTAS_CON_ALERTA = new Set<ConductaObservada>(["REACTIVO", "AGRESIVO"])

/**
 * Fecha local en `YYYY-MM-DD`.
 *
 * No se usa `toISOString()`: convierte a UTC, asi que entre las 21:00 y las
 * 24:00 de Argentina devuelve el dia siguiente y la ficha se archivaria bajo
 * una fecha que todavia no ocurrio.
 */
function hoy(): string {
  const d = new Date()
  const mes = String(d.getMonth() + 1).padStart(2, "0")
  const dia = String(d.getDate()).padStart(2, "0")
  return `${d.getFullYear()}-${mes}-${dia}`
}

/** `Matriz DNI 12345678 · Laura Sosa`, para distinguir dos pacientes homonimos. */
function etiquetaMascota(m: Mascota): string {
  const tutor = m.tutores?.[0]
  const nombreTutor = tutor
    ? `${tutor.nombre} ${tutor.apellido}`
    : "sin tutor"
  return `${m.nombre} · ${nombreTutor}`
}

/**
 * `Laura Sosa · MAT-123`, y solo la matricula si existe.
 *
 * `matricula` es `string | null` desde TASK-007: un auxiliar sin titulo
 * tramitado se puede dar de alta, y sin este guarda el selector renderizaba
 * literalmente "· null".
 */
function etiquetaProfesional(p: Profesional): string {
  return p.matricula ? `${p.nombreCompleto} · ${p.matricula}` : p.nombreCompleto
}

export function FichaMedica() {
  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const profesionales = useApi<Profesional[]>(() => apiProfesionales.listar())

  const [mascotaId, setMascotaId] = useState<number | null>(null)
  const [profesionalId, setProfesionalId] = useState<number | null>(null)
  const [turnoId, setTurnoId] = useState<number | null>(null)
  /**
   * Dia de la atencion. Va aparte de la fecha del turno porque el turno puede
   * no existir: un paciente que llega sin turno previo igual genera ficha, y en
   * ese caso la fecha es la que escribe el operador, no la de una grilla.
   */
  const [fechaAtencion, setFechaAtencion] = useState(hoy())
  const [anamnesis, setAnamnesis] = useState("")
  const [diagnostico, setDiagnostico] = useState("")
  const [tratamiento, setTratamiento] = useState("")
  const [conducta, setConducta] = useState<ConductaObservada>("TRANQUILO")
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  // Los turnos del dia son el origen natural del `turnoClinicoId` que hace
  // idempotente el alta: sin turno, la ficha puede cargarse dos veces.
  // El filtro `mascotaId` va como clave y no dentro de la fn porque `useApi`
  // guarda la fn en un ref: sin la clave, elegir otra mascota no recargaria y
  // el selector de turno seguiría ofreciendo los turnos del paciente anterior.
  const turnos = useApi<TurnoClinico[]>(
    () => apiTurnos.listar(fechaAtencion),
    mascotaId !== null,
    `${fechaAtencion}:${mascotaId}`,
  )

  // De los turnos del dia solo interesan los de la mascota elegida: ofrecer los
  // de otros pacientes invita a vincular una ficha con un turno ajeno.
  const turnosDeLaMascota = (turnos.datos ?? []).filter(
    (t) => t.mascota?.id === mascotaId && t.estado !== "CANCELADO",
  )

  const historial = useApi<ConsultaMedica[]>(
    () => apiConsultas.porMascota(mascotaId as number),
    mascotaId !== null,
  )

  async function registrar() {
    if (mascotaId === null) {
      setError("Elegí la mascota.")
      return
    }
    if (profesionalId === null) {
      setError("Elegí al profesional que hizo la atención.")
      return
    }

    setGuardando(true)
    setError(null)
    setExito(null)
    try {
      const ficha = await apiConsultas.registrar({
        mascotaId,
        profesionalId,
        turnoClinicoId: turnoId,
        // El backend usa esto como fecha de la ficha cuando no hay turno
        // asociado; mandarlo siempre evita que el servidor la fije a hoy.
        fechaAtencion: fechaAtencion || undefined,
        anamnesis: anamnesis.trim() || undefined,
        diagnostico: diagnostico.trim() || undefined,
        tratamiento: tratamiento.trim() || undefined,
        conductaObservada: conducta,
      })
      setExito(
        `Ficha #${ficha.id} guardada. Se emitió la comanda #${ficha.comandaId} para cobrar en recepción.`,
      )
      setAnamnesis("")
      setDiagnostico("")
      setTratamiento("")
      setConducta("TRANQUILO")
      setTurnoId(null)
      await historial.recargar()
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo guardar la ficha")
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Atención médica">
        <div className="flex flex-col gap-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">Mascota</span>
              <Select
                value={mascotaId ?? ""}
                onChange={(e) => {
                  setMascotaId(e.target.value ? Number(e.target.value) : null)
                  // El turno pertenece a la mascota anterior: dejarlo elegido
                  // seria una ficha cruzada sin que nadie lo note.
                  setTurnoId(null)
                }}
                className={TOQUE_MINIMO}
              >
                <option value="">Elegí una mascota…</option>
                {(mascotas.datos ?? []).map((m) => (
                  <option key={m.id} value={m.id}>
                    {etiquetaMascota(m)}
                  </option>
                ))}
              </Select>
              {mascotas.datos && mascotas.datos.length === 0 && (
                <p className="text-xs text-muted-foreground">
                  No hay mascotas dadas de alta. Cargalas en el Padrón.
                </p>
              )}
            </label>

            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">Profesional</span>
              <Select
                value={profesionalId ?? ""}
                onChange={(e) =>
                  setProfesionalId(e.target.value ? Number(e.target.value) : null)
                }
                className={TOQUE_MINIMO}
              >
                <option value="">Elegí un profesional…</option>
                {(profesionales.datos ?? [])
                  .filter((p) => p.activo)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {etiquetaProfesional(p)}
                    </option>
                  ))}
              </Select>
              {profesionales.datos && !profesionales.datos.some((p) => p.activo) && (
                <p className="text-xs text-muted-foreground">
                  No hay profesionales activos. Cargalos desde el Mostrador.
                </p>
              )}
            </label>
          </div>

          <label className="flex flex-col gap-1.5 text-sm sm:max-w-[220px]">
            <span className="text-xs font-medium text-muted-foreground">Fecha de la atención</span>
            <TextInput
              type="date"
              value={fechaAtencion}
              onChange={(e) => {
                setFechaAtencion(e.target.value || hoy())
                setTurnoId(null)
              }}
              className={TOQUE_MINIMO}
            />
            <span className="text-xs text-muted-foreground">
              Admite fechas pasadas: la consulta se documenta cuando ocurrió.
            </span>
          </label>

          {mascotaId !== null && turnosDeLaMascota.length > 0 && (
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">
                Turno (opcional, evita cargas duplicadas)
              </span>
              <Select
                value={turnoId ?? ""}
                onChange={(e) => setTurnoId(e.target.value ? Number(e.target.value) : null)}
                className={TOQUE_MINIMO}
              >
                <option value="">Sin turno asociado</option>
                {turnosDeLaMascota.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.fechaHoraInicio.slice(11, 16)} · {t.motivo}
                  </option>
                ))}
              </Select>
            </label>
          )}

          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Anamnesis</span>
            <TextArea
              value={anamnesis}
              onChange={(e) => setAnamnesis(e.target.value)}
              rows={2}
              className={TOQUE_MINIMO}
            />
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">Diagnóstico</span>
              <TextInput
                value={diagnostico}
                onChange={(e) => setDiagnostico(e.target.value)}
                className={TOQUE_MINIMO}
              />
            </label>

            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted-foreground">Tratamiento</span>
              <TextInput
                value={tratamiento}
                onChange={(e) => setTratamiento(e.target.value)}
                className={TOQUE_MINIMO}
              />
            </label>
          </div>

          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Conducta observada</span>
            <Select
              value={conducta}
              onChange={(e) => setConducta(e.target.value as ConductaObservada)}
              className={TOQUE_MINIMO}
            >
              {CONDUCTAS.map((c) => (
                <option key={c.valor} value={c.valor}>
                  {c.etiqueta}
                </option>
              ))}
            </Select>
            {CONDUCTAS_CON_ALERTA.has(conducta) && (
              <p className="flex items-center gap-1.5 text-xs text-amber-800">
                <TriangleAlert className="size-3.5 shrink-0" aria-hidden />
                Esta conducta va a quedar marcada como alerta en el retiro.
              </p>
            )}
          </label>

          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
          {exito && (
            <p className="text-sm text-emerald-800" role="status">
              {exito}
            </p>
          )}

          <button
            onClick={registrar}
            disabled={guardando || mascotaId === null || profesionalId === null}
            className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <Stethoscope className="size-4" aria-hidden />
            {guardando ? "Guardando…" : "Guardar ficha"}
          </button>

          <p className="text-xs text-muted-foreground">
            Esta pantalla no pide monto ni medio de pago: la consulta emite una comanda
            de cobro y el cobro se registra en recepción.
          </p>
        </div>
      </Panel>

      {mascotaId !== null && (
        <Panel title="Historial clínico">
          <Recurso
            estado={historial}
            textoCarga="Cargando historial"
            textoVacio="Esta mascota todavía no tiene fichas"
          >
            {(lista) => (
              <ul className="flex flex-col divide-y divide-border">
                {lista.map((f) => (
                  <li key={f.id} className="flex flex-col gap-1 py-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium">
                        {f.fechaAtencion.slice(0, 10)}
                      </p>
                      {f.alertaConducta ? (
                        <Semforo tono="ambar">
                          <TriangleAlert className="size-3" aria-hidden />
                          {f.conductaObservada}
                        </Semforo>
                      ) : (
                        <Semforo tono="verde">{f.conductaObservada}</Semforo>
                      )}
                    </div>
                    {f.diagnostico && <p className="text-sm">{f.diagnostico}</p>}
                    {f.tratamiento && (
                      <p className="text-xs text-muted-foreground">{f.tratamiento}</p>
                    )}
                    <p className="text-xs text-muted-foreground">
                      {f.profesional.nombreCompleto} · comanda #{f.comandaId}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </Recurso>
        </Panel>
      )}
    </div>
  )
}