package com.veterinaria.veterinaria_backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.MedicamentoUsadoFicha;

/** Snapshot de los medicamentos aplicados en cada ficha clínica. */
@Repository
public interface MedicamentoUsadoFichaRepository extends JpaRepository<MedicamentoUsadoFicha, Long> {

	List<MedicamentoUsadoFicha> findByConsultaMedicaIdOrderById(Long consultaMedicaId);

	/** Consulta única para el historial de una mascota (evita N+1 por ficha). */
	@Query("SELECT u FROM MedicamentoUsadoFicha u WHERE u.consultaMedica.id IN :ids "
			+ "ORDER BY u.consultaMedica.id, u.id")
	List<MedicamentoUsadoFicha> findPorConsultas(@Param("ids") Collection<Long> ids);

	/** Los ids con consumo en ficha se usan para permitir o rechazar el borrado físico. */
	@Query("SELECT DISTINCT u.medicamentoId FROM MedicamentoUsadoFicha u WHERE u.medicamentoId IN :ids")
	List<Long> idsMedicamentosUsados(@Param("ids") Collection<Long> ids);

	boolean existsByMedicamentoId(Long medicamentoId);
}
