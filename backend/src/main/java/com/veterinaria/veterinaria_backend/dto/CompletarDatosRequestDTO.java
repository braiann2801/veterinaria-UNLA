package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.Valid;

/**
 * Datos de una urgencia que se registro sin cliente ni mascota. Mismas
 * combinaciones que el alta combinada: cliente existente ({@code tutorId}) o
 * nuevo ({@code tutorNuevo}), y mascota existente ({@code mascotaId}) o nueva
 * ({@code mascotaNueva}).
 */
public record CompletarDatosRequestDTO(
		Long tutorId,
		@Valid TutorRequestDTO tutorNuevo,
		Long mascotaId,
		@Valid MascotaRequestDTO mascotaNueva) {
}
