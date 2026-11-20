package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LogoutRevocacionTest {

	private static final String CLAVE = "clave-segura-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

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

	@Test
	@DisplayName("Logout revoca la sesión inmediatamente: POST /api/v1/auth/logout retorna 204 con Cache-Control: no-store y la sesion ya no vale")
	void logoutRevocaInmediatamente() throws Exception {
		Usuario u = crearUsuario("logout-user", Usuario.Rol.RECEPCION, true, false);
		int versionInicial = u.getTokenVersion();

		String t = token("logout-user");

		// Verificamos que el token inicialmente autentica correctamente
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("logout-user"));

		// Ejecutamos logout
		mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Cache-Control", "no-store"));

		// En la base de datos se incrementó tokenVersion
		Usuario uActualizado = usuarioRepository.findById(u.getId()).orElseThrow();
		assertThat(uActualizado.getTokenVersion()).isEqualTo(versionInicial + 1);

		// El token anterior ahora es rechazado con 401 Unauthorized
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	@DisplayName("Logout sin token da 401")
	void logoutSinToken401() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Usuario con cambio de clave pendiente tambien puede hacer logout")
	void usuarioConDebeCambiarPasswordPuedeHacerLogout() throws Exception {
		crearUsuario("pendiente-logout", Usuario.Rol.DUENIO, true, true);
		String t = token("pendiente-logout");

		mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Cache-Control", "no-store"));

		// Su token anterior queda revocado
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + t))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("El TTL por defecto del JWT es de 4 horas (JWT_HORAS=4)")
	void ttlPorDefectoCuatroHoras() throws Exception {
		crearUsuario("ttl-test", Usuario.Rol.RECEPCION, true, false);
		Instant antes = Instant.now();
		MvcResult r = login("ttl-test", CLAVE);
		assertThat(r.getResponse().getStatus()).isEqualTo(200);

		JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
		Instant expiraEn = Instant.parse(body.get("expiraEn").asString());

		Duration diferencia = Duration.between(antes, expiraEn);
		// Debe estar alrededor de 4 horas (entre 3h 59m y 4h 1m)
		assertThat(diferencia.toMinutes()).isBetween(238L, 242L);
	}
}
