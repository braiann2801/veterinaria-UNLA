package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * Mascota con actividad en una jornada, con sus tutores vinculados.
 *
 * <p>Es la proyeccion que comparten el checkout de egreso y la caja del dia: los
 * dos modulos necesitan exactamente la misma pregunta ("¿quien estuvo hoy y
 * quienes pueden venir a buscarlo o a pagarlo?") y por eso hay un solo DTO y una
 * sola consulta. Dos versiones de "actividad del dia" divergen en cuanto una de
 * las dos se olvida de excluir los cancelados.</p>
 *
 * <p>El orden del dia lo arma el backend y no la vista: por el primer horario
 * con actividad, y al final las mascotas que no tienen hora registrada. Es el
 * mismo criterio que usa la agenda para pintar la jornada de guarderia.</p>
 */
public record MascotaDelDiaResponseDTO(
		Long mascotaId,
		String nombreMascota,
		String especie,
		String conducta,

		/** Motivos de la presencia: {@code TURNO_CLINICO} y/o {@code GUARDERIA}. */
		List<String> motivos,

		/** Turnos no cancelados de la mascota en la fecha. */
		int cantidadTurnos,

		/** Estado de la reserva de guarderia del dia, o {@code null} si no hay. */
		String estadoGuarderia,

		/**
		 * Horario de la reserva de guarderia. Null si no hay reserva, o si la
		 * reserva no tiene hora cargada (las anteriores a TASK-008).
		 */
		LocalTime horaIngreso,
		LocalTime horaSalida,

		List<TutorVinculadoResponseDTO> tutores,

		/**
		 * Entrega registrada en la jornada ("Registrar entrega y cerrar"). Null si
		 * todavia no se entrego. Los completa el checkout de egreso; la jornada
		 * compartida con la caja los deja en null.
		 */
		java.time.LocalDateTime fechaHoraEntrega,
		String entregadoA) {

	/** Copia con la entrega registrada. */
	public MascotaDelDiaResponseDTO conEntrega(java.time.LocalDateTime fechaHora, String tutor) {
		return new MascotaDelDiaResponseDTO(mascotaId, nombreMascota, especie, conducta, motivos, cantidadTurnos,
				estadoGuarderia, horaIngreso, horaSalida, tutores, fechaHora, tutor);
	}

	/**
	 * Motivos de presencia, en el vocabulario de la API.
	 *
	 * <p>Se exponen como {@code String} y no como el enum de JPA porque este DTO
	 * no depende de las entidades de agenda: si manana la guarderia suma un tercer
	 * motivo, el valor nuevo llega al frontend sin tocar aca.</p>
	 */
	public static final String MOTIVO_TURNO = "TURNO_CLINICO";
	public static final String MOTIVO_GUARDERIA = "GUARDERIA";

	/**
	 * Tutor vinculado a la mascota, con el flag de retiro de la tupla.
	 *
	 * <p>El flag viaja porque el mostrador tiene que distinguir de entrada a quien
	 * puede llevarsela ("autorizado") de a quien solo puede venir a pagar. El
	 * backend no lo usa para autorizar pagos: esa es otra regla.</p>
	 */
	public record TutorVinculadoResponseDTO(
			Long tutorId,
			String nombreCompleto,
			String dni,
			boolean autorizadoRetiro) {
	}
}
