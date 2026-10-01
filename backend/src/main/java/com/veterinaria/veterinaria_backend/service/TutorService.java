package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResponseDTO;

/**
 * Casos de uso del modulo Tutor.
 *
 * <p>Con {@code spring.jpa.open-in-view=false} la sesion de Hibernate se cierra
 * al salir del metodo de servicio. Toda navegacion de la relacion N:M
 * (incluido el mapeo a DTO) debe ocurrir dentro de una transaccion, por eso las
 * implementaciones anotan los metodos con {@code @Transactional}.</p>
 */
public interface TutorService {

	/** Lista todos los tutores con sus mascotas resumidas. */
	List<TutorResponseDTO> listar();

	/** Busca un tutor por id. Lanza {@code ResourceNotFoundException} si no existe. */
	TutorResponseDTO buscarPorId(Long id);

	/** Crea un tutor con sus vinculos iniciales. Lanza {@code BadRequestException} si el DNI o una mascota no son válidos. */
	TutorResponseDTO crear(TutorRequestDTO request);

	/** Actualiza los datos de un tutor y sincroniza sus vinculos. */
	TutorResponseDTO actualizar(Long id, TutorRequestDTO request);

	/** Elimina un tutor y sus vinculos. Lanza 404 si no existe. */
	void eliminar(Long id);

	/** Mascotas vinculadas al tutor, con el flag de retiro de ese tutor. */
	List<MascotaResponseDTO> listarMascotas(Long tutorId);

	/**
	 * Vincula una mascota a un tutor.
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException si el tutor o la mascota no existen
	 * @throws com.veterinaria.veterinaria_backend.exception.BadRequestException      si el vinculo ya existe
	 */
	MascotaResponseDTO vincularMascota(Long tutorId, Long mascotaId, boolean autorizadoRetiro);

	/** Desvincula una mascota de un tutor. Lanza 404 si el vinculo no existe. */
	void desvincularMascota(Long tutorId, Long mascotaId);
}