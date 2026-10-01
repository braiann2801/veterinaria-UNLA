package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import com.veterinaria.veterinaria_backend.repository.PagoRepository;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-005: caja multipago (AGENTS.md 2.5).
 *
 * <p>Una comanda admite pagos acumulados de cuantos medios haga falta hasta
 * quedar en cero. El caso de referencia es el que pidio el dominio: una comanda
 * de $10.000 saldada con $4.000 en efectivo y $6.000 por transferencia.</p>
 *
 * <p>Lo que mas importa verificar aca no es que la suma final sea correcta, sino
 * que <b>los pagos que no deben aplicarse no quedan aplicados</b>: una caja que
 * descuadra descuenta plata de mas y nadie se entera hasta el cierre del mes.
 * Esa mitad del contrato vive en {@link RollbackTransaccionalTest}, porque
 * necesita una transaccion real para observar el rollback.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CajaMultipagoTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	@Autowired
	private PagoRepository pagoRepository;

	// ------------------------------------------------ el caso de referencia

	@Test
	@DisplayName("Comanda de 10.000 saldada con 4.000 efectivo + 6.000 transferencia queda PAGADA")
	void multipagoSaldadaQuedaPagada() throws Exception {
		long tutorId = crearTutor();
		long comandaId = emitirComanda("GUARDERIA", 10000, tutorId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[
								  {"metodoPago":"EFECTIVO","monto":4000},
								  {"metodoPago":"TRANSFERENCIA","monto":6000}
								]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"))
				.andExpect(jsonPath("$.montoTotal").value(10000.00))
				.andExpect(jsonPath("$.saldoPendiente").value(0.00))
				.andExpect(jsonPath("$.totalPagado").value(10000.00))
				// Los dos medios quedan registrados por separado: el arqueo de
				// boveda necesita saber cuanto efectivo y cuanto transfiriio.
				.andExpect(jsonPath("$.pagos", org.hamcrest.Matchers.hasSize(2)))
				.andExpect(jsonPath("$.pagos[0].metodoPago").value("EFECTIVO"))
				.andExpect(jsonPath("$.pagos[0].monto").value(4000.00))
				.andExpect(jsonPath("$.pagos[0].id").isNumber())
				.andExpect(jsonPath("$.pagos[1].metodoPago").value("TRANSFERENCIA"))
				.andExpect(jsonPath("$.pagos[1].monto").value(6000.00))
				.andExpect(jsonPath("$.pagos[1].id").isNumber());

		ComandaCobro comanda = comandaRepository.findById(comandaId).orElseThrow();
		assertThat(comanda.getEstado()).isEqualTo(ComandaCobro.EstadoComanda.PAGADA);
		assertThat(comanda.getSaldoPendiente()).isEqualByComparingTo("0.00");
		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId)).hasSize(2);
	}

	@Test
	@DisplayName("Pagos sucesivos en llamadas separadas acumulan y dejan PARCIAL en el medio")
	void pagosSucesivosAcumulan() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 10000, crearTutor());

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":4000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"))
				.andExpect(jsonPath("$.saldoPendiente").value(6000.00))
				.andExpect(jsonPath("$.totalPagado").value(4000.00));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"TRANSFERENCIA","monto":6000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"))
				.andExpect(jsonPath("$.saldoPendiente").value(0.00))
				.andExpect(jsonPath("$.totalPagado").value(10000.00));

		mockMvc.perform(get("/api/v1/caja/pagos/{id}", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
	}

	@Test
	@DisplayName("La seña se imputa a la comanda como un medio mas del arqueo")
	void senaEsUnMedioMas() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 10000, crearTutor());

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"SENA","monto":3000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"))
				.andExpect(jsonPath("$.saldoPendiente").value(7000.00));

		// La seña no es un pago menos: es un medio de pago mas, y el cierre de
		// caja la tiene que mostrar separada para saber cuanto se anticipo.
		mockMvc.perform(get("/api/v1/caja/cierre").param("fecha", LocalDate.now().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalesPorMetodo.SENA").value(3000.00));
	}

	// ------------------------------------------------ validacion de saldo

	@Test
	@DisplayName("Un pago mayor al saldo devuelve 400 y no alcanza a restar")
	void pagoMayorAlSaldoSeRechaza() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 10000, crearTutor());

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":10001}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", org.hamcrest.Matchers
						.containsString("excede el saldo")));

		ComandaCobro comanda = comandaRepository.findById(comandaId).orElseThrow();
		assertThat(comanda.getSaldoPendiente()).isEqualByComparingTo("10000.00");
		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId)).isEmpty();
	}

	// ------------------------------------------------ estados no cobrables

	@Test
	@DisplayName("Una comanda ya saldada responde 409 y no acepta mas pagos")
	void comandaPagadaRechazaNuevosPagos() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 5000, crearTutor());

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":100}]}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));

		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId)).hasSize(1);
	}

	@Test
	@DisplayName("Una comanda cancelada responde 409 y no admite pagos")
	void comandaCanceladaRechazaPagos() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 5000, crearTutor());

		mockMvc.perform(put("/api/v1/comandas/{id}/cancelar", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}]}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", org.hamcrest.Matchers
						.containsString("cancelada")));

		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId)).isEmpty();
	}

	@Test
	@DisplayName("Cancelar no borra el saldo ni revierte el estado de una comanda pagada")
	void cancelarNoBorraElSaldoNiRevierteElEstado() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 10000, crearTutor());

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":4000}]}"""))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/comandas/{id}/cancelar", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		ComandaCobro comanda = comandaRepository.findById(comandaId).orElseThrow();
		assertThat(comanda.getEstado())
				.as("cancelar es administrativo: no es lo mismo que no haber cobrado")
				.isEqualTo(ComandaCobro.EstadoComanda.CANCELADA);
		assertThat(comanda.getSaldoPendiente()).isEqualByComparingTo("6000.00");
	}

	// ------------------------------------------------ validacion y cierre

	@Test
	@DisplayName("Una lista de pagos vacia devuelve 400")
	void listaDePagosVaciaDevuelve400() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 5000, crearTutor());

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", org.hamcrest.Matchers.hasItem(
						org.hamcrest.Matchers.containsString("al menos un pago"))));
	}

	@Test
	@DisplayName("El cierre de caja arquea por metodo e incluye los metodos sin movimiento")
	void cierreDeCajaArqueaPorMetodo() throws Exception {
		long comandaId = emitirComanda("GUARDERIA", 10000, crearTutor());
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[
								  {"metodoPago":"EFECTIVO","monto":4000},
								  {"metodoPago":"TRANSFERENCIA","monto":6000}
								]}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/caja/cierre").param("fecha", LocalDate.now().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCobrado").value(10000.00))
				.andExpect(jsonPath("$.cantidadPagos").value(2))
				// Una sola comanda aunque haya dos medios de pago.
				.andExpect(jsonPath("$.cantidadComandas").value(1))
				.andExpect(jsonPath("$.totalesPorMetodo.EFECTIVO").value(4000.00))
				.andExpect(jsonPath("$.totalesPorMetodo.TRANSFERENCIA").value(6000.00))
				// Los metodos sin movimiento aparecen en cero: el mostrador
				// tiene que poder distinguir "no se movio" de "no se consulto".
				.andExpect(jsonPath("$.totalesPorMetodo.DEBITO").value(0.00))
				.andExpect(jsonPath("$.totalesPorMetodo.CREDITO").value(0.00));
	}

	@Test
	@DisplayName("Un dia sin cobros cierra en cero y no en null")
	void cierreDeJornadaSinCobros() throws Exception {
		mockMvc.perform(get("/api/v1/caja/cierre").param("fecha", "2026-01-02"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCobrado").value(0))
				.andExpect(jsonPath("$.cantidadPagos").value(0))
				.andExpect(jsonPath("$.cantidadComandas").value(0))
				.andExpect(jsonPath("$.totalesPorMetodo.EFECTIVO").value(0));
	}

	// ------------------------------------------------ helpers

	private long emitirComanda(String concepto, long monto, long tutorId) throws Exception {
		ponerTarifa(concepto, monto);

		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"%s","tutorId":%d,"descripcion":"Estadia de 3 dias"}
								""".formatted(concepto, tutorId)))
				.andExpect(status().isCreated())
				// El monto no lo manda el emisor: sale de la tarifa vigente.
				.andExpect(jsonPath("$.montoTotal").value(monto * 1.0))
				.andExpect(jsonPath("$.saldoPendiente").value(monto * 1.0))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
	}

	/**
	 * Cada test usa un DNI distinto porque la columna es unica y cada metodo
	 * corre en su propia transaccion con rollback.
	 */
	private long crearTutor() throws Exception {
		String dni = String.valueOf(32000000L
				+ Math.abs(Thread.currentThread().getId() % 100000L)
				+ (int) (System.nanoTime() % 1000L));
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Tutor","apellido":"Caja","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}