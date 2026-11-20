package com.veterinaria.veterinaria_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.security.AlmacenRateLimitRedis.ConfiguracionConexion;

/** Parseo de REDIS_URL: sin requerir servidor Redis ni base de datos. */
class AlmacenRateLimitRedisTest {

	private static final Clock RELOJ = Clock.systemUTC();

	@Test
	@DisplayName("Parsea host, puerto, password y database de una URL redis://")
	void parseaUrlCompleta() {
		ConfiguracionConexion cfg = AlmacenRateLimitRedis.parsearUrl("redis://:clave@redis.example.com:6380/2");

		assertThat(cfg.host()).isEqualTo("redis.example.com");
		assertThat(cfg.port()).isEqualTo(6380);
		assertThat(cfg.password()).isEqualTo("clave");
		assertThat(cfg.ssl()).isFalse();
		assertThat(cfg.database()).isEqualTo(2);
	}

	@Test
	@DisplayName("rediss:// habilita TLS y usa el puerto estandar 6379 si no se especifica otro")
	void redissUsaPuertoEstandar() {
		ConfiguracionConexion cfg = AlmacenRateLimitRedis.parsearUrl("rediss://:clave@redis.example.com/0");

		assertThat(cfg.ssl()).isTrue();
		assertThat(cfg.port()).isEqualTo(6379);
		assertThat(cfg.password()).isEqualTo("clave");
	}

	@Test
	@DisplayName("Una URL invalida cae a defaults locales sin exponer credenciales")
	void urlInvalidaUsaDefaults() {
		ConfiguracionConexion cfg = AlmacenRateLimitRedis.parsearUrl("redis://usuario\r\nmalicioso@host:puerto");

		assertThat(cfg.host()).isEqualTo("localhost");
		assertThat(cfg.port()).isEqualTo(6379);
		assertThat(cfg.password()).isNull();
		assertThat(cfg.ssl()).isFalse();
	}

	@Test
	@DisplayName("El almacen expone la configuracion resuelta para el log de arranque")
	void exponeConfiguracionDelConstructor() {
		AlmacenRateLimitRedis almacen = new AlmacenRateLimitRedis("rediss://:clave@cache.example.com:6381/1", RELOJ);

		assertThat(almacen.getHost()).isEqualTo("cache.example.com");
		assertThat(almacen.getPort()).isEqualTo(6381);
		assertThat(almacen.isSsl()).isTrue();
	}
}
