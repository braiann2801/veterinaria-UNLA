package com.veterinaria.veterinaria_backend.entity;

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
 * Ficha clinica de una atencion veterinaria (AGENTS.md 2.3).
 *
 * <p><b>REGLA INVIOLABLE: esta entidad no tiene ni un solo campo contable.</b>
 * Ni monto, ni medio de pago, ni saldo, ni forma de cobro. Quien estiba
 * precios dentro de la ficha clinica rompe el desacople y termina con dos
 * fuentes de verdad que se contradicen. El precio vive en {@link Tarifa} y el
 * cobro en {@link ComandaCobro}.</p>
 *
 * <p>Al persistir la ficha, {@code ConsultaMedicaService} emite la comanda
 * correspondiente en estado PENDIENTE, en la misma transaccion: o queda la ficha
 * con su comanda, o no queda ninguna de las dos.</p>
 */
@Entity
@Table(name = "consulta_medica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsultaMedica {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Turno que origino la atencion. Opcional: hay consultas de urgencia sin
	 * turno previo, y no conviene inventar un turno solo para poder cobrar.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "turno_clinico_id")
	private TurnoClinico turnoClinico;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mascota_id", nullable = false)
	private Mascota mascota;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profesional_id", nullable = false)
	private Profesional profesional;

	@Column(name = "fecha_atencion", nullable = false)
	private LocalDateTime fechaAtencion;

	/** Relato del propietario: motivo de consulta, cambios de apetito, etc. */
	@Column(name = "anamnesis", nullable = false, length = 2000)
	private String anamnesis;

	@Column(name = "diagnostico", length = 2000)
	private String diagnostico;

	@Column(name = "tratamiento", length = 2000)
	private String tratamiento;

	@Enumerated(EnumType.STRING)
	@Column(name = "conducta_observada", length = 20)
	private ConductaObservada conductaObservada = ConductaObservada.TRANQUILO;

	/**
	 * Alerta visual de conducta (regla 2.5). Se deriva de
	 * {@link #conductaObservada} dentro del setter, no se acepta por API: si el
	 * cliente pudiera mandarla, bastaria un {@code alertaConducta: false} con
	 * {@code conducta: AGRESIVO} para que la advertencia se perdiera en
	 * silencio. El servidor es la unica fuente de verdad.
	 */
	@Column(name = "alerta_conducta", nullable = false)
	private boolean alertaConducta;

	public void setConductaObservada(ConductaObservada conductaObservada) {
		this.conductaObservada = conductaObservada;
		this.alertaConducta = conductaObservada != null && conductaObservada.generaAlerta();
	}

	public enum ConductaObservada {
		TRANQUILO,
		NERVIOSO,
		REACTIVO,
		AGRESIVO;

		/** Las dos conductas que exigen advertencia antes de un ingreso a guarderia. */
		public boolean generaAlerta() {
			return this == REACTIVO || this == AGRESIVO;
		}
	}
}