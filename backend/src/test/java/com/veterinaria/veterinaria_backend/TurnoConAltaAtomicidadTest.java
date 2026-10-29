package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * El alta combinada (cliente + mascota + turno) es todo o nada.
 *
 * <p><b>Sin {@code @Transactional}</b>, por la misma razon que
 * {@link RollbackTransaccionalTest}: con la anotacion, el servicio se uniria a
 * la transaccion del test y el rollback por el 409 no se podria observar. La
 * limpieza es manual.</p>
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
class TurnoConAltaAtomicidadTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM egreso_mascota");
		jdbc.update("DELETE FROM pago");
		jdbc.update("DELETE FROM comanda_cobro");
		jdbc.update("DELETE FROM reserva_guarderia");
		jdbc.update("DELETE FROM consulta_medica");
		jdbc.update("DELETE FROM turno_clinico");
		jdbc.update("DELETE FROM tutor_mascota");
		jdbc.update("DELETE FROM mascota");
		jdbc.update("DELETE FROM tutor");
		jdbc.update("DELETE FROM profesional");
	}

	@Test
	@DisplayName("Si el turno choca con otro (409), no quedan ni el cliente ni la mascota nuevos")
	void solapamientoNoDejaAltasHuerfanas() throws Exception {
		long profesionalId = crearProfesional();
		long mascotaExistente = crearMascota();
		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fechaHoraInicio":"2031-07-01T10:00:00","motivo":"Control",
								 "mascotaId":%d,"profesionalId":%d}
								""".formatted(mascotaExistente, profesionalId)))
				.andExpect(status().isCreated());
		long tutoresAntes = contar("tutor");
		long mascotasAntes = contar("mascota");

		mockMvc.perform(post("/api/v1/turnos/con-alta")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"tutorNuevo":{"nombre":"Ana","apellido":"Paz","dni":"32000001"},
								 "mascotaNueva":{"nombre":"Rex","especie":"CANINO"},
								 "fechaHoraInicio":"2031-07-01T10:30:00","motivo":"Vacuna","profesionalId":%d}
								""".formatted(profesionalId)))
				.andExpect(status().isConflict());

		Assertions.assertEquals(tutoresAntes, contar("tutor"));
		Assertions.assertEquals(mascotasAntes, contar("mascota"));
		Assertions.assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM tutor WHERE dni = '32000001'", Long.class));
		Assertions.assertEquals(1L, contar("turno_clinico"));
	}

	private long contar(String tabla) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
	}

	private long crearMascota() throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Toby\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-ATOM\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
