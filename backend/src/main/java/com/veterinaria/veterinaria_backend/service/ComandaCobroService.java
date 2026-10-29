package com.veterinaria.veterinaria_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.veterinaria.veterinaria_backend.dto.ComandaCobroRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.DeudaTutorResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;

/**
 * Casos de uso de las comandas de cobro.
 *
 * <p>Es el unico modulo con logica contable. El saldo de una comanda solo baja
 * por {@link CajaService#registrarPago}; aqui no se toca.</p>
 */
public interface ComandaCobroService {

	/** Comandas de un tutor, opcionalmente filtradas por estado. */
	List<ComandaCobroResponseDTO> listarPorTutor(Long tutorId, String estado);

	/**
	 * Comandas emitidas en una jornada: la caja del dia.
	 *
	 * <p>Los tres filtros se combinan. Sin tutor devuelve todas las de la fecha,
	 * que es lo que el mostrador consulta al abrir la jornada.</p>
	 *
	 * @param fecha  dia a listar; null significa hoy
	 * @param tutorId tutor a acotar, o null para todas
	 * @param estado estado a acotar, o null para todos
	 */
	List<ComandaCobroResponseDTO> listarDelDia(LocalDate fecha, Long tutorId, String estado);

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

	/**
	 * Emite la comanda de GUARDERIA de una reserva recien creada, a nombre del
	 * tutor autorizado a retirar mas antiguo (o del primero vinculado si ninguno
	 * esta autorizado). Devuelve el id, o {@code null} si la mascota no tiene
	 * tutor: sin titular no hay a quien cobrarle.
	 */
	Long emitirParaReserva(ReservaGuarderia reserva, java.util.function.Supplier<java.math.BigDecimal> monto);

	/** Al cancelar una reserva: anula sus comandas sin pagos y deja las que ya tienen pagos. */
	void anularDeReserva(Long reservaId);

	/** Al eliminar una reserva: borra sus comandas sin pagos; si alguna tiene pagos responde 409. */
	void eliminarDeReserva(Long reservaId);

	/** Id de la comanda vigente de una reserva, o {@code null}. */
	Long comandaDeReserva(Long reservaId);

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