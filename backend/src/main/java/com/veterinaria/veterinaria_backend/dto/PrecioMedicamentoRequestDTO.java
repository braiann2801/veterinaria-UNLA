package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Cambio de precio de un medicamento del catalogo.
 *
 * <p>Rige solo para las comandas que se emitan desde ahora: el precio ya
 * copiado en una comanda anterior no se modifica.</p>
 */
public record PrecioMedicamentoRequestDTO(
		@NotNull(message = "El precio es obligatorio")
		@PositiveOrZero(message = "El precio no puede ser negativo")
		@Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
		BigDecimal precio) {

	public PrecioMedicamentoRequestDTO {
		if (precio != null) {
			precio = precio.setScale(2, RoundingMode.HALF_UP);
		}
	}
}
