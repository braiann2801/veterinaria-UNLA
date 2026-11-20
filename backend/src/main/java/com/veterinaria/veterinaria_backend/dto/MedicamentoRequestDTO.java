package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;

import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Alta de un medicamento. Las claves de unicidad se calculan aca, antes de la
 * validacion, para que el servicio no repita la normalizacion en cada llamada.
 */
public record MedicamentoRequestDTO(
		@NotBlank(message = "El nombre del medicamento es obligatorio")
		@Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
		String nombre,
		@Size(max = 120, message = "La presentación no puede superar los 120 caracteres")
		String presentacion,
		@Size(max = 120, message = "El principio activo no puede superar los 120 caracteres")
		String principioActivo,
		@Size(max = 120, message = "El laboratorio no puede superar los 120 caracteres")
		String laboratorio,
		@PositiveOrZero(message = "El stock inicial no puede ser negativo")
		Integer stockInicial,
		@PositiveOrZero(message = "El stock mínimo no puede ser negativo")
		Integer stockMinimo,
		/** Precio unitario cobrado al cliente; se congela en la comanda al emitir. */
		@PositiveOrZero(message = "El precio no puede ser negativo")
		@Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
		BigDecimal precio) {

	public MedicamentoRequestDTO {
		nombre = NormalizadorAr.texto(nombre);
		presentacion = NormalizadorAr.texto(presentacion);
		principioActivo = NormalizadorAr.texto(principioActivo);
		laboratorio = NormalizadorAr.texto(laboratorio);
		if (stockInicial == null) {
			stockInicial = 0;
		}
		if (stockMinimo == null) {
			stockMinimo = 0;
		}
		if (precio == null) {
			precio = BigDecimal.ZERO;
		} else {
			precio = precio.setScale(2, java.math.RoundingMode.HALF_UP);
		}
	}

	/** Clave de unicidad del nombre, sin acentos ni mayusculas. */
	public String nombreNormalizado() {
		return NormalizadorAr.clave(nombre);
	}

	/** Clave de unicidad de la presentación; vacia cuando no se cargo ninguna. */
	public String presentacionNormalizada() {
		return NormalizadorAr.clave(presentacion);
	}
}
