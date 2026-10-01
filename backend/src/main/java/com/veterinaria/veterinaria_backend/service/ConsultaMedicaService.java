package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO;

/**
 * Casos de uso del modulo Ficha Clinica.
 *
 * <p><b>Regla 2.3.</b> Ningun metodo de esta interfaz recibe ni devuelve
 * informacion de cobro. La ficha se guarda y, en la misma transaccion, se emite
 * una comanda PENDIENTE en el modulo de caja: el veterinario nunca ve el saldo
 * ni el medio de pago.</p>
 */
public interface ConsultaMedicaService {

	/**
	 * Registra la atencion y emite la comanda de cobro en PENDIENTE.
	 *
	 * <p>Ambas escrituras ocurren en la misma transaccion: si la comanda no se
	 * puede emitir, la ficha tampoco queda registrada.</p>
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.BadRequestException si el turno ya tiene ficha
	 */
	ConsultaMedicaResponseDTO registrar(ConsultaMedicaRequestDTO request);

	ConsultaMedicaResponseDTO buscarPorId(Long id);

	/** Historial clinico de una mascota. */
	List<ConsultaMedicaResponseDTO> listarPorMascota(Long mascotaId);

	/**
	 * Consulta la comanda emitida para una ficha, para verificar en recepcion
	 * que el cobro quedo registrado sin entrar al detalle clinico.
	 */
	Long comandaDe(Long consultaId);
}