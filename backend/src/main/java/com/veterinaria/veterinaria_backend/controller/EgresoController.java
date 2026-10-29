package com.veterinaria.veterinaria_backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.EgresoRegistradoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.EgresoRegistroRequestDTO;
import com.veterinaria.veterinaria_backend.dto.EgresoValidacionResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaDelDiaResponseDTO;
import com.veterinaria.veterinaria_backend.service.EgresoService;

import jakarta.validation.Valid;
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

	/**
	 * GET /api/v1/egreso/pacientes-hoy?fecha=2026-10-03
	 *
	 * <p>La fecha es opcional y por defecto es hoy: el mostrador que abre el
	 * mostrador de egreso esta esperando la jornada que esta cerrando, no un
	 * archivo historico. El selector de dias anteriores existe para el caso real de
	 * un retiro que quedo pendiente ayer.</p>
	 *
	 * <p>Devuelve las mascotas con turno o guarderia en esa fecha, con sus tutores
	 * y el flag de retiro de cada vinculo. No es el semaforo: es la lista de
	 * candidatos sobre la que después se corre {@code /validar}.</p>
	 */
	/**
	 * POST /api/v1/egreso/registrar -> 201
	 *
	 * <p>"Registrar entrega y cerrar". Revalida el retiro en el servidor: 409 si
	 * no esta autorizado, tiene deuda o ya se entrego en la jornada.</p>
	 */
	@PostMapping("/registrar")
	public ResponseEntity<EgresoRegistradoResponseDTO> registrarEntrega(
			@Valid @RequestBody EgresoRegistroRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(egresoService.registrarEntrega(request));
	}

	@GetMapping("/pacientes-hoy")
	public ResponseEntity<List<MascotaDelDiaResponseDTO>> pacientesDelDia(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return ResponseEntity.ok(egresoService.listarPacientesDelDia(fecha));
	}
}