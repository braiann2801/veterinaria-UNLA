package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

// Jackson 3 movio databind a tools.jackson, pero las anotaciones siguen en
// el paquete historico com.fasterxml.jackson.annotation.
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vista publica de una comanda de cobro.
 *
 * <p>Aqui si hay montos: es el contrato del mostrador, no el del consultorio.
 * Los pagos van anidados como objetos planos ({@link PagoResponseDTO}), sin
 * retroceso a la comanda, para no reabrir el ciclo de Jackson.</p>
 */
public record ComandaCobroResponseDTO(
		Long id,
		String concepto,
		BigDecimal montoTotal,
		BigDecimal saldoPendiente,
		String estado,
		Long tutorId,
		String nombreTutor,
		Long mascotaId,
		String nombreMascota,
		Long consultaMedicaId,
		LocalDateTime fechaEmision,
		List<PagoResponseDTO> pagos) {

	/**
	 * Total efectivamente cobrado.
	 *
	 * <p>Metodo derivado y no componente del record, a proposito: el dato no se
	 * guarda en ningun lado, se calcula como {@code montoTotal - saldoPendiente}.
	 * Si fuera un componente habria que llenarlo en cada mapper y bastaria
	 * olvidar uno para que la respuesta mostrara un total distinto del real.</p>
	 *
	 * <p>El {@code @JsonProperty} no es decorativo: al no ser componente del
	 * record, Jackson no lo reconoce como getter por su nombre y la propiedad
	 * desapareceria del JSON sin avisar.</p>
	 */
	@JsonProperty("totalPagado")
	public BigDecimal totalPagado() {
		if (montoTotal == null || saldoPendiente == null) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return montoTotal.subtract(saldoPendiente).setScale(2, RoundingMode.HALF_UP);
	}
}