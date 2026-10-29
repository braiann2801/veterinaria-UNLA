package com.veterinaria.veterinaria_backend.dto;

/**
 * Respuesta del alta y del reset de contrasena. {@code passwordTemporal} es la
 * unica vez que la clave viaja en claro: no se guarda ni se puede volver a pedir.
 */
public record UsuarioConClaveTemporalDTO(
		UsuarioResponseDTO usuario,
		String passwordTemporal) {
}
