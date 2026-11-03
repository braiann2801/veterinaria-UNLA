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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Limite de intentos fallidos del login: 5 fallos del mismo usuario desde la
 * misma IP dejan ese par bloqueado 15 minutos (S1-S5 de
 * odd/tasks/login-throttling.md). El reloj es un bean reemplazable, asi el test
 * hace pasar el tiempo sin esperar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginThrottlingTest {

	private static final String CLAVE = "clave-segura-1";
	private static final String MENSAJE_BLOQUEO =
			"Demasiados intentos de ingreso fallidos. Probá de nuevo en 15 minutos.";

	@TestConfiguration
	static class Reloj {
		@Bean
		@Primary
		RelojMovible relojMovible() {
			return new RelojMovible();
		}
	}

	/** Reloj que el test adelanta a mano. */
	static class RelojMovible extends Clock {
		private Instant ahora = Instant.parse("2031-01-01T12:00:00Z");

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
	private RelojMovible reloj;

	@BeforeEach
	void alejarseDeBloqueosAnteriores() {
		// Cada test usa sus propios usuarios, pero por las dudas se deja atras
		// cualquier ventana abierta por otro test de la clase.
		reloj.adelantar(Duration.ofHours(1));
	}

	@Test
	@DisplayName("Tras 5 intentos fallidos el login responde 429 con ApiErrorResponse, aun con la clave correcta")
	void bloqueoTrasCincoFallos() throws Exception {
		crearUsuario("tr.bloqueo");
		for (int i = 0; i < 5; i++) {
			assertThat(login("tr.bloqueo", "mala", "10.0.0.1").getResponse().getStatus()).isEqualTo(401);
		}

		MvcResult bloqueado = login("tr.bloqueo", CLAVE, "10.0.0.1");

		assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
		assertThat(bloqueado.getResponse().getHeader("Retry-After")).isEqualTo("900");
		JsonNode cuerpo = objectMapper.readTree(bloqueado.getResponse().getContentAsString());
		assertThat(cuerpo.get("status").asInt()).isEqualTo(429);
		assertThat(cuerpo.get("error").asString()).isEqualTo("Too Many Requests");
		assertThat(cuerpo.get("mensaje").asString()).isEqualTo(MENSAJE_BLOQUEO);
		assertThat(cuerpo.get("ruta").asString()).isEqualTo("/api/v1/auth/login");
		assertThat(cuerpo.get("timestamp")).isNotNull();
		assertThat(cuerpo.get("detalles").isArray()).isTrue();
		assertThat(cuerpo.has("token")).isFalse();
	}

	@Test
	@DisplayName("El bloqueo expira solo al cumplirse los 15 minutos")
	void bloqueoExpira() throws Exception {
		crearUsuario("tr.expira");
		for (int i = 0; i < 5; i++) {
			login("tr.expira", "mala", "10.0.0.2");
		}

		reloj.adelantar(Duration.ofMinutes(14));
		MvcResult todavia = login("tr.expira", CLAVE, "10.0.0.2");
		assertThat(todavia.getResponse().getStatus()).isEqualTo(429);
		assertThat(todavia.getResponse().getHeader("Retry-After")).isEqualTo("60");
		assertThat(objectMapper.readTree(todavia.getResponse().getContentAsString()).get("mensaje").asString())
				.isEqualTo("Demasiados intentos de ingreso fallidos. Probá de nuevo en 1 minuto.");

		reloj.adelantar(Duration.ofMinutes(1));
		assertThat(login("tr.expira", CLAVE, "10.0.0.2").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Un login exitoso resetea el contador")
	void exitoReseteaElContador() throws Exception {
		crearUsuario("tr.reset");
		for (int i = 0; i < 4; i++) {
			assertThat(login("tr.reset", "mala", "10.0.0.3").getResponse().getStatus()).isEqualTo(401);
		}
		assertThat(login("tr.reset", CLAVE, "10.0.0.3").getResponse().getStatus()).isEqualTo(200);

		// Si el exito no reseteara, el primero de estos ya seria el quinto fallo y el siguiente un 429.
		for (int i = 0; i < 4; i++) {
			assertThat(login("tr.reset", "mala", "10.0.0.3").getResponse().getStatus()).isEqualTo(401);
		}
		assertThat(login("tr.reset", CLAVE, "10.0.0.3").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Los fallos espaciados mas de 15 minutos no se acumulan")
	void fallosViejosNoCuentan() throws Exception {
		crearUsuario("tr.ventana");
		for (int i = 0; i < 4; i++) {
			login("tr.ventana", "mala", "10.0.0.4");
		}
		reloj.adelantar(Duration.ofMinutes(16));
		for (int i = 0; i < 4; i++) {
			assertThat(login("tr.ventana", "mala", "10.0.0.4").getResponse().getStatus()).isEqualTo(401);
		}
		assertThat(login("tr.ventana", CLAVE, "10.0.0.4").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("El bloqueo es por usuario e IP: otra IP y otro usuario siguen entrando")
	void bloqueoPorUsuarioEIp() throws Exception {
		crearUsuario("tr.par");
		crearUsuario("tr.otro");
		for (int i = 0; i < 5; i++) {
			login("tr.par", "mala", "10.0.0.5");
		}
		assertThat(login("tr.par", CLAVE, "10.0.0.5").getResponse().getStatus()).isEqualTo(429);

		assertThat(login("tr.par", CLAVE, "10.0.0.6").getResponse().getStatus()).isEqualTo(200);
		assertThat(login("tr.otro", CLAVE, "10.0.0.5").getResponse().getStatus()).isEqualTo(200);
		// El usuario se normaliza igual que en el login: mayusculas y espacios no esquivan el bloqueo.
		assertThat(login("  TR.PAR ", CLAVE, "10.0.0.5").getResponse().getStatus()).isEqualTo(429);
	}

	@Test
	@DisplayName("Una cuenta inexistente se bloquea igual, sin revelar que no existe")
	void cuentaInexistenteTambienSeBloquea() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(login("tr.nadie", "mala", "10.0.0.7").getResponse().getStatus()).isEqualTo(401);
		}
		MvcResult r = login("tr.nadie", "mala", "10.0.0.7");
		assertThat(r.getResponse().getStatus()).isEqualTo(429);
		assertThat(objectMapper.readTree(r.getResponse().getContentAsString()).get("mensaje").asString())
				.isEqualTo(MENSAJE_BLOQUEO);
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

	private void crearUsuario(String username) {
		Usuario u = new Usuario();
		u.setUsername(username);
		u.setNombreCompleto("Usuario " + username);
		u.setPasswordHash(passwordEncoder.encode(CLAVE));
		u.setRol(Usuario.Rol.RECEPCION);
		u.setActivo(true);
		u.setDebeCambiarPassword(false);
		usuarioRepository.saveAndFlush(u);
	}
}
