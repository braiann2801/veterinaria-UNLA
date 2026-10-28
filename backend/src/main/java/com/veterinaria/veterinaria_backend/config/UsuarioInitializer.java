package com.veterinaria.veterinaria_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;
import com.veterinaria.veterinaria_backend.service.impl.AuthServiceImpl;

/**
 * Siembra la cuenta del Duenio cuando no hay ningun Duenio activo.
 *
 * <p>Las demas cuentas las crea el Duenio desde el sistema, asi que sin esta
 * semilla nadie podria entrar la primera vez. La cuenta nace con
 * {@code debeCambiarPassword}: la clave por defecto solo sirve para entrar y
 * cambiarla, el resto de la API responde 403 hasta entonces.</p>
 *
 * <p>Idempotente, como {@link DataInitializer}: con un Duenio activo no hace
 * nada. Si el usuario configurado ya existe como Duenio desactivado, se
 * reactiva con la clave por defecto en vez de chocar contra el UNIQUE del
 * username; si es la cuenta de otro rol, no se toca. El ABM impide desactivar
 * al ultimo Duenio, asi que esto solo pasa si alguien toco la base a mano.</p>
 */
@Component
public class UsuarioInitializer implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(UsuarioInitializer.class);

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final String username;
	private final String password;

	public UsuarioInitializer(
			UsuarioRepository usuarioRepository,
			PasswordEncoder passwordEncoder,
			@Value("${veterinaria.auth.owner-username:duenio}") String username,
			@Value("${veterinaria.auth.owner-password:cambiar1234}") String password) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.username = AuthServiceImpl.normalizarUsername(username);
		this.password = password;
	}

	@Override
	@Transactional
	public void run(String... args) {
		sembrar();
	}

	/** Devuelve 1 si sembro (o reactivo) la cuenta del Duenio, 0 si ya habia uno activo. */
	@Transactional
	public int sembrar() {
		if (usuarioRepository.countByRolAndActivoTrue(Usuario.Rol.DUENIO) > 0) {
			log.info("Ya hay un usuario Duenio activo; no se siembra la cuenta inicial.");
			return 0;
		}
		Usuario duenio = usuarioRepository.findByUsername(username).orElseGet(Usuario::new);
		// Una cuenta de empleado que casualmente se llama igual no se promueve a
		// Duenio con la clave por defecto: eso le daria el control a quien la use.
		if (duenio.getId() != null && duenio.getRol() != Usuario.Rol.DUENIO) {
			log.error("No hay Duenio activo y el usuario '{}' pertenece a una cuenta {}: no se siembra. "
					+ "Definir OWNER_USERNAME con un usuario libre y reiniciar.", username, duenio.getRol());
			return 0;
		}
		duenio.setUsername(username);
		if (duenio.getNombreCompleto() == null) {
			duenio.setNombreCompleto("Dueño");
		}
		duenio.setPasswordHash(passwordEncoder.encode(password));
		duenio.setRol(Usuario.Rol.DUENIO);
		duenio.setActivo(true);
		duenio.setDebeCambiarPassword(true);
		duenio.invalidarSesiones();
		usuarioRepository.save(duenio);
		log.warn("Se creo la cuenta inicial del Duenio '{}'. Debe cambiar la contraseña en el primer ingreso.", username);
		return 1;
	}
}
