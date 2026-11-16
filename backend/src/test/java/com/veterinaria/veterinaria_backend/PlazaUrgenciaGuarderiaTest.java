package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
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

import tools.jackson.databind.ObjectMapper;

/**
 * Plaza extra de guarderia para urgencia clinica: la habilita solo el
 * Veterinario, por dia, es independiente de las 10 y el flujo de reservas nunca
 * la usa (S3 de odd/tasks/urgencias.md).
 */
@SpringBootTest
@WithMockUser(roles = "VETERINARIO")
@AutoConfigureMockMvc
@Transactional
class PlazaUrgenciaGuarderiaTest {

	private static final String DIA = "2031-10-20";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void tarifa() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/GUARDERIA").with(duenio())
						.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":10000}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Sin habilitar, la plaza no existe: el aforo sigue en 10 y no se puede ocupar")
	void sinHabilitar() throws Exception {
		mockMvc.perform(get("/api/v1/guarderia/urgencia").param("fecha", DIA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.habilitada").value(false))
				.andExpect(jsonPath("$.ocupada").value(false));
		ocupar(mascota("Sin plaza"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La plaza de urgencia no esta habilitada para el " + DIA));
		mockMvc.perform(get("/api/v1/guarderia/aforo").param("fecha", DIA))
				.andExpect(jsonPath("$.aforoMaximo").value(10))
				.andExpect(jsonPath("$.plazaUrgenciaHabilitada").value(false));
	}

	@Test
	@DisplayName("Con las 10 ocupadas, la plaza habilitada recibe una urgencia sin tocar el aforo normal")
	void plazaIndependienteDelAforo() throws Exception {
		for (int i = 0; i < 10; i++) {
			reservar(mascota("Normal " + i)).andExpect(status().isCreated());
		}
		mockMvc.perform(put("/api/v1/guarderia/urgencia/{fecha}", DIA)).andExpect(status().isOk())
				.andExpect(jsonPath("$.habilitada").value(true));

		long urgente = mascota("Urgente");
		ocupar(urgente)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascotaId").value((int) urgente))
				.andExpect(jsonPath("$.urgenciaClinica").value(true))
				.andExpect(jsonPath("$.fecha").value(DIA))
				.andExpect(jsonPath("$.comandaId").value(Matchers.nullValue()));

		mockMvc.perform(get("/api/v1/guarderia/aforo").param("fecha", DIA))
				.andExpect(jsonPath("$.cuposOcupados").value(10))
				.andExpect(jsonPath("$.aforoMaximo").value(10))
				.andExpect(jsonPath("$.completo").value(true))
				.andExpect(jsonPath("$.plazaUrgenciaHabilitada").value(true))
				.andExpect(jsonPath("$.plazaUrgenciaOcupada").value(true));
		mockMvc.perform(get("/api/v1/guarderia").param("fecha", DIA))
				.andExpect(jsonPath("$", Matchers.hasSize(11)));
	}

	@Test
	@DisplayName("El flujo de reservas nunca usa la plaza: con las 10 ocupadas, la reserva 11 sigue dando 409")
	void reservasNoUsanLaPlaza() throws Exception {
		mockMvc.perform(put("/api/v1/guarderia/urgencia/{fecha}", DIA)).andExpect(status().isOk());
		for (int i = 0; i < 10; i++) {
			reservar(mascota("Normal " + i)).andExpect(status().isCreated());
		}
		reservar(mascota("Once")).andExpect(status().isConflict());
		mockMvc.perform(get("/api/v1/guarderia/urgencia").param("fecha", DIA))
				.andExpect(jsonPath("$.ocupada").value(false));
	}

	@Test
	@DisplayName("La plaza es una sola: una segunda urgencia ese dia da 409")
	void unaSolaPlaza() throws Exception {
		mockMvc.perform(put("/api/v1/guarderia/urgencia/{fecha}", DIA)).andExpect(status().isOk());
		ocupar(mascota("Primera")).andExpect(status().isCreated());
		ocupar(mascota("Segunda"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("La plaza de urgencia del " + DIA + " ya esta ocupada"));
	}

	@Test
	@DisplayName("Deshabilitar exige que este libre; liberada (cancelada la estadia), se puede deshabilitar")
	void deshabilitar() throws Exception {
		mockMvc.perform(put("/api/v1/guarderia/urgencia/{fecha}", DIA)).andExpect(status().isOk());
		long reservaId = id(ocupar(mascota("Internada")).andExpect(status().isCreated()));

		mockMvc.perform(delete("/api/v1/guarderia/urgencia/{fecha}", DIA))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(
						"La plaza de urgencia del " + DIA + " esta ocupada: liberala antes de deshabilitarla"));

		mockMvc.perform(put("/api/v1/guarderia/{id}/cancelar", reservaId)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/guarderia/urgencia/{fecha}", DIA)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/guarderia/urgencia").param("fecha", DIA))
				.andExpect(jsonPath("$.habilitada").value(false));
	}

	@Test
	@DisplayName("Solo el Veterinario habilita, deshabilita u ocupa la plaza; el resto la puede consultar")
	void soloElVeterinario() throws Exception {
		for (String rol : new String[] { "DUENIO", "RECEPCION", "GUARDERIA" }) {
			mockMvc.perform(put("/api/v1/guarderia/urgencia/{fecha}", DIA).with(rol(rol)))
					.andExpect(status().isForbidden());
			mockMvc.perform(delete("/api/v1/guarderia/urgencia/{fecha}", DIA).with(rol(rol)))
					.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/v1/guarderia/urgencia/{fecha}/ocupar", DIA).with(rol(rol))
							.contentType(MediaType.APPLICATION_JSON).content("{\"mascotaId\":1}"))
					.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/guarderia/urgencia").param("fecha", DIA).with(rol(rol)))
					.andExpect(status().isOk());
		}
	}

	@Test
	@DisplayName("Una mascota que ya tiene estadia ese dia no ocupa ademas la plaza de urgencia")
	void mascotaYaEstaEnGuarderia() throws Exception {
		long m = mascota("Repetida");
		reservar(m).andExpect(status().isCreated());
		mockMvc.perform(put("/api/v1/guarderia/urgencia/{fecha}", DIA)).andExpect(status().isOk());
		ocupar(m).andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------- helpers

	private ResultActions reservar(long mascotaId) throws Exception {
		return mockMvc.perform(post("/api/v1/guarderia").with(duenio()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"%s\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":%d}".formatted(DIA, mascotaId)));
	}

	private ResultActions ocupar(long mascotaId) throws Exception {
		return mockMvc.perform(post("/api/v1/guarderia/urgencia/{fecha}/ocupar", DIA)
				.contentType(MediaType.APPLICATION_JSON).content("{\"mascotaId\":%d}".formatted(mascotaId)));
	}

	private long mascota(String nombre) throws Exception {
		return id(mockMvc.perform(post("/api/v1/mascotas").with(duenio()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"%s\",\"especie\":\"CANINO\"}".formatted(nombre)))
				.andExpect(status().isCreated()));
	}

	private long id(ResultActions resultado) throws Exception {
		return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asLong();
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor duenio() {
		return rol("DUENIO");
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor rol(String rol) {
		return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test")
				.roles(rol);
	}
}
