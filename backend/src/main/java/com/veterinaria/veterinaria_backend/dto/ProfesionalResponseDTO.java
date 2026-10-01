package com.veterinaria.veterinaria_backend.dto;

/**
 * Vista publica de un profesional.
 */
public record ProfesionalResponseDTO(
		Long id,
		String nombre,
		String apellido,
		String nombreCompleto,
		String matricula,
		boolean activo) {
}