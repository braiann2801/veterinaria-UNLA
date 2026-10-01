package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;

/**
 * Resumen minimo de una mascota.
 *
 * <p>No contiene ninguna referencia a tutores, por lo que puede anidarse
 * dentro de {@link TutorResponseDTO} sin generar ciclos de serializacion.</p>
 */
public record MascotaResumenDTO(
		Long id,
		String nombre,
		Especie especie,
		String raza,
		BigDecimal pesoKg) {

	public enum Especie {
		CANINO,
		FELINO,
		AVE,
		ROEDOR,
		REPTIL,
		OTRO
	}
}