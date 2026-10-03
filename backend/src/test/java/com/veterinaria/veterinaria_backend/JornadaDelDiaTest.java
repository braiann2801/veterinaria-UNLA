package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * TASK-009: la jornada operativa como fuente unica.
 *
 * <p>El checkout de egreso y la caja del dia muestran la misma lista de mascotas
 * y por eso consultan el mismo servicio. Lo que se verifica aca es justamente esa
 * regla compartida, mas los filtros que la UI necesita para no ensuciar la
 * pantalla.</p>
 *
 * <p>El caso que mas importa es el de los estados: un turno CANCELADO o una
 * reserva CANCELADA que aparecieran en la lista harian creer al mostrador que esa
 * persona vino hoy, y la caja le ofreceria cobrarle una consulta que nunca se
 * atendio.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JornadaDelDiaTest {

	/** Dia fijo y lejano: no choca con los turnos que deja el resto de la suite. */
	private static final LocalDate DIA = LocalDate.of(2026, 11, 9);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// ------------------------------------------------------- pacientes-hoy

	@Test
	@DisplayName("Sin fecha devuelve la jornada de hoy y no la de ayer")
	void sinFechaUsaHoy() throws Exception {
		// Se calculan desde LocalDate.now() y no con una fecha fija: el default a
		// hoy se compara contra el reloj de la JVM, que es el mismo que usa el
		// backend al emitir. Con una fecha constante el test solo probaria que el
		// endpoint devuelve algo, no que devuelve el dia correcto.
		LocalDate hoy = LocalDate.now();
		long mascotaDeHoy = crearMascota("Hoy");
		long mascotaDeAyer = crearMascota("Ayer");
		crearTurno(hoy.atTime(9, 0).toString(), mascotaDeHoy, crearProfesional());
		crearTurno(hoy.minusDays(1).atTime(9, 0).toString(), mascotaDeAyer, crearProfesional());

		ArrayNode pacientes = listarPacientes(null);

		// El default a hoy no es un detalle: el mostrador que abre el checkout esta
		// cerrando la jornada de hoy, no la de ayer.
		assertThat(ids(pacientes)).contains(mascotaDeHoy).doesNotContain(mascotaDeAyer);
	}

	@Test
	@DisplayName("Una mascota con turno y guarderia aparece una sola vez, con ambos motivos")
	void fusionaTurnoYGuarderia() throws Exception {
		long mascotaId = crearMascota("Bicho");
		crearTurno("2026-11-09T09:00:00", mascotaId, crearProfesional());
		crearReserva(mascotaId, "2026-11-09", "08:00", "18:00", "COMPLETA_24H");

		ArrayNode pacientes = listarPacientes(DIA.toString());

		assertThat(ids(pacientes)).containsExactly(mascotaId);
		assertThat(pacientes.get(0).get("motivos")).hasSize(2);
		assertThat(nombresDeMotivos(pacientes.get(0))).containsExactlyInAnyOrder("TURNO_CLINICO", "GUARDERIA");
		// El horario mas temprano es el de la guarderia, que ordena la jornada.
		assertThat(pacientes.get(0).get("horaIngreso").asText()).startsWith("08:00");
		assertThat(pacientes.get(0).get("cantidadTurnos").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("El turno CANCELADO no aparece en la jornada")
	void turnoCanceladoNoAparece() throws Exception {
		long mascotaId = crearMascota("SeCancelo");
		long turnoId = crearTurno("2026-11-09T09:00:00", mascotaId, crearProfesional());
		mockMvc.perform(put("/api/v1/turnos/{id}/cancelar", turnoId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));

		ArrayNode pacientes = listarPacientes(DIA.toString());

		assertThat(ids(pacientes)).doesNotContain(mascotaId);
	}

	@Test
	@DisplayName("La reserva CANCELADA no aparece en la jornada")
	void reservaCanceladaNoAparece() throws Exception {
		long mascotaId = crearMascota("NoEntro");
		long reservaId = crearReserva(mascotaId, DIA.toString(), "08:00", "18:00", "COMPLETA_24H");
		mockMvc.perform(put("/api/v1/guarderia/{id}/cancelar", reservaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));

		ArrayNode pacientes = listarPacientes(DIA.toString());

		assertThat(ids(pacientes)).doesNotContain(mascotaId);
	}

	@Test
	@DisplayName("La jornada trae los tutores con su flag de retiro de cada vinculo")
	void tutoresConFlagDeRetiro() throws Exception {
		long duenioId = crearTutor();
		long cotutorId = crearTutor();
		long mascotaId = crearMascotaConTutor("Fiel", duenioId, true);
		// El cotutor se vincula despues: es el caso real del hermano que acompana
		// al dueño pero a quien no le dejan llevarse el animal.
		vincular(cotutorId, mascotaId, false);
		crearTurno("2026-11-09T09:00:00", mascotaId, crearProfesional());

		ArrayNode tutores = (ArrayNode) listarPacientes(DIA.toString()).get(0).get("tutores");

		assertThat(tutores).hasSize(2);
		Map<Long, Boolean> flags = new HashMap<>();
		tutores.forEach(t -> flags.put(t.get("tutorId").asLong(), t.get("autorizadoRetiro").asBoolean()));

		assertThat(flags).containsEntry(duenioId, true);
		assertThat(flags).containsEntry(cotutorId, false);
	}

	@Test
	@DisplayName("Un dia sin movimientos devuelve lista vacia y no un error")
	void jornadaVacia() throws Exception {
		mockMvc.perform(get("/api/v1/egreso/pacientes-hoy").param("fecha", "2026-01-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	// ------------------------------------------------------- tutores-del-dia

	@Test
	@DisplayName("tutores-del-dia agrupa sin repetir: un tutor con dos mascotas aparece una vez")
	void tutoresDelDiaAgrupa() throws Exception {
		long tutorId = crearTutor();
		long primera = crearMascotaConTutor("Perro", tutorId, true);
		long segunda = crearMascotaConTutor("Gato", tutorId, true);
		crearTurno("2026-11-09T09:00:00", primera, crearProfesional());
		crearReserva(segunda, DIA.toString(), "08:00", "18:00", "COMPLETA_24H");

		ArrayNode tutores = listarTutoresDelDia(DIA.toString());

		long encontrados = 0;
		for (JsonNode t : tutores) {
			if (t.get("tutorId").asLong() == tutorId) {
				encontrados++;
				assertThat(t.get("mascotas")).hasSize(2);
			}
		}
		assertThat(encontrados).isEqualTo(1);
	}

	@Test
	@DisplayName("tutores-del-dia devuelve el DNI para poder validar el retiro sin escribirlo")
	void tutoresDelDiaTraeDni() throws Exception {
		long tutorId = crearTutor();
		long mascotaId = crearMascotaConTutor("ConDni", tutorId, true);
		crearTurno("2026-11-09T09:00:00", mascotaId, crearProfesional());

		mockMvc.perform(get("/api/v1/caja/tutores-del-dia").param("fecha", DIA.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].tutorId").value(tutorId))
				.andExpect(jsonPath("$[0].nombreCompleto").value("Tutor Jornada"))
				.andExpect(jsonPath("$[0].mascotas[0].mascotaId").value(mascotaId))
				.andExpect(jsonPath("$[0].mascotas[0].motivos",
						Matchers.contains("TURNO_CLINICO")));
	}

	@Test
	@DisplayName("Egreso y caja ven la misma jornada: un solo origen para el dia")
	void cajaYEgresoCoinciden() throws Exception {
		long tutorId = crearTutor();
		long mascotaId = crearMascotaConTutor("Coincidente", tutorId, true);
		crearTurno("2026-11-09T09:00:00", mascotaId, crearProfesional());

		ArrayNode pacientes = listarPacientes(DIA.toString());
		ArrayNode tutores = listarTutoresDelDia(DIA.toString());

		assertThat(ids(pacientes)).containsExactly(mascotaId);
		assertThat(tutores).hasSize(1);
		assertThat(tutores.get(0).get("tutorId").asLong()).isEqualTo(tutorId);
		// La caja solo lista tutores de mascotas de la jornada: si apareciera uno
		// sin mascota con actividad, las dos pantallas estarian desacopladas.
		assertThat(tutores.get(0).get("mascotas").get(0).get("mascotaId").asLong())
				.isEqualTo(mascotaId);
	}

	// ------------------------------------------------------------- helpers

	private ArrayNode listarPacientes(String fecha) throws Exception {
		var request = get("/api/v1/egreso/pacientes-hoy");
		if (fecha != null) {
			request = request.param("fecha", fecha);
		}
		return leer(mockMvc.perform(request).andExpect(status().isOk()));
	}

	private ArrayNode listarTutoresDelDia(String fecha) throws Exception {
		var request = get("/api/v1/caja/tutores-del-dia");
		if (fecha != null) {
			request = request.param("fecha", fecha);
		}
		return leer(mockMvc.perform(request).andExpect(status().isOk()));
	}

	/** Jackson 3 devuelve JsonNode generico: el endpoint responde un arreglo, y el cast lo deja explicito. */
	private ArrayNode leer(ResultActions resultado) throws Exception {
		return (ArrayNode) objectMapper.readTree(
				resultado.andReturn().getResponse().getContentAsString());
	}

	private long[] ids(ArrayNode pacientes) {
		long[] salida = new long[pacientes.size()];
		for (int i = 0; i < pacientes.size(); i++) {
			salida[i] = pacientes.get(i).get("mascotaId").asLong();
		}
		return salida;
	}

	private List<String> nombresDeMotivos(JsonNode mascota) {
		List<String> salida = new ArrayList<>();
		mascota.get("motivos").forEach(m -> salida.add(m.asText()));
		return salida;
	}

	private long crearMascota(String nombre) throws Exception {
		return crearMascotaConTutor(nombre, null, true);
	}

	/**
	 * Alta de mascota con su tutor anidado cuando se pasa uno.
	 *
	 * <p>Con tutor, una sola llamada: si el vinculo fuera por la via del tutor
	 * despues, la mascota podria quedar sin dueno si la segunda fallara.</p>
	 */
	private long crearMascotaConTutor(String nombre, Long tutorId, boolean autorizado) throws Exception {
		String cuerpo = tutorId == null
				? """
					{"nombre":"%s","especie":"CANINO"}
					""".formatted(nombre)
				: """
					{"nombre":"%s","especie":"CANINO","tutores":[{"tutorId":%d,"autorizadoRetiro":%s}]}
					""".formatted(nombre, tutorId, autorizado);

		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void vincular(long tutorId, long mascotaId, boolean autorizado) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}", tutorId, mascotaId)
						.param("autorizadoRetiro", String.valueOf(autorizado)))
				.andExpect(status().isCreated());
	}

	private long crearTurno(String inicio, long mascotaId, long profesionalId) throws Exception {
		String json = mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fechaHoraInicio":"%s","motivo":"Control","mascotaId":%d,"profesionalId":%d}
								""".formatted(inicio, mascotaId, profesionalId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearReserva(long mascotaId, String fecha, String entrada, String salida, String tipo)
			throws Exception {
		String json = mockMvc.perform(post("/api/v1/guarderia")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fecha":"%s","tipoEstadia":"%s","horaEntrada":"%s","horaSalida":"%s","mascotaId":%d}
								""".formatted(fecha, tipo, entrada, salida, mascotaId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor() throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Tutor","apellido":"Jornada","dni":"%s"}
								""".formatted(dni())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional() throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Sosa","matricula":"MAT-J%d"}
								""".formatted(System.nanoTime() % 100000)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	/** El DNI es unico y cada test corre en su propia transaccion con rollback. */
	private String dni() {
		return String.valueOf(33000000L + Math.abs(System.nanoTime() % 900000L));
	}
}
