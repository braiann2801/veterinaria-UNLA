package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hamcrest.Matchers;
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

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * Reserva de guarderia por rango (S1, S4, S5 de {@code odd/tasks/guarderia-tarifas-rango.md}).
 *
 * <p>Una estadia de varias noches ocupa cupo en cada dia del rango, y su comanda
 * se emite con el monto calculado por horas o por noches.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "DUENIO")
class GuarderiaRangoTest {

	/** Lunes. */
	private static final LocalDate LUNES = LocalDate.of(2031, 6, 2);
	private static final LocalDate MARTES = LUNES.plusDays(1);
	private static final LocalDate MIERCOLES = LUNES.plusDays(2);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TarifaGuarderiaService tarifaService;

	private long tutor;

	@BeforeEach
	void preparar() throws Exception {
		tarifaService.actualizar(new TarifaGuarderiaRequestDTO(new BigDecimal("2000"), new BigDecimal("12000")));
		tutor = id(mockMvc.perform(post("/api/v1/tutores").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Rango\",\"apellido\":\"Tutor\",\"dni\":\"44000001\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long id(String json) throws Exception {
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long mascota(String nombre, boolean conTutor) throws Exception {
		long id = id(mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		if (conTutor) {
			mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}", tutor, id).param("autorizadoRetiro", "true"))
					.andExpect(status().isCreated());
		}
		return id;
	}

	private String rango(long mascotaId, LocalDate desde, String horaEntrada, LocalDate hasta, String horaSalida) {
		return "{\"fecha\":\"" + desde + "\",\"fechaSalida\":\"" + hasta + "\",\"mascotaId\":" + mascotaId
				+ ",\"horaEntrada\":\"" + horaEntrada + "\",\"horaSalida\":\"" + horaSalida + "\"}";
	}

	private String reservar(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("S1/S4: lunes 9:00 a miercoles 10:00 son 2 noches y la comanda sale con ese monto")
	void rangoDeVariasNoches() throws Exception {
		String reserva = reservar(rango(mascota("Rocky", true), LUNES, "09:00", MIERCOLES, "10:00"));
		org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(reserva).get("fechaSalida").asString())
				.isEqualTo(MIERCOLES.toString());
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.concepto").value("GUARDERIA"))
				.andExpect(jsonPath("$.montoTotal").value(24000.00));
	}

	@Test
	@DisplayName("S1/S4: mismo dia 9:00 a 13:30 son 5 horas")
	void mismoDiaPorHoras() throws Exception {
		String reserva = reservar(rango(mascota("Corto", true), LUNES, "09:00", LUNES, "13:30"));
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(10000.00));
	}

	@Test
	@DisplayName("S5: la estadia ocupa cupo cada dia del rango y aparece en la agenda y la jornada de esos dias")
	void ocupaCadaDia() throws Exception {
		long rocky = mascota("Rocky", true);
		reservar(rango(rocky, LUNES, "09:00", MIERCOLES, "10:00"));

		for (LocalDate dia : new LocalDate[] { LUNES, MARTES, MIERCOLES }) {
			mockMvc.perform(get("/api/v1/guarderia/aforo").param("fecha", dia.toString()))
					.andExpect(jsonPath("$.cuposOcupados").value(1));
			mockMvc.perform(get("/api/v1/guarderia").param("fecha", dia.toString()))
					.andExpect(jsonPath("$[*].mascotaId", Matchers.hasItem((int) rocky)));
			mockMvc.perform(get("/api/v1/egreso/pacientes-hoy").param("fecha", dia.toString()))
					.andExpect(jsonPath("$[*].mascotaId", Matchers.hasItem((int) rocky)));
		}
		mockMvc.perform(get("/api/v1/guarderia/aforo").param("fecha", LUNES.plusDays(3).toString()))
				.andExpect(jsonPath("$.cuposOcupados").value(0));
	}

	@Test
	@DisplayName("S5: si algun dia del rango esta lleno, la reserva se rechaza con 409")
	void aforoLlenoEnUnDiaDelRango() throws Exception {
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
					.content("{\"fecha\":\"" + MARTES + "\",\"tipoEstadia\":\"DIURNA\",\"mascotaId\":" + mascota("Lleno" + i, false) + "}"))
					.andExpect(status().isCreated());
		}
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content(rango(mascota("Rocky", true), LUNES, "09:00", MIERCOLES, "10:00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString(MARTES.toString())));
		mockMvc.perform(get("/api/v1/guarderia/aforo").param("fecha", LUNES.toString()))
				.andExpect(jsonPath("$.cuposOcupados").value(0));
	}

	@Test
	@DisplayName("La misma mascota no puede tener dos estadias que se pisen")
	void estadiasSolapadas() throws Exception {
		long rocky = mascota("Rocky", true);
		reservar(rango(rocky, LUNES, "09:00", MIERCOLES, "10:00"));
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"" + MARTES + "\",\"tipoEstadia\":\"DIURNA\",\"mascotaId\":" + rocky + "}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rango invalido: salida no posterior a la entrada, sin horas o de mas de 60 dias da 400")
	void rangoInvalido() throws Exception {
		long rocky = mascota("Rocky", true);
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content(rango(rocky, MARTES, "09:00", LUNES, "10:00")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content(rango(rocky, LUNES, "10:00", LUNES, "10:00")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"" + LUNES + "\",\"fechaSalida\":\"" + MIERCOLES + "\",\"mascotaId\":" + rocky + "}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content(rango(rocky, LUNES, "09:00", LUNES.plusDays(61), "10:00")))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("La entrega del ultimo dia finaliza la estadia de varias noches")
	void entregaFinalizaElRango() throws Exception {
		long rocky = mascota("Rocky", true);
		String reserva = reservar(rango(rocky, LUNES, "09:00", MIERCOLES, "10:00"));
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":24000}],\"tutorId\":" + tutor + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mascotaId\":" + rocky + ",\"tutorId\":" + tutor + ",\"fecha\":\"" + MIERCOLES + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservaGuarderiaId").value(id(reserva)));
	}
}
