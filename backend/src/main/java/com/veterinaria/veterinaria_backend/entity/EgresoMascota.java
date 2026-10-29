package com.veterinaria.veterinaria_backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entrega registrada de una mascota a un tutor ("Registrar entrega y cerrar").
 *
 * <p>Deja constancia de quien la retiro y cuando. Una mascota se entrega una sola
 * vez por jornada: el UNIQUE (mascota, fecha) frena tambien dos registros
 * concurrentes desde dos celulares.</p>
 */
@Entity
@Table(name = "egreso_mascota",
		uniqueConstraints = @UniqueConstraint(name = "uk_egreso_mascota_fecha", columnNames = { "mascota_id", "fecha" }))
@Getter
@Setter
@NoArgsConstructor
public class EgresoMascota {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mascota_id", nullable = false)
	private Mascota mascota;

	/** Quien se llevo a la mascota: siempre un tutor autorizado y sin deuda al momento del registro. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tutor_id", nullable = false)
	private Tutor tutor;

	/** Jornada del checkout (la fecha elegida en la pantalla de egreso). */
	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	/** Estadia de guarderia que se finalizo con esta entrega, si habia una. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reserva_guarderia_id")
	private ReservaGuarderia reservaGuarderia;
}
