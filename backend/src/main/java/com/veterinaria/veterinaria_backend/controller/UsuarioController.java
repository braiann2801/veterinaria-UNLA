package com.veterinaria.veterinaria_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.UsuarioActivoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioConClaveTemporalDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioRequestDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioUpdateDTO;
import com.veterinaria.veterinaria_backend.service.UsuarioService;

import jakarta.validation.Valid;

/** ABM de cuentas de empleados. Solo para el Duenio (regla en {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	/** GET /api/v1/usuarios */
	@GetMapping
	public ResponseEntity<List<UsuarioResponseDTO>> listar() {
		return ResponseEntity.ok(usuarioService.listar());
	}

	/** POST /api/v1/usuarios */
	@PostMapping
	public ResponseEntity<UsuarioConClaveTemporalDTO> crear(@Valid @RequestBody UsuarioRequestDTO request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
	}

	/** PUT /api/v1/usuarios/{id} */
	@PutMapping("/{id}")
	public ResponseEntity<UsuarioResponseDTO> actualizar(@PathVariable Long id,
			@Valid @RequestBody UsuarioUpdateDTO request) {
		return ResponseEntity.ok(usuarioService.actualizar(id, request));
	}

	/** PUT /api/v1/usuarios/{id}/activo */
	@PutMapping("/{id}/activo")
	public ResponseEntity<UsuarioResponseDTO> cambiarActivo(@PathVariable Long id,
			@Valid @RequestBody UsuarioActivoRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(usuarioService.cambiarActivo(id, request.activo(), Long.valueOf(jwt.getSubject())));
	}

	/** POST /api/v1/usuarios/{id}/reset-password */
	@PostMapping("/{id}/reset-password")
	public ResponseEntity<UsuarioConClaveTemporalDTO> resetearPassword(@PathVariable Long id) {
		return ResponseEntity.ok(usuarioService.resetearPassword(id));
	}
}
