package com.veterinaria.veterinaria_backend.dto;

import java.util.List;

/**
 * Vista publica de un tutor. Las mascotas se exponen como
 * {@link MascotaResumenDTO} y cada vinculo incluye el flag de retiro, evitando
 * cualquier referencia de vuelta al tutor (sin ciclos de Jackson).
 */
public record TutorResponseDTO(
		Long id,
		String nombre,
		String apellido,
		String dni,
		String telefono,
		String email,
		String direccion,
		String nombreCompleto,
		List<MascotaVinculadaDTO> mascotas) {

	/**
	 * Mascota resumida dentro del tutor, con el flag de retiro de este tutor
	 * sobre esa mascota en particular.
	 */
	public record MascotaVinculadaDTO(
			Long id,
			String nombre,
			MascotaResumenDTO.Especie especie,
			String raza,
			boolean autorizadoRetiro) {
	}
}