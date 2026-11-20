package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;
import com.veterinaria.veterinaria_backend.security.AlmacenRateLimitRedis;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
		"veterinaria.auth.login-ip-intentos-maximos=20",
		"veterinaria.auth.login-usuario-intentos-maximos=15"
})
class RateLimitingExtendidoTest {

	private static final String CLAVE = "clave-segura-1";

	@TestConfiguration
	static class RelojConfigTest {
		@Bean
		@Primary
		RelojMovibleExtendido relojMovible() {
			return new RelojMovibleExtendido();
		}
	}

	static class RelojMovibleExtendido extends Clock {
		private Instant ahora = Instant.parse("2032-05-01T10:00:00Z");

		void adelantar(Duration duracion) {
			ahora = ahora.plus(duracion);
		}

		@Override
		public Instant instant() {
			return ahora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RelojMovibleExtendido reloj;

	@BeforeEach
	void resetReloj() {
		reloj.adelantar(Duration.ofHours(2));
	}

	private Usuario crearUsuario(String username) {
		Usuario u = new Usuario();
		u.setUsername(username);
		u.setNombreCompleto("Usuario " + username);
		u.setPasswordHash(passwordEncoder.encode(CLAVE));
		u.setRol(Usuario.Rol.RECEPCION);
		u.setActivo(true);
		u.setDebeCambiarPassword(false);
		return usuarioRepository.saveAndFlush(u);
	}

	private MvcResult login(String username, String password, String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
				.andReturn();
	}

	@Test
	@DisplayName("Anti-Password Spraying: 20 fallos desde una misma IP bloquean esa IP")
	void antiPasswordSprayingPorIp() throws Exception {
		String ipAtaque = "192.168.100.50";
		// Probamos 20 usuarios distintos desde la misma IP (1 fallo por usuario para no activar la regla de par)
		for (int i = 1; i <= 20; i++) {
			MvcResult r = login("spray.user." + i, "claveIncorrecta", ipAtaque);
			assertThat(r.getResponse().getStatus()).isEqualTo(401);
		}

		// El intento 21 desde la misma IP queda bloqueado con 429 y Retry-After
		MvcResult bloqueado = login("spray.user.21", "claveIncorrecta", ipAtaque);
		assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
		assertThat(bloqueado.getResponse().getHeader("Retry-After")).isNotNull();

		// Desde otra IP, los pedidos siguen funcionando normalmente
		MvcResult otraIp = login("spray.user.21", "claveIncorrecta", "192.168.100.99");
		assertThat(otraIp.getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("Anti-Ataque Distribuido: 15 fallos contra el mismo usuario desde IPs distintas bloquean al usuario")
	void antiAtaqueDistribuidoPorUsuario() throws Exception {
		String usuarioObjetivo = "usuario.objetivo";
		crearUsuario(usuarioObjetivo);

		// 15 intentos fallidos desde 15 IPs distintas (1 fallo por IP)
		for (int i = 1; i <= 15; i++) {
			String ipDistribuida = "10.50.1." + i;
			MvcResult r = login(usuarioObjetivo, "claveMala", ipDistribuida);
			assertThat(r.getResponse().getStatus()).isEqualTo(401);
		}

		// El intento 16 contra ese usuario desde una IP nueva recibe 429
		MvcResult bloqueado = login(usuarioObjetivo, CLAVE, "10.50.1.100");
		assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
		assertThat(bloqueado.getResponse().getHeader("Retry-After")).isNotNull();

		// Otro usuario desde esa misma IP puede ingresar
		crearUsuario("otro.usuario");
		MvcResult otroOk = login("otro.usuario", CLAVE, "10.50.1.100");
		assertThat(otroOk.getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Rate limiting en cambiar-password: 5 contraseñas actuales fallidas bloquean con 429 y Retry-After")
	void rateLimitCambioPassword() throws Exception {
		Usuario u = crearUsuario("cambio-rate");
		MvcResult loginRes = login("cambio-rate", CLAVE, "10.0.0.88");
		String token = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asString();

		String ip = "10.0.0.88";

		// 5 intentos con clave actual errónea -> 400 Bad Request
		for (int i = 0; i < 5; i++) {
			MvcResult r = mockMvc.perform(post("/api/v1/auth/cambiar-password")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.with(req -> {
						req.setRemoteAddr(ip);
						return req;
					})
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("actual", "mala-" + i, "nueva", "nuevaClave1234"))))
					.andReturn();
			assertThat(r.getResponse().getStatus()).isEqualTo(400);
		}

		// El 6to intento está bloqueado con 429
		MvcResult rBloqueado = mockMvc.perform(post("/api/v1/auth/cambiar-password")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.with(req -> {
					req.setRemoteAddr(ip);
					return req;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", CLAVE, "nueva", "nuevaClave1234"))))
				.andReturn();
		assertThat(rBloqueado.getResponse().getStatus()).isEqualTo(429);
		assertThat(rBloqueado.getResponse().getHeader("Retry-After")).isNotNull();

		// Adelantamos reloj 16 minutos
		reloj.adelantar(Duration.ofMinutes(16));

		// Ahora con la clave correcta se procesa con éxito (200 OK)
		MvcResult rOk = mockMvc.perform(post("/api/v1/auth/cambiar-password")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.with(req -> {
					req.setRemoteAddr(ip);
					return req;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", CLAVE, "nueva", "nuevaClave1234"))))
				.andReturn();
		assertThat(rOk.getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("AlmacenRateLimitRedis parsea correctamente URLs estandar redis:// y rediss://")
	void parseoUrlRedis() {
		AlmacenRateLimitRedis r1 = new AlmacenRateLimitRedis("redis://default:secreto@redis.host.com:6380/3", Clock.systemUTC());
		assertThat(r1.getHost()).isEqualTo("redis.host.com");
		assertThat(r1.getPort()).isEqualTo(6380);
		assertThat(r1.isSsl()).isFalse();

		AlmacenRateLimitRedis r2 = new AlmacenRateLimitRedis("rediss://:otraClave@ssl-redis.upstash.io:6379", Clock.systemUTC());
		assertThat(r2.getHost()).isEqualTo("ssl-redis.upstash.io");
		assertThat(r2.getPort()).isEqualTo(6379);
		assertThat(r2.isSsl()).isTrue();

		AlmacenRateLimitRedis r3 = new AlmacenRateLimitRedis("", Clock.systemUTC());
		assertThat(r3.getHost()).isEqualTo("localhost");
		assertThat(r3.getPort()).isEqualTo(6379);
	}
}
