package com.veterinaria.veterinaria_backend.dto;

import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Carga de stock: siempre una suma, nunca un stock final. */
public record AgregarStockRequestDTO(
		@NotNull(message = "La cantidad es obligatoria")
		@Positive(message = "La cantidad a agregar debe ser mayor a cero")
		Integer cantidad,
		@Size(max = 500, message = "La observación no puede superar los 500 caracteres")
		String observacion) {

	public AgregarStockRequestDTO {
		observacion = NormalizadorAr.texto(observacion);
	}
}
