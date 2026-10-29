package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.service.TarifaGuarderiaService;

/**
 * Tarifas de guarderia (S1, S2 de {@code odd/tasks/guarderia-tarifas-rango.md}).
 *
 * <p>La estadia se cobra por horas si entra y sale el mismo dia (la fraccion de
 * hora se cobra entera) o por noches si pasa al menos una. Los dos precios los
 * ajusta el Duenio.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "DUENIO")
class TarifaGuarderiaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TarifaGuarderiaService tarifaService;

	/** Por el servicio y no por la API: en el caso de Recepcion el PUT daria 403. */
	@BeforeEach
	void tarifas() {
		tarifaService.actualizar(new TarifaGuarderiaRequestDTO(new BigDecimal("2000"), new BigDecimal("12000")));
	}

	@Test
	@DisplayName("S2: GET devuelve los precios vigentes")
	void consultarTarifas() throws Exception {
		mockMvc.perform(get("/api/v1/guarderia/tarifas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precioHora").value(2000.00))
				.andExpect(jsonPath("$.precioNoche").value(12000.00));
	}

	@Test
	@DisplayName("S1: mismo dia se cobra por horas, con la fraccion como hora entera")
	void mismoDiaPorHoras() throws Exception {
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-02T09:00:00").param("hasta", "2031-06-02T13:30:00"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modalidad").value("HORAS"))
				.andExpect(jsonPath("$.cantidad").value(5))
				.andExpect(jsonPath("$.precioUnitario").value(2000.00))
				.andExpect(jsonPath("$.total").value(10000.00));
	}

	@Test
	@DisplayName("S1: si pasa al menos una noche se cobra por noches (lunes 9:00 a miercoles 10:00 = 2)")
	void variasNochesPorNoche() throws Exception {
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-02T09:00:00").param("hasta", "2031-06-04T10:00:00"))
				.andExpect(jsonPath("$.modalidad").value("NOCHES"))
				.andExpect(jsonPath("$.cantidad").value(2))
				.andExpect(jsonPath("$.precioUnitario").value(12000.00))
				.andExpect(jsonPath("$.total").value(24000.00));
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-02T22:00:00").param("hasta", "2031-06-03T08:00:00"))
				.andExpect(jsonPath("$.modalidad").value("NOCHES"))
				.andExpect(jsonPath("$.cantidad").value(1))
				.andExpect(jsonPath("$.total").value(12000.00));
	}

	@Test
	@DisplayName("Una salida igual o anterior a la entrada da 400")
	void rangoInvalido() throws Exception {
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-02T10:00:00").param("hasta", "2031-06-02T10:00:00"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-03T10:00:00").param("hasta", "2031-06-02T10:00:00"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("S2: el Duenio ajusta los precios y la cotizacion usa los nuevos")
	void duenioAjustaPrecios() throws Exception {
		mockMvc.perform(put("/api/v1/guarderia/tarifas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"precioHora\": 2500.5, \"precioNoche\": 15000}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/guarderia/tarifas"))
				.andExpect(jsonPath("$.precioHora").value(2500.50))
				.andExpect(jsonPath("$.precioNoche").value(15000.00));
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-02T09:00:00").param("hasta", "2031-06-02T11:00:00"))
				.andExpect(jsonPath("$.total").value(5001.00));
	}

	@Test
	@DisplayName("S2: precios negativos o faltantes dan 400")
	void preciosInvalidos() throws Exception {
		mockMvc.perform(put("/api/v1/guarderia/tarifas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"precioHora\": -1, \"precioNoche\": 15000}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/guarderia/tarifas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"precioNoche\": 15000}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "RECEPCION")
	@DisplayName("S2: solo el Duenio cambia las tarifas; el resto puede consultarlas y cotizar")
	void soloDuenioCambia() throws Exception {
		mockMvc.perform(put("/api/v1/guarderia/tarifas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"precioHora\": 1, \"precioNoche\": 1}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/guarderia/tarifas")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/guarderia/cotizar")
				.param("desde", "2031-06-02T09:00:00").param("hasta", "2031-06-02T10:00:00"))
				.andExpect(status().isOk());
	}
}
