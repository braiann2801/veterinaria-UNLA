package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Entrega registrada. {@code reservaGuarderiaId} es null si la mascota no estaba en guarderia. */
public record EgresoRegistradoResponseDTO(
		Long id,
		Long mascotaId,
		String nombreMascota,
		Long tutorId,
		String nombreTutor,
		LocalDate fecha,
		LocalDateTime fechaHora,
		Long reservaGuarderiaId) {
}
