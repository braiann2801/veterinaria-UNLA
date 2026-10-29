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
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * Edicion y baja de profesionales (los botones del panel Profesionales del Mostrador).
 *
 * <p>La edicion aplica las mismas reglas que el alta (normalizacion y unicidad de
 * DNI y matricula). Un profesional con turnos o consultas no se borra: la FK lo
 * impediria con un 500 opaco, asi que se rechaza con 409 y el motivo.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// La API exige login: este test prueba reglas de negocio, no permisos.
@WithMockUser(roles = "DUENIO")
class ProfesionalAbmTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long crear(String nombre, String matricula, String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\",\"apellido\":\"Abm\",\"matricula\":\"" + matricula
						+ "\",\"dni\":\"" + dni + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	@Test
	@DisplayName("Editar actualiza los datos con la misma normalizacion que el alta")
	void editar() throws Exception {
		long id = crear("Elena", "MP-ABM-1", "45000001");
		mockMvc.perform(put("/api/v1/profesionales/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\" Elena María \",\"apellido\":\"Vidal\",\"matricula\":\" mp-abm-9 \",\"dni\":\"45000009\",\"telefono\":\" 1122334455 \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.nombre").value("Elena María"))
				.andExpect(jsonPath("$.apellido").value("Vidal"))
				.andExpect(jsonPath("$.matricula").value("MP-ABM-9"))
				.andExpect(jsonPath("$.dni").value("45000009"))
				.andExpect(jsonPath("$.telefono").value("1122334455"));
		mockMvc.perform(get("/api/v1/profesionales"))
				.andExpect(jsonPath("$[?(@.id == " + id + ")].matricula").value("MP-ABM-9"));
	}

	@Test
	@DisplayName("Editar conservando su propia matricula es valido; usar la de otro da 400")
	void editarUnicidad() throws Exception {
		long a = crear("Ana", "MP-ABM-2", "45000002");
		crear("Beto", "MP-ABM-3", "45000003");
		mockMvc.perform(put("/api/v1/profesionales/{id}", a).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellido\":\"Cambiada\",\"matricula\":\"MP-ABM-2\",\"dni\":\"45000002\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/profesionales/{id}", a).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellido\":\"Abm\",\"matricula\":\"MP-ABM-3\",\"dni\":\"45000002\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("MP-ABM-3")));
		mockMvc.perform(put("/api/v1/profesionales/{id}", a).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"apellido\":\"Abm\",\"dni\":\"45000003\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Editar con datos invalidos da 400 y un id inexistente da 404")
	void editarErrores() throws Exception {
		long id = crear("Carla", "MP-ABM-4", "45000004");
		mockMvc.perform(put("/api/v1/profesionales/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"\",\"apellido\":\"Abm\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/profesionales/{id}", 9999999).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"X\",\"apellido\":\"Y\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Eliminar un profesional sin actividad lo borra (204)")
	void eliminarSinActividad() throws Exception {
		long id = crear("Dario", "MP-ABM-5", "45000005");
		mockMvc.perform(delete("/api/v1/profesionales/{id}", id)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/profesionales"))
				.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		mockMvc.perform(delete("/api/v1/profesionales/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Un profesional con turnos no se elimina: 409 con el motivo y sigue en la lista")
	void eliminarConTurnos() throws Exception {
		long id = crear("Eva", "MP-ABM-6", "45000006");
		long mascota = objectMapper.readTree(mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Paciente Abm\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
		mockMvc.perform(post("/api/v1/turnos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaHoraInicio\":\"2032-04-01T10:00:00\",\"motivo\":\"Control\",\"mascotaId\":" + mascota
						+ ",\"profesionalId\":" + id + "}"))
				.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/profesionales/{id}", id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("turnos o consultas")));
		mockMvc.perform(get("/api/v1/profesionales"))
				.andExpect(jsonPath("$[?(@.id == " + id + ")].nombre").value("Eva"));
	}
}
