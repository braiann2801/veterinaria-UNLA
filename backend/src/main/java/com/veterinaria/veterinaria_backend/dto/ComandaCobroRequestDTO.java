package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de emision manual de comanda (guarderia, insumos).
 *
 * <p><b>No acepta monto.</b> El importe sale de la tarifa vigente del concepto,
 * igual que en las consultas clinicas. Si cada emisor escribiera su propio
 * precio, el mismo concepto costaria distinto segun quien lo cargue y el
 * arqueo de caja no cerraria.</p>
 */
public record ComandaCobroRequestDTO(

		@NotBlank(message = "El concepto es obligatorio")
		@Size(max = 30, message = "El concepto no puede superar los 30 caracteres")
		String concepto,

		@NotNull(message = "El id del tutor es obligatorio")
		Long tutorId,

		/** Opcional: las comandas de insumos pueden no referenciar una mascota. */
		Long mascotaId,

		@Size(max = 150, message = "La descripcion no puede superar los 150 caracteres")
		String descripcion) {
}