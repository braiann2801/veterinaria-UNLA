package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
 * Cerrar una atencion: la ficha marca el turno como ATENDIDO y la comanda suma
 * la consulta mas los extras usados, con el detalle de cada uno.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class AtencionConExtrasTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long mascotaId;
	private long profesionalId;

	@BeforeEach
	void preparar() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", "CONSULTA_CLINICA")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":8000}"))
				.andExpect(status().isOk());
		long tutorId = crear("/api/v1/tutores", "{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"dni\":\"%d\"}"
				.formatted(37000000 + System.nanoTime() % 900000));
		mascotaId = crear("/api/v1/mascotas", "{\"nombre\":\"Rex\",\"especie\":\"CANINO\"}");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
		profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-X%d\"}".formatted(System.nanoTime() % 100000));
	}

	@Test
	@DisplayName("La ficha con extras emite una comanda con la consulta mas los extras y su detalle")
	void comandaConExtras() throws Exception {
		long vacuna = crear("/api/v1/extras", "{\"nombre\":\"Vacuna antirrabica\",\"precio\":12500}");
		long jeringa = crear("/api/v1/extras", "{\"nombre\":\"Jeringa\",\"precio\":300}");

		long comandaId = comandaDe(ficha(null, """
				[{"extraId":%d,"cantidad":1},{"extraId":%d,"cantidad":2}]""".formatted(vacuna, jeringa))
				.andExpect(status().isCreated()));

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.concepto").value("CONSULTA_CLINICA"))
				// 8000 de consulta + 12500 + 2 x 300
				.andExpect(jsonPath("$.montoTotal").value(21100.00))
				.andExpect(jsonPath("$.saldoPendiente").value(21100.00))
				.andExpect(jsonPath("$.items", Matchers.hasSize(3)))
				.andExpect(jsonPath("$.items[0].descripcion").value("Consulta clínica"))
				.andExpect(jsonPath("$.items[0].subtotal").value(8000.00))
				.andExpect(jsonPath("$.items[1].descripcion").value("Vacuna antirrabica"))
				.andExpect(jsonPath("$.items[1].cantidad").value(1))
				.andExpect(jsonPath("$.items[1].precioUnitario").value(12500.00))
				.andExpect(jsonPath("$.items[2].descripcion").value("Jeringa"))
				.andExpect(jsonPath("$.items[2].cantidad").value(2))
				.andExpect(jsonPath("$.items[2].subtotal").value(600.00));
	}

	@Test
	@DisplayName("Cambiar el precio de un extra despues no cambia la comanda ya emitida")
	void precioCongelado() throws Exception {
		long vacuna = crear("/api/v1/extras", "{\"nombre\":\"Vacuna sextuple\",\"precio\":10000}");
		long comandaId = comandaDe(ficha(null, "[{\"extraId\":%d,\"cantidad\":1}]".formatted(vacuna))
				.andExpect(status().isCreated()));

		mockMvc.perform(put("/api/v1/extras/{id}", vacuna)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Vacuna sextuple\",\"precio\":99999}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(18000.00))
				.andExpect(jsonPath("$.items[1].precioUnitario").value(10000.00));
	}

	@Test
	@DisplayName("Sin extras la comanda es solo la consulta, como antes")
	void sinExtras() throws Exception {
		long comandaId = comandaDe(ficha(null, null).andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(8000.00))
				.andExpect(jsonPath("$.items", Matchers.hasSize(1)));
	}

	@Test
	@DisplayName("La ficha asociada a un turno lo marca como ATENDIDO")
	void turnoAtendido() throws Exception {
		long turnoId = turno("2031-08-01T10:00:00");
		ficha(turnoId, null).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-08-01"))
				.andExpect(jsonPath("$[?(@.id == " + turnoId + ")].estado").value("ATENDIDO"));
	}

	@Test
	@DisplayName("Un turno cancelado no se puede atender y no se emite nada")
	void turnoCancelado() throws Exception {
		long turnoId = turno("2031-08-02T10:00:00");
		mockMvc.perform(put("/api/v1/turnos/{id}/cancelar", turnoId)).andExpect(status().isOk());

		ficha(turnoId, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El turno " + turnoId + " esta cancelado: no se puede atender"));
		mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	@Test
	@DisplayName("Un turno de otra mascota da 400")
	void turnoDeOtraMascota() throws Exception {
		long otraMascota = crear("/api/v1/mascotas", "{\"nombre\":\"Otra\",\"especie\":\"CANINO\"}");
		long turnoId = turnoDe(otraMascota, "2031-08-03T10:00:00");

		ficha(turnoId, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El turno " + turnoId + " es de otra mascota"));
	}

	@Test
	@DisplayName("Extras inexistentes, inactivos o con cantidad invalida dan error y no emiten nada")
	void extrasInvalidos() throws Exception {
		long inactivo = crear("/api/v1/extras", "{\"nombre\":\"Discontinuado\",\"precio\":100}");
		mockMvc.perform(put("/api/v1/extras/{id}", inactivo)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Discontinuado\",\"precio\":100,\"activo\":false}"))
				.andExpect(status().isOk());
		long activo = crear("/api/v1/extras", "{\"nombre\":\"Gasa\",\"precio\":50}");

		ficha(null, "[{\"extraId\":99999999,\"cantidad\":1}]").andExpect(status().isNotFound());
		ficha(null, "[{\"extraId\":%d,\"cantidad\":1}]".formatted(inactivo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El extra Discontinuado esta desactivado"));
		ficha(null, "[{\"extraId\":%d,\"cantidad\":0}]".formatted(activo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("La cantidad debe ser al menos 1")));
		ficha(null, "[{\"extraId\":%d,\"cantidad\":100}]".formatted(activo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("La cantidad no puede superar 99")));

		mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	// ------------------------------------------------------------- helpers

	private ResultActions ficha(Long turnoId, String extras) throws Exception {
		return mockMvc.perform(post("/api/v1/consultas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"turnoClinicoId":%s,"mascotaId":%d,"profesionalId":%d,
						 "anamnesis":"Control","extras":%s}
						""".formatted(turnoId == null ? "null" : turnoId, mascotaId, profesionalId,
						extras == null ? "null" : extras)));
	}

	private long comandaDe(ResultActions resultado) throws Exception {
		long id = objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString())
				.get("comandaId").asLong();
		Assertions.assertTrue(id > 0);
		return id;
	}

	private long turno(String inicio) throws Exception {
		return turnoDe(mascotaId, inicio);
	}

	private long turnoDe(long mascota, String inicio) throws Exception {
		return crear("/api/v1/turnos", """
				{"fechaHoraInicio":"%s","motivo":"Control","mascotaId":%d,"profesionalId":%d}
				""".formatted(inicio, mascota, profesionalId));
	}

	private long crear(String ruta, String body) throws Exception {
		String json = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
