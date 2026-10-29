package com.veterinaria.veterinaria_backend.dto;

import com.veterinaria.veterinaria_backend.entity.Usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de una cuenta por el Duenio. No trae contrasena: el servidor genera una
 * temporal, la devuelve una sola vez y la cuenta nace con cambio obligatorio.
 * Asi el Duenio nunca elige ni conoce la clave definitiva del empleado.
 */
public record UsuarioRequestDTO(

		@NotBlank(message = "El usuario es obligatorio")
		@Pattern(regexp = "^\\s*[A-Za-z0-9._-]{3,40}\\s*$",
				message = "El usuario debe tener entre 3 y 40 caracteres: letras, números, punto, guion o guion bajo")
		String username,

		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
		String nombreCompleto,

		@NotNull(message = "El rol es obligatorio")
		Usuario.Rol rol) {
}
