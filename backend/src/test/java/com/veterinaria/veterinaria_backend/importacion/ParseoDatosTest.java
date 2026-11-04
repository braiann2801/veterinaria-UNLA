package com.veterinaria.veterinaria_backend.importacion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;

/** Parsers del Excel historico: fechas, montos, telefonos, edades y enums. */
class ParseoDatosTest {

	@Test
	@DisplayName("Fechas: ISO, dd/MM/yyyy, dd/MM/yy y texto en espanol")
	void fechas() {
		assertEquals(LocalDate.of(2026, 5, 10), ParseoDatos.fecha("10/05/2026"));
		assertEquals(LocalDate.of(2026, 5, 11), ParseoDatos.fecha("2026-05-11"));
		assertEquals(LocalDate.of(2026, 5, 12), ParseoDatos.fecha("12 de Mayo 2026"));
		assertEquals(LocalDate.of(2026, 5, 16), ParseoDatos.fecha("16/05/26"));
		assertEquals(LocalDate.of(2026, 5, 18), ParseoDatos.fecha("18 de mayo de 2026"));
		assertNull(ParseoDatos.fecha("sin fecha"));
	}

	@Test
	@DisplayName("Montos: simbolo, miles con punto y coma decimal")
	void montos() {
		assertEquals(0, new BigDecimal("18500.00").compareTo(ParseoDatos.dinero("$18.500")));
		assertEquals(0, new BigDecimal("24000.00").compareTo(ParseoDatos.dinero("24.000")));
		assertEquals(0, new BigDecimal("32000.00").compareTo(ParseoDatos.dinero("$ 32.000,00")));
		assertEquals(0, new BigDecimal("12000.00").compareTo(ParseoDatos.dinero("12000")));
		assertEquals(0, new BigDecimal("28.50").compareTo(ParseoDatos.dinero("28,5")));
		assertNull(ParseoDatos.dinero("sin monto"));
	}

	@Test
	@DisplayName("Peso: coma o punto decimal y sufijo kg")
	void pesos() {
		assertEquals(0, new BigDecimal("28.50").compareTo(ParseoDatos.peso("28,5")));
		assertEquals(0, new BigDecimal("12.20").compareTo(ParseoDatos.peso("12.2")));
		assertEquals(0, new BigDecimal("34.00").compareTo(ParseoDatos.peso("34,0 kg")));
		assertEquals(0, new BigDecimal("3.90").compareTo(ParseoDatos.peso("3.9 kg")));
	}

	@Test
	@DisplayName("Telefonos: +54, 0 de larga distancia, 15 de celular y area inferida")
	void telefonos() {
		assertEquals("1144558899", ParseoDatos.telefono("11 4455-8899", "0000000001"));
		assertEquals("1144558899", ParseoDatos.telefono("+5491144558899", "0000000001"));
		assertEquals("1133221100", ParseoDatos.telefono("+54 9 11 3322 1100", "0000000001"));
		assertEquals("1166772233", ParseoDatos.telefono("15-6677-2233", "0000000001"));
		assertEquals("3415443322", ParseoDatos.telefono("0341-155443322", "0000000001"));
		assertEquals("1148901122", ParseoDatos.telefono("011 4890-1122", "0000000001"));
		assertEquals("0000000001", ParseoDatos.telefono("Sin telefono (dejo mail)", "0000000001"));
	}

	@Test
	@DisplayName("Edad: anos, meses y 'anos y medio' a meses")
	void edades() {
		assertEquals(48, ParseoDatos.mesesDesdeEdad("4 años"));
		assertEquals(30, ParseoDatos.mesesDesdeEdad("2 años y medio"));
		assertEquals(6, ParseoDatos.mesesDesdeEdad("6 meses"));
		assertEquals(36, ParseoDatos.mesesDesdeEdad("3 años aprox"));
		assertEquals(132, ParseoDatos.mesesDesdeEdad("11 años"));
		assertEquals(48, ParseoDatos.mesesDesdeEdad("4"));
		assertNull(ParseoDatos.mesesDesdeEdad("no dice"));
	}

	@Test
	@DisplayName("Especie y raza a partir de Especie_Raza")
	void especieYRaza() {
		assertEquals(Mascota.Especie.CANINO, ParseoDatos.especie("Perro - Labrador"));
		assertEquals(Mascota.Especie.FELINO, ParseoDatos.especie("Gata Siamesa"));
		assertEquals(Mascota.Especie.CANINO, ParseoDatos.especie("Mestizo mediano"));
		assertEquals(Mascota.Especie.OTRO, ParseoDatos.especie("Loro"));

		assertEquals("Labrador", ParseoDatos.raza("Perro - Labrador", Mascota.Especie.CANINO));
		assertEquals("Comun", ParseoDatos.raza("Gato comun", Mascota.Especie.FELINO));
		assertEquals("Caniche Toy", ParseoDatos.raza("Perro caniche toy", Mascota.Especie.CANINO));
		assertNull(ParseoDatos.raza("canino", Mascota.Especie.CANINO));
	}

	@Test
	@DisplayName("Modalidad de guarderia al enum TipoEstadia")
	void modalidades() {
		assertEquals(ReservaGuarderia.TipoEstadia.NOCTURNA,
				ParseoDatos.modalidad("Guarderia Nocturna (3 noches)"));
		assertEquals(ReservaGuarderia.TipoEstadia.NOCTURNA,
				ParseoDatos.modalidad("Guarderia felina"));
		assertEquals(ReservaGuarderia.TipoEstadia.COMPLETA_24H,
				ParseoDatos.modalidad("Estadia semanal"));
		assertEquals(ReservaGuarderia.TipoEstadia.DIURNA,
				ParseoDatos.modalidad("Pase diario completo"));
	}

	@Test
	@DisplayName("Nombres: se quita el titulo y el ultimo token es el apellido")
	void nombres() {
		assertArrayEquals(new String[] { "Carlos", "Tevez" },
				ParseoDatos.nombreYApellido("Dr. Carlos Tevez"));
		assertArrayEquals(new String[] { "Valeria", "Gomez" },
				ParseoDatos.nombreYApellido("VALERIA GOMEZ"));
		assertArrayEquals(new String[] { "Laura", "" },
				ParseoDatos.nombreYApellido("Dra. Laura"));
		assertEquals("carlos tevez", ParseoDatos.claveNombre("Dr. Carlos Tevez"));
		assertEquals("carlos tevez", ParseoDatos.claveNombre("Carlos Tevez"));
	}

	@Test
	@DisplayName("Numero de ficha o reserva: se extraen los digitos")
	void numerosDeRegistro() {
		assertEquals(501, ParseoDatos.numeroRegistro("AT-501"));
		assertEquals(502, ParseoDatos.numeroRegistro("502"));
		assertEquals(101, ParseoDatos.numeroRegistro("RES-101"));
		assertNull(ParseoDatos.numeroRegistro("sin numero"));
	}
}
