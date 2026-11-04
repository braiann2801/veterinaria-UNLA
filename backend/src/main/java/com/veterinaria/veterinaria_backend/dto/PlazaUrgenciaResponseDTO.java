package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDate;

/**
 * Estado de la plaza de urgencia de un dia. {@code reservaId} y
 * {@code nombreMascota} son nulos mientras la plaza esta libre.
 */
public record PlazaUrgenciaResponseDTO(
		LocalDate fecha,
		boolean habilitada,
		boolean ocupada,
		Long reservaId,
		String nombreMascota) {
}
