package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de registro de pagos sobre una comanda.
 *
 * <p>Admite uno o varios pagos en la misma llamada porque el mostrador cobra
 * combinado con frecuencia: $4.000 en efectivo y $6.000 por transferencia para
 * saldar una consulta de $10.000. En el caso simple llega un solo elemento.</p>
 *
 * <p>Todos los pagos de una llamada se aplican en la misma transaccion: si uno
 * falla, no se aplica ninguno. Un cobro parcial silencioso dejaria la caja
 * descuadrada respecto de lo que el cliente cree que pago.</p>
 */
public record PagoRequestDTO(

		@NotEmpty(message = "Debe registrar al menos un pago")
		List<@Valid DetallePagoDTO> pagos) {

	public record DetallePagoDTO(

			@NotNull(message = "El metodo de pago es obligatorio")
			MetodoPagoRequestDTO metodoPago,

			@NotNull(message = "El monto del pago es obligatorio")
			@DecimalMin(value = "0.01", message = "El monto del pago debe ser mayor a 0")
			BigDecimal monto) {
	}

	public enum MetodoPagoRequestDTO {
		EFECTIVO,
		TRANSFERENCIA,
		DEBITO,
		CREDITO,
		SENA
	}
}