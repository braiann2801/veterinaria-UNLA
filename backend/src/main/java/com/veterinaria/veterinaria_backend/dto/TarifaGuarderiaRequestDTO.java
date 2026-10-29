package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Precios de guarderia que ajusta el Duenio. */
public record TarifaGuarderiaRequestDTO(

		@NotNull(message = "El precio por hora es obligatorio")
		@DecimalMin(value = "0.00", message = "El precio por hora no puede ser negativo")
		BigDecimal precioHora,

		@NotNull(message = "El precio por noche es obligatorio")
		@DecimalMin(value = "0.00", message = "El precio por noche no puede ser negativo")
		BigDecimal precioNoche) {
}
