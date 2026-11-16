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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Una mascota con un turno clinico de hoy sin atender no se retira: antes, como
 * la comanda nace al atender, un turno pendiente no dejaba deuda y el egreso
 * pasaba sin que nadie la hubiera visto.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class EgresoTurnoSinAtenderTest {

	private static final LocalDate HOY = LocalDate.now();
	private static final String MENSAJE = "Toby tiene un turno clínico de hoy a las 10:00 sin atender: "
			+ "atendelo o cancelalo antes del retiro.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long tutorId;
	private long mascotaId;
	private long profesionalId;

	@BeforeEach
	void datos() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/CONSULTA_CLINICA").contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\":8000}")).andExpect(status().isOk());
		tutorId = crear("/api/v1/tutores",
				"{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"dni\":\"%d\"}".formatted(20000000 + System.nanoTime() % 9000000));
		mascotaId = crear("/api/v1/mascotas",
				"{\"nombre\":\"Toby\",\"especie\":\"CANINO\",\"tutores\":[{\"tutorId\":%d,\"autorizadoRetiro\":true}]}"
						.formatted(tutorId));
		profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-E%d\"}".formatted(System.nanoTime() % 100000));
	}

	@Test
	@DisplayName("Turno de hoy pendiente: el retiro se bloquea con motivo y mensaje propios")
	void turnoPendienteBloquea() throws Exception {
		turno(HOY, "10:00");

		validar(tutorId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("TURNO_SIN_ATENDER"))
				.andExpect(jsonPath("$.etiquetaMotivo").value("Turno clínico sin atender"))
				.andExpect(jsonPath("$.turnoSinAtender").value(true))
				.andExpect(jsonPath("$.mensaje").value(MENSAJE));
	}

	@Test
	@DisplayName("Registrar la entrega con el turno pendiente da 409 y no deja egreso guardado")
	void registrarConTurnoPendiente() throws Exception {
		turno(HOY, "10:00");

		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON)
						.content("{\"mascotaId\":%d,\"tutorId\":%d}".formatted(mascotaId, tutorId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(MENSAJE));
		mockMvc.perform(get("/api/v1/egreso/pacientes-hoy"))
				.andExpect(jsonPath("$[?(@.mascotaId == " + mascotaId + ")].fechaHoraEntrega").value(
						org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
	}

	@Test
	@DisplayName("Atendido el turno, ya no bloquea por turno: queda la deuda de la consulta")
	void turnoAtendido() throws Exception {
		long turnoId = turno(HOY, "10:00");
		mockMvc.perform(post("/api/v1/consultas").contentType(MediaType.APPLICATION_JSON).content("""
				{"turnoClinicoId":%d,"mascotaId":%d,"profesionalId":%d,"fechaAtencion":"%sT10:00:00","anamnesis":"Control"}
				""".formatted(turnoId, mascotaId, profesionalId, HOY))).andExpect(status().isCreated());

		validar(tutorId)
				.andExpect(jsonPath("$.turnoSinAtender").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("DEUDA_PENDIENTE"));
	}

	@Test
	@DisplayName("Un turno cancelado no bloquea el retiro")
	void turnoCancelado() throws Exception {
		long turnoId = turno(HOY, "10:00");
		mockMvc.perform(put("/api/v1/turnos/{id}/cancelar", turnoId)).andExpect(status().isOk());

		validar(tutorId)
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.motivoBloqueo").value("NINGUNO"));
	}

	@Test
	@DisplayName("Un turno pendiente de otro dia no bloquea el retiro de hoy")
	void turnoDeOtroDia() throws Exception {
		turno(HOY.plusDays(1), "10:00");

		validar(tutorId)
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.turnoSinAtender").value(false));
	}

	@Test
	@DisplayName("Quien no esta autorizado sigue teniendo precedencia sobre el turno sin atender")
	void precedenciaDelTutor() throws Exception {
		turno(HOY, "10:00");
		long otro = crear("/api/v1/tutores",
				"{\"nombre\":\"Juan\",\"apellido\":\"Ruiz\",\"dni\":\"%d\"}".formatted(30000000 + System.nanoTime() % 9000000));

		validar(otro)
				.andExpect(jsonPath("$.motivoBloqueo").value("TUTOR_NO_AUTORIZADO"))
				.andExpect(jsonPath("$.turnoSinAtender").value(true));
	}

	// ------------------------------------------------------------- helpers

	private ResultActions validar(long tutor) throws Exception {
		return mockMvc.perform(get("/api/v1/egreso/validar/tutor")
				.param("mascotaId", String.valueOf(mascotaId)).param("tutorId", String.valueOf(tutor)));
	}

	private long turno(LocalDate dia, String hora) throws Exception {
		return crear("/api/v1/turnos", """
				{"fechaHoraInicio":"%sT%s:00","motivo":"Control","profesionalId":%d,"mascotaId":%d}
				""".formatted(dia, hora, profesionalId, mascotaId));
	}

	private long crear(String url, String body) throws Exception {
		return objectMapper.readTree(mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
	}
}
