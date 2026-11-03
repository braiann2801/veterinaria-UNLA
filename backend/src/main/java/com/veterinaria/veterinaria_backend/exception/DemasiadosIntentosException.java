package com.veterinaria.veterinaria_backend.exception;

/**
 * Login bloqueado por demasiados intentos fallidos (429). Lleva los segundos que
 * faltan para que expire el bloqueo, que viajan tambien en {@code Retry-After}.
 */
public class DemasiadosIntentosException extends RuntimeException {

	private final long segundosRestantes;

	public DemasiadosIntentosException(long segundosRestantes) {
		super(mensaje(segundosRestantes));
		this.segundosRestantes = segundosRestantes;
	}

	public long getSegundosRestantes() {
		return segundosRestantes;
	}

	private static String mensaje(long segundos) {
		long minutos = Math.max(1, (segundos + 59) / 60);
		return "Demasiados intentos de ingreso fallidos. Probá de nuevo en " + minutos
				+ (minutos == 1 ? " minuto." : " minutos.");
	}
}
