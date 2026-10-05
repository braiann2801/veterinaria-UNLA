package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.ProfesionalService;
import com.veterinaria.veterinaria_backend.service.TutorService;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TANDA 2 / FASE 2: la API no puede rechazar una fecha por su formato.
 *
 * <p>Se reprodujo contra la base un bug que dejaba la pantalla de ficha medica
 * sin poder guardar nada: {@code components/modules/ficha.tsx} manda la fecha desde
 * un {@code <input type="date">}, o sea {@code YYYY-MM-DD} sin hora, contra un
 * campo {@code LocalDateTime}. Jackson respondia 400 con
 * {@code DateTimeParseException: Text '2026-10-01' could not be parsed at index
 * 10}. Estos tests quedan como barrera: si alguien saca el
 * {@code FlexibleLocalDateTimeDeserializer} de los DTO, la clase se pone roja.</p>
 *
 * <p><b>Por que van por HTTP y no contra el servicio.</b> El fallo ocurre en la
 * capa de deserializacion, que el servicio nunca ve: llamar a
 * {@code consultaService.registrar(...)} con un {@code LocalDateTime} ya
 * construido pasaria siempre, aun con el bug presente. Reciben un POST con JSON
 * crudo reproduce la condicion real.</p>
 *
 * <p><b>Lo que estos tests NO fijan.</b> Nada de fechas futuras ni pasadas: la
 * retroactividad es otra regla y ya tiene su barrera en
 * {@link TurnoRetroactivoTest}. Aca importa solo que el formato no sea motivo de
 * rechazo.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FechasFlexiblesApiTest {

	@Autowired
	private MockMvc mockMvc;

	// Spring Boot 4.1 usa Jackson 3.
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MascotaService mascotaService;

	@Autowired
	private ProfesionalService profesionalService;

	@Autowired
	private TutorService tutorService;

	/** Un solo profesional por clase: ver la nota de `profesionalActivo()`. */
	private Long profesionalId;

	/**
	 * Tarifa de consulta clinica.
	 *
	 * <p>No es un detalle del formato de fecha, pero sin ella el alta de la ficha
	 * falla antes de llegar a la deserializacion: la comanda de cobro congelaria el
	 * monto de una tarifa que no existe (regla 2.3). Un 400 por eso, en medio de un
	 * test de fechas, tapa justo el error que se quiere ver.</p>
	 */
	@BeforeEach
	void cargarTarifaDeConsulta() throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/CONSULTA_CLINICA")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"monto\":8000}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Ficha medica: fecha sin hora, el caso de <input type=date>, se guarda igual")
	void fichaAceptaFechaSinHora() throws Exception {
		JsonNode body = postFicha("2026-06-15");

		// La fecha sola se completa a las 00:00:00: no se descarta ni se corre de dia.
		assertThat(body.get("fechaAtencion").asString()).startsWith("2026-06-15T00:00:00");
	}

	@Test
	@DisplayName("Ficha medica: el ISO completo sigue siendo valido (no se rompio lo que andaba)")
	void fichaAceptaIsoCompleto() throws Exception {
		JsonNode body = postFicha("2026-06-15T09:30:00");

		assertThat(body.get("fechaAtencion").asString()).startsWith("2026-06-15T09:30");
	}

	@Test
	@DisplayName("Ficha medica: sin segundos tambien entra")
	void fichaAceptaIsoSinSegundos() throws Exception {
		JsonNode body = postFicha("2026-06-15T09:30");

		assertThat(body.get("fechaAtencion").asString()).startsWith("2026-06-15T09:30");
	}

	@Test
	@DisplayName("Ficha medica: el offset en UTC no se corre de dia, la columna es hora local")
	void fichaIgnoraElOffset() throws Exception {
		// `toISOString()` de JavaScript produce este formato. Antes entraba por
		// casualidad; ahora entra a proposito y sin mover el dia.
		JsonNode body = postFicha("2026-06-15T09:30:00.000Z");

		assertThat(body.get("fechaAtencion").asString()).startsWith("2026-06-15T09:30");
	}

	@Test
	@DisplayName("Ficha medica: fecha pasada y futura se aceptan (no es la regla de esta correccion)")
	void fichaAceptaFechaPasadaYFutura() throws Exception {
		// La retroactividad ya la cubre TurnoRetroactivoTest para turnos. Aca se
		// verifica solo que el formato no se confunda con una restriccion de calendario.
		postFicha("2019-01-07");
		postFicha("2031-11-30");
	}

	@Test
	@DisplayName("Ficha medica: un formato ilegible sigue siendo 400, con mensaje util")
	void fichaRechazaFormatoIlegible() throws Exception {
		mockMvc.perform(post("/api/v1/consultas")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoDeFicha("\"fechaAtencion\":\"15/06/2026\"")))
				.andExpect(status().isBadRequest())
				// El mensaje dice que formatos se aceptan, en vez del
				// "could not be parsed at index N" que no orienta a nadie.
				.andExpect(jsonPath("$.mensaje").value(containsString("YYYY-MM-DDTHH:mm:ss")));
	}

	@Test
	@DisplayName("Turnos: la tolerancia de formato tambien aplica al alta")
	void turnoAceptaFechaSinHora() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/turnos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fechaHoraInicio\":\"2026-06-15\",\"motivo\":\"Consulta de control\","
						+ "\"mascotaId\":" + mascotaConTutor() + ",\"profesionalId\":" + profesionalActivo() + "}"))
				.andExpect(status().isCreated())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("fechaHoraInicio").asString()).startsWith("2026-06-15T00:00:00");
		// El dominio sigue derivando la ventana 45+15: la tolerancia es de formato,
		// no una via para esquivar la desinfeccion.
		assertThat(body.get("fechaHoraFin").asString()).startsWith("2026-06-15T00:45:00");
		assertThat(body.get("fechaFinBloque").asString()).startsWith("2026-06-15T01:00:00");
	}

	@Test
	@DisplayName("Turnos: el filtro por dia sigue exigiendo ISO.DATE y no acepta un datetime")
	void elFiltroPorDiaSigueExigiendoUnaFecha() throws Exception {
		// Contraparte del test anterior: la flexibilidad es del body, no del query
		// param. `?fecha=2026-06-15T00:00:00` es un dia mal formado y tiene que
		// seguir dando 400, porque en query params el formato si esta pactado.
		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2026-06-15T00:00:00"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2026-06-15"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Guarderia: la fecha sola y la hora HH:mm no necesitan puente, ya entraban")
	void guarderiaNoSeRompio() throws Exception {
		// El DTO de reserva usa LocalDate y LocalTime, no LocalDateTime: ya era
		// tolerante. Este test existe para que el cambio del deserializador no
		// haya inyectado un LocalDateTime donde antes habia un LocalDate.
		mockMvc.perform(post("/api/v1/guarderia")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"2026-06-15\",\"tipoEstadia\":\"DIURNA\","
						+ "\"mascotaId\":" + mascotaConTutor() + ",\"horaEntrada\":\"08:00\","
						+ "\"horaSalida\":\"18:00\",\"sena\":0}"))
				.andExpect(status().isCreated());
	}

	// ------------------------------------------------------------- helpers

	/** POST de ficha con la fecha indicada y devuelve el cuerpo de la respuesta. */
	private JsonNode postFicha(String fechaAtencion) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/consultas")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoDeFicha("\"fechaAtencion\":\"" + fechaAtencion + "\"")))
				.andReturn();

		String cuerpo = result.getResponse().getContentAsString();
		// El mensaje del servidor va en el fallo: un 400 sin texto no dice si el
		// problema fue el formato de la fecha, el vinculo del tutor o la comanda, y
		// adivinarlo desde el test es perder el tiempo de quien lo lee.
		assertThat(result.getResponse().getStatus())
				.withFailMessage("POST /consultas con fechaAtencion=%s devolvio %s: %s",
						fechaAtencion, result.getResponse().getStatus(), cuerpo)
				.isEqualTo(201);
		return objectMapper.readTree(cuerpo);
	}

	/**
	 * Cuerpo JSON de una ficha con la propiedad de fecha inyectada como texto.
	 *
	 * <p>Se inyecta la propiedad como fragmento crudo y no con un {@code Map} por
	 * una razon concreta: lo que se prueba es el parsing de esa cadena, y armarla
	 * con el serializador de Jackson la dejaria ya escapada, que es justo lo que no
	 * se quiere verificar.</p>
	 */
	private String cuerpoDeFicha(String propiedadDeFecha) {
		return "{" + propiedadDeFecha + ","
				+ "\"mascotaId\":" + mascotaConTutor() + ","
				+ "\"profesionalId\":" + profesionalActivo() + ","
				+ "\"anamnesis\":\"Control de prueba de formato de fecha\","
				+ "\"conductaObservada\":\"TRANQUILO\"}";
	}

	/**
	 * Mascota con un tutor autorizado a retirar.
	 *
	 * <p>El tutor autorizado no es un detalle del contrato de fecha, pero sin el
	 * la ficha no se emite: la comanda de cobro necesita a alguien a quien
	 * cargarle el importe. Un 400 por "no tiene tutor autorizado" en medio de un
	 * test de formato tapa el bug que se quiere ver, asi que se paga el setup.</p>
	 */
	private Long mascotaConTutor() {
		MascotaRequestDTO request = new MascotaRequestDTO(
				"Fechas", MascotaRequestDTO.EspecieRequestDTO.CANINO, "Mestizo", "Macho",
				null, new BigDecimal("9.5"), "normal", null, null, null);
		var mascota = mascotaService.crear(request);
		var tutor = tutorService.crear(new TutorRequestDTO(
				"Probe", "Fechas", dniUnico(), "1134567890", null, null, null));
		tutorService.vincularMascota(tutor.id(), mascota.id(), true);
		return mascota.id();
	}

	/**
	 * Un solo profesional por clase.
	 *
	 * <p>Se cachea porque el mismo profesional con varios turnos a horas distintas
	 * no choca, y asi cada test queda aislado del orden en que JUnit los mezcle.</p>
	 */
	private Long profesionalActivo() {
		if (profesionalId == null) {
			profesionalId = profesionalService
					.crear(new ProfesionalRequestDTO("Laura", "Sosa", null, "MAT-" + System.nanoTime(), null))
					.id();
		}
		return profesionalId;
	}

	/** 8 digitos: el {@code @Pattern} del DNI pide entre 7 y 10. */
	private String dniUnico() {
		return String.valueOf(20_000_000L + (System.nanoTime() % 9_000_000L));
	}
}