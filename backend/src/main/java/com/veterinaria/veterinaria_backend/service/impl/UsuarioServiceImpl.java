package com.veterinaria.veterinaria_backend.service.impl;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.UsuarioConClaveTemporalDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioRequestDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioResponseDTO;
import com.veterinaria.veterinaria_backend.dto.UsuarioUpdateDTO;
import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;
import com.veterinaria.veterinaria_backend.service.UsuarioService;

/**
 * ABM de cuentas.
 *
 * <p>Toda modificacion que cambia lo que el usuario puede hacer (rol, baja,
 * reset de clave) llama a {@link Usuario#invalidarSesiones()}: la sesion abierta
 * en el celular del empleado cae en el siguiente request.</p>
 *
 * <p>Invariante: siempre queda al menos un Duenio activo. Sin el, nadie podria
 * volver a administrar cuentas desde el sistema.</p>
 */
@Service
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

	/** Sin 0/O ni 1/l/I: la clave temporal se dicta en voz alta en el mostrador. */
	private static final char[] ALFABETO = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int LARGO_TEMPORAL = 10;

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final EntityMapper mapper;
	private final SecureRandom random = new SecureRandom();

	public UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			EntityMapper mapper) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.mapper = mapper;
	}

	@Override
	public List<UsuarioResponseDTO> listar() {
		return mapper.toUsuarioResponseList(usuarioRepository.findAllByOrderByNombreCompletoAsc());
	}

	@Override
	@Transactional
	public UsuarioConClaveTemporalDTO crear(UsuarioRequestDTO request) {
		String username = AuthServiceImpl.normalizarUsername(request.username());
		if (usuarioRepository.existsByUsername(username)) {
			throw new BusinessConflictException("Ya existe una cuenta con el usuario '" + username + "'");
		}
		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setNombreCompleto(request.nombreCompleto().trim());
		usuario.setRol(request.rol());
		String temporal = generarTemporal();
		usuario.setPasswordHash(passwordEncoder.encode(temporal));
		usuario.setDebeCambiarPassword(true);
		return new UsuarioConClaveTemporalDTO(mapper.toResponse(usuarioRepository.save(usuario)), temporal);
	}

	@Override
	@Transactional
	public UsuarioResponseDTO actualizar(Long id, UsuarioUpdateDTO request) {
		Usuario usuario = buscar(id);
		if (usuario.getRol() != request.rol()) {
			if (usuario.getRol() == Usuario.Rol.DUENIO && usuario.isActivo()) {
				exigirOtroDuenioActivo("quitarle el rol de Dueño");
			}
			usuario.setRol(request.rol());
			usuario.invalidarSesiones();
		}
		usuario.setNombreCompleto(request.nombreCompleto().trim());
		return mapper.toResponse(usuarioRepository.save(usuario));
	}

	@Override
	@Transactional
	public UsuarioResponseDTO cambiarActivo(Long id, boolean activo, Long solicitanteId) {
		Usuario usuario = buscar(id);
		if (!activo && usuario.isActivo()) {
			if (usuario.getId().equals(solicitanteId)) {
				throw new BusinessConflictException("No se puede desactivar la propia cuenta");
			}
			if (usuario.getRol() == Usuario.Rol.DUENIO) {
				exigirOtroDuenioActivo("desactivarlo");
			}
			usuario.setActivo(false);
			usuario.invalidarSesiones();
		} else if (activo) {
			usuario.setActivo(true);
		}
		return mapper.toResponse(usuarioRepository.save(usuario));
	}

	@Override
	@Transactional
	public UsuarioConClaveTemporalDTO resetearPassword(Long id) {
		Usuario usuario = buscar(id);
		String temporal = generarTemporal();
		usuario.setPasswordHash(passwordEncoder.encode(temporal));
		usuario.setDebeCambiarPassword(true);
		usuario.invalidarSesiones();
		return new UsuarioConClaveTemporalDTO(mapper.toResponse(usuarioRepository.save(usuario)), temporal);
	}

	private void exigirOtroDuenioActivo(String accion) {
		if (usuarioRepository.countByRolAndActivoTrue(Usuario.Rol.DUENIO) <= 1) {
			throw new BusinessConflictException(
					"Es el único Dueño activo: no se puede " + accion + " sin antes dar de alta otro Dueño");
		}
	}

	private String generarTemporal() {
		StringBuilder sb = new StringBuilder(LARGO_TEMPORAL);
		for (int i = 0; i < LARGO_TEMPORAL; i++) {
			sb.append(ALFABETO[random.nextInt(ALFABETO.length)]);
		}
		return sb.toString();
	}

	private Usuario buscar(Long id) {
		return usuarioRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe el usuario " + id));
	}
}
