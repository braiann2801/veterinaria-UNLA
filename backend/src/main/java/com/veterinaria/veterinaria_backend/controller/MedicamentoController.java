package com.veterinaria.veterinaria_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MovimientoStockResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PaginaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PrecioMedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.service.MedicamentoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Catálogo y stock de medicamentos. Los cuatro roles pueden consultar y cargar stock. */
@RestController
@RequestMapping("/api/v1/medicamentos")
@RequiredArgsConstructor
public class MedicamentoController {

	private final MedicamentoService medicamentoService;

	@GetMapping
	public ResponseEntity<PaginaResponseDTO<MedicamentoResponseDTO>> listar(
			@RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return ResponseEntity.ok(medicamentoService.listar(q, page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<MedicamentoResponseDTO> obtener(@PathVariable Long id) {
		return ResponseEntity.ok(medicamentoService.obtener(id));
	}

	@PostMapping
	public ResponseEntity<MedicamentoResponseDTO> crear(
			@Valid @RequestBody MedicamentoRequestDTO request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(medicamentoService.crear(request, usuarioId(jwt)));
	}

	@PostMapping("/{id}/stock")
	public ResponseEntity<MedicamentoResponseDTO> agregarStock(
			@PathVariable Long id,
			@Valid @RequestBody AgregarStockRequestDTO request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(medicamentoService.agregarStock(id, request, usuarioId(jwt)));
	}

	/**
	 * Cambia el precio de catalogo. Cualquier empleado puede fijarlo, igual que
	 * carga stock; el precio viejo sigue vigente en las comandas ya emitidas.
	 */
	@PutMapping("/{id}/precio")
	public ResponseEntity<MedicamentoResponseDTO> actualizarPrecio(
			@PathVariable Long id,
			@Valid @RequestBody PrecioMedicamentoRequestDTO request) {
		return ResponseEntity.ok(medicamentoService.actualizarPrecio(id, request));
	}

	/** Historial de solo lectura; se muestra dentro del modal de carga de stock. */
	@GetMapping("/{id}/movimientos")
	public ResponseEntity<PaginaResponseDTO<MovimientoStockResponseDTO>> movimientos(
			@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(medicamentoService.movimientos(id, page, size));
	}

	/** El JWT solo lleva {@code sub}; el servicio resuelve el username desde la base. */
	private static Long usuarioId(Jwt jwt) {
		return jwt == null ? null : Long.valueOf(jwt.getSubject());
	}
}
