package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista publica de un pago aplicado a una comanda.
 *
 * <p>Proyeccion plana a proposito: solo datos del pago. Si arrastrara la
 * comanda, y la comanda arrastra sus pagos, Jackson cicla. El tutor que paga
 * entra como id y nombre, por la misma razon: es una referencia, no un objeto anidado.</p>
 *
 * <p>{@code tutorPagadorId} puede venir null: son los pagos cargados antes de que
 * existiera el concepto de pagador (TASK-009). El frontend lo trata como "no
 * informado", nunca como "no pago nadie".</p>
 */
public record PagoResponseDTO(
		Long id,
		String metodoPago,
		BigDecimal monto,
		LocalDateTime fechaHora,
		Long tutorPagadorId,
		String nombreTutorPagador) {
}