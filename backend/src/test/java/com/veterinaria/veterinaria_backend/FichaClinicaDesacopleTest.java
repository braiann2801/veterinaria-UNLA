package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-005: desacople clinico-contable (AGENTS.md 2.3).
 *
 * <p>La regla 2.3 dice que la consulta clinica NO almacena cobros ni medios de
 * pago y que genera una comanda diferida al mostrador. Esa regla se rompe de dos
 * formas y estas pruebas cubren las dos:</p>
 *
 * <ol>
 *   <li><b>Por omision.</b> Alguien agrega {@code monto} o {@code metodoPago}
 *       al contrato clinico. Los tests de reflexion fallan al instante, sin
 *       depender de que alguien lea el JSON con atencion.</li>
 *   <li><b>Por omision en el servicio.</b> La ficha se guarda pero la comanda
 *       no se emite.</li>
 * </ol>
 *
 * <p>Los tests de reflexion existen porque un test que solo mira el valor de los
 * campos pasa igual con un {@code monto} extra: nadie lo notaria hasta que un
 * veterinario empiece a cargar precios desde la pantalla clinica.</p>
 *
 * <p><b>Lo que este test NO puede verificar.</b> Que la ficha se revierta
 * cuando la comanda no se puede emitir exige observar un rollback real, y dentro
 * de una transaccion administrada por el test el rollback nunca ocurre: la
 * excepcion sube y el test la ve, pero los cambios del contexto de persistencia
 * quedan ahi. Esa parte vive en {@link RollbackTransaccionalTest}, que corre sin
 * {@code @Transactional}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FichaClinicaDesacopleTest {

	/**
	 * Cualquier campo cuyo nombre huela a dinero es motivo de rechazo.
	 *
	 * <p>Se busca por patron y no por lista cerrada porque una lista cerrada se
	 * olvida actualizar justo cuando alguien agrega el campo prohibido.</p>
	 */
	private static final Pattern CAMPOS_CONTABLES = Pattern.compile(
			"(?i).*(monto|precio|saldo|total|pago|importe|cobro|cobrar|forma).*");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	// ------------------------------------------------ contratos sin contabilidad

	@Test
	@DisplayName("El DTO de alta de consulta no declara ningun campo contable")
	void requestDtoNoTieneCamposContables() {
		List<String> componentes = nombresDe(ConsultaMedicaRequestDTO.class.getRecordComponents());

		assertThat(componentes)
				.as("ConsultaMedicaRequestDTO no debe exponer montos ni medios de pago")
				.noneMatch(campo -> CAMPOS_CONTABLES.matcher(campo).matches());
	}

	@Test
	@DisplayName("El DTO de respuesta de consulta no declara ningun campo contable")
	void responseDtoNoTieneCamposContables() {
		List<String> componentes = nombresDe(ConsultaMedicaResponseDTO.class.getRecordComponents());

		assertThat(componentes)
				.as("ConsultaMedicaResponseDTO no debe exponer saldo ni monto")
				.noneMatch(campo -> CAMPOS_CONTABLES.matcher(campo).matches());
	}

	@Test
	@DisplayName("La entidad ConsultaMedica no tiene ningun campo contable")
	void entidadNoTieneCamposContables() {
		Field[] fields = ConsultaMedica.class.getDeclaredFields();

		assertThat(Arrays.stream(fields).map(Field::getName).toList())
				.as("La ficha clinica no puede persistir montos (regla 2.3)")
				.noneMatch(campo -> CAMPOS_CONTABLES.matcher(campo).matches());

		// Defensa extra por tipo: ningun BigDecimal, sea cual sea el nombre.
		assertThat(Arrays.stream(fields).filter(f -> BigDecimal.class.equals(f.getType())))
				.as("La ficha clinica no debe tener ningun campo BigDecimal")
				.isEmpty();
	}

	// ------------------------------------------------ emision de la comanda

	@Test
	@DisplayName("Guardar la ficha emite una comanda PENDIENTE sin tocar el contrato clinico")
	void registrarEmiteComandaPendiente() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 8000);
		long mascotaId = crearMascota();
		long profesionalId = crearProfesional("MAT-D1");
		long tutorId = crearTutor("31000001");

		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId))
				.andExpect(status().isCreated());

		String body = mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,
								 "anamnesis":"Tos persistente desde hace 5 dias",
								 "diagnostico":"Bronquitis",
								 "tratamiento":"Antibiotico 7 dias",
								 "conductaObservada":"NERVIOSO"}
								""".formatted(mascotaId, profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.anamnesis").value("Tos persistente desde hace 5 dias"))
				.andExpect(jsonPath("$.conductaObservada").value("NERVIOSO"))
				.andExpect(jsonPath("$.alertaConducta").value(false))
				.andExpect(jsonPath("$.comandaId").isNumber())
				.andReturn().getResponse().getContentAsString();

		long comandaId = objectMapper.readTree(body).get("comandaId").asLong();

		// La comanda existe, del lado del mostrador, y arranca impaga.
		ComandaCobro comanda = comandaRepository.findById(comandaId).orElseThrow();
		assertThat(comanda.getEstado()).isEqualTo(ComandaCobro.EstadoComanda.PENDIENTE);
		assertThat(comanda.getConcepto()).isEqualTo(ComandaCobro.Concepto.CONSULTA_CLINICA);
		assertThat(comanda.getMontoTotal()).isEqualByComparingTo("8000.00");
		assertThat(comanda.getSaldoPendiente()).isEqualByComparingTo("8000.00");
		assertThat(comanda.getTutor().getId()).isEqualTo(tutorId);

		// Y el JSON clinico no menciona un solo peso.
		assertThat(body.toLowerCase(Locale.ROOT))
				.doesNotContain("monto")
				.doesNotContain("saldo")
				.doesNotContain("precio");
	}

	@Test
	@DisplayName("Recepcion verifica la emision de la comanda sin ver el detalle clinico")
	void verificarEmisionEnRecepcion() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 5000);
		long mascotaId = crearMascota();
		long tutorId = crearTutor("31000002");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId))
				.andExpect(status().isCreated());

		long consultaId = registrarConsulta(mascotaId, crearProfesional("MAT-D2"), "TRANQUILO");

		mockMvc.perform(get("/api/v1/consultas/{id}/comanda", consultaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.consultaId").value(consultaId))
				.andExpect(jsonPath("$.emitida").value(true))
				.andExpect(jsonPath("$.comandaId").isNumber())
				.andExpect(jsonPath("$.monto").doesNotExist())
				.andExpect(jsonPath("$.saldo").doesNotExist());
	}

	@Test
	@DisplayName("Sin tutor autorizado a retirar la consulta se rechaza y no se emite comanda")
	void sinTutorAutorizadoRechazaLaConsulta() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 8000);
		long mascotaId = crearMascota();
		long tutorId = crearTutor("31000003");

		// Vinculado pero SIN autorizacion de retiro: es cotutor, no deudor.
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false",
				tutorId, mascotaId))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Control anual"}
								""".formatted(mascotaId, crearProfesional("MAT-D3"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje",
						org.hamcrest.Matchers.containsString("autorizado a retirar")));

		// No se emite ninguna comanda a nombre del cotutor sin permisos.
		// Que la ficha tampoco se descarte lo verifica RollbackTransaccionalTest,
		// que necesita una transaccion real para observar el rollback.
		assertThat(comandaRepository.findByTutorIdOrderByFechaEmisionDesc(tutorId)).isEmpty();
	}

	// ------------------------------------------------ alertas de conducta

	@Test
	@DisplayName("REACTIVO y AGRESIVO encienden la alerta; NERVIOSO y TRANQUILO no")
	void alertaDeConductaSeDerivaEnElServidor() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 3000);
		long mascotaId = crearMascota();
		long tutorId = crearTutor("31000004");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId))
				.andExpect(status().isCreated());
		long profesionalId = crearProfesional("MAT-D4");

		for (String conducta : List.of("REACTIVO", "AGRESIVO", "TRANQUILO", "NERVIOSO")) {
			registrarConsulta(mascotaId, profesionalId, conducta);
		}

		String json = mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// El JSON se parsea en vez de usar un filtro jsonPath: el orden de las
		// fichas depende de la fecha de atencion y un filtro sobre el indice
		// haria que el test dependiera de ese orden.
		Map<String, Boolean> alertas = new LinkedHashMap<>();
		for (JsonNode ficha : objectMapper.readTree(json)) {
			alertas.put(ficha.get("conductaObservada").asText(),
					ficha.get("alertaConducta").asBoolean());
		}

		assertThat(alertas).hasSize(4)
				.containsEntry("AGRESIVO", Boolean.TRUE)
				.containsEntry("REACTIVO", Boolean.TRUE)
				.containsEntry("NERVIOSO", Boolean.FALSE)
				.containsEntry("TRANQUILO", Boolean.FALSE);
	}

	@Test
	@DisplayName("El cliente no puede apagar una alerta mandando alertaConducta=false")
	void elClienteNoPuedeApagarLaAlerta() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 3000);
		long mascotaId = crearMascota();
		long tutorId = crearTutor("31000005");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId))
				.andExpect(status().isCreated());

		// alertaConducta no existe en el contrato: Jackson ignora el campo extra
		// y el servidor deriva el valor real desde la conducta observada.
		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Mordisco",
								 "conductaObservada":"AGRESIVO","alertaConducta":false}
								""".formatted(mascotaId, crearProfesional("MAT-D5"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.alertaConducta").value(true));
	}

	// ------------------------------------------------ idempotencia

	@Test
	@DisplayName("El mismo turno no puede emitir dos fichas ni dos comandas")
	void turnoNoSeCargaDosVeces() throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 7000);
		long mascotaId = crearMascota();
		long tutorId = crearTutor("31000006");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId))
				.andExpect(status().isCreated());

		long profesionalId = crearProfesional("MAT-D6");
		long turnoId = crearTurno(mascotaId, profesionalId);
		String payload = """
				{"turnoClinicoId":%d,"mascotaId":%d,"profesionalId":%d,"anamnesis":"Vacunacion"}
				""".formatted(turnoId, mascotaId, profesionalId);

		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isCreated());

		// Doble clic del veterinario: sin este chequeo se facturaria dos veces.
		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje",
						org.hamcrest.Matchers.containsString("ya tiene una ficha")));

		mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
	}

	// ------------------------------------------------ helpers

	private static List<String> nombresDe(RecordComponent[] componentes) {
		return Arrays.stream(componentes).map(RecordComponent::getName).toList();
	}

	private long registrarConsulta(long mascotaId, long profesionalId, String conducta) throws Exception {
		String json = mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,
								 "anamnesis":"Consulta de control","conductaObservada":"%s"}
								""".formatted(mascotaId, profesionalId, conducta)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.monto").value(monto));
	}

	private long crearMascota() throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Firulais","especie":"CANINO","raza":"Mestizo","sexo":"Macho"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Ana","apellido":"Gomez","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional(String matricula) throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Ruiz","matricula":"%s"}
								""".formatted(matricula)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTurno(long mascotaId, long profesionalId) throws Exception {
		// Tarde: lejos de cualquier turno previo de los otros tests del modulo.
		String json = mockMvc.perform(post("/api/v1/turnos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,
								 "fechaHoraInicio":"2026-03-17T14:00:00","motivo":"Vacunacion"}
								""".formatted(mascotaId, profesionalId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}