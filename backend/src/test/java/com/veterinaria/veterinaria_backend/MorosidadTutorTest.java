package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * TASK-005: morosidad consolidada por tutor (AGENTS.md 2.5).
 *
 * <p>El endpoint {@code GET /api/v1/tutores/{id}/deuda} es lo que dispara la
 * alerta visual antes de un egreso de guarderia, asi que un total erroneo tiene
 * un costo concreto: deja salir a un perro de un local que le debe plata, o le
 * traba la salida a un cliente que esta al dia.</p>
 *
 * <p>Los casos que mas importan no son los aritmeticos obvios sino los de
 * <b>alcance</b>: que una comanda pagada o cancelada no infle el total, y que la
 * deuda de un tutor no se mezcle con la de otro. Un error de filtrado en cualquiera
 * de los dos seria invisible en una prueba con un solo tutor.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MorosidadTutorTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	@Test
	@DisplayName("Un tutor sin comandas no debe nada")
	void tutorSinDeuda() throws Exception {
		long tutorId = crearTutor("34000001");

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tutorId").value(tutorId))
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.totalDeuda").value(0.00))
				.andExpect(jsonPath("$.cantidadComandas").value(0))
				.andExpect(jsonPath("$.comandasPendientes").isEmpty());
	}

	@Test
	@DisplayName("Dos comandas pendientes se suman y se devuelven en detalle")
	void dosComandasPendientesSeSuman() throws Exception {
		// Dos conceptos distintos para poder tener dos montos distintos.
		long tutorId = crearTutor("34000002");
		emitirComanda("GUARDERIA", 10000, tutorId);
		emitirComanda("OTRO", 5000, tutorId);

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.totalDeuda").value(15000.00))
				.andExpect(jsonPath("$.cantidadComandas").value(2))
				// El detalle existe para que el mostrador diga que se debe y
				// desde cuando, no solo cuanto.
				.andExpect(jsonPath("$.comandasPendientes", org.hamcrest.Matchers.hasSize(2)))
				.andExpect(jsonPath("$.comandasPendientes[0].concepto").value("GUARDERIA"))
				.andExpect(jsonPath("$.comandasPendientes[0].saldoPendiente").value(10000.00))
				.andExpect(jsonPath("$.comandasPendientes[0].diasDeAntiguedad").isNumber())
				.andExpect(jsonPath("$.comandasPendientes[1].concepto").value("OTRO"));

		// El total no puede contradecir al detalle: sale de la misma lectura.
		assertThat(sumarDetalle(tutorId)).isEqualByComparingTo("15000.00");
	}

	@Test
	@DisplayName("Una comanda pagada no aparece en la deuda")
	void comandaPagadaNoCuenta() throws Exception {
		long tutorId = crearTutor("34000003");
		long comandaId = emitirComanda("GUARDERIA", 8000, tutorId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":8000}]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.totalDeuda").value(0.00))
				.andExpect(jsonPath("$.cantidadComandas").value(0));
	}

	@Test
	@DisplayName("Una comanda cancelada no aparece en la deuda")
	void comandaCanceladaNoCuenta() throws Exception {
		long tutorId = crearTutor("34000004");
		long comandaId = emitirComanda("GUARDERIA", 8000, tutorId);

		mockMvc.perform(put("/api/v1/comandas/{id}/cancelar", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.totalDeuda").value(0.00));
	}

	@Test
	@DisplayName("Una comanda parcial suma solo el saldo, no el monto total")
	void comandaParcialSumaSoloElSaldo() throws Exception {
		long tutorId = crearTutor("34000005");
		long comandaId = emitirComanda("GUARDERIA", 10000, tutorId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":4000}]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"));

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				// 10.000 era el precio, pero lo que se debe son los 6.000 que
				// faltan. Sumar el monto total seria exigirle al tutor el doble.
				.andExpect(jsonPath("$.totalDeuda").value(6000.00))
				.andExpect(jsonPath("$.comandasPendientes[0].saldoPendiente").value(6000.00));
	}

	@Test
	@DisplayName("La deuda de un tutor no incluye las comandas de otro")
	void laDeudaNoSeMezclaEntreTutores() throws Exception {
		long deudor = crearTutor("34000006");
		long alDia = crearTutor("34000007");

		emitirComanda("GUARDERIA", 10000, deudor);
		emitirComanda("OTRO", 5000, alDia);

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", deudor))
				.andExpect(jsonPath("$.totalDeuda").value(10000.00))
				.andExpect(jsonPath("$.cantidadComandas").value(1));

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", alDia))
				.andExpect(jsonPath("$.totalDeuda").value(5000.00))
				.andExpect(jsonPath("$.cantidadComandas").value(1));

		// Y la suma de los dos no se mezcla con nadie mas.
		assertThat(comandaRepository.findByTutorIdOrderByFechaEmisionDesc(deudor))
				.hasSize(1)
				.allSatisfy(c -> assertThat(c.getTutor().getId()).isEqualTo(deudor));
	}

	@Test
	@DisplayName("Un tutor inexistente responde 404 y no una deuda en cero")
	void tutorInexistenteDevuelve404() throws Exception {
		// Un 200 con totalDeuda 0 para un tutor que no existe es peor que un
		// 404: el mostrador leeria "al dia" y dejaria salir a una mascota cuyo
		// tutor ni siquiera esta cargado.
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", 987654321L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje",
						org.hamcrest.Matchers.containsString("No existe un tutor")));
	}

	// ------------------------------------------------------------- helpers

	private java.math.BigDecimal sumarDetalle(long tutorId) throws Exception {
		String json = mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		java.math.BigDecimal suma = java.math.BigDecimal.ZERO;
		for (var comanda : objectMapper.readTree(json).get("comandasPendientes")) {
			suma = suma.add(new java.math.BigDecimal(comanda.get("saldoPendiente").asText()));
		}
		return suma;
	}

	private long emitirComanda(String concepto, long monto, long tutorId) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());

		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"%s","tutorId":%d,"descripcion":"Consumo"}
								""".formatted(concepto, tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Rita","apellido":"Sosa","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}