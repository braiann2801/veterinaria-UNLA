package com.veterinaria.veterinaria_backend.exception;

/**
 * Recurso inexistente. Se traduce a HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ResourceNotFoundException(String mensaje) {
		super(mensaje);
	}

	public static ResourceNotFoundException tutor(Long id) {
		return new ResourceNotFoundException("No existe un tutor con id " + id);
	}

	public static ResourceNotFoundException mascota(Long id) {
		return new ResourceNotFoundException("No existe una mascota con id " + id);
	}
}