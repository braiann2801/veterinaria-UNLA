package com.veterinaria.veterinaria_backend.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;

@Repository
public interface ComandaCobroRepository extends JpaRepository<ComandaCobro, Long> {

	/** Comandas de un tutor, opcionalmente filtradas por estado. */
	List<ComandaCobro> findByTutorIdOrderByFechaEmisionDesc(Long tutorId);

	List<ComandaCobro> findByTutorIdAndEstadoOrderByFechaEmisionDesc(Long tutorId,
			ComandaCobro.EstadoComanda estado);

	/** Comandas con saldo por cobrar de un tutor: la base de la alerta de morosidad. */
	List<ComandaCobro> findByTutorIdAndEstadoInOrderByFechaEmisionAsc(Long tutorId,
			List<ComandaCobro.EstadoComanda> estados);

	/**
	 * Deuda consolidada de un tutor.
	 *
	 * <p>{@code COALESCE} a cero porque {@code SUM} sobre un conjunto vacio
	 * devuelve NULL, y un NULL viajaria como {@code null} en el JSON de deuda en
	 * lugar de 0. Un tutor al dia debe reportar 0, no ausencia de dato.</p>
	 */
	@Query("""
			SELECT COALESCE(SUM(c.saldoPendiente), 0)
			FROM ComandaCobro c
			WHERE c.tutor.id = :tutorId
			  AND c.estado IN (com.veterinaria.veterinaria_backend.entity.ComandaCobro$EstadoComanda.PENDIENTE,
			                   com.veterinaria.veterinaria_backend.entity.ComandaCobro$EstadoComanda.PARCIAL)
			""")
	BigDecimal sumarDeudaPendiente(@Param("tutorId") Long tutorId);

	/** Comanda emitida para una ficha clinica, si ya existe. */
	Optional<ComandaCobro> findByConsultaMedicaId(Long consultaMedicaId);

	/** Evita emitir una segunda comanda para la misma consulta. */
	boolean existsByConsultaMedicaId(Long consultaMedicaId);

	List<ComandaCobro> findByEstado(ComandaCobro.EstadoComanda estado);

	/**
	 * Toma la comanda con {@code SELECT ... FOR UPDATE}.
	 *
	 * <p>SQL nativo y no {@code @Lock}: en Hibernate 7 combinar {@code @Query}
	 * JPQL con un modo de lock produce {@code FOR UPDATE OF alias}, que es
	 * sintaxis de Oracle/PostgreSQL y MariaDB rechaza con error 1064. Es el
	 * mismo problema que se documento en TASK-004-HOTFIX.</p>
	 *
	 * <p>Es necesario porque el saldo es un dato compartido: dos cajas
	 * cobrando a la vez leen el mismo saldo, las dos ven que alcanza y las dos
	 * restan. El resultado seria una comanda con saldo negativo y mas dinero
	 * cobrado que facturado.</p>
	 */
	@Query(value = "SELECT * FROM comanda_cobro WHERE id = :id FOR UPDATE", nativeQuery = true)
	Optional<ComandaCobro> bloquearPorId(@Param("id") Long id);
}