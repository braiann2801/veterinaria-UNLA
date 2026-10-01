package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista publica de un pago aplicado a una comanda.
 *
 * <p>Proyeccion plana a proposito: solo datos del pago. Si arrastrara la
 * comanda, y la comanda arrastra sus pagos, Jackson cicla.</p>
 */
public record PagoResponseDTO(
		Long id,
		String metodoPago,
		BigDecimal monto,
		LocalDateTime fechaHora) {
}