package com.veterinaria.veterinaria_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;

/** Historial de movimientos de stock, consultable por medicamento. */
@Repository
public interface MovimientoStockMedicamentoRepository
		extends JpaRepository<MovimientoStockMedicamento, Long> {

	/** El id es desempate del orden por fecha: dos movimientos en el mismo segundo no se intercambian. */
	Page<MovimientoStockMedicamento> findByMedicamentoIdOrderByFechaHoraDescIdDesc(
			Long medicamentoId, Pageable pageable);

	/** Conteo usado por los tests para comprobar que cada carga dejó su movimiento. */
	long countByMedicamentoIdAndTipo(Long medicamentoId, TipoMovimiento tipo);

	/** Limpieza del historial al retirar un medicamento de la base de pruebas. */
	void deleteByMedicamentoId(Long medicamentoId);
}
