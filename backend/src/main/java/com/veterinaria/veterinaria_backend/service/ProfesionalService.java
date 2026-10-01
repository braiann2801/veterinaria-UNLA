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
}