package com.veterinaria.veterinaria_backend.exception;

/**
 * Error de negocio de la API. Se traduce a HTTP 400.
 * Se reserva para reglas de negocio violadas, no para recursos ausentes.
 */
public class BadRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BadRequestException(String mensaje) {
		super(mensaje);
	}
}