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
			WHERE r.fecha = :fecha
			ORDER BY CASE WHEN r.horaEntrada IS NULL THEN 1 ELSE 0 END,
			         r.horaEntrada, r.horaSalida, r.id
			""")
	List<ReservaGuarderia> findByFechaOrdenadasPorHorario(@Param("fecha") LocalDate fecha);

	List<ReservaGuarderia> findByFechaAndEstadoIn(LocalDate fecha,
			List<ReservaGuarderia.EstadoReserva> estados);

	/**
	 * Aforo ocupado de una fecha: cuenta solo reservas RESERVADO o INGRESADO.
	 *
	 * <p>JPQL en vez de derived query porque el filtro depende de dos estados y
	 * de un valor constante de la entidad (AFORO_MAXIMO). Es la unica fuente de
	 * verdad del aforo: el servicio no recalcula en memoria.</p>
	 */
	@Query("""
			SELECT COUNT(r) FROM ReservaGuarderia r
			WHERE r.fecha = :fecha
			  AND r.estado IN (com.veterinaria.veterinaria_backend.entity.ReservaGuarderia$EstadoReserva.RESERVADO,
			                   com.veterinaria.veterinaria_backend.entity.ReservaGuarderia$EstadoReserva.INGRESADO)
			""")
	long contarCuposOcupados(@Param("fecha") LocalDate fecha);

	/** ¿La mascota ya tiene reserva activa para esa fecha? */
	boolean existsByMascotaIdAndFechaAndEstadoIn(Long mascotaId, LocalDate fecha,
			List<ReservaGuarderia.EstadoReserva> estados);
}