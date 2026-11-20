package com.veterinaria.veterinaria_backend.security;

/**
 * Resultado de una verificación de límite de intentos.
 *
 * @param permitido si el intento fue aceptado dentro de la cuota
 * @param segundosParaReintentar segundos restantes de bloqueo o hasta expirar la ventana si fue rechazado
 */
public record ResultadoRateLimit(boolean permitido, long segundosParaReintentar) {

	public static ResultadoRateLimit concedido() {
		return new ResultadoRateLimit(true, 0);
	}

	public static ResultadoRateLimit bloqueado(long segundos) {
		return new ResultadoRateLimit(false, Math.max(1, segundos));
	}
}
