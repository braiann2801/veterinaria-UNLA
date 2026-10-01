package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-003: prueba de los endpoints REST de tutores contra MySQL real.
 * Verifica codigos de estado, validacion y manejo de 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TutorControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("GET lista devuelve 200 con array, incluso vacio")
	void listarDevuelve200() throws Exception {
		mockMvc.perform(get("/api/v1/tutores"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	@DisplayName("GET by id inexistente devuelve 404 con JSON de error estructurado")
	void buscarInexistenteDevuelve404() throws Exception {
		mockMvc.perform(get("/api/v1/tutores/{id}", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.ruta").value("/api/v1/tutores/999999"))
				.andExpect(jsonPath("$.mensaje").exists());
	}

	@Test
	@DisplayName("POST invalido devuelve 400 con el detalle de cada campo")
	void crearInvalidoDevuelve400() throws Exception {
		Map<String, Object> body = Map.of(
				"nombre", "  ",
				"apellido", "",
				"dni", "abc");

		mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detalles").isArray())
				.andExpect(jsonPath("$.detalles", org.hamcrest.Matchers.hasItem(
						"El DNI debe contener entre 7 y 10 digitos")));
	}

	@Test
	@DisplayName("Ciclo completo: crear tutor, vincular mascota, desvincular y eliminar")
	void cicloCompletoDeVinculacion() throws Exception {
		// Arrange: una mascota sin tutores
		long mascotaId = crearMascota();

		// 201 al crear el tutor
		String tutorJson = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Gomez","dni":"20304050",
								 "telefono":"1133334444","email":"ana@correo.com"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombreCompleto").value("Ana Gomez"))
				.andExpect(jsonPath("$.mascotas").isEmpty())
				.andReturn().getResponse().getContentAsString();
		long tutorId = objectMapper.readTree(tutorJson).get("id").asLong();

		// 201 al vincular, con el flag por query param
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
						tutorId, mascotaId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tutores", org.hamcrest.Matchers.hasSize(1)))
				.andExpect(jsonPath("$.tutores[0].autorizadoRetiro").value(true));

		// El tutor ahora ve la mascota con su flag
		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mascotas", org.hamcrest.Matchers.hasSize(1)))
				.andExpect(jsonPath("$.mascotas[0].autorizadoRetiro").value(true));

		// El JSON del tutor no debe arrastrar la vuelta hacia la mascota
		String tutorConMascota = mockMvc.perform(get("/api/v1/tutores/{id}", tutorId))
				.andReturn().getResponse().getContentAsString();
		assertThat(tutorConMascota.split("\"tutores\"", -1)).hasSize(1);

		// 400 si se intenta vincular dos veces
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
						tutorId, mascotaId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(
						org.hamcrest.Matchers.containsString("ya esta vinculada")));

		// 400 si no se manda el flag en absoluto
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}", tutorId, 424242))
				.andExpect(status().isBadRequest());

		// 204 al desvincular
		mockMvc.perform(delete("/api/v1/tutores/{t}/mascotas/{m}", tutorId, mascotaId))
				.andExpect(status().isNoContent());

		// 404 al desvincular de nuevo
		mockMvc.perform(delete("/api/v1/tutores/{t}/mascotas/{m}", tutorId, mascotaId))
				.andExpect(status().isNotFound());

		// 204 al eliminar el tutor
		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("Vincular contra una mascota inexistente devuelve 404")
	void vincularMascotaInexistenteDevuelve404() throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Bruno","apellido":"Sosa","dni":"33445566"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long tutorId = objectMapper.readTree(json).get("id").asLong();

		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
						tutorId, 888888))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje").value(
						org.hamcrest.Matchers.containsString("No existe una mascota")));

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("PUT actualiza y rechaza DNI duplicado con 400")
	void actualizarYRechazarDniDuplicado() throws Exception {
		long mascotaId = crearMascota();

		long tutorA = crearTutor("11111111");
		long tutorB = crearTutor("22222222");

		// Vincula la misma mascota con ambos tutores (N:M real)
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorA, mascotaId))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false", tutorB, mascotaId))
				.andExpect(status().isCreated());

		// La mascota ve dos tutores con flags distintos
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tutores", org.hamcrest.Matchers.hasSize(2)));

		// PUT del tutor A es aceptado
		mockMvc.perform(put("/api/v1/tutores/{id}", tutorA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana Maria","apellido":"Gomez","dni":"11111111",
								 "vinculos":[{"mascotaId":%d,"autorizadoRetiro":false}]}
								""".formatted(mascotaId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana Maria"))
				.andExpect(jsonPath("$.mascotas[0].autorizadoRetiro").value(false));

		// Intentar tomar el DNI del tutor B: 400
		mockMvc.perform(put("/api/v1/tutores/{id}", tutorA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana Maria","apellido":"Gomez","dni":"22222222"}
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorA)).andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorB)).andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId)).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("CORS permite el origen http://localhost:3000")
	void corsPermiteLocalhost3000() throws Exception {
		mockMvc.perform(get("/api/v1/tutores")
						.header("Origin", "http://localhost:3000"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}

	// ------------------------------------------------------------- helpers

	private long crearMascota() throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Firulais","especie":"CANINO","raza":"Mestizo",
								 "sexo":"Macho","pesoKg":12.50,"conducta":"normal"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tutores").isEmpty())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Tutor","apellido":"Prueba","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}