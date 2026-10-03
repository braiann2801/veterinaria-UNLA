package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pago individual aplicado a una {@link ComandaCobro}.
 *
 * <p>Una comanda admite cuantos pagos haga falta hasta saldar el saldo (regla
 * 2.5, caja multipago): $4.000 en efectivo y $6.000 por transferencia son dos
 * filas de esta tabla, no un unico pago difuminado. Cada medio de pago queda
 * registrado por separado porque el cierre de caja necesita arqueo por metodo.</p>
 *
 * <p>Los valores se normalizan a 2 decimales con {@code HALF_UP} al construir,
 * para que el saldo de la comanda y la suma de los pagos no divergan por
 * precision flotante.</p>
 */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", nullable = false)
	private ComandaCobro comanda;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo_pago", nullable = false, length = 20)
	private MetodoPago metodoPago;

	@Column(name = "monto", nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora = LocalDateTime.now();

	/**
	 * Quien pago efectivamente.
	 *
	 * <p>Es nullable a proposito: los pagos anteriores a TASK-009 no lo tienen, y
	 * rellenarlo con el tutor de la comanda seria inventar un dato que nadie
	 * registro. Cuando el cliente lo omite se asume que pago el deudor de la
	 * comanda, que es el caso normal y el unico posible antes de esta fase.</p>
	 *
	 * <p>No reemplaza al {@code tutor} de la comanda ni lo reassigna: la deuda
	 * sigue siendo de quien la contrajo. Si un cotutor paga y la comanda queda
	 * saldada, la deuda desaparece para todos; si queda un saldo, sigue siendo del
	 * titular. Moverle el deudor al que pago haria que el deudor real apareciera al
	 * dia y el que pago quedara moroso, que es justo lo contrario de lo justo.</p>
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tutor_pagador_id")
	private Tutor tutorPagador;

	public enum MetodoPago {
		EFECTIVO,
		TRANSFERENCIA,
		DEBITO,
		CREDITO,
		SENA
	}

	/**
	 * Normaliza el monto a 2 decimales.
	 *
	 * <p>Sin esto, un pago de {@code 4000} queda con escala 0 y el saldo
	 * quedan con escala 2: el BigDecimal resultante no es igual a cero aunque
	 * numericamente lo sea, y la comanda nunca llegaria a PAGADA.</p>
	 */
	public void setMonto(BigDecimal monto) {
		this.monto = monto == null ? null : monto.setScale(2, RoundingMode.HALF_UP);
	}
}