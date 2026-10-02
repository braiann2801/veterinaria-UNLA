package com.veterinaria.veterinaria_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.EgresoValidacionResponseDTO;
import com.veterinaria.veterinaria_backend.service.EgresoService;

import jakarta.validation.constraints.NotNull;

/**
 * API del circuito de egreso seguro.
 *
 * <p>Un unico endpoint, deliberadamente. El mostrador no necesita "consultar si
 * esta autorizado", "consultar si debe" y "consultar si muerde" por separado:
 * necesita una luz. Separar los checks obligaria al frontend a decidir cuando
 * combines, y esa decision es la regla de negocio, no de la vista.</p>
 *
 * <p>El endpoint no devuelve 409 cuando el retiro esta bloqueado. Bloquear es
 * una respuesta valida: si fuera error, el frontend tendria que atrapar la
 * excepcion para pintar el semaforo en rojo, y el camino del error quedaria
 * fuera de la cobertura de pruebas del camino normal.</p>
 */
@RestController
@RequestMapping("/api/v1/egreso")
public class EgresoController {

	private final EgresoService egresoService;

	public EgresoController(EgresoService egresoService) {
		this.egresoService = egresoService;
	}

	/**
	 * GET /api/v1/egreso/validar?mascotaId=1&tutorDni=30111222
	 *
	 * <p>El DNI es la via natural del mostrador: es lo que se lee de un
	 * documento, sin depender de que el receptor haya encontrado a la persona en
	 * un buscador.</p>
	 */
	@GetMapping("/validar")
	public ResponseEntity<EgresoValidacionResponseDTO> validarPorDni(
			@RequestParam @NotNull Long mascotaId,
			@RequestParam String tutorDni) {
		return ResponseEntity.ok(egresoService.validarPorDni(mascotaId, tutorDni));
	}

	/** GET /api/v1/egreso/validar?mascotaId=1&tutorId=1 */
	@GetMapping("/validar/tutor")
	public ResponseEntity<EgresoValidacionResponseDTO> validarPorTutorId(
			@RequestParam @NotNull Long mascotaId,
			@RequestParam @NotNull Long tutorId) {
		return ResponseEntity.ok(egresoService.validarPorTutorId(mascotaId, tutorId));
	}
}