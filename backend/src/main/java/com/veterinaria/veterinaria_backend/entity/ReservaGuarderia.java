package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * Reserva de guarderia para una mascota en una fecha.
 *
 * <p>Regla innegociable 2.1 de AGENTS.md: maximo 10 perros concurrentes por
 * dia. Solo cuentan las reservas en estado {@link EstadoReserva#RESERVADO} o
 * {@link EstadoReserva#INGRESADO}; las finalizadas o canceladas liberan cupo.</p>
 */
@Entity
@Table(name = "reserva_guarderia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservaGuarderia {

	/** Aforo maximo e innegociable de la guarderia. */
	public static final int AFORO_MAXIMO = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_estadia", nullable = false, length = 20)
	private TipoEstadia tipoEstadia = TipoEstadia.COMPLETA_24H;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoReserva estado = EstadoReserva.RESERVADO;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mascota_id", nullable = false)
	private Mascota mascota;

	@Column(name = "sena", precision = 10, scale = 2)
	private BigDecimal sena;

	@Column(name = "observaciones", length = 300)
	private String observaciones;

	/** Un estado ocupa cupo en el aforo del dia. */
	public boolean ocupaCupo() {
		return estado == EstadoReserva.RESERVADO || estado == EstadoReserva.INGRESADO;
	}

	public enum TipoEstadia {
		DIURNA,
		NOCTURNA,
		COMPLETA_24H
	}

	public enum EstadoReserva {
		RESERVADO,
		INGRESADO,
		FINALIZADO,
		CANCELADO
	}
}