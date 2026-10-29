package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
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
 * Cobro de la guarderia: la reserva emite la comanda de GUARDERIA.
 *
 * <p>Antes ninguna parte del sistema generaba comandas de guarderia, asi que las
 * estadias no se podian cobrar desde la Caja y el egreso nunca encontraba deuda
 * que bloquear. La comanda nace con la reserva (decision del Dueño): se puede
 * pagar o señar por adelantado y el egreso exige que este saldada.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// La API exige login: este test prueba reglas de negocio, no permisos.
@WithMockUser(roles = "DUENIO")
class GuarderiaCobroTest {

	private static final LocalDate DIA = LocalDate.of(2031, 3, 14);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void tarifa() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/GUARDERIA").contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\": 10000}"))
				.andExpect(status().isOk());
	}

	private long id(String json) throws Exception {
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String nombre, String dni) throws Exception {
		return id(mockMvc.perform(post("/api/v1/tutores").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\",\"apellido\":\"Guarderia\",\"dni\":\"" + dni + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private long crearMascota(String nombre) throws Exception {
		return id(mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private void vincular(long tutorId, long mascotaId, boolean autorizado) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}", tutorId, mascotaId)
				.param("autorizadoRetiro", String.valueOf(autorizado)))
				.andExpect(status().isCreated());
	}

	private String reservar(long mascotaId) throws Exception {
		return mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"" + DIA + "\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":" + mascotaId + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	@DisplayName("Reservar emite una comanda de GUARDERIA pendiente con la tarifa, a nombre del tutor autorizado")
	void reservaEmiteComanda() throws Exception {
		long cotutor = crearTutor("Cotutor", "42000001");
		long titular = crearTutor("Titular", "42000002");
		long mascota = crearMascota("Lola");
		// El cotutor se vincula primero: el titular tiene que ser el autorizado, no el mas antiguo.
		vincular(cotutor, mascota, false);
		vincular(titular, mascota, true);

		String reserva = reservar(mascota);
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.concepto").value("GUARDERIA"))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.montoTotal").value(10000.00))
				.andExpect(jsonPath("$.saldoPendiente").value(10000.00))
				.andExpect(jsonPath("$.tutorId").value(titular))
				.andExpect(jsonPath("$.mascotaId").value(mascota));

		// La deuda de la estadia bloquea el retiro hasta que se pague.
		mockMvc.perform(get("/api/v1/egreso/validar").param("mascotaId", String.valueOf(mascota))
				.param("tutorDni", "42000002"))
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.tieneDeuda").value(true));
	}

	@Test
	@DisplayName("Una mascota sin tutor se puede reservar, pero sin comanda (comandaId null)")
	void mascotaSinTutorNoEmite() throws Exception {
		long mascota = crearMascota("Huerfano");
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"" + DIA + "\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":" + mascota + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comandaId").value(Matchers.nullValue()));
	}

	@Test
	@DisplayName("Cancelar la reserva anula su comanda si no tiene pagos")
	void cancelarAnulaComanda() throws Exception {
		long tutor = crearTutor("Cancela", "42000003");
		long mascota = crearMascota("Toto");
		vincular(tutor, mascota, true);
		String reserva = reservar(mascota);
		long reservaId = id(reserva);
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();

		mockMvc.perform(put("/api/v1/guarderia/{id}/cancelar", reservaId)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.estado").value("CANCELADA"));
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutor))
				.andExpect(jsonPath("$.tieneDeuda").value(false));
	}

	@Test
	@DisplayName("Cancelar la reserva no toca una comanda que ya tiene pagos")
	void cancelarConPagosNoAnula() throws Exception {
		long tutor = crearTutor("Sena", "42000004");
		long mascota = crearMascota("Bruno");
		vincular(tutor, mascota, true);
		String reserva = reservar(mascota);
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagos\":[{\"metodoPago\":\"SENA\",\"monto\":3000}],\"tutorId\":" + tutor + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/guarderia/{id}/cancelar", id(reserva))).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.estado").value("PARCIAL"))
				.andExpect(jsonPath("$.saldoPendiente").value(7000.00));
	}

	@Test
	@DisplayName("Eliminar la reserva borra su comanda sin pagos; con pagos responde 409")
	void eliminarReserva() throws Exception {
		long tutor = crearTutor("Elimina", "42000005");
		long sinPagos = crearMascota("Sin Pagos");
		long conPagos = crearMascota("Con Pagos");
		vincular(tutor, sinPagos, true);
		vincular(tutor, conPagos, true);

		String r1 = reservar(sinPagos);
		long c1 = objectMapper.readTree(r1).get("comandaId").asLong();
		mockMvc.perform(delete("/api/v1/guarderia/{id}", id(r1))).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/comandas/{id}", c1)).andExpect(status().isNotFound());

		String r2 = reservar(conPagos);
		long c2 = objectMapper.readTree(r2).get("comandaId").asLong();
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", c2).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":1000}],\"tutorId\":" + tutor + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/guarderia/{id}", id(r2))).andExpect(status().isConflict());
		mockMvc.perform(get("/api/v1/comandas/{id}", c2)).andExpect(jsonPath("$.estado").value("PARCIAL"));
	}
}
