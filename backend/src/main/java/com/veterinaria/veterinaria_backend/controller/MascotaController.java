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
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.DeudaMascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResumenDTO;
import com.veterinaria.veterinaria_backend.service.ComandaCobroService;
import com.veterinaria.veterinaria_backend.service.MascotaService;

import jakarta.validation.Valid;

/**
 * API REST de mascotas. Solo consume servicios, nunca repositorios.
 */
@RestController
@RequestMapping("/api/v1/mascotas")
public class MascotaController {

	private final MascotaService mascotaService;
	private final ComandaCobroService comandaService;

	public MascotaController(MascotaService mascotaService, ComandaCobroService comandaService) {
		this.mascotaService = mascotaService;
		this.comandaService = comandaService;
	}

	/** GET /api/v1/mascotas */
	@GetMapping
	public ResponseEntity<List<MascotaResponseDTO>> listar() {
		return ResponseEntity.ok(mascotaService.listar());
	}

	/** GET /api/v1/mascotas/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<MascotaResponseDTO> buscarPorId(@PathVariable Long id) {
		return ResponseEntity.ok(mascotaService.buscarPorId(id));
	}

	/** POST /api/v1/mascotas -> 201 */
	@PostMapping
	public ResponseEntity<MascotaResponseDTO> crear(@Valid @RequestBody MascotaRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(mascotaService.crear(request));
	}

	/** PUT /api/v1/mascotas/{id} */
	@PutMapping("/{id}")
	public ResponseEntity<MascotaResponseDTO> actualizar(@PathVariable Long id,
			@Valid @RequestBody MascotaRequestDTO request) {
		return ResponseEntity.ok(mascotaService.actualizar(id, request));
	}

	/** DELETE /api/v1/mascotas/{id} -> 204: desactiva (no borra; el historial queda). */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable Long id) {
		mascotaService.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	/** PUT .../{id}/reactivar -> 200 con el registro activo de nuevo. */
	@PutMapping("/{id}/reactivar")
	public ResponseEntity<MascotaResponseDTO> reactivar(@PathVariable Long id) {
		return ResponseEntity.ok(mascotaService.reactivar(id));
	}

	/** GET /api/v1/mascotas/{mascotaId}/tutores */
	@GetMapping("/{mascotaId}/tutores")
	public ResponseEntity<List<TutorResumenDTO>> listarTutores(@PathVariable Long mascotaId) {
		return ResponseEntity.ok(mascotaService.listarTutores(mascotaId));
	}

	/**
	 * GET /api/v1/mascotas/{mascotaId}/deuda
	 *
	 * <p>Saldo pendiente de la mascota con el detalle de cada comanda y su fecha
	 * de emision. Es el mismo calculo que bloquea el egreso: mostrarlo en la caja
	 * evita que el mostrador tenga que sumar a mano lo que ve en pantalla.</p>
	 */
	@GetMapping("/{mascotaId}/deuda")
	public ResponseEntity<DeudaMascotaResponseDTO> consultarDeuda(@PathVariable Long mascotaId) {
		return ResponseEntity.ok(comandaService.consultarDeudaDeMascota(mascotaId));
	}
}
