package com.veterinaria.veterinaria_backend.dto;

/**
 * Resumen minimo de un tutor.
 *
 * <p>No contiene referencias a mascotas, por lo que puede anidarse dentro de
 * {@link MascotaResponseDTO} sin generar ciclos de serializacion.</p>
 */
public record TutorResumenDTO(
		Long id,
		String nombre,
		String apellido,
		String telefono,
		boolean autorizadoRetiro) {

	public String nombreCompleto() {
		if (nombre == null && apellido == null) {
			return "";
		}
		if (apellido == null) {
			return nombre;
		}
		if (nombre == null) {
			return apellido;
		}
		return nombre + " " + apellido;
	}
}