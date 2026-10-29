"use client"

/**
 * TASK-009: checkout de egreso de la jornada.
 *
 * Antes de esta fase el módulo no tenía día: había que acordarse de qué animal
 * estaba en el local, buscarlo entre todos los pacientes, recién ahí elegir al
 * tutor y recién ahíenterarse de si podía salir. Ahora arranca en hoy, lista a
 * los pacientes con turno o guardería en esa fecha, y cada fila abre su semáforo
 * con un clic.
 *
 * La pantalla **no recalcula ninguna regla**. Si el semáforo dice rojo, es porque
 * el servidor dijo `puedeRetirar: false` con un `motivoBloqueo`: identidad primero
 * (quién se presenta), después deuda, y por último la alerta de conducta. La UI
 * solo pinta ese veredicto. Reimplementar el semáforo acá daría dos verdades y
 * el mostrador acabaría entregando un perro que el backend nunca autorizó.
 */

import { useState } from "react"
import {
  CheckCircle2,
  Clock,
  Home,
  LogOut,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  TriangleAlert,
} from "lucide-react"

import { ApiError, egreso as apiEgreso, hoyLocal } from "@/lib/api"
import type { EgresoValidacion, MascotaDelDia } from "@/lib/api"
import { useApi, mensajeDeError } from "@/lib/use-api"
import { Panel, Select, TextInput } from "@/components/ui/field"
import { DatePicker } from "@/components/ui/date-picker"
import { Modal } from "@/components/ui/modal"
import { ErrorBox, Semforo, TOQUE_MINIMO, formatearDni, money, soloDni } from "@/lib/vista"

const ETIQUETA_CONDUCTA: Record<string, string> = {
  normal: "normal",
  gruñe: "gruñe",
  muerde: "muerde",
  miedoso: "miedoso",
}

export function Egreso() {
  const [fecha, setFecha] = useState(hoyLocal())

  const pacientes = useApi<MascotaDelDia[]>(() => apiEgreso.pacientesDelDia(fecha), true, fecha)
  const [validando, setValidando] = useState<MascotaDelDia | null>(null)

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Checkout de egreso">
        <div className="flex flex-col gap-1.5 text-sm sm:max-w-xs">
          <DatePicker label="Jornada" value={fecha} onChange={setFecha} />
          <p className="text-xs text-muted-foreground">
            Pacientes con turno o guardería en esa fecha. Un retiro de ayer quedó
            pendiente, cambiá la fecha para encontrarlo.
          </p>
        </div>
      </Panel>

      <Panel title={`Pacientes del ${fecha.split("-").reverse().join("/")}`}>
        {pacientes.cargando && pacientes.datos === null ? (
          <p className="py-8 text-sm text-muted-foreground" role="status">
            Cargando jornada
          </p>
        ) : pacientes.error && pacientes.datos === null ? (
          <ErrorBox error={pacientes.error} onReintentar={pacientes.recargar} />
        ) : (pacientes.datos ?? []).length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            No hay pacientes con turno o guardería en esa jornada.
          </p>
        ) : (
          <ul className="flex flex-col divide-y divide-border">
            {(pacientes.datos ?? []).map((m) => (
              <li key={m.mascotaId} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                    {m.nombreMascota}
                    {m.conducta && m.conducta !== "normal" && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-900 dark:bg-amber-500/15 dark:text-amber-300">
                        <TriangleAlert className="size-3" aria-hidden />
                        {ETIQUETA_CONDUCTA[m.conducta] ?? m.conducta}
                      </span>
                    )}
                  </p>
                  <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
                    {m.motivos.includes("TURNO_CLINICO") && (
                      <span className="inline-flex items-center gap-1">
                        <Clock className="size-3" aria-hidden />
                        {m.cantidadTurnos} turno{m.cantidadTurnos === 1 ? "" : "s"}
                      </span>
                    )}
                    {m.estadoGuarderia && (
                      <span className="inline-flex items-center gap-1">
                        <Home className="size-3" aria-hidden />
                        Guardería {m.estadoGuarderia.toLowerCase()}
                        {m.horaIngreso ? ` · ${m.horaIngreso.slice(0, 5)}` : ""}
                        {m.horaSalida ? `–${m.horaSalida.slice(0, 5)}` : ""}
                      </span>
                    )}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {m.tutores.length === 0
                      ? "Sin tutores vinculados: validar por DNI."
                      : m.tutores
                          .map((t) => `${t.nombreCompleto}${t.autorizadoRetiro ? "" : " (sin retiro)"}`)
                          .join(" · ")}
                  </p>
                </div>

                {m.fechaHoraEntrega ? (
                  // Ya se registró la entrega: no se ofrece validar de nuevo.
                  <span className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                    <CheckCircle2 className="size-3.5" aria-hidden />
                    Entregado {m.fechaHoraEntrega.slice(11, 16)} a {m.entregadoA}
                  </span>
                ) : (
                  <button
                    onClick={() => setValidando(m)}
                    className={`inline-flex shrink-0 items-center justify-center gap-2 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 ${TOQUE_MINIMO}`}
                  >
                    <LogOut className="size-3.5" aria-hidden />
                    Validar egreso
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {validando && (
        <ModalValidar
          mascota={validando}
          fecha={fecha}
          abierto
          onCerrar={() => setValidando(null)}
          onValidado={async () => {
            setValidando(null)
            await pacientes.recargar()
          }}
        />
      )}
    </div>
  )
}

/**
 * Semáforo de una fila.
 *
 * Abre con el paciente ya elegido y los tutores vinculados como candidatos: en
 * el mostrador el caso normal es que se presente uno de ellos, y escribir un DNI
 * de memoria es una fuente de errores que el backend igual va a rechazar.
 *
 * El campo de DNI queda igual, para el caso real de un tutor que no está
 * vinculado en la base y hay que consultarlo por documento. Es el mismo endpoint y
 * la misma validación: dos caminos de entrada, un solo veredicto.
 */
function ModalValidar({
  mascota,
  fecha,
  abierto,
  onCerrar,
  onValidado,
}: {
  mascota: MascotaDelDia
  /** Jornada del checkout: la entrega se registra en esa fecha. */
  fecha: string
  abierto: boolean
  onCerrar: () => void
  onValidado: () => void | Promise<void>
}) {
  const primero = mascota.tutores[0]
  // Sin vínculos no hay lista que ofrecer, pero sí hay un endpoint por DNI. Si
  // el modo arrancara en "vinculado" el modal quedaría con una etiqueta vacía y
  // sin veredicto: ni rojo ni verde, nada. Arrancar en DNI pinta el bloqueo con
  // su motivo, que es lo que el mostrador necesita saber.
  const [modo, setModo] = useState<"vinculado" | "dni">(primero ? "vinculado" : "dni")
  const [tutorId, setTutorId] = useState<string>(primero ? String(primero.tutorId) : "")
  const [dni, setDni] = useState("")

  const validacion = useApi<EgresoValidacion>(
    () =>
      modo === "vinculado" && tutorId
        ? apiEgreso.validarPorTutorId(mascota.mascotaId, Number(tutorId))
        : apiEgreso.validarPorDni(mascota.mascotaId, dni.trim()),
    abierto && (modo === "dni" ? dni.trim().length > 0 : tutorId !== ""),
    `${mascota.mascotaId}|${modo}|${tutorId}|${dni}`,
  )

  const veredicto = validacion.datos
  const [registrando, setRegistrando] = useState(false)
  const [errorRegistro, setErrorRegistro] = useState<string | null>(null)

  // El backend vuelve a validar al registrar: si entre el veredicto y el clic
  // cambió algo (otra comanda, la autorización), responde 409 con el motivo.
  async function registrarEntrega() {
    if (!veredicto?.tutorId) return
    setRegistrando(true)
    setErrorRegistro(null)
    try {
      await apiEgreso.registrar(mascota.mascotaId, veredicto.tutorId, fecha)
      await onValidado()
    } catch (e) {
      setErrorRegistro(e instanceof ApiError ? mensajeDeError(e) : "No se pudo registrar la entrega")
      setRegistrando(false)
    }
  }
  const tutorElegido = mascota.tutores.find((t) => String(t.tutorId) === tutorId)

  return (
    <Modal open={abierto} onClose={onCerrar} title={`Egreso de ${mascota.nombreMascota}`}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Quién se presenta</span>
          {mascota.tutores.length > 0 && (
            <Select
              value={modo === "vinculado" ? tutorId : "__dni__"}
              onChange={(e) => {
                const valor = e.target.value
                if (valor === "__dni__") {
                  setModo("dni")
                } else {
                  setModo("vinculado")
                  setTutorId(valor)
                }
              }}
              aria-label="Tutor que se presenta"
              className={TOQUE_MINIMO}
            >
              {mascota.tutores.map((t) => (
                <option key={t.tutorId} value={String(t.tutorId)}>
                  {t.nombreCompleto} · DNI {formatearDni(t.dni)}
                  {t.autorizadoRetiro ? "" : " · no autorizado a retirar"}
                </option>
              ))}
              <option value="__dni__">Otro (buscar por DNI)</option>
            </Select>
          )}
          {modo === "dni" && (
            <TextInput
              value={dni}
              onChange={(e) => setDni(soloDni(e.target.value))}
              placeholder="DNI del tutor"
              inputMode="numeric"
              aria-label="DNI del tutor que se presenta"
              className={TOQUE_MINIMO}
            />
          )}
          {mascota.tutores.length === 0 && (
            <p className="text-xs text-muted-foreground">
              Sin tutores vinculados en la base: consultá por DNI. El servidor
              responderá con el motivo del bloqueo.
            </p>
          )}
          {tutorElegido && !tutorElegido.autorizadoRetiro && (
            <p className="text-xs text-amber-800 dark:text-amber-300">
              Este tutor está vinculado pero no tiene autorización de retiro. Puede
              hacer consultas y pagos; el retiro se valida aparte.
            </p>
          )}
        </div>

        {validacion.cargando && !veredicto && (
          <p className="py-4 text-sm text-muted-foreground" role="status">
            Validando retiro
          </p>
        )}

        {validacion.error && (
          <p className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {mensajeDeError(validacion.error)}
          </p>
        )}

        {veredicto && <Veredicto veredicto={veredicto} />}

        {errorRegistro && (
          <p className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive" role="alert">
            {errorRegistro}
          </p>
        )}

        {veredicto && (
          <button
            onClick={() => (veredicto.puedeRetirar ? void registrarEntrega() : onCerrar())}
            disabled={registrando}
            className={`w-full rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            {veredicto.puedeRetirar ? (registrando ? "Registrando…" : "Registrar entrega y cerrar") : "Cerrar"}
          </button>
        )}
      </div>
    </Modal>
  )
}

/**
 * Veredicto consolidado del backend.
 *
 * Se pinta entero y sin reinterpretar: el mensaje, el motivo y el saldo salen
 * del servidor. La única decisión de color es de qué lado de la puerta se está
 * (verde puede, rojo no, ámbar la alerta de conducta con el retiro permitido), y
 * esa lectura está en los tres casos documentados acá, no repartida en la vista.
 */
function Veredicto({ veredicto }: { veredicto: EgresoValidacion }) {
  const tono = veredicto.puedeRetirar
    ? veredicto.alertaConducta
      ? "ambar"
      : "verde"
    : "rojo"

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-muted-foreground">Veredicto</span>
        <Semforo tono={tono}>
          {veredicto.puedeRetirar ? "Puede retirarse" : "Retiro bloqueado"}
        </Semforo>
      </div>

      <div className="flex items-start gap-2 rounded-lg border border-border p-3 text-sm">
        {veredicto.puedeRetirar ? (
          veredicto.alertaConducta ? (
            <ShieldAlert className="mt-0.5 size-5 shrink-0 text-amber-600" aria-hidden />
          ) : (
            <ShieldCheck className="mt-0.5 size-5 shrink-0 text-emerald-600" aria-hidden />
          )
        ) : (
          <ShieldX className="mt-0.5 size-5 shrink-0 text-red-600" aria-hidden />
        )}
        <div>
          <p className="font-medium">{veredicto.etiquetaMotivo}</p>
          <p className="mt-1 leading-relaxed text-muted-foreground">{veredicto.mensaje}</p>
        </div>
      </div>

      {veredicto.tieneDeuda && (
        <p className="flex items-center justify-between rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900 dark:border-red-500/40 dark:bg-red-500/10 dark:text-red-300">
          <span>Deuda pendiente de {veredicto.mascotaNombre}</span>
          <span className="font-medium">{money(veredicto.saldoDeuda)}</span>
        </p>
      )}

      {veredicto.alertaConducta && (
        <p className="flex items-center gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-300">
          <TriangleAlert className="size-4 shrink-0" aria-hidden />
          <span>
            Ficha de conducta: {ETIQUETA_CONDUCTA[veredicto.conducta ?? "normal"] ?? "—"}
            {veredicto.conductaUltimaConsulta
              ? ` · última consulta: ${veredicto.conductaUltimaConsulta.toLowerCase()}`
              : ""}
            {veredicto.fechaUltimaConsulta ? ` (${veredicto.fechaUltimaConsulta.slice(0, 10)})` : ""}
          </span>
        </p>
      )}

      <p className="text-xs text-muted-foreground">
        Evaluado sobre {veredicto.mascotaNombre} para {veredicto.tutorNombre}.
      </p>
    </div>
  )
}

/** Reexportado para que el mostrador pueda abrir el circuito sin reimplementarlo. */
export { ModalValidar as ModalEgreso }
