package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Turnos de urgencia: se registran sin datos a la hora actual, se pueden atender
 * sin cliente ni mascota y los datos se completan antes de cobrar
 * (S1-S2 de odd/tasks/urgencias.md).
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class TurnoUrgenciaTest {

	private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
	private static final LocalDateTime AHORA = LocalDateTime.of(2031, 9, 10, 13, 7, 42);

	@TestConfiguration
	static class Reloj {
		@Bean
		@Primary
		Clock relojFijo() {
			return Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long profesionalId;

	@BeforeEach
	void preparar() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", "CONSULTA_CLINICA")
						.contentType(MediaType.APPLICATION_JSON).content("{\"monto\":8000}"))
				.andExpect(status().isOk());
		profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-U%d\"}".formatted(System.nanoTime() % 100000));
	}

	@Test
	@DisplayName("Una urgencia se registra sin datos, a la hora actual y marcada como urgencia")
	void urgenciaSinDatos() throws Exception {
		mockMvc.perform(post("/api/v1/turnos/urgencia"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.urgencia").value(true))
				.andExpect(jsonPath("$.fechaHoraInicio").value("2031-09-10T13:07:00"))
				.andExpect(jsonPath("$.fechaFinBloque").value("2031-09-10T14:07:00"))
				.andExpect(jsonPath("$.motivo").value("Urgencia"))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.mascota").value(Matchers.nullValue()))
				.andExpect(jsonPath("$.profesional").value(Matchers.nullValue()));

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-09-10"))
				.andExpect(jsonPath("$[0].urgencia").value(true));
		// La jornada del dia (mostrador, egreso, caja) no se rompe con un turno sin mascota.
		mockMvc.perform(get("/api/v1/egreso/pacientes-hoy").param("fecha", "2031-09-10"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Un turno normal sigue exigiendo mascota y profesional")
	void turnoNormalSigueExigiendoDatos() throws Exception {
		mockMvc.perform(post("/api/v1/turnos").contentType(MediaType.APPLICATION_JSON)
						.content("{\"fechaHoraInicio\":\"2031-09-11T10:00:00\",\"motivo\":\"Control\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("El id de la mascota es obligatorio")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("El id del profesional es obligatorio")));
	}

	@Test
	@DisplayName("Se atiende sin datos: el turno toma al profesional aunque tenga otro turno, y la comanda queda sin titular")
	void atenderSinDatos() throws Exception {
		// El profesional ya tiene un turno a esa hora: la urgencia igual se atiende.
		long otraMascota = mascotaDe(tutor("40000001"), "Ocupante");
		crear("/api/v1/turnos", """
				{"fechaHoraInicio":"2031-09-10T13:00:00","motivo":"Control","mascotaId":%d,"profesionalId":%d}
				""".formatted(otraMascota, profesionalId));
		long turnoId = urgencia();

		long comandaId = comandaDe(atender(turnoId).andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascota").value(Matchers.nullValue())));

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-09-10"))
				.andExpect(jsonPath("$[?(@.id == " + turnoId + ")].estado").value("ATENDIDO"))
				.andExpect(jsonPath("$[?(@.id == " + turnoId + ")].profesional.id").value((int) profesionalId));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(8000.00))
				.andExpect(jsonPath("$.tutorId").value(Matchers.nullValue()))
				.andExpect(jsonPath("$.mascotaId").value(Matchers.nullValue()));
		// La caja del dia (la comanda se emite con la fecha real) la lista aunque no
		// tenga titular, y filtrar por tutor no se rompe.
		mockMvc.perform(get("/api/v1/comandas/dia"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + comandaId + ")]", Matchers.hasSize(1)));
		mockMvc.perform(get("/api/v1/comandas/dia").param("tutorId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + comandaId + ")]").isEmpty());
	}

	@Test
	@DisplayName("Sin datos no se puede cobrar: 409 y la comanda queda intacta")
	void noSeCobraSinDatos() throws Exception {
		long comandaId = comandaDe(atender(urgencia()).andExpect(status().isCreated()));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":8000}]}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(
						"Completá los datos del cliente y la mascota de la urgencia antes de cobrar"));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.saldoPendiente").value(8000.00))
				.andExpect(jsonPath("$.pagos", Matchers.hasSize(0)));
	}

	@Test
	@DisplayName("Completar datos con cliente y mascota nuevos asigna todo y habilita el cobro")
	void completarDatosYCobrar() throws Exception {
		long turnoId = urgencia();
		long comandaId = comandaDe(atender(turnoId).andExpect(status().isCreated()));

		mockMvc.perform(post("/api/v1/turnos/{id}/completar-datos", turnoId).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"tutorNuevo":{"nombre":"ana","apellido":"paz","dni":"40000002"},
								 "mascotaNueva":{"nombre":"rex","especie":"CANINO"}}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mascota.nombre").value("Rex"))
				.andExpect(jsonPath("$.urgencia").value(true));

		String comanda = mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.nombreTutor").value("Ana Paz"))
				.andExpect(jsonPath("$.nombreMascota").value("Rex"))
				.andReturn().getResponse().getContentAsString();
		long mascotaId = objectMapper.readTree(comanda).get("mascotaId").asLong();
		mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(jsonPath("$", Matchers.hasSize(1)));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":8000}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));
	}

	@Test
	@DisplayName("Completar datos antes de atender deja atender la urgencia como un turno normal")
	void completarAntesDeAtender() throws Exception {
		long tutorId = tutor("40000003");
		long mascotaId = mascotaDe(tutorId, "Luna");
		long turnoId = urgencia();

		mockMvc.perform(post("/api/v1/turnos/{id}/completar-datos", turnoId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"tutorId\":%d,\"mascotaId\":%d}".formatted(tutorId, mascotaId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mascota.id").value((int) mascotaId));

		long comandaId = comandaDe(mockMvc.perform(post("/api/v1/consultas").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"turnoClinicoId":%d,"mascotaId":%d,"profesionalId":%d,"anamnesis":"Golpe"}
								""".formatted(turnoId, mascotaId, profesionalId)))
				.andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.tutorId").value((int) tutorId));
	}

	@Test
	@DisplayName("Completar datos rechaza turnos que ya los tienen, mascotas ajenas y combinaciones invalidas")
	void completarDatosInvalido() throws Exception {
		long tutorId = tutor("40000004");
		long propia = mascotaDe(tutorId, "Propia");
		long ajena = mascotaDe(tutor("40000005"), "Ajena");
		long turnoNormal = crear("/api/v1/turnos", """
				{"fechaHoraInicio":"2031-09-12T10:00:00","motivo":"Control","mascotaId":%d,"profesionalId":%d}
				""".formatted(propia, profesionalId));
		long turnoId = urgencia();

		completar(turnoNormal, "{\"tutorId\":%d,\"mascotaId\":%d}".formatted(tutorId, propia))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El turno " + turnoNormal + " ya tiene cliente y mascota"));
		completar(turnoId, "{\"tutorId\":%d,\"mascotaId\":%d}".formatted(tutorId, ajena))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La mascota elegida no pertenece al cliente indicado"));
		completar(turnoId, "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Falta la mascota del turno"));

		completar(turnoId, "{\"tutorId\":%d,\"mascotaId\":%d}".formatted(tutorId, propia)).andExpect(status().isOk());
		completar(turnoId, "{\"tutorId\":%d,\"mascotaId\":%d}".formatted(tutorId, propia))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El turno " + turnoId + " ya tiene cliente y mascota"));
	}

	@Test
	@DisplayName("Atender sin mascota solo vale para urgencias")
	void fichaSinMascotaSoloEnUrgencias() throws Exception {
		mockMvc.perform(post("/api/v1/consultas").contentType(MediaType.APPLICATION_JSON)
						.content("{\"profesionalId\":%d,\"anamnesis\":\"Control\"}".formatted(profesionalId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El id de la mascota es obligatorio"));
	}

	// ------------------------------------------------------------- helpers

	private long urgencia() throws Exception {
		String json = mockMvc.perform(post("/api/v1/turnos/urgencia"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private ResultActions atender(long turnoId) throws Exception {
		return mockMvc.perform(post("/api/v1/consultas").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"turnoClinicoId":%d,"profesionalId":%d,"anamnesis":"Ingresa por urgencia"}
						""".formatted(turnoId, profesionalId)));
	}

	private ResultActions completar(long turnoId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/turnos/{id}/completar-datos", turnoId)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long comandaDe(ResultActions resultado) throws Exception {
		return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get("comandaId").asLong();
	}

	private long tutor(String dni) throws Exception {
		return crear("/api/v1/tutores", "{\"nombre\":\"Rita\",\"apellido\":\"Sosa\",\"dni\":\"%s\"}".formatted(dni));
	}

	private long mascotaDe(long tutorId, String nombre) throws Exception {
		long id = crear("/api/v1/mascotas", "{\"nombre\":\"%s\",\"especie\":\"CANINO\"}".formatted(nombre));
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, id))
				.andExpect(status().isCreated());
		return id;
	}

	private long crear(String ruta, String body) throws Exception {
		String json = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
