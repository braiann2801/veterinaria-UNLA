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

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Pago;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.PagoRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Balance por periodo: el cierre de caja deja de ser solo la jornada de hoy.
 *
 * <p>Se verifican las reglas que hacen confiable el numero, no el transporte:</p>
 *
 * <p><b>Suma del rango.</b> Un periodo suma los pagos de todos sus dias y deja
 * afuera los de antes y despues. Con un rango cerrado por error, el ultimo dia
 * del periodo se contaria dos veces al mirar dos periodos consecutivos.</p>
 *
 * <p><b>Cobrado no es facturado.</b> Cobrado se fecha por {@code pago.fechaHora}
 * y facturado por {@code comanda.fechaEmision}. Un pago de hoy puede saldar una
 * comanda de la semana pasada, asi que los dos numeros pueden diferir sin que
 * haya un error: es la diferencia que el Balance necesita mostrar.</p>
 *
 * <p><b>Serie completa.</b> La respuesta trae un punto por dia, con cero en los
 * dias sin cobros. Si la base devolviera solo los dias con actividad, la vista
 * tendria que reconstruir el calendario por su cuenta.</p>
 */
@SpringBootTest
// La API exige login: estos tests prueban reglas de negocio, no permisos.
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class BalancePorPeriodoTest {

	/** Periodo fijo en el pasado, para no depender del dia en que corre la suite. */
	private static final String DIA_UNO = "2026-03-10";
	private static final String DIA_DOS = "2026-03-12";
	private static final String DIA_TRES = "2026-03-14";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PagoRepository pagoRepository;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	@Test
	@DisplayName("El periodo suma los pagos de todos sus dias y arma la serie diaria")
	void sumaElRangoYSeriaDiaria() throws Exception {
		long comandaUno = emitir("GUARDERIA", 5000);
		long comandaDos = emitir("CONSULTA_CLINICA", 3000);
		moverPago(pagar(comandaUno, "EFECTIVO", 5000), DIA_UNO + "T10:00:00");
		moverPago(pagar(comandaDos, "TRANSFERENCIA", 3000), DIA_DOS + "T09:30:00");

		JsonNode cierre = cierre(DIA_UNO, DIA_DOS);

		assertThat(cierre.get("desde").asString()).isEqualTo(DIA_UNO);
		assertThat(cierre.get("hasta").asString()).isEqualTo(DIA_DOS);
		assertThat(cierre.get("dias").asInt()).isEqualTo(3);
		assertThat(cierre.get("totalCobrado").decimalValue()).isEqualByComparingTo("8000.00");
		assertThat(cierre.get("cantidadPagos").asInt()).isEqualTo(2);
		assertThat(cierre.get("cantidadComandas").asInt()).isEqualTo(2);

		JsonNode porDia = cierre.get("porDia");
		assertThat(porDia).hasSize(3);
		assertThat(porDia.get(0).get("fecha").asString()).isEqualTo(DIA_UNO);
		assertThat(porDia.get(0).get("totalCobrado").decimalValue()).isEqualByComparingTo("5000.00");
		assertThat(porDia.get(0).get("cantidadPagos").asInt()).isEqualTo(1);
		// El dia del medio no tuvo cobros: viaja en cero, no ausente.
		assertThat(porDia.get(1).get("fecha").asString()).isEqualTo("2026-03-11");
		assertThat(porDia.get(1).get("totalCobrado").decimalValue()).isEqualByComparingTo("0.00");
		assertThat(porDia.get(1).get("cantidadPagos").asInt()).isZero();
		assertThat(porDia.get(2).get("totalCobrado").decimalValue()).isEqualByComparingTo("3000.00");

		// El arqueo por medio tambien suma el rango, no solo el ultimo dia.
		assertThat(cierre.get("totalesPorMetodo").get("EFECTIVO").decimalValue())
				.isEqualByComparingTo("5000.00");
		assertThat(cierre.get("totalesPorMetodo").get("TRANSFERENCIA").decimalValue())
				.isEqualByComparingTo("3000.00");
		// Los cinco metodos viajan siempre: un cero es un dato, no un hueco.
		assertThat(cierre.get("totalesPorMetodo").get("CREDITO").decimalValue())
				.isEqualByComparingTo("0.00");

		assertThat(cierre.get("movimientos")).hasSize(2);
		assertThat(cierre.get("movimientos").get(0).get("fechaHora").asString()).startsWith(DIA_UNO);
		assertThat(cierre.get("movimientos").get(1).get("fechaHora").asString()).startsWith(DIA_DOS);
	}

	@Test
	@DisplayName("El rango es inclusivo en los dos extremos y excluye lo de afuera")
	void rangoInclusivoYExcluyente() throws Exception {
		long dentroDelPrimero = emitir("GUARDERIA", 4000);
		long enElUltimo = emitir("CONSULTA_CLINICA", 2000);
		long antes = emitir("GUARDERIA", 1000);
		long despues = emitir("GUARDERIA", 1000);
		moverPago(pagar(dentroDelPrimero, "EFECTIVO", 4000), DIA_UNO + "T00:00:00");
		moverPago(pagar(enElUltimo, "EFECTIVO", 2000), DIA_DOS + "T23:59:59");
		moverPago(pagar(antes, "EFECTIVO", 1000), "2026-03-09T23:59:59");
		moverPago(pagar(despues, "EFECTIVO", 1000), "2026-03-13T00:00:00");

		JsonNode cierre = cierre(DIA_UNO, DIA_DOS);

		assertThat(cierre.get("totalCobrado").decimalValue()).isEqualByComparingTo("6000.00");
		assertThat(cierre.get("movimientos")).hasSize(2);
		assertThat(cierre.get("porDia")).hasSize(3);
	}

	@Test
	@DisplayName("Facturado y saldo pendiente se filtran por la emision de la comanda")
	void facturadoYSaldoPendientePorEmision() throws Exception {
		long comandaEnPeriodo = emitir("GUARDERIA", 5000);
		moverComanda(comandaEnPeriodo, DIA_UNO + "T08:00:00");
		moverPago(pagar(comandaEnPeriodo, "EFECTIVO", 2000), DIA_UNO + "T11:00:00");

		// Emitida antes del periodo: no cuenta ni en facturado ni en saldo pendiente.
		long comandaVieja = emitir("GUARDERIA", 9000);
		moverComanda(comandaVieja, "2026-02-01T08:00:00");

		JsonNode cierre = cierre(DIA_UNO, DIA_DOS);

		assertThat(cierre.get("totalCobrado").decimalValue()).isEqualByComparingTo("2000.00");
		assertThat(cierre.get("totalFacturado").decimalValue()).isEqualByComparingTo("5000.00");
		assertThat(cierre.get("saldosPendientes").decimalValue()).isEqualByComparingTo("3000.00");
	}

	@Test
	@DisplayName("Un periodo sin actividad responde cero, no error")
	void periodoSinActividad() throws Exception {
		JsonNode cierre = cierre("2026-01-01", "2026-01-03");

		assertThat(cierre.get("totalCobrado").decimalValue()).isEqualByComparingTo("0.00");
		assertThat(cierre.get("totalFacturado").decimalValue()).isEqualByComparingTo("0.00");
		assertThat(cierre.get("saldosPendientes").decimalValue()).isEqualByComparingTo("0.00");
		assertThat(cierre.get("cantidadPagos").asInt()).isZero();
		assertThat(cierre.get("porDia")).hasSize(3);
		assertThat(cierre.get("movimientos")).isEmpty();
	}

	@Test
	@DisplayName("Un solo extremo del periodo es 400, no un cierre del dia de ese extremo")
	void unSoloExtremoEs400() throws Exception {
		mockMvc.perform(get("/api/v1/caja/cierre").param("desde", DIA_UNO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("fecha inicial y la final")));

		mockMvc.perform(get("/api/v1/caja/cierre").param("hasta", DIA_DOS))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("fecha inicial y la final")));
	}

	@Test
	@DisplayName("Un periodo invertido es 400")
	void periodoInvertidoEs400() throws Exception {
		mockMvc.perform(get("/api/v1/caja/cierre").param("desde", DIA_DOS).param("hasta", DIA_UNO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("no puede ser anterior")));
	}

	@Test
	@DisplayName("Un periodo de mas de un anio es 400")
	void periodoDemasiadoLargoEs400() throws Exception {
		mockMvc.perform(get("/api/v1/caja/cierre")
						.param("desde", "2024-01-01").param("hasta", "2025-12-31"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("periodo maximo")));
	}

	@Test
	@DisplayName("Mezclar fecha con desde/hasta es 400: no hay dos cierres en una consulta")
	void fechaYPeriodoJuntosEs400() throws Exception {
		mockMvc.perform(get("/api/v1/caja/cierre")
						.param("fecha", DIA_UNO)
						.param("desde", DIA_UNO)
						.param("hasta", DIA_DOS))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("no las dos cosas")));
	}

	@Test
	@DisplayName("?fecha= sigue siendo el cierre de una jornada")
	void fechaSigueSiendoUnaJornada() throws Exception {
		long comanda = emitir("GUARDERIA", 7000);
		moverPago(pagar(comanda, "EFECTIVO", 7000), DIA_UNO + "T12:00:00");

		JsonNode cierre = objectMapper.readTree(mockMvc.perform(get("/api/v1/caja/cierre")
						.param("fecha", DIA_UNO))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		assertThat(cierre.get("desde").asString()).isEqualTo(DIA_UNO);
		assertThat(cierre.get("hasta").asString()).isEqualTo(DIA_UNO);
		assertThat(cierre.get("dias").asInt()).isEqualTo(1);
		assertThat(cierre.get("totalCobrado").decimalValue()).isEqualByComparingTo("7000.00");
		assertThat(cierre.get("porDia")).hasSize(1);
		assertThat(cierre.get("movimientos")).hasSize(1);
	}

	@Test
	@DisplayName("Un anio completo entra como periodo valido")
	void anioCompletoEsValido() throws Exception {
		JsonNode cierre = cierre("2025-01-01", "2025-12-31");

		assertThat(cierre.get("dias").asInt()).isEqualTo(365);
		assertThat(cierre.get("porDia")).hasSize(365);
	}

	// ------------------------------------------------------------- helpers

	private JsonNode cierre(String desde, String hasta) throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/api/v1/caja/cierre")
						.param("desde", desde)
						.param("hasta", hasta))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private long emitir(String concepto, long monto) throws Exception {
		ponerTarifa(concepto, monto);
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"concepto\":\"%s\",\"tutorId\":%d}"
								.formatted(concepto, crearTutor())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor() throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Balance","apellido":"Periodo","dni":"%s"}
								""".formatted(20000000L + Math.abs(System.nanoTime() % 9000000L))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long pagar(long comandaId, String metodo, long monto) throws Exception {
		String json = mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"%s","monto":%d}]}
								""".formatted(metodo, monto)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("pagos").get(0).get("id").asLong();
	}

	/**
	 * Mueve el pago al dia que necesita el test.
	 *
	 * <p>La API registra {@code fechaHora = now()}, igual que en la operacion
	 * real. Para probar un periodo del pasado se toca la fila por repositorio,
	 * no se agrega un parametro de fecha al cobro: seria una puerta para falsear
	 * el cierre de caja.</p>
	 */
	private void moverPago(long pagoId, String fechaHora) {
		Pago pago = pagoRepository.findById(pagoId).orElseThrow();
		pago.setFechaHora(LocalDateTime.parse(fechaHora));
		pagoRepository.save(pago);
	}

	private void moverComanda(long comandaId, String fechaHora) {
		ComandaCobro comanda = comandaRepository.findById(comandaId).orElseThrow();
		comanda.setFechaEmision(LocalDateTime.parse(fechaHora));
		comandaRepository.save(comanda);
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
	}
}
