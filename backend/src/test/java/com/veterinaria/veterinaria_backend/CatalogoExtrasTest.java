package com.veterinaria.veterinaria_backend;

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
 * Catalogo de extras (vacunas, urgencias, insumos) con precio fijado por el
 * Duenio. El profesional los elige al cerrar la atencion; nunca pone precios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoExtrasTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@WithMockUser(roles = "DUENIO")
	@DisplayName("El Duenio da de alta un extra; el nombre se normaliza y el precio queda con 2 decimales")
	void altaDeExtra() throws Exception {
		mockMvc.perform(post("/api/v1/extras")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"  vacuna   antirrabica \",\"precio\":12500}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Vacuna antirrabica"))
				.andExpect(jsonPath("$.precio").value(12500.00))
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	@WithMockUser(roles = "DUENIO")
	@DisplayName("Editar cambia nombre y precio; desactivar lo saca del listado de activos pero no lo borra")
	void editarYDesactivar() throws Exception {
		long id = crearExtra("Urgencia", 20000);

		mockMvc.perform(put("/api/v1/extras/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Urgencia nocturna\",\"precio\":25000,\"activo\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Urgencia nocturna"))
				.andExpect(jsonPath("$.precio").value(25000.00))
				.andExpect(jsonPath("$.activo").value(false));

		mockMvc.perform(get("/api/v1/extras").param("soloActivos", "true"))
				.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		mockMvc.perform(get("/api/v1/extras"))
				.andExpect(jsonPath("$[?(@.id == " + id + ")].activo").value(false));
	}

	@Test
	@WithMockUser(roles = "DUENIO")
	@DisplayName("Datos invalidos y nombres repetidos dan 400")
	void validaciones() throws Exception {
		crearExtra("Desparasitario", 3000);

		mockMvc.perform(post("/api/v1/extras")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"\",\"precio\":-1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("El nombre del extra es obligatorio")))
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("El precio no puede ser negativo")));

		mockMvc.perform(post("/api/v1/extras")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"desparasitario\",\"precio\":3500}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Ya existe un extra llamado Desparasitario"));
	}

	@Test
	@WithMockUser(roles = "DUENIO")
	@DisplayName("Editar un extra inexistente da 404")
	void editarInexistente() throws Exception {
		mockMvc.perform(put("/api/v1/extras/{id}", 99999999)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"X\",\"precio\":1,\"activo\":true}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(roles = "VETERINARIO")
	@DisplayName("El veterinario puede leer el catalogo pero no modificarlo")
	void veterinarioSoloLee() throws Exception {
		mockMvc.perform(get("/api/v1/extras")).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/extras")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Vacuna\",\"precio\":100}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/extras/{id}", 1)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Vacuna\",\"precio\":100,\"activo\":true}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "RECEPCION")
	@DisplayName("Recepcion tampoco puede modificar el catalogo")
	void recepcionNoModifica() throws Exception {
		mockMvc.perform(post("/api/v1/extras")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Vacuna\",\"precio\":100}"))
				.andExpect(status().isForbidden());
	}

	private long crearExtra(String nombre, long precio) throws Exception {
		String json = mockMvc.perform(post("/api/v1/extras")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"%s\",\"precio\":%d}".formatted(nombre, precio)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
