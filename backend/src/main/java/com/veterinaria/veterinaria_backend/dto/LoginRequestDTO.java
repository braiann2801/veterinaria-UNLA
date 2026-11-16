package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de ingreso. El usuario se normaliza (trim + minusculas) en el servicio.
 *
 * <p>Los topes no son decorativos: {@code username} es {@code varchar(40)} en la
 * base y BCrypt solo considera los primeros 72 bytes de la clave. Sin el limite,
 * un cuerpo gigante llegaba hasta la consulta y hasta el hash. El control de
 * bytes (no de caracteres) queda en {@code AuthServiceImpl}, porque un acento
 * ocupa dos o tres bytes: 72 caracteres pueden superar los 72 bytes.</p>
 */
public record LoginRequestDTO(

		@NotBlank(message = "El usuario es obligatorio")
		@Size(max = 40, message = "El usuario no puede superar los 40 caracteres")
		String username,

		@NotBlank(message = "La contraseña es obligatoria")
		@Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
		String password) {
}
