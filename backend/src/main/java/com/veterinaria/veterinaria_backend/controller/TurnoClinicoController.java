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

import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.service.TurnoClinicoService;

import jakarta.validation.Valid;

/**
 * API REST de turnos clinicos.
 *
 * <p>Consume unicamente {@link TurnoClinicoService}. El solapamiento de bloques
 * se traduce a 409 via {@code BusinessConflictException}.</p>
 */
@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoClinicoController {

	private final TurnoClinicoService turnoService;

	public TurnoClinicoController(TurnoClinicoService turnoService) {
		this.turnoService = turnoService;
	}

	/** GET /api/v1/turnos?fecha=2026-10-01 */
	@GetMapping
	public ResponseEntity<List<TurnoClinicoResponseDTO>> listarPorFecha(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return ResponseEntity.ok(turnoService.listarPorFecha(fecha));
	}

	/** GET /api/v1/turnos/profesional/{profesionalId}?fecha=2026-10-01 */
	@GetMapping("/profesional/{profesionalId}")
	public ResponseEntity<List<TurnoClinicoResponseDTO>> listarPorProfesional(
			@PathVariable Long profesionalId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return ResponseEntity.ok(turnoService.listarPorProfesional(profesionalId, fecha));
	}

	/**
	 * GET /api/v1/turnos/disponibilidad?profesionalId=1&inicio=2026-10-01T09:00:00
	 * Permite al frontend deshabilitar horarios antes de enviar el POST.
	 */
	@GetMapping("/disponibilidad")
	public ResponseEntity<DisponibilidadResponse> consultarDisponibilidad(
			@RequestParam Long profesionalId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime inicio) {
		return ResponseEntity.ok(
				new DisponibilidadResponse(turnoService.disponible(profesionalId, inicio)));
	}

	/** POST /api/v1/turnos -> 201, o 409 si el bloque se solapa */
	@PostMapping
	public ResponseEntity<TurnoClinicoResponseDTO> crear(@Valid @RequestBody TurnoClinicoRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(turnoService.crear(request));
	}

	/** PUT /api/v1/turnos/{id} -> 200, o 409 si al reprogramar se solapa */
	@PutMapping("/{id}")
	public ResponseEntity<TurnoClinicoResponseDTO> actualizar(@PathVariable Long id,
			@Valid @RequestBody TurnoClinicoRequestDTO request) {
		return ResponseEntity.ok(turnoService.actualizar(id, request));
	}

	/** PATCH /api/v1/turnos/{id}/cancelar */
	@PutMapping("/{id}/cancelar")
	public ResponseEntity<TurnoClinicoResponseDTO> cancelar(@PathVariable Long id) {
		return ResponseEntity.ok(turnoService.cancelar(id));
	}

	/** DELETE /api/v1/turnos/{id} -> 204 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable Long id) {
		turnoService.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	/** Respuesta mínima del chequeo de disponibilidad. */
	public record DisponibilidadResponse(boolean disponible) {
	}
}