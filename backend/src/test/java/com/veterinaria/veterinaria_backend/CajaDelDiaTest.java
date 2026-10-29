package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.PagoRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * TASK-009: caja del dia y cobro por un tercero vinculado.
 *
 * <p>Dos cosas se verifican y las dos son reglas, no detalles de transporte.</p>
 *
 * <p><b>La fecha.</b> La caja del dia filtra por fecha de <i>emision</i>, que es lo
 * unico que la comanda conoce. Filtrar por la fecha del turno seria inventar un
 * vinculo que la base no tiene, y una consulta de la semana pasada pagada hoy
 * apareceria en el corte de hoy.</p>
 *
 * <p><b>Quien paga.</b> El caso de referencia es el hermano: esta vinculado a la
 * mascota, tiene prohibido retirarse al animal y vino a pagar la comanda de su
 * familia. Se acepta. Un tercero sin vinculo, no. Y el flag de retiro
 * ({@code autorizado_retiro}) no participa en esta regla, que es la confusion
 * mas probable de esta fase.</p>
 */
@SpringBootTest
// La API exige login: estos tests prueban reglas de negocio, no permisos
// (ver AutenticacionTest). Duenio tiene acceso a todos los endpoints.
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class CajaDelDiaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PagoRepository pagoRepository;

	@Autowired
	private ComandaCobroRepository comandaRepository;

	// ------------------------------------------------------- caja del dia

	@Test
	@DisplayName("Sin fecha devuelve las comandas emitidas hoy")
	void sinFechaUsaHoy() throws Exception {
		long tutorId = crearTutor();
		long deHoy = emitirComanda(tutorId, "GUARDERIA", 8000);
		crearComandaEnFecha(tutorId, "2026-02-03T12:00:00");

		var listado = listarDia(null, null, null);

		assertThat(listado).hasSize(1);
		assertThat(listado.get(0).get("id").asLong()).isEqualTo(deHoy);
	}

	@Test
	@DisplayName("La fecha acota la jornada: lo emitido en otro dia no se cuela")
	void acotaPorFecha() throws Exception {
		long tutorId = crearTutor();
		long emitidaHoy = emitirComanda(tutorId, "GUARDERIA", 8000);
		long emitidaEnMarzo = crearComandaEnFecha(tutorId, "2026-03-03T12:00:00");

		// Marzo: solo la que se forzo a esa fecha.
		var marzo = listarDia("2026-03-03", null, null);
		assertThat(marzo).hasSize(1);
		assertThat(marzo.get(0).get("id").asLong()).isEqualTo(emitidaEnMarzo);

		// Hoy: la que se emitio normalmente, y no la de marzo.
		var hoy = listarDia(LocalDate.now().toString(), null, null);
		assertThat(hoy).hasSize(1);
		assertThat(hoy.get(0).get("id").asLong()).isEqualTo(emitidaHoy);

		// Un dia sin nada no inventa filas.
		assertThat(listarDia("2026-04-04", null, null)).isEmpty();
	}

	@Test
	@DisplayName("Una comanda emitida a las 00:00 no cuenta como del dia anterior")
	void midnightPerteneceAlDiaCorrecto() throws Exception {
		long tutorId = crearTutor();
		crearComandaEnFecha(tutorId, "2026-05-05T00:00:00");

		// Con un rango cerrado por los dos lados, esta entraria en las dos jornadas
		// y el mostrador la veria dos veces.
		assertThat(listarDia("2026-05-04", null, null)).isEmpty();

		var delDia = listarDia("2026-05-05", null, null);
		assertThat(delDia).hasSize(1);
		assertThat(delDia.get(0).get("fechaEmision").asText()).startsWith("2026-05-05T00:00");
	}

	@Test
	@DisplayName("Los filtros de tutor y estado se combinan con la fecha")
	void filtrosCombinables() throws Exception {
		long tutorA = crearTutor();
		long tutorB = crearTutor();
		long deA = emitirComanda(tutorA, "GUARDERIA", 5000);
		emitirComanda(tutorB, "GUARDERIA", 7000);

		// Saldar una de las dos la deja PARCIAL, para tener las dos en la jornada.
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", deA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":1000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"));

		assertThat(listarDia(null, tutorA, null)).hasSize(1);
		assertThat(listarDia(null, tutorB, null)).hasSize(1);
		assertThat(listarDia(null, null, "PENDIENTE")).hasSize(1);
		assertThat(listarDia(null, null, "PARCIAL")).hasSize(1);
		assertThat(listarDia(null, tutorA, "PENDIENTE")).isEmpty();
	}

	@Test
	@DisplayName("Un estado invalido devuelve 400 aunque la jornada este vacia")
	void estadoInvalidoDevuelve400() throws Exception {
		mockMvc.perform(get("/api/v1/comandas/dia").param("fecha", "2026-01-09")
						.param("estado", "INVENTADO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("Estado de comanda invalido")));
	}

	@Test
	@DisplayName("El listado por tutor sigue siendo el historial completo y exige el tutor")
	void listadoPorTutorNoCambio() throws Exception {
		long tutorId = crearTutor();
		emitirComanda(tutorId, "GUARDERIA", 6000);
		crearComandaEnFecha(tutorId, "2026-06-06T12:00:00");

		mockMvc.perform(get("/api/v1/comandas").param("tutorId", String.valueOf(tutorId)))
				.andExpect(status().isOk())
				// Sin filtro de fecha: las dos, de cualquier dia.
				.andExpect(jsonPath("$", Matchers.hasSize(2)));

		mockMvc.perform(get("/api/v1/comandas"))
				.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------- quien esta pagando

	@Test
	@DisplayName("Un cotutor vinculado puede pagar aunque tenga prohibido retirar a la mascota")
	void cotutorVinculadoPuedePagar() throws Exception {
		long duenioId = crearTutor();
		long hermanoId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Firulais", duenioId);
		vincular(hermanoId, mascotaId, false);

		long comandaId = emitirComandaParaMascota(duenioId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}],"tutorId":%d}
								""".formatted(hermanoId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"))
				.andExpect(jsonPath("$.pagos[0].tutorPagadorId").value(hermanoId))
				.andExpect(jsonPath("$.pagos[0].nombreTutorPagador").value("Tutor Caja"));

		// La comanda no cambia de deudor: la pago el hermano, pero la deuda era del
		// dueno. Y al quedar saldada, la morosidad desaparece para todos.
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", duenioId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(false));
	}

	@Test
	@DisplayName("El deudor puede pagar su propia comanda y queda registrado como pagador")
	void deudorPuedePagar() throws Exception {
		long tutorId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Propio", tutorId);
		long comandaId = emitirComandaParaMascota(tutorId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"TRANSFERENCIA","monto":5000}],"tutorId":%d}
								""".formatted(tutorId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagos[0].tutorPagadorId").value(tutorId));
	}

	@Test
	@DisplayName("Sin tutorId se asume el deudor y el pago queda igual de valido")
	void sinTutorIdAsumeElDeudor() throws Exception {
		long tutorId = crearTutor();
		long mascotaId = crearMascotaConDuenio("SinNombre", tutorId);
		long comandaId = emitirComandaParaMascota(tutorId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"))
				// Se rellena con el deudor: no informado no es lo mismo que sin dato,
				// y el arqueo necesita saber quien llevo la plata.
				.andExpect(jsonPath("$.pagos[0].tutorPagadorId").value(tutorId));
	}

	@Test
	@DisplayName("Un tercero sin vinculo no puede pagar la comanda de otro")
	void terceroSinVinculoRechazado() throws Exception {
		long duenioId = crearTutor();
		long desconocidoId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Ajena", duenioId);
		long comandaId = emitirComandaParaMascota(duenioId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}],"tutorId":%d}
								""".formatted(desconocidoId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje",
						Matchers.containsString("no esta vinculado")));

		// Y el rechazo no dejo el pago a medias.
		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId)).isEmpty();
	}

	@Test
	@DisplayName("Un tutor inexistente devuelve 404, no 400")
	void pagadorInexistenteDevuelve404() throws Exception {
		long duenioId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Ausente", duenioId);
		long comandaId = emitirComandaParaMascota(duenioId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}],"tutorId":999999}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("No existe un tutor")));
	}

	@Test
	@DisplayName("Sin mascota en la comanda, solo el titular puede pagarla")
	void comandaSinMascotaSoloTitular() throws Exception {
		long duenioId = crearTutor();
		long otroId = crearTutor();
		long comandaId = emitirComanda(duenioId, "GUARDERIA", 5000);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}],"tutorId":%d}
								""".formatted(otroId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje",
						Matchers.containsString("no tiene mascota asociada")));

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}],"tutorId":%d}
								""".formatted(duenioId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));
	}

	@Test
	@DisplayName("El pago de un tercero no desplaza al deudor de la comanda")
	void elDeudorNoCambia() throws Exception {
		long duenioId = crearTutor();
		long hermanoId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Compartida", duenioId);
		vincular(hermanoId, mascotaId, false);
		long comandaId = emitirComandaParaMascota(duenioId, mascotaId);

		// Pago parcial del hermano: la comanda queda PARCIAL.
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":1000}],"tutorId":%d}
								""".formatted(hermanoId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"))
				// El deudor sigue siendo el dueno. Reasignarlo haria que el dueno
				// apareciera al dia y el hermano quedara moroso.
				.andExpect(jsonPath("$.tutorId").value(duenioId));

		// Y la deuda sigue siendo del dueno, que es lo correcto: el hermano pago
		// parte, pero la comanda se emitió a nombre del dueno.
		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", duenioId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.totalDeuda").value(4000.00));

		mockMvc.perform(get("/api/v1/tutores/{id}/deuda", hermanoId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneDeuda").value(false));
	}

	@Test
	@DisplayName("El cobro mixto por un tercero registra el pagador en las dos lineas")
	void mixtoConTercero() throws Exception {
		long duenioId = crearTutor();
		long hermanoId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Mixta", duenioId);
		vincular(hermanoId, mascotaId, false);
		long comandaId = emitirComandaParaMascota(duenioId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[
								   {"metodoPago":"EFECTIVO","monto":2000},
								   {"metodoPago":"TRANSFERENCIA","monto":3000}
								 ],"tutorId":%d}
								""".formatted(hermanoId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"))
				.andExpect(jsonPath("$.pagos", Matchers.hasSize(2)))
				.andExpect(jsonPath("$.pagos[0].tutorPagadorId").value(hermanoId))
				.andExpect(jsonPath("$.pagos[1].tutorPagadorId").value(hermanoId));

		assertThat(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId))
				.allMatch(p -> p.getTutorPagador() != null
						&& p.getTutorPagador().getId().equals(hermanoId)
						&& p.getComanda() != null);
	}

	@Test
	@DisplayName("El pagador no altera el arqueo: el medio sigue siendo el que se uso")
	void pagadorNoAfectaElArqueo() throws Exception {
		long duenioId = crearTutor();
		long hermanoId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Arqueo", duenioId);
		vincular(hermanoId, mascotaId, false);
		long comandaId = emitirComandaParaMascota(duenioId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"CREDITO","monto":5000}],"tutorId":%d}
								""".formatted(hermanoId)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/caja/pagos/{id}", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].tutorPagadorId").value(hermanoId))
				.andExpect(jsonPath("$[0].nombreTutorPagador").value("Tutor Caja"))
				.andExpect(jsonPath("$[0].metodoPago").value("CREDITO"));

		// Y el cierre de la jornada suma ese credito: quien pago no cambia el medio,
		// y si lo cambiara el arqueo de boveda dejaria de cuadrar.
		mockMvc.perform(get("/api/v1/caja/cierre"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalesPorMetodo.CREDITO").value(5000.00))
				// El extracto del dia refleja el pago con su medio, concepto y
				// estado: es el detalle que muestra el Balance.
				.andExpect(jsonPath("$.movimientos", Matchers.hasSize(1)))
				.andExpect(jsonPath("$.movimientos[0].metodoPago").value("CREDITO"))
				.andExpect(jsonPath("$.movimientos[0].monto").value(5000.00))
				.andExpect(jsonPath("$.movimientos[0].concepto").value("CONSULTA_CLINICA"))
				.andExpect(jsonPath("$.movimientos[0].estadoComanda").value("PAGADA"))
				.andExpect(jsonPath("$.movimientos[0].nombreMascota").value("Arqueo"))
				.andExpect(jsonPath("$.movimientos[0].nombreTutor", Matchers.notNullValue()))
				// La comanda quedo saldada, asi que no arrastra deuda del dia.
				.andExpect(jsonPath("$.saldosPendientes").value(0.00));
	}

	@Test
	@DisplayName("El extracto del cierre distingue al titular de la comanda de quien pago")
	void cierreInformaQuienPago() throws Exception {
		long titularId = crearTutor("Ana", "Titular");
		long cotutorId = crearTutor("Carlos", "Pagador");
		long mascotaId = crearMascotaConDuenio("Pagada", titularId);
		vincular(cotutorId, mascotaId, false);
		long comandaId = emitirComandaParaMascota(titularId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":5000}],"tutorId":%d}
								""".formatted(cotutorId)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/caja/cierre"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.movimientos", Matchers.hasSize(1)))
				.andExpect(jsonPath("$.movimientos[0].nombreTutor").value("Ana Titular"))
				.andExpect(jsonPath("$.movimientos[0].nombrePagador").value("Carlos Pagador"));
	}

	@Test
	@DisplayName("El cierre informa el saldo que quedo sin cobrar de la jornada")
	void cierreInformaSaldosPendientes() throws Exception {
		long tutorId = crearTutor();
		long mascotaId = crearMascotaConDuenio("Pendiente", tutorId);
		long comandaId = emitirComandaParaMascota(tutorId, mascotaId);

		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":2000}],"tutorId":%d}
								""".formatted(tutorId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"));

		mockMvc.perform(get("/api/v1/caja/cierre"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCobrado").value(2000.00))
				// De los 5000 facturados se cobraron 2000: el cierre tiene que
				// mostrar los 3000 que quedaron pendientes.
				.andExpect(jsonPath("$.saldosPendientes").value(3000.00))
				.andExpect(jsonPath("$.cantidadPagos").value(1))
				.andExpect(jsonPath("$.movimientos", Matchers.hasSize(1)))
				.andExpect(jsonPath("$.movimientos[0].metodoPago").value("EFECTIVO"));
	}

	@Test
	@DisplayName("El extracto informa la emision: distingue un saldo de otro dia de un cobro del dia")
	void cobroDeComandaViejaInformaSuEmision() throws Exception {
		long tutorId = crearTutor();
		// Comanda emitida un miercoles, pagada hoy: el pago entra en la jornada de
		// hoy pero la deuda es de otro dia. El extracto tiene que informar la
		// emision para que la vista pueda marcarlo como "deuda anterior".
		long vieja = crearComandaEnFecha(tutorId, "2026-09-16T10:00:00");
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", vieja)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":3000}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));

		// Una comanda del dia, para contrastar la emision.
		long deHoy = emitirComanda(tutorId, "GUARDERIA", 8000);
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", deHoy)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"pagos":[{"metodoPago":"EFECTIVO","monto":8000}]}"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/caja/cierre"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.movimientos", Matchers.hasSize(2)))
				.andExpect(jsonPath("$.movimientos[*].fechaEmision",
						Matchers.hasItem(Matchers.startsWith("2026-09-16T"))))
				.andExpect(jsonPath("$.movimientos[*].fechaEmision",
						Matchers.hasItem(Matchers.startsWith(LocalDate.now().toString()))));
	}

	// ------------------------------------------------------------- helpers

	private ArrayNode listarDia(String fecha, Long tutorId, String estado)
			throws Exception {
		var request = get("/api/v1/comandas/dia");
		if (fecha != null) {
			request = request.param("fecha", fecha);
		}
		if (tutorId != null) {
			request = request.param("tutorId", String.valueOf(tutorId));
		}
		if (estado != null) {
			request = request.param("estado", estado);
		}
		return (ArrayNode) objectMapper.readTree(mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	/** Crea una comanda de hoy y devuelve su id. */
	private long emitirComanda(long tutorId, String concepto, long monto) throws Exception {
		ponerTarifa(concepto, monto);
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"concepto\":\"%s\",\"tutorId\":%d}".formatted(concepto, tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long emitirComandaParaMascota(long tutorId, long mascotaId) throws Exception {
		ponerTarifa("CONSULTA_CLINICA", 5000);
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"CONSULTA_CLINICA","tutorId":%d,"mascotaId":%d}
								""".formatted(tutorId, mascotaId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.montoTotal").value(5000.00))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	/**
	 * Comanda con fecha de emision forzada.
	 *
	 * <p>La API no acepta la fecha de emision: sale de {@code LocalDateTime.now()}.
	 * Para probar el filtro por jornada hay que tocar la fila, asi que se escribe
	 * directo por repositorio en vez de agregar un parametro de test a la API: un
	 * endpoint de pruebas que fija la fecha de emision terminaria siendo una puerta
	 * por la que se puede falsear el corte de caja.</p>
	 */
	private long crearComandaEnFecha(long tutorId, String fechaHora) throws Exception {
		ponerTarifa("GUARDERIA", 3000);
		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"GUARDERIA","tutorId":%d}
								""".formatted(tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long id = objectMapper.readTree(json).get("id").asLong();

		var comanda = comandaRepository.findById(id).orElseThrow();
		comanda.setFechaEmision(LocalDateTime.parse(fechaHora));
		comandaRepository.save(comanda);
		return id;
	}

	private void ponerTarifa(String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());
	}

	private long crearMascotaConDuenio(String nombre, long tutorId) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","tutores":[{"tutorId":%d,"autorizadoRetiro":true}]}
								""".formatted(nombre, tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void vincular(long tutorId, long mascotaId, boolean autorizado) throws Exception {
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}", tutorId, mascotaId)
						.param("autorizadoRetiro", String.valueOf(autorizado)))
				.andExpect(status().isCreated());
	}

	private long crearTutor() throws Exception {
		return crearTutor("Tutor", "Caja");
	}

	private long crearTutor(String nombre, String apellido) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","apellido":"%s","dni":"%s"}
								""".formatted(nombre, apellido, dni())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private String dni() {
		return String.valueOf(34000000L + Math.abs(System.nanoTime() % 900000L));
	}
}
