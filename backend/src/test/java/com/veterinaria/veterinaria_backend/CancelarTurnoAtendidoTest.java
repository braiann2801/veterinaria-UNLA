package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
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
 * Un turno atendido no se cancela: ya tiene ficha clinica y comanda, y
 * cancelarlo dejaria las dos colgadas de un turno que figura como no ocurrido.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class CancelarTurnoAtendidoTest {

	private static final String DIA = "2031-09-15";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long mascotaId;
	private long profesionalId;

	@BeforeEach
	void datos() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/CONSULTA_CLINICA").contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\":8000}")).andExpect(status().isOk());
		long tutorId = crear("/api/v1/tutores",
				"{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"dni\":\"%d\"}".formatted(20000000 + System.nanoTime() % 9000000));
		mascotaId = crear("/api/v1/mascotas",
				"{\"nombre\":\"Toby\",\"especie\":\"CANINO\",\"tutores\":[{\"tutorId\":%d,\"autorizadoRetiro\":true}]}"
						.formatted(tutorId));
		profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-C%d\"}".formatted(System.nanoTime() % 100000));
	}

	@Test
	@DisplayName("Cancelar un turno atendido da 409 y el turno sigue atendido")
	void atendidoNoSeCancela() throws Exception {
		long turnoId = turno();
		mockMvc.perform(post("/api/v1/consultas").contentType(MediaType.APPLICATION_JSON).content("""
				{"turnoClinicoId":%d,"mascotaId":%d,"profesionalId":%d,"fechaAtencion":"%sT10:00:00","anamnesis":"Control"}
				""".formatted(turnoId, mascotaId, profesionalId, DIA))).andExpect(status().isCreated());

		mockMvc.perform(put("/api/v1/turnos/{id}/cancelar", turnoId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(
						"El turno " + turnoId + " ya fue atendido: tiene ficha clínica y comanda, no se puede cancelar"));

		mockMvc.perform(get("/api/v1/turnos").param("fecha", DIA))
				.andExpect(jsonPath("$[?(@.id == " + turnoId + ")].estado").value("ATENDIDO"));
	}

	@Test
	@DisplayName("Un turno pendiente se sigue cancelando como siempre")
	void pendienteSeCancela() throws Exception {
		long turnoId = turno();

		mockMvc.perform(put("/api/v1/turnos/{id}/cancelar", turnoId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));
	}

	private long turno() throws Exception {
		return crear("/api/v1/turnos", """
				{"fechaHoraInicio":"%sT10:00:00","motivo":"Control","profesionalId":%d,"mascotaId":%d}
				""".formatted(LocalDate.parse(DIA), profesionalId, mascotaId));
	}

	private long crear(String url, String body) throws Exception {
		return objectMapper.readTree(mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
	}
}
