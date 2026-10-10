package com.veterinaria.veterinaria_backend.repository;

import java.math.BigDecimal;
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

	/**
	 * Total cobrado en una jornada.
	 *
	 * <p>{@code COALESCE} a cero porque {@code SUM} sobre un conjunto vacio
	 * devuelve NULL. Sumar en la base y no trayendo las filas: una jornada con
	 * cientos de pagos no tiene por que viajar entera solo para sacar un total.</p>
	 */
	@Query("""
			SELECT COALESCE(SUM(p.monto), 0)
			FROM Pago p
			WHERE p.fechaHora >= :desde AND p.fechaHora < :hasta
			""")
	BigDecimal sumarTotalDelDia(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	/** Cantidad de pagos registrados en la jornada (una comanda mixta suma varias). */
	@Query("""
			SELECT COUNT(p)
			FROM Pago p
			WHERE p.fechaHora >= :desde AND p.fechaHora < :hasta
			""")
	long contarPagosDelDia(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	/**
	 * Extracto plano de los pagos de una jornada, para el Balance.
	 *
	 * <p>Trae solo las columnas que muestra la vista y resuelve el nombre del
	 * tutor con {@code CONCAT}: {@code Tutor.nombreCompleto} es un getter
	 * derivado, no un campo persistente, asi que no se puede proyectar con
	 * {@code t.nombreCompleto}. El rango es semiabierto
	 * {@code [desde, hasta)} para no solapar el pago de las 00:00 del dia
	 * siguiente.</p>
	 */
	@Query("""
			SELECT p.id AS id,
			       p.fechaHora AS fechaHora,
			       c.fechaEmision AS fechaEmision,
			       p.metodoPago AS metodoPago,
			       p.monto AS monto,
			       c.concepto AS concepto,
			       CONCAT(CONCAT(t.nombre, ' '), t.apellido) AS nombreTutor,
			       m.nombre AS nombreMascota,
			       c.estado AS estadoComanda
			FROM Pago p
			JOIN p.comanda c
			JOIN c.tutor t
			LEFT JOIN c.mascota m
			WHERE p.fechaHora >= :desde AND p.fechaHora < :hasta
			ORDER BY p.fechaHora ASC, p.id ASC
			""")
	List<MovimientoCajaProyeccion> movimientosDelDia(@Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);

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

	/** ¿El tutor pago alguna comanda como pagador? Bloquea su borrado. */
	boolean existsByTutorPagadorId(Long tutorId);
}