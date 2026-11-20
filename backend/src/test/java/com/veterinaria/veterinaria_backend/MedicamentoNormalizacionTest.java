package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

/** Normalización de las claves de unicidad y valores por defecto del DTO de alta. */
class MedicamentoNormalizacionTest {

	@Test
	@DisplayName("La clave ignora mayúsculas, acentos, guiones y espacios repetidos")
	void claveNormalizada() {
		assertThat(NormalizadorAr.clave("  Amoxicilina 500mg "))
				.isEqualTo(NormalizadorAr.clave("AMOXICILINA-500 MG"))
				.isEqualTo("amoxicilina 500 mg");
		assertThat(NormalizadorAr.clave(null)).isEmpty();
	}

	@Test
	@DisplayName("El alta recorta el texto, calcula las claves y defaulta stocks y precio en cero")
	void altaNormalizada() {
		MedicamentoRequestDTO dto = new MedicamentoRequestDTO(
				"  Amoxicilina  500mg  ", "  Comprimidos ", "  amoxicilina ", "  Laboratorio X ", null, null, null);

		assertThat(dto.nombre()).isEqualTo("Amoxicilina 500mg");
		assertThat(dto.nombreNormalizado()).isEqualTo("amoxicilina 500 mg");
		assertThat(dto.presentacionNormalizada()).isEqualTo("comprimidos");
		assertThat(dto.stockInicial()).isZero();
		assertThat(dto.stockMinimo()).isZero();
		assertThat(dto.precio()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("La observación vacía del movimiento se normaliza a null")
	void observacionVacia() {
		assertThat(new AgregarStockRequestDTO(5, "   ").observacion()).isNull();
	}
}
