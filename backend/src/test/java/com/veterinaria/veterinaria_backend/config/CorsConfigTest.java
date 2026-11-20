package com.veterinaria.veterinaria_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de la resolucion de origenes CORS sin levantar Spring. */
class CorsConfigTest {

	@Test
	@DisplayName("Sin CORS_ORIGENES solo se aceptan los origenes locales (sin hosting atado)")
	void sinVariableSeUsanLosOrigenesPorDefecto() {
		CorsConfig config = new CorsConfig("", CorsConfig.ORIGENES_POR_DEFECTO);

		assertThat(origenesDe(config))
				.containsExactly("http://localhost:3000", "http://127.0.0.1:3000");
	}

	@Test
	@DisplayName("Con CORS_ORIGENES definida se descarta el comodin amplio de Vercel")
	void variableDefinidaDescartaElComodinAmplio() {
		CorsConfig config = new CorsConfig("https://mi-veterinaria.vercel.app,https://*.vercel.app", "");

		assertThat(origenesDe(config)).containsExactly("https://mi-veterinaria.vercel.app");
	}

	@Test
	@DisplayName("Con CORS_ORIGENES definida no se agrega localhost salvo que se declare")
	void variableDefinidaNoAgregaLocalhost() {
		CorsConfig config = new CorsConfig("https://mi-veterinaria.vercel.app", "");

		assertThat(origenesDe(config)).containsExactly("https://mi-veterinaria.vercel.app");
	}

	@Test
	@DisplayName("Si CORS_ORIGENES solo trae comodines se vuelve a los origenes locales")
	void variableDefinidaSoloConComodinesVuelveALocal() {
		CorsConfig config = new CorsConfig("https://*.vercel.app", "");

		assertThat(origenesDe(config))
				.containsExactly("http://localhost:3000", "http://127.0.0.1:3000");
	}

	@Test
	@DisplayName("Se ignoran entradas vacias y espacios")
	void ignoraEntradasVacias() {
		CorsConfig config = new CorsConfig(" https://a.vercel.app , , https://b.vercel.app ", "");

		assertThat(origenesDe(config)).containsExactly("https://a.vercel.app", "https://b.vercel.app");
	}

	@Test
	@DisplayName("El comodin de Vercel se detecta con esquema, puerto y barra final")
	void detectaComodinesDeVercel() {
		assertThat(CorsConfig.esComodinAmplioVercel("https://*.vercel.app")).isTrue();
		assertThat(CorsConfig.esComodinAmplioVercel("http://*.vercel.app:443/")).isTrue();
		assertThat(CorsConfig.esComodinAmplioVercel("https://mi-app.vercel.app")).isFalse();
		assertThat(CorsConfig.esComodinAmplioVercel("https://*.example.com")).isFalse();
		assertThat(CorsConfig.esComodinAmplioVercel("http://localhost:3000")).isFalse();
	}

	private static String[] origenesDe(CorsConfig config) {
		return config.origenesParaPruebas();
	}
}
