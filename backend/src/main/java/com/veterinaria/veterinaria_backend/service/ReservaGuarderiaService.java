package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDate;
import java.util.List;

import com.veterinaria.veterinaria_backend.dto.AforoGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaResponseDTO;

/**
 * Casos de uso del modulo Guarderia.
 *
 * <p>Regla innegociable: maximo {@code ReservaGuarderia.AFORO_MAXIMO} (10)
 * perros concurrentes por dia (AGENTS.md 2.1). Ningun metodo de esta interfaz
 * puede confirmar una reserva si el aforo ya esta completo.</p>
 */
public interface ReservaGuarderiaService {

	/** Reservas de una fecha, con el aforo del dia en cada item. */
	List<ReservaGuarderiaResponseDTO> listarPorFecha(LocalDate fecha);

	/**
	 * Crea una reserva respetando el aforo.
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.BusinessConflictException si el dia ya tiene 10 cupos ocupados
	 * @throws com.veterinaria.veterinaria_backend.exception.BadRequestException       si la mascota ya tiene reserva activa ese dia
	 */
	ReservaGuarderiaResponseDTO crear(ReservaGuarderiaRequestDTO request);

	/** Actualiza tipo de estadia, estado, sena u observaciones. No cambia la fecha. */
	ReservaGuarderiaResponseDTO actualizar(Long id, ReservaGuarderiaRequestDTO request);

	/** Cancela una reserva liberando el cupo del dia. */
	ReservaGuarderiaResponseDTO cancelar(Long id);

	/** Elimina fisicamente la reserva. */
	void eliminar(Long id);

	/** Estado del aforo de una fecha. */
	AforoGuarderiaResponseDTO consultarAforo(LocalDate fecha);
}