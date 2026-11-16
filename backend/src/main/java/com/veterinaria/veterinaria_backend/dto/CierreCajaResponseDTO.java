package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Cierre de caja de un periodo.
 *
 * <p>El arqueo por metodo de pago es lo que permite detectar un faltante: si el
 * efectivo en boveda no cuadra con el efectivo que declara el sistema, hay una
 * diferencia que hay que explicar.</p>
 *
 * <p>{@code totalesPorMetodo} incluye los cinco metodos aunque alguno no haya
 * recibido pagos, con total 0: un cierre con huecos obliga al mostrador a
 * adivinar que metodos no se movieron.</p>
 *
 * <p>{@code movimientos} es el extracto plano de los pagos del periodo, para
 * que el Balance muestre el detalle y no solo el total. Se arma con una
 * proyeccion JPQL filtrada en la base por rango de {@code fechaHora}.</p>
 *
 * <p><b>Cobrado y facturado no son lo mismo y se fechan distinto.</b>
 * {@code totalCobrado} suma pagos por el dia en que entro la plata
 * ({@code pago.fechaHora}); {@code totalFacturado} suma comandas emitidas en el
 * periodo ({@code comanda.fechaEmision}). Un cobro de hoy puede saldar una
 * comanda de la semana pasada, asi que los dos numeros pueden diferir sin que
 * haya ningun error: es exactamente lo que el Balance tiene que mostrar.</p>
 *
 * @param desde             primer dia del periodo
 * @param hasta             ultimo dia del periodo (inclusive)
 * @param dias              cantidad de dias del periodo, contando los dos extremos
 * @param totalFacturado    monto total de las comandas emitidas en el periodo,
 *                          sin las canceladas
 * @param totalCobrado      suma de todos los pagos del periodo
 * @param cantidadPagos     filas de pago registradas (una comanda de dos medios
 *                          cuenta como dos)
 * @param cantidadComandas  comandas distintas que recibieron al menos un pago
 * @param saldosPendientes  deuda aun no cobrada de las comandas emitidas en el
 *                          periodo (saldo actual, no una foto historica)
 * @param totalesPorMetodo  arqueo por medio de pago, con los cinco metodos
 * @param porDia            recaudacion dia por dia, con los dias sin cobros en
 *                          cero
 * @param movimientos       detalle de cada pago, ordenado por fecha ascendente
 */
public record CierreCajaResponseDTO(
		LocalDate desde,
		LocalDate hasta,
		int dias,
		BigDecimal totalFacturado,
		BigDecimal totalCobrado,
		int cantidadPagos,
		int cantidadComandas,
		BigDecimal saldosPendientes,
		Map<String, BigDecimal> totalesPorMetodo,
		List<SerieDiaDTO> porDia,
		List<MovimientoCajaDTO> movimientos) {
}
