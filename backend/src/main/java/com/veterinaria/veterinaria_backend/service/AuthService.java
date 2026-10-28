package com.veterinaria.veterinaria_backend.service;

import com.veterinaria.veterinaria_backend.dto.CambioPasswordRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;

public interface AuthService {

	LoginResponseDTO login(LoginRequestDTO request);

	UsuarioResponseDTO me(Long usuarioId);

	/** Cambia la propia clave, cierra las demas sesiones y devuelve un token nuevo. */
	LoginResponseDTO cambiarPassword(Long usuarioId, CambioPasswordRequestDTO request);
}
