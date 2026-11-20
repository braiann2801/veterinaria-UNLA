package com.veterinaria.veterinaria_backend.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.veterinaria.veterinaria_backend.security.AlmacenRateLimit;
import com.veterinaria.veterinaria_backend.security.AlmacenRateLimitMemoria;
import com.veterinaria.veterinaria_backend.security.AlmacenRateLimitRedis;
import com.veterinaria.veterinaria_backend.security.SanitizadorLog;

@Configuration
public class RateLimitConfig {

	private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

	@Bean
	public AlmacenRateLimit almacenRateLimit(
			Clock reloj,
			@Value("${veterinaria.auth.rate-limit.storage:memory}") String storage,
			@Value("${veterinaria.auth.rate-limit.redis-url:}") String redisUrl) {
		if ("redis".equalsIgnoreCase(storage) && redisUrl != null && !redisUrl.isBlank()) {
			log.info("Inicializando almacén de rate limit con Redis: {}", SanitizadorLog.sanitizarUrl(redisUrl));
			return new AlmacenRateLimitRedis(redisUrl, reloj);
		}
		log.info("Inicializando almacén de rate limit en memoria (local).");
		return new AlmacenRateLimitMemoria(reloj);
	}
}
