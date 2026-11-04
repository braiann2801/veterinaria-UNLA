package com.veterinaria.veterinaria_backend.importacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.veterinaria.veterinaria_backend.entity.Pago;

/**
 * Interpretacion manual de la columna {@code Detalle_Clinico_y_Cobro} y de
 * {@code Estado_Pago}.
 *
 * <p>El origen mezcla texto clinico con montos, medios de pago y saldos en una
 * sola celda. Parsear esa prosa con expresiones regulares seria fragil:
 * "abono senia previa de $30.000 y hoy saldo restante de $35.000" no tiene una
 * gramatica estable. Por eso cada fila se concilio a mano y se guarda aca,
 * identificada por el numero de ficha/reserva. El constructor del plan verifica
 * que la fila exista en el Excel y que los nombres/fechas coincidan; si no,
 * la fila se rechaza y queda en el reporte.</p>
 *
 * <p>Todo pago asumido (porque el texto no dice el medio) queda marcado en la
 * nota y se repite en el reporte de importacion.</p>
 */
public final class ConciliacionHistorica {

	public record PagoConciliado(Pago.MetodoPago metodo, BigDecimal monto, String nota) {
	}

	public record Conciliacion(BigDecimal total, List<PagoConciliado> pagos, String nota) {

		public BigDecimal pagado() {
			return pagos.stream()
					.map(PagoConciliado::monto)
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.setScale(2, RoundingMode.HALF_UP);
		}

		public BigDecimal saldo() {
			return total.subtract(pagado()).setScale(2, RoundingMode.HALF_UP);
		}

		public BigDecimal sena() {
			return pagos.stream()
					.filter(pago -> pago.metodo() == Pago.MetodoPago.SENA)
					.map(PagoConciliado::monto)
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.setScale(2, RoundingMode.HALF_UP);
		}
	}

	private static final Map<Integer, Conciliacion> CONSULTAS = Map.ofEntries(
			Map.entry(501, new Conciliacion(
					m(18500),
					List.of(p(Pago.MetodoPago.EFECTIVO, 18500, "El texto dice 'en efectivo'")),
					"Cobrado completo en efectivo.")),
			Map.entry(502, new Conciliacion(
					m(24000),
					List.of(p(Pago.MetodoPago.TRANSFERENCIA, 24000, "MP se interpreta como transferencia")),
					"Total consulta + vacuna; pagado por Mercado Pago.")),
			Map.entry(503, new Conciliacion(
					m(37000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 27000,
							"Medio no informado: se asume efectivo")),
					"Placa $15.000 + atencion $22.000 = $37.000; queda debiendo $10.000.")),
			Map.entry(504, new Conciliacion(
					m(65000),
					List.of(
							p(Pago.MetodoPago.SENA, 30000, "Senia previa"),
							p(Pago.MetodoPago.TRANSFERENCIA, 35000, "Saldo por transferencia bancaria")),
					"Senia de $30.000 y saldo de $35.000 por transferencia.")),
			Map.entry(505, new Conciliacion(
					m(28000),
					List.of(p(Pago.MetodoPago.DEBITO, 28000, "Tarjeta de debito")),
					"Cobrado con tarjeta de debito.")),
			Map.entry(506, new Conciliacion(
					m(9000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 9000,
							"Medio no informado: se asume efectivo")),
					"Atencion bonificada al 50%; total $9.000. No dice medio de pago.")),
			Map.entry(507, new Conciliacion(
					m(8500),
					List.of(p(Pago.MetodoPago.EFECTIVO, 8500, "El texto dice 'en billete'")),
					"Cobrado en efectivo.")),
			Map.entry(508, new Conciliacion(
					m(14000),
					List.of(),
					"Debe $14.000: sin pago registrado.")),
			Map.entry(509, new Conciliacion(
					m(21000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 21000, "El texto dice 'en mano'")),
					"Pagado en efectivo.")),
			Map.entry(510, new Conciliacion(
					m(32000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 16000,
							"Medio no informado: se asume efectivo")),
					"Total $32.000; pago la mitad y deja $16.000 para la semana siguiente.")));

	private static final Map<Integer, Conciliacion> GUARDERIAS = Map.ofEntries(
			Map.entry(101, new Conciliacion(
					m(45000),
					List.of(p(Pago.MetodoPago.SENA, 20000, "Senia de $20.000")),
					"Senia $20.000; resto $25.000 al retiro.")),
			Map.entry(102, new Conciliacion(
					m(12000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 12000,
							"Medio no informado: se asume efectivo")),
					"Pagado completo; no dice medio de pago.")),
			Map.entry(103, new Conciliacion(
					m(32000),
					List.of(),
					"Transferencia pendiente: sin pago registrado.")),
			Map.entry(104, new Conciliacion(
					m(95000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 95000,
							"Medio no informado: se asume efectivo")),
					"Abono el 100% anticipado; no dice medio de pago.")),
			Map.entry(105, new Conciliacion(
					m(8000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 8000, "El texto dice 'efectivo'")),
					"Pagado en efectivo.")),
			Map.entry(106, new Conciliacion(
					m(15000),
					List.of(p(Pago.MetodoPago.SENA, 5000, "Senia de $5.000")),
					"Senia $5.000; resto $10.000 pendiente.")),
			Map.entry(107, new Conciliacion(
					m(68000),
					List.of(),
					"Debe el total: sin pago registrado.")),
			Map.entry(108, new Conciliacion(
					m(14000),
					List.of(p(Pago.MetodoPago.TRANSFERENCIA, 14000, "MP se interpreta como transferencia")),
					"Pagado por Mercado Pago.")),
			Map.entry(109, new Conciliacion(
					m(75000),
					List.of(p(Pago.MetodoPago.SENA, 30000, "Senia de $30.000")),
					"Senia $30.000; resto $45.000 pendiente.")),
			Map.entry(110, new Conciliacion(
					m(36000),
					List.of(p(Pago.MetodoPago.EFECTIVO, 36000, "Efectivo en mano")),
					"Pagado en efectivo.")));

	private ConciliacionHistorica() {
	}

	public static Conciliacion consulta(int numero) {
		return CONSULTAS.get(numero);
	}

	public static Conciliacion guarderia(int numero) {
		return GUARDERIAS.get(numero);
	}

	private static BigDecimal m(long valor) {
		return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
	}

	private static PagoConciliado p(Pago.MetodoPago metodo, long monto, String nota) {
		return new PagoConciliado(metodo, m(monto), nota);
	}
}
