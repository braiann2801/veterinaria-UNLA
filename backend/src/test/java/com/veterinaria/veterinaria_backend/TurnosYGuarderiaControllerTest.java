package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import tools.jackson.databind.ObjectMapper;

/**
 * TASK-004: verifica que las reglas de negocio llegan al cliente con el codigo
 * HTTP correcto y un mensaje descriptivo.
 *
 * <p>Cubre el contrato completo: aforo -> 409, solapamiento -> 409, validacion
 * de body -> 400, recurso inexistente -> 404.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TurnosYGuarderiaControllerTest {

	private static final LocalDate DIA = LocalDate.of(2026, 12, 1);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Crear 10 reservas llena el aforo; la 11 devuelve 409 con detalle del cupo")
	void aforoLlenoDevuelve409() throws Exception {
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(post("/api/v1/guarderia")
							.contentType(MediaType.APPLICATION_JSON)
							.content(reservaJson(crearMascota("Perro" + i), DIA.toString())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.aforoMaximo").value(10));
		}

		// El aforo ya esta completo
		mockMvc.perform(get("/api/v1/guarderia/aforo").param("fecha", DIA.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cuposOcupados").value(10))
				.andExpect(jsonPath("$.cuposDisponibles").value(0))
				.andExpect(jsonPath("$.completo").value(true));

		// La 11 se rebota con 409
		mockMvc.perform(post("/api/v1/guarderia")
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservaJson(crearMascota("Sobrante"), DIA.toString())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("aforo completo")))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("10 de 10")));
	}

	@Test
	@DisplayName("Turno en ventana de desinfeccion devuelve 409 con las horas en conflicto")
	void solapamientoDevuelve409() throws Exception {
		long profesionalId = crearProfesional();
		long mascotaId = crearMascota();

		// Turno valido a las 09:00 (bloquea hasta las 10:00 por desinfeccion)
		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content(turnoJson("2026-12-01T09:00:00", mascotaId, profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fechaHoraFin").value("2026-12-01T09:45:00"))
				.andExpect(jsonPath("$.fechaFinBloque").value("2026-12-01T10:00:00"));

		// Turno a las 09:45 cae en desinfeccion
		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content(turnoJson("2026-12-01T09:45:00", mascotaId, profesionalId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("desinfeccion")))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("se solapa")));

		// A las 10:00 la desinfeccion termino: entra
		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content(turnoJson("2026-12-01T10:00:00", mascotaId, profesionalId)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("El endpoint de disponibilidad refleja el mismo criterio")
	void disponibilidadPorHttp() throws Exception {
		long profesionalId = crearProfesional();
		long mascotaId = crearMascota();

		mockMvc.perform(get("/api/v1/turnos/disponibilidad")
						.param("profesionalId", String.valueOf(profesionalId))
						.param("inicio", "2026-12-01T09:00:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(true));

		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content(turnoJson("2026-12-01T09:00:00", mascotaId, profesionalId)))
				.andExpect(status().isCreated());

		// El minuto 45 sigue bloqueado por desinfeccion
		mockMvc.perform(get("/api/v1/turnos/disponibilidad")
						.param("profesionalId", String.valueOf(profesionalId))
						.param("inicio", "2026-12-01T09:45:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(false));

		mockMvc.perform(get("/api/v1/turnos/disponibilidad")
						.param("profesionalId", String.valueOf(profesionalId))
						.param("inicio", "2026-12-01T10:00:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(true));
	}

	@Test
	@DisplayName("Body invalido devuelve 400 con el detalle por campo")
	void bodyInvalidoDevuelve400() throws Exception {
		Map<String, Object> body = Map.of("fecha", "2026-12-01", "tipoEstadia", "DIURNA");

		mockMvc.perform(post("/api/v1/guarderia")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles").isArray())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem(
						"El id de la mascota es obligatorio")));
	}

	@Test
	@DisplayName("Reservar una mascota inexistente devuelve 404")
	void mascotaInexistenteDevuelve404() throws Exception {
		mockMvc.perform(post("/api/v1/guarderia")
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservaJson(999999, DIA.toString())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("No existe una mascota")));
	}

	@Test
	@DisplayName("Cancelar una reserva libera el cupo y devuelve el aforo actualizado")
	void cancelarLiberaCupoPorHttp() throws Exception {
		long mascotaId = crearMascota();

		mockMvc.perform(post("/api/v1/guarderia")
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservaJson(mascotaId, DIA.toString())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.cuposOcupados").value(1));

		String listado = mockMvc.perform(get("/api/v1/guarderia").param("fecha", DIA.toString()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long reservaId = objectMapper.readTree(listado).get(0).get("id").asLong();

		mockMvc.perform(put("/api/v1/guarderia/{id}/cancelar", reservaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"))
				.andExpect(jsonPath("$.cuposOcupados").value(0));
	}

	// ------------------------------------------------------------- helpers

	private long crearMascota(String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO"}
								""".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascota() throws Exception {
		return crearMascota("Paciente" + Math.abs(System.nanoTime() % 100000));
	}

	private long crearProfesional() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Sosa","matricula":"MAT-%d"}
								""".formatted(System.nanoTime() % 100000)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private String reservaJson(long mascotaId, String fecha) {
		return """
				{"fecha":"%s","tipoEstadia":"COMPLETA_24H","mascotaId":%d}
				""".formatted(fecha, mascotaId);
	}

	private String turnoJson(String inicio, long mascotaId, long profesionalId) {
		return """
				{"fechaHoraInicio":"%s","motivo":"Consulta de control","mascotaId":%d,"profesionalId":%d}
				""".formatted(inicio, mascotaId, profesionalId);
	}
}