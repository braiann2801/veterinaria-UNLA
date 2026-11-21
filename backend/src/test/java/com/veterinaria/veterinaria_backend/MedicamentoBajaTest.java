package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.Medicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;
import com.veterinaria.veterinaria_backend.repository.MedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.MedicamentoUsadoFichaRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoStockMedicamentoRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Baja de medicamentos con el mismo patrón que tutores y mascotas: borrado
 * físico solo para lo que nunca se usó, toggle para lo que ya está en una ficha.
 *
 * <p>"Usado" es consumo en ficha, no el INGRESO de la carga: un medicamento
 * cargado por error se puede borrar aunque tenga su movimiento de alta.</p>
 */
@SpringBootTest
@WithMockUser(roles = "DUENIO")
@AutoConfigureMockMvc
@Transactional
class MedicamentoBajaTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MedicamentoRepository medicamentoRepository;

	@Autowired
	private MovimientoStockMedicamentoRepository movimientoRepository;

	@Autowired
	private MedicamentoUsadoFichaRepository snapshotRepository;

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
		long tutorId = crear("/api/v1/tutores",
				"{\"nombre\":\"Ana\",\"apellido\":\"Paz\",\"dni\":\"%d\"}"
						.formatted(37000000 + System.nanoTime() % 900000));
		mascotaId = crear("/api/v1/mascotas", "{\"nombre\":\"Rex\",\"especie\":\"CANINO\"}");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true", tutorId, mascotaId))
				.andExpect(status().isCreated());
		profesionalId = crear("/api/v1/profesionales",
				"{\"nombre\":\"Laura\",\"apellido\":\"Sosa\",\"matricula\":\"MAT-B%d\"}"
						.formatted(System.nanoTime() % 100000));
	}

	@Test
	@DisplayName("Un medicamento nunca usado se borra físicamente con su historial de carga")
	void borrarNuncaUsado() throws Exception {
		long id = crearMedicamento("Carga por error", 7);
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(id, TipoMovimiento.INGRESO)).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/medicamentos/{id}", id))
				.andExpect(status().isNoContent());

		assertThat(medicamentoRepository.findById(id)).isEmpty();
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(id, TipoMovimiento.INGRESO)).isZero();
		mockMvc.perform(get("/api/v1/medicamentos/{id}", id))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Un medicamento usado en una ficha no se puede borrar: 409 dice que se desactive")
	void borrarUsadoConflicto() throws Exception {
		long id = crearMedicamento("Vacuna usada", 10);
		long consultaId = registrarFicha(id, 2);

		mockMvc.perform(delete("/api/v1/medicamentos/{id}", id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("fue usado en fichas clínicas")))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("no se puede borrar")))
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("Desactivalo")));

		// Nada se tocó: catálogo, movimiento, snapshot, ficha y comanda siguen iguales.
		assertThat(medicamentoRepository.findById(id)).isPresent();
		assertThat(medicamentoRepository.findById(id).orElseThrow().isActivo()).isTrue();
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(id, TipoMovimiento.EGRESO_FICHA)).isEqualTo(1);
		assertThat(snapshotRepository.existsByMedicamentoId(id)).isTrue();
		mockMvc.perform(get("/api/v1/consultas/{id}", consultaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.medicamentos[0].nombre").value("Vacuna usada-" + sufijo));
	}

	@Test
	@DisplayName("La baja con toggle oculta el medicamento del catálogo activo y la reactivación lo trae de vuelta")
	void desactivarYReactivar() throws Exception {
		long usado = crearMedicamento("Desactivable", 10);
		long consultaId = registrarFicha(usado, 1);
		long inactivosAntes = cantidadInactivos();

		mockMvc.perform(get("/api/v1/medicamentos/{id}", usado))
				.andExpect(jsonPath("$.enUso").value(true));

		mockMvc.perform(put("/api/v1/medicamentos/{id}/desactivar", usado))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false))
				.andExpect(jsonPath("$.enUso").value(true));

		// Desaparece del listado normal y del selector de la ficha, pero el toggle lo encuentra.
		mockMvc.perform(get("/api/v1/medicamentos"))
				.andExpect(jsonPath("$.contenido[?(@.id == " + usado + ")]", Matchers.empty()));
		mockMvc.perform(get("/api/v1/medicamentos").param("incluirInactivos", "true"))
				.andExpect(jsonPath("$.contenido[?(@.id == " + usado + ")]", Matchers.hasSize(1)));
		assertThat(cantidadInactivos()).isEqualTo(inactivosAntes + 1);

		// La ficha conserva la trazabilidad con el medicamento ya dado de baja.
		mockMvc.perform(get("/api/v1/consultas/{id}", consultaId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.medicamentos[0].nombre").value("Desactivable-" + sufijo))
				.andExpect(jsonPath("$.medicamentos[0].presentacion").value("Frasco 10ml"))
				.andExpect(jsonPath("$.medicamentos[0].cantidad").value(1));

		mockMvc.perform(put("/api/v1/medicamentos/{id}/reactivar", usado))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
		assertThat(cantidadInactivos()).isEqualTo(inactivosAntes);
		mockMvc.perform(get("/api/v1/medicamentos"))
				.andExpect(jsonPath("$.contenido[?(@.id == " + usado + ")]", Matchers.hasSize(1)));
	}

	@Test
	@DisplayName("Un medicamento desactivado no admite nuevas cargas de stock ni consumos en fichas")
	void inactivoBloqueaMovimientos() throws Exception {
		long id = crearMedicamento("Descontinuado", 10);
		mockMvc.perform(put("/api/v1/medicamentos/{id}/desactivar", id))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/medicamentos/{id}/stock", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("desactivado")));

		mockMvc.perform(post("/api/v1/consultas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Control",
						 "medicamentos":[{"medicamentoId":%d,"cantidad":1}]}
						""".formatted(mascotaId, profesionalId, id)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", Matchers.containsString("desactivado")));

		assertThat(medicamentoRepository.findById(id).orElseThrow().getStockActual()).isEqualTo(10);
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(id, TipoMovimiento.INGRESO)).isEqualTo(1);
		assertThat(snapshotRepository.existsByMedicamentoId(id)).isFalse();
	}

	@Test
	@DisplayName("Dar de baja y borrar un medicamento inexistente responde 404")
	void inexistente() throws Exception {
		mockMvc.perform(put("/api/v1/medicamentos/99999999/desactivar"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/medicamentos/99999999"))
				.andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------- helpers

	private long cantidadInactivos() throws Exception {
		String json = mockMvc.perform(get("/api/v1/medicamentos/inactivos/cantidad"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("cantidad").asLong();
	}

	/** Ficha real: deja snapshot, egreso de stock y comanda, igual que en producción. */
	private long registrarFicha(long medicamentoId, int cantidad) throws Exception {
		String json = mockMvc.perform(post("/api/v1/consultas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"mascotaId":%d,"profesionalId":%d,"anamnesis":"Control",
						 "medicamentos":[{"medicamentoId":%d,"cantidad":%d}]}
						""".formatted(mascotaId, profesionalId, medicamentoId, cantidad)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMedicamento(String nombre, int stock) throws Exception {
		String json = mockMvc.perform(post("/api/v1/medicamentos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"%s-%d","presentacion":"Frasco 10ml","precio":1000,"stockInicial":%d}
						""".formatted(nombre, sufijo, stock)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crear(String ruta, String body) throws Exception {
		String json = mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}
