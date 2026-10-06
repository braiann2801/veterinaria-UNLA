package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.veterinaria.veterinaria_backend.config.DataInitializer;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Tarifa;
import com.veterinaria.veterinaria_backend.repository.TarifaRepository;

/**
 * La semilla de tarifas (riesgo 6 de PROGRESS.md).
 *
 * <p>Se invoca {@link DataInitializer#sembrar()} directamente en vez de confiar
 * en que corrio al levantar el contexto: el contexto de test es compartido y
 * otra clase (RollbackTransaccionalTest) borra la tabla en su @AfterEach, asi
 * que un test que solo mirara "hay 3 tarifas despues de arrancar" seria
 * dependiente del orden de ejecucion.</p>
 */
@SpringBootTest
class DataInitializerTest {

	@Autowired
	private DataInitializer dataInitializer;

	@Autowired
	private TarifaRepository tarifaRepository;

	@BeforeEach
	void limpiar() {
		tarifaRepository.deleteAll();
	}

	@Test
	@DisplayName("Sobre una tabla vacia siembra los tres conceptos del enum compartido")
	void siembraCuandoEstaVacia() {
		int sembradas = dataInitializer.sembrar();

		assertThat(sembradas).isEqualTo(3);
		assertThat(tarifaRepository.count()).isEqualTo(3);
		assertThat(tarifaRepository.findByConcepto(ComandaCobro.Concepto.CONSULTA_CLINICA))
				.as("consulta clinica")
				.isPresent()
				.get()
				.satisfies(t -> {
					assertThat(t.isVigente()).isTrue();
					assertThat(t.getMonto()).isGreaterThan(BigDecimal.ZERO);
				});
		assertThat(tarifaRepository.findByConcepto(ComandaCobro.Concepto.GUARDERIA))
				.as("guarderia").isPresent();
		assertThat(tarifaRepository.findByConcepto(ComandaCobro.Concepto.OTRO))
				.as("concepto generico").isPresent();
	}

	@Test
	@DisplayName("No pisa un precio ya cargado: si hay tarifas, no siembra")
	void noPisaPreciosExistentes() {
		Tarifa existente = new Tarifa();
		existente.setConcepto(ComandaCobro.Concepto.CONSULTA_CLINICA);
		existente.setMonto(new BigDecimal("12345.00"));
		existente.setVigente(true);
		tarifaRepository.save(existente);

		int sembradas = dataInitializer.sembrar();

		assertThat(sembradas).isZero();
		assertThat(tarifaRepository.count()).isEqualTo(1);
		assertThat(tarifaRepository.findByConcepto(ComandaCobro.Concepto.CONSULTA_CLINICA))
				.isPresent()
				.get()
				.satisfies(t -> assertThat(t.getMonto()).isEqualByComparingTo("12345.00"));
	}

	@Test
	@DisplayName("Es idempotente: sembrar dos veces deja 3, no 6")
	void esIdempotente() {
		assertThat(dataInitializer.sembrar()).isEqualTo(3);
		assertThat(dataInitializer.sembrar()).isZero();
		assertThat(tarifaRepository.count()).isEqualTo(3);
	}
}