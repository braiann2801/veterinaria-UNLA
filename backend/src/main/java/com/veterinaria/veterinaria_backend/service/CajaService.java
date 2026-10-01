package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDate;
import java.util.List;

import com.veterinaria.veterinaria_backend.dto.CierreCajaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PagoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.PagoResponseDTO;

/**
 * Casos de uso de la caja multipago (regla 2.5).
 *
 * <p>Es la unica via para bajar el saldo de una comanda. Concentrarlo en un
 * solo servicio evita que dos puntos de entrada distintos apliquen pagos con
 * reglas diferentes y dejen la caja descuadrada.</p>
 */
public interface CajaService {

	/**
	 * Registra uno o varios pagos combinados contra una comanda.
	 *
	 * <p>Todos los pagos de la llamada se aplican o ninguno: es una sola
	 * transaccion.</p>
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.BadRequestException si la suma excede el saldo
	 * @throws com.veterinaria.veterinaria_backend.exception.BusinessConflictException si la comanda esta cancelada
	 */
	ComandaCobroResponseDTO registrarPago(Long comandaId, PagoRequestDTO request);

	/** Pagos aplicados a una comanda. */
	List<PagoResponseDTO> pagosDe(Long comandaId);

	/** Cierre de caja de una jornada, con el arqueo por metodo de pago. */
	CierreCajaResponseDTO cerrarJornada(LocalDate fecha);
}