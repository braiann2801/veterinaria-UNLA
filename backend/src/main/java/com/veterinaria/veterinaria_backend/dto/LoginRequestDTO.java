package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales de ingreso. El usuario se normaliza (trim + minusculas) en el servicio. */
public record LoginRequestDTO(

		@NotBlank(message = "El usuario es obligatorio")
		String username,

		@NotBlank(message = "La contraseña es obligatoria")
		String password) {
}
