package com.veterinaria.veterinaria_backend.exception;

/**
 * Login rechazado. Se traduce a HTTP 401.
 *
 * <p>El mensaje es siempre el mismo para clave incorrecta, usuario inexistente o
 * cuenta desactivada: distinguirlos le diria a un tercero que usuarios existen.</p>
 */
public class CredencialesInvalidasException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public static final String MENSAJE = "Usuario o contraseña incorrectos";

	public CredencialesInvalidasException() {
		super(MENSAJE);
	}
}
