package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.entity.Medicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;
import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;
import com.veterinaria.veterinaria_backend.repository.MedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoStockMedicamentoRepository;
import com.veterinaria.veterinaria_backend.service.MedicamentoService;

/**
 * El lock pesimista se mide con transacciones reales de MySQL: por eso esta
 * clase NO es {@code @Transactional}. Si la transacción del test envolviera a
 * los hilos, ninguno vería el lock del otro y la prueba no probaría nada.
 */
@SpringBootTest
class MedicamentoStockConcurrenteTest {

	private static final int CARGAS = 12;

	@Autowired
	private MedicamentoService medicamentoService;
	@Autowired
	private MedicamentoRepository medicamentoRepository;
	@Autowired
	private MovimientoStockMedicamentoRepository movimientoRepository;
	@Autowired
	private PlatformTransactionManager transacciones;

	private Long medicamentoId;

	@AfterEach
	void limpiar() {
		if (medicamentoId == null) {
			return;
		}
		//Sin @Transactional en la clase, la limpieza necesita su propia transaccion.
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			movimientoRepository.deleteByMedicamentoId(medicamentoId);
			medicamentoRepository.deleteById(medicamentoId);
		});
	}

	@Test
	@DisplayName("12 cargas simultáneas de 1 unidad dejan stock 12 y 12 movimientos INGRESO")
	void cargasConcurrentes() throws Exception {
		Medicamento medicamento = new Medicamento();
		// Nombre único por corrida: si una ejecución muere antes del @AfterEach,
		// la siguiente no choca con el constraint de unicidad.
		medicamento.setNombre("Medicamento concurrente " + java.util.UUID.randomUUID());
		medicamento.setNombreNormalizado(NormalizadorAr.clave(medicamento.getNombre()));
		medicamento.setPresentacion("");
		medicamento.setPresentacionNormalizada("");
		medicamento.setStockActual(0);
		medicamento.setStockMinimo(0);
		medicamento.setActivo(true);
		medicamento.setFechaActualizacion(LocalDateTime.now());
		medicamentoId = medicamentoRepository.saveAndFlush(medicamento).getId();

		ExecutorService hilos = Executors.newFixedThreadPool(CARGAS);
		CountDownLatch salida = new CountDownLatch(1);
		List<Future<?>> resultados = new ArrayList<>();
		try {
			for (int i = 0; i < CARGAS; i++) {
				resultados.add(hilos.submit(() -> {
					salida.await();
					medicamentoService.agregarStock(medicamentoId,
							new AgregarStockRequestDTO(1, "Carga concurrente"), null);
					return null;
				}));
			}
			salida.countDown();
			for (Future<?> resultado : resultados) {
				resultado.get(60, TimeUnit.SECONDS);
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(medicamentoRepository.findById(medicamentoId).orElseThrow().getStockActual()).isEqualTo(CARGAS);
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(medicamentoId, TipoMovimiento.INGRESO))
				.isEqualTo(CARGAS);
		assertThat(movimientoRepository.findByMedicamentoIdOrderByFechaHoraDescIdDesc(
				medicamentoId, PageRequest.of(0, CARGAS)).getTotalElements())
				.isEqualTo(CARGAS);
	}
}
