package com.veterinaria.veterinaria_backend.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plaza extra de guarderia habilitada por un Veterinario para una urgencia
 * clinica (excepcion de la regla 1 de AGENTS.md).
 *
 * <p>Una fila por dia habilitado: la existencia de la fila es la habilitacion.
 * La plaza es una sola, no se suma al aforo de 10 y el flujo de reservas
 * normales nunca la usa; la ocupa una {@link ReservaGuarderia} con
 * {@code urgenciaClinica = true}.</p>
 */
@Entity
@Table(name = "plaza_urgencia_guarderia",
		uniqueConstraints = @UniqueConstraint(name = "uk_plaza_urgencia_fecha", columnNames = "fecha"))
@Getter
@Setter
@NoArgsConstructor
public class PlazaUrgenciaGuarderia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	public PlazaUrgenciaGuarderia(LocalDate fecha) {
		this.fecha = fecha;
	}
}
