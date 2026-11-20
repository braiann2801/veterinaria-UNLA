package com.veterinaria.veterinaria_backend.service.impl;

import java.nio.charset.StandardCharsets;
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
import com.veterinaria.veterinaria_backend.security.LimitadorIntentosLogin;
import com.veterinaria.veterinaria_backend.security.TokenService;
import com.veterinaria.veterinaria_backend.service.AuthService;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final EntityMapper mapper;
	private final LimitadorIntentosLogin limitador;
	/**
	 * Hash contra el que se compara cuando el usuario no existe: el login tarda
	 * lo mismo exista o no la cuenta, y el tiempo de respuesta no la delata.
	 */
	private final String hashSenuelo;

	public AuthServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			TokenService tokenService, EntityMapper mapper, LimitadorIntentosLogin limitador) {
		this.limitador = limitador;
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
	public LoginResponseDTO login(LoginRequestDTO request, String ip) {
		String username = normalizarUsername(request.username());
		// BCrypt solo mira los primeros 72 bytes: con acentos, una clave de 72
		// caracteres puede superarlos y perder el final en silencio. Se rechaza
		// antes de la consulta y del hash, en lugar de aceptar una clave recortada.
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new BadRequestException("La contraseña es demasiado larga");
		}
		// El limite va por el usuario pedido, exista o no: asi el 429 no revela que cuentas existen.
		// Aplica las 3 reglas combinadas: par (usuario, IP), IP (anti-spraying) y usuario (anti-distribuido).
		limitador.verificarLogin(username, ip);

		Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
		boolean claveOk = passwordEncoder.matches(request.password(),
				usuario != null ? usuario.getPasswordHash() : hashSenuelo);
		if (usuario == null || !claveOk || !usuario.isActivo()) {
			limitador.registrarFalloLogin(username, ip);
			throw new CredencialesInvalidasException();
		}
		limitador.registrarExitoLogin(username, ip);
		return tokenService.emitir(usuario);
	}

	@Override
	public UsuarioResponseDTO me(Long usuarioId) {
		return mapper.toResponse(buscar(usuarioId));
	}

	@Override
	@Transactional
	public void logout(Long usuarioId) {
		Usuario usuario = buscar(usuarioId);
		usuario.invalidarSesiones();
		usuarioRepository.saveAndFlush(usuario);
	}

	@Override
	@Transactional
	public LoginResponseDTO cambiarPassword(Long usuarioId, CambioPasswordRequestDTO request, String ip) {
		// Control de intentos para evitar fuerza bruta sobre la contrasena actual
		limitador.verificarCambioPassword(usuarioId, ip);

		Usuario usuario = buscar(usuarioId);
		if (!passwordEncoder.matches(request.actual(), usuario.getPasswordHash())) {
			limitador.registrarFalloCambioPassword(usuarioId, ip);
			throw new BadRequestException("La contraseña actual no es correcta");
		}
		if (request.actual().equals(request.nueva())) {
			throw new BadRequestException("La contraseña nueva tiene que ser distinta de la actual");
		}
		// @Size cuenta caracteres; BCrypt corta en 72 bytes. Con acentos o ñ una
		// clave de menos de 72 caracteres puede pasar ese limite y perder el final.
		if (request.nueva().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new BadRequestException("La contraseña nueva es demasiado larga");
		}
		limitador.registrarExitoCambioPassword(usuarioId, ip);
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
