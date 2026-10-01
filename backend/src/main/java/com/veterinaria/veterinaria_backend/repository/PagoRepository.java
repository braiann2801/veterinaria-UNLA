package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Pago;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

	List<Pago> findByComandaIdOrderByFechaHoraAsc(Long comandaId);

	/** Pagos de una jornada, para el arqueo de caja. */
	List<Pago> findByFechaHoraBetweenOrderByFechaHoraAsc(LocalDateTime desde, LocalDateTime hasta);

	/**
	 * Total cobrado por metodo de pago en una jornada.
	 *
	 * <p>Agrupar en la base y no en Java: la caja cierra con el detalle de las
	 * boletas del dia y traer todas las filas para sumarlas en memoria escala
	 * mal, ademas de abrir la puerta a olvidar alguna fila del arqueo.</p>
	 */
	@Query("""
			SELECT p.metodoPago, COALESCE(SUM(p.monto), 0)
			FROM Pago p
			WHERE p.fechaHora >= :desde AND p.fechaHora < :hasta
			GROUP BY p.metodoPago
			""")
	List<Object[]> totaledPorMetodoPago(@Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);

	/**
	 * Cuantas comandas distintas recibieron al menos un pago en la jornada.
	 *
	 * <p>{@code COUNT(DISTINCT)} y no el tamano de la lista de pagos: una
	 * comanda saldada con efectivo y transferencia es una sola comanda cobrada,
	 * no dos.</p>
	 */
	@Query("""
			SELECT COUNT(DISTINCT p.comanda.id)
			FROM Pago p
			WHERE p.fechaHora >= :desde AND p.fechaHora < :hasta
			""")
	long contarComandasCobradas(@Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);
}