"use client"

/**
 * TASK-007 (FASE 1): alta de mascota contra la API real.
 *
 * <p>Este componente es la correccion del sintoma reportado. Antes, la unica
 * pantalla que "daba de alta" una mascota era el modal de cliente del Mostrador,
 * que escribia en el store en memoria de `lib/store.tsx`: la pantalla aceptaba los
 * datos, el operador los veia aparecer y al recargar no habia nada, porque nada
 * habia salido del navegador.</p>
 *
 * <p>El alta usa las dos rutas que expone el backend, y elige entre ellas:</p>
 * <ul>
 *   <li>Con tutor elegido: un solo {@code POST /mascotas} con el tutor anidado en
 *       la lista {@code tutores}. Es una transaccion, asi que no puede quedar la
 *       mascota guardada sin su vinculo.</li>
 *   <li>Sin tutor: {@code POST /mascotas} pelado, y el vinculo se agrega despues
 *       desde el modal del tutor. El backend no exige tutor en el alta, y
 *       obligar a elegir uno dejaria fuera el caso real de un animal encontrado
 *       sin dueño presente.</li>
 * </ul>
 */

import { useState } from "react"
import { PawPrint, Plus } from "lucide-react"

import { mascotas as apiMascotas } from "@/lib/api"
import type { EspecieMascota, Tutor } from "@/lib/api"
import { Panel, Select, TextArea, TextInput } from "@/components/ui/field"
import { TOQUE_MINIMO } from "@/lib/vista"

/** Especies del enum `Mascota.Especie`. La etiqueta es la que ve el operador. */
const ESPECIES: { valor: EspecieMascota; etiqueta: string }[] = [
  { valor: "CANINO", etiqueta: "Canino" },
  { valor: "FELINO", etiqueta: "Felino" },
  { valor: "AVE", etiqueta: "Ave" },
  { valor: "ROEDOR", etiqueta: "Roedor" },
  { valor: "REPTIL", etiqueta: "Reptil" },
  { valor: "OTRO", etiqueta: "Otro" },
]

const CONDUCTAS = [
  { valor: "normal", etiqueta: "Normal" },
  { valor: "gruñe", etiqueta: "Gruñe" },
  { valor: "muerde", etiqueta: "Muerde" },
  { valor: "miedoso", etiqueta: "Miedoso" },
] as const

export function AltaMascota({
  tutores,
  onCreada,
}: {
  tutores: Tutor[]
  onCreada: (mascotaId: number) => void | Promise<void>
}) {
  const [nombre, setNombre] = useState("")
  const [especie, setEspecie] = useState<EspecieMascota>("CANINO")
  const [raza, setRaza] = useState("")
  const [sexo, setSexo] = useState<"" | "Macho" | "Hembra">("")
  const [fechaNacimiento, setFechaNacimiento] = useState("")
  const [peso, setPeso] = useState("")
  const [conducta, setConducta] = useState<"" | (typeof CONDUCTAS)[number]["valor"]>("")
  const [chip, setChip] = useState("")
  const [observaciones, setObservaciones] = useState("")
  const [tutorId, setTutorId] = useState<number | null>(null)
  const [autorizado, setAutorizado] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  function limpiar() {
    setNombre("")
    setEspecie("CANINO")
    setRaza("")
    setSexo("")
    setFechaNacimiento("")
    setPeso("")
    setConducta("")
    setChip("")
    setObservaciones("")
    setTutorId(null)
    setAutorizado(true)
    setError(null)
  }

  async function guardar() {
    setGuardando(true)
    setError(null)
    setExito(null)
    try {
      const pesoNum = peso.trim() === "" ? undefined : Number(peso)
      if (pesoNum !== undefined && (Number.isNaN(pesoNum) || pesoNum <= 0)) {
        setError("El peso tiene que ser un número mayor a 0.")
        return
      }

      const creada = await apiMascotas.crear({
        nombre: nombre.trim(),
        especie,
        raza: raza.trim() || undefined,
        sexo: sexo || undefined,
        // El backend rechaza fechas futuras con @Past, asi que se manda solo si
        // hay valor: mandar "" no es "sin fecha", es una fecha inválida.
        fechaNacimiento: fechaNacimiento || undefined,
        pesoKg: pesoNum,
        conducta: conducta || undefined,
        chip: chip.trim() || undefined,
        observaciones: observaciones.trim() || undefined,
        // El flag viaja explicito en la tupla, nunca se deja que el backend lo
        // asuma (regla 2.4). `null` significa "sin tutor todavia".
        tutores:
          tutorId === null
            ? undefined
            : [{ tutorId, autorizadoRetiro: autorizado }],
      })

      setExito(
        tutorId === null
          ? `${creada.nombre} quedó cargada sin tutor. Vinculala desde el tutor cuando aparezca.`
          : `${creada.nombre} quedó vinculada a ${tutores.find((t) => t.id === tutorId)?.nombreCompleto ?? "el tutor"}.`,
      )
      limpiar()
      await onCreada(creada.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo crear la mascota")
    } finally {
      setGuardando(false)
    }
  }

  const listo = nombre.trim().length > 0

  return (
    <Panel
      title="Nueva mascota"
      action={
        <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <PawPrint className="size-3.5" aria-hidden />
          Se guarda en la base
        </span>
      }
    >
      <div className="flex flex-col gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Nombre" valor={nombre} onChange={setNombre} placeholder="Ej: Firulais" />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Especie</span>
            <Select
              value={especie}
              onChange={(e) => setEspecie(e.target.value as EspecieMascota)}
              className={TOQUE_MINIMO}
            >
              {ESPECIES.map((e) => (
                <option key={e.valor} value={e.valor}>
                  {e.etiqueta}
                </option>
              ))}
            </Select>
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Campo etiqueta="Raza" valor={raza} onChange={setRaza} placeholder="Ej: Mestizo" />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Sexo</span>
            <Select
              value={sexo}
              onChange={(e) => setSexo(e.target.value as "" | "Macho" | "Hembra")}
              className={TOQUE_MINIMO}
            >
              <option value="">Sin especificar</option>
              <option value="Macho">Macho</option>
              <option value="Hembra">Hembra</option>
            </Select>
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Fecha de nacimiento</span>
            <TextInput
              type="date"
              value={fechaNacimiento}
              max={new Date().toISOString().slice(0, 10)}
              onChange={(e) => setFechaNacimiento(e.target.value)}
              className={TOQUE_MINIMO}
            />
          </label>
          <Campo
            etiqueta="Peso (kg)"
            valor={peso}
            onChange={setPeso}
            inputMode="decimal"
            placeholder="Ej: 12.5"
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="text-xs font-medium text-muted-foreground">Conducta</span>
            <Select
              value={conducta}
              onChange={(e) =>
                setConducta(e.target.value as "" | (typeof CONDUCTAS)[number]["valor"])
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
          </label>
          <Campo etiqueta="Chip" valor={chip} onChange={setChip} placeholder="Opcional" />
        </div>

        <label className="flex flex-col gap-1.5 text-sm">
          <span className="text-xs font-medium text-muted-foreground">Observaciones</span>
          <TextArea
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            placeholder="Opcional"
          />
        </label>

        <div className="rounded-md border border-border p-3">
          <p className="mb-2 text-xs font-medium text-muted-foreground">
            Vincular a un tutor
          </p>

          {tutores.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Todavía no hay tutores cargados. Se puede guardar la mascota igual y
              vincularla después.
            </p>
          ) : (
            <>
              <Select
                value={tutorId ?? ""}
                onChange={(e) => setTutorId(e.target.value ? Number(e.target.value) : null)}
                className={TOQUE_MINIMO}
                aria-label="Tutor de la mascota"
              >
                <option value="">Sin tutor por ahora</option>
                {tutores.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombreCompleto} · DNI {t.dni}
                  </option>
                ))}
              </Select>

              {tutorId !== null && (
                <label className="mt-2 flex min-h-[44px] items-center gap-3 rounded-md border border-border px-3">
                  <input
                    type="checkbox"
                    checked={autorizado}
                    onChange={(e) => setAutorizado(e.target.checked)}
                    className="size-4"
                  />
                  <span className="text-sm">
                    Autorizado a retirar
                    <span className="block text-xs text-muted-foreground">
                      Si está apagado, este tutor queda como cotutor y no puede llevarse a la mascota.
                    </span>
                  </span>
                </label>
              )}
            </>
          )}
        </div>

        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        {exito && (
          <p className="text-sm text-emerald-700" role="status">
            {exito}
          </p>
        )}

        <button
          onClick={guardar}
          disabled={guardando || !listo}
          className={`inline-flex items-center justify-center gap-2 rounded-md bg-primary text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60 ${TOQUE_MINIMO}`}
        >
          <Plus className="size-4" aria-hidden />
          {guardando ? "Guardando…" : "Guardar mascota"}
        </button>
      </div>
    </Panel>
  )
}

function Campo({
  etiqueta,
  valor,
  onChange,
  inputMode,
  placeholder,
}: {
  etiqueta: string
  valor: string
  onChange: (v: string) => void
  inputMode?: "numeric" | "tel" | "decimal"
  placeholder?: string
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm">
      <span className="text-xs font-medium text-muted-foreground">{etiqueta}</span>
      <TextInput
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        inputMode={inputMode}
        placeholder={placeholder}
        className={TOQUE_MINIMO}
      />
    </label>
  )
}
