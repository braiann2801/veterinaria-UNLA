package com.veterinaria.veterinaria_backend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicacion como bean: el codigo que depende del paso del tiempo
 * (p. ej. el bloqueo de intentos de login) lo recibe inyectado, y los tests lo
 * reemplazan por uno que adelantan a mano en lugar de esperar.
 */
@Configuration
public class RelojConfig {

	@Bean
	public Clock reloj() {
		return Clock.systemDefaultZone();
	}
}
