package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

/**
 * Alta o edicion de un extra del catalogo. {@code activo} es opcional: en el
 * alta vale {@code true} y en la edicion, si no viene, conserva el valor actual.
 */
public record ExtraRequestDTO(
		@NotBlank(message = "El nombre del extra es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
		String nombre,
		@NotNull(message = "El precio es obligatorio")
		@PositiveOrZero(message = "El precio no puede ser negativo")
		BigDecimal precio,
		Boolean activo) {

	/** Sin espacios de mas y con la primera letra en mayuscula ("vacuna  antirrabica" -> "Vacuna antirrabica"). */
	public ExtraRequestDTO {
		String limpio = NormalizadorAr.texto(nombre);
		nombre = limpio == null ? "" : limpio.substring(0, 1).toUpperCase(java.util.Locale.forLanguageTag("es-AR"))
				+ limpio.substring(1);
	}
}
