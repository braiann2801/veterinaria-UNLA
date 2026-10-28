package com.veterinaria.veterinaria_backend.dto;

import com.veterinaria.veterinaria_backend.entity.Usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Edicion de una cuenta: nombre y rol. El username no cambia (es el login). */
public record UsuarioUpdateDTO(

		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
		String nombreCompleto,

		@NotNull(message = "El rol es obligatorio")
		Usuario.Rol rol) {
}
