package com.veterinaria.veterinaria_backend.exception;

/**
 * Helper de mensajes para el rechazo de solapamiento de turnos.
 *
 * <p>El texto nombra las tres marcas de tiempo involucradas (inicio, fin de
 * atencion y fin de bloque con desinfeccion) para que quien recibe el 409
 * entienda exactamente por que no se puede agendar.</p>
 */
public final class SolapamientoTurnoMessages {

	private SolapamientoTurnoMessages() {
	}

	public static String conflicto(String profesional,
			java.time.LocalDateTime inicioExistente,
			java.time.LocalDateTime finExistente,
			java.time.LocalDateTime finBloqueExistente,
			java.time.LocalDateTime inicioSolicitado,
			java.time.LocalDateTime finBloqueSolicitado) {
		return String.format(
				"El profesional %s ya tiene un turno de las %s a las %s, que ocupa el bloque hasta las %s "
						+ "por desinfeccion. El turno solicitado (de las %s a las %s) se solapa con esa ventana.",
				profesional,
				inicioExistente.toLocalTime(),
				finExistente.toLocalTime(),
				finBloqueExistente.toLocalTime(),
				inicioSolicitado.toLocalTime(),
				finBloqueSolicitado.toLocalTime());
	}
}