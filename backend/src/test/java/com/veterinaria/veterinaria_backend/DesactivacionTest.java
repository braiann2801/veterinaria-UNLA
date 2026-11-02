package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Los registros no se borran: tutores, mascotas y profesionales se desactivan
 * y conservan su historial. Al desactivar un tutor, se desactivan las mascotas
 * que no tengan otro tutor activo.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class DesactivacionTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Desactivar un tutor desactiva la mascota que no tiene otro tutor activo y conserva el historial")
	void tutorDesactivaMascotaHuerfana() throws Exception {
		long tutorId = tutor("38000001");
		long mascotaId = mascotaDe(tutorId, "Kira");
		long comandaId = comanda(tutorId, mascotaId);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/tutores"))
				.andExpect(jsonPath("$[?(@.id == " + tutorId + ")].activo").value(false));
	}

	@Test
	@DisplayName("La mascota con otro tutor activo sigue activa")
	void mascotaConOtroTutorSigueActiva() throws Exception {
		long titular = tutor("38000002");
		long otro = tutor("38000003");
		long mascotaId = mascotaDe(titular, "Luna");
		vincular(otro, mascotaId);

		mockMvc.perform(delete("/api/v1/tutores/{id}", titular)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	@DisplayName("Una mascota con historial se desactiva en lugar de dar 409")
	void mascotaConHistorialSeDesactiva() throws Exception {
		long tutorId = tutor("38000004");
		long mascotaId = mascotaDe(tutorId, "Toby");
		comanda(tutorId, mascotaId);

		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(jsonPath("$.cantidadComandas").value(1));
	}

	@Test
	@DisplayName("Un profesional con turnos se desactiva en lugar de dar 409")
	void profesionalConTurnosSeDesactiva() throws Exception {
		long profesionalId = profesional();
		long mascotaId = mascotaDe(tutor("38000005"), "Rex");
		turno(mascotaId, profesionalId, "2031-09-01T10:00:00").andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/profesionales/{id}", profesionalId)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/profesionales"))
				.andExpect(jsonPath("$[?(@.id == " + profesionalId + ")].activo").value(false));
		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-09-01"))
				.andExpect(jsonPath("$", Matchers.hasSize(1)));
	}

	@Test
	@DisplayName("Reactivar: la mascota necesita un tutor activo antes de volver")
	void reactivar() throws Exception {
		long tutorId = tutor("38000006");
		long mascotaId = mascotaDe(tutorId, "Mora");
		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId)).andExpect(status().isNoContent());

		mockMvc.perform(put("/api/v1/mascotas/{id}/reactivar", mascotaId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(
						"La mascota Mora no tiene ningun tutor activo: reactivá primero a un tutor"));

		mockMvc.perform(put("/api/v1/tutores/{id}/reactivar", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
		// Reactivar al tutor no reactiva sola a la mascota: se decide de a uno.
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId)).andExpect(jsonPath("$.activo").value(false));

		mockMvc.perform(put("/api/v1/mascotas/{id}/reactivar", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	@DisplayName("Reactivar un profesional lo vuelve a ofrecer")
	void reactivarProfesional() throws Exception {
		long profesionalId = profesional();
		mockMvc.perform(delete("/api/v1/profesionales/{id}", profesionalId)).andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/profesionales/{id}/reactivar", profesionalId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	@DisplayName("Mascotas y profesionales inactivos no toman turnos ni reservas nuevas")
	void inactivosNoAgendan() throws Exception {
		long profesionalId = profesional();
		long tutorId = tutor("38000007");
		long mascotaId = mascotaDe(tutorId, "Simba");
		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId)).andExpect(status().isNoContent());

		turno(mascotaId, profesionalId, "2031-09-02T10:00:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La mascota Simba esta desactivada"));
		mockMvc.perform(post("/api/v1/guarderia")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fecha\":\"2031-09-02\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":%d}"
								.formatted(mascotaId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La mascota Simba esta desactivada"));

		long activa = mascotaDe(tutorId, "Nala");
		mockMvc.perform(delete("/api/v1/profesionales/{id}", profesionalId)).andExpect(status().isNoContent());
		turno(activa, profesionalId, "2031-09-03T10:00:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El profesional Laura Sosa esta desactivado"));
	}

	@Test
	@DisplayName("Un tutor inactivo no recibe mascotas nuevas desde el alta de turno")
	void tutorInactivoNoRecibeMascotas() throws Exception {
		long profesionalId = profesional();
		long tutorId = tutor("38000008");
		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId)).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/turnos/con-alta")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"tutorId":%d,"mascotaNueva":{"nombre":"Rex","especie":"CANINO"},
								 "fechaHoraInicio":"2031-09-04T10:00:00","motivo":"Control","profesionalId":%d}
								""".formatted(tutorId, profesionalId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El cliente Rita Sosa esta desactivado"));
	}

	// ------------------------------------------------------------- helpers

	private org.springframework.test.web.servlet.ResultActions turno(long mascotaId, long profesionalId, String inicio)
			throws Exception {
		return mockMvc.perform(post("/api/v1/turnos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fechaHoraInicio":"%s","motivo":"Control","mascotaId":%d,"profesionalId":%d}
						""".formatted(inicio, mascotaId, profesionalId)));
	}

	private long comanda(long tutorId, long mascotaId) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{c}", "OTRO")
						.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":1000}"))
				.andExpect(status().isOk());
		return crear("/api/v1/comandas",
				"{\"concepto\":\"OTRO\",\"tutorId\":%d,\"mascotaId\":%d,\"descripcion\":\"x\"}".formatted(tutorId, mascotaId));
	}

	private long tutor(String dni) throws Exception {
		return crear("/api/v1/tutores", "{\"nombre\":\"Rita\",\"apellido\":\"Sosa\",\"dni\":\"%s\"}".formatted(dni));
	}

	private long mascotaDe(long tutorId, String nombre) throws Exception {
		long id = crear("/api/v1/mascotas", "{\"nombre\":\"%s\",\"especie\":\"CANINO\"}".formatted(nombre));
		vincular(tutorId, id);
		return id;
	}

	private void vincular(long tutorId, long mascotaId) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
	}

	private long profesional() throws Exception {
		return crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-D%d\"}".formatted(System.nanoTime() % 100000));
	}

	private long crear(String ruta, String body) throws Exception {
		String json = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
