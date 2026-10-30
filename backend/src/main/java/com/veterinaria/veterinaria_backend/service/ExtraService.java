package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.ExtraRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ExtraResponseDTO;

/** Catalogo de extras: solo el Duenio lo modifica (ver SecurityConfig). */
public interface ExtraService {

	List<ExtraResponseDTO> listar(boolean soloActivos);

	ExtraResponseDTO crear(ExtraRequestDTO request);

	ExtraResponseDTO actualizar(Long id, ExtraRequestDTO request);
}
