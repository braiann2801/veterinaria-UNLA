package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista publica de una reserva de guarderia, con el dato de aforo del dia.
 *
 * <p>{@code cuposOcupados} viaja en la respuesta para que el frontend pueda
 * mostrar la barra de disponibilidad sin una segunda llamada.</p>
 */
public record ReservaGuarderiaResponseDTO(
		Long id,
		LocalDate fecha,
		String tipoEstadia,
		String estado,
		Long mascotaId,
		String nombreMascota,
		BigDecimal sena,
		String observaciones,
		long cuposOcupados,
		int aforoMaximo) {
}