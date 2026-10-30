package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import com.veterinaria.veterinaria_backend.config.FlexibleLocalDateTimeDeserializer;

/**
 * Turno con alta de cliente y/o mascota en el mismo paso, para el cliente que
 * llega por primera vez al mostrador.
 *
 * <p>El cliente es {@code tutorId} (existente) o {@code tutorNuevo}; la mascota
 * es {@code mascotaId} (existente) o {@code mascotaNueva}. Las combinaciones
 * validas las decide el servicio; las anotaciones solo validan cada parte.</p>
 */
public record TurnoConAltaRequestDTO(
		Long tutorId,
		@Valid TutorRequestDTO tutorNuevo,
		Long mascotaId,
		@Valid MascotaRequestDTO mascotaNueva,
		@NotNull(message = "La fecha y hora de inicio es obligatoria")
		@JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
		LocalDateTime fechaHoraInicio,
		@NotBlank(message = "El motivo de la consulta es obligatorio")
		@Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
		String motivo,
		@NotNull(message = "El id del profesional es obligatorio")
		Long profesionalId) {
}
