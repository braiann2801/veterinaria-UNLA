package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;

@Repository
public interface ReservaGuarderiaRepository extends JpaRepository<ReservaGuarderia, Long> {

	List<ReservaGuarderia> findByFecha(LocalDate fecha);

	/**
	 * Reservas de un dia ordenadas como una jornada: primero las que tienen
	 * horario y de la mas temprano a la mas tarde, y al final las que no lo
	 * tienen.
	 *
	 * <p>El {@code CASE} hace el trabajo que un {@code ORDER BY hora_entrada ASC}
	 * no puede: en MySQL el NULL ordena primero en ascendente, asi que las
	 * reservas sin horario taparian la cabeza de la lista del mostrador. En
	 * JPQL y no en SQL nativo para que el orden sea el mismo en H2 y en MySQL.</p>
	 */
	@Query("""
			SELECT r FROM ReservaGuarderia r
			WHERE r.fecha <= :fecha AND COALESCE(r.fechaSalida, r.fecha) >= :fecha
			ORDER BY CASE WHEN r.horaEntrada IS NULL THEN 1 ELSE 0 END,
			         r.horaEntrada, r.horaSalida, r.id
			""")
	List<ReservaGuarderia> findByFechaOrdenadasPorHorario(@Param("fecha") LocalDate fecha);

	/**
	 * Estadias en esos estados que cubren el dia: las de un solo dia de esa fecha
	 * y las de varias noches cuyo rango lo incluye.
	 */
	@Query("""
			SELECT r FROM ReservaGuarderia r
			WHERE r.fecha <= :dia AND COALESCE(r.fechaSalida, r.fecha) >= :dia
			  AND r.estado IN :estados
			""")
	List<ReservaGuarderia> findVigentesEnDia(@Param("dia") LocalDate dia,
			@Param("estados") List<ReservaGuarderia.EstadoReserva> estados);

	/**
	 * Aforo ocupado de una fecha: cuenta solo reservas RESERVADO o INGRESADO.
	 *
	 * <p>JPQL en vez de derived query porque el filtro depende de dos estados y
	 * de un valor constante de la entidad (AFORO_MAXIMO). Es la unica fuente de
	 * verdad del aforo: el servicio no recalcula en memoria.</p>
	 */
	@Query("""
			SELECT COUNT(r) FROM ReservaGuarderia r
			WHERE r.fecha <= :fecha AND COALESCE(r.fechaSalida, r.fecha) >= :fecha
			  AND r.estado IN (com.veterinaria.veterinaria_backend.entity.ReservaGuarderia$EstadoReserva.RESERVADO,
			                   com.veterinaria.veterinaria_backend.entity.ReservaGuarderia$EstadoReserva.INGRESADO)
			""")
	long contarCuposOcupados(@Param("fecha") LocalDate fecha);

	/** ¿La mascota ya tiene una estadia activa que se pise con [desde, hasta]? */
	@Query("""
			SELECT COUNT(r) > 0 FROM ReservaGuarderia r
			WHERE r.mascota.id = :mascotaId
			  AND r.fecha <= :hasta AND COALESCE(r.fechaSalida, r.fecha) >= :desde
			  AND r.estado IN :estados
			""")
	boolean existeEstadiaSolapada(@Param("mascotaId") Long mascotaId, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta, @Param("estados") List<ReservaGuarderia.EstadoReserva> estados);

	/** Estadia vigente de una mascota que cubre el dia (la que se finaliza al entregarla). */
	@Query("""
			SELECT r FROM ReservaGuarderia r
			WHERE r.mascota.id = :mascotaId
			  AND r.fecha <= :dia AND COALESCE(r.fechaSalida, r.fecha) >= :dia
			  AND r.estado IN :estados
			ORDER BY r.fecha, r.id
			""")
	List<ReservaGuarderia> findVigentesDeMascotaEnDia(@Param("mascotaId") Long mascotaId,
			@Param("dia") LocalDate dia, @Param("estados") List<ReservaGuarderia.EstadoReserva> estados);

	/** ¿La mascota tiene alguna reserva de guarderia? Bloquea su borrado. */
	boolean existsByMascotaId(Long mascotaId);
}