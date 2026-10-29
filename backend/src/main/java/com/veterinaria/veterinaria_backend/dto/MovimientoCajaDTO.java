package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila del extracto de caja de una jornada.
 *
 * <p>El Balance del dia necesita ver <b>que se cobro</b>, no solo el arqueo
 * agregado. Se proyecta plano a proposito: solo los campos que la vista usa. Si
 * arrastrara la comanda, y la comanda sus pagos, Jackson ciclaria; y aun sin
 * ciclo, exponer la entidad entera obligaria a serializar relaciones que el
 * mostrador no mira.</p>
 *
 * <p>Los enums viajan como su nombre (igual que {@code PagoResponseDTO}): el
 * frontend los tipa como union y una cadena desconocida salta en compilacion en
 * vez de mostrar un metodo de pago vacio.</p>
 *
 * @param fechaHora     momento del pago; ordena el extracto y define la jornada
 * @param fechaEmision  cuando se emitio la comanda. Si es anterior a la jornada
 *                      que se esta mirando, el cobro esta saldando una deuda de
 *                      otro dia y la vista lo marca como "deuda anterior"
 * @param metodoPago    EFECTIVO, TRANSFERENCIA, DEBITO, CREDITO o SENA
 * @param monto         importe del pago, ya normalizado a 2 decimales
 * @param concepto      motivo de la comanda cobrada
 * @param nombreTutor   deudor de la comanda (no necesariamente quien pago)
 * @param nombrePagador quien pago: un cotutor puede saldar la comanda del
 *                      titular. {@code null} en pagos sin pagador registrado
 * @param nombreMascota puede ser {@code null}: las comandas de guarderia sin
 *                      mascota no la tienen
 * @param estadoComanda estado de la comanda despues del pago
 */
public record MovimientoCajaDTO(
		Long id,
		LocalDateTime fechaHora,
		LocalDateTime fechaEmision,
		String metodoPago,
		BigDecimal monto,
		String concepto,
		String nombreTutor,
		String nombrePagador,
		String nombreMascota,
		String estadoComanda) {
}
