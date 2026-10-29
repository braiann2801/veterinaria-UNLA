package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * Cambio de la autorizacion de retiro de un vinculo Tutor-Mascota existente.
 *
 * <p>Antes, el boton "Autorizado / No autorizado" del Padron reusaba el POST de
 * alta del vinculo, que rechaza un vinculo repetido (400), asi que la
 * autorizacion no se podia cambiar sin desvincular y volver a vincular. El PUT
 * actualiza solo el flag; el POST sigue rechazando duplicados.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// La API exige login: este test prueba la regla del vinculo, no permisos.
@WithMockUser(roles = "DUENIO")
class VinculoAutorizacionTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long tutorId;
	private long mascotaId;
	private final String dni = "41234567";

	private long idDe(String json) throws Exception {
		return objectMapper.readTree(json).get("id").asLong();
	}

	@BeforeEach
	void vincularAutorizado() throws Exception {
		tutorId = idDe(mockMvc.perform(post("/api/v1/tutores").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Vera\",\"apellido\":\"Vinculo\",\"dni\":\"" + dni + "\",\"telefono\":\"1100000000\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mascotaId = idDe(mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Rocco\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mockMvc.perform(post("/api/v1/tutores/" + tutorId + "/mascotas/" + mascotaId + "?autorizadoRetiro=true"))
				.andExpect(status().isCreated());
	}

	private String vinculo() {
		return "/api/v1/tutores/" + tutorId + "/mascotas/" + mascotaId;
	}

	private String egreso() {
		return "/api/v1/egreso/validar?mascotaId=" + mascotaId + "&tutorDni=" + dni;
	}

	@Test
	@DisplayName("PUT cambia la autorizacion de un vinculo existente y el egreso la respeta")
	void cambiarAutorizacion() throws Exception {
		mockMvc.perform(put(vinculo() + "?autorizadoRetiro=false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(mascotaId))
				.andExpect(jsonPath("$.tutores[0].id").value(tutorId))
				.andExpect(jsonPath("$.tutores[0].autorizadoRetiro").value(false));
		mockMvc.perform(get(egreso()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("TUTOR_NO_AUTORIZADO"));

		mockMvc.perform(put(vinculo() + "?autorizadoRetiro=true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tutores[0].autorizadoRetiro").value(true));
		mockMvc.perform(get(egreso()))
				.andExpect(jsonPath("$.puedeRetirar").value(true));
	}

	@Test
	@DisplayName("PUT tambien acepta el flag en el body, como el POST")
	void cambiarAutorizacionPorBody() throws Exception {
		mockMvc.perform(put(vinculo()).contentType(MediaType.APPLICATION_JSON).content("{\"autorizadoRetiro\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tutores[0].autorizadoRetiro").value(false));
	}

	@Test
	@DisplayName("PUT sobre un vinculo inexistente da 404 y sin flag da 400")
	void errores() throws Exception {
		long otraMascota = idDe(mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Sin Vinculo\",\"especie\":\"FELINO\"}"))
				.andReturn().getResponse().getContentAsString());
		mockMvc.perform(put("/api/v1/tutores/" + tutorId + "/mascotas/" + otraMascota + "?autorizadoRetiro=false"))
				.andExpect(status().isNotFound());
		mockMvc.perform(put(vinculo()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("El POST de alta sigue rechazando un vinculo repetido")
	void postDuplicadoSigueEn400() throws Exception {
		mockMvc.perform(post(vinculo() + "?autorizadoRetiro=false"))
				.andExpect(status().isBadRequest());
	}
}
