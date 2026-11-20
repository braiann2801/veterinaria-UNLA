package com.veterinaria.veterinaria_backend.security;

/**
 * Servicio de alto nivel para el control de tasa de intentos (rate limiting).
 *
 * <p>Aplica reglas agregadas:</p>
 * <ul>
 *   <li>Par (usuario, IP): previene fuerza bruta sobre cuentas específicas.</li>
 *   <li>Por IP (anti-password spraying): detecta pruebas masivas sobre múltiples usuarios desde un mismo origen.</li>
 *   <li>Por usuario (anti-ataque distribuido): detecta ataques coordinados desde múltiples IPs contra una misma cuenta.</li>
 *   <li>Por usuario / IP en cambio de contraseña: previene fuerza bruta sobre la contraseña actual.</li>
 * </ul>
 */
public interface RateLimiterService {

	/**
	 * Verifica y reserva cupos para el ingreso de usuario e IP.
	 * Lanza {@link com.veterinaria.veterinaria_backend.exception.DemasiadosIntentosException} si alguna regla bloquea.
	 */
	void verificarLogin(String usernameNormalizado, String ip);

	/** Registra fallo en las reglas de login e impone bloqueos si se alcanzó el límite. */
	void registrarFalloLogin(String usernameNormalizado, String ip);

	/** Registra ingreso exitoso y resetea contadores del usuario y del par. */
	void registrarExitoLogin(String usernameNormalizado, String ip);

	/** Verifica cupo para intento de cambio de contraseña actual. */
	void verificarCambioPassword(Long usuarioId, String ip);

	/** Registra fallo de contraseña actual en cambio de contraseña. */
	void registrarFalloCambioPassword(Long usuarioId, String ip);

	/** Registra cambio de contraseña exitoso. */
	void registrarExitoCambioPassword(Long usuarioId, String ip);
}
