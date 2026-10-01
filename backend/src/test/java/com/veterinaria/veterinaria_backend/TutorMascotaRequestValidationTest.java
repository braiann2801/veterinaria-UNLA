package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;

/**
 * TASK-002: valida las reglas de Bean Validation declaradas en los RequestDTO.
 * No necesita contexto de Spring, corre la capa de validacion de forma aislada.
 */
class TutorMascotaRequestValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		if (factory != null) {
			factory.close();
		}
	}

	@Test
	@DisplayName("Rechaza un tutor sin nombre, sin apellido y con DNI invalido")
	void tutorInvalidoEsRechazado() {
		TutorRequestDTO dto = new TutorRequestDTO("  ", "", "abc", "123", "no-es-mail", null, null);

		var violaciones = validator.validate(dto);

		assertThat(violaciones).extracting("message")
				.contains("El nombre del tutor es obligatorio",
						"El apellido del tutor es obligatorio",
						"El DNI debe contener entre 7 y 10 digitos",
						"El telefono debe contener entre 6 y 15 digitos",
						"El email no tiene un formato valido");
	}

	@Test
	@DisplayName("Exige el flag autorizadoRetiro explicito en cada vinculo (regla 2.4)")
	void vinculoSinFlagEsRechazado() {
		TutorRequestDTO dto = new TutorRequestDTO(
				"Ana", "Gomez", "20304050", "1133334444", "ana@correo.com", "Calle 123",
				List.of(new TutorRequestDTO.VinculoMascotaDTO(10L, null)));

		var violaciones = validator.validate(dto);

		assertThat(violaciones).extracting("message")
				.contains("Debe indicar explicitamente si el tutor esta autorizado a retirar");
	}

	@Test
	@DisplayName("Acepta un tutor valido con vinculos correctamente declarados")
	void tutorValidoEsAceptado() {
		TutorRequestDTO dto = new TutorRequestDTO(
				"Ana", "Gomez", "20304050", "1133334444", "ana@correo.com", "Calle 123",
				List.of(new TutorRequestDTO.VinculoMascotaDTO(10L, true)));

		assertThat(validator.validate(dto)).isEmpty();
	}

	@Test
	@DisplayName("Rechaza una mascota sin especie, con peso no positivo y fecha futura")
	void mascotaInvalidaEsRechazada() {
		MascotaRequestDTO dto = new MascotaRequestDTO(
				"Firulais", null, null, "Hermafrodita", LocalDate.now().plusDays(1),
				BigDecimal.ZERO, "pesado", null, null, null);

		var violaciones = validator.validate(dto);

		assertThat(violaciones).extracting("message")
				.contains("La especie es obligatoria",
						"El sexo debe ser Macho o Hembra",
						"La fecha de nacimiento no puede ser futura",
						"El peso debe ser mayor a 0",
						"La conducta debe ser normal, gruñe, muerde o miedoso");
	}

	@Test
	@DisplayName("Acepta una mascota valida con su lista de tutores")
	void mascotaValidaEsAceptada() {
		MascotaRequestDTO dto = new MascotaRequestDTO(
				"Firulais", MascotaRequestDTO.EspecieRequestDTO.CANINO, "Mestizo", "Macho",
				LocalDate.of(2020, 5, 1), new BigDecimal("12.50"), "normal", "CHIP-001", null,
				List.of(new MascotaRequestDTO.VinculoTutorDTO(1L, true)));

		assertThat(validator.validate(dto)).isEmpty();
	}
}