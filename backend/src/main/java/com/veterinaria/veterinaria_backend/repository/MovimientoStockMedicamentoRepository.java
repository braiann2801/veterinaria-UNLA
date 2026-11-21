package com.veterinaria.veterinaria_backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	boolean existsByMedicamentoIdAndTipo(Long medicamentoId, TipoMovimiento tipo);

	/** Limpieza del historial al retirar un medicamento de la base de pruebas. */
	void deleteByMedicamentoId(Long medicamentoId);

	/**
	 * Ids con consumo en ficha, en lote: un ingreso o un ajuste no cuentan como
	 * "uso" (un medicamento cargado por error se puede borrar).
	 */
	@Query("SELECT DISTINCT m.medicamento.id FROM MovimientoStockMedicamento m "
			+ "WHERE m.tipo = :tipo AND m.medicamento.id IN :ids")
	List<Long> idsConConsumo(@Param("tipo") TipoMovimiento tipo, @Param("ids") Collection<Long> ids);

	/** Fichas antiguas, sin snapshot propio: el egreso guarda el id de la ficha. */
	List<MovimientoStockMedicamento> findByTipoAndReferenciaIdOrderById(
			TipoMovimiento tipo, Long referenciaId);

	List<MovimientoStockMedicamento> findByTipoAndReferenciaIdInOrderByReferenciaIdAscIdAsc(
			TipoMovimiento tipo, Collection<Long> referenciaIds);
}
