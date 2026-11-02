package com.veterinaria.veterinaria_backend;

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
 * La seña de una reserva de guarderia se guarda como parte de pago: queda
 * registrada como un pago SENA en la comanda y se resta del saldo.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class SenaGuarderiaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long mascotaId;

	@BeforeEach
	void preparar() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/GUARDERIA").contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\": 10000}"))
				.andExpect(status().isOk());
		long tutorId = crear("/api/v1/tutores", "{\"nombre\":\"Ana\",\"apellido\":\"Sena\",\"dni\":\"%d\"}"
				.formatted(39000000 + System.nanoTime() % 900000));
		mascotaId = crear("/api/v1/mascotas", "{\"nombre\":\"Lola\",\"especie\":\"CANINO\"}");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("La seña queda como pago SENA y el saldo pendiente es total menos seña")
	void senaSeRestaDelTotal() throws Exception {
		long comandaId = comandaDe(reservar("3000").andExpect(status().isCreated())
				.andExpect(jsonPath("$.sena").value(3000.00)));

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(10000.00))
				.andExpect(jsonPath("$.saldoPendiente").value(7000.00))
				.andExpect(jsonPath("$.totalPagado").value(3000.00))
				.andExpect(jsonPath("$.estado").value("PARCIAL"))
				.andExpect(jsonPath("$.pagos", Matchers.hasSize(1)))
				.andExpect(jsonPath("$.pagos[0].metodoPago").value("SENA"))
				.andExpect(jsonPath("$.pagos[0].monto").value(3000.00));
	}

	@Test
	@DisplayName("Una seña igual al total deja la comanda pagada")
	void senaTotal() throws Exception {
		long comandaId = comandaDe(reservar("10000").andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.saldoPendiente").value(0.00))
				.andExpect(jsonPath("$.estado").value("PAGADA"));
	}

	@Test
	@DisplayName("Sin seña (o seña 0) no se registra ningun pago")
	void sinSena() throws Exception {
		long comandaId = comandaDe(reservar("0").andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.saldoPendiente").value(10000.00))
				.andExpect(jsonPath("$.pagos", Matchers.hasSize(0)));
	}

	@Test
	@DisplayName("Una seña mayor al total da 400 y no crea la reserva")
	void senaMayorAlTotal() throws Exception {
		reservar("15000")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(
						"La seña ($15000.00) no puede superar el total de la estadia ($10000.00)"));
		mockMvc.perform(get("/api/v1/guarderia").param("fecha", "2031-10-14"))
				.andExpect(jsonPath("$[?(@.mascotaId == " + mascotaId + ")]").isEmpty());
	}

	private ResultActions reservar(String sena) throws Exception {
		return mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"2031-10-14\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":%d,\"sena\":%s}"
						.formatted(mascotaId, sena)));
	}

	private long comandaDe(ResultActions resultado) throws Exception {
		return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get("comandaId").asLong();
	}

	private long crear(String ruta, String body) throws Exception {
		String json = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
