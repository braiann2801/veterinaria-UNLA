package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Vista publica de una reserva de guarderia, con el dato de aforo del dia.
 *
 * <p>{@code cuposOcupados} viaja en la respuesta para que el frontend pueda
 * mostrar la barra de disponibilidad sin una segunda llamada.</p>
 */
public record ReservaGuarderiaResponseDTO(
		Long id,
		LocalDate fecha,
		/** Ultimo dia de la estadia; null en las de un solo dia. */
		LocalDate fechaSalida,
		String tipoEstadia,
		String estado,
		Long mascotaId,
		String nombreMascota,
		LocalTime horaEntrada,
		LocalTime horaSalida,
		BigDecimal sena,
		String observaciones,
		long cuposOcupados,
		int aforoMaximo,
		/** Comanda de GUARDERIA emitida por la reserva; {@code null} si la mascota no tiene tutor. */
		Long comandaId) {
}