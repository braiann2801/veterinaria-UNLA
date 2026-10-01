package com.veterinaria.veterinaria_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el frontend Next.js en desarrollo y en produccion.
 *
 * <p>Requisito de la seccion 1 de AGENTS.md: el backend en 8080 debe aceptar
 * el origen 3000 y los subdominios de vercel.app.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(
						"http://localhost:3000",
						"http://127.0.0.1:3000",
						"https://*.vercel.app")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.exposedHeaders("Location")
				.allowCredentials(true)
				.maxAge(3600);
	}
}