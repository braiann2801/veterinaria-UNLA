package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

/**
 * Vista del vinculo Tutor-Mascota expuesta por la API.
 * Reemplaza a la entidad {@code TutorMascota} para no exponer relaciones JPA.
 */
public record TutorMascotaResponseDTO(
		Long tutorId,
		Long mascotaId,
		boolean autorizadoRetiro,
		LocalDateTime vinculadoDesde) {
}