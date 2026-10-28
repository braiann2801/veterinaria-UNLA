package com.veterinaria.veterinaria_backend.service;

import java.util.List;

import com.veterinaria.veterinaria_backend.dto.UsuarioConClaveTemporalDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioRequestDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioUpdateDTO;

/** ABM de cuentas de empleados. Solo lo usa el Duenio (ver {@code SecurityConfig}). */
public interface UsuarioService {

	List<UsuarioResponseDTO> listar();

	UsuarioConClaveTemporalDTO crear(UsuarioRequestDTO request);

	UsuarioResponseDTO actualizar(Long id, UsuarioUpdateDTO request);

	UsuarioResponseDTO cambiarActivo(Long id, boolean activo, Long solicitanteId);

	UsuarioConClaveTemporalDTO resetearPassword(Long id);

	/** Borra la cuenta. Su sesion abierta cae en el siguiente request (el usuario ya no existe). */
	void eliminar(Long id, Long solicitanteId);
}
