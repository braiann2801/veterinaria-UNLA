package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.TurnoClinico;

@Repository
public interface TurnoClinicoRepository extends JpaRepository<TurnoClinico, Long> {

	List<TurnoClinico> findByFechaHoraInicioBetween(LocalDateTime desde, LocalDateTime hasta);

	List<TurnoClinico> findByMascotaId(Long mascotaId);

	/**
	 * Detecta solapamiento del bloque completo (atencion + desinfeccion).
	 *
	 * <p>Dos intervalos [a,b) y [c,d) se solapan si a &lt; d y c &lt; b. La
	 * ventana comparada va de {@code fecha_hora_inicio} a
	 * {@code fecha_fin_bloque}, que ya incluye los 15 min de desinfeccion
	 * (regla 2.2). Por eso un turno que arranca exactamente cuando el anterior
	 * termina la atencion tambien se rechaza: el bloque anterior sigue ocupado
	 * por desinfeccion.</p>
	 *
	 * <p>Los CANCELADOS se excluyen porque no ocupan consultorio.</p>
	 */
	@Query("""
			SELECT t FROM TurnoClinico t
			WHERE t.profesional.id = :profesionalId
			  AND t.estado <> com.veterinaria.veterinaria_backend.entity.TurnoClinico$EstadoTurno.CANCELADO
			  AND t.fechaHoraInicio < :finBloque
			  AND :inicio < t.fechaFinBloque
			""")
	List<TurnoClinico> findSolapamientos(@Param("profesionalId") Long profesionalId,
			@Param("inicio") LocalDateTime inicio,
			@Param("finBloque") LocalDateTime finBloque);

	/** Agenda de un profesional en una fecha, para pintar el calendario. */
	List<TurnoClinico> findByProfesionalIdAndFechaHoraInicioBetweenOrderByFechaHoraInicio(
			Long profesionalId, LocalDateTime desde, LocalDateTime hasta);
}