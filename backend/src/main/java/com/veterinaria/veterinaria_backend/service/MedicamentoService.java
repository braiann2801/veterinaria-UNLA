package com.veterinaria.veterinaria_backend.service;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MovimientoStockResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PaginaResponseDTO;

/** Catálogo, stock e historial de medicamentos. Accesible a los cuatro roles. */
public interface MedicamentoService {

	PaginaResponseDTO<MedicamentoResponseDTO> listar(String q, int page, int size);

	MedicamentoResponseDTO obtener(Long id);

	MedicamentoResponseDTO crear(MedicamentoRequestDTO request, Long usuarioId);

	MedicamentoResponseDTO agregarStock(Long id, AgregarStockRequestDTO request, Long usuarioId);

	PaginaResponseDTO<MovimientoStockResponseDTO> movimientos(Long id, int page, int size);

	/**
	 * Descuento de stock para una ficha clínica. Preparado para el siguiente
	 * bloque: {@code ConsultaMedicaService} todavía no lo invoca.
	 *
	 * @param referenciaId id de la ficha/consulta que se guardará en el movimiento
	 */
	void registrarConsumo(Long medicamentoId, int cantidad, Long referenciaId, Long usuarioId, String observacion);
}
