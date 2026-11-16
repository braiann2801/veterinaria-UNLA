package com.veterinaria.veterinaria_backend.exception;

/**
 * Error de entrada cuyo mensaje esta pensado para el usuario y se puede mostrar
 * tal cual.
 *
 * <p>La mayoria de los fallos de deserializacion no deben llegar al cliente: el
 * texto de Jackson nombra clases, paquetes y fragmentos del JSON. Pero algunos
 * los escribimos nosotros a proposito, como el formato de fecha que acepta
 * {@code FlexibleLocalDateTimeDeserializer}. Esta excepcion marca esa diferencia:
 * el manejador central muestra su mensaje y usa uno generico para el resto.</p>
 */
public class MensajePublicoException extends RuntimeException {

	public MensajePublicoException(String mensaje) {
		super(mensaje);
	}
}
