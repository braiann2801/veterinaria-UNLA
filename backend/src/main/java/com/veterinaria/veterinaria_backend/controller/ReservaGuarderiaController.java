package com.veterinaria.veterinaria_backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.AforoGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.service.ReservaGuarderiaService;

import jakarta.validation.Valid;

/**
 * API REST de guarderia.
 *
 * <p>El aforo estricto (maximo 10 concurrentes) se aplica en el servicio. Si
 * ya hay 10 reservas activas ese dia, {@code crear} lanza
 * {@code BusinessConflictException} que se traduce a HTTP 409
 * (AGENTS.md 2.1).</p>
 */
@RestController
@RequestMapping("/api/v1/guarderia")
public class ReservaGuarderiaController {

	private final ReservaGuarderiaService guarderiaService;

	public ReservaGuarderiaController(ReservaGuarderiaService guarderiaService) {
		this.guarderiaService = guarderiaService;
	}

	/**
	 * GET /api/v1/guarderia?fecha=2026-10-01
	 * Devuelve reservas de esa fecha y el aforo ocupado en cada item.
	 */
	@GetMapping
	public ResponseEntity<List<ReservaGuarderiaResponseDTO>> listarPorFecha(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return ResponseEntity.ok(guarderiaService.listarPorFecha(fecha));
	}

	/** GET /api/v1/guarderia/aforo?fecha=2026-10-01 */
	@GetMapping("/aforo")
	public ResponseEntity<AforoGuarderiaResponseDTO> consultarAforo(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return ResponseEntity.ok(guarderiaService.consultarAforo(fecha));
	}

	/**
	 * POST /api/v1/guarderia -> 201 o 409 si aforo completo (>= 10 ocupados).
	 */
	@PostMapping
	public ResponseEntity<ReservaGuarderiaResponseDTO> crear(@Valid @RequestBody ReservaGuarderiaRequestDTO request) {
		ReservaGuarderiaResponseDTO creada = guarderiaService.crear(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(creada);
	}

	/** PUT /api/v1/guarderia/{id} */
	@PutMapping("/{id}")
	public ResponseEntity<ReservaGuarderiaResponseDTO> actualizar(@PathVariable Long id,
			@Valid @RequestBody ReservaGuarderiaRequestDTO request) {
		return ResponseEntity.ok(guarderiaService.actualizar(id, request));
	}

	/** PUT /api/v1/guarderia/{id}/cancelar */
	@PutMapping("/{id}/cancelar")
	public ResponseEntity<ReservaGuarderiaResponseDTO> cancelar(@PathVariable Long id) {
		return ResponseEntity.ok(guarderiaService.cancelar(id));
	}

	/** DELETE /api/v1/guarderia/{id} -> 204 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable Long id) {
		guarderiaService.eliminar(id);
		return ResponseEntity.noContent().build();
	}
}