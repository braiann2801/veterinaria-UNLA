package com.veterinaria.veterinaria_backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.veterinaria.veterinaria_backend.dto.CambioPasswordRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;
import com.veterinaria.veterinaria_backend.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Ingreso de empleados.
 *
 * <p>{@code /login} es publico. {@code /me} y {@code /cambiar-password} exigen
 * token, pero se permiten aun con el cambio de clave pendiente: es lo unico que
 * puede hacer el Duenio recien sembrado (ver {@code SecurityConfig}).</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** POST /api/v1/auth/login */
	@PostMapping("/login")
	public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request,
			HttpServletRequest http) {
		// getRemoteAddr y no X-Forwarded-For: ese header lo escribe el cliente y se puede falsificar.
		// no-store: la respuesta lleva el token y no debe quedar en caches intermedias.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(authService.login(request, http.getRemoteAddr()));
	}

	/** GET /api/v1/auth/me */
	@GetMapping("/me")
	public ResponseEntity<UsuarioResponseDTO> me(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(authService.me(Long.valueOf(jwt.getSubject())));
	}

	/** POST /api/v1/auth/logout */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
		if (jwt != null && jwt.getSubject() != null) {
			authService.logout(Long.valueOf(jwt.getSubject()));
		}
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore())
				.build();
	}

	/** POST /api/v1/auth/cambiar-password */
	@PostMapping("/cambiar-password")
	public ResponseEntity<LoginResponseDTO> cambiarPassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CambioPasswordRequestDTO request,
			HttpServletRequest http) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(authService.cambiarPassword(Long.valueOf(jwt.getSubject()), request, http.getRemoteAddr()));
	}
}
