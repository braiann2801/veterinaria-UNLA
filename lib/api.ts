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

import { borrarSesion, obtenerSesion, type RolApi, type Sesion, type UsuarioSesion } from "@/lib/sesion"

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

  /** 401: no hay sesion valida (o, en el login, usuario o clave incorrectos). */
  get esNoAutenticado(): boolean {
    return this.status === 401
  }

  /** 403: el rol del usuario no tiene permiso para esa accion. */
  get esProhibido(): boolean {
    return this.status === 403
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

/**
 * Un 401 en cualquier endpoint que no sea el login significa que la sesion ya no
 * vale (vencio, la desactivaron o le cambiaron el rol): se limpia y se manda al
 * login. El login en si devuelve 401 por clave incorrecta, y ese error lo tiene
 * que mostrar el formulario, no redirigir.
 */
function sesionCaducada(path: string) {
  if (path === "/auth/login") return
  borrarSesion()
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.assign("/login?motivo=sesion")
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE}${path}`
  const token = obtenerSesion()?.token

  let respuesta: Response
  try {
    respuesta = await fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
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
    if (respuesta.status === 401) sesionCaducada(path)
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
  /** El backend emite el nombre del enum: coincide con {@link EspecieMascota}. */
  especie: EspecieMascota
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
  /**
   * Identificadores del profesional. Son `string` y no `number` porque la
   * matricula es alfanumerica ("MAT-1234") y el DNI puede traer guiones.
   * `null` cuando el profesional todavia no tramito la matricula o el DNI.
   */
  dni: string | null
  matricula: string | null
  telefono: string | null
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
  profesional: { id: number; nombreCompleto: string; matricula: string | null }
}

export interface TurnoClinicoInput {
  /**
   * `YYYY-MM-DDTHH:mm` en hora local. Acepta fechas pasadas a proposito: una
   * atencion ya ocurrida se documenta cuando el paciente vuelve, y el backend
   * no impone ninguna restriccion de futuro.
   */
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
  /** Último día de la estadía; `null` en las de un solo día. */
  fechaSalida: string | null
  tipoEstadia: TipoEstadiaGuarderia
  estado: string
  mascotaId: number
  nombreMascota: string
  /** `null` en las reservas cargadas antes de que existiera el horario. */
  horaEntrada: string | null
  horaSalida: string | null
  sena: number
  observaciones: string | null
  cuposOcupados: number
  aforoMaximo: number
  /** Comanda de guardería emitida al reservar; `null` si la mascota no tiene tutor. */
  comandaId: number | null
}

export interface ReservaGuarderiaInput {
  fecha: string
  /**
   * Último día de una estadía por rango (requiere `horaEntrada` y `horaSalida`).
   * Sin él, la reserva es de un solo día, como siempre.
   */
  fechaSalida?: string
  /** Opcional: con rango el backend lo deduce de las fechas. */
  tipoEstadia?: TipoEstadiaGuarderia
  mascotaId: number
  /**
   * Horarios en `HH:mm`. Se mandan juntos o ninguno: el backend rechaza media
   * pareja en vez de completarla. En `COMPLETA_24H` la salida es una hora del
   * reloj del dia siguiente, asi que puede ser anterior a la de entrada.
   */
  horaEntrada?: string
  horaSalida?: string
  sena: number
  observaciones?: string
}

export type TipoEstadiaGuarderia = "DIURNA" | "NOCTURNA" | "COMPLETA_24H"

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
  profesional: { id: number; nombreCompleto: string; matricula: string | null }
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
  /** Extras usados en la atención. Sin precio: lo pone el catálogo del Dueño. */
  extras?: { extraId: number; cantidad: number }[]
}

export interface Pago {
  id: number
  metodoPago: MetodoPago
  monto: number
  fechaHora: string
  /**
   * Quien pago. `null` en los pagos cargados antes de TASK-009: "no informado",
   * nunca "no pago nadie".
   */
  tutorPagadorId: number | null
  nombreTutorPagador: string | null
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
  /** Detalle (consulta y extras) con el precio congelado al emitir. Vacío en comandas sin detalle. */
  items: ComandaItem[]
}

export interface ComandaItem {
  descripcion: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface ComandaPendiente {
  id: number
  concepto: ConceptoCobro
  saldoPendiente: number
  fechaEmision: string
  diasDeAntiguedad: number
  /** Mascota de la comanda: su saldo es el que retiene a esa mascota. `null` si no es de ninguna. */
  mascotaId: number | null
  nombreMascota: string | null
}

export interface DeudaTutor {
  tutorId: number
  nombreTutor: string
  tieneDeuda: boolean
  totalDeuda: number
  cantidadComandas: number
  comandasPendientes: ComandaPendiente[]
}

export interface MovimientoCaja {
  id: number
  fechaHora: string
  /** Emisión de la comanda: si es anterior a la jornada, el cobro salda una deuda vieja. */
  fechaEmision: string
  metodoPago: MetodoPago
  monto: number
  concepto: ConceptoCobro
  /** Deudor de la comanda, no necesariamente quien pagó. */
  nombreTutor: string
  /** Quién pagó (un cotutor puede saldar la comanda del titular). `null` si no se registró. */
  nombrePagador: string | null
  /** `null` en las comandas de guardería sin mascota. */
  nombreMascota: string | null
  estadoComanda: EstadoComanda
}

export interface CierreCaja {
  fecha: string
  totalCobrado: number
  cantidadPagos: number
  cantidadComandas: number
  /** Deuda aún no cobrada de las comandas emitidas ese día. */
  saldosPendientes: number
  totalesPorMetodo: Record<string, number>
  /** Extracto de los pagos de la jornada, ordenado por hora ascendente. */
  movimientos: MovimientoCaja[]
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

// ------------------------------------------------------------- jornada del día

/** Motivos por los que una mascota estuvo en el local un día (TASK-009). */
export type MotivoJornada = "TURNO_CLINICO" | "GUARDERIA"

/** Tutor vinculado a la mascota, con el flag de retiro de esa tupla. */
export interface TutorVinculado {
  tutorId: number
  nombreCompleto: string
  dni: string
  /**
   * Solo gobierna quién puede **retirar** a la mascota (regla 2.4). No habilita ni
   * bloquea el pago: un cotutor sin autorización puede perfectamente pagar.
   */
  autorizadoRetiro: boolean
}

/**
 * Mascota con actividad en una jornada.
 *
 * La usan el checkout de egreso y la caja del día, y salen de la misma consulta
 * del backend a propósito: si cada pantalla armara la suya, una podría mostrar un
 * turno cancelado que la otra no muestra.
 */
export interface MascotaDelDia {
  mascotaId: number
  nombreMascota: string
  especie: EspecieMascota
  conducta: ConductaMascota | null
  motivos: MotivoJornada[]
  cantidadTurnos: number
  estadoGuarderia: "RESERVADO" | "INGRESADO" | null
  horaIngreso: string | null
  horaSalida: string | null
  tutores: TutorVinculado[]
  /** Entrega registrada en la jornada ("Registrar entrega y cerrar"); `null` si todavía no se entregó. */
  fechaHoraEntrega: string | null
  entregadoA: string | null
}

/** Respuesta de `POST /egreso/registrar`. */
export interface EgresoRegistrado {
  id: number
  mascotaId: number
  nombreMascota: string
  tutorId: number
  nombreTutor: string
  fecha: string
  fechaHora: string
  reservaGuarderiaId: number | null
}

/** Tutor con actividad en una jornada, con las mascotas por las que vino. */
export interface TutorDelDia {
  tutorId: number
  nombreCompleto: string
  dni: string
  mascotas: { mascotaId: number; nombreMascota: string; motivos: MotivoJornada[] }[]
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
  /** Cambia la autorización de un vínculo que ya existe (`vincular` rechaza duplicados con 400). */
  actualizarAutorizacion: (tutorId: number, mascotaId: number, autorizadoRetiro: boolean) =>
    put<Mascota>(`/tutores/${tutorId}/mascotas/${mascotaId}?autorizadoRetiro=${autorizadoRetiro}`),
  desvincular: (tutorId: number, mascotaId: number) =>
    del<void>(`/tutores/${tutorId}/mascotas/${mascotaId}`),
  deuda: (tutorId: number) => get<DeudaTutor>(`/tutores/${tutorId}/deuda`),
}

// --------------------------------------------------------------- mascotas

/**
 * Especie segun el enum `Mascota.Especie` del backend.
 *
 * Se tipa como union y no como `string` a proposito: el backend deserializa
 * `especie` contra ese enum, asi que un `"canino"` en minusculas devuelve un
 * 400 con un mensaje que no dice nada util. Con la union, el error aparece en
 * `tsc` y no en produccion.
 */
export type EspecieMascota = "CANINO" | "FELINO" | "AVE" | "ROEDOR" | "REPTIL" | "OTRO"

/** Payload de alta/edición de mascota. */
export interface MascotaInput {
  nombre: string
  especie: EspecieMascota
  raza?: string
  sexo?: "Macho" | "Hembra"
  fechaNacimiento?: string
  pesoKg?: number
  conducta?: ConductaMascota
  chip?: string
  observaciones?: string
  /**
   * Tutores a vincular en la misma operacion, cada uno con su flag de retiro
   * explicito (regla 2.4). El backend sincroniza la lista completa: lo que no
   * este aqui queda desvinculado.
   */
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

/**
 * Payload de alta de profesional.
 *
 * Solo `nombre` y `apellido` son obligatorios: el backend acepta un profesional
 * sin matricula todavia (un auxiliar que recien se incorporo) y solo rechaza
 * el DNI o la matricula si ya pertenecen a otro profesional.
 */
export interface ProfesionalInput {
  nombre: string
  apellido: string
  dni?: string
  matricula?: string
  telefono?: string
}

export const profesionales = {
  listar: () => get<Profesional[]>("/profesionales"),
  crear: (body: ProfesionalInput) => post<Profesional>("/profesionales", body),
  actualizar: (id: number, body: ProfesionalInput) => put<Profesional>(`/profesionales/${id}`, body),
  /** 409 si el profesional tiene turnos o consultas registrados. */
  eliminar: (id: number) => del<void>(`/profesionales/${id}`),
}

// ----------------------------------------------------------------- turnos

/**
 * Turno con alta de cliente y/o mascota en el mismo paso. El cliente es
 * `tutorId` o `tutorNuevo`; la mascota, `mascotaId` o `mascotaNueva`. El backend
 * lo hace todo en una transacción: si el turno choca (409), no queda nada creado.
 */
export interface TurnoConAltaInput {
  tutorId?: number
  tutorNuevo?: TutorInput
  mascotaId?: number
  mascotaNueva?: Omit<MascotaInput, "tutores">
  fechaHoraInicio: string
  motivo: string
  profesionalId: number
}

export const turnos = {
  listar: (fecha?: string) =>
    get<TurnoClinico[]>(`/turnos${fecha ? `?fecha=${fecha}` : ""}`),
  /** Turnos de `desde` a `hasta`, ambos inclusive (máximo 62 días): la vista mensual. */
  listarRango: (desde: string, hasta: string) =>
    get<TurnoClinico[]>(`/turnos?desde=${desde}&hasta=${hasta}`),
  crear: (body: TurnoClinicoInput) => post<TurnoClinico>("/turnos", body),
  crearConAlta: (body: TurnoConAltaInput) => post<TurnoClinico>("/turnos/con-alta", body),
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

// ----------------------------------------------------------------- extras

/** Extra del catálogo (vacuna, urgencia, insumo) con el precio que fija el Dueño. */
export interface Extra {
  id: number
  nombre: string
  precio: number
  activo: boolean
}

export interface ExtraInput {
  nombre: string
  precio: number
  activo?: boolean
}

export const extras = {
  listar: (soloActivos = false) => get<Extra[]>(`/extras${soloActivos ? "?soloActivos=true" : ""}`),
  /** Solo el Dueño: el backend responde 403 al resto. */
  crear: (body: ExtraInput) => post<Extra>("/extras", body),
  actualizar: (id: number, body: ExtraInput) => put<Extra>(`/extras/${id}`, body),
}

export const consultas = {
  registrar: (body: ConsultaMedicaInput) => post<ConsultaMedica>("/consultas", body),
  buscarPorId: (id: number) => get<ConsultaMedica>(`/consultas/${id}`),
  porMascota: (mascotaId: number) => get<ConsultaMedica[]>(`/consultas/mascota/${mascotaId}`),
}

// ---------------------------------------------------------------- comandas

export const comandas = {
  /**
   * Historial completo de un tutor, de cualquier fecha.
   *
   * Para la caja del día está `listarDelDia`: este no acota por fecha, y el
   * mostrador necesita las dos cosas.
   */
  listarPorTutor: (tutorId: number, estado?: EstadoComanda) =>
    get<ComandaCobro[]>(
      `/comandas?tutorId=${tutorId}${estado ? `&estado=${estado}` : ""}`,
    ),
  /**
   * Caja del día: comandas **emitidas** en la fecha.
   *
   * La fecha filtra por emisión y no por la fecha del turno, porque la comanda no
   * conoce otra: se emite al guardar la ficha, que puede ser días después de la
   * atención. `fecha` es opcional porque el backend usa hoy por defecto.
   */
  listarDelDia: (fecha?: string, tutorId?: number, estado?: EstadoComanda) =>
    get<ComandaCobro[]>(
      `/comandas/dia?${query({ fecha, tutorId, estado })}`,
    ),
  buscarPorId: (id: number) => get<ComandaCobro>(`/comandas/${id}`),
  emitir: (body: { concepto: ConceptoCobro; tutorId: number; mascotaId?: number; descripcion?: string }) =>
    post<ComandaCobro>("/comandas", body),
  cancelar: (id: number) => put<ComandaCobro>(`/comandas/${id}/cancelar`),
  tarifas: () => get<Record<string, number>>("/comandas/tarifas"),
  /** Solo el Dueño (403 al resto). */
  actualizarTarifa: (concepto: ConceptoCobro, monto: number) =>
    put<{ concepto: string; monto: number }>(`/comandas/tarifas/${concepto}`, { monto }),
}

// -------------------------------------------------------------------- caja

export const caja = {
  /**
   * Registra uno o varios pagos contra una comanda.
   *
   * El array es lo que habilita el cobro mixto: una misma comanda se puede saldar
   * con efectivo y transferencia en la misma operacion, y el backend recalcula el
   * saldo una sola vez al final.
   *
   * `tutorId` es opcional y aplica a toda la llamada: es la misma persona en los
   * tres medios. Sirve para que un cotutor vinculado pague la comanda de su
   * familia. El backend exige que este vinculado a la mascota de la comanda, y no
   * revisa el flag de retiro: omitirlo asume al deudor. No cambia quien debe la
   * comanda, solo registra quien pago.
   */
  registrarPago: (
    comandaId: number,
    pagos: { metodoPago: MetodoPago; monto: number }[],
    tutorId?: number,
  ) => post<ComandaCobro>(`/caja/pagos/${comandaId}`, { pagos, tutorId }),
  pagosDe: (comandaId: number) => get<Pago[]>(`/caja/pagos/${comandaId}`),
  cierre: (fecha: string) => get<CierreCaja>(`/caja/cierre?fecha=${fecha}`),
  /** Quién estuvo en la jornada, para cobrarle sin recorrer todo el padrón. */
  tutoresDelDia: (fecha?: string) => get<TutorDelDia[]>(`/caja/tutores-del-dia?${query({ fecha })}`),
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
  /**
   * Mascotas con turno o guardería en la fecha, con sus tutores y el flag de
   * retiro de cada vínculo. La lista de candidatos del checkout; el veredicto sale
   * de `validar`. Sin `fecha` es hoy.
   */
  pacientesDelDia: (fecha?: string) =>
    get<MascotaDelDia[]>(`/egreso/pacientes-hoy?${query({ fecha })}`),
  /**
   * Registra la entrega. El backend vuelve a validar: si el retiro ya no está
   * permitido (o ya se entregó en la jornada) responde 409 con el motivo.
   */
  registrar: (mascotaId: number, tutorId: number, fecha: string) =>
    post<EgresoRegistrado>("/egreso/registrar", { mascotaId, tutorId, fecha }),
}

// -------------------------------------------------------------------- auth

export const auth = {
  login: (username: string, password: string) => post<Sesion>("/auth/login", { username, password }),
  me: () => get<UsuarioSesion>("/auth/me"),
  /** Devuelve una sesion nueva: el cambio de clave invalida el token anterior. */
  cambiarPassword: (actual: string, nueva: string) => post<Sesion>("/auth/cambiar-password", { actual, nueva }),
}

// ---------------------------------------------------------------- usuarios

/** Alta y reset: la clave temporal viaja una sola vez y no se puede volver a pedir. */
export interface UsuarioConClaveTemporal {
  usuario: UsuarioSesion
  passwordTemporal: string
}

/** ABM de cuentas. Solo el Dueño: el backend responde 403 al resto. */
export const usuarios = {
  listar: () => get<UsuarioSesion[]>("/usuarios"),
  crear: (datos: { username: string; nombreCompleto: string; rol: RolApi }) =>
    post<UsuarioConClaveTemporal>("/usuarios", datos),
  actualizar: (id: number, datos: { nombreCompleto: string; rol: RolApi }) =>
    put<UsuarioSesion>(`/usuarios/${id}`, datos),
  cambiarActivo: (id: number, activo: boolean) => put<UsuarioSesion>(`/usuarios/${id}/activo`, { activo }),
  resetearPassword: (id: number) => post<UsuarioConClaveTemporal>(`/usuarios/${id}/reset-password`),
  eliminar: (id: number) => del<void>(`/usuarios/${id}`),
}

// ------------------------------------------------------- tarifas guardería

export interface TarifaGuarderia {
  precioHora: number
  precioNoche: number
}

/** Monto de una estadía: por horas si entra y sale el mismo día, por noches si pasa al menos una. */
export interface CotizacionGuarderia {
  modalidad: "HORAS" | "NOCHES"
  cantidad: number
  precioUnitario: number
  total: number
}

export const tarifasGuarderia = {
  obtener: () => get<TarifaGuarderia>("/guarderia/tarifas"),
  /** Solo el Dueño (403 al resto). */
  actualizar: (tarifas: TarifaGuarderia) => put<TarifaGuarderia>("/guarderia/tarifas", tarifas),
  /** `desde`/`hasta` en ISO local sin zona: `2031-06-02T09:00:00`. */
  cotizar: (desde: string, hasta: string) =>
    get<CotizacionGuarderia>(`/guarderia/cotizar?${query({ desde, hasta })}`),
}

// ----------------------------------------------------------------- helpers

/**
 * Query string descartando lo que viene vacío.
 *
 * Sin esto habría que escribir `fecha ? "&fecha=" + fecha : ""` en cada llamada, y
 * el olvido manda un parámetro vacío que el backend no sabe interpretar.
 */
function query(params: Record<string, string | number | undefined | null>) {
  const partes = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== "")
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
  return partes.length > 0 ? partes.join("&") : ""
}

/**
 * Fecha de hoy en `YYYY-MM-DD` **local**.
 *
 * `new Date().toISOString()` devuelve la fecha en UTC, que en una zona como
 * UTC-3 entre las 21:00 y las 24:00 da el dia de manana. Para una caja o un
 * checkout abiertos por defecto a hoy, eso significaria mostrar la jornada
 * equivocada justo cuando el mostrador esta cerrando el dia.
 */
export function hoyLocal() {
  const ahora = new Date()
  const mes = String(ahora.getMonth() + 1).padStart(2, "0")
  const dia = String(ahora.getDate()).padStart(2, "0")
  return `${ahora.getFullYear()}-${mes}-${dia}`
}