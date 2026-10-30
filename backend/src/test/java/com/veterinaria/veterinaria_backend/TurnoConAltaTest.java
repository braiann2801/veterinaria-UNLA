package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Agendar un turno cargando en el mismo paso un cliente nuevo y su mascota, o
 * una mascota nueva de un cliente existente (POST /api/v1/turnos/con-alta).
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class TurnoConAltaTest {

	private static final String TURNO = """
			"fechaHoraInicio":"2031-06-10T10:00:00","motivo":"Vacuna","profesionalId":%d""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Cliente nuevo y mascota nueva: se crean los dos, vinculados, y el turno queda agendado")
	void clienteYMascotaNuevos() throws Exception {
		long profesionalId = crearProfesional();

		alta("""
				{"tutorNuevo":{"nombre":"ana","apellido":"paz","dni":"31.000.001","telefono":"11 2345-6789"},
				 "mascotaNueva":{"nombre":"firulais","especie":"CANINO","sexo":"Macho"},
				 %s}
				""".formatted(TURNO.formatted(profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascota.nombre").value("Firulais"))
				.andExpect(jsonPath("$.motivo").value("Vacuna"))
				.andExpect(jsonPath("$.fechaHoraInicio").value("2031-06-10T10:00:00"))
				.andExpect(jsonPath("$.profesional.id").value(profesionalId));

		JsonNode tutor = tutorPorDni("31000001");
		Assertions.assertEquals("Ana", tutor.get("nombre").asText());
		Assertions.assertEquals("1123456789", tutor.get("telefono").asText());
		Assertions.assertEquals(1, tutor.get("mascotas").size());
		Assertions.assertEquals("Firulais", tutor.get("mascotas").get(0).get("nombre").asText());
		Assertions.assertTrue(tutor.get("mascotas").get(0).get("autorizadoRetiro").asBoolean());

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-06-10"))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].mascota.nombre").value("Firulais"));
	}

	@Test
	@DisplayName("Cliente existente y mascota nueva: la mascota se suma a sus mascotas")
	void mascotaNuevaDeClienteExistente() throws Exception {
		long profesionalId = crearProfesional();
		long tutorId = crearTutor("31000002");

		alta("""
				{"tutorId":%d,"mascotaNueva":{"nombre":"luna","especie":"FELINO"},%s}
				""".formatted(tutorId, TURNO.formatted(profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascota.nombre").value("Luna"));

		JsonNode tutor = tutorPorDni("31000002");
		Assertions.assertEquals(1, tutor.get("mascotas").size());
		Assertions.assertEquals("Luna", tutor.get("mascotas").get(0).get("nombre").asText());
	}

	@Test
	@DisplayName("Mascota existente: funciona como el alta de turno de siempre")
	void mascotaExistente() throws Exception {
		long profesionalId = crearProfesional();
		long mascotaId = crearMascota("Toby");

		alta("""
				{"mascotaId":%d,%s}
				""".formatted(mascotaId, TURNO.formatted(profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascota.id").value(mascotaId));
	}

	@Test
	@DisplayName("Datos invalidos del cliente nuevo dan 400 y no crean nada")
	void clienteInvalido() throws Exception {
		long profesionalId = crearProfesional();
		int tutores = cantidad("/api/v1/tutores");
		int mascotas = cantidad("/api/v1/mascotas");

		alta("""
				{"tutorNuevo":{"nombre":"Ana","apellido":"Paz","dni":"123"},
				 "mascotaNueva":{"nombre":"Rex","especie":"CANINO"},%s}
				""".formatted(TURNO.formatted(profesionalId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("El DNI debe tener 7 u 8 digitos (ej.: 30.123.456)")));

		Assertions.assertEquals(tutores, cantidad("/api/v1/tutores"));
		Assertions.assertEquals(mascotas, cantidad("/api/v1/mascotas"));
		Assertions.assertEquals(0, cantidadDeTurnos("2031-06-10"));
	}

	@Test
	@DisplayName("Combinaciones invalidas de cliente y mascota dan 400 con el motivo")
	void combinacionesInvalidas() throws Exception {
		long profesionalId = crearProfesional();
		long tutorId = crearTutor("31000003");
		long mascotaId = crearMascota("Kira");
		String turno = TURNO.formatted(profesionalId);

		esperar400("""
				{"mascotaNueva":{"nombre":"Rex","especie":"CANINO"},%s}
				""".formatted(turno),
				"La mascota nueva necesita un cliente: elegí uno existente o cargá uno nuevo");
		esperar400("""
				{"tutorNuevo":{"nombre":"Ana","apellido":"Paz","dni":"31000004"},"mascotaId":%d,%s}
				""".formatted(mascotaId, turno),
				"Un cliente nuevo se carga con una mascota nueva");
		esperar400("""
				{"tutorId":%d,"mascotaId":%d,"mascotaNueva":{"nombre":"Rex","especie":"CANINO"},%s}
				""".formatted(tutorId, mascotaId, turno),
				"Indicá una mascota existente o una nueva, no las dos");
		esperar400("""
				{"tutorId":%d,"tutorNuevo":{"nombre":"Ana","apellido":"Paz","dni":"31000005"},
				 "mascotaNueva":{"nombre":"Rex","especie":"CANINO"},%s}
				""".formatted(tutorId, turno),
				"Indicá un cliente existente o uno nuevo, no los dos");
		esperar400("""
				{%s}
				""".formatted(turno),
				"Falta la mascota del turno");

		Assertions.assertEquals(0, cantidadDeTurnos("2031-06-10"));
		Assertions.assertTrue(tutorPorDniOpcional("31000004") == null);
	}

	@Test
	@DisplayName("Cliente existente con una mascota que no es suya da 400 y no agenda nada")
	void mascotaAjenaAlCliente() throws Exception {
		long profesionalId = crearProfesional();
		long tutorId = crearTutor("31000006");
		long mascotaAjena = crearMascota("Ajena");

		esperar400("""
				{"tutorId":%d,"mascotaId":%d,%s}
				""".formatted(tutorId, mascotaAjena, TURNO.formatted(profesionalId)),
				"La mascota elegida no pertenece al cliente indicado");
		Assertions.assertEquals(0, cantidadDeTurnos("2031-06-10"));
	}

	@Test
	@DisplayName("Cliente existente con su propia mascota agenda el turno")
	void mascotaPropiaDelCliente() throws Exception {
		long profesionalId = crearProfesional();
		long tutorId = crearTutor("31000007");
		long mascotaId = crearMascota("Propia");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());

		alta("""
				{"tutorId":%d,"mascotaId":%d,%s}
				""".formatted(tutorId, mascotaId, TURNO.formatted(profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascota.id").value(mascotaId));
	}

	@Test
	@DisplayName("Un cliente existente que no existe da 404")
	void clienteInexistente() throws Exception {
		long profesionalId = crearProfesional();
		alta("""
				{"tutorId":99999999,"mascotaNueva":{"nombre":"Rex","especie":"CANINO"},%s}
				""".formatted(TURNO.formatted(profesionalId)))
				.andExpect(status().isNotFound());
		Assertions.assertEquals(0, cantidadDeTurnos("2031-06-10"));
	}

	// ------------------------------------------------------------- helpers

	private ResultActions alta(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/turnos/con-alta")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private void esperar400(String body, String mensaje) throws Exception {
		alta(body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(mensaje));
	}

	private int cantidad(String ruta) throws Exception {
		String json = mockMvc.perform(get(ruta)).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).size();
	}

	private int cantidadDeTurnos(String fecha) throws Exception {
		String json = mockMvc.perform(get("/api/v1/turnos").param("fecha", fecha))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).size();
	}

	private JsonNode tutorPorDni(String dni) throws Exception {
		JsonNode tutor = tutorPorDniOpcional(dni);
		Assertions.assertNotNull(tutor, "No se encontro el tutor " + dni);
		return tutor;
	}

	private JsonNode tutorPorDniOpcional(String dni) throws Exception {
		String json = mockMvc.perform(get("/api/v1/tutores")).andReturn().getResponse().getContentAsString();
		for (JsonNode tutor : objectMapper.readTree(json)) {
			if (dni.equals(tutor.get("dni").asText())) {
				return tutor;
			}
		}
		return null;
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Rita\",\"apellido\":\"Sosa\",\"dni\":\"%s\"}".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascota(String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"%s\",\"especie\":\"CANINO\"}".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Sosa","matricula":"MAT-A%d"}
								""".formatted(System.nanoTime() % 100000)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
