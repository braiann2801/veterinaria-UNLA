package com.veterinaria.veterinaria_backend.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta/edicion de un tutor.
 *
 * <p>Los vinculos con mascotas se reciben como DTOs anidados validados en cascada
 * para que la deserializacion nunca toque entidades JPA.</p>
 */
public record TutorRequestDTO(

		@NotBlank(message = "El nombre del tutor es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
		String nombre,

		@NotBlank(message = "El apellido del tutor es obligatorio")
		@Size(max = 80, message = "El apellido no puede superar los 80 caracteres")
		String apellido,

		@NotBlank(message = "El DNI del tutor es obligatorio")
		@Pattern(regexp = "\\d{7,10}", message = "El DNI debe contener entre 7 y 10 digitos")
		String dni,

		@Pattern(regexp = "\\d{6,15}", message = "El telefono debe contener entre 6 y 15 digitos")
		String telefono,

		@Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
				message = "El email no tiene un formato valido")
		String email,

		@Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
		String direccion,

		List<@Valid VinculoMascotaDTO> vinculos) {

	/**
	 * Mascota a vincular con el tutor, identificada por su id.
	 *
	 * @param mascotaId      id de la mascota a vincular, obligatorio
	 * @param autorizadoRetiro si el tutor puede retirar a la mascota (regla 2.4)
	 */
	public record VinculoMascotaDTO(

			@NotNull(message = "El id de la mascota es obligatorio")
			Long mascotaId,

			@NotNull(message = "Debe indicar explicitamente si el tutor esta autorizado a retirar")
			Boolean autorizadoRetiro) {
	}
}