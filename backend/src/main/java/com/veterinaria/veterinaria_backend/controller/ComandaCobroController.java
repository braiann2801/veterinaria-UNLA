package com.veterinaria.veterinaria_backend.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.ComandaCobroRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.service.ComandaCobroService;

import jakarta.validation.Valid;

/**
 * API REST de las comandas de cobro.
 *
 * <p>Es el contrato del mostrador: a diferencia del clinico, aqui si hay
 * montos. No baja saldos: eso es tarea de {@link CajaController}. Emitir,
 * consultar y cancelar es todo lo que este controller hace.</p>
 */
@RestController
@RequestMapping("/api/v1/comandas")
public class ComandaCobroController {

	private final ComandaCobroService comandaService;

	public ComandaCobroController(ComandaCobroService comandaService) {
		this.comandaService = comandaService;
	}

	/** GET /api/v1/comandas?tutorId=1&estado=PENDIENTE */
	@GetMapping
	public ResponseEntity<List<ComandaCobroResponseDTO>> listar(
			@RequestParam Long tutorId,
			@RequestParam(required = false) String estado) {
		return ResponseEntity.ok(comandaService.listarPorTutor(tutorId, estado));
	}

	/** GET /api/v1/comandas/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<ComandaCobroResponseDTO> buscarPorId(@PathVariable Long id) {
		return ResponseEntity.ok(comandaService.buscarPorId(id));
	}

	/** POST /api/v1/comandas -> 201. Emite una comanda manual (guarderia, insumos). */
	@PostMapping
	public ResponseEntity<ComandaCobroResponseDTO> emitir(
			@Valid @RequestBody ComandaCobroRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(comandaService.emitir(request));
	}

	/** PUT /api/v1/comandas/{id}/cancelar */
	@PutMapping("/{id}/cancelar")
	public ResponseEntity<ComandaCobroResponseDTO> cancelar(@PathVariable Long id) {
		return ResponseEntity.ok(comandaService.cancelar(id));
	}

	/** GET /api/v1/comandas/tarifas */
	@GetMapping("/tarifas")
	public ResponseEntity<Map<String, BigDecimal>> listarTarifas() {
		return ResponseEntity.ok(comandaService.listarTarifas());
	}

	/** PUT /api/v1/comandas/tarifas/{concepto} */
	@PutMapping("/tarifas/{concepto}")
	public ResponseEntity<TarifaActualizadaDTO> actualizarTarifa(
			@PathVariable String concepto,
			@RequestBody TarifaRequestDTO request) {
		BigDecimal monto = comandaService.actualizarTarifa(concepto, request.monto());
		return ResponseEntity.ok(new TarifaActualizadaDTO(concepto.toUpperCase(), monto));
	}

	/**
	 * Precio de un concepto.
	 *
	 * <p>No acepta cantidad: la tarifa es por unidad de concepto. El monto de
	 * una comanda de guarderia por varios dias lo define el mostrador con otra
	 * via cuando haga falta, no esta.</p>
	 */
	public record TarifaRequestDTO(BigDecimal monto) {
	}

	/** Confirmacion de la tarifa vigente tras el cambio. */
	public record TarifaActualizadaDTO(String concepto, BigDecimal monto) {
	}
}