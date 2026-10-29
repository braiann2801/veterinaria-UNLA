package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.security.SecurityConfig;

/**
 * El secreto de firma nunca puede ser un valor conocido.
 *
 * <p>Sin {@code JWT_SECRET}, el backend no cae en un literal del repositorio
 * (que permitiria fabricar un token de Duenio a cualquiera que lea el codigo):
 * genera una clave aleatoria en cada arranque.</p>
 */
class JwtSecretTest {

	private final SecurityConfig config = new SecurityConfig();

	@Test
	@DisplayName("sin JWT_SECRET se genera una clave aleatoria de 256 bits, distinta en cada arranque")
	void sinSecretoGeneraClaveAleatoria() {
		SecretKey a = config.jwtSecretKey("");
		SecretKey b = config.jwtSecretKey("   ");
		assertThat(a.getEncoded()).hasSize(32);
		assertThat(b.getEncoded()).hasSize(32);
		assertThat(a.getEncoded()).isNotEqualTo(b.getEncoded());
	}

	@Test
	@DisplayName("con JWT_SECRET se usa ese valor")
	void conSecretoUsaElValor() {
		String secreto = "un-secreto-de-produccion-de-32-bytes!!";
		assertThat(config.jwtSecretKey(secreto).getEncoded())
				.isEqualTo(secreto.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("un JWT_SECRET de menos de 32 bytes corta el arranque")
	void secretoCortoFalla() {
		assertThatThrownBy(() -> config.jwtSecretKey("corto"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}
}
