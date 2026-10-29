package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Los datos personales se guardan normalizados segun el uso argentino, sin
 * importar como los escriba el mostrador: nombres con mayuscula inicial, DNI
 * solo con digitos, telefono como numero nacional de 10 digitos y matricula en
 * mayusculas.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class NormalizacionDatosTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("El alta de un tutor guarda nombre, DNI, telefono y email normalizados")
	void tutorNormalizado() throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"juan carlos","apellido":"de la fuente","dni":"30.123.456",
								 "telefono":"+54 9 11 2345-6789","email":" Ana@Mail.COM ",
								 "direccion":"  Av.  Mitre   750 "}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("Juan Carlos"))
				.andExpect(jsonPath("$.apellido").value("De la Fuente"))
				.andExpect(jsonPath("$.dni").value("30123456"))
				.andExpect(jsonPath("$.telefono").value("1123456789"))
				.andExpect(jsonPath("$.email").value("ana@mail.com"))
				.andExpect(jsonPath("$.direccion").value("Av. Mitre 750"))
				.andReturn().getResponse().getContentAsString();
		long id = objectMapper.readTree(json).get("id").asLong();

		// Lo guardado, no solo lo respondido.
		mockMvc.perform(get("/api/v1/tutores/{id}", id))
				.andExpect(jsonPath("$.nombre").value("Juan Carlos"))
				.andExpect(jsonPath("$.dni").value("30123456"))
				.andExpect(jsonPath("$.telefono").value("1123456789"));
	}

	@Test
	@DisplayName("La edicion de un tutor normaliza igual que el alta")
	void edicionNormalizada() throws Exception {
		long id = crearTutor("30123457");
		mockMvc.perform(put("/api/v1/tutores/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"MARÍA JOSÉ","apellido":"o'connor","dni":"30.123.457",
								 "telefono":"(0351) 456-7890"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("María José"))
				.andExpect(jsonPath("$.apellido").value("O'Connor"))
				.andExpect(jsonPath("$.dni").value("30123457"))
				.andExpect(jsonPath("$.telefono").value("3514567890"));
	}

	@Test
	@DisplayName("Telefono con 15 y DNI de 9 digitos dan 400 y no crean nada")
	void datosInvalidosNoCrean() throws Exception {
		int antes = cantidadDeTutores();

		mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Paz","dni":"301.234.567","telefono":"11 15 2345 6789"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El DNI debe tener 7 u 8 digitos (ej.: 30.123.456)")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El telefono debe tener 10 digitos con el codigo de area, sin 0 ni 15 (ej.: 11 2345-6789)")));

		Assertions.assertEquals(antes, cantidadDeTutores());
	}

	@Test
	@DisplayName("Un DNI con puntos choca con el mismo DNI sin puntos")
	void dniDuplicadoConPuntos() throws Exception {
		crearTutor("30123458");
		int antes = cantidadDeTutores();

		mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Paz","dni":"30.123.458"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Ya existe un tutor con el DNI 30123458"));

		Assertions.assertEquals(antes, cantidadDeTutores());
	}

	@Test
	@DisplayName("El egreso encuentra al tutor aunque el DNI se escriba con puntos")
	void egresoPorDniConPuntos() throws Exception {
		long tutorId = crearTutor("30123459");
		long mascotaId = crearMascota("rocco");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/egreso/validar")
						.param("mascotaId", String.valueOf(mascotaId))
						.param("tutorDni", "30.123.459"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tutorId").value(tutorId))
				.andExpect(jsonPath("$.puedeRetirar").value(true));
	}

	@Test
	@DisplayName("La mascota y su raza se guardan con mayuscula inicial")
	void mascotaNormalizada() throws Exception {
		mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"firulais","especie":"CANINO","raza":"golden retriever","conducta":"normal"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("Firulais"))
				.andExpect(jsonPath("$.raza").value("Golden Retriever"));
	}

	@Test
	@DisplayName("La matricula se normaliza y la misma escrita distinto es duplicada")
	void matriculaNormalizada() throws Exception {
		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"laura","apellido":"ruiz","matricula":"m.p. 98765",
								 "dni":"28.765.432","telefono":"+54 11 4567 8901"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("Laura"))
				.andExpect(jsonPath("$.apellido").value("Ruiz"))
				.andExpect(jsonPath("$.matricula").value("MP 98765"))
				.andExpect(jsonPath("$.dni").value("28765432"))
				.andExpect(jsonPath("$.telefono").value("1145678901"));

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Otra","apellido":"Persona","matricula":"MP98765"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La matricula MP 98765 ya esta registrada"));
	}

	// ------------------------------------------------------------- helpers

	private int cantidadDeTutores() throws Exception {
		String json = mockMvc.perform(get("/api/v1/tutores"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).size();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Paz","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascota(String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","conducta":"normal"}
								""".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
