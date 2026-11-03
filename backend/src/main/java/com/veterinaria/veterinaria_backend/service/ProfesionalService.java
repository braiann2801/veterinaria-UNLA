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

	/** Da de baja al profesional sin borrarlo: queda inactivo y conserva su historial. */
	void eliminar(Long id);

	/** Vuelve a dar de alta a un profesional inactivo. */
	ProfesionalResponseDTO reactivar(Long id);
}