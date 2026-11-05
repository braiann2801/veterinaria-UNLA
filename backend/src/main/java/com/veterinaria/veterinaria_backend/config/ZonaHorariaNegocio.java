package com.veterinaria.veterinaria_backend.config;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Fija la zona horaria del negocio como zona por defecto de la JVM.
 *
 * <p>Todas las horas del dominio son {@code LocalDateTime} tomadas con
 * {@code now()}: la hora de un pago, la emision de una comanda, la jornada del
 * Balance. Sin esto dependen de la zona del servidor, y en Docker o en la nube
 * es UTC: un cobro de las 20:56 queda a las 23:56 y, desde las 21:00, cae en la
 * jornada del dia siguiente.</p>
 *
 * <p>Corre al prepararse el entorno, antes de crear cualquier bean (pool de
 * conexiones, {@link RelojConfig}), y despues de que se cargaron las
 * propiedades, asi que lee {@code veterinaria.zona-horaria}. Se registra en
 * {@code META-INF/spring.factories} para que aplique tambien a los tests.</p>
 */
public class ZonaHorariaNegocio implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	public static final String PROPIEDAD = "veterinaria.zona-horaria";
	public static final String POR_DEFECTO = "America/Argentina/Buenos_Aires";

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
		String zona = evento.getEnvironment().getProperty(PROPIEDAD, POR_DEFECTO);
		// ZoneId.of falla con una zona inexistente: mejor no arrancar que
		// arrancar con UTC sin avisar (TimeZone.getTimeZone devolveria GMT).
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zona)));
	}

	@Override
	public int getOrder() {
		// Despues del listener que carga application.properties.
		return Ordered.LOWEST_PRECEDENCE;
	}
}
