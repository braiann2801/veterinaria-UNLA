package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Deuda a cobrar por un tutor. Es el unico lugar del sistema donde vive la
 * logica contable.
 *
 * <p><b>Decoupling clinico-contable (AGENTS.md 2.3).</b> La ficha clinica no
 * sabe que esto existe. La comanda se emite al guardar la consulta, y el
 * mostrador la liquida con uno o varios {@link Pago}. Si la atencion se anula,
 * la comanda se cancela: son dos hechos independientes y por eso son dos
 * entidades separadas.</p>
 *
 * <p><b>Los pagos acumulados son la unica via para bajar el saldo.</b> No hay
 * un setter publico de {@code saldoPendiente}: si lo hubiera, cualquiera
 * podria "dar por saldada" una comanda sin registrar el cobro, y la caja
 * cerraria descuadrada.</p>
 */
@Entity
@Table(name = "comanda_cobro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComandaCobro {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "concepto", nullable = false, length = 30)
	private Concepto concepto;

	/** Monto congelado al emitir la comanda, con 2 decimales. */
	@Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
	private BigDecimal montoTotal;

	/** Lo que falta pagar. Lo unica forma de bajarlo es {@link #registrarPago}. */
	@Column(name = "saldo_pendiente", nullable = false, precision = 12, scale = 2)
	private BigDecimal saldoPendiente;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoComanda estado = EstadoComanda.PENDIENTE;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tutor_id", nullable = false)
	private Tutor tutor;

	/** Opcional: las comandas de guarderia pueden no tener mascota. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mascota_id")
	private Mascota mascota;

	/**
	 * Ficha clinica que origino esta comanda. Sirve de trazabilidad y permite
	 * idempotencia: no se emiten dos comandas para la misma consulta.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "consulta_medica_id")
	private ConsultaMedica consultaMedica;

	@Column(name = "fecha_emision", nullable = false)
	private LocalDateTime fechaEmision = LocalDateTime.now();

	/**
	 * Pagos aplicados. Con cascada porque un pago no tiene vida propia sin su
	 * comanda: si se borra la comanda, sus pagos tambien.
	 */
	@OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Pago> pagos = new ArrayList<>();

	public enum Concepto {
		CONSULTA_CLINICA,
		GUARDERIA,
		OTRO
	}

	public enum EstadoComanda {
		PENDIENTE,
		PARCIAL,
		PAGADA,
		CANCELADA
	}

	/**
	 * Aplica un pago y recalcula el estado.
	 *
	 * <p>Valida que el pago no exceda el saldo. Sin esta comprobacion, dos
	 * pagos concurrentes sobre la misma comanda podrian dejar el saldo en
	 * negativo y el total cobrado sin correspondencia con lo facturado.</p>
	 *
	 * @param pago pago ya asociado a esta comanda y persistido
	 * @throws IllegalArgumentException si el monto supera el saldo pendiente
	 */
	public void registrarPago(Pago pago) {
		BigDecimal monto = pago.getMonto().setScale(2, RoundingMode.HALF_UP);
		BigDecimal saldo = saldoPendiente.setScale(2, RoundingMode.HALF_UP);

		if (monto.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("El monto del pago debe ser mayor a cero");
		}
		if (monto.compareTo(saldo) > 0) {
			throw new IllegalArgumentException(String.format(
					"El pago de %s excede el saldo pendiente de %s", monto, saldo));
		}

		this.saldoPendiente = saldo.subtract(monto).setScale(2, RoundingMode.HALF_UP);
		this.pagos.add(pago);
		this.estado = recalcularEstado();
	}

	/**
	 * Estado derivado del saldo.
	 *
	 * <p>Una comanda CANCELADA conserva su estado aunque mas tarde reciba pagos:
	 * la cancelacion es una decision administrativa del mostrador y no se
	 * revierte sola porque el saldo llegue a cero.</p>
	 */
	private EstadoComanda recalcularEstado() {
		if (estado == EstadoComanda.CANCELADA) {
			return EstadoComanda.CANCELADA;
		}
		if (saldoPendiente.compareTo(BigDecimal.ZERO) == 0) {
			return EstadoComanda.PAGADA;
		}
		if (saldoPendiente.compareTo(montoTotal) < 0) {
			return EstadoComanda.PARCIAL;
		}
		return EstadoComanda.PENDIENTE;
	}

	/**
	 * Un tutor es moroso si tiene al menos una comanda con saldo por cobrar.
	 *
	 * <p>Las comandas CANCELADAS y PAGADAS no cuentan: una cancelada no debe
	 * nada y una pagada esta saldada. Solo PENDIENTE y PARCIAL generan deuda
	 * real.</p>
	 */
	public boolean tieneSaldoPendiente() {
		boolean activa = estado == EstadoComanda.PENDIENTE || estado == EstadoComanda.PARCIAL;
		return activa && saldoPendiente.compareTo(BigDecimal.ZERO) > 0;
	}
}