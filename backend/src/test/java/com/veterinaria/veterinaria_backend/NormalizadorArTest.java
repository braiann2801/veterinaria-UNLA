package com.veterinaria.veterinaria_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

/** Reglas de normalizacion de datos personales segun el uso argentino. */
class NormalizadorArTest {

	@Test
	@DisplayName("Nombre propio: mayuscula inicial por palabra y particulas en minuscula")
	void nombrePropio() {
		assertEquals("Juan Carlos", NormalizadorAr.nombrePropio("juan carlos"));
		assertEquals("Juan Perez", NormalizadorAr.nombrePropio("JUAN PEREZ"));
		assertEquals("María de los Ángeles", NormalizadorAr.nombrePropio("maría de los ángeles"));
		assertEquals("De la Fuente", NormalizadorAr.nombrePropio("de la fuente"));
		assertEquals("María-José", NormalizadorAr.nombrePropio("maría-josé"));
		assertEquals("O'Connor", NormalizadorAr.nombrePropio("o'connor"));
		assertEquals("Ana Paz", NormalizadorAr.nombrePropio("  ana   paz "));
		assertEquals("", NormalizadorAr.nombrePropio("   "));
		assertNull(NormalizadorAr.nombrePropio(null));
	}

	@Test
	@DisplayName("DNI: se quitan puntos, espacios y guiones; las letras quedan")
	void dni() {
		assertEquals("30123456", NormalizadorAr.dni("30.123.456"));
		assertEquals("30123456", NormalizadorAr.dni(" 30 123 456 "));
		assertEquals("30123456", NormalizadorAr.dni("30-123-456"));
		assertEquals("abc", NormalizadorAr.dni("abc"));
		assertNull(NormalizadorAr.dni("  "));
		assertNull(NormalizadorAr.dni(null));
	}

	@Test
	@DisplayName("Telefono: numero nacional de 10 digitos sin +54, 9, 0 ni separadores")
	void telefono() {
		assertEquals("1123456789", NormalizadorAr.telefono("+54 9 11 2345-6789"));
		assertEquals("1123456789", NormalizadorAr.telefono("+54 11 2345 6789"));
		assertEquals("1123456789", NormalizadorAr.telefono("0054 9 11 2345 6789"));
		assertEquals("1123456789", NormalizadorAr.telefono("011 2345-6789"));
		assertEquals("3514567890", NormalizadorAr.telefono("(0351) 456-7890"));
		assertEquals("1123456789", NormalizadorAr.telefono("11.2345.6789"));
		// El 15 no se puede quitar sin la tabla de codigos de area: queda y la validacion lo rechaza.
		assertEquals("111523456789", NormalizadorAr.telefono("11 15 2345 6789"));
		assertNull(NormalizadorAr.telefono(" "));
		assertNull(NormalizadorAr.telefono(null));
	}

	@Test
	@DisplayName("Matricula: mayusculas, sin puntos y con espacio entre prefijo y numero")
	void matricula() {
		assertEquals("MP 1234", NormalizadorAr.matricula("m.p. 1234"));
		assertEquals("MP 1234", NormalizadorAr.matricula("mp1234"));
		assertEquals("MP 1234", NormalizadorAr.matricula(" MP   1234 "));
		assertEquals("MP-ABM-9", NormalizadorAr.matricula(" mp-abm-9 "));
		assertEquals("MAT-E4", NormalizadorAr.matricula("MAT-E4"));
		assertEquals("1234", NormalizadorAr.matricula("1234"));
		assertNull(NormalizadorAr.matricula("  "));
		assertNull(NormalizadorAr.matricula(null));
	}

	@Test
	@DisplayName("Email en minuscula y texto libre sin espacios de mas")
	void emailYTexto() {
		assertEquals("ana@mail.com", NormalizadorAr.email(" Ana@Mail.COM "));
		assertNull(NormalizadorAr.email(" "));
		assertEquals("Av. Siempre Viva 742", NormalizadorAr.texto("  Av.  Siempre   Viva 742 "));
		assertNull(NormalizadorAr.texto(""));
	}
}
