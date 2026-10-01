package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta de profesional clinico. Lo necesita el endpoint de turnos:
 * la validacion de solapamiento es por profesional y hacia falta una entidad
 * real, no un string libre.
 */
public record ProfesionalRequestDTO(

		@NotBlank(message = "El nombre del profesional es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
		String nombre,

		@NotBlank(message = "El apellido del profesional es obligatorio")
		@Size(max = 80, message = "El apellido no puede superar los 80 caracteres")
		String apellido,

		@Size(max = 40, message = "La matricula no puede superar los 40 caracteres")
		String matricula) {
}