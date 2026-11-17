package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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

import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Filtro de la caja por mascota y deuda de una mascota.
 *
 * <p>La caja del dia obliga a recorrer fechas para encontrar la deuda de una
 * mascota. El listado por mascota responde "qué debe este animal" sin acotar
 * por jornada, y el endpoint de deuda devuelve el total con la fecha de cada
 * comanda. La fecha de emisión es la que se muestra: es la única fecha que la
 * comanda conoce y la que permite rastrear desde cuándo se debe.</p>
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class ComandasPorMascotaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	@Test
	@DisplayName("El listado por mascota trae sus comandas de cualquier fecha y ninguna de otra")
	void listaTodasLasFechasDeUnaMascota() throws Exception {
		long tutorId = crearTutor("37000001");
		long luna = crearMascotaDe(tutorId, "Luna");
		long simba = crearMascotaDe(tutorId, "Simba");

		long deHoy = emitirComanda(tutorId, luna, "GUARDERIA", 8000);
		long deMarzo = crearComandaEnFecha(tutorId, luna, "2026-03-03T12:00:00");
		long deSimba = emitirComanda(tutorId, simba, "OTRO", 4000);

		var listado = listarPorMascota(luna, null);

		assertThat(listado).hasSize(2);
		assertThat(listado).extracting(n -> n.get("id").asLong())
				.containsExactlyInAnyOrder(deHoy, deMarzo)
				.doesNotContain(deSimba);
		// La vista ordena por emisión descendente: la deuda más reciente arriba.
		assertThat(listado.get(0).get("id").asLong()).isEqualTo(deHoy);
	}

	@Test
	@DisplayName("El estado acota el listado y uno inválido devuelve 400 aunque no haya comandas")
	void estadoFiltraYValida() throws Exception {
		long tutorId = crearTutor("37000002");
		long mascotaId = crearMascotaDe(tutorId, "Rocky");
		long pendiente = emitirComanda(tutorId, mascotaId, "GUARDERIA", 9000);
		long parcial = emitirComanda(tutorId, mascotaId, "OTRO", 5000);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", parcial)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":1000}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"));

		var pendientes = listarPorMascota(mascotaId, "PENDIENTE");
		assertThat(pendientes).hasSize(1);
		assertThat(pendientes.get(0).get("id").asLong()).isEqualTo(pendiente);

		var parciales = listarPorMascota(mascotaId, "PARCIAL");
		assertThat(parciales).hasSize(1);
		assertThat(parciales.get(0).get("id").asLong()).isEqualTo(parcial);

		mockMvc.perform(get("/api/v1/comandas/mascota/{id}", mascotaId)
						.param("estado", "INVENTADO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("Estado de comanda invalido")));
	}

	@Test
	@DisplayName("Una mascota inexistente responde 404, no una lista vacía")
	void mascotaInexistenteDevuelve404() throws Exception {
		mockMvc.perform(get("/api/v1/comandas/mascota/{id}", 987654321L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("No existe una mascota")));
	}

	@Test
	@DisplayName("La deuda de la mascota informa el total y la fecha de cada comanda")
	void deudaDeMascotaInformaFechaYTotal() throws Exception {
		long tutorId = crearTutor("37000003");
		long mascotaId = crearMascotaDe(tutorId, "Kira");
		crearComandaEnFecha(tutorId, mascotaId, "2026-09-16T10:00:00");
		long deHoy = emitirComanda(tutorId, mascotaId, "OTRO", 2500);

		mockMvc.perform(get("/api/v1/mascotas/{id}/deuda", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mascotaId").value(mascotaId))
				.andExpect(jsonPath("$.nombreMascota").value("Kira"))
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.totalDeuda").value(5500.00))
				.andExpect(jsonPath("$.cantidadComandas").value(2))
				// La fecha viaja en el detalle: es lo que la caja muestra al lado
				// del saldo para que se sepa de qué jornada es la deuda.
				.andExpect(jsonPath("$.comandasPendientes[*].fechaEmision",
						Matchers.hasItem("2026-09-16")));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", deHoy)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":2500}]}"))
				.andExpect(status().isOk());

		// Pagada una, la deuda baja y la comanda sale del detalle.
		mockMvc.perform(get("/api/v1/mascotas/{id}/deuda", mascotaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.totalDeuda").value(3000.00))
				.andExpect(jsonPath("$.cantidadComandas").value(1));
	}

	// ------------------------------------------------------------- helpers

	private ArrayNode listarPorMascota(long mascotaId, String estado) throws Exception {
		var request = get("/api/v1/comandas/mascota/{id}", mascotaId);
		if (estado != null) {
			request = request.param("estado", estado);
		}
		return (ArrayNode) objectMapper.readTree(mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private long emitirComanda(long tutorId, long mascotaId, String concepto, long monto) throws Exception {
		ponerTarifa(concepto, monto);
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"%s","tutorId":%d,"mascotaId":%d}
								""".formatted(concepto, tutorId, mascotaId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	/**
	 * Comanda con fecha de emisión forzada por repositorio: la API no acepta la
	 * fecha y agregar un parámetro de test sería una puerta para falsear el corte.
	 */
	private long crearComandaEnFecha(long tutorId, long mascotaId, String fechaHora) throws Exception {
		long id = emitirComanda(tutorId, mascotaId, "GUARDERIA", 3000);
		var comanda = comandaRepository.findById(id).orElseThrow();
		comanda.setFechaEmision(LocalDateTime.parse(fechaHora));
		comandaRepository.save(comanda);
		return id;
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
	}

	private long crearMascotaDe(long tutorId, String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","tutores":[{"tutorId":%d,"autorizadoRetiro":true}]}
								""".formatted(nombre, tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Paz","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
