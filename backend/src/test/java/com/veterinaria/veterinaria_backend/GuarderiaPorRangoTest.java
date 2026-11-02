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

/** La agenda mensual de guarderia pide las estadias de un rango de dias en una sola llamada. */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class GuarderiaPorRangoTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("El rango trae las estadias que se pisan con el, incluidas las que empiezan antes")
	void estadiasQueSePisanConElRango() throws Exception {
		long a = mascota("Fuera");
		long b = mascota("Cruza");
		long c = mascota("Dentro");
		long d = mascota("Despues");
		reservar(a, "2031-11-20", null);
		reservar(b, "2031-11-28", "2031-12-02");
		reservar(c, "2031-12-10", null);
		reservar(d, "2032-01-05", null);

		mockMvc.perform(get("/api/v1/guarderia").param("desde", "2031-12-01").param("hasta", "2031-12-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[0].mascotaId").value(b))
				.andExpect(jsonPath("$[0].fechaSalida").value("2031-12-02"))
				.andExpect(jsonPath("$[1].mascotaId").value(c));
	}

	@Test
	@DisplayName("El listado por fecha sigue igual")
	void fechaSigueFuncionando() throws Exception {
		long m = mascota("Dia");
		reservar(m, "2031-12-15", null);
		mockMvc.perform(get("/api/v1/guarderia").param("fecha", "2031-12-15"))
				.andExpect(jsonPath("$[?(@.mascotaId == " + m + ")]", Matchers.hasSize(1)));
	}

	@Test
	@DisplayName("Rangos invalidos dan 400")
	void rangosInvalidos() throws Exception {
		mockMvc.perform(get("/api/v1/guarderia").param("desde", "2031-12-10").param("hasta", "2031-12-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La fecha hasta no puede ser anterior a desde"));
		mockMvc.perform(get("/api/v1/guarderia").param("desde", "2031-01-01").param("hasta", "2031-03-05"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El rango no puede superar los 62 dias"));
		mockMvc.perform(get("/api/v1/guarderia").param("desde", "2031-12-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Hay que indicar desde y hasta juntos"));
	}

	private void reservar(long mascotaId, String fecha, String fechaSalida) throws Exception {
		String rango = fechaSalida == null
				? "\"tipoEstadia\":\"COMPLETA_24H\""
				: "\"fechaSalida\":\"%s\",\"horaEntrada\":\"10:00\",\"horaSalida\":\"10:00\"".formatted(fechaSalida);
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
						.content("{\"fecha\":\"%s\",\"mascotaId\":%d,%s}".formatted(fecha, mascotaId, rango)))
				.andExpect(status().isCreated());
	}

	private long mascota(String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"%s\",\"especie\":\"CANINO\"}".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
