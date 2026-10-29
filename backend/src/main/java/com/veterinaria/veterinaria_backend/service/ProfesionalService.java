package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalResponseDTO;

/**
 * Casos de uso del alta de profesionales clinicos.
 */
public interface ProfesionalService {

	List<ProfesionalResponseDTO> listar();

	ProfesionalResponseDTO crear(ProfesionalRequestDTO request);

	/** Edita con las mismas reglas que el alta (normalizacion y unicidad de DNI y matricula). */
	ProfesionalResponseDTO actualizar(Long id, ProfesionalRequestDTO request);

	/** Borra un profesional sin turnos ni consultas; con actividad responde 409. */
	void eliminar(Long id);
}