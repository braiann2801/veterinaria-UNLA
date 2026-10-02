"use client"

/**
 * TASK-006: modulo de Egreso con semáforo.
 *
 * El recepcionista elige la mascota y teclea el DNI de quien se presenta; el
 * backend responde con el veredicto consolidado y esta pantalla lo pinta. No
 * recalcula ninguna regla: si el semáforo dice rojo, es porque el servidor dijo
 * `puedeRetirar: false`.
 */

import { useMemo, useState } from "react"
import { LogOut, ShieldAlert, ShieldCheck, ShieldX, TriangleAlert } from "lucide-react"

import { egreso, mascotas as apiMascotas, tutores as apiTutores, ApiError } from "@/lib/api"
import type { EgresoValidacion, Mascota, Tutor } from "@/lib/api"
import { useApi } from "@/lib/use-api"
import { Panel, Select, TextInput } from "@/components/ui/field"
import { Semforo, TOQUE_MINIMO, money } from "@/lib/vista"
import { Modal } from "@/components/ui/modal"

const ETIQUETA_CONDUCTA: Record<string, string> = {
  normal: "normal",
  "gruñe": "gruñe",
  muerde: "muerde",
  miedoso: "miedoso",
}

export function Egreso() {
  const [mascotaId, setMascotaId] = useState<string>("")
  const [dni, setDni] = useState<string>("")
  const [consultado, setConsultado] = useState<string>("")
  const [veredicto, setVeredicto] = useState<EgresoValidacion | null>(null)
  const [consultando, setConsultando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mascotas = useApi<Mascota[]>(() => apiMascotas.listar())
  const tutores = useApi<Tutor[]>(() => apiTutores.listar())

  // Un DNI solo sirve si la persona esta registrada: se ofrece la lista para
  // que el mostrador elija en vez de adivinar el numero.
  const tutoresFiltrados = useMemo(() => {
    const todos = tutores.datos ?? []
    if (dni.trim().length === 0) return todos
    const q = dni.trim().toUpperCase()
    return todos.filter(
      (t) => t.dni.toUpperCase().includes(q) || t.nombreCompleto.toUpperCase().includes(q),
    )
  }, [tutores.datos, dni])

  async function validar() {
    if (!mascotaId) {
      setError("Elegí la mascota que se va a retirar.")
      return
    }
    if (!dni.trim()) {
      setError("Ingresá el DNI de quien se presenta.")
      return
    }
    setConsultando(true)
    setError(null)
    try {
      const r = await egreso.validarPorDni(Number(mascotaId), dni.trim())
      setVeredicto(r)
      setConsultado(`${mascotaId}|${dni.trim()}`)
    } catch (err) {
      setVeredicto(null)
      setError(err instanceof ApiError ? err.message : "No se pudo validar el retiro")
    } finally {
      setConsultando(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Egreso seguro">
        <p className="mb-4 text-sm text-muted-foreground">
          Verificá autorización, deuda y conducta antes de entregar la mascota.
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Mascota</span>
            <Select value={mascotaId} onChange={(e) => setMascotaId(e.target.value)} className={TOQUE_MINIMO}>
              <option value="">Elegí una mascota…</option>
              {(mascotas.datos ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nombre}
                </option>
              ))}
            </Select>
          </label>

          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">DNI de quien retira</span>
            <TextInput
              value={dni}
              onChange={(e) => setDni(e.target.value)}
              placeholder="30111222"
              inputMode="numeric"
              className={TOQUE_MINIMO}
            />
          </label>
        </div>

        {error && (
          <p className="mt-3 text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <div className="mt-4 flex flex-wrap gap-2">
          <button
            onClick={validar}
            disabled={consultando}
            className={`inline-flex items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-opacity disabled:opacity-60 ${TOQUE_MINIMO}`}
          >
            <LogOut className="size-4" aria-hidden />
            {consultando ? "Verificando…" : "Validar retiro"}
          </button>
          {veredicto && (
            <button
              onClick={() => {
                setVeredicto(null)
                setConsultado("")
              }}
              className={`rounded-md border border-border px-4 text-sm font-medium transition-colors hover:bg-muted ${TOQUE_MINIMO}`}
            >
              Limpiar
            </button>
          )}
        </div>

        {consultado && tutores.datos && tutores.datos.length === 0 && (
          <p className="mt-3 text-xs text-muted-foreground">
            Todavía no hay tutores cargados. Cargá uno en el padrón para poder validar el retiro.
          </p>
        )}
      </Panel>

      {veredicto && <TarjetaEgreso veredicto={veredicto} />}
    </div>
  )
}

function TarjetaEgreso({ veredicto }: { veredicto: EgresoValidacion }) {
  const tono = !veredicto.puedeRetirar ? "rojo" : veredicto.alertaConducta ? "ambar" : "verde"
  const Icono = !veredicto.puedeRetirar ? ShieldX : veredicto.alertaConducta ? TriangleAlert : ShieldCheck

  return (
    <div className="flex flex-col gap-4">
      <Panel title="Resultado">
        <p className="mb-3 text-xs text-muted-foreground">
          {veredicto.mascotaNombre} · {veredicto.tutorNombre}
        </p>
        <div className="flex items-start gap-3">
          <Icono
            className={`mt-0.5 size-6 shrink-0 ${
              tono === "verde" ? "text-emerald-600" : tono === "ambar" ? "text-amber-600" : "text-red-600"
            }`}
            aria-hidden
          />
          <div className="flex flex-col gap-2">
            <Semforo tono={tono}>{veredicto.etiquetaMotivo}</Semforo>
            <p className="text-sm leading-relaxed">{veredicto.mensaje}</p>
          </div>
        </div>
      </Panel>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Detalle titulo="Autorización de retiro" ok={veredicto.tutorAutorizado}>
          {veredicto.tutorAutorizado ? "Tutor habilitado" : "Tutor no habilitado"}
        </Detalle>

        <Detalle titulo="Estado contable" ok={!veredicto.tieneDeuda}>
          {veredicto.tieneDeuda ? `${money(veredicto.saldoDeuda)} pendientes` : "Sin deuda"}
        </Detalle>

        <Detalle titulo="Conducta" ok={!veredicto.alertaConducta}>
          {veredicto.conducta ? ETIQUETA_CONDUCTA[veredicto.conducta] ?? veredicto.conducta : "—"}
        </Detalle>
      </div>

      {veredicto.alertaConducta && (
        <Panel title="Precauciones de entrega">
          <p className="text-sm leading-relaxed">
            Esta mascota tiene alerta de conducta. Avisale al receptor que tome las
            precauciones correspondientes; la alerta no impide la entrega.
          </p>
          {veredicto.conductaUltimaConsulta && (
            <p className="mt-2 text-xs text-muted-foreground">
              Última atención: {veredicto.conductaUltimaConsulta}
              {veredicto.fechaUltimaConsulta ? ` (${veredicto.fechaUltimaConsulta.slice(0, 10)})` : ""}
            </p>
          )}
        </Panel>
      )}
    </div>
  )
}

function Detalle({
  titulo,
  ok,
  children,
}: {
  titulo: string
  ok: boolean
  children: React.ReactNode
}) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
        {ok ? (
          <ShieldCheck className="size-3.5 text-emerald-600" aria-hidden />
        ) : (
          <ShieldAlert className="size-3.5 text-red-600" aria-hidden />
        )}
        {titulo}
      </p>
      <p className="mt-1 text-sm font-medium">{children}</p>
    </div>
  )
}

/** Modal de checkout rápido para usar desde la lista de mascotas. */
export function ModalEgreso({
  mascotaId,
  abierto,
  onCerrar,
}: {
  mascotaId: number | null
  abierto: boolean
  onCerrar: () => void
}) {
  const [dni, setDni] = useState("")
  const [veredicto, setVeredicto] = useState<EgresoValidacion | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  async function validar() {
    if (!mascotaId || !dni.trim()) {
      setError("Ingresá el DNI de quien retira.")
      return
    }
    setCargando(true)
    setError(null)
    try {
      setVeredicto(await egreso.validarPorDni(mascotaId, dni.trim()))
    } catch (err) {
      setVeredicto(null)
      setError(err instanceof ApiError ? err.message : "No se pudo validar")
    } finally {
      setCargando(false)
    }
  }

  return (
    <Modal open={abierto} onClose={onCerrar} title="Egreso de mascota">
      <div className="flex flex-col gap-3 p-4">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">DNI de quien retira</span>
          <TextInput
            value={dni}
            onChange={(e) => setDni(e.target.value)}
            inputMode="numeric"
            placeholder="30111222"
            className={TOQUE_MINIMO}
          />
        </label>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        <button
          onClick={validar}
          disabled={cargando}
          className={`rounded-md bg-primary text-sm font-medium text-primary-foreground disabled:opacity-60 ${TOQUE_MINIMO}`}
        >
          {cargando ? "Verificando…" : "Verificar retiro"}
        </button>

        {veredicto && (
          <div className="rounded-lg border border-border p-3">
            <Semforo tono={veredicto.puedeRetirar ? (veredicto.alertaConducta ? "ambar" : "verde") : "rojo"}>
              {veredicto.etiquetaMotivo}
            </Semforo>
            <p className="mt-2 text-sm">{veredicto.mensaje}</p>
          </div>
        )}
      </div>
    </Modal>
  )
}