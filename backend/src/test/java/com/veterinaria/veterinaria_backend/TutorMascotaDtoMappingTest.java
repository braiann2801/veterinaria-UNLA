package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Spring Boot 4.x usa Jackson 3 (tools.jackson), no el paquete com.fasterxml anterior.
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;

/**
 * TASK-002: verifica que la capa DTO rompe el ciclo de serializacion de la
 * relacion bidireccional Tutor-Mascota.
 *
 * <p>La prueba construye un grafo de objetos con ciclo real en memoria (no
 * persistido) y comprueba dos cosas: que Jackson falla al serializar la entidad
 * cruda y que el mismo grafo se serializa sin problemas al pasar por
 * {@link EntityMapper}.</p>
 */
@SpringBootTest
@Transactional
class TutorMascotaDtoMappingTest {

	@Autowired
	private EntityMapper mapper;

	@Test
	@DisplayName("Jackson no puede serializar la entidad cruda por el ciclo Tutor-Mascota")
	void entidadCrudaProvocaCiclo() {
		Tutor tutor = tutorConMascota();

		ObjectMapper jackson = new ObjectMapper();

		// Sin DTOs, Jackson entra en la relacion bidireccional y aborta el ciclo.
		assertThatThrownBy(() -> jackson.writeValueAsString(tutor))
				.isInstanceOf(Throwable.class);
	}

	@Test
	@DisplayName("El DTO del tutor se serializa completo sin recursin ni referencia de vuelta")
	void tutorResponseDtoSeSerializaSinCiclo() throws Exception {
		Tutor tutor = tutorConMascota();

		TutorResponseDTO dto = mapper.toResponse(tutor);
		ObjectMapper jackson = new ObjectMapper();
		String json = jackson.writeValueAsString(dto);

		assertThat(json).contains("\"dni\":\"20304050\"");
		assertThat(json).contains("\"autorizadoRetiro\":true");
		assertThat(json).contains("\"nombre\":\"Firulais\"");
		// "mascotas" aparece una sola vez (el campo del tutor). Si el resumen
		// arrastrara la coleccion de vuelta, se repetiria y habria ciclo.
		assertThat(countOcurrences(json, "\"mascotas\"")).isEqualTo(1);
		// Si hubiera ciclo, la cadena crecera de forma patologica; este limite la detecta.
		assertThat(json.length()).isLessThan(1000);
	}

	@Test
	@DisplayName("El DTO de la mascota expone los tutores con su flag de retiro")
	void mascotaResponseDtoExponeTutoresConFlag() throws Exception {
		Tutor tutor = tutorConMascota();
		Mascota mascota = tutor.getVinculos().get(0).getMascota();

		MascotaResponseDTO dto = mapper.toResponse(mascota);
		String json = new ObjectMapper().writeValueAsString(dto);

		assertThat(dto.tutores()).hasSize(1);
		assertThat(dto.tutores().get(0).autorizadoRetiro()).isTrue();
		assertThat(dto.tutores().get(0).nombreCompleto()).isEqualTo("Ana Gomez");
		// Sin referencia de vuelta a la mascota.
		assertThat(json).doesNotContain("\"mascotas\"");
	}

	@Test
	@DisplayName("Las listas nulas se normalizan a vacias para no propagar nulls al frontend")
	void listasNulasSeNormalizan() {
		Tutor tutor = new Tutor(null, "Solo", "Nombre", "11111111", null, null, null, null);
		tutor.setVinculos(null);

		TutorResponseDTO dto = mapper.toResponse(tutor);

		assertThat(dto.mascotas()).isNotNull().isEmpty();
		assertThat(dto.nombreCompleto()).isEqualTo("Solo Nombre");
		assertThat(mapper.toTutorResponseList(null)).isEmpty();
		assertThat(mapper.toMascotaResponseList(null)).isEmpty();
		assertThat(mapper.toVinculoResponseList(null)).isEmpty();
	}

	/** Cuenta ocurrencias de un token sin depender de commons-lang. */
	private static int countOcurrences(String texto, String token) {
		int total = 0;
		int indice = texto.indexOf(token);
		while (indice >= 0) {
			total++;
			indice = texto.indexOf(token, indice + token.length());
		}
		return total;
	}

	/**
	 * Grafo con ciclo bidireccional real:
	 * Tutor -> vinculos -> TutorMascota -> Mascota -> vinculos -> TutorMascota -> Tutor.
	 */
	private Tutor tutorConMascota() {
		Tutor tutor = new Tutor();
		tutor.setId(1L);
		tutor.setNombre("Ana");
		tutor.setApellido("Gomez");
		tutor.setDni("20304050");
		tutor.setTelefono("1133334444");
		tutor.setEmail("ana@correo.com");
		tutor.setDireccion("Calle 123");

		Mascota mascota = new Mascota();
		mascota.setId(10L);
		mascota.setNombre("Firulais");
		mascota.setEspecie(Mascota.Especie.CANINO);
		mascota.setRaza("Mestizo");
		mascota.setSexo("Macho");
		mascota.setFechaNacimiento(LocalDate.of(2020, 5, 1));
		mascota.setPesoKg(new BigDecimal("12.50"));
		mascota.setConducta(Mascota.Conducta.NORMAL);
		mascota.setChip("CHIP-001");

		List<TutorMascota> vinculosTutor = new ArrayList<>();
		List<TutorMascota> vinculosMascota = new ArrayList<>();

		TutorMascota vinculo = new TutorMascota(tutor, mascota, true);
		vinculosTutor.add(vinculo);
		vinculosMascota.add(vinculo);

		tutor.setVinculos(vinculosTutor);
		mascota.setVinculos(vinculosMascota);

		return tutor;
	}
}