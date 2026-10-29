package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de la propia contrasena. Se pide la actual aunque el usuario ya tenga
 * token: un celular desbloqueado en el mostrador no alcanza para tomar la cuenta.
 *
 * <p>El maximo de 72 es el limite de BCrypt: por encima, los bytes extra se
 * ignoran en silencio y dos claves distintas valdrian lo mismo.</p>
 */
public record CambioPasswordRequestDTO(

		@NotBlank(message = "La contraseña actual es obligatoria")
		String actual,

		@NotBlank(message = "La contraseña nueva es obligatoria")
		@Size(min = 8, max = 72, message = "La contraseña nueva debe tener entre 8 y 72 caracteres")
		String nueva) {
}
