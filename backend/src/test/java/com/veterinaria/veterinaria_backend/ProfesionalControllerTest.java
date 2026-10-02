package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-007 (FASE 1): cobertura directa del padron de profesionales.
 *
 * <p>Antes de esta clase, {@code POST /api/v1/profesionales} solo se ejercitaba
 * como precondicio de los tests de turnos, y {@code GET} no se probaba en
 * absoluto. Un endpoint que nadie asserta puede devolver cualquier cosa sin que
 * la barrera se entere.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfesionalControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProfesionalRepository profesionalRepository;

	@Test
	@DisplayName("GET lista devuelve 200 con array")
	void listarDevuelve200() throws Exception {
		mockMvc.perform(get("/api/v1/profesionales"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	@DisplayName("POST crea el profesional con los seis campos y los persiste en MySQL")
	void crearPersisteLosSeisCampos() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Elena","apellido":"Vidal","dni":"31222333",
								 "matricula":"MAT-7788","telefono":"1145566677"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Elena"))
				.andExpect(jsonPath("$.apellido").value("Vidal"))
				.andExpect(jsonPath("$.nombreCompleto").value("Elena Vidal"))
				.andExpect(jsonPath("$.dni").value("31222333"))
				.andExpect(jsonPath("$.matricula").value("MAT-7788"))
				.andExpect(jsonPath("$.telefono").value("1145566677"))
				.andExpect(jsonPath("$.activo").value(true))
				.andReturn().getResponse().getContentAsString();

		long id = objectMapper.readTree(json).get("id").asLong();

		// La fila existe de verdad: se relee por la entidad, no por la respuesta.
		var guardado = profesionalRepository.findById(id).orElseThrow();
		assertThat(guardado.getNombre()).isEqualTo("Elena");
		assertThat(guardado.getApellido()).isEqualTo("Vidal");
		assertThat(guardado.getDni()).isEqualTo("31222333");
		assertThat(guardado.getMatricula()).isEqualTo("MAT-7788");
		assertThat(guardado.getTelefono()).isEqualTo("1145566677");
		assertThat(guardado.isActivo()).isTrue();

		// Y reaparece en el listado.
		mockMvc.perform(get("/api/v1/profesionales"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].dni", Matchers.hasItem("31222333")))
				.andExpect(jsonPath("$[*].telefono", Matchers.hasItem("1145566677")));
	}

	@Test
	@DisplayName("El DNI se normaliza a mayusculas y recortado")
	void dniSeNormaliza() throws Exception {
		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Raul","apellido":"Ibarra","dni":"  30999888  ",
								 "matricula":"  mat-99  "}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.dni").value("30999888"))
				.andExpect(jsonPath("$.matricula").value("MAT-99"));
	}

	@Test
	@DisplayName("DNI repetido devuelve 400 y no crea la fila")
	void dniDuplicadoDevuelve400() throws Exception {
		crear("30445566", "MAT-1");

		long antes = profesionalRepository.count();

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Otro","apellido":"Profesor","dni":"30445566","matricula":"MAT-2"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("El DNI")))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("ya esta registrada")));

		assertThat(profesionalRepository.count()).isEqualTo(antes);
	}

	@Test
	@DisplayName("DNI repetido con distinta grafia tambien se rechaza")
	void dniDuplicadoIgnoraGrafia() throws Exception {
		crear("30445577", "MAT-3");

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Otro","apellido":"Profesor","dni":" 30445577 ","matricula":"MAT-4"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Matricula repetida devuelve 400 y no crea la fila")
	void matriculaDuplicadaDevuelve400() throws Exception {
		crear("30556688", "MAT-DUP");

		long antes = profesionalRepository.count();

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Otro","apellido":"Profesor","dni":"30556699","matricula":"mat-dup"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("La matricula")));

		assertThat(profesionalRepository.count()).isEqualTo(antes);
	}

	@Test
	@DisplayName("Varios profesionales sin DNI pueden coexistir (MySQL admite NULL en UNIQUE)")
	void dniNuloNoChoca() throws Exception {
		crear("30667788", "MAT-5");
		crear(null, "MAT-6");
		crear(null, "MAT-7");

		// Los tres se guardaron: el DNI nullable no choca consigo mismo.
		assertThat(profesionalRepository.findByMatricula("MAT-5")).isPresent();
		assertThat(profesionalRepository.findByMatricula("MAT-6")).isPresent();
		assertThat(profesionalRepository.findByMatricula("MAT-7")).isPresent();

		// Y el finder por DNI sigue resolviendolo a los que lo tienen.
		assertThat(profesionalRepository.findByDni("30667788")).hasSize(1);
	}

	@Test
	@DisplayName("Sin nombre o sin apellido devuelve 400 con el detalle por campo")
	void faltanDatosDevuelve400() throws Exception {
		Map<String, Object> body = Map.of("nombre", "  ", "apellido", "", "dni", "30778899");

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detalles").isArray())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El nombre del profesional es obligatorio")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El apellido del profesional es obligatorio")));
	}

	@Test
	@DisplayName("Sin nombre, sin apellido, sin dni y sin matricula: solo nombre y apellido bastan")
	void sinDniNiMatriculaDevuelve201() throws Exception {
		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Auxiliar","apellido":"Turno"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.dni").doesNotExist())
				.andExpect(jsonPath("$.matricula").doesNotExist());
	}

	@Test
	@DisplayName("Campos de mas de largo devuelven 400")
	void camposDemasiadoLargosDevuelven400() throws Exception {
		String largo = "X".repeat(120);

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":%1$s,"apellido":"Vidal","dni":%1$s,"matricula":%1$s,"telefono":%1$s}
								""".formatted(objectMapper.writeValueAsString(largo))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El nombre no puede superar los 80 caracteres")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El DNI no puede superar los 20 caracteres")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"La matricula no puede superar los 40 caracteres")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El telefono no puede superar los 30 caracteres")));
	}

	// ------------------------------------------------------------- helpers

	private void crear(String dni, String matricula) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("nombre", "Prueba");
		body.put("apellido", "Basica");
		if (dni != null) {
			body.put("dni", dni);
		}
		body.put("matricula", matricula);

		mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated());
	}
}
