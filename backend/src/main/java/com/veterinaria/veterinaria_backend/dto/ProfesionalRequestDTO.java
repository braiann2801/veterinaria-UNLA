package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta de profesional clinico. Lo necesita el endpoint de turnos:
 * la validacion de solapamiento es por profesional y hacia falta una entidad
 * real, no un string libre.
 *
 * <p>Solo {@code nombre} y {@code apellido} son obligatorios. {@code dni} y
 * {@code matricula} identifican al profesional y por eso se rechazan si vienen
 * repetidos, pero ninguno es obligatorio: un profesional puede darse de alta en el
 * sistema antes de presentar la matricula. Exigirla dejaria fuera al auxiliar
 * recien incorporado que todavia no la tramito.</p>
 *
 * <p>Los espacios se recortan en el servicio y no aca: un DNI pegado desde un
 * lector o desde un PDF suele traer espacios, y rechazar el alta por eso seria
 * una friccion evitable.</p>
 */
public record ProfesionalRequestDTO(

		@NotBlank(message = "El nombre del profesional es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
		String nombre,

		@NotBlank(message = "El apellido del profesional es obligatorio")
		@Size(max = 80, message = "El apellido no puede superar los 80 caracteres")
		String apellido,

		@Size(max = 20, message = "El DNI no puede superar los 20 caracteres")
		String dni,

		@Size(max = 40, message = "La matricula no puede superar los 40 caracteres")
		String matricula,

		@Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
		String telefono) {
}
