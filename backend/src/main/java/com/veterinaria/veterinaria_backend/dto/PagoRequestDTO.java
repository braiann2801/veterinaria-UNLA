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
 *
 * <p>{@code tutorId} es opcional y aplica a toda la llamada, no a cada pago:
 * una operacion de mostrador la hace una sola persona aunque la comanda se salde
 * con tres medios. Cuando viene, el backend exige que ese tutor este vinculado
 * a la mascota de la comanda; cuando no viene, se asume el deudor.</p>
 */
public record PagoRequestDTO(

		@NotEmpty(message = "Debe registrar al menos un pago")
		List<@Valid DetallePagoDTO> pagos,

		/**
		 * Quien paga. Opcional: null significa el deudor de la comanda.
		 *
		 * <p>Se valida la <b>vinculacion</b> con la mascota, no el flag de retiro.
		 * El flag gobierna quien puede llevarse al animal (regla 2.4); pagar es otra
		 * puerta, y un cotutor sin autorizacion de retiro es justamente a quien se
		 * le acepta el pago cuando vino a saldar la cuenta de su familia.</p>
		 */
		Long tutorId) {

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