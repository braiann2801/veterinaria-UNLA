package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * ABM de cuentas por el Duenio (S4 de {@code odd/tasks/autenticacion.md}):
 * alta, cambio de rol, desactivacion y reset de contrasena, con revocacion
 * inmediata de las sesiones afectadas y la guarda de "siempre un Duenio activo".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioAbmTest {

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
	private UsuarioInitializer usuarioInitializer;

	private Usuario crear(String username, Usuario.Rol rol) {
		Usuario u = new Usuario();
		u.setUsername(username);
		u.setNombreCompleto("Usuario " + username);
		u.setPasswordHash(passwordEncoder.encode(CLAVE));
		u.setRol(rol);
		return usuarioRepository.saveAndFlush(u);
	}

	private MvcResult login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
				.andReturn();
	}

	private String bearer(String username) throws Exception {
		MvcResult r = login(username, CLAVE);
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		return "Bearer " + objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asString();
	}

	private JsonNode json(MvcResult r) throws Exception {
		return objectMapper.readTree(r.getResponse().getContentAsString());
	}

	/** Deja a {@code jefa} como unico Duenio activo, para probar la guarda del ultimo Duenio. */
	private void unicoDuenio(Usuario jefa) {
		usuarioRepository.findAll().stream()
				.filter(u -> u.getRol() == Usuario.Rol.DUENIO && !u.getId().equals(jefa.getId()))
				.forEach(u -> u.setActivo(false));
		usuarioRepository.flush();
	}

	@Test
	@DisplayName("S4: alta con clave temporal de un solo uso; el empleado entra y debe cambiarla")
	void altaConClaveTemporal() throws Exception {
		crear("jefa", Usuario.Rol.DUENIO);
		String auth = bearer("jefa");

		MvcResult alta = mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", " Laura.P ", "nombreCompleto", "Laura Pérez", "rol", "GUARDERIA"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.username").value("laura.p"))
				.andExpect(jsonPath("$.usuario.rol").value("GUARDERIA"))
				.andExpect(jsonPath("$.usuario.debeCambiarPassword").value(true))
				.andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
				.andReturn();
		String temporal = json(alta).get("passwordTemporal").asString();
		assertThat(temporal).hasSizeGreaterThanOrEqualTo(10);

		MvcResult ingreso = login("laura.p", temporal);
		assertThat(ingreso.getResponse().getStatus()).isEqualTo(200);
		assertThat(json(ingreso).get("usuario").get("debeCambiarPassword").asBoolean()).isTrue();

		mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.username == 'laura.p')].rol").value("GUARDERIA"));
	}

	@Test
	@DisplayName("S4: alta rechaza username repetido (409) y datos invalidos (400)")
	void altaInvalida() throws Exception {
		crear("jefa", Usuario.Rol.DUENIO);
		crear("ocupado", Usuario.Rol.RECEPCION);
		String auth = bearer("jefa");

		mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", "OCUPADO", "nombreCompleto", "Otro", "rol", "RECEPCION"))))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", "con espacio", "nombreCompleto", "", "rol", "RECEPCION"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles").isArray());
	}

	@Test
	@DisplayName("S4: desactivar corta la sesion abierta y bloquea el login")
	void desactivarRevocaSesion() throws Exception {
		crear("jefa", Usuario.Rol.DUENIO);
		Usuario emp = crear("emp", Usuario.Rol.RECEPCION);
		String auth = bearer("jefa");
		String sesionEmp = bearer("emp");

		mockMvc.perform(put("/api/v1/usuarios/" + emp.getId() + "/activo").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));

		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, sesionEmp))
				.andExpect(status().isUnauthorized());
		assertThat(login("emp", CLAVE).getResponse().getStatus()).isEqualTo(401);

		mockMvc.perform(put("/api/v1/usuarios/" + emp.getId() + "/activo").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": true}"))
				.andExpect(status().isOk());
		assertThat(login("emp", CLAVE).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("S4: cambiar el rol rige al instante y corta la sesion vieja")
	void cambioDeRol() throws Exception {
		crear("jefa", Usuario.Rol.DUENIO);
		Usuario emp = crear("emp", Usuario.Rol.RECEPCION);
		String auth = bearer("jefa");
		String sesionVieja = bearer("emp");

		mockMvc.perform(put("/api/v1/usuarios/" + emp.getId()).header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("nombreCompleto", "Emp Ascendido", "rol", "DUENIO"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("DUENIO"))
				.andExpect(jsonPath("$.nombreCompleto").value("Emp Ascendido"));

		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, sesionVieja))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/caja/cierre").header(HttpHeaders.AUTHORIZATION, bearer("emp")))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("S4: reset genera clave temporal, invalida la vieja y obliga a cambiarla")
	void resetDePassword() throws Exception {
		crear("jefa", Usuario.Rol.DUENIO);
		Usuario emp = crear("emp", Usuario.Rol.VETERINARIO);
		String auth = bearer("jefa");
		String sesionVieja = bearer("emp");

		MvcResult reset = mockMvc.perform(post("/api/v1/usuarios/" + emp.getId() + "/reset-password")
				.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.debeCambiarPassword").value(true))
				.andReturn();
		String temporal = json(reset).get("passwordTemporal").asString();

		mockMvc.perform(get("/api/v1/tutores").header(HttpHeaders.AUTHORIZATION, sesionVieja))
				.andExpect(status().isUnauthorized());
		assertThat(login("emp", CLAVE).getResponse().getStatus()).isEqualTo(401);
		assertThat(login("emp", temporal).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("S4: no se puede dejar el sistema sin un Duenio activo ni desactivarse a uno mismo")
	void guardaDelUltimoDuenio() throws Exception {
		Usuario jefa = crear("jefa", Usuario.Rol.DUENIO);
		unicoDuenio(jefa);
		String auth = bearer("jefa");

		mockMvc.perform(put("/api/v1/usuarios/" + jefa.getId() + "/activo").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
				.andExpect(status().isConflict());
		mockMvc.perform(put("/api/v1/usuarios/" + jefa.getId()).header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("nombreCompleto", "Jefa", "rol", "RECEPCION"))))
				.andExpect(status().isConflict());

		// Con un segundo Duenio, igual no puede desactivarse a si misma.
		crear("socio", Usuario.Rol.DUENIO);
		mockMvc.perform(put("/api/v1/usuarios/" + jefa.getId() + "/activo").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
				.andExpect(status().isConflict());
		assertThat(usuarioRepository.findById(jefa.getId()).orElseThrow().isActivo()).isTrue();
		assertThat(usuarioRepository.findById(jefa.getId()).orElseThrow().getRol()).isEqualTo(Usuario.Rol.DUENIO);
	}

	@Test
	@DisplayName("S4: usuario inexistente da 404")
	void inexistente404() throws Exception {
		crear("jefa", Usuario.Rol.DUENIO);
		mockMvc.perform(post("/api/v1/usuarios/999999/reset-password").header(HttpHeaders.AUTHORIZATION, bearer("jefa")))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("La semilla no promueve a Duenio una cuenta de empleado con el mismo username")
	void semillaNoSecuestraCuenta() {
		usuarioRepository.findAll().forEach(u -> {
			if (u.getRol() == Usuario.Rol.DUENIO) {
				u.setActivo(false);
			}
		});
		usuarioRepository.findByUsername("duenio").ifPresent(u -> {
			u.setRol(Usuario.Rol.RECEPCION);
			u.setActivo(true);
		});
		if (usuarioRepository.findByUsername("duenio").isEmpty()) {
			crear("duenio", Usuario.Rol.RECEPCION);
		}
		usuarioRepository.flush();
		String hashAntes = usuarioRepository.findByUsername("duenio").orElseThrow().getPasswordHash();

		assertThat(usuarioInitializer.sembrar()).isZero();
		Usuario cuenta = usuarioRepository.findByUsername("duenio").orElseThrow();
		assertThat(cuenta.getRol()).isEqualTo(Usuario.Rol.RECEPCION);
		assertThat(cuenta.getPasswordHash()).isEqualTo(hashAntes);
	}

	@Test
	@DisplayName("La clave nueva no puede superar los 72 bytes de BCrypt aunque tenga menos de 72 caracteres")
	void claveNuevaLimiteEnBytes() throws Exception {
		crear("emp", Usuario.Rol.RECEPCION);
		String nueva = "ñ".repeat(40); // 40 caracteres, 80 bytes en UTF-8
		mockMvc.perform(post("/api/v1/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, bearer("emp"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("actual", CLAVE, "nueva", nueva))))
				.andExpect(status().isBadRequest());
	}
}
