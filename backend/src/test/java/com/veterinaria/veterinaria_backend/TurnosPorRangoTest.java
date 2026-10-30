package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
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
 * La vista mensual de la Agenda pide los turnos de un rango de dias en una sola
 * llamada, en vez de una por cada dia del mes.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class TurnosPorRangoTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("El rango incluye el primer y el ultimo dia y devuelve los turnos en orden")
	void rangoInclusivoYOrdenado() throws Exception {
		long mascotaId = crearMascota();
		long profesionalId = crearProfesional();
		crearTurno(mascotaId, profesionalId, "2031-02-28T09:00:00");
		crearTurno(mascotaId, profesionalId, "2031-03-31T18:00:00");
		crearTurno(mascotaId, profesionalId, "2031-03-01T09:00:00");
		crearTurno(mascotaId, profesionalId, "2031-03-15T10:00:00");
		crearTurno(mascotaId, profesionalId, "2031-04-01T09:00:00");

		mockMvc.perform(get("/api/v1/turnos").param("desde", "2031-03-01").param("hasta", "2031-03-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(3)))
				.andExpect(jsonPath("$[0].fechaHoraInicio").value("2031-03-01T09:00:00"))
				.andExpect(jsonPath("$[1].fechaHoraInicio").value("2031-03-15T10:00:00"))
				.andExpect(jsonPath("$[2].fechaHoraInicio").value("2031-03-31T18:00:00"));
	}

	@Test
	@DisplayName("El listado por fecha sigue devolviendo solo ese dia")
	void fechaSigueFuncionando() throws Exception {
		long mascotaId = crearMascota();
		long profesionalId = crearProfesional();
		crearTurno(mascotaId, profesionalId, "2031-05-10T09:00:00");
		crearTurno(mascotaId, profesionalId, "2031-05-11T09:00:00");

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-05-10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].fechaHoraInicio").value("2031-05-10T09:00:00"));
	}

	@Test
	@DisplayName("Hasta anterior a desde responde 400")
	void hastaAnteriorADesde() throws Exception {
		mockMvc.perform(get("/api/v1/turnos").param("desde", "2031-03-10").param("hasta", "2031-03-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La fecha hasta no puede ser anterior a desde"));
	}

	@Test
	@DisplayName("Un rango de mas de 62 dias responde 400")
	void rangoDemasiadoLargo() throws Exception {
		mockMvc.perform(get("/api/v1/turnos").param("desde", "2031-01-01").param("hasta", "2031-03-05"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El rango no puede superar los 62 dias"));
	}

	@Test
	@DisplayName("Desde sin hasta responde 400")
	void desdeSinHasta() throws Exception {
		mockMvc.perform(get("/api/v1/turnos").param("desde", "2031-03-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Hay que indicar desde y hasta juntos"));
	}

	// ------------------------------------------------------------- helpers

	private void crearTurno(long mascotaId, long profesionalId, String inicio) throws Exception {
		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fechaHoraInicio":"%s","motivo":"Control","mascotaId":%d,"profesionalId":%d}
								""".formatted(inicio, mascotaId, profesionalId)))
				.andExpect(status().isCreated());
	}

	private long crearMascota() throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Rango\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Sosa","matricula":"MAT-R%d"}
								""".formatted(System.nanoTime() % 100000)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
