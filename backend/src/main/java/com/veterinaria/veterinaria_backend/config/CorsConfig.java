package com.veterinaria.veterinaria_backend.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el frontend Next.js en desarrollo y en produccion.
 *
 * <p>La seccion 1 de AGENTS.md pide aceptar el origen 3000 y los subdominios de
 * {@code vercel.app}; ese es el valor por defecto. En un despliegue concreto se
 * acota con {@code CORS_ORIGENES} (lista separada por comas) para no dejar
 * cualquier aplicacion alojada en vercel consumiendo la API.</p>
 *
 * <p>No se habilitan credenciales: el JWT viaja en el header
 * {@code Authorization}, no en una cookie. Sin {@code allowCredentials}, un
 * sitio ajeno no puede leer respuestas autenticadas aunque comparta origen
 * permitido, y se elimina la combinacion mas riesgosa de CORS (patron comodin +
 * credenciales).</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] origenes;

	public CorsConfig(@Value("${veterinaria.cors.origenes:"
			+ "http://localhost:3000,http://127.0.0.1:3000,https://*.vercel.app}") String origenes) {
		this.origenes = Arrays.stream(origenes.split(","))
				.map(String::trim)
				.filter(origen -> !origen.isEmpty())
				.toArray(String[]::new);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(origenes)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
				.allowedHeaders("*")
				.exposedHeaders("Location")
				.allowCredentials(false)
				.maxAge(3600);
	}
}
