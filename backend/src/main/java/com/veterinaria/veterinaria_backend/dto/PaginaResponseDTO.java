package com.veterinaria.veterinaria_backend.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Contrato de paginacion del API. El frontend necesita el total para saber si
 * existe la pagina siguiente sin traer todas las filas; devolver solo una lista
 * lo obligaria a pedir siempre "una mas" para adivinarlo.
 */
public record PaginaResponseDTO<T>(
		List<T> contenido,
		int pagina,
		int tamanio,
		long totalElementos,
		int totalPaginas) {

	public static <E, R> PaginaResponseDTO<R> desde(Page<E> fuente, Function<E, R> mapper) {
		return new PaginaResponseDTO<>(
				fuente.getContent().stream().map(mapper).toList(),
				fuente.getNumber(),
				fuente.getSize(),
				fuente.getTotalElements(),
				fuente.getTotalPages());
	}
}
