package com.veterinaria.veterinaria_backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Buscadores paginados del Padrón: filtros, totales, orden e inactivos.
 *
 * <p>Contexto propio por el limitador de login compartido (ver
 * {@code MedicamentoControllerTest}).</p>
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
		"veterinaria.auth.login-intentos-maximos=1000",
		"veterinaria.auth.login-ip-intentos-maximos=1000",
		"veterinaria.auth.login-usuario-intentos-maximos=1000"
})
class PadronBusquedaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Buscar mascotas por nombre: pagina, total, orden y tutores resumidos")
	void buscarMascotasPorNombre() throws Exception {
		long tutorId = crearTutor("Zzz", "BusquedaTutor", "99110001", null);
		long primera = crearMascota("AlfaBusqueda");
		long segunda = crearMascota("AlfaBusquedaDos");
		crearMascota("BetaBusqueda");
		vincular(tutorId, primera);

		mockMvc.perform(get("/api/v1/mascotas/buscar").param("q", "alfabusqueda"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(1))
				.andExpect(jsonPath("$.contenido", hasSize(2)))
				.andExpect(jsonPath("$.contenido[0].nombre").value("Alfabusqueda"))
				.andExpect(jsonPath("$.contenido[0].tutores", hasSize(1)))
				.andExpect(jsonPath("$.contenido[0].tutores[0].nombre").value("Zzz"))
				.andExpect(jsonPath("$.contenido[1].nombre").value("Alfabusquedados"));

		mockMvc.perform(get("/api/v1/mascotas/buscar").param("q", "alfabusqueda").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.contenido", hasSize(1)));

		mockMvc.perform(get("/api/v1/mascotas/buscar").param("q", "alfabusqueda")
						.param("size", "1").param("page", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(segunda));
	}

	@Test
	@DisplayName("El buscador de mascotas respeta el toggle de inactivos")
	void buscarMascotasInactivas() throws Exception {
		long id = crearMascota("InactivaBusqueda");
		mockMvc.perform(delete("/api/v1/mascotas/{id}", id)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/mascotas/buscar").param("q", "inactivabusqueda"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));

		mockMvc.perform(get("/api/v1/mascotas/buscar").param("q", "inactivabusqueda")
						.param("incluirInactivos", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].activo").value(false));
	}

	@Test
	@DisplayName("Buscar tutores por nombre, apellido, DNI con puntos y teléfono")
	void buscarTutoresPorCampos() throws Exception {
		crearTutor("Ana", "ZqBusqueda", "99110002", "1155551234");

		mockMvc.perform(get("/api/v1/tutores/buscar").param("q", "zqbusqueda"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].nombreCompleto").value("Ana Zqbusqueda"));

		mockMvc.perform(get("/api/v1/tutores/buscar").param("q", "99.110.002"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1));

		mockMvc.perform(get("/api/v1/tutores/buscar").param("q", "1155551234"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1));

		mockMvc.perform(get("/api/v1/tutores/buscar").param("q", "nadie-coincide"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0))
				.andExpect(jsonPath("$.contenido", hasSize(0)));
	}

	@Test
	@DisplayName("Buscar tutores: orden por apellido, paginación, inactivos y acote de parámetros")
	void buscarTutoresPaginado() throws Exception {
		long primero = crearTutor("Beto", "ZqOrden", "99110003", null);
		long segundo = crearTutor("Carla", "ZqOrden", "99110004", null);
		mockMvc.perform(delete("/api/v1/tutores/{id}", segundo)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/tutores/buscar").param("q", "zqorden").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].id").value(primero));

		mockMvc.perform(get("/api/v1/tutores/buscar").param("q", "zqorden")
						.param("incluirInactivos", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[0].nombre").value("Beto"))
				.andExpect(jsonPath("$.contenido[1].nombre").value("Carla"))
				.andExpect(jsonPath("$.contenido[1].mascotas").isArray());

		mockMvc.perform(get("/api/v1/tutores/buscar").param("page", "-5").param("size", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(200));
	}

	@Test
	@DisplayName("Las rutas /buscar no colisionan con /{id}")
	void rutaBuscarNoColisionaConId() throws Exception {
		mockMvc.perform(get("/api/v1/tutores/buscar"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido").isArray());
		mockMvc.perform(get("/api/v1/mascotas/buscar"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido").isArray());
		mockMvc.perform(get("/api/v1/mascotas/999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje", containsString("mascota")));
	}

	// ------------------------------------------------------------- helpers

	private long crearMascota(String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","conducta":"normal"}
								""".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String nombre, String apellido, String dni, String telefono) throws Exception {
		String telefonoJson = telefono == null ? "" : ",\"telefono\":\"" + telefono + "\"";
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","apellido":"%s","dni":"%s"%s}
								""".formatted(nombre, apellido, dni, telefonoJson)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void vincular(long tutorId, long mascotaId) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
	}
}
