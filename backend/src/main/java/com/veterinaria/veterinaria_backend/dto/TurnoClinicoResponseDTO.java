package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

/**
 * Vista publica de un turno clinico.
 *
 * <p>Mascota y profesional se proyectan como resumenes planos para no arrastrar
 * sus propias colecciones (cada una tiene su relacion N:M, y anidarlas aqui
 * reintroduce el ciclo que TASK-002 elimino).</p>
 */
public record TurnoClinicoResponseDTO(
		Long id,
		LocalDateTime fechaHoraInicio,
		LocalDateTime fechaHoraFin,
		LocalDateTime fechaFinBloque,
		String motivo,
		String estado,
		MascotaTurnoResumenDTO mascota,
		ProfesionalResumenDTO profesional) {

	/** Mascota en version plana, sin tutores. */
	public record MascotaTurnoResumenDTO(Long id, String nombre) {
	}

	/** Profesional en version plana. */
	public record ProfesionalResumenDTO(Long id, String nombreCompleto, String matricula) {
	}
}