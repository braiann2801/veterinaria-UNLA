package com.veterinaria.veterinaria_backend.controller;

import java.util.List;

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

import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResponseDTO;
import com.veterinaria.veterinaria_backend.dto.VinculoRequestDTO;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.service.TutorService;

import jakarta.validation.Valid;

/**
 * API REST de tutores.
 *
 * <p>El controller no toca repositorios: solo consume {@link TutorService},
 * que es quien administra las transacciones y traduce entidades a DTOs.</p>
 */
@RestController
@RequestMapping("/api/v1/tutores")
public class TutorController {

	private final TutorService tutorService;

	public TutorController(TutorService tutorService) {
		this.tutorService = tutorService;
	}

	/** GET /api/v1/tutores */
	@GetMapping
	public ResponseEntity<List<TutorResponseDTO>> listar() {
		return ResponseEntity.ok(tutorService.listar());
	}

	/** GET /api/v1/tutores/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<TutorResponseDTO> buscarPorId(@PathVariable Long id) {
		return ResponseEntity.ok(tutorService.buscarPorId(id));
	}

	/** POST /api/v1/tutores -> 201 */
	@PostMapping
	public ResponseEntity<TutorResponseDTO> crear(@Valid @RequestBody TutorRequestDTO request) {
		TutorResponseDTO creado = tutorService.crear(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	/** PUT /api/v1/tutores/{id} */
	@PutMapping("/{id}")
	public ResponseEntity<TutorResponseDTO> actualizar(@PathVariable Long id,
			@Valid @RequestBody TutorRequestDTO request) {
		return ResponseEntity.ok(tutorService.actualizar(id, request));
	}

	/** DELETE /api/v1/tutores/{id} -> 204 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable Long id) {
		tutorService.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	/** GET /api/v1/tutores/{tutorId}/mascotas */
	@GetMapping("/{tutorId}/mascotas")
	public ResponseEntity<List<MascotaResponseDTO>> listarMascotas(@PathVariable Long tutorId) {
		return ResponseEntity.ok(tutorService.listarMascotas(tutorId));
	}

	/**
	 * POST /api/v1/tutores/{tutorId}/mascotas/{mascotaId}
	 *
	 * <p>El flag puede venir por body o por query param. Si no llega ninguno se
	 * responde 400: la regla 2.4 pide que cada vinculo registre el flag de
	 * forma explicita y no queremos asumirlos en silencio.</p>
	 */
	@PostMapping("/{tutorId}/mascotas/{mascotaId}")
	public ResponseEntity<MascotaResponseDTO> vincular(@PathVariable Long tutorId,
			@PathVariable Long mascotaId,
			@RequestParam(required = false) Boolean autorizadoRetiro,
			@RequestBody(required = false) VinculoRequestDTO body) {

		Boolean flag = resolverFlag(autorizadoRetiro, body);
		MascotaResponseDTO vinculada = tutorService.vincularMascota(tutorId, mascotaId, flag);
		return ResponseEntity.status(HttpStatus.CREATED).body(vinculada);
	}

	/** DELETE /api/v1/tutores/{tutorId}/mascotas/{mascotaId} -> 204 */
	@DeleteMapping("/{tutorId}/mascotas/{mascotaId}")
	public ResponseEntity<Void> desvincular(@PathVariable Long tutorId, @PathVariable Long mascotaId) {
		tutorService.desvincularMascota(tutorId, mascotaId);
		return ResponseEntity.noContent().build();
	}

	private Boolean resolverFlag(Boolean queryParam, VinculoRequestDTO body) {
		if (queryParam != null && body != null && body.autorizadoRetiro() != null
				&& !queryParam.equals(body.autorizadoRetiro())) {
			throw new BadRequestException(
					"El flag autorizadoRetiro llega con valores distintos en query param y body");
		}
		if (queryParam != null) {
			return queryParam;
		}
		if (body != null && body.autorizadoRetiro() != null) {
			return body.autorizadoRetiro();
		}
		throw new BadRequestException(
				"Debe indicar explicitamente autorizadoRetiro (query param o body) al vincular");
	}
}