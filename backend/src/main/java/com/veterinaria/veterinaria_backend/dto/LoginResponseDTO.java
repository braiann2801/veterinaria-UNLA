package com.veterinaria.veterinaria_backend.dto;

import java.time.Instant;

/**
 * Sesion emitida. El token se manda en {@code Authorization: Bearer ...}.
 * {@code expiraEn} permite al frontend cerrar la sesion antes de que el backend
 * empiece a responder 401.
 */
public record LoginResponseDTO(
		String token,
		Instant expiraEn,
		UsuarioResponseDTO usuario) {
}
