package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoActualizacionRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MovimientoStockResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PaginaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PrecioMedicamentoRequestDTO;

/** Catálogo, stock e historial de medicamentos. Accesible a los cuatro roles. */
public interface MedicamentoService {

	/** Listado paginado; por defecto excluye los desactivados (toggle de la UI). */
	PaginaResponseDTO<MedicamentoResponseDTO> listar(String q, boolean incluirInactivos, int page, int size);

	/** Cantidad de desactivados, para el toggle "Mostrar desactivados (N)". */
	long contarInactivos();

	/** Activos bajo el stock mínimo, para los avisos del Mostrador. */
	List<MedicamentoResponseDTO> bajoStock();

	MedicamentoResponseDTO obtener(Long id);

	MedicamentoResponseDTO crear(MedicamentoRequestDTO request, Long usuarioId);

	/** Edita todos los datos del catálogo; el stock se registra como ajuste. */
	MedicamentoResponseDTO actualizar(Long id, MedicamentoActualizacionRequestDTO request, Long usuarioId);

	MedicamentoResponseDTO agregarStock(Long id, AgregarStockRequestDTO request, Long usuarioId);

	/** Cambia el precio de catálogo. Solo afecta a las comandas emitidas desde ahora. */
	MedicamentoResponseDTO actualizarPrecio(Long id, PrecioMedicamentoRequestDTO request);

	/**
	 * Borrado físico, permitido solo si nunca se usó en una ficha. Si se usó,
	 * responde 409 indicando que debe desactivarse.
	 */
	void eliminar(Long id);

	/** Toggle de baja: conserva fichas, comandas y movimientos. */
	MedicamentoResponseDTO desactivar(Long id);

	MedicamentoResponseDTO reactivar(Long id);

	PaginaResponseDTO<MovimientoStockResponseDTO> movimientos(Long id, int page, int size);

	/**
	 * Descuento de stock para una ficha clínica, invocado por
	 * {@code ConsultaMedicaService} dentro de la misma transacción de la ficha.
	 *
	 * @param referenciaId id de la ficha/consulta que se guardará en el movimiento
	 */
	void registrarConsumo(Long medicamentoId, int cantidad, Long referenciaId, Long usuarioId, String observacion);
}
