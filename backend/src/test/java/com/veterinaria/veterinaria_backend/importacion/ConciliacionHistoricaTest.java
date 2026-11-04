package com.veterinaria.veterinaria_backend.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La conciliacion manual de cobros debe cuadrar con el texto de la planilla. */
class ConciliacionHistoricaTest {

	@Test
	@DisplayName("Consulta con saldo: total 37.000, pagado 27.000, debe 10.000")
	void consultaConSaldo() {
		ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.consulta(503);

		assertNotNull(conciliacion);
		assertEquals(0, new BigDecimal("37000.00").compareTo(conciliacion.total()));
		assertEquals(0, new BigDecimal("27000.00").compareTo(conciliacion.pagado()));
		assertEquals(0, new BigDecimal("10000.00").compareTo(conciliacion.saldo()));
	}

	@Test
	@DisplayName("Consulta con sena y transferencia: 30.000 + 35.000 saldan 65.000")
	void consultaConSena() {
		ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.consulta(504);

		assertNotNull(conciliacion);
		assertEquals(0, new BigDecimal("30000.00").compareTo(conciliacion.sena()));
		assertEquals(0, new BigDecimal("65000.00").compareTo(conciliacion.pagado()));
		assertEquals(0, BigDecimal.ZERO.compareTo(conciliacion.saldo()));
	}

	@Test
	@DisplayName("Guarderia con sena: 20.000 pagados, 25.000 pendientes")
	void guarderiaConSena() {
		ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.guarderia(101);

		assertNotNull(conciliacion);
		assertEquals(0, new BigDecimal("45000.00").compareTo(conciliacion.total()));
		assertEquals(0, new BigDecimal("20000.00").compareTo(conciliacion.sena()));
		assertEquals(0, new BigDecimal("25000.00").compareTo(conciliacion.saldo()));
	}

	@Test
	@DisplayName("Guarderia impaga: no tiene pagos y debe el total")
	void guarderiaImpaga() {
		ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.guarderia(107);

		assertNotNull(conciliacion);
		assertTrue(conciliacion.pagos().isEmpty());
		assertEquals(0, new BigDecimal("68000.00").compareTo(conciliacion.saldo()));
	}

	@Test
	@DisplayName("Todas las fichas conciliadas tienen saldo no negativo")
	void saldosNoNegativos() {
		for (int numero = 501; numero <= 510; numero++) {
			ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.consulta(numero);
			assertNotNull(conciliacion, "Falta la ficha " + numero);
			assertTrue(conciliacion.saldo().compareTo(BigDecimal.ZERO) >= 0,
					"Saldo negativo en la ficha " + numero);
		}
		for (int numero = 101; numero <= 110; numero++) {
			ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.guarderia(numero);
			assertNotNull(conciliacion, "Falta la reserva " + numero);
			assertTrue(conciliacion.saldo().compareTo(BigDecimal.ZERO) >= 0,
					"Saldo negativo en la reserva " + numero);
		}
	}

	@Test
	@DisplayName("Una ficha inexistente no devuelve conciliacion")
	void fichaInexistente() {
		assertNull(ConciliacionHistorica.consulta(999));
		assertNull(ConciliacionHistorica.guarderia(999));
	}
}
