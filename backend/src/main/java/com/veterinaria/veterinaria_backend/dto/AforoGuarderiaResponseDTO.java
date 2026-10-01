package com.veterinaria.veterinaria_backend.dto;

/**
 * Estado del aforo de guarderia para una fecha.
 *
 * <p>Permite que el frontend muestre "quedan N cupos" sin tener que derivarlo
 * de la lista de reservas.</p>
 */
public record AforoGuarderiaResponseDTO(
		java.time.LocalDate fecha,
		long cuposOcupados,
		int aforoMaximo,
		int cuposDisponibles,
		boolean completo) {
}