"use client"

/**
 * Cliente nuevo o existente + mascota nueva o existente, en un solo bloque.
 *
 * <p>Lo usan el alta de turno ("Cliente o mascota nuevos") y el completado de
 * datos de una urgencia. Es controlado: el estado vive en el padre, que con
 * `seleccionLista` sabe si se puede guardar y con `aPedido` arma el cuerpo que
 * espera el backend (`tutorId`/`tutorNuevo` + `mascotaId`/`mascotaNueva`).</p>
 */

import { hoyLocal } from "@/lib/api"
import type { EspecieMascota, MascotaInput, Tutor, TutorInput } from "@/lib/api"
import { DatePicker } from "@/components/ui/date-picker"
import { Field, Select, TextInput } from "@/components/ui/field"
import { cn } from "@/lib/utils"
import { ESPECIES, TOQUE_MINIMO, formatearDni, soloDni, soloTelefono } from "@/lib/vista"

type ModoCliente = "existente" | "nuevo"
type ModoMascota = "existente" | "nueva"

export type SeleccionClienteMascota = {
  modoCliente: ModoCliente
  modoMascota: ModoMascota
  tutorId: string
  mascotaId: string
  tutorNuevo: { nombre: string; apellido: string; dni: string; telefono: string }
  mascotaNueva: {
    nombre: string
    especie: EspecieMascota
    sexo: "" | "Macho" | "Hembra"
    raza: string
    fechaNacimiento: string
  }
}

export const SELECCION_INICIAL: SeleccionClienteMascota = {
  modoCliente: "nuevo",
  modoMascota: "nueva",
  tutorId: "",
  mascotaId: "",
  tutorNuevo: { nombre: "", apellido: "", dni: "", telefono: "" },
  mascotaNueva: { nombre: "", especie: "CANINO", sexo: "", raza: "", fechaNacimiento: "" },
}

/** Un cliente nuevo no tiene mascotas: la suya se carga nueva en el mismo paso. */
function mascotaEsNueva(sel: SeleccionClienteMascota) {
  return sel.modoCliente === "nuevo" || sel.modoMascota === "nueva"
}

/** Si ya están los datos mínimos para mandar el pedido. */
export function seleccionLista(sel: SeleccionClienteMascota): boolean {
  const clienteListo =
    sel.modoCliente === "nuevo"
      ? sel.tutorNuevo.nombre.trim() !== "" && sel.tutorNuevo.apellido.trim() !== "" && sel.tutorNuevo.dni.length >= 7
      : sel.tutorId !== ""
  const mascotaLista = mascotaEsNueva(sel) ? sel.mascotaNueva.nombre.trim() !== "" : sel.mascotaId !== ""
  return clienteListo && mascotaLista
}

/** Cuerpo para el backend. Los opcionales van como `undefined` y no como "": el backend los valida con @Pattern. */
export function aPedido(sel: SeleccionClienteMascota): {
  tutorId?: number
  tutorNuevo?: TutorInput
  mascotaId?: number
  mascotaNueva?: Omit<MascotaInput, "tutores">
} {
  const nuevo = sel.modoCliente === "nuevo"
  return {
    tutorId: nuevo ? undefined : Number(sel.tutorId),
    tutorNuevo: nuevo
      ? {
          nombre: sel.tutorNuevo.nombre.trim(),
          apellido: sel.tutorNuevo.apellido.trim(),
          dni: sel.tutorNuevo.dni,
          telefono: sel.tutorNuevo.telefono.trim() || undefined,
        }
      : undefined,
    mascotaId: mascotaEsNueva(sel) ? undefined : Number(sel.mascotaId),
    mascotaNueva: mascotaEsNueva(sel)
      ? {
          nombre: sel.mascotaNueva.nombre.trim(),
          especie: sel.mascotaNueva.especie,
          sexo: sel.mascotaNueva.sexo || undefined,
          raza: sel.mascotaNueva.raza.trim() || undefined,
          // "" no es "sin fecha": el backend rechaza lo que no sea una fecha pasada.
          fechaNacimiento: sel.mascotaNueva.fechaNacimiento || undefined,
        }
      : undefined,
  }
}

export function ClienteMascotaForm({
  valor,
  onChange,
  tutores,
}: {
  valor: SeleccionClienteMascota
  onChange: (valor: SeleccionClienteMascota) => void
  tutores: Tutor[]
}) {
  const clienteNuevo = valor.modoCliente === "nuevo"
  const tutorElegido = tutores.find((t) => String(t.id) === valor.tutorId) ?? null
  const set = (cambios: Partial<SeleccionClienteMascota>) => onChange({ ...valor, ...cambios })
  const setTutorNuevo = (cambios: Partial<SeleccionClienteMascota["tutorNuevo"]>) =>
    set({ tutorNuevo: { ...valor.tutorNuevo, ...cambios } })
  const setMascotaNueva = (cambios: Partial<SeleccionClienteMascota["mascotaNueva"]>) =>
    set({ mascotaNueva: { ...valor.mascotaNueva, ...cambios } })

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-muted/30 p-3">
      <Segmentado
        etiqueta="Cliente"
        valor={valor.modoCliente}
        onChange={(modo) => set({ modoCliente: modo, mascotaId: "" })}
        opciones={[
          { valor: "nuevo", etiqueta: "Cliente nuevo" },
          { valor: "existente", etiqueta: "Cliente existente" },
        ]}
      />

      {clienteNuevo ? (
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Nombre">
            <TextInput
              value={valor.tutorNuevo.nombre}
              onChange={(e) => setTutorNuevo({ nombre: e.target.value })}
              maxLength={80}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Apellido">
            <TextInput
              value={valor.tutorNuevo.apellido}
              onChange={(e) => setTutorNuevo({ apellido: e.target.value })}
              maxLength={80}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="DNI">
            <TextInput
              value={valor.tutorNuevo.dni}
              onChange={(e) => setTutorNuevo({ dni: soloDni(e.target.value) })}
              inputMode="numeric"
              placeholder="30123456"
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Teléfono (opcional)">
            <TextInput
              value={valor.tutorNuevo.telefono}
              onChange={(e) => setTutorNuevo({ telefono: soloTelefono(e.target.value) })}
              inputMode="tel"
              placeholder="11 2345-6789"
              className={TOQUE_MINIMO}
            />
          </Field>
        </div>
      ) : (
        <>
          <Field label="Cliente">
            <Select
              value={valor.tutorId}
              // La mascota elegida era de otro cliente.
              onChange={(e) => set({ tutorId: e.target.value, mascotaId: "" })}
              className={TOQUE_MINIMO}
            >
              <option value="">Elegí un cliente…</option>
              {tutores
                .filter((t) => t.activo)
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombreCompleto} · DNI {formatearDni(t.dni)}
                  </option>
                ))}
            </Select>
          </Field>
          <Segmentado
            etiqueta="Mascota"
            valor={valor.modoMascota}
            onChange={(modo) => set({ modoMascota: modo, mascotaId: "" })}
            opciones={[
              { valor: "existente", etiqueta: "Mascota existente" },
              { valor: "nueva", etiqueta: "Mascota nueva" },
            ]}
          />
          {valor.modoMascota === "existente" && (
            <Field label="Mascota del cliente">
              <Select
                value={valor.mascotaId}
                onChange={(e) => set({ mascotaId: e.target.value })}
                disabled={!tutorElegido}
                className={TOQUE_MINIMO}
              >
                <option value="">{tutorElegido ? "Elegí una mascota…" : "Primero elegí el cliente"}</option>
                {(tutorElegido?.mascotas ?? [])
                  .filter((m) => m.activo)
                  .map((m) => (
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

      {mascotaEsNueva(valor) && (
        <div className="grid gap-3 sm:grid-cols-2">
          {clienteNuevo && (
            <p className="text-xs text-muted-foreground sm:col-span-2">
              La mascota del cliente nuevo se carga acá mismo y queda autorizada a retirarla.
            </p>
          )}
          <Field label="Nombre de la mascota">
            <TextInput
              value={valor.mascotaNueva.nombre}
              onChange={(e) => setMascotaNueva({ nombre: e.target.value })}
              maxLength={80}
              className={TOQUE_MINIMO}
            />
          </Field>
          <Field label="Especie">
            <Select
              value={valor.mascotaNueva.especie}
              onChange={(e) => setMascotaNueva({ especie: e.target.value as EspecieMascota })}
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
              value={valor.mascotaNueva.sexo}
              onChange={(e) => setMascotaNueva({ sexo: e.target.value as "" | "Macho" | "Hembra" })}
              className={TOQUE_MINIMO}
            >
              <option value="">Sin indicar</option>
              <option value="Macho">Macho</option>
              <option value="Hembra">Hembra</option>
            </Select>
          </Field>
          <Field label="Raza (opcional)">
            <TextInput
              value={valor.mascotaNueva.raza}
              onChange={(e) => setMascotaNueva({ raza: e.target.value })}
              maxLength={80}
              className={TOQUE_MINIMO}
            />
          </Field>
          <DatePicker
            label="Fecha de nacimiento (opcional)"
            value={valor.mascotaNueva.fechaNacimiento}
            max={hoyLocal()}
            onChange={(nueva) => setMascotaNueva({ fechaNacimiento: nueva })}
            limpiable
          />
        </div>
      )}
    </div>
  )
}

/** Dos opciones excluyentes como botones, con el área táctil mínima. */
export function Segmentado<T extends string>({
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
              valor === o.valor ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
            )}
          >
            {o.etiqueta}
          </button>
        ))}
      </div>
    </div>
  )
}
