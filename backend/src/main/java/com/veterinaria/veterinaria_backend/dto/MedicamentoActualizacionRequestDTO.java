package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Edición completa de un medicamento del catálogo.
 *
 * <p>{@code stockActual} es el valor deseado, no una suma. Viene acompañado de
 * {@code stockActualAnterior} (el valor que el operador vio en pantalla) para
 * detectar cambios concurrentes y no sobrescribirlos. Cualquier modificación
 * del stock exige una observación y queda registrada como movimiento
 * {@code AJUSTE}.</p>
 */
public record MedicamentoActualizacionRequestDTO(
		@NotBlank(message = "El nombre del medicamento es obligatorio")
		@Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
		String nombre,
		@Size(max = 120, message = "La presentación no puede superar los 120 caracteres")
		String presentacion,
		@Size(max = 120, message = "El principio activo no puede superar los 120 caracteres")
		String principioActivo,
		@Size(max = 120, message = "El laboratorio no puede superar los 120 caracteres")
		String laboratorio,
		@NotNull(message = "El precio es obligatorio")
		@PositiveOrZero(message = "El precio no puede ser negativo")
		@Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
		BigDecimal precio,
		@NotNull(message = "El stock mínimo es obligatorio")
		@PositiveOrZero(message = "El stock mínimo no puede ser negativo")
		Integer stockMinimo,
		@NotNull(message = "El stock actual es obligatorio")
		@PositiveOrZero(message = "El stock actual no puede ser negativo")
		Integer stockActual,
		@NotNull(message = "El stock anterior es obligatorio")
		@PositiveOrZero(message = "El stock anterior no puede ser negativo")
		Integer stockActualAnterior,
		@Size(max = 500, message = "La observación no puede superar los 500 caracteres")
		String observacionStock) {

	public MedicamentoActualizacionRequestDTO {
		nombre = NormalizadorAr.texto(nombre);
		presentacion = NormalizadorAr.texto(presentacion);
		principioActivo = NormalizadorAr.texto(principioActivo);
		laboratorio = NormalizadorAr.texto(laboratorio);
		observacionStock = NormalizadorAr.texto(observacionStock);
		if (precio != null) {
			precio = precio.setScale(2, RoundingMode.HALF_UP);
		}
	}

	/** Clave de unicidad del nombre, sin acentos ni mayúsculas. */
	public String nombreNormalizado() {
		return NormalizadorAr.clave(nombre);
	}

	/** Clave de unicidad de la presentación; vacía cuando no se cargó ninguna. */
	public String presentacionNormalizada() {
		return NormalizadorAr.clave(presentacion);
	}
}
