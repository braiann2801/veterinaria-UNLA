package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResumenDTO;

/**
 * Casos de uso del modulo Mascota. Mismas reglas transaccionales que
 * {@link TutorService}.
 */
public interface MascotaService {

	/** Lista todas las mascotas con sus tutores resumidos. */
	List<MascotaResponseDTO> listar();

	/** Busca una mascota por id. Lanza {@code ResourceNotFoundException} si no existe. */
	MascotaResponseDTO buscarPorId(Long id);

	/** Crea una mascota con sus tutores iniciales. Lanza {@code BadRequestException} si algún tutor no existe. */
	MascotaResponseDTO crear(MascotaRequestDTO request);

	/** Actualiza los datos de una mascota y sincroniza sus tutores. */
	MascotaResponseDTO actualizar(Long id, MascotaRequestDTO request);

	/** Elimina una mascota y sus vinculos. Lanza 404 si no existe. */
	void eliminar(Long id);

	/** Tutores vinculados a la mascota, cada uno con su flag de retiro. */
	List<TutorResumenDTO> listarTutores(Long mascotaId);
}