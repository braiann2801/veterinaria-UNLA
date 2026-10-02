/**
 * Cliente HTTP contra el backend Spring Boot.
 *
 * Un solo modulo conoce la URL base, el manejo de errores y la forma de los
 * DTOs. Los componentes no hacen fetch: piden datos a los hooks de
 * `lib/use-api.ts`. Si esta capa cambia, el cambio no se propaga a la UI.
 *
 * Todas las listas y DTOs usan los nombres exactos que emite Jackson, para que
 * el tipado del frontend detecte cualquier desalineacion con el backend en
 * lugar de fallar en runtime con `undefined`.
 */

export const API_BASE =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api/v1"

// ------------------------------------------------------------------ errores

/**
 * Error de API con el status HTTP a mano.
 *
 * El frontend necesita distinguir 400 (el formulario esta mal) de 404 (no
 * existe) de 409 (la regla de negocio lo veta) de 500 (caigo algo). Sin el
 * status, cada pantalla terminaria mostrando "algo fallo" para todo y el
 * operador no podria saber que corregir.
 */
export class ApiError extends Error {
  readonly status: number
  readonly detalles: string[]
  readonly ruta: string

  constructor(status: number, mensaje: string, ruta: string, detalles: string[] = []) {
    super(mensaje)
    this.name = "ApiError"
    this.status = status
    this.ruta = ruta
    this.detalles = detalles
  }

  /** 400: el usuario tiene que corregir algo en lo que escribio. */
  get esValidacion(): boolean {
    return this.status === 400
  }

  /** 404: la entidad no existe. Vale la pena recargar los listados. */
  get esNoEncontrado(): boolean {
    return this.status === 404
  }

  /** 409: la regla de negocio lo veta (aforo, turno solapado, doble cobro). */
  get esConflicto(): boolean {
    return this.status === 409
  }

  /** El backend esta caido o el frontend no puede alcanzarlo. */
  get esServidor(): boolean {
    return this.status >= 500 || this.status === 0
  }
}

/**
 * Error de transporte: no hubo respuesta.
 *
 * Se distingue del 500 a proposito. "El backend no esta prendido" y "el backend
 * tiro una excepcion" son dos mensajes distintos para el operador, y el primero
 * tiene una accion concreta: prender el servidor.
 */
export class ApiConnectionError extends ApiError {
  constructor(causa?: unknown) {
    super(0, "No se pudo conectar con el servidor. Verificá que el backend esté corriendo en el puerto 8080.", "/", [])
    this.name = "ApiConnectionError"
    this.cause = causa
  }
}

// ---------------------------------------------------------------- transporte

interface ApiErrorBody {
  mensaje?: string
  detalles?: string[]
  status?: number
  ruta?: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE}${path}`

  let respuesta: Response
  try {
    respuesta = await fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...init?.headers,
      },
      // El backend es el estado de verdad: la cache del navegador solo sirve
      // para que el mostrador vea una lista vieja justo cuando esta por cobrar.
      cache: "no-store",
    })
  } catch (causa) {
    throw new ApiConnectionError(causa)
  }

  if (!respuesta.ok) {
    let cuerpo: ApiErrorBody = {}
    try {
      cuerpo = (await respuesta.json()) as ApiErrorBody
    } catch {
      // Un body no-JSON (un 502 de un proxy, por ejemplo) no debe tapar el status.
    }
    throw new ApiError(
      respuesta.status,
      cuerpo.mensaje ?? `Error ${respuesta.status} al llamar a ${path}`,
      cuerpo.ruta ?? path,
      cuerpo.detalles ?? [],
    )
  }

  if (respuesta.status === 204) {
    return undefined as T
  }
  return (await respuesta.json()) as T
}

const get = <T,>(path: string) => request<T>(path)
const post = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) })
const put = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: "PUT", body: body === undefined ? undefined : JSON.stringify(body) })
const del = <T,>(path: string) => request<T>(path, { method: "DELETE" })

// -------------------------------------------------------------------- tipos

export type ConductaMascota = "normal" | "gruñe" | "muerde" | "miedoso"
export type ConductaObservada = "TRANQUILO" | "NERVIOSO" | "REACTIVO" | "AGRESIVO"
export type MetodoPago = "EFECTIVO" | "TRANSFERENCIA" | "DEBITO" | "CREDITO" | "SENA"
export type ConceptoCobro = "CONSULTA_CLINICA" | "GUARDERIA" | "OTRO"
export type EstadoComanda = "PENDIENTE" | "PARCIAL" | "PAGADA" | "CANCELADA"
export type MotivoBloqueo = "NINGUNO" | "TUTOR_NO_AUTORIZADO" | "DEUDA_PENDIENTE"

export interface TutorResumen {
  id: number
  nombre: string
  apellido: string
  telefono: string | null
  autorizadoRetiro: boolean
}

export interface MascotaVinculada {
  id: number
  nombre: string
  especie: string
  raza: string | null
  autorizadoRetiro: boolean
}

export interface Tutor {
  id: number
  nombre: string
  apellido: string
  dni: string
  telefono: string | null
  email: string | null
  direccion: string | null
  nombreCompleto: string
  mascotas: MascotaVinculada[]
}

export interface Mascota {
  id: number
  nombre: string
  especie: string
  raza: string | null
  sexo: string | null
  fechaNacimiento: string | null
  pesoKg: number | null
  conducta: ConductaMascota | null
  chip: string | null
  observaciones: string | null
  tutores: TutorResumen[]
}

export interface Profesional {
  id: number
  nombre: string
  apellido: string
  nombreCompleto: string
  matricula: string
  activo: boolean
}

export interface TurnoClinico {
  id: number
  fechaHoraInicio: string
  fechaHoraFin: string
  fechaFinBloque: string
  motivo: string
  estado: "PENDIENTE" | "ATENDIDO" | "CANCELADO"
  mascota: { id: number; nombre: string }
  profesional: { id: number; nombreCompleto: string; matricula: string }
}

export interface TurnoClinicoInput {
  fechaHoraInicio: string
  motivo: string
  mascotaId: number
  profesionalId: number
}

export interface AforoGuarderia {
  fecha: string
  cuposOcupados: number
  aforoMaximo: number
  cuposDisponibles: number
  completo: boolean
}

export interface ReservaGuarderia {
  id: number
  fecha: string
  tipoEstadia: "DIURNA" | "NOCTURNA" | "COMPLETA_24H"
  estado: string
  mascotaId: number
  nombreMascota: string
  sena: number
  observaciones: string | null
  cuposOcupados: number
  aforoMaximo: number
}

export interface ReservaGuarderiaInput {
  fecha: string
  tipoEstadia: "DIURNA" | "NOCTURNA" | "COMPLETA_24H"
  mascotaId: number
  sena: number
  observaciones?: string
}

export interface ConsultaMedica {
  id: number
  turnoClinicoId: number | null
  fechaAtencion: string
  anamnesis: string | null
  diagnostico: string | null
  tratamiento: string | null
  conductaObservada: ConductaObservada
  alertaConducta: boolean
  mascota: { id: number; nombre: string; especie: string }
  profesional: { id: number; nombreCompleto: string; matricula: string }
  comandaId: number
}

/**
 * Alta de ficha clinica.
 *
 * Deliberadamente NO tiene monto, metodoPago ni precio (regla 2.3 de AGENTS.md).
 * Tampoco `alertaConducta`: el servidor la deriva de `conductaObservada`, y
 * aceptarla por request seria darle al cliente poder de apagarla.
 */
export interface ConsultaMedicaInput {
  turnoClinicoId?: number | null
  mascotaId: number
  profesionalId: number
  fechaAtencion?: string
  anamnesis?: string
  diagnostico?: string
  tratamiento?: string
  conductaObservada?: ConductaObservada
}

export interface Pago {
  id: number
  metodoPago: MetodoPago
  monto: number
  fechaHora: string
}

export interface ComandaCobro {
  id: number
  concepto: ConceptoCobro
  montoTotal: number
  saldoPendiente: number
  /** Derivado en el backend como montoTotal - saldoPendiente. */
  totalPagado: number
  estado: EstadoComanda
  tutorId: number
  nombreTutor: string
  mascotaId: number | null
  nombreMascota: string | null
  consultaMedicaId: number | null
  fechaEmision: string
  pagos: Pago[]
}

export interface ComandaPendiente {
  id: number
  concepto: ConceptoCobro
  saldoPendiente: number
  fechaEmision: string
  diasDeAntiguedad: number
}

export interface DeudaTutor {
  tutorId: number
  nombreTutor: string
  tieneDeuda: boolean
  totalDeuda: number
  cantidadComandas: number
  comandasPendientes: ComandaPendiente[]
}

export interface CierreCaja {
  fecha: string
  totalCobrado: number
  cantidadPagos: number
  cantidadComandas: number
  totalesPorMetodo: Record<string, number>
}

/** Veredicto del circuito de egreso (TASK-006). */
export interface EgresoValidacion {
  puedeRetirar: boolean
  motivoBloqueo: MotivoBloqueo
  tutorAutorizado: boolean
  tieneDeuda: boolean
  saldoDeuda: number
  alertaConducta: boolean
  conducta: ConductaMascota | null
  conductaUltimaConsulta: ConductaObservada | null
  fechaUltimaConsulta: string | null
  mensaje: string
  mascotaId: number
  mascotaNombre: string
  tutorId: number
  tutorNombre: string
  etiquetaMotivo: string
}

// ---------------------------------------------------------------- tutores

/** Payload de alta/edición de tutor. */
export interface TutorInput {
  nombre: string
  apellido: string
  dni: string
  telefono?: string
  email?: string
  direccion?: string
}

export const tutores = {
  listar: () => get<Tutor[]>("/tutores"),
  buscarPorId: (id: number) => get<Tutor>(`/tutores/${id}`),
  crear: (body: TutorInput) => post<Tutor>("/tutores", body),
  actualizar: (id: number, body: Partial<TutorInput>) => put<Tutor>(`/tutores/${id}`, body),
  eliminar: (id: number) => del<void>(`/tutores/${id}`),
  listarMascotas: (tutorId: number) => get<Mascota[]>(`/tutores/${tutorId}/mascotas`),
  /**
   * Vincula (o re-vincula) una mascota. El flag es obligatorio por regla 2.4:
   * se envia siempre explicito, nunca se deja que el backend lo asuma.
   */
  vincular: (tutorId: number, mascotaId: number, autorizadoRetiro: boolean) =>
    post<Mascota>(`/tutores/${tutorId}/mascotas/${mascotaId}?autorizadoRetiro=${autorizadoRetiro}`),
  desvincular: (tutorId: number, mascotaId: number) =>
    del<void>(`/tutores/${tutorId}/mascotas/${mascotaId}`),
  deuda: (tutorId: number) => get<DeudaTutor>(`/tutores/${tutorId}/deuda`),
}

// --------------------------------------------------------------- mascotas

/** Payload de alta/edición de mascota. */
export interface MascotaInput {
  nombre: string
  especie: string
  raza?: string
  sexo?: "Macho" | "Hembra"
  fechaNacimiento?: string
  pesoKg?: number
  conducta?: ConductaMascota
  chip?: string
  observaciones?: string
  tutores?: { tutorId: number; autorizadoRetiro: boolean }[]
}

export const mascotas = {
  listar: () => get<Mascota[]>("/mascotas"),
  buscarPorId: (id: number) => get<Mascota>(`/mascotas/${id}`),
  crear: (body: MascotaInput) => post<Mascota>("/mascotas", body),
  actualizar: (id: number, body: MascotaInput) => put<Mascota>(`/mascotas/${id}`, body),
  eliminar: (id: number) => del<void>(`/mascotas/${id}`),
  tutores: (mascotaId: number) =>
    get<{ tutorId: number; mascotaId: number; autorizadoRetiro: boolean }[]>(
      `/mascotas/${mascotaId}/tutores`,
    ),
}

// ------------------------------------------------------------ profesionales

export const profesionales = {
  listar: () => get<Profesional[]>("/profesionales"),
  crear: (body: { nombre: string; apellido: string; matricula: string }) =>
    post<Profesional>("/profesionales", body),
}

// ----------------------------------------------------------------- turnos

export const turnos = {
  listar: (fecha?: string) =>
    get<TurnoClinico[]>(`/turnos${fecha ? `?fecha=${fecha}` : ""}`),
  crear: (body: TurnoClinicoInput) => post<TurnoClinico>("/turnos", body),
}

// -------------------------------------------------------------- guarderia

export const guarderia = {
  listar: (fecha?: string) =>
    get<ReservaGuarderia[]>(`/guarderia${fecha ? `?fecha=${fecha}` : ""}`),
  aforo: (fecha: string) => get<AforoGuarderia>(`/guarderia/aforo?fecha=${fecha}`),
  crear: (body: ReservaGuarderiaInput) => post<ReservaGuarderia>("/guarderia", body),
  actualizar: (id: number, body: Partial<ReservaGuarderiaInput>) =>
    put<ReservaGuarderia>(`/guarderia/${id}`, body),
  cancelar: (id: number) => put<ReservaGuarderia>(`/guarderia/${id}/cancelar`),
  eliminar: (id: number) => del<void>(`/guarderia/${id}`),
}

// --------------------------------------------------------------- consultas

export const consultas = {
  registrar: (body: ConsultaMedicaInput) => post<ConsultaMedica>("/consultas", body),
  buscarPorId: (id: number) => get<ConsultaMedica>(`/consultas/${id}`),
  porMascota: (mascotaId: number) => get<ConsultaMedica[]>(`/consultas/mascota/${mascotaId}`),
}

// ---------------------------------------------------------------- comandas

export const comandas = {
  listarPorTutor: (tutorId: number, estado?: EstadoComanda) =>
    get<ComandaCobro[]>(
      `/comandas?tutorId=${tutorId}${estado ? `&estado=${estado}` : ""}`,
    ),
  buscarPorId: (id: number) => get<ComandaCobro>(`/comandas/${id}`),
  emitir: (body: { concepto: ConceptoCobro; tutorId: number; mascotaId?: number; descripcion?: string }) =>
    post<ComandaCobro>("/comandas", body),
  cancelar: (id: number) => put<ComandaCobro>(`/comandas/${id}/cancelar`),
  tarifas: () => get<Record<string, number>>("/comandas/tarifas"),
}

// -------------------------------------------------------------------- caja

export const caja = {
  /**
   * Registra uno o varios pagos contra una comanda.
   *
   * El array es lo que habilita el cobro mixto: una misma comanda se puede
   * saldar con efectivo y transferencia en la misma operacion, y el backend
   * recalcula el saldo una sola vez al final.
   */
  registrarPago: (comandaId: number, pagos: { metodoPago: MetodoPago; monto: number }[]) =>
    post<ComandaCobro>(`/caja/pagos/${comandaId}`, { pagos }),
  pagosDe: (comandaId: number) => get<Pago[]>(`/caja/pagos/${comandaId}`),
  cierre: (fecha: string) => get<CierreCaja>(`/caja/cierre?fecha=${fecha}`),
}

// ------------------------------------------------------------------ egreso

export const egreso = {
  /**
   * Valida un retiro por DNI.
   *
   * No tira excepcion cuando el retiro esta bloqueado: el bloqueo es una
   * respuesta valida con `motivoBloqueo` y `puedeRetirar=false`. Solo falla si
   * el tutor o la mascota no existen, y en ese caso si es `ApiError` 404.
   */
  validarPorDni: (mascotaId: number, tutorDni: string) =>
    get<EgresoValidacion>(
      `/egreso/validar?mascotaId=${mascotaId}&tutorDni=${encodeURIComponent(tutorDni)}`,
    ),
  validarPorTutorId: (mascotaId: number, tutorId: number) =>
    get<EgresoValidacion>(`/egreso/validar/tutor?mascotaId=${mascotaId}&tutorId=${tutorId}`),
}

// ----------------------------------------------------------------- helpers

/** Un numero del backend es `number | null`; la UI no deberia ver nulls. */
export function aTexto(valor: number | null | undefined, porDefecto = "—") {
  return valor === null || valor === undefined ? porDefecto : String(valor)
}

/**
 * Convierte los `LocalDateTime` del backend a `YYYY-MM-DD` local.
 *
 * `new Date("2026-03-10T10:00:00")` se interpreta como hora local, pero
 * `toISOString()` convierte a UTC y en una zona como UTC-3 devuelve el dia
 * anterior. Para una agenda eso significa un turno del dia 10 mostrado el dia 9.
 */
export function aFechaLocal(iso: string | null | undefined) {
  if (!iso) return null
  const [fecha] = iso.split("T")
  return fecha
}

/** Hora local en HH:mm, sin el corrimiento de huso de `toISOString()`. */
export function aHoraLocal(iso: string | null | undefined) {
  if (!iso) return "—"
  const [, hora] = iso.split("T")
  return hora ? hora.slice(0, 5) : "—"
}

export function esFechaHoy(fecha: string | null | undefined) {
  return fecha === new Date().toISOString().slice(0, 10)
}