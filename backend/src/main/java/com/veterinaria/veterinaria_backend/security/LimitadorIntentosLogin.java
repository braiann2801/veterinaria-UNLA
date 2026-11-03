package com.veterinaria.veterinaria_backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Los fallos se cuentan en una ventana que arranca en el primer fallo. Al
 * llegar al maximo, el par queda bloqueado durante la ventana: mientras dura no
 * se verifica la contrasena, asi que ni la correcta pasa. Un login exitoso borra
 * el contador. El estado vive en memoria: un reinicio lo limpia y, con varias
 * instancias, cada una cuenta por separado.</p>
 */
@Component
public class LimitadorIntentosLogin {

	/** Por encima de este tamaño se purgan las entradas vencidas, para que el mapa no crezca sin limite. */
	private static final int UMBRAL_PURGA = 10_000;

	private final Clock reloj;
	private final int intentosMaximos;
	private final Duration ventana;
	private final Map<String, Estado> estados = new ConcurrentHashMap<>();

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

	/** 429 si el par esta bloqueado. Se llama antes de verificar la contrasena. */
	public void verificar(String clave) {
		Estado estado = estados.get(clave);
		Instant ahora = reloj.instant();
		if (estado != null && estado.bloqueadoHasta() != null && ahora.isBefore(estado.bloqueadoHasta())) {
			long segundos = Duration.between(ahora, estado.bloqueadoHasta()).toSeconds();
			throw new DemasiadosIntentosException(Math.max(1, segundos));
		}
	}

	/** Suma un fallo; al llegar al maximo, bloquea el par durante la ventana. */
	public void registrarFallo(String clave) {
		Instant ahora = reloj.instant();
		// compute es atomico por clave: dos fallos simultaneos no se pisan el conteo.
		estados.compute(clave, (k, previo) -> {
			Estado base = previo == null || vencido(previo, ahora) ? new Estado(0, ahora, null) : previo;
			int fallos = base.fallos() + 1;
			Instant bloqueo = fallos >= intentosMaximos ? ahora.plus(ventana) : null;
			return new Estado(fallos, base.primerFallo(), bloqueo);
		});
		if (estados.size() > UMBRAL_PURGA) {
			estados.entrySet().removeIf(e -> vencido(e.getValue(), ahora));
		}
	}

	/** Un login exitoso resetea el contador del par. */
	public void registrarExito(String clave) {
		estados.remove(clave);
	}

	/** Bloqueo cumplido, o ventana de conteo cerrada sin llegar al maximo. */
	private boolean vencido(Estado estado, Instant ahora) {
		if (estado.bloqueadoHasta() != null) {
			return !ahora.isBefore(estado.bloqueadoHasta());
		}
		return !ahora.isBefore(estado.primerFallo().plus(ventana));
	}

	private record Estado(int fallos, Instant primerFallo, Instant bloqueadoHasta) {
	}
}
