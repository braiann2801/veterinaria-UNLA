package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotNull;

/** Activa o desactiva una cuenta. Desactivar corta sus sesiones al instante. */
public record UsuarioActivoRequestDTO(

		@NotNull(message = "Hay que indicar si la cuenta queda activa")
		Boolean activo) {
}
