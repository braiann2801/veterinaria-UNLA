package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta/edicion de una mascota.
 *
 * <p>La lista de tutores llega como DTOs anidados; cada uno declara de forma
 * explicita el flag de autorizacion de retiro.</p>
 */
public record MascotaRequestDTO(

		@NotBlank(message = "El nombre de la mascota es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
		String nombre,

		@NotNull(message = "La especie es obligatoria")
		EspecieRequestDTO especie,

		@Size(max = 80, message = "La raza no puede superar los 80 caracteres")
		String raza,

		@Pattern(regexp = "^(Macho|Hembra)$", message = "El sexo debe ser Macho o Hembra")
		String sexo,

		@Past(message = "La fecha de nacimiento no puede ser futura")
		LocalDate fechaNacimiento,

		@Positive(message = "El peso debe ser mayor a 0")
		BigDecimal pesoKg,

		// Acepta las dos grafias porque el enum de la base es ASCII (GRUNE) y el
		// cliente usa la palabra acentuada; el mapper normaliza a una sola.
		@Pattern(regexp = "^(normal|grune|gruñe|muerde|miedoso)$",
				message = "La conducta debe ser normal, gruñe, muerde o miedoso")
		String conducta,

		@Size(max = 40, message = "El chip no puede superar los 40 caracteres")
		String chip,

		@Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
		String observaciones,

		List<@Valid VinculoTutorDTO> tutores) {

	public enum EspecieRequestDTO {
		CANINO,
		FELINO,
		AVE,
		ROEDOR,
		REPTIL,
		OTRO
	}

	/**
	 * Tutor a vincular con la mascota.
	 *
	 * @param tutorId          id del tutor a vincular, obligatorio
	 * @param autorizadoRetiro si ese tutor puede retirar a la mascota (regla 2.4)
	 */
	public record VinculoTutorDTO(

			@NotNull(message = "El id del tutor es obligatorio")
			Long tutorId,

			@NotNull(message = "Debe indicar explicitamente si el tutor esta autorizado a retirar")
			Boolean autorizadoRetiro) {
	}
}