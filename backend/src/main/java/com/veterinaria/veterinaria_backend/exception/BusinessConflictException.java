package com.veterinaria.veterinaria_backend.exception;

/**
 * Conflicto de reglas de negocio: la peticion es valida en forma pero choca con
 * el estado actual del sistema. Se traduce a HTTP 409.
 *
 * <p>Ejemplos: aforo de guarderia lleno, turno dentro de la ventana de
 * desinfeccion de otro turno, o semaforo de un dia que otra transaccion
 * tardo demasiado en liberar.</p>
 */
public class BusinessConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BusinessConflictException(String mensaje) {
		super(mensaje);
	}

	/** Aforo de guarderia agotado para la fecha indicada. */
	public static BusinessConflictException aforoLleno(java.time.LocalDate fecha, long ocupados, int maximo) {
		return new BusinessConflictException(String.format(
				"No hay cupo disponible en guarderia para el %s: aforo completo (%d de %d perros). "
						+ "No se pueden admitir mas reservas ese dia.",
				fecha, ocupados, maximo));
	}

	/**
	 * No seitoria el lock del semaforo del dia dentro del tiempo de espera.
	 *
	 * <p>No es un rechazo de negocio sino unSayno semaforo esta tomado por otra
	 * transaccion que aun no cerro. El mensaje pide reintentar porque es
	 * transitorio: la operacion es valida, solo hubo que esperar de mas.</p>
	 */
	public static BusinessConflictException semaforoOcupado(java.time.LocalDate fecha) {
		return new BusinessConflictException(String.format(
				"No se pudo reservar cupo para el %s: hay otra operacion sobre el mismo dia "
						+ "que no se libero a tiempo. Reintente la reserva en unos segundos.",
				fecha));
	}
}