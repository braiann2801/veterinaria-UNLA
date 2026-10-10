package com.veterinaria.veterinaria_backend.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

	/**
	 * Saldo por cobrar de las comandas emitidas en una jornada.
	 *
	 * <p>Alimenta la tarjeta "saldos pendientes" del cierre: de lo facturado ese
	 * dia, cuanto quedo sin cobrar. Se suman solo PENDIENTE y PARCIAL porque una
	 * comanda CANCELADA no debe nada y una PAGADA esta saldada. Igual que
	 * {@code findDelDia}, filtra por {@code fechaEmision} (la unica fecha de la
	 * comanda) y con rango semiabierto {@code [inicio, fin)}.</p>
	 */
	@Query("""
			SELECT COALESCE(SUM(c.saldoPendiente), 0)
			FROM ComandaCobro c
			WHERE c.fechaEmision >= :inicio AND c.fechaEmision < :fin
			  AND c.estado IN (com.veterinaria.veterinaria_backend.entity.ComandaCobro$EstadoComanda.PENDIENTE,
			                   com.veterinaria.veterinaria_backend.entity.ComandaCobro$EstadoComanda.PARCIAL)
			""")
	BigDecimal sumarSaldoPendienteDelDia(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

	/** Comanda emitida para una ficha clinica, si ya existe. */
	Optional<ComandaCobro> findByConsultaMedicaId(Long consultaMedicaId);

	/** Evita emitir una segunda comanda para la misma consulta. */
	boolean existsByConsultaMedicaId(Long consultaMedicaId);

	/** ¿El tutor tiene comandas emitidas a su nombre? Bloquea su borrado. */
	boolean existsByTutorId(Long tutorId);

	/** ¿Hay alguna comanda asociada a la mascota? Bloquea su borrado. */
	boolean existsByMascotaId(Long mascotaId);

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

	/**
	 * Comandas emitidas en una jornada, para la caja del dia.
	 *
	 * <p>El rango es semiabierto {@code [inicio, fin)} por la misma razon que en
	 * {@code TurnoClinicoRepository.findDelDia}: una comanda emitida a las 00:00
	 * del dia siguiente no es de hoy, y con un rango cerrado entraria en los dos
	 * listados.</p>
	 *
	 * <p><b>La fecha filtra por {@code fechaEmision}, que es la unica fecha que la
	 * comanda tiene.</b> No se infiere el dia del turno o de la reserva: la comanda
	 * se emite al guardar la ficha clinica, que puede ser dias despues de la
	 * atencion. Filtrar "comandas del dia" por la actividad mezclaria en el corte
	 * de hoy una consulta de la semana pasada. Lo que la caja del dia necesita son
	 * las emisiones de la jornada, que es ademas lo que un cierre de caja cuenta.</p>
	 *
	 * <p>El orden es por emision descendente con desempate por id: la comanda mas
	 * reciente arriba, y dos emitidas en el mismo instante con orden estable.</p>
	 */
	@Query("""
			SELECT c FROM ComandaCobro c
			WHERE c.fechaEmision >= :inicio AND c.fechaEmision < :fin
			ORDER BY c.fechaEmision DESC, c.id DESC
			""")
	List<ComandaCobro> findDelDia(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}