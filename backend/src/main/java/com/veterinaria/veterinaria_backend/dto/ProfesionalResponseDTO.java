package com.veterinaria.veterinaria_backend.dto;

/**
 * Vista publica de un profesional.
 *
 * <p>Los identificadores van como texto y no como numeros: la matricula es
 * alfanumerica y el DNI puede traer guiones o letras en el formato de otras
 * provincias. El frontend solo los muestra y los compara, nunca los opera.</p>
 */
public record ProfesionalResponseDTO(
		Long id,
		String nombre,
		String apellido,
		String nombreCompleto,
		String dni,
		String matricula,
		String telefono,
		boolean activo) {
}
