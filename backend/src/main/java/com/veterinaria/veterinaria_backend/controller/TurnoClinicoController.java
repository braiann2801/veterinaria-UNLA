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
import com.veterinaria.veterinaria_backend.dto.TurnoConAltaRequestDTO;
import com.veterinaria.veterinaria_backend.service.TurnoClinicoService;
import com.veterinaria.veterinaria_backend.service.TurnoConAltaService;

import jakarta.validation.Valid;

/**
 * API REST de turnos clinicos.
 *
 * <p>Consume {@link TurnoClinicoService} y, para el alta combinada,
 * {@link TurnoConAltaService}. El solapamiento de bloques
 * se traduce a 409 via {@code BusinessConflictException}.</p>
 */
@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoClinicoController {

	private final TurnoClinicoService turnoService;
	private final TurnoConAltaService turnoConAltaService;

	public TurnoClinicoController(TurnoClinicoService turnoService, TurnoConAltaService turnoConAltaService) {
		this.turnoService = turnoService;
		this.turnoConAltaService = turnoConAltaService;
	}

	/**
	 * GET /api/v1/turnos?fecha=2026-10-01, o GET /api/v1/turnos?desde=2026-10-01&hasta=2026-10-31
	 * para la vista mensual. Con {@code desde} o {@code hasta} manda el rango.
	 */
	@GetMapping
	public ResponseEntity<List<TurnoClinicoResponseDTO>> listarPorFecha(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		if (desde != null || hasta != null) {
			return ResponseEntity.ok(turnoService.listarEntre(desde, hasta));
		}
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

	/**
	 * POST /api/v1/turnos/con-alta -> 201. Cliente y mascota nuevos (o mascota
	 * nueva de un cliente existente) y el turno, todo o nada: 400 por datos o
	 * combinacion invalida, 404 si el cliente no existe, 409 si el turno se solapa.
	 */
	@PostMapping("/con-alta")
	public ResponseEntity<TurnoClinicoResponseDTO> crearConAlta(@Valid @RequestBody TurnoConAltaRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(turnoConAltaService.agendar(request));
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