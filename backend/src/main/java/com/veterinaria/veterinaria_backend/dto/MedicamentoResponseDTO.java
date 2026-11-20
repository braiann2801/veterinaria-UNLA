package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Medicamento tal como lo consume el frontend. Nunca expone la entidad JPA. */
public record MedicamentoResponseDTO(
		Long id,
		String nombre,
		String presentacion,
		String principioActivo,
		String laboratorio,
		BigDecimal precio,
		int stockActual,
		int stockMinimo,
		boolean bajoStock,
		boolean activo,
		LocalDateTime fechaActualizacion) {
}
