package com.veterinaria.veterinaria_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO;
import com.veterinaria.veterinaria_backend.service.ConsultaMedicaService;

import jakarta.validation.Valid;

/**
 * API REST de la ficha clinica.
 *
 * <p><b>Acoplamiento cero con la caja (AGENTS.md 2.3).</b> Los tres endpoints
 * usan contratos que no declaran monto, saldo ni medio de pago: no hay forma de
 * que esta API acepte un cobro ni de que devuelva uno. El unico rastro del
 * comanda es el {@code comandaId} de la respuesta, que es una llave para que
 * el mostrador consulte {@code /api/v1/comandas/{id}} por su lado.</p>
 *
 * <p>Guardar la ficha ({@code POST}) emite la comanda PENDIENTE en la misma
 * transaccion, aunque el endpoint no lo mencione: el cobro se genera solo.</p>
 */
@RestController
@RequestMapping("/api/v1/consultas")
public class ConsultaMedicaController {

	private final ConsultaMedicaService consultaService;

	public ConsultaMedicaController(ConsultaMedicaService consultaService) {
		this.consultaService = consultaService;
	}

	/** POST /api/v1/consultas -> 201 */
	@PostMapping
	public ResponseEntity<ConsultaMedicaResponseDTO> registrar(
			@Valid @RequestBody ConsultaMedicaRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(consultaService.registrar(request));
	}

	/** GET /api/v1/consultas/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<ConsultaMedicaResponseDTO> buscarPorId(@PathVariable Long id) {
		return ResponseEntity.ok(consultaService.buscarPorId(id));
	}

	/** GET /api/v1/consultas/mascota/{mascotaId} */
	@GetMapping("/mascota/{mascotaId}")
	public ResponseEntity<List<ConsultaMedicaResponseDTO>> listarPorMascota(
			@PathVariable Long mascotaId) {
		return ResponseEntity.ok(consultaService.listarPorMascota(mascotaId));
	}

	/**
	 * GET /api/v1/consultas/{id}/comanda
	 *
	 * <p>Verificacion de emision para recepcion: confirma que la atencion
	 * genero su comanda sin necessitate abrir el detalle clinico, que no le
	 * corresponde a quien cobra.</p>
	 */
	@GetMapping("/{id}/comanda")
	public ResponseEntity<ComandaEmitidaDTO> verificarEmision(@PathVariable Long id) {
		return ResponseEntity.ok(new ComandaEmitidaDTO(id, consultaService.comandaDe(id)));
	}

	/**
	 * Confirmacion de que una ficha emitio comanda.
	 *
	 * <p>No expone monto ni saldo a proposito: recepcion solo necesita saber si
	 * hay comanda y cual es. El importe se consulta en el modulo de caja.</p>
	 */
	public record ComandaEmitidaDTO(Long consultaId, Long comandaId, boolean emitida) {

		public ComandaEmitidaDTO(Long consultaId, Long comandaId) {
			this(consultaId, comandaId, comandaId != null);
		}
	}
}