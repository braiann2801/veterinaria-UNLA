package com.veterinaria.veterinaria_backend.service.impl;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.CambioPasswordRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginRequestDTO;
import com.veterinaria.veterinaria_backend.dto.LoginResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.CredencialesInvalidasException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;
import com.veterinaria.veterinaria_backend.security.TokenService;
import com.veterinaria.veterinaria_backend.service.AuthService;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final EntityMapper mapper;
	/**
	 * Hash contra el que se compara cuando el usuario no existe: el login tarda
	 * lo mismo exista o no la cuenta, y el tiempo de respuesta no la delata.
	 */
	private final String hashSenuelo;

	public AuthServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			TokenService tokenService, EntityMapper mapper) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.mapper = mapper;
		this.hashSenuelo = passwordEncoder.encode("senuelo-para-tiempo-constante");
	}

	/** Misma normalizacion que el alta de usuarios: el login no distingue mayusculas. */
	public static String normalizarUsername(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public LoginResponseDTO login(LoginRequestDTO request) {
		Usuario usuario = usuarioRepository.findByUsername(normalizarUsername(request.username())).orElse(null);
		boolean claveOk = passwordEncoder.matches(request.password(),
				usuario != null ? usuario.getPasswordHash() : hashSenuelo);
		if (usuario == null || !claveOk || !usuario.isActivo()) {
			throw new CredencialesInvalidasException();
		}
		return tokenService.emitir(usuario);
	}

	@Override
	public UsuarioResponseDTO me(Long usuarioId) {
		return mapper.toResponse(buscar(usuarioId));
	}

	@Override
	@Transactional
	public LoginResponseDTO cambiarPassword(Long usuarioId, CambioPasswordRequestDTO request) {
		Usuario usuario = buscar(usuarioId);
		if (!passwordEncoder.matches(request.actual(), usuario.getPasswordHash())) {
			throw new BadRequestException("La contraseña actual no es correcta");
		}
		if (request.actual().equals(request.nueva())) {
			throw new BadRequestException("La contraseña nueva tiene que ser distinta de la actual");
		}
		usuario.setPasswordHash(passwordEncoder.encode(request.nueva()));
		usuario.setDebeCambiarPassword(false);
		usuario.invalidarSesiones();
		return tokenService.emitir(usuarioRepository.saveAndFlush(usuario));
	}

	private Usuario buscar(Long usuarioId) {
		return usuarioRepository.findById(usuarioId)
				.orElseThrow(() -> new ResourceNotFoundException("No existe el usuario " + usuarioId));
	}
}
