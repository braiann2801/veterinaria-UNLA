package com.veterinaria.veterinaria_backend.dto;

import java.util.List;

/**
 * Tutor con actividad en una jornada, con las mascotas por las que vino.
 *
 * <p>Es la lista de llegada de la caja del dia: en vez de hacer elegir a un tutor
 * entre todo el padron, el mostrador elige entre quienes efectivamente pasaron por
 * el consultorio o dejaron un perro en la guarderia hoy.</p>
 *
 * <p>El agrupado por tutor lo arma el backend y no la vista, y no es cosmetico: un
 * tutor con dos mascotas aparece una sola vez. Armarlo en el cliente daria dos
 * veces la misma persona y el selector terminaria mostrando un renglon que no
 * corresponde a nadie.</p>
 */
public record TutorDelDiaResponseDTO(
		Long tutorId,
		String nombreCompleto,
		String dni,
		List<MascotaDelDiaTutorDTO> mascotas) {

	/** Mascota por la que el tutor esta en la jornada. */
	public record MascotaDelDiaTutorDTO(
			Long mascotaId,
			String nombreMascota,
			List<String> motivos) {
	}
}
