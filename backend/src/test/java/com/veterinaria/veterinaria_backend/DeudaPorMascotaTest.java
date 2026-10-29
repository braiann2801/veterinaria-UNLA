package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * La deuda que bloquea el egreso es la de la mascota, no la del tutor.
 *
 * <p>Pedido textual: "si un dueño tiene dos mascotas y paga la deuda de la
 * primera mascota, esta mascota ya se puede retirar. En cambio, en la otra no."
 * La deuda de una mascota es el saldo de las comandas emitidas para ella, sin
 * importar a nombre de qué tutor se emitieron.</p>
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class DeudaPorMascotaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Pagar la deuda de la primera mascota la libera; la segunda sigue bloqueada")
	void pagarUnaMascotaNoLiberaALaOtra() throws Exception {
		long tutorId = crearTutor("36000001");
		long primera = crearMascotaDe(tutorId, "Luna");
		long segunda = crearMascotaDe(tutorId, "Simba");
		long comandaPrimera = emitirComanda(tutorId, primera, "GUARDERIA", 9000);
		emitirComanda(tutorId, segunda, "GUARDERIA", 9000);

		pagar(comandaPrimera, 9000);

		mockMvc.perform(validar(primera, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.motivoBloqueo").value("NINGUNO"))
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.saldoDeuda").value(0.00));

		mockMvc.perform(validar(segunda, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("DEUDA_PENDIENTE"))
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				// Solo la deuda de Simba, no la suma de las dos.
				.andExpect(jsonPath("$.saldoDeuda").value(9000.00))
				.andExpect(jsonPath("$.etiquetaMotivo").value("Mascota con deuda pendiente"))
				.andExpect(jsonPath("$.mensaje").value("$9.000 pendientes en 1 comanda(s) de Simba."));
	}

	@Test
	@DisplayName("Sin pagar nada, cada mascota ve solo su propio saldo")
	void cadaMascotaVeSuSaldo() throws Exception {
		long tutorId = crearTutor("36000002");
		long primera = crearMascotaDe(tutorId, "Kira");
		long segunda = crearMascotaDe(tutorId, "Rocky");
		emitirComanda(tutorId, primera, "GUARDERIA", 4000);
		emitirComanda(tutorId, segunda, "OTRO", 7000);

		mockMvc.perform(validar(primera, tutorId))
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.saldoDeuda").value(4000.00));
		mockMvc.perform(validar(segunda, tutorId))
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.saldoDeuda").value(7000.00));
	}

	@Test
	@DisplayName("La deuda sigue a la mascota: otro tutor autorizado tampoco la retira")
	void laDeudaSigueALaMascota() throws Exception {
		long titular = crearTutor("36000003");
		long cotitular = crearTutor("36000004");
		long mascotaId = crearMascotaDe(titular, "Coco");
		vincular(cotitular, mascotaId);
		emitirComanda(titular, mascotaId, "GUARDERIA", 5000);

		mockMvc.perform(validar(mascotaId, cotitular))
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("DEUDA_PENDIENTE"))
				.andExpect(jsonPath("$.saldoDeuda").value(5000.00));
	}

	@Test
	@DisplayName("Una comanda sin mascota no bloquea el egreso, pero sigue siendo deuda del tutor")
	void comandaSinMascotaNoBloquea() throws Exception {
		long tutorId = crearTutor("36000005");
		long mascotaId = crearMascotaDe(tutorId, "Mora");
		emitirComanda(tutorId, null, "OTRO", 3000);

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.tieneDeuda").value(false));
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.totalDeuda").value(3000.00));
	}

	@Test
	@DisplayName("La deuda del tutor detalla de qué mascota es cada comanda")
	void laDeudaDelTutorNombraLaMascota() throws Exception {
		long tutorId = crearTutor("36000006");
		long mascotaId = crearMascotaDe(tutorId, "Tango");
		emitirComanda(tutorId, mascotaId, "GUARDERIA", 2500);

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comandasPendientes[0].mascotaId").value(mascotaId))
				.andExpect(jsonPath("$.comandasPendientes[0].nombreMascota").value("Tango"));
	}

	// ------------------------------------------------------------- helpers

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validar(
			long mascotaId, long tutorId) {
		return get("/api/v1/egreso/validar/tutor")
				.param("mascotaId", String.valueOf(mascotaId))
				.param("tutorId", String.valueOf(tutorId));
	}

	private void pagar(long comandaId, long monto) throws Exception {
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":%d}]}".formatted(monto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));
	}

	private long emitirComanda(long tutorId, Long mascotaId, String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"%s","tutorId":%d,"mascotaId":%s,"descripcion":"Consumo"}
								""".formatted(concepto, tutorId, mascotaId == null ? "null" : mascotaId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascotaDe(long tutorId, String nombre) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","sexo":"Hembra","conducta":"normal"}
								""".formatted(nombre)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long mascotaId = objectMapper.readTree(json).get("id").asLong();
		vincular(tutorId, mascotaId);
		return mascotaId;
	}

	private void vincular(long tutorId, long mascotaId) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
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
