package com.veterinaria.veterinaria_backend.controller;

import java.util.List;

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

import com.veterinaria.veterinaria_backend.dto.ExtraRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ExtraResponseDTO;
import com.veterinaria.veterinaria_backend.service.ExtraService;

import jakarta.validation.Valid;

/**
 * Catalogo de extras. GET para cualquier empleado (la ficha lo necesita);
 * POST y PUT solo el Duenio, aplicado en SecurityConfig. No hay DELETE: un
 * extra se desactiva con {@code activo=false}.
 */
@RestController
@RequestMapping("/api/v1/extras")
public class ExtraController {

	private final ExtraService extraService;

	public ExtraController(ExtraService extraService) {
		this.extraService = extraService;
	}

	/** GET /api/v1/extras?soloActivos=true */
	@GetMapping
	public ResponseEntity<List<ExtraResponseDTO>> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
		return ResponseEntity.ok(extraService.listar(soloActivos));
	}

	@PostMapping
	public ResponseEntity<ExtraResponseDTO> crear(@Valid @RequestBody ExtraRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(extraService.crear(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ExtraResponseDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ExtraRequestDTO request) {
		return ResponseEntity.ok(extraService.actualizar(id, request));
	}
}
