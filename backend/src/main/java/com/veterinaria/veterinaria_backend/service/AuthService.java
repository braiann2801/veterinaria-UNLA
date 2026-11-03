package com.veterinaria.veterinaria_backend.service;

import com.veterinaria.veterinaria_backend.dto.CambioPasswordRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;

public interface AuthService {

	/**
	 * Ingreso con usuario y clave. {@code ip} identifica el origen para el limite
	 * de intentos fallidos: 429 si el par usuario/IP esta bloqueado.
	 */
	LoginResponseDTO login(LoginRequestDTO request, String ip);

	UsuarioResponseDTO me(Long usuarioId);

	/** Cambia la propia clave, cierra las demas sesiones y devuelve un token nuevo. */
	LoginResponseDTO cambiarPassword(Long usuarioId, CambioPasswordRequestDTO request);
}
