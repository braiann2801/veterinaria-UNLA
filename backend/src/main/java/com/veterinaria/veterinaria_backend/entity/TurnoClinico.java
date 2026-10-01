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
 * Turno clinico: bloque de 45 minutos de atencion seguidos de 15 de
 * desinfeccion (regla de negocio 2.2 de AGENTS.md).
 *
 * <p>Las tres marcas de tiempo se derivan del inicio y no se aceptan desde la
 * API: la ventana de ocupacion real va de {@code fechaHoraInicio} a
 * {@code fechaFinBloque} (+60 min), y ese es el rango que se usa para detectar
 * solapamientos entre turnos del mismo profesional.</p>
 */
@Entity
@Table(name = "turno_clinico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TurnoClinico {

	/** Duracion de atencion medica, en minutos. */
	public static final int DURACION_ATENCION_MINUTOS = 45;

	/** Desinfeccion obligatoria entre bloques, en minutos. */
	public static final int DESINFECCION_MINUTOS = 15;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha_hora_inicio", nullable = false)
	private LocalDateTime fechaHoraInicio;

	/** Inicio + 45 min. Fin de la atencion medica. */
	@Column(name = "fecha_hora_fin", nullable = false)
	private LocalDateTime fechaHoraFin;

	/** Inicio + 60 min. Fin del bloque incluyendo desinfeccion. */
	@Column(name = "fecha_fin_bloque", nullable = false)
	private LocalDateTime fechaFinBloque;

	@Column(name = "motivo", nullable = false, length = 300)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoTurno estado = EstadoTurno.PENDIENTE;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mascota_id", nullable = false)
	private Mascota mascota;

	/** Profesional responsable. El solapamiento se valida por este campo. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profesional_id", nullable = false)
	private Profesional profesional;

	/**
	 * Deriva fin y fin de bloque a partir del inicio.
	 * Se llama en el constructor y en cada cambio de horario.
	 */
	public void recalcularVentana() {
		if (fechaHoraInicio == null) {
			return;
		}
		this.fechaHoraFin = fechaHoraInicio.plusMinutes(DURACION_ATENCION_MINUTOS);
		this.fechaFinBloque = fechaHoraInicio.plusMinutes(
				DURACION_ATENCION_MINUTOS + DESINFECCION_MINUTOS);
	}

	public void setFechaHoraInicio(LocalDateTime fechaHoraInicio) {
		this.fechaHoraInicio = fechaHoraInicio;
		recalcularVentana();
	}

	public enum EstadoTurno {
		PENDIENTE,
		ATENDIDO,
		CANCELADO
	}
}