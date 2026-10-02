package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de reserva de guarderia.
 */
public record ReservaGuarderiaRequestDTO(

		@NotNull(message = "La fecha de la reserva es obligatoria")
		LocalDate fecha,

		@NotNull(message = "El tipo de estadia es obligatorio")
		TipoEstadiaRequestDTO tipoEstadia,

		@NotNull(message = "El id de la mascota es obligatorio")
		Long mascotaId,

		/**
		 * Horario de entrada. Opcional para no invalidar las reservas ya
		 * cargadas, pero si se manda {@code horaSalida} tiene que venir con
		 * entrada: un horario a medias no se puede leer en el mostrador.
		 */
		LocalTime horaEntrada,

		/**
		 * Horario de salida. Debe ser posterior a {@code horaEntrada}; la
		 * comparacion va en el servicio porque necesita los dos valores juntos.
		 */
		LocalTime horaSalida,

		@DecimalMin(value = "0.00", message = "La sena no puede ser negativa")
		BigDecimal sena,

		@Size(max = 300, message = "Las observaciones no pueden superar los 300 caracteres")
		String observaciones) {

	public enum TipoEstadiaRequestDTO {
		DIURNA,
		NOCTURNA,
		COMPLETA_24H
	}
}