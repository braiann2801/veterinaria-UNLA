package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;

/**
 * Monto de una estadia de guarderia.
 *
 * @param modalidad      HORAS si entra y sale el mismo dia, NOCHES si pasa al menos una
 * @param cantidad       horas (redondeadas hacia arriba) o noches
 * @param precioUnitario precio por hora o por noche vigente
 * @param total          cantidad x precioUnitario
 */
public record CotizacionGuarderiaDTO(
		String modalidad,
		long cantidad,
		BigDecimal precioUnitario,
		BigDecimal total) {
}
