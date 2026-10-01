package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Cierre de caja de una jornada.
 *
 * <p>El arqueo por metodo de pago es lo que permite detectar un faltante: si el
 * efectivo en boveda no cuadra con el efectivo que declara el sistema, hay una
 * diferencia que hay que explicar.</p>
 *
 * <p>{@code totalesPorMetodo} incluye los cinco metodos aunque alguno no haya
 * recibido pagos, con total 0: un cierre con huecos obliga al mostrador a
 * adivinar que metodos no se movieron.</p>
 *
 * @param totalCobrado      suma de todos los pagos de la jornada
 * @param cantidadPagos     filas de pago registradas (un comanda de dos medios
 *                          cuenta como dos)
 * @param cantidadComandas  comandas distintas que recibieron al menos un pago
 */
public record CierreCajaResponseDTO(
		LocalDate fecha,
		BigDecimal totalCobrado,
		int cantidadPagos,
		int cantidadComandas,
		Map<String, BigDecimal> totalesPorMetodo) {
}