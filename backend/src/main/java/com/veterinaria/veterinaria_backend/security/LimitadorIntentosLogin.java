package com.veterinaria.veterinaria_backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.exception.DemasiadosIntentosException;

/**
 * Limite de intentos fallidos del login, contra la fuerza bruta.
 *
 * <p>La clave es el par (usuario normalizado, IP): un bloqueo solo por usuario
 * dejaria que cualquiera deje sin acceso a un empleado desde otra IP, y uno solo
 * por IP bloquearia a todo un local detras de la misma conexion.</p>
 *
 * <p>Cada intento se reserva antes de verificar la contrasena, en una sola
 * operacion atomica: asi, aunque lleguen muchos pedidos a la vez, nunca pasan
 * mas intentos que el maximo. Si la contrasena es correcta, el contador se
 * borra; si es incorrecta y se llego al maximo, el par queda bloqueado durante
 * la ventana, y mientras dura no se verifica la contrasena (ni la correcta pasa).</p>
 *
 * <p>El estado vive en memoria: un reinicio lo limpia y, con varias instancias,
 * cada una cuenta por separado.</p>
 */
@Component
public class LimitadorIntentosLogin {

	private static final Logger log = LoggerFactory.getLogger(LimitadorIntentosLogin.class);

	/** Por encima de este tamaño se purgan las entradas vencidas. */
	private static final int UMBRAL_PURGA = 10_000;
	/** La purga recorre todo el mapa: como mucho una vez por minuto, para que no se repita en cada pedido. */
	private static final Duration INTERVALO_PURGA = Duration.ofMinutes(1);

	private final Clock reloj;
	private final int intentosMaximos;
	private final Duration ventana;
	private final Map<String, Estado> estados = new ConcurrentHashMap<>();
	private final AtomicReference<Instant> ultimaPurga = new AtomicReference<>(Instant.EPOCH);

	public LimitadorIntentosLogin(Clock reloj,
			@Value("${veterinaria.auth.login-intentos-maximos:5}") int intentosMaximos,
			@Value("${veterinaria.auth.login-bloqueo-minutos:15}") long minutosDeBloqueo) {
		if (intentosMaximos < 1 || minutosDeBloqueo < 1) {
			throw new IllegalStateException("login-intentos-maximos y login-bloqueo-minutos deben ser al menos 1");
		}
		this.reloj = reloj;
		this.intentosMaximos = intentosMaximos;
		this.ventana = Duration.ofMinutes(minutosDeBloqueo);
	}

	/** Clave del contador: el mismo usuario desde otra IP cuenta aparte. */
	public static String clave(String usernameNormalizado, String ip) {
		return usernameNormalizado + "|" + ip;
	}

	/**
	 * Reserva un intento para el par, o responde 429 si esta bloqueado o ya uso
	 * todos sus intentos de la ventana. Se llama antes de verificar la contrasena.
	 */
	public void verificar(String clave) {
		Instant ahora = reloj.instant();
		boolean[] reservado = { false };
		// compute es atomico por clave: dos pedidos simultaneos no pueden reservar el mismo cupo.
		Estado estado = estados.compute(clave, (k, previo) -> {
			Estado base = previo == null || vencido(previo, ahora) ? new Estado(0, ahora, null) : previo;
			if (base.bloqueadoHasta() != null || base.intentos() >= intentosMaximos) {
				return base;
			}
			reservado[0] = true;
			return new Estado(base.intentos() + 1, base.primerIntento(), null);
		});
		if (!reservado[0]) {
			Instant fin = estado.bloqueadoHasta() != null ? estado.bloqueadoHasta() : estado.primerIntento().plus(ventana);
			throw new DemasiadosIntentosException(Math.max(1, Duration.between(ahora, fin).toSeconds()));
		}
	}

	/** El intento reservado fallo: si fue el ultimo permitido, bloquea el par durante la ventana. */
	public void registrarFallo(String clave) {
		Instant ahora = reloj.instant();
		Estado estado = estados.computeIfPresent(clave, (k, actual) ->
				actual.bloqueadoHasta() == null && actual.intentos() >= intentosMaximos
						? new Estado(actual.intentos(), actual.primerIntento(), ahora.plus(ventana))
						: actual);
		if (estado != null && ahora.plus(ventana).equals(estado.bloqueadoHasta())) {
			// Sin la contrasena ni datos personales: el usuario y la IP alcanzan para investigar.
			log.warn("Login bloqueado {} min por {} intentos fallidos: {}", ventana.toMinutes(), intentosMaximos, clave);
		}
		purgarSiHaceFalta(ahora);
	}

	/** Un login exitoso resetea el contador del par. */
	public void registrarExito(String clave) {
		estados.remove(clave);
	}

	private void purgarSiHaceFalta(Instant ahora) {
		Instant previa = ultimaPurga.get();
		if (estados.size() <= UMBRAL_PURGA || ahora.isBefore(previa.plus(INTERVALO_PURGA))) {
			return;
		}
		// Solo el hilo que gana el compareAndSet recorre el mapa.
		if (ultimaPurga.compareAndSet(previa, ahora)) {
			estados.entrySet().removeIf(e -> vencido(e.getValue(), ahora));
		}
	}

	/** Bloqueo cumplido, o ventana de conteo cerrada sin bloqueo. */
	private boolean vencido(Estado estado, Instant ahora) {
		if (estado.bloqueadoHasta() != null) {
			return !ahora.isBefore(estado.bloqueadoHasta());
		}
		return !ahora.isBefore(estado.primerIntento().plus(ventana));
	}

	private record Estado(int intentos, Instant primerIntento, Instant bloqueadoHasta) {
	}
}
