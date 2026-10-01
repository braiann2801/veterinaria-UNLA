package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;

@Repository
public interface ConsultaMedicaRepository extends JpaRepository<ConsultaMedica, Long> {

	/** Historial clinico de una mascota, del mas reciente al mas antiguo. */
	List<ConsultaMedica> findByMascotaIdOrderByFechaAtencionDesc(Long mascotaId);

	/** Consultas de un profesional, para el seguimiento de su agenda. */
	List<ConsultaMedica> findByProfesionalIdOrderByFechaAtencionDesc(Long profesionalId);

	/**
	 * Ficha de un turno, si ya se cargo. Se usa para idempotencia: una consulta
	 * no puede emitir dos comandas para el mismo turno.
	 */
	Optional<ConsultaMedica> findByTurnoClinicoId(Long turnoClinicoId);

	boolean existsByTurnoClinicoId(Long turnoClinicoId);
}