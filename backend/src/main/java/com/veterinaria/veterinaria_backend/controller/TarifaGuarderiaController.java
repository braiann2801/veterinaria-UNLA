package com.veterinaria.veterinaria_backend.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.CotizacionGuarderiaDTO;
import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.service.TarifaGuarderiaService;

import jakarta.validation.Valid;

/**
 * Precios de guarderia y cotizacion de una estadia.
 *
 * <p>Consultar y cotizar: cualquier empleado (el modal de reserva muestra el
 * monto). Cambiar los precios: solo el Duenio (regla en {@code SecurityConfig}).</p>
 */
@RestController
@RequestMapping("/api/v1/guarderia")
public class TarifaGuarderiaController {

	private final TarifaGuarderiaService tarifaService;

	public TarifaGuarderiaController(TarifaGuarderiaService tarifaService) {
		this.tarifaService = tarifaService;
	}

	/** GET /api/v1/guarderia/tarifas */
	@GetMapping("/tarifas")
	public ResponseEntity<TarifaGuarderiaResponseDTO> obtener() {
		return ResponseEntity.ok(tarifaService.obtener());
	}

	/** PUT /api/v1/guarderia/tarifas */
	@PutMapping("/tarifas")
	public ResponseEntity<TarifaGuarderiaResponseDTO> actualizar(@Valid @RequestBody TarifaGuarderiaRequestDTO request) {
		return ResponseEntity.ok(tarifaService.actualizar(request));
	}

	/** GET /api/v1/guarderia/cotizar?desde=2031-06-02T09:00:00&hasta=2031-06-04T10:00:00 */
	@GetMapping("/cotizar")
	public ResponseEntity<CotizacionGuarderiaDTO> cotizar(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
		return ResponseEntity.ok(tarifaService.cotizar(desde, hasta));
	}
}
