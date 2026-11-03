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
import org.springframework.security.test.context.support.WithMockUser;
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
// La API exige login: estos tests prueban reglas de negocio, no permisos
// (ver AutenticacionTest). Duenio tiene acceso a todos los endpoints.
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class PadronAbmTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// Los registros no se borran: DELETE desactiva y conserva vinculos e historial
	// (S8 de odd/tasks/correcciones-flujo-registros.md).

	@Test
	@DisplayName("Dar de baja a un tutor deja activa a la mascota que tiene otro tutor activo")
	void borrarTutorConMascotaCompartidaMantieneLaMascota() throws Exception {
		long tutorA = crearTutor("51000001");
		long tutorB = crearTutor("51000002");
		long mascotaId = crearMascota("Compartida");

		vincular(tutorA, mascotaId, true);
		vincular(tutorB, mascotaId, false);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorA))
				.andExpect(status().isNoContent());

		// El vinculo con el tutor dado de baja queda como registro historico.
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(jsonPath("$.tutores", Matchers.hasSize(2)));
		mockMvc.perform(get("/api/v1/tutores/{id}", tutorA))
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	@DisplayName("Dar de baja al unico tutor desactiva tambien a la mascota, sin borrarla")
	void borrarUnicoTutorEliminaLaMascotaHuerfana() throws Exception {
		long tutorId = crearTutor("51000003");
		long mascotaId = crearMascota("Huerfana");
		vincular(tutorId, mascotaId, true);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	@DisplayName("Una mascota huerfana con historial ya no bloquea la baja del tutor: se desactivan los dos")
	void mascotaHuerfanaConHistorialBloqueaElBorradoDelTutor() throws Exception {
		long tutorId = crearTutor("51000004");
		long mascotaId = crearMascota("ConHistorial");
		vincular(tutorId, mascotaId, true);
		crearTurno(mascotaId);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId)).andExpect(jsonPath("$.activo").value(false));
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId)).andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	@DisplayName("Una mascota sin historial se desactiva con 204 y sigue consultable")
	void mascotaSinHistorialSeElimina() throws Exception {
		long mascotaId = crearMascota("SinHistorial");

		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	@DisplayName("Una mascota con turno se desactiva y el turno queda registrado")
	void mascotaConHistorialNoSeElimina() throws Exception {
		long mascotaId = crearMascota("ConTurno");
		crearTurno(mascotaId);

		mockMvc.perform(delete("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	@DisplayName("Un tutor con comandas se desactiva y su deuda sigue registrada")
	void tutorConComandaNoSeElimina() throws Exception {
		long tutorId = crearTutor("51000005");
		emitirComanda(tutorId);

		mockMvc.perform(delete("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(jsonPath("$.tieneDeuda").value(true));
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
