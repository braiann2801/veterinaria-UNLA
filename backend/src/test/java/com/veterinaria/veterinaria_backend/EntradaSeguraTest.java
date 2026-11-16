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

/**
 * Verificacion de datos de entrada y de campos que el cliente no debe poder tocar.
 *
 * <p>Cubre la pregunta de seguridad del login: inyeccion SQL en usuario o clave,
 * cuerpos gigantes, tipos y campos inventados. Tambien prueba que el rol y los
 * flags de una cuenta salen de la base y no del JSON, que es la forma practica
 * de "cambiar un dato en el inspector para entrar".</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntradaSeguraTest {

	private static final String CLAVE = "clave-segura-9";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Usuario crearUsuario(String username, Usuario.Rol rol, boolean debeCambiar) {
		Usuario u = new Usuario();
		u.setUsername(username);
		u.setNombreCompleto("Usuario " + username);
		u.setPasswordHash(passwordEncoder.encode(CLAVE));
		u.setRol(rol);
		u.setActivo(true);
		u.setDebeCambiarPassword(debeCambiar);
		return usuarioRepository.saveAndFlush(u);
	}

	private MvcResult loginConCuerpo(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json))
				.andReturn();
	}

	private String token(String username) throws Exception {
		MvcResult r = loginConCuerpo(objectMapper.writeValueAsString(Map.of("username", username, "password", CLAVE)));
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		return objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asString();
	}

	@Test
	@DisplayName("SQLi en usuario o clave devuelve 401 y no toca la base")
	void inyeccionSqlNoEntra() throws Exception {
		crearUsuario("objetivo", Usuario.Rol.DUENIO, false);
		long usuariosAntes = usuarioRepository.count();

		String[] usuarios = {
				"' OR 1=1--",
				"admin' --",
				"x' UNION SELECT 1--",
				"a\"; DROP TABLE usuario; --",
				"duenio' AND '1'='1",
				"1 OR 1=1",
				"\\' OR 1=1 #",
				"' OR SLEEP(5)--",
		};
		String[] claves = {
				"x",
				"' OR '1'='1",
				"x' OR 1=1--",
				"') OR ('1'='1",
		};

		for (String usuario : usuarios) {
			for (String clave : claves) {
				// Cuatro claves por usuario: el limitador corta a los cinco fallos y
				// queremos medir la inyeccion, no el 429.
				MvcResult r = loginConCuerpo(objectMapper.writeValueAsString(
						Map.of("username", usuario, "password", clave)));
				assertThat(r.getResponse().getStatus())
						.as("payload usuario=%s clave=%s", usuario, clave)
						.isEqualTo(401);
			}
		}

		// La tabla sigue existiendo y con las mismas filas: nada se ejecuto.
		assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
		assertThat(usuarioRepository.findByUsername("objetivo")).isPresent();
		// Y el login legitimo sigue funcionando.
		assertThat(loginConCuerpo(objectMapper.writeValueAsString(
				Map.of("username", "objetivo", "password", CLAVE))).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Usuario o clave fuera de rango dan 400, nunca 500")
	void tamaniosFueraDeRango() throws Exception {
		String usuarioLargo = "u".repeat(41);
		String claveLarga = "a".repeat(73);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", usuarioLargo, "password", CLAVE))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detalles[0]").value("El usuario no puede superar los 40 caracteres"));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", "cualquiera", "password", claveLarga))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles[0]").value("La contraseña no puede superar los 72 caracteres"));

		// 40 caracteres pero 80 bytes en UTF-8: @Size no lo ve, el control de
		// bytes del servicio si. Sin el, BCrypt recortaria la clave en silencio.
		String claveAcentuada = "ñ".repeat(40);
		assertThat(claveAcentuada.length()).isEqualTo(40);
		assertThat(claveAcentuada.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(80);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", "cualquiera", "password", claveAcentuada))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La contraseña es demasiado larga"));
	}

	@Test
	@DisplayName("El rol y el estado salen de la base: un campo extra en el JSON no escala privilegios")
	void camposExtraNoEscalanPrivilegios() throws Exception {
		crearUsuario("vet-sincero", Usuario.Rol.VETERINARIO, false);

		MvcResult r = loginConCuerpo("{"
				+ "\"username\":\"vet-sincero\","
				+ "\"password\":\"" + CLAVE + "\","
				+ "\"rol\":\"DUENIO\","
				+ "\"activo\":false,"
				+ "\"id\":1,"
				+ "\"tokenVersion\":0,"
				+ "\"debeCambiarPassword\":false"
				+ "}");

		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		JsonNode usuario = objectMapper.readTree(r.getResponse().getContentAsString()).get("usuario");
		assertThat(usuario.get("rol").asString()).isEqualTo("VETERINARIO");
		assertThat(usuario.get("activo").asBoolean()).isTrue();
		assertThat(usuario.has("passwordHash")).isFalse();

		// El token tambien respeta el rol de la base: los endpoints de Duenio dan 403.
		String auth = "Bearer " + cuerpo(r).get("token").asString();
		mockMvc.perform(get("/api/v1/caja/cierre").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isForbidden());
	}

	private JsonNode cuerpo(MvcResult r) throws Exception {
		return objectMapper.readTree(r.getResponse().getContentAsString());
	}

	@Test
	@DisplayName("PUT /usuarios ignora activo y debeCambiarPassword enviados por el cliente")
	void actualizarUsuarioNoAceptaCamposSensibles() throws Exception {
		crearUsuario("jefe-seguro", Usuario.Rol.DUENIO, false);
		Usuario victima = crearUsuario("victima", Usuario.Rol.VETERINARIO, true);
		String auth = "Bearer " + token("jefe-seguro");

		mockMvc.perform(put("/api/v1/usuarios/" + victima.getId())
				.header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombreCompleto\":\"Victima Editada\",\"rol\":\"RECEPCION\","
						+ "\"activo\":false,\"debeCambiarPassword\":false,\"passwordHash\":\"x\",\"tokenVersion\":99}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("RECEPCION"))
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(jsonPath("$.debeCambiarPassword").value(true))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	@DisplayName("Tarifa con monto nulo o negativo se rechaza antes del servicio")
	void tarifaValidada() throws Exception {
		crearUsuario("jefe-tarifa", Usuario.Rol.DUENIO, false);
		String auth = "Bearer " + token("jefe-tarifa");

		mockMvc.perform(put("/api/v1/comandas/tarifas/OTRO").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles[0]").value("El monto es obligatorio"));

		mockMvc.perform(put("/api/v1/comandas/tarifas/OTRO").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":-1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles[0]").value("El monto no puede ser negativo"));
	}

	@Test
	@DisplayName("Un body ilegible no filtra clases internas ni el stack")
	void bodyIlegibleSinDetalleInterno() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ esto no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La peticion no pudo interpretarse: revise el formato del cuerpo."))
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("com.veterinaria"))));
	}

	@Test
	@DisplayName("Un query param con tipo invalido responde 400 sin detalle interno")
	void queryParamInvalido() throws Exception {
		crearUsuario("recep-param", Usuario.Rol.RECEPCION, false);
		String auth = "Bearer " + token("recep-param");

		mockMvc.perform(get("/api/v1/comandas").param("tutorId", "abc")
				.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El valor de 'tutorId' no tiene el formato esperado."));
	}
}
