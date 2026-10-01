package com.veterinaria.veterinaria_backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.CierreCajaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PagoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.PagoResponseDTO;
import com.veterinaria.veterinaria_backend.service.CajaService;

import jakarta.validation.Valid;

/**
 * API REST de la caja multipago (regla 2.5).
 *
 * <p><b>Este es el unico endpoint del sistema que puede bajar el saldo de una
 * comanda.</b> Concentrarlo aqui evita que aparezcan caminos alternativos que
 * apliquen pagos con reglas distintas.</p>
 *
 * <p>El endpoint acepta uno o varios pagos en la misma llamada porque el
 * mostrador cobra combinado con frecuencia. Varios medios en una request no
 * significa pagos separados: es una sola operacion atomica.</p>
 */
@RestController
@RequestMapping("/api/v1/caja")
public class CajaController {

	private final CajaService cajaService;

	public CajaController(CajaService cajaService) {
		this.cajaService = cajaService;
	}

	/** POST /api/v1/caja/pagos/{comandaId} -> 200 */
	@PostMapping("/pagos/{comandaId}")
	public ResponseEntity<ComandaCobroResponseDTO> registrarPago(
			@PathVariable Long comandaId,
			@Valid @RequestBody PagoRequestDTO request) {
		return ResponseEntity.ok(cajaService.registrarPago(comandaId, request));
	}

	/** GET /api/v1/caja/pagos/{comandaId} */
	@GetMapping("/pagos/{comandaId}")
	public ResponseEntity<List<PagoResponseDTO>> pagosDe(@PathVariable Long comandaId) {
		return ResponseEntity.ok(cajaService.pagosDe(comandaId));
	}

	/** GET /api/v1/caja/cierre?fecha=2026-09-30 */
	@GetMapping("/cierre")
	public ResponseEntity<CierreCajaResponseDTO> cerrarJornada(
			@RequestParam(required = false) LocalDate fecha) {
		return ResponseEntity.ok(cajaService.cerrarJornada(fecha));
	}
}