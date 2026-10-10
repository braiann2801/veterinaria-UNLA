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
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * ABM del padron de tutores y mascotas.
 *
 * <p>Cubre la regla de negocio inviolable: una mascota no puede quedar sin
 * ningun tutor. Al eliminar un tutor, las mascotas que solo estaban vinculadas a
 * el se eliminan tambien; si esa mascota huerfana tiene historial clinico o
 * contable, no se puede borrar y el borrado del tutor se aborta completo
 * (transaccion atomica), sin dejar el padron a medias.</p>
 *
 * <p>Los borrados con historial se rechazan con 409 en lugar de dejar que la
 * clave foranea reviente en la base con un 500 opaco.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PadronAbmTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Borrar un tutor deja viva a la mascota que tiene otro tutor")
	void borrarTutorConMascotaCompartidaMantieneLaMascota() throws Exception {
		long tutorA = crearTutor("51000001");
		long tutorB = crearTutor("51000002");
		long mascotaId = crearMascota("Compartida");

		vincular(tutorA, mascotaId, true);
		vincular(tutorB, mascotaId, false);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorA))
				.andExpect(status().isNoContent());

		// La mascota sigue viva porque le queda el tutor B.
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tutores", Matchers.hasSize(1)))
				.andExpect(jsonPath("$.tutores[0].id").value((int) tutorB));
	}

	@Test
	@DisplayName("Borrar al unico tutor de una mascota sin historial la elimina tambien")
	void borrarUnicoTutorEliminaLaMascotaHuerfana() throws Exception {
		long tutorId = crearTutor("51000003");
		long mascotaId = crearMascota("Huerfana");
		vincular(tutorId, mascotaId, true);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Una mascota huerfana con historial bloquea el borrado del tutor con 409")
	void mascotaHuerfanaConHistorialBloqueaElBorradoDelTutor() throws Exception {
		long tutorId = crearTutor("51000004");
		long mascotaId = crearMascota("ConHistorial");
		vincular(tutorId, mascotaId, true);
		crearTurno(mascotaId);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("quedaria sin tutor")));

		// Atomicidad: ni el tutor ni la mascota se borraron.
		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Una mascota sin historial se elimina con 204")
	void mascotaSinHistorialSeElimina() throws Exception {
		long mascotaId = crearMascota("SinHistorial");

		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Una mascota con turno no se elimina (409)")
	void mascotaConHistorialNoSeElimina() throws Exception {
		long mascotaId = crearMascota("ConTurno");
		crearTurno(mascotaId);

		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("historial clinico o contable")));

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Un tutor con comandas no se elimina (409)")
	void tutorConComandaNoSeElimina() throws Exception {
		long tutorId = crearTutor("51000005");
		emitirComanda(tutorId);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("movimientos contables")));

		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId)).andExpect(status().isOk());
	}

	// ------------------------------------------------------------- helpers

	private long crearMascota(String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO"}
								""".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Tutor","apellido":"Abm","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void vincular(long tutorId, long mascotaId, boolean autorizado) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro={a}",
						tutorId, mascotaId, autorizado))
				.andExpect(status().isCreated());
	}

	private void crearTurno(long mascotaId) throws Exception {
		long profesionalId = crearProfesional();
		mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fechaHoraInicio":"2026-12-01T09:00:00","motivo":"Control",
								 "mascotaId":%d,"profesionalId":%d}
								""".formatted(mascotaId, profesionalId)))
				.andExpect(status().isCreated());
	}

	private long crearProfesional() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Vera","apellido":"Abm","matricula":"MAT-%d"}
								""".formatted(System.nanoTime() % 100000)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void emitirComanda(long tutorId) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", "OTRO")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":5000}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"OTRO","tutorId":%d,"descripcion":"Consumo"}
								""".formatted(tutorId)))
				.andExpect(status().isCreated());
	}
}
