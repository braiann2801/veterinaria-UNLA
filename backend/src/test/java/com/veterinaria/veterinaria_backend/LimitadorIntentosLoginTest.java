package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.exception.DemasiadosIntentosException;
import com.veterinaria.veterinaria_backend.security.LimitadorIntentosLogin;

/** Pruebas del limitador sin Spring: concurrencia y configuracion invalida. */
class LimitadorIntentosLoginTest {

	private static final Clock RELOJ = Clock.fixed(Instant.parse("2031-01-01T12:00:00Z"), ZoneId.of("UTC"));

	@Test
	@DisplayName("Intentos simultaneos no superan el maximo: verificar reserva el intento de forma atomica")
	void intentosSimultaneosNoSuperanElMaximo() throws Exception {
		LimitadorIntentosLogin limitador = new LimitadorIntentosLogin(RELOJ, 5, 15);
		String clave = LimitadorIntentosLogin.clave("ana", "10.0.0.1");
		int hilos = 50;
		AtomicInteger permitidos = new AtomicInteger();
		// Todos verifican antes de que cualquiera registre su fallo: es la ventana
		// en la que una verificacion no atomica deja pasar intentos de mas.
		CountDownLatch verificaron = new CountDownLatch(hilos);
		ExecutorService pool = Executors.newFixedThreadPool(hilos);
		try {
			List<Future<?>> tareas = new ArrayList<>();
			for (int i = 0; i < hilos; i++) {
				tareas.add(pool.submit(() -> {
					boolean paso = false;
					try {
						limitador.verificar(clave);
						permitidos.incrementAndGet();
						paso = true;
					} catch (DemasiadosIntentosException bloqueado) {
						// esperado para los que exceden el maximo
					} finally {
						verificaron.countDown();
					}
					verificaron.await(10, TimeUnit.SECONDS);
					if (paso) {
						limitador.registrarFallo(clave);
					}
					return null;
				}));
			}
			for (Future<?> tarea : tareas) {
				tarea.get(15, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		assertThat(permitidos.get()).isEqualTo(5);
		assertThatThrownBy(() -> limitador.verificar(clave)).isInstanceOf(DemasiadosIntentosException.class);
	}

	@Test
	@DisplayName("Una configuracion invalida falla al arrancar")
	void configuracionInvalida() {
		assertThatThrownBy(() -> new LimitadorIntentosLogin(RELOJ, 0, 15))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("login-intentos-maximos y login-bloqueo-minutos deben ser al menos 1");
		assertThatThrownBy(() -> new LimitadorIntentosLogin(RELOJ, 5, 0))
				.isInstanceOf(IllegalStateException.class);
	}
}
