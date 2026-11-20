package com.veterinaria.veterinaria_backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementación en memoria de {@link AlmacenRateLimit} para desarrollo local y tests.
 *
 * <p>Totalmente concurrente mediante {@link ConcurrentHashMap#compute}. Purga periódica
 * de claves vencidas sin bloquear los requests de la aplicación.</p>
 */
public class AlmacenRateLimitMemoria implements AlmacenRateLimit {

	private static final int UMBRAL_PURGA = 10_000;
	private static final Duration INTERVALO_PURGA = Duration.ofMinutes(1);

	private final Clock reloj;
	private final Map<String, Estado> estados = new ConcurrentHashMap<>();
	private final AtomicReference<Instant> ultimaPurga = new AtomicReference<>(Instant.EPOCH);

	public AlmacenRateLimitMemoria(Clock reloj) {
		this.reloj = reloj != null ? reloj : Clock.systemUTC();
	}

	@Override
	public ResultadoRateLimit verificarYConsumir(String clave, int maxIntentos, Duration ventana) {
		Instant ahora = reloj.instant();
		boolean[] reservado = { false };
		long[] segundosEspera = { 0 };

		estados.compute(clave, (k, previo) -> {
			Estado base = previo == null || vencido(previo, ahora, ventana)
					? new Estado(0, ahora, null)
					: previo;

			if (base.bloqueadoHasta() != null) {
				segundosEspera[0] = Math.max(1, Duration.between(ahora, base.bloqueadoHasta()).toSeconds());
				return base;
			}
			if (base.intentos() >= maxIntentos) {
				Instant finVentana = base.primerIntento().plus(ventana);
				segundosEspera[0] = Math.max(1, Duration.between(ahora, finVentana).toSeconds());
				return base;
			}
			reservado[0] = true;
			return new Estado(base.intentos() + 1, base.primerIntento(), null);
		});

		if (!reservado[0]) {
			return ResultadoRateLimit.bloqueado(segundosEspera[0]);
		}
		return ResultadoRateLimit.concedido();
	}

	@Override
	public boolean registrarFallo(String clave, int maxIntentos, Duration ventana) {
		Instant ahora = reloj.instant();
		boolean[] acabaDeBloquearse = { false };

		estados.computeIfPresent(clave, (k, actual) -> {
			if (actual.bloqueadoHasta() == null && actual.intentos() >= maxIntentos) {
				acabaDeBloquearse[0] = true;
				return new Estado(actual.intentos(), actual.primerIntento(), ahora.plus(ventana));
			}
			return actual;
		});

		purgarSiHaceFalta(ahora, ventana);
		return acabaDeBloquearse[0];
	}

	@Override
	public void registrarExito(String clave) {
		estados.remove(clave);
	}

	@Override
	public long obtenerSegundosBloqueo(String clave) {
		Instant ahora = reloj.instant();
		Estado estado = estados.get(clave);
		if (estado == null) {
			return 0;
		}
		if (estado.bloqueadoHasta() != null && ahora.isBefore(estado.bloqueadoHasta())) {
			return Math.max(1, Duration.between(ahora, estado.bloqueadoHasta()).toSeconds());
		}
		return 0;
	}

	private void purgarSiHaceFalta(Instant ahora, Duration ventana) {
		Instant previa = ultimaPurga.get();
		if (estados.size() <= UMBRAL_PURGA || ahora.isBefore(previa.plus(INTERVALO_PURGA))) {
			return;
		}
		if (ultimaPurga.compareAndSet(previa, ahora)) {
			estados.entrySet().removeIf(e -> vencido(e.getValue(), ahora, ventana));
		}
	}

	private boolean vencido(Estado estado, Instant ahora, Duration ventana) {
		if (estado.bloqueadoHasta() != null) {
			return !ahora.isBefore(estado.bloqueadoHasta());
		}
		return !ahora.isBefore(estado.primerIntento().plus(ventana));
	}

	private record Estado(int intentos, Instant primerIntento, Instant bloqueadoHasta) {
	}
}
