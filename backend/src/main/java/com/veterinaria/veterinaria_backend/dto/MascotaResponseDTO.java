package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vista publica de una mascota. Los tutores se exponen como
 * {@link TutorResumenDTO}, que ya incluye el flag de retiro de cada tutor sobre
 * esa mascota, de modo que no existe camino de regreso a la mascota.
 */
public record MascotaResponseDTO(
		Long id,
		String nombre,
		String especie,
		String raza,
		String sexo,
		LocalDate fechaNacimiento,
		BigDecimal pesoKg,
		String conducta,
		String chip,
		String observaciones,
		List<TutorResumenDTO> tutores) {
}