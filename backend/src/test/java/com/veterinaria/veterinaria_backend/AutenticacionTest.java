package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.config.UsuarioInitializer;
import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Autenticacion y matriz de permisos con tokens reales (sin {@code @WithMockUser}).
 *
 * <p>Cubre S2 (la API exige token), S3 (Balance, cierre y tarifas solo para el
 * Duenio) y S5 (el Duenio sembrado debe cambiar la contrasena antes de usar el
 * sistema) de {@code odd/tasks/autenticacion.md}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutenticacionTest {

	private static final String CLAVE = "clave-segura-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private UsuarioInitializer usuarioInitializer;

	private Usuario crearUsuario(String username, Usuario.Rol rol, boolean activo, boolean debeCambiar) {
		Usuario u = new Usuario();
		u.setUsername(username);
		u.setNombreCompleto("Usuario " + username);
		u.setPasswordHash(passwordEncoder.encode(CLAVE));
		u.setRol(rol);
		u.setActivo(activo);
		u.setDebeCambiarPassword(debeCambiar);
		return usuarioRepository.saveAndFlush(u);
	}

	private MvcResult login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
				.andReturn();
	}

	private String token(String username) throws Exception {
		MvcResult r = login(username, CLAVE);
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		return objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asString();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	@Test
	@DisplayName("S2: sin token la API responde 401 con el contrato ApiErrorResponse")
	void sinToken401() throws Exception {
		mockMvc.perform(get("/api/v1/tutores"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.ruta").value("/api/v1/tutores"))
				.andExpect(jsonPath("$.mensaje").exists());
	}

	@Test
	@DisplayName("S2: el health check y el login siguen siendo publicos")
	void rutasPublicas() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isOk());
		crearUsuario("publico", Usuario.Rol.RECEPCION, true, false);
		assertThat(login("publico", CLAVE).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("S2: login correcto devuelve token, vencimiento y usuario sin el hash")
	void loginCorrecto() throws Exception {
		crearUsuario("ana", Usuario.Rol.RECEPCION, true, false);
		MvcResult r = login("  ANA ", CLAVE);
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
		assertThat(body.get("token").asString()).isNotBlank();
		assertThat(body.get("expiraEn").asString()).isNotBlank();
		assertThat(body.get("usuario").get("username").asString()).isEqualTo("ana");
		assertThat(body.get("usuario").get("rol").asString()).isEqualTo("RECEPCION");
		assertThat(body.get("usuario").get("debeCambiarPassword").asBoolean()).isFalse();
		assertThat(body.get("usuario").has("passwordHash")).isFalse();

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(body.get("token").asString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("ana"));
	}

	@Test
	@DisplayName("S2: clave incorrecta, usuario inexistente e inactivo dan el mismo 401")
	void loginInvalido() throws Exception {
		crearUsuario("beto", Usuario.Rol.VETERINARIO, true, false);
		crearUsuario("inactivo", Usuario.Rol.VETERINARIO, false, false);

		MvcResult mala = login("beto", "otra-clave-1");
		MvcResult inexistente = login("nadie", CLAVE);
		MvcResult baja = login("inactivo", CLAVE);

		for (MvcResult r : new MvcResult[] { mala, inexistente, baja }) {
			assertThat(r.getResponse().getStatus()).isEqualTo(401);
			JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
			assertThat(body.get("status").asInt()).isEqualTo(401);
			assertThat(body.get("mensaje").asString()).isEqualTo("Usuario o contraseña incorrectos");
		}
	}

	@Test
	@DisplayName("S2: un token con la firma alterada da 401")
	void tokenAdulterado401() throws Exception {
		crearUsuario("carla", Usuario.Rol.RECEPCION, true, false);
		String t = token("carla");
		String alterado = t.substring(0, t.length() - 4) + (t.endsWith("AAAA") ? "BBBB" : "AAAA");
		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, bearer(alterado)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	@DisplayName("S2: un token vencido da 401")
	void tokenVencido401() throws Exception {
		Usuario u = crearUsuario("dani", Usuario.Rol.RECEPCION, true, false);
		Instant hace = Instant.now().minus(10, ChronoUnit.HOURS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(u.getId().toString())
				.issuedAt(hace)
				.expiresAt(hace.plus(1, ChronoUnit.HOURS))
				.claim("ver", u.getTokenVersion())
				.build();
		String vencido = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, bearer(vencido)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("S3: recepcion, veterinario y guarderia usan la API pero no el cierre ni las tarifas ni usuarios")
	void empleadosSinPermisosDeDuenio() throws Exception {
		for (Usuario.Rol rol : new Usuario.Rol[] { Usuario.Rol.RECEPCION, Usuario.Rol.VETERINARIO, Usuario.Rol.GUARDERIA }) {
			String username = "emp-" + rol.name().toLowerCase();
			crearUsuario(username, rol, true, false);
			String auth = bearer(token(username));

			mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/caja/cierre").header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.status").value(403))
					.andExpect(jsonPath("$.error").value("Forbidden"));
			mockMvc.perform(put("/api/v1/comandas/tarifas/OTRO").header(HttpHeaders.AUTHORIZATION, auth)
					.contentType(MediaType.APPLICATION_JSON).content("{\"monto\": 1}"))
					.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	@DisplayName("S3: HEAD no saltea la regla de solo-Duenio del cierre de caja")
	void headNoSalteaElCierre() throws Exception {
		crearUsuario("recep-head", Usuario.Rol.RECEPCION, true, false);
		mockMvc.perform(head("/api/v1/caja/cierre").header(HttpHeaders.AUTHORIZATION, bearer(token("recep-head"))))
				.andExpect(status().isForbidden());
	}

	private String firmar(JwtClaimsSet claims) {
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	@Test
	@DisplayName("S2: un token firmado sin vencimiento da 401")
	void tokenSinExp401() throws Exception {
		Usuario u = crearUsuario("sin-exp", Usuario.Rol.RECEPCION, true, false);
		String t = firmar(JwtClaimsSet.builder().subject(u.getId().toString()).issuedAt(Instant.now())
				.claim("ver", u.getTokenVersion()).build());
		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, bearer(t)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("S2: un claim ver no entero no se trunca a la version real")
	void verNoEntero401() throws Exception {
		Usuario u = crearUsuario("ver-float", Usuario.Rol.RECEPCION, true, false);
		String t = firmar(JwtClaimsSet.builder().subject(u.getId().toString()).issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
				.claim("ver", u.getTokenVersion() + 0.5).build());
		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, bearer(t)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("S3: el duenio accede al cierre de caja")
	void duenioAccedeAlCierre() throws Exception {
		crearUsuario("jefa", Usuario.Rol.DUENIO, true, false);
		mockMvc.perform(get("/api/v1/caja/cierre").header(HttpHeaders.AUTHORIZATION, bearer(token("jefa"))))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("S5: con cambio pendiente solo se puede consultar /me y cambiar la clave")
	void cambioDePasswordObligatorio() throws Exception {
		crearUsuario("nueva", Usuario.Rol.DUENIO, true, true);
		String viejo = token("nueva");

		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, bearer(viejo)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(viejo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.debeCambiarPassword").value(true));

		mockMvc.perform(post("/api/v1/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, bearer(viejo))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", "no-es-esta", "nueva", "clave-nueva-123"))))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, bearer(viejo))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", CLAVE, "nueva", CLAVE))))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, bearer(viejo))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", CLAVE, "nueva", "corta"))))
				.andExpect(status().isBadRequest());

		MvcResult ok = mockMvc.perform(post("/api/v1/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, bearer(viejo))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", CLAVE, "nueva", "clave-nueva-123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.debeCambiarPassword").value(false))
				.andReturn();
		String nuevo = objectMapper.readTree(ok.getResponse().getContentAsString()).get("token").asString();

		// El cambio de clave cierra las demas sesiones: el token anterior deja de valer.
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(viejo)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, bearer(nuevo)))
				.andExpect(status().isOk());
		assertThat(login("nueva", "clave-nueva-123").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("S5: la semilla crea un duenio con cambio obligatorio solo si no hay ninguno activo")
	void semillaDelDuenio() {
		usuarioRepository.findAll().stream()
				.filter(u -> u.getRol() == Usuario.Rol.DUENIO)
				.forEach(u -> u.setActivo(false));
		usuarioRepository.flush();

		assertThat(usuarioInitializer.sembrar()).isEqualTo(1);
		Usuario sembrado = usuarioRepository.findAll().stream()
				.filter(u -> u.getRol() == Usuario.Rol.DUENIO && u.isActivo())
				.findFirst().orElseThrow();
		assertThat(sembrado.isDebeCambiarPassword()).isTrue();
		assertThat(sembrado.getPasswordHash()).doesNotContain("cambiar1234");

		assertThat(usuarioInitializer.sembrar()).isZero();
	}
}
