package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta/edicion de un turno clinico.
 *
 * <p>Solo se recibe {@code fechaHoraInicio}: el fin de atencion (+45) y el fin
 * de bloque (+60) los deriva el dominio. Aceptarlos por API permitiria
 * saltarse la ventana de desinfeccion.</p>
 */
public record TurnoClinicoRequestDTO(

		@NotNull(message = "La fecha y hora de inicio es obligatoria")
		LocalDateTime fechaHoraInicio,

		@NotBlank(message = "El motivo de la consulta es obligatorio")
		@Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
		String motivo,

		@NotNull(message = "El id de la mascota es obligatorio")
		Long mascotaId,

		@NotNull(message = "El id del profesional es obligatorio")
		Long profesionalId,

		TurnoEstadoRequestDTO estado) {

	public enum TurnoEstadoRequestDTO {
		PENDIENTE,
		ATENDIDO,
		CANCELADO
	}
}