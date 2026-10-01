package com.veterinaria.veterinaria_backend.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cuerpo de error unificado que devuelve la API.
 * Evita filtrar la traza interna al cliente.
 */
public record ApiErrorResponse(
		LocalDateTime timestamp,
		int status,
		String error,
		String mensaje,
		String ruta,
		List<String> detalles) {

	public static ApiErrorResponse of(int status, String error, String mensaje, String ruta) {
		return new ApiErrorResponse(LocalDateTime.now(), status, error, mensaje, ruta, List.of());
	}

	public static ApiErrorResponse of(int status, String error, String mensaje, String ruta,
			List<String> detalles) {
		return new ApiErrorResponse(LocalDateTime.now(), status, error, mensaje, ruta, detalles);
	}
}