package com.veterinaria.veterinaria_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalResponseDTO;
import com.veterinaria.veterinaria_backend.service.ProfesionalService;

import jakarta.validation.Valid;

/**
 * API REST de profesionales clinicos.
 *
 * <p>Nace porque la validacion de solapamiento de turnos es por profesional
 * (AGENTS.md 2.2) y hacia falta una entidad real, no un string libre. Consume
 * unicamente {@link ProfesionalService}.</p>
 */
@RestController
@RequestMapping("/api/v1/profesionales")
public class ProfesionalController {

	private final ProfesionalService profesionalService;

	public ProfesionalController(ProfesionalService profesionalService) {
		this.profesionalService = profesionalService;
	}

	/** GET /api/v1/profesionales */
	@GetMapping
	public ResponseEntity<List<ProfesionalResponseDTO>> listar() {
		return ResponseEntity.ok(profesionalService.listar());
	}

	/** POST /api/v1/profesionales -> 201 */
	@PostMapping
	public ResponseEntity<ProfesionalResponseDTO> crear(@Valid @RequestBody ProfesionalRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(profesionalService.crear(request));
	}
}