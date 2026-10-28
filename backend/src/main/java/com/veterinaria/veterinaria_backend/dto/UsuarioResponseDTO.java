package com.veterinaria.veterinaria_backend.dto;

import com.veterinaria.veterinaria_backend.entity.Usuario;

/** Vista de una cuenta de empleado. No incluye el hash de la contrasena. */
public record UsuarioResponseDTO(
		Long id,
		String username,
		String nombreCompleto,
		Usuario.Rol rol,
		boolean activo,
		boolean debeCambiarPassword) {
}
