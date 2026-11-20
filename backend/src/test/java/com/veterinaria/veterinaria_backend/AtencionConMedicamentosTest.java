package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.veterinaria.veterinaria_backend.entity.Medicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.MedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoStockMedicamentoRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Cerrar una atencion: la ficha marca el turno como ATENDIDO, descuenta el
 * stock de los medicamentos usados y emite una comanda con la consulta mas
 * cada medicamento, con nombre y precio copiados al emitir.
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class AtencionConMedicamentosTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MedicamentoRepository medicamentoRepository;

	@Autowired
	private MovimientoStockMedicamentoRepository movimientoRepository;

	@Autowired
	private ConsultaMedicaRepository consultaRepository;

	@Autowired
	private PlatformTransactionManager transacciones;

	private long mascotaId;
	private long profesionalId;
	private long sufijo;

	@BeforeEach
	void preparar() throws Exception {
		sufijo = System.nanoTime() % 1000000;
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", "CONSULTA_CLINICA")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":8000}"))
				.andExpect(status().isOk());
		long tutorId = crear("/api/v1/tutores", "{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"dni\":\"%d\"}"
				.formatted(37000000 + System.nanoTime() % 900000));
		mascotaId = crear("/api/v1/mascotas", "{\"nombre\":\"Rex\",\"especie\":\"CANINO\"}");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
		profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-X%d\"}".formatted(System.nanoTime() % 100000));
	}

	@Test
	@DisplayName("La ficha con medicamentos emite una comanda con el detalle y descuenta el stock")
	void comandaConMedicamentos() throws Exception {
		long vacuna = crearMedicamento("Vacuna antirrabica", 12500, 10);
		long jeringa = crearMedicamento("Jeringa", 300, 20);

		long comandaId = comandaDe(ficha(null, """
				[{"medicamentoId":%d,"cantidad":1},{"medicamentoId":%d,"cantidad":2}]"""
				.formatted(vacuna, jeringa))
				.andExpect(status().isCreated()));

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.concepto").value("CONSULTA_CLINICA"))
				// 8000 de consulta + 12500 + 2 x 300
				.andExpect(jsonPath("$.montoTotal").value(21100.00))
				.andExpect(jsonPath("$.saldoPendiente").value(21100.00))
				.andExpect(jsonPath("$.items", Matchers.hasSize(3)))
				.andExpect(jsonPath("$.items[0].descripcion").value("Consulta clínica"))
				.andExpect(jsonPath("$.items[0].subtotal").value(8000.00))
				.andExpect(jsonPath("$.items[1].descripcion").value("Vacuna antirrabica-" + sufijo))
				.andExpect(jsonPath("$.items[1].cantidad").value(1))
				.andExpect(jsonPath("$.items[1].precioUnitario").value(12500.00))
				.andExpect(jsonPath("$.items[2].descripcion").value("Jeringa-" + sufijo))
				.andExpect(jsonPath("$.items[2].cantidad").value(2))
				.andExpect(jsonPath("$.items[2].precioUnitario").value(300.00))
				.andExpect(jsonPath("$.items[2].subtotal").value(600.00));

		Assertions.assertEquals(9, medicamentoRepository.findById(vacuna).orElseThrow().getStockActual());
		Assertions.assertEquals(18, medicamentoRepository.findById(jeringa).orElseThrow().getStockActual());
		Assertions.assertEquals(1,
				movimientoRepository.countByMedicamentoIdAndTipo(vacuna, TipoMovimiento.EGRESO_FICHA));
		Assertions.assertEquals(1,
				movimientoRepository.countByMedicamentoIdAndTipo(jeringa, TipoMovimiento.EGRESO_FICHA));

		// El movimiento queda vinculado a la ficha que consumio el medicamento.
		long consultaId = objectMapper
				.readTree(mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
						.andReturn().getResponse().getContentAsString())
				.get(0).get("id").asLong();
		mockMvc.perform(get("/api/v1/medicamentos/{id}/movimientos", vacuna))
				.andExpect(jsonPath("$.contenido[0].tipo").value("EGRESO_FICHA"))
				.andExpect(jsonPath("$.contenido[0].referenciaId").value(consultaId));
	}

	@Test
	@DisplayName("Cambiar el precio despues no altera la comanda ya emitida, pero rige para la siguiente")
	void precioCongelado() throws Exception {
		long vacuna = crearMedicamento("Vacuna sextuple", 10000, 10);
		long comandaId = comandaDe(ficha(null, "[{\"medicamentoId\":%d,\"cantidad\":1}]".formatted(vacuna))
				.andExpect(status().isCreated()));

		mockMvc.perform(put("/api/v1/medicamentos/{id}/precio", vacuna)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"precio\":99999}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precio").value(99999.00));

		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(18000.00))
				.andExpect(jsonPath("$.items[1].precioUnitario").value(10000.00));

		long comandaNueva = comandaDe(ficha(null, "[{\"medicamentoId\":%d,\"cantidad\":1}]".formatted(vacuna))
				.andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaNueva))
				.andExpect(jsonPath("$.montoTotal").value(107999.00))
				.andExpect(jsonPath("$.items[1].precioUnitario").value(99999.00));
	}

	@Test
	@DisplayName("Sin medicamentos la comanda es solo la consulta y no hay egresos de stock")
	void sinMedicamentos() throws Exception {
		long vacuna = crearMedicamento("Vacuna sin usar", 12500, 5);
		long comandaId = comandaDe(ficha(null, null).andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/comandas/{id}", comandaId))
				.andExpect(jsonPath("$.montoTotal").value(8000.00))
				.andExpect(jsonPath("$.items", Matchers.hasSize(1)));
		Assertions.assertEquals(5, medicamentoRepository.findById(vacuna).orElseThrow().getStockActual());
		Assertions.assertEquals(0,
				movimientoRepository.countByMedicamentoIdAndTipo(vacuna, TipoMovimiento.EGRESO_FICHA));
	}

	@Test
	@DisplayName("La ficha asociada a un turno lo marca como ATENDIDO")
	void turnoAtendido() throws Exception {
		long turnoId = turno("2031-08-01T10:00:00");
		ficha(turnoId, null).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/turnos").param("fecha", "2031-08-01"))
				.andExpect(jsonPath("$[?(@.id == " + turnoId + ")].estado").value("ATENDIDO"));
	}

	@Test
	@DisplayName("Un turno cancelado no se puede atender y no se emite nada")
	void turnoCancelado() throws Exception {
		long turnoId = turno("2031-08-02T10:00:00");
		mockMvc.perform(put("/api/v1/turnos/{id}/cancelar", turnoId)).andExpect(status().isOk());

		ficha(turnoId, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El turno " + turnoId + " esta cancelado: no se puede atender"));
		mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	@Test
	@DisplayName("Un turno de otra mascota da 400")
	void turnoDeOtraMascota() throws Exception {
		long otraMascota = crear("/api/v1/mascotas", "{\"nombre\":\"Otra\",\"especie\":\"CANINO\"}");
		long turnoId = turnoDe(otraMascota, "2031-08-03T10:00:00");

		ficha(turnoId, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El turno " + turnoId + " es de otra mascota"));
	}

	@Test
	@DisplayName("Medicamentos inexistentes, inactivos o con cantidad invalida dan error y no emiten nada")
	void medicamentosInvalidos() throws Exception {
		long inactivo = crearMedicamento("Discontinuado", 100, 5);
		Medicamento entidad = medicamentoRepository.findById(inactivo).orElseThrow();
		entidad.setActivo(false);
		medicamentoRepository.save(entidad);
		long activo = crearMedicamento("Gasa", 50, 5);

		ficha(null, "[{\"medicamentoId\":99999999,\"cantidad\":1}]").andExpect(status().isNotFound());
		ficha(null, "[{\"medicamentoId\":%d,\"cantidad\":1}]".formatted(inactivo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El medicamento Discontinuado-" + sufijo + " esta desactivado"));
		ficha(null, "[{\"medicamentoId\":%d,\"cantidad\":0}]".formatted(activo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("La cantidad debe ser al menos 1")));
		ficha(null, "[{\"medicamentoId\":%d,\"cantidad\":100}]".formatted(activo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("La cantidad no puede superar 99")));

		mockMvc.perform(get("/api/v1/consultas/mascota/{id}", mascotaId))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
		Assertions.assertEquals(5, medicamentoRepository.findById(activo).orElseThrow().getStockActual());
	}

	@Test
	@DisplayName("Sin stock suficiente la atencion se rechaza con 409 y no deja ficha ni comanda")
	void stockInsuficiente() throws Exception {
		long escaso = crearMedicamento("Stock escaso", 500, 1);

		ficha(null, "[{\"medicamentoId\":%d,\"cantidad\":2}]".formatted(escaso))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("Stock insuficiente")));

		// El rollback ocurre al salir del servicio. El test comparte esa transaccion
		// y sigue viendo la fila no confirmada: se cuenta desde una transaccion nueva,
		// que es lo que veria el mostrador despues del rollback real.
		Assertions.assertEquals(0L, consultasConfirmadas());
		Assertions.assertEquals(1, medicamentoRepository.findById(escaso).orElseThrow().getStockActual());
		Assertions.assertEquals(0,
				movimientoRepository.countByMedicamentoIdAndTipo(escaso, TipoMovimiento.EGRESO_FICHA));
	}

	@Test
	@DisplayName("El peso se registra en la ficha y actualiza el peso actual de la mascota")
	void pesoEnLaAtencion() throws Exception {
		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Control","pesoKg":12.5}
								""".formatted(mascotaId, profesionalId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pesoKg").value(12.5));
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(jsonPath("$.pesoKg").value(12.5));

		// Sin peso, la ficha no pisa el ultimo registrado.
		ficha(null, null).andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/mascotas/{id}", mascotaId))
				.andExpect(jsonPath("$.pesoKg").value(12.5));

		// Editar la mascota sin mandar el peso (el formulario ya no lo pide) lo conserva.
		mockMvc.perform(put("/api/v1/mascotas/{id}", mascotaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nombre\":\"Rex\",\"especie\":\"CANINO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pesoKg").value(12.5));

		mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Control","pesoKg":-1}
								""".formatted(mascotaId, profesionalId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detalles", Matchers.hasItem("El peso debe ser mayor a 0")));
	}

	// ------------------------------------------------------------- helpers

	/** Cuenta las fichas confirmadas, fuera de la transaccion del test. */
	private long consultasConfirmadas() {
		TransactionTemplate nueva = new TransactionTemplate(transacciones);
		nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return nueva.execute(status -> consultaRepository.count());
	}

	private ResultActions ficha(Long turnoId, String medicamentos) throws Exception {
		return mockMvc.perform(post("/api/v1/consultas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"turnoClinicoId":%s,"mascotaId":%d,"profesionalId":%d,
						 "anamnesis":"Control","medicamentos":%s}
						""".formatted(turnoId == null ? "null" : turnoId, mascotaId, profesionalId,
						medicamentos == null ? "null" : medicamentos)));
	}

	private long crearMedicamento(String nombre, int precio, int stock) throws Exception {
		String json = mockMvc.perform(post("/api/v1/medicamentos")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s-%d","precio":%d,"stockInicial":%d}
								""".formatted(nombre, sufijo, precio, stock)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long comandaDe(ResultActions resultado) throws Exception {
		long id = objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString())
				.get("comandaId").asLong();
		Assertions.assertTrue(id > 0);
		return id;
	}

	private long turno(String inicio) throws Exception {
		return turnoDe(mascotaId, inicio);
	}

	private long turnoDe(long mascota, String inicio) throws Exception {
		return crear("/api/v1/turnos", """
				{"fechaHoraInicio":"%s","motivo":"Control","mascotaId":%d,"profesionalId":%d}
				""".formatted(inicio, mascota, profesionalId));
	}

	private long crear(String ruta, String body) throws Exception {
		String json = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
