package com.veterinaria.veterinaria_backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.veterinaria.veterinaria_backend.dto.ComandaCobroRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.DeudaTutorResponseDTO;

/**
 * Casos de uso de las comandas de cobro.
 *
 * <p>Es el unico modulo con logica contable. El saldo de una comanda solo baja
 * por {@link CajaService#registrarPago}; aqui no se toca.</p>
 */
public interface ComandaCobroService {

	/** Comandas de un tutor, opcionalmente filtradas por estado. */
	List<ComandaCobroResponseDTO> listarPorTutor(Long tutorId, String estado);

	ComandaCobroResponseDTO buscarPorId(Long id);

	/**
	 * Emite una comanda manual (guarderia, venta de insumos).
	 *
	 * <p>Las consultas clinicas no pasan por aca: su comanda se emite sola al
	 * guardar la ficha.</p>
	 */
	ComandaCobroResponseDTO emitir(ComandaCobroRequestDTO request);

	/** Cancela una comanda y la saca del conteo de deuda. */
	ComandaCobroResponseDTO cancelar(Long id);

	/** Estado consolidado de morosidad de un tutor (regla 2.5). */
	DeudaTutorResponseDTO consultarDeuda(Long tutorId);

	/** Precios vigentes de todos los conceptos. */
	Map<String, BigDecimal> listarTarifas();

	/**
	 * Fija el precio de un concepto. Solo para dar de alta la tarifa inicial o
	 * cambiar precios: no modifica las comandas ya emitidas, que congelaron su
	 * monto al emitirse.
	 */
	BigDecimal actualizarTarifa(String concepto, BigDecimal monto);
}