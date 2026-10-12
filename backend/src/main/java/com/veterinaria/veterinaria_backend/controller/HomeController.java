package com.veterinaria.veterinaria_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check basico de la API.
 *
 * <p>Responde en la raiz (GET /) sin tocar la base de datos, para saber de un
 * vistazo si el proceso esta arriba. Tipico uso: sonda manual en Docker Compose
 * o monitor de salud del backend.</p>
 */
@RestController
public class HomeController {

	@GetMapping("/")
	public Map<String, String> home() {
		return Map.of(
				"status", "UP",
				"message", "API de Veterinaria funcionando correctamente");
	}
}