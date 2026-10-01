package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de morosidad de un tutor: la base de la alerta visual antes de un
 * egreso de guarderia (regla 2.5).
 *
 * <p>{@code comandasPendientes} viene con el detalle para que el mostrador
 * pueda mostrar el concepto y desde cuando se debe, no solo un numero.</p>
 */
public record DeudaTutorResponseDTO(
		Long tutorId,
		String nombreTutor,
		boolean tieneDeuda,
		BigDecimal totalDeuda,
		int cantidadComandas,
		List<ComandaPendienteDTO> comandasPendientes) {

	/**
	 * Una comanda que todavia debe dinero.
	 *
	 * @param diasDeAntiguedad dias desde la emision: avisa cuando una deuda
	 *                          envejece y hay que perseguirla
	 */
	public record ComandaPendienteDTO(
			Long id,
			String concepto,
			BigDecimal saldoPendiente,
			LocalDate fechaEmision,
			long diasDeAntiguedad) {
	}
}