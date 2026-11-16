package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recaudacion de un dia dentro de un periodo del Balance.
 *
 * <p>El Balance por periodo necesita ver <b>como viene</b> la caja, no solo el
 * total: dos meses con la misma recaudacion pueden ser uno parejo y otro con una
 * semana muerta y un pico. La serie se arma en la base agrupando por dia y se
 * devuelve completa, con los dias sin cobros en cero, para que la vista no tenga
 * que reconstruir huecos ni adivinar que dia falta.</p>
 *
 * @param fecha          dia de la serie
 * @param totalCobrado   suma de los pagos de ese dia
 * @param cantidadPagos  filas de pago registradas ese dia (un cobro mixto suma
 *                       mas de una)
 */
public record SerieDiaDTO(
		LocalDate fecha,
		BigDecimal totalCobrado,
		int cantidadPagos) {
}
