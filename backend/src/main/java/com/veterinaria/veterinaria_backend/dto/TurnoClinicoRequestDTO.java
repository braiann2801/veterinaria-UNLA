package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import com.veterinaria.veterinaria_backend.config.FlexibleLocalDateTimeDeserializer;

/**
 * Payload de alta/edicion de un turno clinico.
 *
 * <p>Solo se recibe {@code fechaHoraInicio}: el fin de atencion (+45) y el fin
 * de bloque (+60) los deriva el dominio. Aceptarlos por API permitiria
 * saltarse la ventana de desinfeccion.</p>
 */
public record TurnoClinicoRequestDTO(

		/**
	 * Inicio del turno. <b>Acepta valores pasados a proposito:</b> no lleva
	 * {@code @Future} porque las atenciones ya ocurridas se documentan en el
	 * momento en que el paciente vuelve o el veterinario cierra la ficha.
	 *
	 * <p>Se deserializa con
	 * {@link FlexibleLocalDateTimeDeserializer} por <b>tolerancia de formato</b>,
	 * no por permitir el pasado: el frontend ya manda el ISO completo. Lo que
	 * aporta es que un cliente que mande solo la fecha, o sin segundos, no reciba
	 * un 400 por el formato.</p>
	 */
		@NotNull(message = "La fecha y hora de inicio es obligatoria")
		@JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
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