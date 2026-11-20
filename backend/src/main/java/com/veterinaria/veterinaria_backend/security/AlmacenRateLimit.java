package com.veterinaria.veterinaria_backend.security;

import java.time.Duration;

/**
 * Contrato de almacenamiento para el conteo y bloqueo de intentos de autenticación.
 *
 * <p>Permite desacoplar las reglas de negocio de autenticación del almacén subyacente:
 * en desarrollo local corre en memoria (sin dependencias externas obligatorias),
 * y en producción puede respaldarse en Redis para coordinar múltiples réplicas.</p>
 */
public interface AlmacenRateLimit {

	/**
	 * Verifica y reserva atómicamente un intento para la clave dada.
	 *
	 * @param clave identificador único
	 * @param maxIntentos cuota máxima en la ventana
	 * @param ventana duración de la ventana de evaluación y bloqueo
	 * @return resultado indicando si fue permitido o los segundos de espera si se bloqueó
	 */
	ResultadoRateLimit verificarYConsumir(String clave, int maxIntentos, Duration ventana);

	/**
	 * Registra un fallo para la clave. Si los intentos alcanzaron el máximo,
	 * establece el bloqueo durante la duración de la ventana.
	 *
	 * @return true si la clave quedó bloqueada tras este fallo
	 */
	boolean registrarFallo(String clave, int maxIntentos, Duration ventana);

	/**
	 * Resetea y limpia el estado para la clave tras una operación exitosa.
	 */
	void registrarExito(String clave);

	/**
	 * Consulta los segundos de bloqueo vigentes para la clave sin consumir intento.
	 *
	 * @return segundos restantes de bloqueo, o 0 si no está bloqueada
	 */
	long obtenerSegundosBloqueo(String clave);
}
