package com.veterinaria.veterinaria_backend;

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
 * Registro de la entrega ("Registrar entrega y cerrar" del checkout de egreso).
 *
 * <p>Antes el boton solo cerraba el modal: no quedaba constancia de quien
 * retiro ni cuando, y la reserva de guarderia seguia RESERVADA. Ahora el
 * backend vuelve a validar el retiro (no confia en la pantalla), guarda la
 * entrega y finaliza la estadia.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// La API exige login: este test prueba reglas de negocio, no permisos.
@WithMockUser(roles = "DUENIO")
class EgresoRegistroTest {

	private static final LocalDate DIA = LocalDate.of(2031, 5, 20);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private long tutor;
	private long cotutor;
	private long mascota;

	@BeforeEach
	void preparar() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/GUARDERIA").contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\": 10000}")).andExpect(status().isOk());
		tutor = crearTutor("Retira", "43000001");
		cotutor = crearTutor("NoRetira", "43000002");
		mascota = id(mockMvc.perform(post("/api/v1/mascotas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Kira\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		vincular(tutor, true);
		vincular(cotutor, false);
	}

	private long id(String json) throws Exception {
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String nombre, String dni) throws Exception {
		return id(mockMvc.perform(post("/api/v1/tutores").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"" + nombre + "\",\"apellido\":\"Egreso\",\"dni\":\"" + dni + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private void vincular(long tutorId, boolean autorizado) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}", tutorId, mascota)
				.param("autorizadoRetiro", String.valueOf(autorizado)))
				.andExpect(status().isCreated());
	}

	/** Reserva de guarderia del DIA, con su comanda ya pagada: la estadia queda lista para retirar. */
	private long reservarYPagar() throws Exception {
		String reserva = mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"" + DIA + "\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":" + mascota + "}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long comandaId = objectMapper.readTree(reserva).get("comandaId").asLong();
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":10000}],\"tutorId\":" + tutor + "}"))
				.andExpect(status().isOk());
		return id(reserva);
	}

	private String registrar(long tutorId) {
		return "{\"mascotaId\":" + mascota + ",\"tutorId\":" + tutorId + ",\"fecha\":\"" + DIA + "\"}";
	}

	@Test
	@DisplayName("Registrar la entrega guarda quien retiro, finaliza la estadia y la marca en la lista del dia")
	void registrarEntrega() throws Exception {
		long reservaId = reservarYPagar();

		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON).content(registrar(tutor)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mascotaId").value(mascota))
				.andExpect(jsonPath("$.tutorId").value(tutor))
				.andExpect(jsonPath("$.nombreTutor").value("Retira Egreso"))
				.andExpect(jsonPath("$.reservaGuarderiaId").value(reservaId))
				.andExpect(jsonPath("$.fechaHora").isNotEmpty());

		mockMvc.perform(get("/api/v1/guarderia").param("fecha", DIA.toString()))
				.andExpect(jsonPath("$[0].estado").value("FINALIZADO"));
		mockMvc.perform(get("/api/v1/egreso/pacientes-hoy").param("fecha", DIA.toString()))
				.andExpect(jsonPath("$[0].mascotaId").value(mascota))
				.andExpect(jsonPath("$[0].entregadoA").value("Retira Egreso"))
				.andExpect(jsonPath("$[0].fechaHoraEntrega").isNotEmpty());
	}

	@Test
	@DisplayName("El backend vuelve a validar: un cotutor sin autorizacion no puede registrar la entrega (409)")
	void cotutorNoAutorizado() throws Exception {
		reservarYPagar();
		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON).content(registrar(cotutor)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("no está autorizado")));
		mockMvc.perform(get("/api/v1/guarderia").param("fecha", DIA.toString()))
				.andExpect(jsonPath("$[0].estado").value("RESERVADO"));
	}

	@Test
	@DisplayName("Con la estadia impaga no se puede registrar la entrega (409)")
	void conDeuda() throws Exception {
		mockMvc.perform(post("/api/v1/guarderia").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"" + DIA + "\",\"tipoEstadia\":\"COMPLETA_24H\",\"mascotaId\":" + mascota + "}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON).content(registrar(tutor)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("No se registra dos veces la entrega de la misma mascota en el dia (409)")
	void entregaDuplicada() throws Exception {
		reservarYPagar();
		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON).content(registrar(tutor)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON).content(registrar(tutor)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("ya fue entregada")));
	}

	@Test
	@DisplayName("Una mascota sin guarderia (solo turno) tambien registra la entrega, sin reserva asociada")
	void sinGuarderia() throws Exception {
		mockMvc.perform(post("/api/v1/egreso/registrar").contentType(MediaType.APPLICATION_JSON).content(registrar(tutor)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservaGuarderiaId").value(Matchers.nullValue()));
	}
}
