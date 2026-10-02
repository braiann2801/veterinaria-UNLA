package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * TASK-007 (FASE 1): la mascota creada desde la API queda en {@code mascota} y su
 * vinculo en {@code tutor_mascota}.
 *
 * <p>Se verifica con las dos rutas que puede tomar el alta desde la UI, y en
 * ambos casos se releen las tablas por repositorio. Un 201 con el JSON correcto
 * no alcanza como prueba de persistencia: la respuesta se arma de la entidad en
 * memoria, asi que pasaria igual si el INSERT no llegara a commitear.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MascotaPersistenciaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MascotaRepository mascotaRepository;

	@Autowired
	private TutorMascotaRepository tutorMascotaRepository;

	@Test
	@DisplayName("Ruta 1: POST /mascotas con tutores anidados escribe mascota y tutor_mascota")
	void crearMascotaConTutorAnidado() throws Exception {
		long tutorId = crearTutor("40111222");

		mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Nube","especie":"CANINO","raza":"Caniche",
								 "sexo":"Hembra","pesoKg":6.20,"conducta":"miedoso",
								 "tutores":[{"tutorId":%d,"autorizadoRetiro":true}]}
								""".formatted(tutorId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value("Nube"))
				.andExpect(jsonPath("$.conducta").value("miedoso"))
				.andExpect(jsonPath("$.tutores", Matchers.hasSize(1)))
				.andExpect(jsonPath("$.tutores[0].id").value((int) tutorId))
				.andExpect(jsonPath("$.tutores[0].autorizadoRetiro").value(true));

		long mascotaId = ultimaMascota("Nube");

		// La fila de `mascota` existe con todos los campos del formulario.
		var mascota = mascotaRepository.findById(mascotaId).orElseThrow();
		assertThat(mascota.getNombre()).isEqualTo("Nube");
		assertThat(mascota.getEspecie().name()).isEqualTo("CANINO");
		assertThat(mascota.getRaza()).isEqualTo("Caniche");
		assertThat(mascota.getSexo()).isEqualTo("Hembra");
		assertThat(mascota.getPesoKg()).isEqualByComparingTo("6.20");
		assertThat(mascota.getConducta().name()).isEqualTo("MIEDOSO");

		// Y la tupla N:M quedo escrita con el flag de la regla 2.4.
		var vinculo = tutorMascotaRepository.findByTutorIdAndMascotaId(tutorId, mascotaId)
				.orElseThrow(() -> new AssertionError("No se escribio la tupla en tutor_mascota"));
		assertThat(vinculo.isAutorizadoRetiro()).isTrue();
		assertThat(vinculo.getVinculadoDesde()).isNotNull();
	}

	@Test
	@DisplayName("Ruta 2: POST /mascotas y despues POST /tutores/{id}/mascotas/{id}")
	void crearMascotaYVincularDespues() throws Exception {
		long tutorId = crearTutor("40222333");

		// Paso 1: la mascota nace sin tutores.
		long mascotaId = crearMascota("Tizón", "Mestizo", "normal");
		assertThat(tutorMascotaRepository.findByMascotaId(mascotaId)).isEmpty();

		// Paso 2: el vinculo se agrega despues, con el flag explicito.
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false",
						tutorId, mascotaId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tutores[0].autorizadoRetiro").value(false));

		// La tupla existe y es de un cotutor, no de un tutor con retiro.
		var vinculo = tutorMascotaRepository.findByTutorIdAndMascotaId(tutorId, mascotaId)
				.orElseThrow(() -> new AssertionError("No se escribio la tupla en tutor_mascota"));
		assertThat(vinculo.isAutorizadoRetiro()).isFalse();

		// El listado de mascotas lo refleja al releer de la base.
		mockMvc.perform(get("/api/v1/mascotas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre", Matchers.hasItem("Tizón")))
				.andExpect(jsonPath("$[?(@.nombre=='Tizón')].tutores", Matchers.hasSize(1)));
	}

	@Test
	@DisplayName("Dos tutores sobre la misma mascota generan dos filas, con flags distintos")
	void dosTutoresConFlagsDistintos() throws Exception {
		long tutorA = crearTutor("40333444");
		long tutorB = crearTutor("40444555");
		long mascotaId = crearMascota("Bolt", "Border Collie", "gruñe");

		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorA, mascotaId)).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false",
				tutorB, mascotaId)).andExpect(status().isCreated());

		assertThat(tutorMascotaRepository.findByMascotaId(mascotaId)).hasSize(2);
		assertThat(tutorMascotaRepository.findByMascotaIdAndAutorizadoRetiroTrue(mascotaId))
				.hasSize(1);
	}

	@Test
	@DisplayName("Vincular contra un tutor inexistente no deja la mascota a medias")
	void tutorInexistenteDevuelve404() throws Exception {
		long mascotaId = crearMascota("Roco", "Mestizo", "normal");

		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				777777, mascotaId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("No existe un tutor")));

		// La mascota existe, pero sin ninguna tupla: no se creo un vinculo huerfano.
		assertThat(tutorMascotaRepository.findByMascotaId(mascotaId)).isEmpty();
	}

	@Test
	@DisplayName("El alta con especie en minuscula devuelve 400 en vez de guardarla mal")
	void especieInvalidaDevuelve400() throws Exception {
		mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Corto","especie":"canino"}
								"""))
				.andExpect(status().isBadRequest());

		assertThat(mascotaRepository.findAll())
				.noneMatch(m -> "Corto".equals(m.getNombre()));
	}

	// ------------------------------------------------------------- helpers

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Tutor","apellido":"Persistencia","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascota(String nombre, String raza, String conducta) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","raza":"%s","conducta":"%s"}
								""".formatted(nombre, raza, conducta)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	/** Busca por nombre en vez de confiar en el id de la respuesta. */
	private long ultimaMascota(String nombre) {
		return mascotaRepository.findAll().stream()
				.filter(m -> nombre.equals(m.getNombre()))
				.map(Mascota::getId)
				.reduce((primero, ultimo) -> ultimo)
				.orElseThrow(() -> new AssertionError(
						"No se encontro la mascota '" + nombre + "' en la tabla mascota"));
	}
}
