package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;

/**
 * Casos de uso del modulo Turnos Clinicos.
 *
 * <p>Bloques de 45 min de atencion + 15 de desinfeccion (AGENTS.md 2.2). Un
 * profesional no puede tener dos bloques solapados.</p>
 */
public interface TurnoClinicoService {

	List<TurnoClinicoResponseDTO> listarPorFecha(LocalDate fecha);

	List<TurnoClinicoResponseDTO> listarPorProfesional(Long profesionalId, LocalDate fecha);

	/**
	 * Agenda un turno. Rechaza si el bloque se solapa con uno existente del
	 * mismo profesional, incluyendo la ventana de desinfeccion.
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.BusinessConflictException si hay solapamiento
	 */
	TurnoClinicoResponseDTO crear(TurnoClinicoRequestDTO request);

	/** Reprograma o cambia el estado de un turno, revalidando solapamientos. */
	TurnoClinicoResponseDTO actualizar(Long id, TurnoClinicoRequestDTO request);

	/** Cancela un turno (libera el bloque del profesional). */
	TurnoClinicoResponseDTO cancelar(Long id);

	void eliminar(Long id);

	/**
	 * Verifica si un profesional puede ocupar el bloque indicado. Util para que
	 * el frontend deshabilite horarios antes de enviar la peticion.
	 */
	boolean disponible(Long profesionalId, LocalDateTime inicio);
}