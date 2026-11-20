package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;

/** Un movimiento del historial, sin la entidad ni la relacion con el medicamento. */
public record MovimientoStockResponseDTO(
		Long id,
		TipoMovimiento tipo,
		int cantidad,
		int stockResultante,
		String observacion,
		Long referenciaId,
		String usuario,
		LocalDateTime fechaHora) {
}
