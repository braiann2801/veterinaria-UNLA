package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import tools.jackson.databind.ObjectMapper;

/** Matriz de roles del módulo: los cuatro consultan y los cuatro cargan stock. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MedicamentoPermisosTest {

	private static final String CLAVE = "clave-segura-1";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private UsuarioRepository usuarioRepository;

	private void crearUsuario(String username, Usuario.Rol rol) {
		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setNombreCompleto("Usuario " + rol);
		usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
		usuario.setRol(rol);
		usuario.setActivo(true);
		usuario.setDebeCambiarPassword(false);
		usuarioRepository.saveAndFlush(usuario);
	}

	private String token(String username) throws Exception {
		MvcResult respuesta = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", username, "password", CLAVE))))
				.andReturn();
		assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
		return objectMapper.readTree(respuesta.getResponse().getContentAsString()).get("token").asString();
	}

	@Test
	@DisplayName("Sin token el módulo responde 401 con el contrato de error")
	void sinToken() throws Exception {
		mockMvc.perform(get("/api/v1/medicamentos"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	@DisplayName("Duennio, Veterinario, Recepción y Guardería consultan, crean y cargan stock")
	void todosLosRolesOperan() throws Exception {
		for (Usuario.Rol rol : Usuario.Rol.values()) {
			String username = "med-" + rol.name().toLowerCase();
			crearUsuario(username, rol);
			String autorizacion = "Bearer " + token(username);

			mockMvc.perform(get("/api/v1/medicamentos").header(HttpHeaders.AUTHORIZATION, autorizacion))
					.andExpect(status().isOk());

			MvcResult alta = mockMvc.perform(post("/api/v1/medicamentos")
					.header(HttpHeaders.AUTHORIZATION, autorizacion)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
							"nombre", "Medicamento de " + rol.name(), "stockInicial", 1))))
					.andExpect(status().isCreated())
					.andReturn();
			long id = objectMapper.readTree(alta.getResponse().getContentAsString()).get("id").asLong();

			mockMvc.perform(post("/api/v1/medicamentos/{id}/stock", id)
					.header(HttpHeaders.AUTHORIZATION, autorizacion)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("cantidad", 2))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.stockActual").value(3));

			mockMvc.perform(get("/api/v1/medicamentos/{id}/movimientos", id)
					.header(HttpHeaders.AUTHORIZATION, autorizacion))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElementos").value(2));
		}
	}
}
