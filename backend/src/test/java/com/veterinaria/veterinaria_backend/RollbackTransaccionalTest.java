package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.PagoRepository;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-005: atomicidad de las escrituras de ficha clinica y caja.
 *
 * <p><b>Por que este test NO lleva {@code @Transactional}.</b> Es la misma
 * razon que en {@link AforoConcurrenteTest}: con la anotacion, la llamada al
 * servicio se une a la transaccion abierta por el test y el rollback que Spring
 * ejecuta al propagarse la excepcion no alcanza a deshacer nada, porque el
 * rollback real ocurre en el commit final, que nunca llega. El test veria la
 * excepcion pero el contexto de persistencia quedaria con los cambios a medias y
 * la asercion fallaria por el motivo equivocado.</p>
 *
 * <p>Sin la anotacion, cada llamada al servicio es duena de su transaccion: si
 * algo falla, deshace. Eso es exactamente lo que hay que probar, asi que la
 * limpieza de datos es manual.</p>
 *
 * <p>Las dos propiedades verificadas son las que un fallo silencioso dejaria
 * pasar: una ficha clinica sin su comanda (el local asume el costo) y un cobro
 * parcial registrado (la caja cierra descuadrada).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RollbackTransaccionalTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	@Autowired
	private PagoRepository pagoRepository;

	@AfterEach
	void limpiar() {
		// Las reservas se borran primero porque AforoConcurrenteTest tambien
		// deja mascotas commiteadas: si el orden de ejecucion de las clases
		// dejara alguna reserva viva, el DELETE de mascota reventaria por FK.
		jdbc.update("DELETE FROM reserva_guarderia");
		jdbc.update("DELETE FROM pago");
		jdbc.update("DELETE FROM comanda_cobro");
		jdbc.update("DELETE FROM consulta_medica");
		jdbc.update("DELETE FROM turno_clinico");
		jdbc.update("DELETE FROM tutor_mascota");
		jdbc.update("DELETE FROM mascota");
		jdbc.update("DELETE FROM tutor");
		jdbc.update("DELETE FROM profesional");
		jdbc.update("DELETE FROM tarifa");
	}

	@Test
	@DisplayName("Si la comanda no se puede emitir, la ficha clinica tampoco queda registrada")
	void fichaSinComandaNoQuedaRegistrada() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 8000);

		long mascotaId = crearMascota();
		long tutorId = crearTutor("33000001");

		// El vinculo existe pero NO autoriza el retiro: el consultorio no tiene a
		// quien cargarle la cuenta, y esa es exactamente la situacion en la que
		// un "elegir el primer tutor" dejaria una ficha huérfana.
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false",
				tutorId, mascotaId))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Urgencia"}
								""".formatted(mascotaId, crearProfesional("MAT-R1"))))
				.andExpect(status().isBadRequest());

		assertThat(contar("consulta_medica", "mascota_id", mascotaId))
				.as("una atencion sin comanda deja al local asumiendo el costo")
				.isZero();

		assertThat(contar("comanda_cobro", "tutor_id", tutorId))
				.as("no se debe emitir ninguna comanda")
				.isZero();
	}

	@Test
	@DisplayName("Si un pago del combo falla, ninguno queda aplicado")
	void comboDePagosInvalidoNoAplicaNinguno() throws Exception {
		ponerTarifa("GUARDERIA", 10000);
		long tutorId = crearTutor("33000002");
		long comandaId = emitirComanda("GUARDERIA", tutorId);

		// El efectivo entra bien; el credito de 7000 ya no alcanza contra el
		// saldo restante de 6000.
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[
								  {"metodoPago":"EFECTIVO","monto":4000},
								  {"metodoPago":"CREDITO","monto":7000}
								]}
								"""))
				.andExpect(status().isBadRequest());

		// Lectura fresca: si el primer pago hubiera quedado, el mostrador creeria
		// haber cobrado 4000 y el cliente saldaria solo los 6000 restantes
		// creyendose al dia.
		assertThat(contar("pago", "comanda_id", comandaId))
				.as("ningun pago del combo debe haber sobrevivido")
				.isZero();

		ComandaCobro comanda = comandaRepository.findById(comandaId).orElseThrow();
		assertThat(comanda.getEstado()).isEqualTo(ComandaCobro.EstadoComanda.PENDIENTE);
		assertThat(comanda.getSaldoPendiente()).isEqualByComparingTo("10000.00");
	}

	@Test
	@DisplayName("Un combo valido deja la comanda PAGADA y los dos medios registrados")
	void comboValidoSiSePersiste() throws Exception {
		ponerTarifa("GUARDERIA", 10000);
		long tutorId = crearTutor("33000003");
		long comandaId = emitirComanda("GUARDERIA", tutorId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[
								  {"metodoPago":"EFECTIVO","monto":4000},
								  {"metodoPago":"TRANSFERENCIA","monto":6000}
								]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));

		// Contraprueba del caso anterior: aca los pagos SI quedan, con id.
		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId))
				.hasSize(2)
				.allSatisfy(pago -> assertThat(pago.getId()).isNotNull());
	}

	// ------------------------------------------------------------- helpers

	private long contar(String tabla, String columna, long valor) {
		Long total = jdbc.queryForObject(
				"SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ?",
				Long.class, valor);
		return total == null ? 0L : total;
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
	}

	private long emitirComanda(String concepto, long tutorId) throws Exception {
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"%s","tutorId":%d,"descripcion":"Estadia"}
								""".formatted(concepto, tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascota() throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Firulais","especie":"CANINO","raza":"Mestizo","sexo":"Macho"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Gomez","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional(String matricula) throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Ruiz","matricula":"%s"}
								""".formatted(matricula)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}