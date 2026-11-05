package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El extracto del Balance trae la fecha del servicio cobrado, para marcar como
 * "deuda anterior" lo que se presto otro dia aunque la comanda se haya emitido
 * hoy (una estadia o una atencion cargadas despues de ocurridas).
 *
 * <p>La fecha del servicio es el ultimo dia de la estadia o el dia de la
 * atencion. Una comanda manual no tiene servicio: viaja {@code null} y la vista
 * sigue usando la emision.</p>
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class BalanceDeudaAnteriorTest {

	private static final LocalDate HOY = LocalDate.now();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long tutorId;
	private long mascotaId;

	@BeforeEach
	void datos() throws Exception {
		ponerTarifa("GUARDERIA", 10000);
		ponerTarifa("CONSULTA_CLINICA", 8000);
		tutorId = crear("/api/v1/tutores",
				"{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"dni\":\"%d\"}".formatted(20000000 + System.nanoTime() % 9000000));
		mascotaId = crear("/api/v1/mascotas",
				"{\"nombre\":\"Toby\",\"especie\":\"CANINO\",\"tutores\":[{\"tutorId\":%d,\"autorizadoRetiro\":true}]}"
						.formatted(tutorId));
	}

	@Test
	@DisplayName("Estadia de un dia anterior cobrada hoy: el movimiento trae la fecha de la estadia")
	void estadiaDeOtroDia() throws Exception {
		LocalDate dia = HOY.minusDays(4);
		long comanda = comandaDe(crearJson("/api/v1/guarderia",
				"{\"fecha\":\"%s\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":%d}".formatted(dia, mascotaId)));

		JsonNode movimiento = cobrarYLeerMovimiento(comanda);

		assertThat(movimiento.get("fechaServicio").asString()).isEqualTo(dia.toString());
		// La emision no cambia: sigue siendo el momento en que se cargo.
		assertThat(movimiento.get("fechaEmision").asString()).startsWith(HOY.toString());
	}

	@Test
	@DisplayName("Estadia de varios dias que termina hoy: la fecha del servicio es su ultimo dia")
	void estadiaQueTerminaHoy() throws Exception {
		long comanda = comandaDe(crearJson("/api/v1/guarderia", """
				{"fecha":"%s","fechaSalida":"%s","horaEntrada":"08:00","horaSalida":"08:00","mascotaId":%d}
				""".formatted(HOY.minusDays(3), HOY, mascotaId)));

		JsonNode movimiento = cobrarYLeerMovimiento(comanda);

		assertThat(movimiento.get("fechaServicio").asString()).isEqualTo(HOY.toString());
	}

	@Test
	@DisplayName("Atencion de un dia anterior cobrada hoy: el movimiento trae la fecha de la atencion")
	void atencionDeOtroDia() throws Exception {
		long profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-B%d\"}".formatted(System.nanoTime() % 100000));
		LocalDate dia = HOY.minusDays(2);
		JsonNode ficha = crearJson("/api/v1/consultas", """
				{"mascotaId":%d,"profesionalId":%d,"fechaAtencion":"%sT10:00:00","anamnesis":"Control"}
				""".formatted(mascotaId, profesionalId, dia));

		JsonNode movimiento = cobrarYLeerMovimiento(ficha.get("comandaId").asLong());

		assertThat(movimiento.get("fechaServicio").asString()).isEqualTo(dia.toString());
	}

	@Test
	@DisplayName("Comanda manual sin servicio: la fecha del servicio viaja nula")
	void comandaSinServicio() throws Exception {
		long comanda = crear("/api/v1/comandas", "{\"concepto\":\"GUARDERIA\",\"tutorId\":%d}".formatted(tutorId));

		JsonNode movimiento = cobrarYLeerMovimiento(comanda);

		assertThat(movimiento.get("fechaServicio").isNull()).isTrue();
	}

	// ------------------------------------------------------------- helpers

	private JsonNode cobrarYLeerMovimiento(long comandaId) throws Exception {
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":1000}]}"))
				.andExpect(status().isOk());
		JsonNode cierre = objectMapper.readTree(mockMvc.perform(get("/api/v1/caja/cierre").param("fecha", HOY.toString()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(cierre.get("movimientos")).hasSize(1);
		return cierre.get("movimientos").get(0);
	}

	private long comandaDe(JsonNode reserva) {
		return reserva.get("comandaId").asLong();
	}

	private long crear(String url, String body) throws Exception {
		return crearJson(url, body).get("id").asLong();
	}

	private JsonNode crearJson(String url, String body) throws Exception {
		return objectMapper.readTree(mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
	}
}
